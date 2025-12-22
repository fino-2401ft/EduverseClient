package org.example.eduverseclient.network.p2p;

import common.enums.MeetingRole;
import common.model.MeetingEnrollment;
import common.model.Peer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.media.*;
import org.example.eduverseclient.network.udp.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
public class MediaStreamManager {
    // Shared Sockets (Chìa khóa để sửa lỗi Bind Exception)
    private DatagramSocket videoSocket;
    private DatagramSocket audioSocket;
    private DatagramSocket chatSocket;

    // Video
    private CameraCapture cameraCapture; // Singleton
    private UDPVideoSender videoSender;
    private UDPVideoReceiver videoReceiver;

    // Audio
    private MicrophoneCapture microphoneCapture;
    private UDPAudioSender audioSender;
    private UDPAudioReceiver audioReceiver;
    private Map<String, AudioPlayer> audioPlayers;

    // Common
    private MeetingEnrollment myEnrollment;
    private Peer hostPeer;
    private Peer myPeer;
    private String meetingId;

    // Peer Cache - Dùng CopyOnWriteArrayList để thread-safe
    private volatile List<Peer> otherPeers = new CopyOnWriteArrayList<>();
    private final Object peerListLock = new Object();
    private ScheduledExecutorService peerUpdateExecutor;
    private static final long PEER_UPDATE_INTERVAL = 2000; // Update mỗi 2 giây thay vì 5 giây

    // Callbacks
    private UDPChatSender chatSender;
    private UDPChatReceiver chatReceiver;
    private BiConsumer<String, String> chatMessageCallback;
    private BiConsumer<String, Image> videoCallback;

    public MediaStreamManager(MeetingEnrollment enrollment) {
        this.myEnrollment = enrollment;
        this.myPeer = RMIClient.getInstance().getMyPeer();
        this.meetingId = enrollment.getMeetingId();
        this.audioPlayers = new ConcurrentHashMap<>();
        log.info("✅ MediaStreamManager initialized - Role: {}, Port: {}", enrollment.getRole(), myPeer.getVideoPort());
    }

    public void start(Peer hostPeer, BiConsumer<String, Image> videoCallback, BiConsumer<String, String> chatCallback) {
        this.hostPeer = hostPeer;
        this.videoCallback = videoCallback;
        this.chatMessageCallback = chatCallback;

        try {
            // 1. KHỞI TẠO SOCKET DÙNG CHUNG (FIX LỖI BIND EXCEPTION)
            // Cùng 1 cổng cho cả gửi và nhận
            this.videoSocket = new DatagramSocket(myPeer.getVideoPort());
            this.audioSocket = new DatagramSocket(myPeer.getAudioPort());
            this.chatSocket = new DatagramSocket(myPeer.getChatPort());

            log.info("✅ Sockets bound successfully: Video={}, Audio={}, Chat={}",
                    myPeer.getVideoPort(), myPeer.getAudioPort(), myPeer.getChatPort());

            // Update peer list ngay lập tức
            updatePeerList();
            
            // Bắt đầu periodic peer list update
            startPeerListUpdater();

            // ============ VIDEO ============
            // Sử dụng Singleton Camera (FIX LỖI WEBCAM LOCK)
            cameraCapture = CameraCapture.getInstance();

            // Truyền socket đã tạo vào Sender và Receiver
            videoSender = new UDPVideoSender(videoSocket, myPeer.getUserId());
            videoReceiver = new UDPVideoReceiver(videoSocket);

            videoReceiver.start((senderId, receivedImage) -> {
                if (videoCallback != null) videoCallback.accept(senderId, receivedImage);
                if (myEnrollment.getRole() == MeetingRole.HOST) forwardVideoToOthers(senderId, receivedImage);
            });

            // Chỉ start camera capture, không tạo mới object
            cameraCapture.start(
                    frameData -> {
                        // Nếu là HOST, gửi trực tiếp đến tất cả participants
                        // Nếu là PARTICIPANT, gửi đến HOST để forward
                        if (myEnrollment.getRole() == MeetingRole.HOST) {
                            sendFrameToAllPeers(frameData);
                        } else {
                            sendFrameToHost(frameData);
                        }
                    },
                    previewImage -> {
                        if (videoCallback != null) videoCallback.accept(myPeer.getUserId(), previewImage);
                    }
            );

            // ============ AUDIO ============
            microphoneCapture = new MicrophoneCapture();
            audioSender = new UDPAudioSender(audioSocket, myPeer.getUserId());
            audioReceiver = new UDPAudioReceiver(audioSocket);

            audioReceiver.start((senderId, audioData) -> {
                playAudio(senderId, audioData);
                if (myEnrollment.getRole() == MeetingRole.HOST) forwardAudioToOthers(senderId, audioData);
            });
            microphoneCapture.start(audioData -> {
                // Nếu là HOST, gửi trực tiếp đến tất cả participants
                // Nếu là PARTICIPANT, gửi đến HOST để forward
                if (myEnrollment.getRole() == MeetingRole.HOST) {
                    sendAudioToAllPeers(audioData);
                } else {
                    sendAudioToHost(audioData);
                }
            });

            // ============ CHAT ============
            chatSender = new UDPChatSender(chatSocket, myPeer.getUserId());
            chatReceiver = new UDPChatReceiver(chatSocket);

            chatReceiver.start(
                    (senderId, message) -> {
                        if (chatMessageCallback != null) chatMessageCallback.accept(senderId, message);
                        if (myEnrollment.getRole() == MeetingRole.HOST) forwardChatToOthers(senderId, message);
                    },
                    new UDPChatReceiver.FileTransferCallback() {
                        @Override public void onFileStart(String senderId, String fileName, int fileSize, int totalChunks) {}
                        @Override public void onFileChunk(String senderId, int chunkIndex, int totalChunks) {}
                        @Override public void onFileComplete(String senderId, String fileName, byte[] fileData) {
                            log.info("✅ File received: {}", fileName);
                        }
                    }
            );

            log.info("✅ Media streaming started successfully!");

        } catch (SocketException e) {
            log.error("❌ Critical Error: Failed to bind sockets. Port already in use?", e);
            stop(); // Cleanup nếu lỗi
        }
    }

