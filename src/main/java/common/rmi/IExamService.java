//package common.rmi;
//
//import common.model.exam.Exam;
//
//import java.rmi.Remote;
//import java.rmi.RemoteException;
//import java.util.List;
//
//public interface IExamService extends Remote {
//
//    /**
//     * Tạo bài thi mới (Teacher)
//     */
//    Exam createExam(String courseId, String proctorId, String title, int durationMinutes, long scheduledTime)
//            throws RemoteException;
//
//    /**
//     * Bắt đầu bài thi
//     */
//    boolean startExam(String examId, String proctorId)
//            throws RemoteException;
//
//    /**
//     * Kết thúc bài thi
//     */
//    boolean endExam(String examId, String proctorId)
//            throws RemoteException;
//
//    /**
//     * Học viên tham gia thi
//     */
//    Participant joinExam(String examId, String studentId, String ipAddress, int videoPort)
//            throws RemoteException;
//
//    /**
//     * Học viên rời phòng thi
//     */
//    boolean leaveExam(String examId, String studentId)
//            throws RemoteException;
//
//    /**
//     * Lấy thông tin bài thi
//     */
//    Exam getExamById(String examId)
//            throws RemoteException;
//
//    /**
//     * Lấy danh sách bài thi của khóa học
//     */
//    List<Exam> getExamsByCourse(String courseId)
//            throws RemoteException;
//
//    /**
//     * Lấy danh sách thí sinh trong phòng thi
//     */
//    List<Participant> getExamParticipants(String examId)
//            throws RemoteException;
//}