package org.example.eduverseclient.media;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class MicrophoneCapture {

    private TargetDataLine microphone;
    private ExecutorService executor;
    private Consumer<byte[]> audioCallback;

    // Trạng thái hoạt động
    private volatile boolean isRunning = false;
    private volatile boolean isMuted = false; // ✨ BIẾN QUAN TRỌNG ĐỂ FIX LỖI "LUÔN BẬT"

    // Cấu hình âm thanh (Standard VoIP config: 16kHz, 16-bit, Mono)
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final int BUFFER_SIZE = 3200; // 200ms buffer

    private final AudioFormat audioFormat;

    public MicrophoneCapture() {
        this.audioFormat = new AudioFormat(
                SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, SIGNED, BIG_ENDIAN
        );
        initMicrophone();
    }

    /**
     * Khởi tạo phần cứng Microphone
     */
    private void initMicrophone() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                log.error("❌ Audio line not supported on this device");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            // Lưu ý: Chưa open/start ở đây, sẽ làm khi gọi hàm start()
            log.info("✅ Microphone hardware detected");

        } catch (Exception e) {
            log.error("❌ Failed to initialize microphone hardware", e);
        }
    }

    /**
     * Bắt đầu thu âm
     * @param callback Hàm xử lý dữ liệu âm thanh thu được
     */
    public void start(Consumer<byte[]> callback) {
        if (isRunning) return; // Tránh gọi 2 lần
        if (microphone == null) {
            log.error("❌ Microphone unavailable, cannot start");
            return;
        }

        this.audioCallback = callback;

        try {
            // Mở và bắt đầu dòng thu âm
            if (!microphone.isOpen()) {
                microphone.open(audioFormat);
            }
            microphone.start();

            isRunning = true;
            executor = Executors.newSingleThreadExecutor();

            // Chạy vòng lặp thu âm trên luồng riêng
            executor.submit(this::captureLoop);

            log.info("✅ Microphone capture started - Format: {}Hz {}bit", SAMPLE_RATE, SAMPLE_SIZE);

        } catch (LineUnavailableException e) {
            log.error("❌ Microphone line unavailable (used by another app?)", e);
        }
    }

    /**
     * Vòng lặp thu âm (Chạy trên luồng riêng)
     */
    private void captureLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (isRunning && microphone.isOpen()) {
            try {
                // Đọc dữ liệu từ phần cứng
                int bytesRead = microphone.read(buffer, 0, buffer.length);

                // ✨ FIX LỖI: Chỉ gửi dữ liệu nếu đọc thành công VÀ KHÔNG BỊ MUTE
                if (bytesRead > 0 && !isMuted) {
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);

                    if (audioCallback != null) {
                        audioCallback.accept(audioData);
                    }
                } else if (isMuted) {
                    // Nếu Mute, có thể sleep nhẹ để giảm tải CPU (tuỳ chọn)
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }

            } catch (Exception e) {
                if (isRunning) { // Chỉ log lỗi nếu vẫn đang chạy
                    log.error("❌ Error in capture loop", e);
                }
            }
        }
    }

    /**
     * Dừng thu âm và giải phóng tài nguyên
     */
    public void stop() {
        isRunning = false;

        if (executor != null) {
            executor.shutdownNow();
        }

        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }

        log.info("🛑 Microphone capture stopped");
    }

    /**
     * Bật/Tắt chế độ Mute
     */
    public void setMuted(boolean muted) {
        this.isMuted = muted;
        log.info(muted ? "🔇 Microphone Muted" : "🎤 Microphone Unmuted");
    }

    public boolean isMuted() {
        return isMuted;
    }

    public boolean isAvailable() {
        return microphone != null;
    }
}