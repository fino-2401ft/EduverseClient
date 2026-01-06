package org.example.eduverseclient.network.media;

import common.model.Peer;
import common.model.exam.ExamParticipant;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * ExamStreamManager - Quản lý streaming cho Exam Room
 * Logic giống Meeting: Proctor (HOST) forward video giữa tất cả participants
 */
@Slf4j
public class ExamStreamManager {
    // Shared Sockets
    private DatagramSocket videoSocket;
    private DatagramSocket audioSocket;
    private DatagramSocket chatSocket;

    // Video
    private CameraCapture cameraCapture;
    private UDPVideoSender videoSender;
    private UDPVideoReceiver videoReceiver;

    // Audio
    private MicrophoneCapture microphoneCapture;
    private UDPAudioSender audioSender;
    private UDPAudioReceiver audioReceiver;
    private Map<String, AudioPlayer> audioPlayers;

    // Common
    private ExamParticipant myParticipant;
    private Peer proctorPeer;  // Proctor peer (chỉ có nếu mình là student)
    private Peer myPeer;
    private String examId;
    private boolean isProctor;

    // Peer Cache
    private List<Peer> otherPeers;  // Tất cả peers khác (proctor hoặc students)
    private long lastPeerUpdateTime = 0;
    private static final long PEER_UPDATE_INTERVAL = 2000;
    private ScheduledExecutorService peerUpdateExecutor;
    
    // Circuit breaker for updatePeerList
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES = 3;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 10000;
    private long lastFailureTime = 0;

    // Callbacks
    private BiConsumer<String, Image> videoCallback;

    public ExamStreamManager(ExamParticipant participant, boolean isProctor) {
        this.myParticipant = participant;
        this.isProctor = isProctor;
        this.myPeer = RMIClient.getInstance().getMyPeer();
        this.examId = participant.getExamId();
        this.audioPlayers = new ConcurrentHashMap<>();
        log.info("✅ ExamStreamManager initialized - Role: {}, ExamId: {}", 
                isProctor ? "PROCTOR" : "STUDENT", examId);
    }

    public void start(Peer proctorPeer, BiConsumer<String, Image> videoCallback) {
        this.proctorPeer = proctorPeer;
        this.videoCallback = videoCallback;

        try {
            // 1. INITIALIZE SOCKETS
            this.videoSocket = new DatagramSocket(myPeer.getVideoPort());
            this.audioSocket = new DatagramSocket(myPeer.getAudioPort());
            this.chatSocket = new DatagramSocket(myPeer.getChatPort());

            log.info("✅ Sockets bound: Video={}, Audio={}, Chat={}",
                    myPeer.getVideoPort(), myPeer.getAudioPort(), myPeer.getChatPort());

            // Initial peer list update
            updatePeerList();
            peerUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
            peerUpdateExecutor.scheduleAtFixedRate(this::updatePeerList,
                    PEER_UPDATE_INTERVAL, PEER_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);

            // ============ VIDEO ============
            cameraCapture = CameraCapture.getInstance();
            videoSender = new UDPVideoSender(videoSocket, myPeer.getUserId());
            videoReceiver = new UDPVideoReceiver(videoSocket);

            videoReceiver.start((senderId, receivedImage) -> {
                // Nhận video và hiển thị (cho cả proctor và student)
                if (videoCallback != null) {
                    videoCallback.accept(senderId, receivedImage);
                }
                // Proctor forward video từ students đến tất cả participants khác
                // (không forward video của chính mình)
                if (isProctor && !senderId.equals(myPeer.getUserId())) {
                    forwardVideoToOthers(senderId, receivedImage);
                }
            });

            cameraCapture.start(
                    frameData -> {
                        if (isProctor) {
                            // PROCTOR: Broadcast camera của mình đến TẤT CẢ students
                            updatePeerList(); // Update peer list trước khi gửi
                            if (otherPeers != null && !otherPeers.isEmpty() && videoSender != null) {
                                otherPeers.forEach(peer -> {
                                    try {
                                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                                    } catch (Exception e) {
                                        log.error("Failed to send frame to {}: {}", peer.getUserId(), e.getMessage());
                                    }
                                });
                            }
                        } else {
                            // STUDENT: Gửi video đến proctor (proctor sẽ forward)
                            sendFrameToProctor(frameData);
                        }
                    },
                    previewImage -> {
                        // Preview camera của chính mình
                        if (videoCallback != null) {
                            videoCallback.accept(myPeer.getUserId(), previewImage);
                        }
                    }
            );

            // ============ AUDIO ============
            microphoneCapture = new MicrophoneCapture();
            audioSender = new UDPAudioSender(audioSocket, myPeer.getUserId());
            audioReceiver = new UDPAudioReceiver(audioSocket);

            audioReceiver.start((senderId, audioData) -> {
                playAudio(senderId, audioData);
                // Proctor forward audio đến tất cả participants khác
                if (isProctor) {
                    forwardAudioToOthers(senderId, audioData);
                }
            });

            microphoneCapture.start(audioData -> {
                if (isProctor) {
                    // PROCTOR: Broadcast audio đến tất cả students
                    if (otherPeers != null && !otherPeers.isEmpty() && audioSender != null) {
                        otherPeers.forEach(peer -> {
                            try {
                                audioSender.sendAudio(audioData, peer.getIpAddress(), peer.getAudioPort());
                            } catch (Exception e) {
                                log.error("Failed to send audio to {}: {}", peer.getUserId(), e.getMessage());
                            }
                        });
                    }
                } else {
                    // STUDENT: Gửi audio đến proctor
                    sendAudioToProctor(audioData);
                }
            });

            log.info("✅ Exam streaming started successfully!");

        } catch (SocketException e) {
            log.error("❌ Critical Error: Failed to bind sockets", e);
            stop();
        }
    }

