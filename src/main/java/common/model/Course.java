package common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course implements Serializable {
    private static final long serialVersionUID = 1L;

    private String courseId;
    private String title;
    private String description;
    private String teacherId;
    private String teacherName;
    private String thumbnailUrl;

    @Builder.Default
    private List<String> studentIds = new ArrayList<>();

    @Builder.Default
    private long createdAt = System.currentTimeMillis();

    @Builder.Default
    private long updatedAt = System.currentTimeMillis();

    @Builder.Default
    private boolean isActive = true;

    // Helper methods
    public void addStudent(String studentId) {
        if (!this.studentIds.contains(studentId)) {
            this.studentIds.add(studentId);
        }
    }

    public void removeStudent(String studentId) {
        this.studentIds.remove(studentId);
    }

    public int getStudentCount() {
        return studentIds.size();
    }

    public boolean hasStudent(String studentId) {
        return studentIds.contains(studentId);
    }
}