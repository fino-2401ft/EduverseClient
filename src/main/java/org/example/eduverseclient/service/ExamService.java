package org.example.eduverseclient.service;

import common.model.Peer;
import common.model.exam.Exam;
import common.model.exam.ExamParticipant;
import common.model.exam.ExamResult;
import common.model.exam.Question;
import common.model.exam.StudentAnswer;
import common.model.exam.Violation;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ExamService {
    private static ExamService instance;
    private final RMIClient rmiClient;
    
    private ExamService() {
        this.rmiClient = RMIClient.getInstance();
    }
    
    public static synchronized ExamService getInstance() {
        if (instance == null) {
            instance = new ExamService();
        }
        return instance;
    }
    
    /**
     * Tạo bài thi mới
     */
    public Exam createExam(String courseId, String title, String description, 
                          int durationMinutes, long scheduledTime) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) {
                log.error("User not logged in");
                return null;
            }
            
            Exam exam = rmiClient.getExamService().createExam(
                courseId,
                currentUser.getUserId(),
                title,
                description,
                durationMinutes,
                scheduledTime
            );
            
            log.info("✅ Exam created: {}", exam != null ? exam.getExamId() : "null");
            return exam;
            
        } catch (Exception e) {
            log.error("❌ Create exam failed", e);
            return null;
        }
    }
    
    /**
     * Thêm câu hỏi vào bài thi
     */
    public boolean addQuestion(String examId, Question question) {
        try {
            return rmiClient.getExamService().addQuestion(examId, question);
        } catch (Exception e) {
            log.error("❌ Add question failed", e);
            return false;
        }
    }
    
    /**
     * Bắt đầu bài thi
     */
    public boolean startExam(String examId) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            boolean success = rmiClient.getExamService().startExam(examId, currentUser.getUserId());
            if (success) {
                log.info("✅ Exam started: {}", examId);
            }
            return success;
            
        } catch (Exception e) {
            log.error("❌ Start exam failed", e);
            return false;
        }
    }
    
    /**
     * Kết thúc bài thi
     */
    public boolean endExam(String examId) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            boolean success = rmiClient.getExamService().endExam(examId, currentUser.getUserId());
            if (success) {
                log.info("✅ Exam ended: {}", examId);
            }
            return success;
            
        } catch (Exception e) {
            log.error("❌ End exam failed", e);
            return false;
        }
    }
    
    /**
     * Tham gia bài thi
     */
    public ExamParticipant joinExam(String examId) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            var myPeer = rmiClient.getMyPeer();
            
            if (currentUser == null || myPeer == null) {
                log.error("User not logged in or peer not available");
                return null;
            }
            
            ExamParticipant participant = rmiClient.getExamService().joinExam(
                examId,
                currentUser.getUserId(),
                myPeer.getIpAddress(),
                myPeer.getVideoPort()
            );
            
            if (participant != null) {
                log.info("✅ Joined exam: {} as {}", examId, participant.getStatus());
            }
            
            return participant;
            
        } catch (Exception e) {
            log.error("❌ Join exam failed", e);
            return null;
        }
    }
    
    /**
     * Rời bài thi
     */
    public boolean leaveExam(String examId) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            boolean success = rmiClient.getExamService().leaveExam(examId, currentUser.getUserId());
            if (success) {
                log.info("✅ Left exam: {}", examId);
            }
            return success;
            
        } catch (Exception e) {
            log.error("❌ Leave exam failed", e);
            return false;
        }
    }
    
    /**
     * Lấy thông tin bài thi
     */
    public Exam getExamById(String examId) {
        try {
            return rmiClient.getExamService().getExamById(examId);
        } catch (Exception e) {
            log.error("❌ Get exam failed", e);
            return null;
        }
    }
    
    /**
     * Lấy danh sách câu hỏi
     */
    public List<Question> getQuestions(String examId) {
        try {
            return rmiClient.getExamService().getQuestions(examId);
        } catch (Exception e) {
            log.error("❌ Get questions failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy danh sách bài thi của course
     */
    public List<Exam> getExamsByCourse(String courseId) {
        try {
            return rmiClient.getExamService().getExamsByCourse(courseId);
        } catch (Exception e) {
            log.error("❌ Get exams by course failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy danh sách participants
     */
    public List<ExamParticipant> getExamParticipants(String examId) {
        try {
            return rmiClient.getExamService().getExamParticipants(examId);
        } catch (Exception e) {
            log.error("❌ Get participants failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Nộp câu trả lời
     */
    public boolean submitAnswer(String examId, StudentAnswer answer) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            answer.setStudentId(currentUser.getUserId());
            return rmiClient.getExamService().submitAnswer(examId, currentUser.getUserId(), answer);
            
        } catch (Exception e) {
            log.error("❌ Submit answer failed", e);
            return false;
        }
    }
    
    /**
     * Nộp bài thi
     */
    public ExamResult submitExam(String examId) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return null;
            
            ExamResult result = rmiClient.getExamService().submitExam(examId, currentUser.getUserId());
            if (result != null) {
                log.info("✅ Exam submitted and graded: Score {}/{}", 
                        result.getTotalScore(), result.getMaxScore());
            }
            return result;
            
        } catch (Exception e) {
            log.error("❌ Submit exam failed", e);
            return null;
        }
    }
    
    /**
     * Lấy kết quả thi
     */
    public ExamResult getExamResult(String examId) {
        try {
            var currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return null;
            
            return rmiClient.getExamService().getExamResult(examId, currentUser.getUserId());
            
        } catch (Exception e) {
            log.error("❌ Get exam result failed", e);
            return null;
        }
    }
    
    /**
     * Lấy tất cả kết quả (cho proctor)
     */
    public List<ExamResult> getAllExamResults(String examId) {
        try {
            return rmiClient.getExamService().getAllExamResults(examId);
        } catch (Exception e) {
            log.error("❌ Get all exam results failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy Proctor peer (cho student)
     */
    public Peer getProctorPeer(String examId) {
        try {
            return rmiClient.getExamService().getProctorPeer(examId);
        } catch (Exception e) {
            log.error("❌ Get proctor peer failed", e);
            return null;
        }
    }
    
    /**
     * Lấy tất cả student peers (cho proctor)
     */
    public List<Peer> getAllStudentPeers(String examId) {
        try {
            return rmiClient.getExamService().getAllStudentPeers(examId);
        } catch (Exception e) {
            log.error("❌ Get student peers failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Báo cáo violation (gian lận)
     */
    public boolean reportViolation(Violation violation) {
        try {
            return rmiClient.getExamService().reportViolation(violation);
        } catch (Exception e) {
            log.error("❌ Report violation failed", e);
            return false;
        }
    }
}

