package org.example.eduverseclient.network.media;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
public class MediaStreamManager {
    // Shared Sockets (To fix Bind Exception)
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

    // Peer Cache
    private List<Peer> otherPeers;
    private long lastPeerUpdateTime = 0;
    private static final long PEER_UPDATE_INTERVAL = 2000; // Update every 2 seconds
    private ScheduledExecutorService peerUpdateExecutor;
    
    // Circuit breaker for updatePeerList
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES = 3;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 10000; // 10 seconds
    private long lastFailureTime = 0;

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
            // 1. INITIALIZE SHARED SOCKETS
            this.videoSocket = new DatagramSocket(myPeer.getVideoPort());
            this.audioSocket = new DatagramSocket(myPeer.getAudioPort());
            
            // Try to reuse chat socket from P2PMessengerService to avoid port conflict
            java.net.DatagramSocket existingChatSocket = null;
            try {
                org.example.eduverseclient.service.P2PMessengerService messengerService = 
                    org.example.eduverseclient.service.P2PMessengerService.getInstance();
                if (messengerService.isInitialized()) {
                    existingChatSocket = messengerService.getChatSocket();
                }
            } catch (Exception e) {
                log.debug("P2PMessengerService not available, creating new chat socket");
            }
            
            if (existingChatSocket != null && !existingChatSocket.isClosed()) {
                this.chatSocket = existingChatSocket;
                log.info("✅ Reusing chat socket from P2PMessengerService");
            } else {
                this.chatSocket = new DatagramSocket(myPeer.getChatPort());
                log.info("✅ Created new chat socket on port {}", myPeer.getChatPort());
            }

            log.info("✅ Sockets bound successfully: Video={}, Audio={}, Chat={}",
                    myPeer.getVideoPort(), myPeer.getAudioPort(), myPeer.getChatPort());

            // Initial peer list update
            updatePeerList();

            // Start periodic peer list updates
            peerUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
            peerUpdateExecutor.scheduleAtFixedRate(this::updatePeerList,
                    PEER_UPDATE_INTERVAL, PEER_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
            
            // Force immediate update after short delay to catch late joiners (only runs once)
            peerUpdateExecutor.schedule(() -> {
                updatePeerList();
                log.info("✅ Force peer list update for late joiners");
            }, 500, TimeUnit.MILLISECONDS);

            // ============ VIDEO ============
            // Use Singleton Camera
            cameraCapture = CameraCapture.getInstance();

            videoSender = new UDPVideoSender(videoSocket, myPeer.getUserId());
            videoReceiver = new UDPVideoReceiver(videoSocket);

            videoReceiver.start((senderId, receivedImage) -> {
                if (videoCallback != null) videoCallback.accept(senderId, receivedImage);
                if (myEnrollment.getRole() == MeetingRole.HOST) forwardVideoToOthers(senderId, receivedImage);
            });

            cameraCapture.start(
                    frameData -> {
                        // Send to host (if participant) or broadcast (if host)
                        if (myEnrollment.getRole() == MeetingRole.HOST) {
                            // Host: broadcast to all participants
                          //  updatePeerList();


                            if (otherPeers != null && videoSender != null) {
                                otherPeers.forEach(peer -> {
                                    try {
                                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                                    } catch (Exception e) {
                                        log.error("Failed to send frame to {}: {}", peer.getUserId(), e.getMessage());
                                    }
                                });
                            }
                        } else {
                            // Participant: send to host only
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
                if (myEnrollment.getRole() == MeetingRole.HOST) {
                  //  updatePeerList();
                    if (otherPeers != null && audioSender != null) {
                        otherPeers.forEach(peer -> {
                            try {
                                audioSender.sendAudio(audioData, peer.getIpAddress(), peer.getAudioPort());
                            } catch (Exception e) {
                                log.error("Failed to send audio to {}: {}", peer.getUserId(), e.getMessage());
                            }
                        });
                    }
                } else {
                    sendAudioToHost(audioData);
                }
            });

            // ============ CHAT ============
            chatSender = new UDPChatSender(chatSocket, myPeer.getUserId());
            chatReceiver = new UDPChatReceiver(chatSocket);

            chatReceiver.start(
                    (senderId, conversationId, message) -> {
                        // For meeting chat, conversationId should be meetingId
                        if (chatMessageCallback != null) chatMessageCallback.accept(senderId, message);
                        if (myEnrollment.getRole() == MeetingRole.HOST) forwardChatToOthers(senderId, message);
                    },
                    new UDPChatReceiver.FileTransferCallback() {
                        @Override public void onFileStart(String senderId, String conversationId, String fileName, int fileSize, int totalChunks) {}
                        @Override public void onFileChunk(String senderId, String conversationId, int chunkIndex, int totalChunks) {}
                        @Override public void onFileComplete(String senderId, String conversationId, String fileName, byte[] fileData) {
                            log.info("✅ File received: {}", fileName);
                        }
                    }
            );

            log.info("✅ Media streaming started successfully!");

        } catch (SocketException e) {
            log.error("❌ Critical Error: Failed to bind sockets. Port already in use?", e);
            stop(); // Cleanup on error
        }
    }

