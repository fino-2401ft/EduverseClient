package common.model.exam;

import lombok.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String resultId;
    private String examId;
    private String studentId;
    private String studentName;
    
    private long startTime;
    private long submitTime;
    private long duration;
    
    private double totalScore;
    private double maxScore;
    private double percentage;
    
    @Builder.Default
    private String status = "IN_PROGRESS";  // IN_PROGRESS, SUBMITTED, GRADED
    
    @Builder.Default
    private List<StudentAnswer> answers = new ArrayList<>();
    
    private int correctAnswers;
    private int wrongAnswers;
    private int totalQuestions;
    
    private String feedback;
    
    @Builder.Default
    private boolean isPassed = false;
    
    @Builder.Default
    private long createdAt = System.currentTimeMillis();
}