    public void stop() {
        log.info("🛑 Stopping Exam Stream Manager...");

        try {
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

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

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

            log.info("✅ Exam Stream Manager stopped completely.");
        } catch (Exception e) {
            log.error("❌ Error during ExamStreamManager stop", e);
        }
    }

    public void setMicrophoneMute(boolean mute) {
        if (microphoneCapture != null) {
            microphoneCapture.setMuted(mute);
        }
    }

    public void setCameraActive(boolean active) {
        // Trong exam, camera luôn phải ON (bắt buộc)
        if (!active) {
            log.warn("⚠️ Camera cannot be turned off in exam mode");
            return;
        }

        CameraCapture camera = CameraCapture.getInstance();
        if (active) {
            camera.start(
                    frameData -> {
                        if (isProctor) {
                            if (otherPeers != null && !otherPeers.isEmpty() && videoSender != null) {
                                otherPeers.forEach(peer -> {
                                    try {
                                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                                    } catch (Exception e) {
                                        log.error("Failed to send frame to {}: {}", peer.getUserId(), e.getMessage());
                                    }
                                });
                            }
                        } else {
                            sendFrameToProctor(frameData);
                        }
                    },
                    previewImage -> {
                        if (videoCallback != null) {
                            videoCallback.accept(myPeer.getUserId(), previewImage);
                        }
                    }
            );
        }
    }

    // --- DATA SENDING METHODS ---

    private void sendFrameToProctor(byte[] frameData) {
        // Nếu chưa có proctorPeer, thử lấy lại
        if (proctorPeer == null) {
            try {
                proctorPeer = RMIClient.getInstance().getExamService().getProctorPeer(examId);
                if (proctorPeer != null) {
                    log.debug("✅ Found proctor peer: {}", proctorPeer.getUserId());
                }
            } catch (Exception e) {
                log.warn("Failed to get proctor peer: {}", e.getMessage());
            }
        }
        
        if (proctorPeer != null && videoSender != null) {
            try {
                videoSender.sendFrame(frameData, proctorPeer.getIpAddress(), proctorPeer.getVideoPort());
                log.trace("📤 Sent frame to proctor: {}:{}", proctorPeer.getIpAddress(), proctorPeer.getVideoPort());
            } catch (Exception e) {
                log.error("Failed to send frame to proctor: {}", e.getMessage());
            }
        } else {
            if (proctorPeer == null) {
                log.debug("⚠️ Cannot send frame: Proctor peer is null");
            }
        }
    }

