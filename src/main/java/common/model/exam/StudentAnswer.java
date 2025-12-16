package common.model.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAnswer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String answerRecordId;
    private String resultId;
    private String questionId;
    private String studentId;
    
    private String selectedAnswerId;  // Trắc nghiệm
    private String essayAnswer;       // Tự luận
    
    @Builder.Default
    private boolean isCorrect = false;
    
    private double pointsEarned;
    private double maxPoints;
    
    @Builder.Default
    private long answeredAt = System.currentTimeMillis();
    
    private String teacherComment;
}