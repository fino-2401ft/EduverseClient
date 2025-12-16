package org.example.eduverseclient.rmi;


import common.constant.RMIConfig;
import common.model.Peer;
import common.model.User;
import common.rmi.IAuthService;
import common.rmi.IChatService;
import common.rmi.IMeetingService;
import common.rmi.IPeerService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
    private IChatService chatService;
    
    @Getter
    private IPeerService peerService;
    
    @Getter
    private User currentUser;
    
    @Getter
    private Peer myPeer;
    
    private ScheduledExecutorService heartbeatExecutor;
    
    private RMIClient() {
        // Constructor private để Singleton
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
            log.info("🔌 Connecting to RMI Server...");
            
            Registry registry = LocateRegistry.getRegistry(
                RMIConfig.RMI_HOST,
                RMIConfig.RMI_PORT
            );
            
            // Lookup các services
            authService = (IAuthService) registry.lookup(RMIConfig.AUTH_SERVICE);
            meetingService = (IMeetingService) registry.lookup(RMIConfig.MEETING_SERVICE);
            chatService = (IChatService) registry.lookup(RMIConfig.CHAT_SERVICE);
            peerService = (IPeerService) registry.lookup(RMIConfig.PEER_SERVICE);
            
            log.info(" Connected to RMI Server");
            return true;
            
        } catch (Exception e) {
            log.error(" Failed to connect RMI Server", e);
            return false;
        }
    }
    
    /**
     * Login
     */
    public User login(String email, String password) {
        try {
            currentUser = authService.login(email, password);
            
            if (currentUser != null) {
                // Lấy peer info
                myPeer = peerService.getGlobalPeer(currentUser.getUserId());
                
                log.info("✅ Login success: {}", currentUser.getFullName());
                log.info("📡 My Peer: {}:{}", myPeer.getIpAddress(), myPeer.getVideoPort());
                
                // Bắt đầu heartbeat
                startHeartbeat();
            }
            
            return currentUser;
            
        } catch (Exception e) {
            log.error("❌ Login failed", e);
            return null;
        }
    }
    
    /**
     * Logout
     */
    public boolean logout() {
        try {
            if (currentUser != null) {
                authService.logout(currentUser.getUserId());
                
                // Dừng heartbeat
                stopHeartbeat();
                
                currentUser = null;
                myPeer = null;
                
                log.info("✅ Logout success");
                return true;
            }
            return false;
            
        } catch (Exception e) {
            log.error("❌ Logout failed", e);
            return false;
        }
    }
    
    /**
     * Kiểm tra đã login chưa
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }
    
    /**
     * Bắt đầu heartbeat (10s một lần)
     */
    private void startHeartbeat() {
        heartbeatExecutor = Executors.newScheduledThreadPool(1);
        
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (currentUser != null) {
                    peerService.heartbeat(currentUser.getUserId());
                    log.debug("💓 Heartbeat sent");
                }
            } catch (Exception e) {
                log.error("❌ Heartbeat failed", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
        
        log.info("💓 Heartbeat started");
    }
    
    /**
     * Dừng heartbeat
     */
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            log.info("💓 Heartbeat stopped");
        }
    }
    
    /**
     * Shutdown client
     */
    public void shutdown() {
        stopHeartbeat();
        if (currentUser != null) {
            logout();
        }
    }
}