    public void stop() {
        log.info("🛑 Stopping Media Stream Manager...");

        // 0. Dừng peer list updater trước
        if (peerUpdateExecutor != null && !peerUpdateExecutor.isShutdown()) {
            peerUpdateExecutor.shutdown();
            try {
                if (!peerUpdateExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    peerUpdateExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                peerUpdateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 1. Dừng Camera & Mic
        if (cameraCapture != null) {
            try {
                cameraCapture.stop();
            } catch (Exception e) {
                log.warn("Error stopping camera", e);
            }
        }
        if (microphoneCapture != null) {
            try {
                microphoneCapture.stop();
            } catch (Exception e) {
                log.warn("Error stopping microphone", e);
            }
        }

        // 2. Dừng Receiver Threads (phải dừng trước khi đóng socket)
        if (videoReceiver != null) {
            try {
                videoReceiver.stop();
            } catch (Exception e) {
                log.warn("Error stopping video receiver", e);
            }
        }
        if (audioReceiver != null) {
            try {
                audioReceiver.stop();
            } catch (Exception e) {
                log.warn("Error stopping audio receiver", e);
            }
        }
        if (chatReceiver != null) {
            try {
                chatReceiver.stop();
            } catch (Exception e) {
                log.warn("Error stopping chat receiver", e);
            }
        }

        // 3. Đợi một chút để threads kết thúc
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. Đóng Sockets (Quan trọng: Đóng sau khi threads đã dừng)
        if (videoSocket != null && !videoSocket.isClosed()) {
            try {
                videoSocket.close();
            } catch (Exception e) {
                log.warn("Error closing video socket", e);
            }
        }
        if (audioSocket != null && !audioSocket.isClosed()) {
            try {
                audioSocket.close();
            } catch (Exception e) {
                log.warn("Error closing audio socket", e);
            }
        }
        if (chatSocket != null && !chatSocket.isClosed()) {
            try {
                chatSocket.close();
            } catch (Exception e) {
                log.warn("Error closing chat socket", e);
            }
        }

        // 5. Cleanup Audio Players
        if (audioPlayers != null) {
            audioPlayers.values().forEach(player -> {
                try {
                    player.stop();
                } catch (Exception e) {
                    log.warn("Error stopping audio player", e);
                }
            });
            audioPlayers.clear();
        }

        // 6. Clear peer list
        synchronized (peerListLock) {
            otherPeers.clear();
        }

        log.info("✅ Media Stream Manager stopped completely.");
    }

    public void setMicrophoneMute(boolean mute) {
        if (microphoneCapture != null) {
            microphoneCapture.setMuted(mute);
        }
    }


// Trong MediaStreamManager.java

    public void setCameraActive(boolean active) {
        // Lấy instance của Camera
        CameraCapture camera = CameraCapture.getInstance();

        if (active) {
            // Nếu bật -> Gọi start lại (kèm callback gửi ảnh)
            camera.start(
                    frameData -> {
                        // Nếu là HOST, gửi trực tiếp đến tất cả participants
                        // Nếu là PARTICIPANT, gửi đến HOST để forward
                        if (myEnrollment.getRole() == MeetingRole.HOST) {
                            sendFrameToAllPeers(frameData);
                        } else {
                            sendFrameToHost(frameData);
                        }
                    },
                    previewImage -> {
                        if (videoCallback != null) {
                            videoCallback.accept(myPeer.getUserId(), previewImage);
                        }
                    }
            );
        } else {
            // Nếu tắt -> Gọi stop
            camera.stop();

            // (Tuỳ chọn) Gửi một ảnh đen hoặc null để bên kia biết mình tắt cam
            // Nhưng hiện tại stop() là đủ để ngừng gửi dữ liệu
        }
    }

    // --- CÁC HÀM GỬI DỮ LIỆU (ĐÃ FIX NPE) ---

    private void sendFrameToHost(byte[] frameData) {
        if (hostPeer != null && videoSender != null) {
            videoSender.sendFrame(frameData, hostPeer.getIpAddress(), hostPeer.getVideoPort());
        }
    }

    private void sendFrameToAllPeers(byte[] frameData) {
        // HOST gửi video của chính mình đến tất cả participants
        if (videoSender == null) return;
        
        updatePeerList(); // Đảm bảo có danh sách mới nhất
        
        synchronized (peerListLock) {
            if (otherPeers == null || otherPeers.isEmpty()) {
                return;
            }
            
            otherPeers.forEach(peer -> {
                try {
                    if (peer != null && !peer.getUserId().equals(myPeer.getUserId())) {
                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                    }
                } catch (Exception e) {
                    log.warn("Failed to send frame to {}: {}", peer.getUserId(), e.getMessage());
                }
            });
        }
    }

    private void sendAudioToHost(byte[] audioData) {
        if (hostPeer != null && audioSender != null) {
            audioSender.sendAudio(audioData, hostPeer.getIpAddress(), hostPeer.getAudioPort());
        }
    }

    private void sendAudioToAllPeers(byte[] audioData) {
        // HOST gửi audio của chính mình đến tất cả participants
        if (audioSender == null) return;
        
        updatePeerList(); // Đảm bảo có danh sách mới nhất
        
        synchronized (peerListLock) {
            if (otherPeers == null || otherPeers.isEmpty()) {
                return;
            }
            
            otherPeers.forEach(peer -> {
                try {
                    if (peer != null && !peer.getUserId().equals(myPeer.getUserId())) {
                        audioSender.sendAudio(audioData, peer.getIpAddress(), peer.getAudioPort());
                    }
                } catch (Exception e) {
                    log.warn("Failed to send audio to {}: {}", peer.getUserId(), e.getMessage());
                }
            });
        }
    }

    public void sendChatMessage(String message) {
        if (chatSender == null) {
            log.warn("Cannot send chat: ChatSender is null");
            return;
        }
        
        // Nếu là HOST, gửi trực tiếp đến tất cả participants
        // Nếu là PARTICIPANT, gửi đến HOST để forward
        if (myEnrollment.getRole() == MeetingRole.HOST) {
            sendChatToAllPeers(message);
        } else {
            if (hostPeer != null) {
                chatSender.sendMessage(message, hostPeer.getIpAddress(), hostPeer.getChatPort());
            } else {
                log.warn("Cannot send chat: Host peer is null");
            }
        }
    }
    
    private void sendChatToAllPeers(String message) {
        // HOST gửi chat của chính mình đến tất cả participants
        updatePeerList(); // Đảm bảo có danh sách mới nhất
        
        synchronized (peerListLock) {
            if (otherPeers == null || otherPeers.isEmpty()) {
                return;
            }
            
            otherPeers.forEach(peer -> {
                try {
                    if (peer != null && !peer.getUserId().equals(myPeer.getUserId())) {
                        chatSender.sendMessage(message, peer.getIpAddress(), peer.getChatPort());
                    }
                } catch (Exception e) {
                    log.warn("Failed to send chat to {}: {}", peer.getUserId(), e.getMessage());
                }
            });
        }
    }

    // --- CÁC HÀM FORWARD (GIỮ NGUYÊN LOGIC CỦA BẠN) ---
    // (Tôi đã rút gọn code lặp lại để dễ nhìn hơn, logic giữ nguyên)

    private void forwardChatToOthers(String senderId, String message) {
        forwardData(senderId, (peer) -> {
            if (chatSender != null) {
                chatSender.sendMessage(message, peer.getIpAddress(), peer.getChatPort());
            }
        });
    }

    private void forwardVideoToOthers(String senderId, Image receivedImage) {
        // Luôn update peer list trước khi forward (đảm bảo có danh sách mới nhất)
        updatePeerList();
        
        byte[] frameData = convertImageToBytes(receivedImage);
        if (frameData != null && videoSender != null) {
            forwardData(senderId, (peer) ->
                    videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort()));
        }
    }

    private void forwardAudioToOthers(String senderId, byte[] audioData) {
        // Update peer list trước khi forward
        updatePeerList();
        
        if (audioSender != null) {
            forwardData(senderId, (peer) ->
                    audioSender.sendAudio(audioData, peer.getIpAddress(), peer.getAudioPort()));
        }
    }

    private void forwardData(String senderId, ThrowingConsumer<Peer> action) {
        // Tạo snapshot của peer list để tránh race condition
        List<Peer> peersToForward;
        synchronized (peerListLock) {
            if (otherPeers == null || otherPeers.isEmpty()) {
                log.debug("No peers to forward to");
                return;
            }
            // Tạo copy để tránh ConcurrentModificationException
            peersToForward = new CopyOnWriteArrayList<>(otherPeers);
        }
        
        // Filter và forward
        peersToForward.stream()
                .filter(p -> p != null && 
                        !p.getUserId().equals(senderId) && 
                        !p.getUserId().equals(myPeer.getUserId()))
                .forEach(peer -> {
                    try {
                        action.accept(peer);
                        log.debug("✅ Forwarded to {}:{}", peer.getUserId(), peer.getIpAddress());
                    } catch (Exception e) {
                        log.warn("❌ Forward failed to {}: {}", peer.getUserId(), e.getMessage());
                    }
                });
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }

    // --- CÁC HÀM TIỆN ÍCH (UPDATE PEER, CONVERT IMAGE, PLAY AUDIO) ---
    
    /**
     * Bắt đầu periodic peer list updater
     */
    private void startPeerListUpdater() {
        peerUpdateExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PeerListUpdater");
            t.setDaemon(true);
            return t;
        });
        
        peerUpdateExecutor.scheduleAtFixedRate(() -> {
            try {
                updatePeerList();
            } catch (Exception e) {
                log.error("Error in peer list updater", e);
            }
        }, 1, PEER_UPDATE_INTERVAL / 1000, TimeUnit.SECONDS);
        
        log.info("✅ Peer list updater started (interval: {}ms)", PEER_UPDATE_INTERVAL);
    }
    
