package common.model;

import lombok.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseEnrollment implements Serializable {
    private static final long serialVersionUID = 1L;

    private String enrollmentId;
    private String studentId;
    private String studentName;
    private String courseId;
    private String courseName;

    @Builder.Default
    private long enrolledAt = System.currentTimeMillis();

    private long completedAt;

    @Builder.Default
    private String status = "ACTIVE";  // ACTIVE, COMPLETED, DROPPED

    @Builder.Default
    private int progress = 0;          // 0-100%

    @Builder.Default
    private Set<String> completedLessonIds = new HashSet<>();

    private int totalLessons;

    @Builder.Default
    private double averageGrade = 0.0;

    // Helper methods
    public void completeLesson(String lessonId) {
        completedLessonIds.add(lessonId);
        updateProgress();
    }

    public void updateProgress() {
        if (totalLessons > 0) {
            this.progress = (completedLessonIds.size() * 100) / totalLessons;
        }
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
}