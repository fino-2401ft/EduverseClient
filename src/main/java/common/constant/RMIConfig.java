package common.constant;

//file để lưu các hằng số cấu hình liên quan đến RMI
public class RMIConfig {
    public static final String RMI_HOST = "192.168.100.54";
    public static final int RMI_PORT = 1099;

    public static final String AUTH_SERVICE = "AuthService";
    public static final String COURSE_SERVICE = "CourseService";
    public static final String MEETING_SERVICE = "MeetingService";
    public static final String CHAT_SERVICE = "ChatService";
    public static final String PEER_SERVICE = "PeerService";
    public static final String EXAM_SERVICE = "ExamService";

    public static String getRMIUrl(String serviceName) {
        return "rmi://" + RMI_HOST + ":" + RMI_PORT + "/" + serviceName;
    }
}