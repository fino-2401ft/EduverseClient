package common.rmi;//package common.rmi;
//
//import java.rmi.Remote;
//import java.rmi.RemoteException;
//import java.util.List;
//
//public interface IP2PSignalingService extends Remote {
//
//    /**
//     * Đăng ký thông tin P2P connection (IP, Port)
//     */
//    boolean registerP2PConnection(String sessionId, String userId, String ipAddress, int videoPort, int audioPort)
//            throws RemoteException;
//
//    /**
//     * Lấy thông tin kết nối P2P của Host
//     */
//    P2PConnection getHostConnection(String sessionId)
//            throws RemoteException;
//
//    /**
//     * Lấy tất cả kết nối P2P trong session
//     */
//    List<P2PConnection> getAllConnections(String sessionId)
//            throws RemoteException;
//
//    /**
//     * Hủy đăng ký P2P connection
//     */
//    boolean unregisterP2PConnection(String sessionId, String userId)
//            throws RemoteException;
//
//    /**
//     * Kiểm tra user có online không
//     */
//    boolean isUserOnline(String userId)
//            throws RemoteException;
//
//    /**
//     * Lấy IP và Port của user cụ thể (cho chat P2P)
//     */
//    P2PConnection getUserConnection(String userId)
//            throws RemoteException;
//
//    /**
//     * Heartbeat - duy trì kết nối
//     */
//    boolean heartbeat(String sessionId, String userId)
//            throws RemoteException;
//}