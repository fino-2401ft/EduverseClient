package org.example.eduverseclient;

import common.constant.RMIConfig;
import common.model.Peer;
import common.model.User;
import common.rmi.IAuthService;
import common.rmi.IChatService;
import common.rmi.ICourseService;
import common.rmi.IExamService;
import common.rmi.IMeetingService;
import common.rmi.IPeerService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.utils.NetworkUtil;


import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RMIClient {
    private static RMIClient instance;

    @Getter
    private IAuthService authService;

    @Getter
    private IMeetingService meetingService;

    @Getter
    private ICourseService courseService;

//    @Getter
//    private IChatService chatService;
    @Getter
    private IChatService chatService;

    @Getter
    private IPeerService peerService;

    @Getter
    private IExamService examService;

    @Getter
    private User currentUser;

    @Getter
    private Peer myPeer;

    @Getter
    private String myIPAddress; // Client's detected IP

    private ScheduledExecutorService heartbeatExecutor;

    // RMI Server config
    private static final String RMI_HOST = "192.168.100.54"; // TODO: Load from config
    private static final int RMI_PORT = 1099;

    private RMIClient() {
        // Private constructor for Singleton
    }

    public static synchronized RMIClient getInstance() {
        if (instance == null) {
            instance = new RMIClient();
        }
        return instance;
    }

    /**
     * Kết nối đến RMI Server
     */
    public boolean connect() {
        try {
            log.info("🔌 Connecting to RMI Server at {}:{}...", RMI_HOST, RMI_PORT);

            Registry registry = LocateRegistry.getRegistry(RMI_HOST, RMI_PORT);

            // Lookup services
            authService = (IAuthService) registry.lookup(RMIConfig.AUTH_SERVICE);
            meetingService = (IMeetingService) registry.lookup(RMIConfig.MEETING_SERVICE);
            courseService = (ICourseService) registry.lookup(RMIConfig.COURSE_SERVICE);
            chatService = (IChatService) registry.lookup(RMIConfig.CHAT_SERVICE);
            peerService = (IPeerService) registry.lookup(RMIConfig.PEER_SERVICE);
            examService = (IExamService) registry.lookup(RMIConfig.EXAM_SERVICE);

            log.info("✅ Connected to RMI Server");
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to connect to RMI Server", e);
            return false;
        }
    }

    /**
     * 🔥 AUTO-DETECT IP ADDRESS
     */
    private void detectMyIPAddress() {
        // Try to detect IP (prefer LAN IP)
        myIPAddress = NetworkUtil.getLocalIPAddress();

        log.info("📍 Detected my IP address: {}", myIPAddress);

        // If localhost, try public IP (for WAN)
        if (myIPAddress.equals("127.0.0.1") || myIPAddress.equals("localhost")) {
            log.warn("⚠️ Detected localhost, trying public IP...");

            String publicIP = NetworkUtil.getPublicIPAddress();
            if (publicIP != null && !publicIP.isEmpty()) {
                myIPAddress = publicIP;
                log.info("📍 Using public IP: {}", myIPAddress);
            } else {
                log.warn("⚠️ Failed to get public IP, using localhost");
            }
        }
    }

    /**
     * Login with auto IP detection
     */
    public User login(String email, String password) {
        try {
            // 1. Detect my IP address
            detectMyIPAddress();

            log.info("🔐 Logging in as {} from IP: {}", email, myIPAddress);

            // 2. Call login with detected IP
            currentUser = authService.login(email, password, myIPAddress);

            if (currentUser != null) {
                log.info("✅ Login success: {}", currentUser.getFullName());

                // 3. Get my peer info from server
                myPeer = peerService.getGlobalPeer(currentUser.getUserId());

                if (myPeer != null) {
                    log.info("📡 My Peer Info:");
                    log.info("   - IP: {}", myPeer.getIpAddress());
                    log.info("   - Video Port: {}", myPeer.getVideoPort());
                    log.info("   - Audio Port: {}", myPeer.getAudioPort());
                    log.info("   - Chat Port: {}", myPeer.getChatPort());

                    // 4. Start heartbeat
                    startHeartbeat();
                } else {
                    log.error("❌ Failed to get peer info");
                }
            }

            return currentUser;

        } catch (Exception e) {
            log.error("❌ Login failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Logout
     */
    public boolean logout() {
        try {
            if (currentUser == null) {
                log.warn("⚠️ No user logged in");
                return false;
            }

            log.info("👋 Logging out: {}", currentUser.getUserId());

            // Stop heartbeat
            stopHeartbeat();

            // Call server logout
            boolean success = authService.logout(currentUser.getUserId());

            if (success) {
                currentUser = null;
                myPeer = null;
                log.info("✅ Logout success");
            }

            return success;

        } catch (Exception e) {
            log.error("❌ Logout failed", e);
            return false;
        }
    }

    /**
     * Start heartbeat (gửi tín hiệu sống mỗi 10 giây)
     */
    private void startHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            log.warn("⚠️ Heartbeat already running");
            return;
        }

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (currentUser != null) {
                    boolean alive = peerService.heartbeat(currentUser.getUserId());
                    if (!alive) log.warn("⚠️ Server reported heartbeat failed");
                }
            } catch (Exception e) {
                // ✨ SỬA: Bắt lỗi kết nối im lặng hơn
                // Nếu lỗi là ConnectException (Server tắt), chỉ log warn 1 dòng ngắn gọn
                if (e instanceof java.rmi.ConnectException || e.getCause() instanceof java.net.ConnectException) {
                    log.warn("⚠️ Server unreachable (Heartbeat skipped)");
                } else {
                    log.error("❌ Heartbeat error", e);
                }
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

    /**
     * Stop heartbeat
     */
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
            }
            log.info("💔 Heartbeat stopped");
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return authService != null && meetingService != null && courseService != null && examService != null;
    }

    /**
     * Check if logged in
     */
    public boolean isLoggedIn() {
        return currentUser != null && myPeer != null;
    }

    /**
     * Get connection status info
     */
    public String getConnectionInfo() {
        if (!isConnected()) {
            return "❌ Not connected to server";
        }

        if (!isLoggedIn()) {
            return "⚠️ Connected but not logged in";
        }

        return String.format("✅ Connected as %s (%s) - IP: %s",
                currentUser.getFullName(),
                currentUser.getRole(),
                myPeer.getIpAddress()
        );
    }

    public void shutdown() {
        try {
            log.info("🛑 Shutting down RMI Client...");

            // 1. Logout if logged in
            if (isLoggedIn()) {
                log.info("👋 Logging out current user: {}", currentUser.getUserId());
                logout();
            }

            // 2. Stop heartbeat
            stopHeartbeat();

            // 3. Clear references
            currentUser = null;
            myPeer = null;
            myIPAddress = null;

            authService = null;
            meetingService = null;
            courseService = null;
          //  chatService = null;
            peerService = null;
            examService = null;

            log.info("✅ RMI Client shutdown complete");

        } catch (Exception e) {
            log.error("❌ Error during shutdown", e);
        }
    }



}