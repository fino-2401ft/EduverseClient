package org.example.eduverseclient.network.p2p;

import common.model.MeetingEnrollment;
import common.model.Peer;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.media.AudioPlayer;
import org.example.eduverseclient.media.CameraCapture;
import org.example.eduverseclient.media.MicrophoneCapture;
import org.example.eduverseclient.media.UDPVideoSender;
import org.example.eduverseclient.network.udp.UDPAudioReceiver;
import org.example.eduverseclient.network.udp.UDPAudioSender;
import org.example.eduverseclient.network.udp.UDPVideoReceiver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
public class MediaStreamManager {
    // Video
    private CameraCapture cameraCapture;
    private UDPVideoSender videoSender;
    private UDPVideoReceiver videoReceiver;

    // Audio
    private MicrophoneCapture microphoneCapture;
    private UDPAudioSender audioSender;
    private UDPAudioReceiver audioReceiver;
    private Map<String, AudioPlayer> audioPlayers;

    private MeetingEnrollment myEnrollment;
    private Peer hostPeer;
    private Peer myPeer;

    private BiConsumer<String, Image> videoCallback;

    public MediaStreamManager(MeetingEnrollment enrollment) {
        this.myEnrollment = enrollment;
        this.myPeer = RMIClient.getInstance().getMyPeer();
        this.audioPlayers = new ConcurrentHashMap<>();

        log.info("✅ MediaStreamManager created - Role: {}", enrollment.getRole());
    }

    /**
     * Start media streaming (video + audio)
     */
    public void start(Peer hostPeer, BiConsumer<String, Image> videoCallback) {
        this.hostPeer = hostPeer;
        this.videoCallback = videoCallback;

        // ============ VIDEO ============
        cameraCapture = new CameraCapture();
        videoSender = new UDPVideoSender(myPeer.getUserId(), myPeer.getVideoPort());
        videoReceiver = new UDPVideoReceiver(myPeer.getVideoPort());

        videoReceiver.start(videoCallback);

        cameraCapture.start(
                frameData -> sendFrameToHost(frameData),
                previewImage -> videoCallback.accept(myPeer.getUserId(), previewImage)
        );

        // ============ AUDIO ============
        microphoneCapture = new MicrophoneCapture();
        audioSender = new UDPAudioSender(myPeer.getUserId(), myPeer.getAudioPort());
        audioReceiver = new UDPAudioReceiver(myPeer.getAudioPort());

        audioReceiver.start((userId, audioData) -> playAudio(userId, audioData));

        microphoneCapture.start(audioData -> sendAudioToHost(audioData));

        log.info("✅ Media streaming started (video + audio)");
    }

    /**
     * Gửi video frame đến HOST
     */
    private void sendFrameToHost(byte[] frameData) {
        if (hostPeer == null) return;

        videoSender.sendFrame(
                frameData,
                hostPeer.getIpAddress(),
                hostPeer.getVideoPort()
        );
    }

    /**
     * Gửi audio đến HOST
     */
    private void sendAudioToHost(byte[] audioData) {
        if (hostPeer == null) return;

        audioSender.sendAudio(
                audioData,
                hostPeer.getIpAddress(),
                hostPeer.getAudioPort()
        );
    }

    /**
     * Play audio from other participants
     */
    private void playAudio(String userId, byte[] audioData) {
        // Don't play our own audio
        if (userId.equals(myPeer.getUserId())) {
            return;
        }

        // Get or create audio player for this user
        AudioPlayer player = audioPlayers.computeIfAbsent(userId, id -> {
            AudioPlayer p = new AudioPlayer();
            p.start();
            log.info("✅ Audio player created for user: {}", id);
            return p;
        });

        player.play(audioData);
    }

    /**
     * Stop media streaming
     */
    public void stop() {
        // Stop video
        if (cameraCapture != null) {
            cameraCapture.stop();
        }

        if (videoSender != null) {
            videoSender.close();
        }

        if (videoReceiver != null) {
            videoReceiver.stop();
        }

        // Stop audio
        if (microphoneCapture != null) {
            microphoneCapture.stop();
        }

        if (audioSender != null) {
            audioSender.close();
        }

        if (audioReceiver != null) {
            audioReceiver.stop();
        }

        if (audioPlayers != null) {
            audioPlayers.values().forEach(AudioPlayer::stop);
            audioPlayers.clear();
        }

        log.info("🛑 Media streaming stopped (video + audio)");
    }
}