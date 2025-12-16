package common.constant;


// File này để lưu các hằng số cấu hình liên quan đến mạng cho việc truyền phát media
public class NetworkConfig {
    // UDP Configuration for Media Streaming
    public static final int UDP_VIDEO_PORT_START = 5000;
    public static final int UDP_VIDEO_PORT_END = 5999;
    
    public static final int UDP_AUDIO_PORT_START = 6000;
    public static final int UDP_AUDIO_PORT_END = 6999;
    
    // Packet Size
    public static final int MAX_PACKET_SIZE = 65507; // Max UDP packet size
    public static final int VIDEO_PACKET_SIZE = 60000;
    public static final int AUDIO_PACKET_SIZE = 8192;
    
    // Frame Rate
    public static final int VIDEO_FPS = 15; // 15 frames per second
    public static final int FRAME_INTERVAL_MS = 1000 / VIDEO_FPS;
    
    // Timeout
    public static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    public static final int READ_TIMEOUT = 5000; // 5 seconds
}