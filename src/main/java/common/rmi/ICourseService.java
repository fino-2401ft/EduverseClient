package common.rmi;

import common.model.Course;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ICourseService extends Remote {

    /**
     * Tạo khóa học mới (Teacher)
     */
    Course createCourse(String teacherId, String title, String description)
            throws RemoteException;

    /**
     * Cập nhật khóa học
     */
    boolean updateCourse(Course course)
            throws RemoteException;

    /**
     * Xóa khóa học
     */
    boolean deleteCourse(String courseId)
            throws RemoteException;

    /**
     * Lấy tất cả khóa học
     */
    List<Course> getAllCourses()
            throws RemoteException;

    /**
     * Lấy khóa học theo ID
     */
    Course getCourseById(String courseId)
            throws RemoteException;

    /**
     * Lấy khóa học của giảng viên
     */
    List<Course> getCoursesByTeacher(String teacherId)
            throws RemoteException;

    /**
     * Lấy khóa học mà học viên đã tham gia
     */
    List<Course> getCoursesByStudent(String studentId)
            throws RemoteException;

    /**
     * Học viên tham gia khóa học
     */
    boolean joinCourse(String courseId, String studentId)
            throws RemoteException;

    /**
     * Học viên rời khỏi khóa học
     */
    boolean leaveCourse(String courseId, String studentId)
            throws RemoteException;

    /**
     * Lấy danh sách học viên trong khóa học
     */
    List<String> getStudentIds(String courseId)
            throws RemoteException;
    
    /**
     * Lấy danh sách bài học của khóa học
     */
    List<common.model.Lesson> getLessonsByCourse(String courseId)
            throws RemoteException;
    
    /**
     * Lấy enrollments của một student
     */
    List<common.model.CourseEnrollment> getEnrollmentsByStudent(String studentId)
            throws RemoteException;
    
    /**
     * Lấy enrollment theo courseId và studentId
     */
    common.model.CourseEnrollment getEnrollmentByCourseAndStudent(String courseId, String studentId)
            throws RemoteException;
    
    /**
     * Lấy exam results của một student
     */
    List<common.model.exam.ExamResult> getExamResultsByStudent(String studentId)
            throws RemoteException;
}