    public void stop() {
        log.info("🛑 Stopping Media Stream Manager...");

        try {
            // 0. Stop periodic peer updates first
            if (peerUpdateExecutor != null) {
                peerUpdateExecutor.shutdown();
                try {
                    if (!peerUpdateExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                        peerUpdateExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    peerUpdateExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // 1. Stop Camera & Mic
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

            // 2. Stop Receiver Threads (CRITICAL: Stop receivers BEFORE closing sockets)
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

            // 3. Wait a bit for receivers to finish processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 4. Close Sockets
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
            // Only close chat socket if we created it (not reused from P2PMessengerService)
            if (chatSocket != null && !chatSocket.isClosed()) {
                try {
                    // Check if this socket is being used by P2PMessengerService
                    org.example.eduverseclient.service.P2PMessengerService messengerService = 
                        org.example.eduverseclient.service.P2PMessengerService.getInstance();
                    if (messengerService.isInitialized() && messengerService.getChatSocket() == chatSocket) {
                        // Socket is shared with P2PMessengerService, don't close it
                        log.info("ℹ️ Chat socket is shared with P2PMessengerService, not closing");
                    } else {
                        // We created this socket, safe to close
                        chatSocket.close();
                    }
                } catch (Exception e) {
                    log.warn("Error closing chat socket", e);
                }
            }

            // 5. Cleanup Audio Players
            if (audioPlayers != null) {
                audioPlayers.values().forEach(player -> {
                    try {
                        if (player != null) player.stop();
                    } catch (Exception e) {
                        log.warn("Error stopping audio player", e);
                    }
                });
                audioPlayers.clear();
            }

            log.info("✅ Media Stream Manager stopped completely.");
        } catch (Exception e) {
            log.error("❌ Error during MediaStreamManager stop", e);
        }
    }

    public void setMicrophoneMute(boolean mute) {
        if (microphoneCapture != null) {
            microphoneCapture.setMuted(mute);
        }
    }

    public void setCameraActive(boolean active) {
        CameraCapture camera = CameraCapture.getInstance();

        if (active) {
            camera.start(
                    frameData -> {
                        if (myEnrollment.getRole() == MeetingRole.HOST) {
                            updatePeerList();
                            if (otherPeers != null && videoSender != null) {
                                otherPeers.forEach(peer -> {
                                    try {
                                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                                    } catch (Exception e) {
                                        log.error("Failed to send frame to {}: {}", peer.getUserId(), e.getMessage());
                                    }
                                });
                            }
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
            camera.stop();
        }
    }

    // --- DATA SENDING METHODS ---

    private void sendFrameToHost(byte[] frameData) {
        if (hostPeer != null && videoSender != null) {
            videoSender.sendFrame(frameData, hostPeer.getIpAddress(), hostPeer.getVideoPort());
        }
    }

    private void sendAudioToHost(byte[] audioData) {
        if (hostPeer != null && audioSender != null) {
            audioSender.sendAudio(audioData, hostPeer.getIpAddress(), hostPeer.getAudioPort());
        }
    }

    public void sendChatMessage(String message) {
        if (chatSender == null) {
            log.error("❌ Chat sender is not initialized. Cannot send message.");
            return;
        }
        
        if (chatSocket == null || chatSocket.isClosed()) {
            log.error("❌ Chat socket is not available. Cannot send message.");
            return;
        }

        // --- UPDATED LOGIC TO PREVENT DUPLICATE MESSAGES ---
        if (myEnrollment.getRole() == MeetingRole.HOST) {
            // Case 1: I AM HOST
            // Broadcast directly to other clients using my own ID
            log.debug("Host sending chat broadcast: {}", message);
            forwardChatToOthers(myPeer.getUserId(), message);

        } else {
            // Case 2: I AM CLIENT
            // Send to Host, Host will distribute
            if (hostPeer != null) {
                try {
                    chatSender.sendMessage(meetingId, message, hostPeer.getIpAddress(), hostPeer.getChatPort());
                } catch (Exception e) {
                    log.error("❌ Failed to send chat message to host: {}", e.getMessage());
                }
            } else {
                log.warn("⚠️ Cannot send chat: Host peer is null");
            }
        }
    }

    // --- FORWARDING METHODS ---

    private void forwardChatToOthers(String senderId, String message) {
        updatePeerList(); // Ensure fresh peer list before forwarding

        forwardData(senderId, (peer) ->
                //  Truyền thêm meetingId vào vị trí conversationId
                chatSender.forwardMessage(senderId, meetingId, message, peer.getIpAddress(), peer.getChatPort())
        );
    }

    private void forwardVideoToOthers(String senderId, Image receivedImage) {
        updatePeerList(); // Ensure fresh peer list before forwarding
        byte[] frameData = convertImageToBytes(receivedImage);
        if (frameData != null) {
            forwardData(senderId, (peer) ->
                    videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort()));
        }
    }

    private void forwardAudioToOthers(String senderId, byte[] audioData) {
        updatePeerList(); // Ensure fresh peer list before forwarding
        forwardData(senderId, (peer) ->
                audioSender.sendAudio(audioData, peer.getIpAddress(), peer.getAudioPort()));
    }

    private void forwardData(String senderId, ThrowingConsumer<Peer> action) {
        if (otherPeers == null) return;
        otherPeers.stream()
                // Filter out:
                // 1. The original sender (so they don't get their own message back)
                // 2. Myself (Host)
                .filter(p -> !p.getUserId().equals(senderId) && !p.getUserId().equals(myPeer.getUserId()))
                .forEach(peer -> {
                    try {
                        action.accept(peer);
                    } catch (Exception e) {
                        log.error("Forward failed to {}: {}", peer.getUserId(), e.getMessage());
                    }
                });
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }

    // --- UTILITY METHODS ---

    private void updatePeerList() {
        // Circuit breaker: Skip RMI call if too many failures
        if (consecutiveFailures >= MAX_FAILURES) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceLastFailure < CIRCUIT_BREAKER_TIMEOUT) {
                // Still in circuit breaker timeout, skip RMI call
                log.debug("⏸️ Circuit breaker active, using cached peer list");
                return;
            } else {
                // Timeout expired, reset and try again
                consecutiveFailures = 0;
                log.info("🔄 Circuit breaker reset, retrying peer list update");
            }
        }
        
        try {
            // Cố gắng lấy danh sách mới từ Server
            List<Peer> latestPeers = RMIClient.getInstance().getMeetingService().getAllPeers(meetingId);

            if (latestPeers != null && !latestPeers.isEmpty()) {
                // Lọc bỏ bản thân mình ra
                this.otherPeers = latestPeers.stream()
                        .filter(p -> p != null && !p.getUserId().equals(myPeer.getUserId()))
                        .collect(Collectors.toList());

                this.lastPeerUpdateTime = System.currentTimeMillis();
                consecutiveFailures = 0; // Reset on success
                log.debug("📋 Updated peer list: {} peers", otherPeers.size());
            }
        } catch (Exception e) {
            consecutiveFailures++;
            lastFailureTime = System.currentTimeMillis();
            
            if (consecutiveFailures >= MAX_FAILURES) {
                log.warn("⚠️ Circuit breaker activated after {} failures. Using cached peer list ({} peers).",
                        MAX_FAILURES, (otherPeers != null ? otherPeers.size() : 0));
            } else {
                log.warn("⚠️ Server connection issue ({} failures). Using cached peer list.",
                        consecutiveFailures);
            }
            // ✨ QUAN TRỌNG: KHÔNG XÓA this.otherPeers
            // Hệ thống sẽ tiếp tục dùng danh sách cũ để gửi Video/Chat
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