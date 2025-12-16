package common.model.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam implements Serializable {
    private static final long serialVersionUID = 1L;

    private String examId;
    private String courseId;
    private String courseName;

    private String proctorId;      // Giám thị
    private String proctorName;

    private String title;
    private String description;

    private long scheduledTime;
    private long startTime;
    private long endTime;

    private int durationMinutes;   // Thời gian làm bài

    @Builder.Default
    private String status = "SCHEDULED";  // SCHEDULED, IN_PROGRESS, ENDED

    @Builder.Default
    private Map<String, ExamParticipant> participants = new HashMap<>();

    @Builder.Default
    private int maxParticipants = 100;

    @Builder.Default
    private long createdAt = System.currentTimeMillis();
}