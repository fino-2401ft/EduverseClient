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
 * Khác với Meeting:
 * - Proctor: Chỉ gửi camera của mình đến tất cả students
 * - Student: Chỉ nhận camera của proctor, KHÔNG nhận camera của students khác
 * - Không forward video giữa students (1-1 feeling: Student ↔ Proctor)
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

    // Peer Cache (chỉ dùng cho proctor để broadcast)
    private List<Peer> studentPeers;
    private long lastPeerUpdateTime = 0;
    private static final long PEER_UPDATE_INTERVAL = 2000;
    private ScheduledExecutorService peerUpdateExecutor;

    // Callbacks
    private UDPChatSender chatSender;
    private UDPChatReceiver chatReceiver;
    private BiConsumer<String, String> chatMessageCallback;
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

    public void start(Peer proctorPeer, BiConsumer<String, Image> videoCallback, 
                     BiConsumer<String, String> chatCallback) {
        this.proctorPeer = proctorPeer;
        this.videoCallback = videoCallback;
        this.chatMessageCallback = chatCallback;

        try {
            // 1. INITIALIZE SOCKETS
            this.videoSocket = new DatagramSocket(myPeer.getVideoPort());
            this.audioSocket = new DatagramSocket(myPeer.getAudioPort());
            this.chatSocket = new DatagramSocket(myPeer.getChatPort());

            log.info("✅ Sockets bound: Video={}, Audio={}, Chat={}",
                    myPeer.getVideoPort(), myPeer.getAudioPort(), myPeer.getChatPort());

            // Initial peer list update (chỉ proctor cần)
            if (isProctor) {
                updatePeerList();
                peerUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
                peerUpdateExecutor.scheduleAtFixedRate(this::updatePeerList,
                        PEER_UPDATE_INTERVAL, PEER_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
            }

            // ============ VIDEO ============
            cameraCapture = CameraCapture.getInstance();
            videoSender = new UDPVideoSender(videoSocket, myPeer.getUserId());
            videoReceiver = new UDPVideoReceiver(videoSocket);

            videoReceiver.start((senderId, receivedImage) -> {
                if (isProctor) {
                    // PROCTOR: Nhận video từ TẤT CẢ students để hiển thị trong grid
                    String currentUserId = myPeer.getUserId();
                    if (!senderId.equals(currentUserId)) {
                        // Video từ student (không phải chính mình)
                        log.debug("📥 Proctor received video from student: {}", senderId);
                        if (videoCallback != null) {
                            videoCallback.accept(senderId, receivedImage);
                        } else {
                            log.warn("⚠️ Video callback is null for proctor");
                        }
                    }
                } else {
                    // STUDENT: Chỉ nhận video từ proctor
                    // Lấy exam để check proctorId
                    try {
                        common.model.exam.Exam exam = org.example.eduverseclient.RMIClient.getInstance()
                                .getExamService().getExamById(examId);
                        if (exam != null) {
                            String proctorId = exam.getProctorId();
                            
                            // Nếu senderId là proctorId, thì accept video
                            if (senderId.equals(proctorId)) {
                                // Update proctorPeer nếu chưa có hoặc không khớp
                                if (proctorPeer == null || !senderId.equals(proctorPeer.getUserId())) {
                                    Peer latestProctorPeer = org.example.eduverseclient.RMIClient.getInstance()
                                            .getExamService().getProctorPeer(examId);
                                    if (latestProctorPeer != null) {
                                        this.proctorPeer = latestProctorPeer;
                                        log.info("✅ Updated proctor peer: {} -> {}:{}", 
                                                senderId, proctorPeer.getIpAddress(), proctorPeer.getVideoPort());
                                    }
                                }
                                
                                // Nhận video từ proctor
                                log.debug("📥 Student received video from proctor: {}", senderId);
                                if (videoCallback != null) {
                                    videoCallback.accept(senderId, receivedImage);
                                } else {
                                    log.warn("⚠️ Video callback is null for student");
                                }
                            } else {
                                log.debug("⚠️ Video not from proctor. Expected proctorId: {}, Got: {}", 
                                        proctorId, senderId);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to verify proctor: {}", e.getMessage());
                        // Fallback: Nếu proctorPeer đã được set, accept video từ nó
                        if (proctorPeer != null && senderId.equals(proctorPeer.getUserId())) {
                            if (videoCallback != null) {
                                videoCallback.accept(senderId, receivedImage);
                            }
                        }
                    }
                }
            });

            cameraCapture.start(
                    frameData -> {
                        if (isProctor) {
                            // PROCTOR: Broadcast camera của mình đến TẤT CẢ students
                            updatePeerList();
                            if (studentPeers != null && !studentPeers.isEmpty() && videoSender != null) {
                                studentPeers.forEach(peer -> {
                                    try {
                                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                                        log.trace("📤 Proctor sent frame to student {}:{}", 
                                                peer.getIpAddress(), peer.getVideoPort());
                                    } catch (Exception e) {
                                        log.error("Failed to send frame to student {}:{}: {}", 
                                                peer.getIpAddress(), peer.getVideoPort(), e.getMessage());
                                    }
                                });
                            } else {
                                log.debug("⚠️ No students to send video to (peers: {})", 
                                        studentPeers != null ? studentPeers.size() : 0);
                            }
                        } else {
                            // STUDENT: Gửi video đến proctor (để proctor xem trong grid)
                            // Đảm bảo proctorPeer được set đúng
                            if (proctorPeer == null) {
                                try {
                                    Peer latestProctorPeer = org.example.eduverseclient.RMIClient.getInstance()
                                            .getExamService().getProctorPeer(examId);
                                    if (latestProctorPeer != null) {
                                        this.proctorPeer = latestProctorPeer;
                                        log.info("✅ Updated proctor peer for sending: {} -> {}:{}", 
                                                proctorPeer.getUserId(), proctorPeer.getIpAddress(), proctorPeer.getVideoPort());
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to get proctor peer for sending: {}", e.getMessage());
                                }
                            }
                            
                            if (proctorPeer != null && videoSender != null) {
                                try {
                                    videoSender.sendFrame(frameData, proctorPeer.getIpAddress(), proctorPeer.getVideoPort());
                                } catch (Exception e) {
                                    log.error("Failed to send frame to proctor {}:{}: {}", 
                                            proctorPeer.getIpAddress(), proctorPeer.getVideoPort(), e.getMessage());
                                }
                            }
                            // Không log warning nếu proctorPeer null vì có thể proctor chưa join
                        }
                    },
                    previewImage -> {
                        // Preview camera của chính mình (cho cả proctor và student)
                        // Chỉ gọi callback để hiển thị preview, không gửi qua network
                        if (videoCallback != null) {
                            try {
                                videoCallback.accept(myPeer.getUserId(), previewImage);
                            } catch (Exception e) {
                                log.warn("Error in preview callback: {}", e.getMessage());
                            }
                        }
                    }
            );

            // ============ AUDIO ============
            microphoneCapture = new MicrophoneCapture();
            audioSender = new UDPAudioSender(audioSocket, myPeer.getUserId());
            audioReceiver = new UDPAudioReceiver(audioSocket);

            audioReceiver.start((senderId, audioData) -> {
                // STUDENT: Chỉ nhận audio từ proctor
                if (!isProctor) {
                    // Lấy lại proctorPeer nếu cần
                    if (proctorPeer == null || !senderId.equals(proctorPeer.getUserId())) {
                        try {
                            Peer latestProctorPeer = org.example.eduverseclient.RMIClient.getInstance()
                                    .getExamService().getProctorPeer(examId);
                            if (latestProctorPeer != null && senderId.equals(latestProctorPeer.getUserId())) {
                                this.proctorPeer = latestProctorPeer;
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    
                    if (proctorPeer != null && senderId.equals(proctorPeer.getUserId())) {
                        playAudio(senderId, audioData);
                    }
                }
            });

            microphoneCapture.start(audioData -> {
                if (isProctor) {
                    // PROCTOR: Broadcast audio đến tất cả students
                    updatePeerList();
                    if (studentPeers != null && audioSender != null) {
                        studentPeers.forEach(peer -> {
                            try {
                                audioSender.sendAudio(audioData, peer.getIpAddress(), peer.getAudioPort());
                            } catch (Exception e) {
                                log.error("Failed to send audio to {}: {}", peer.getUserId(), e.getMessage());
                            }
                        });
                    }
                } else {
                    // STUDENT: Gửi audio đến proctor (nếu cần)
                    if (proctorPeer != null) {
                        audioSender.sendAudio(audioData, proctorPeer.getIpAddress(), proctorPeer.getAudioPort());
                    }
                }
            });

            // ============ CHAT ============
            chatSender = new UDPChatSender(chatSocket, myPeer.getUserId());
            chatReceiver = new UDPChatReceiver(chatSocket);

            chatReceiver.start(
                    (senderId, conversationId, message) -> {
                        if (chatMessageCallback != null) chatMessageCallback.accept(senderId, message);
                        // Proctor forward chat đến students (tương tự meeting)
                        if (isProctor) {
                            forwardChatToStudents(senderId, message);
                        }
                    },
                    new UDPChatReceiver.FileTransferCallback() {
                        @Override public void onFileStart(String senderId, String conversationId, String fileName, int fileSize, int totalChunks) {}
                        @Override public void onFileChunk(String senderId, String conversationId, int chunkIndex, int totalChunks) {}
                        @Override public void onFileComplete(String senderId, String conversationId, String fileName, byte[] fileData) {
                            log.info("✅ File received: {}", fileName);
                        }
                    }
            );

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
            if (chatReceiver != null) {
                try {
                    chatReceiver.stop();
                } catch (Exception e) {
                    log.warn("Error stopping chat receiver", e);
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
                            updatePeerList();
                            if (studentPeers != null && videoSender != null) {
                                studentPeers.forEach(peer -> {
                                    try {
                                        videoSender.sendFrame(frameData, peer.getIpAddress(), peer.getVideoPort());
                                    } catch (Exception e) {
                                        log.error("Failed to send frame to {}: {}", peer.getUserId(), e.getMessage());
                                    }
                                });
                            }
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

    public void sendChatMessage(String message) {
        if (chatSender == null) {
            log.error("❌ Chat sender is not initialized");
            return;
        }

        if (chatSocket == null || chatSocket.isClosed()) {
            log.error("❌ Chat socket is not available");
            return;
        }

        if (isProctor) {
            // PROCTOR: Broadcast đến tất cả students
            forwardChatToStudents(myPeer.getUserId(), message);
        } else {
            // STUDENT: Gửi đến proctor
            if (proctorPeer != null) {
                try {
                    chatSender.sendMessage(examId, message, proctorPeer.getIpAddress(), proctorPeer.getChatPort());
                } catch (Exception e) {
                    log.error("❌ Failed to send chat message to proctor: {}", e.getMessage());
                }
            } else {
                log.warn("⚠️ Cannot send chat: Proctor peer is null");
            }
        }
    }

    private void forwardChatToStudents(String senderId, String message) {
        updatePeerList();
        if (studentPeers == null) return;

        studentPeers.stream()
                .filter(p -> !p.getUserId().equals(senderId) && !p.getUserId().equals(myPeer.getUserId()))
                .forEach(peer -> {
                    try {
                        chatSender.forwardMessage(senderId, examId, message, peer.getIpAddress(), peer.getChatPort());
                    } catch (Exception e) {
                        log.error("Forward chat failed to {}: {}", peer.getUserId(), e.getMessage());
                    }
                });
    }

    private void updatePeerList() {
        if (!isProctor) return; // Chỉ proctor cần update peer list

        try {
            List<Peer> latestPeers = RMIClient.getInstance().getExamService().getAllStudentPeers(examId);
            if (latestPeers != null && !latestPeers.isEmpty()) {
                int oldSize = studentPeers != null ? studentPeers.size() : 0;
                this.studentPeers = latestPeers;
                this.lastPeerUpdateTime = System.currentTimeMillis();
                if (oldSize != studentPeers.size()) {
                    log.info("📋 Updated student peer list: {} students (was: {})", studentPeers.size(), oldSize);
                }
            } else {
                if (studentPeers == null || !studentPeers.isEmpty()) {
                    this.studentPeers = new ArrayList<>();
                    log.debug("📋 No students in exam yet");
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Server connection lost. Using cached peer list ({} students). Error: {}",
                    (studentPeers != null ? studentPeers.size() : 0), e.getMessage());
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

