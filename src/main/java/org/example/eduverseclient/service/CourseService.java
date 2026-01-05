package org.example.eduverseclient.service;

import common.model.Course;
import common.model.CourseEnrollment;
import common.model.Lesson;
import common.model.exam.ExamResult;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CourseService {
    private static CourseService instance;
    private final RMIClient rmiClient;
    
    private CourseService() {
        this.rmiClient = RMIClient.getInstance();
    }
    
    public static synchronized CourseService getInstance() {
        if (instance == null) {
            instance = new CourseService();
        }
        return instance;
    }
    
    /**
     * Lấy tất cả khóa học
     */
    public List<Course> getAllCourses() {
        try {
            if (rmiClient.getCourseService() == null) {
                log.warn("CourseService not available");
                return new ArrayList<>();
            }
            return rmiClient.getCourseService().getAllCourses();
        } catch (Exception e) {
            log.error("❌ Get all courses failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy khóa học theo ID
     */
    public Course getCourseById(String courseId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return null;
            }
            return rmiClient.getCourseService().getCourseById(courseId);
        } catch (Exception e) {
            log.error("❌ Get course by ID failed", e);
            return null;
        }
    }
    
    /**
     * Lấy khóa học của giảng viên
     */
    public List<Course> getCoursesByTeacher(String teacherId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return new ArrayList<>();
            }
            return rmiClient.getCourseService().getCoursesByTeacher(teacherId);
        } catch (Exception e) {
            log.error("❌ Get courses by teacher failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy khóa học mà học viên đã tham gia
     */
    public List<Course> getCoursesByStudent(String studentId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return new ArrayList<>();
            }
            return rmiClient.getCourseService().getCoursesByStudent(studentId);
        } catch (Exception e) {
            log.error("❌ Get courses by student failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Học viên tham gia khóa học
     */
    public boolean joinCourse(String courseId, String studentId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return false;
            }
            boolean success = rmiClient.getCourseService().joinCourse(courseId, studentId);
            if (success) {
                log.info("✅ Joined course: {}", courseId);
            }
            return success;
        } catch (Exception e) {
            log.error("❌ Join course failed", e);
            return false;
        }
    }
    
    /**
     * Học viên rời khỏi khóa học
     */
    public boolean leaveCourse(String courseId, String studentId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return false;
            }
            boolean success = rmiClient.getCourseService().leaveCourse(courseId, studentId);
            if (success) {
                log.info("✅ Left course: {}", courseId);
            }
            return success;
        } catch (Exception e) {
            log.error("❌ Leave course failed", e);
            return false;
        }
    }
    
    /**
     * Lấy danh sách bài học của khóa học
     */
    public List<Lesson> getLessonsByCourse(String courseId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return new ArrayList<>();
            }
            return rmiClient.getCourseService().getLessonsByCourse(courseId);
        } catch (Exception e) {
            log.error("❌ Get lessons by course failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy enrollments của một student
     */
    public List<CourseEnrollment> getEnrollmentsByStudent(String studentId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return new ArrayList<>();
            }
            return rmiClient.getCourseService().getEnrollmentsByStudent(studentId);
        } catch (Exception e) {
            log.error("❌ Get enrollments by student failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy enrollment theo courseId và studentId
     */
    public CourseEnrollment getEnrollmentByCourseAndStudent(String courseId, String studentId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return null;
            }
            return rmiClient.getCourseService().getEnrollmentByCourseAndStudent(courseId, studentId);
        } catch (Exception e) {
            log.error("❌ Get enrollment by course and student failed", e);
            return null;
        }
    }
    
    /**
     * Lấy exam results của một student
     */
    public List<ExamResult> getExamResultsByStudent(String studentId) {
        try {
            if (rmiClient.getCourseService() == null) {
                return new ArrayList<>();
            }
            return rmiClient.getCourseService().getExamResultsByStudent(studentId);
        } catch (Exception e) {
            log.error("❌ Get exam results by student failed", e);
            return new ArrayList<>();
        }
    }
}

