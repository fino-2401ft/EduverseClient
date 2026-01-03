package org.example.eduverseclient.media;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamLockException;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class CameraCapture {

    // 1. Singleton Instance: Đảm bảo chỉ có 1 instance duy nhất
    private static CameraCapture instance;

    private Webcam webcam;
    private ScheduledExecutorService executor;
    private Consumer<byte[]> frameCallback;
    private Consumer<Image> previewCallback;
    private volatile boolean isMuted = false;


    // Biến volatile để đảm bảo tính nhất quán giữa các luồng
    private volatile boolean isRunning = false;

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int FPS = 15;

    // 2. Private Constructor: Không cho phép tạo mới từ bên ngoài
    private CameraCapture(String preferredCameraName) {
        try {
            this.webcam = null;

            if (preferredCameraName != null) {
                // 1. Tìm trong danh sách các camera có sẵn
                for (Webcam w : Webcam.getWebcams()) {
                    log.info("🔍 Detected Camera: {}", w.getName()); // Log ra để xem tên
                    if (w.getName().contains(preferredCameraName)) {
                        this.webcam = w;
                        log.info("✅ Selected preferred camera: {}", w.getName());
                        break;
                    }
                }
            }

            // 2. Nếu không tìm thấy hoặc không yêu cầu -> Lấy mặc định
            if (this.webcam == null) {
                this.webcam = Webcam.getDefault();
                log.info("⚠️ Using default camera: {}", this.webcam.getName());
            }

            if (this.webcam != null) {
                webcam.setViewSize(new Dimension(WIDTH, HEIGHT));
            } else {
                log.warn("⚠️ No webcam found");
            }

        } catch (Exception e) {
            log.error("❌ Error initializing webcam driver: {}", e.getMessage());
        }
    }

    // Sửa lại Singleton để hỗ trợ khởi tạo linh hoạt
    // Lưu ý: Singleton chuẩn chỉ tạo 1 lần, nên đây là cách "hack" nhẹ để test
    public static synchronized CameraCapture getInstance(String cameraName) {
        if (instance == null) {
            instance = new CameraCapture(cameraName);
        }
        return instance;
    }

    // Giữ nguyên hàm getInstance() cũ để tương thích code cũ (mặc định lấy null)
    public static synchronized CameraCapture getInstance() {
        return getInstance(null);
    }



    public synchronized void start(Consumer<byte[]> frameCallback, Consumer<Image> previewCallback) {
        if (webcam == null) {
            log.error("❌ No webcam available");
            return;
        }

        // Nếu đang chạy rồi thì chỉ cập nhật callback (Ví dụ: Chuyển màn hình vẫn giữ camera)
        if (isRunning && webcam.isOpen()) {
            log.info("🔄 Camera already running, updating callbacks");
            this.frameCallback = frameCallback;
            this.previewCallback = previewCallback;
            return;
        }

        this.frameCallback = frameCallback;
        this.previewCallback = previewCallback;

        try {
            // Mở camera (Chế độ async = true để không block UI thread)
            if (!webcam.isOpen()) {
                webcam.open(true);
            }

            isRunning = true;

            // Khởi tạo luồng capture nếu chưa có hoặc đã bị shutdown
            if (executor == null || executor.isShutdown()) {
                executor = Executors.newScheduledThreadPool(1);
            }

            int intervalMs = 1000 / FPS;

            executor.scheduleAtFixedRate(() -> {
                try {
                    if (isRunning && webcam.isOpen()) {
                        BufferedImage image = webcam.getImage();

                        if (image != null) {
                            // 1. Gửi qua mạng (Background Thread)
                            if (this.frameCallback != null) {
                                byte[] frameBytes = encodeFrame(image);
                                this.frameCallback.accept(frameBytes);
                            }

                            // 2. Hiển thị lên UI (JavaFX Thread)
                            if (this.previewCallback != null) {
                                // Convert sang FX Image
                                Image fxImage = SwingFXUtils.toFXImage(image, null);
                                Platform.runLater(() -> {
                                    // Kiểm tra lại callback tránh null pointer nếu vừa stop
                                    if (this.previewCallback != null) {
                                        this.previewCallback.accept(fxImage);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Capture loop error", e);
                }
            }, 0, intervalMs, TimeUnit.MILLISECONDS);

            log.info("✅ Camera capture started - {}x{} @ {}fps", WIDTH, HEIGHT, FPS);

        } catch (WebcamLockException e) {
            log.error("🔒 Camera is LOCKED by another process or instance. Cannot start capture.");
            isRunning = false;
        } catch (Exception e) {
            log.error("❌ Critical error starting camera", e);
            isRunning = false;
        }
    }

    public synchronized void stop() {
        isRunning = false; // Ngắt vòng lặp logic bên trong

        // Dừng Executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow(); // Dừng ngay lập tức
            executor = null; // Gán null để lần sau start() sẽ tạo mới
        }

        // Đóng Webcam
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }

        log.info("🛑 Camera stopped");
    }

    private byte[] encodeFrame(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Encode error", e);
            return new byte[0];
        }
    }

    public boolean isAvailable() {
        return webcam != null;
    }

    public Webcam getWebcam() {
        return webcam;
    }
}