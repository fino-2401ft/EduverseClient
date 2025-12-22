package org.example.eduverseclient.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Enumeration;

@Slf4j
public class NetworkUtil {

    /**
     * Lấy IP address của máy (ưu tiên LAN private IP)
     * Nếu trên cùng mạng LAN → dùng 192.168.x.x
     * Nếu khác mạng → cần public IP (hoặc NAT/Port forwarding)
     */
    public static String getLocalIPAddress() {
        try {
            // 1. Thử lấy IP từ network interfaces (LAN)
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                
                // Skip loopback và interface không active
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // Chỉ lấy IPv4 (bỏ IPv6)
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        
                        // Ưu tiên IP private (LAN)
                        if (ip.startsWith("192.168.") || 
                            ip.startsWith("10.") || 
                            ip.startsWith("172.")) {
                            log.info("✅ Detected LAN IP: {}", ip);
                            return ip;
                        }
                    }
                }
            }
            
            // 2. Fallback: Thử connect đến Google DNS để lấy local IP
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("8.8.8.8", 80), 1000);
                String ip = socket.getLocalAddress().getHostAddress();
                log.info("✅ Detected IP via socket: {}", ip);
                return ip;
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to detect IP", e);
        }
        
        // 3. Fallback cuối cùng
        log.warn("⚠️ Using fallback IP: localhost");
        return "127.0.0.1";
    }

    /**
     * Lấy Public IP (nếu cần kết nối WAN)
     * Dùng service bên ngoài để check
     */
    public static String getPublicIPAddress() {
        try {
            URL url = new URL("https://api.ipify.org");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String publicIP = in.readLine();
            in.close();
            
            log.info("✅ Public IP: {}", publicIP);
            return publicIP;
            
        } catch (Exception e) {
            log.error("❌ Failed to get public IP", e);
            return null;
        }
    }

    /**
     * Kiểm tra xem 2 IP có cùng subnet không (LAN)
     */
    public static boolean isSameSubnet(String ip1, String ip2) {
        if (ip1 == null || ip2 == null) return false;
        
        String[] parts1 = ip1.split("\\.");
        String[] parts2 = ip2.split("\\.");
        
        if (parts1.length != 4 || parts2.length != 4) return false;
        
        // So sánh 3 octet đầu (Class C subnet)
        return parts1[0].equals(parts2[0]) &&
               parts1[1].equals(parts2[1]) &&
               parts1[2].equals(parts2[2]);
    }

    /**
     * Check xem port có available không
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Test UDP connection đến target
     */
    public static boolean testUDPConnection(String targetIP, int targetPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2000); // 2 seconds timeout
            
            // Send test packet
            byte[] testData = "PING".getBytes();
            DatagramPacket packet = new DatagramPacket(
                testData, 
                testData.length, 
                InetAddress.getByName(targetIP), 
                targetPort
            );
            socket.send(packet);
            
            log.info("✅ Sent UDP test packet to {}:{}", targetIP, targetPort);
            return true;
            
        } catch (Exception e) {
            log.error("❌ UDP connection test failed to {}:{}", targetIP, targetPort);
            return false;
        }
    }
}