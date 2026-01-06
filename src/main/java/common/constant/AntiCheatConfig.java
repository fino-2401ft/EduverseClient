package common.constant;

/**
 * Configuration for AI Anti-Cheating Service
 * 
 * IMPORTANT: If Python AI service runs on a different machine than the client,
 * change AI_SERVICE_URL to that machine's IP address.
 * 
 * Example:
 * - If Python service runs on 192.168.100.54: "http://192.168.100.54:8000/analyze/frame"
 * - If Python service runs on same machine: "http://127.0.0.1:8000/analyze/frame"
 */
public class AntiCheatConfig {
    // AI Service URL
    // IP của máy chạy Python AI service
    public static final String AI_SERVICE_URL = "http://192.168.100.54:8000/analyze/frame";
    
    // Analysis interval (1 FPS = 1000ms)
    public static final long ANALYSIS_INTERVAL_MS = 1000;
    
    // Violation threshold (0.0 - 1.0)
    public static final double VIOLATION_THRESHOLD = 0.70;
}