    /**
     * Update peer list từ server (thread-safe)
     */
    private void updatePeerList() {
        try {
            List<Peer> newPeers = RMIClient.getInstance().getMeetingService().getAllPeers(meetingId)
                    .stream()
                    .filter(p -> p != null && !p.getUserId().equals(myPeer.getUserId()))
                    .collect(Collectors.toList());
            
            synchronized (peerListLock) {
                int oldSize = otherPeers.size();
                otherPeers.clear();
                otherPeers.addAll(newPeers);
                
                if (newPeers.size() != oldSize) {
                    log.info("📡 Peer list updated: {} peers (was {})", newPeers.size(), oldSize);
                    newPeers.forEach(p -> log.debug("  - {}:{}:{}", p.getUserId(), p.getIpAddress(), p.getVideoPort()));
                }
            }
        } catch (RemoteException e) {
            log.error("❌ Update peer list failed", e);
        } catch (Exception e) {
            log.error("❌ Unexpected error updating peer list", e);
        }
    }

    private byte[] convertImageToBytes(Image image) {
        try {
            BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bImage, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException | NullPointerException e) { return null; }
    }

    private void playAudio(String userId, byte[] audioData) {
        if (userId.equals(myPeer.getUserId())) return;
        audioPlayers.computeIfAbsent(userId, id -> {
            AudioPlayer p = new AudioPlayer();
            p.start();
            return p;
        }).play(audioData);
    }
}