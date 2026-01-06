package common.rmi;

import common.model.Peer;
import common.model.exam.Exam;
import common.model.exam.ExamParticipant;
import common.model.exam.ExamResult;
import common.model.exam.Question;
import common.model.exam.StudentAnswer;
import common.model.exam.Violation;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IExamService extends Remote {

    /**
     * Tạo bài thi mới (Proctor/Teacher)
     */
    Exam createExam(String courseId, String proctorId, String title, String description, 
                    int durationMinutes, long scheduledTime) throws RemoteException;

    /**
     * Thêm câu hỏi vào bài thi
     */
    boolean addQuestion(String examId, Question question) throws RemoteException;

    /**
     * Bắt đầu bài thi
     */
    boolean startExam(String examId, String proctorId) throws RemoteException;

    /**
     * Kết thúc bài thi
     */
    boolean endExam(String examId, String proctorId) throws RemoteException;

    /**
     * Học viên tham gia thi
     */
    ExamParticipant joinExam(String examId, String studentId, String ipAddress, int videoPort) 
            throws RemoteException;

    /**
     * Học viên rời phòng thi
     */
    boolean leaveExam(String examId, String studentId) throws RemoteException;

    /**
     * Lấy thông tin bài thi
     */
    Exam getExamById(String examId) throws RemoteException;

    /**
     * Lấy danh sách câu hỏi của bài thi
     */
    List<Question> getQuestions(String examId) throws RemoteException;

    /**
     * Lấy danh sách bài thi của khóa học
     */
    List<Exam> getExamsByCourse(String courseId) throws RemoteException;

    /**
     * Lấy danh sách thí sinh trong phòng thi
     */
    List<ExamParticipant> getExamParticipants(String examId) throws RemoteException;

    /**
     * Student nộp câu trả lời
     */
    boolean submitAnswer(String examId, String studentId, StudentAnswer answer) throws RemoteException;

    /**
     * Student nộp toàn bộ bài thi
     */
    ExamResult submitExam(String examId, String studentId) throws RemoteException;

    /**
     * Chấm điểm tự động (trắc nghiệm)
     */
    ExamResult gradeExam(String examId, String studentId) throws RemoteException;

    /**
     * Lấy kết quả thi
     */
    ExamResult getExamResult(String examId, String studentId) throws RemoteException;

    /**
     * Lấy tất cả kết quả của bài thi (cho proctor)
     */
    List<ExamResult> getAllExamResults(String examId) throws RemoteException;

    /**
     * Lấy Proctor peer (cho student để nhận video)
     */
    Peer getProctorPeer(String examId) throws RemoteException;

    /**
     * Lấy tất cả student peers (cho proctor để broadcast)
     */
    List<Peer> getAllStudentPeers(String examId) throws RemoteException;

    /**
     * Báo cáo violation (gian lận)
     */
    boolean reportViolation(Violation violation) throws RemoteException;

    /**
     * Lấy violations gần đây của exam (cho proctor)
     */
    List<Violation> getRecentViolations(String examId, long sinceTimestamp) throws RemoteException;

    /**
     * Lấy tất cả violations của exam (cho proctor)
     */
    List<Violation> getViolationsByExam(String examId) throws RemoteException;
}