    private void sendAudioToProctor(byte[] audioData) {
        if (proctorPeer != null && audioSender != null) {
            try {
                audioSender.sendAudio(audioData, proctorPeer.getIpAddress(), proctorPeer.getAudioPort());
            } catch (Exception e) {
                log.error("Failed to send audio to proctor: {}", e.getMessage());
            }
        }
    }

    // --- FORWARDING METHODS ---

    private void forwardVideoToOthers(String senderId, Image receivedImage) {
        updatePeerList(); // Đảm bảo có peer list mới nhất
        byte[] frameData = convertImageToBytes(receivedImage);
        if (frameData == null) {
            log.warn("Failed to convert image to bytes for forwarding");
            return;
        }

        if (otherPeers == null || otherPeers.isEmpty()) {
            log.debug("No peers to forward video to");
            return;
        }

        // Forward đến tất cả participants khác (trừ sender và chính mình)
        otherPeers.stream()
                .filter(p -> p != null && !p.getUserId().equals(senderId) && !p.getUserId().equals(myPeer.getUserId()))
                .forEach(peer -> {
                    try {
                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                        log.trace("📤 Forwarded video from {} to {}", senderId, peer.getUserId());
                    } catch (Exception e) {
                        log.error("Failed to forward video to {}: {}", peer.getUserId(), e.getMessage());
                    }
                });
    }

    private void forwardAudioToOthers(String senderId, byte[] audioData) {
        updatePeerList();
        if (otherPeers == null || otherPeers.isEmpty()) {
            return;
        }

        otherPeers.stream()
                .filter(p -> p != null && !p.getUserId().equals(senderId) && !p.getUserId().equals(myPeer.getUserId()))
                .forEach(peer -> {
                    try {
                        audioSender.sendAudio(audioData, peer.getIpAddress(), peer.getAudioPort());
                    } catch (Exception e) {
                        log.error("Failed to forward audio to {}: {}", peer.getUserId(), e.getMessage());
                    }
                });
    }


    private byte[] convertImageToBytes(Image image) {
        try {
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to convert image to bytes", e);
            return null;
        }
    }

    private void updatePeerList() {
        // Circuit breaker: Nếu fail quá nhiều, tạm dừng update
        if (consecutiveFailures >= MAX_FAILURES) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceLastFailure < CIRCUIT_BREAKER_TIMEOUT) {
                return; // Circuit breaker is open, skip update
            } else {
                // Reset after timeout
                consecutiveFailures = 0;
                log.info("🔄 Circuit breaker reset, retrying peer list update");
            }
        }

        try {
            // Cả proctor và student đều lấy tất cả peers (giống meeting)
            List<Peer> latestPeers = RMIClient.getInstance().getMeetingService().getAllPeers(examId);

            if (latestPeers != null && !latestPeers.isEmpty()) {
                this.otherPeers = latestPeers.stream()
                        .filter(p -> p != null && !p.getUserId().equals(myPeer.getUserId()))
                        .collect(Collectors.toList());
                this.lastPeerUpdateTime = System.currentTimeMillis();
                consecutiveFailures = 0; // Reset on success
                
                if (isProctor) {
                    log.debug("📋 Updated peer list: {} students", otherPeers.size());
                } else {
                    log.debug("📋 Updated peer list: {} peers", otherPeers.size());
                }
            } else {
                this.otherPeers = new ArrayList<>();
            }
        } catch (Exception e) {
            consecutiveFailures++;
            lastFailureTime = System.currentTimeMillis();
            log.warn("⚠️ Server connection lost. Using cached peer list ({} peers). Error: {}",
                    (otherPeers != null ? otherPeers.size() : 0), e.getMessage());
        }
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
