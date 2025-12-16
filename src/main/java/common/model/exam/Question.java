package common.model.exam;

import lombok.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String questionId;
    private String examId;
    
    private String questionText;
    private String questionType;   // MULTIPLE_CHOICE, TRUE_FALSE, ESSAY
    
    private String imageUrl;       // Hình minh họa
    
    @Builder.Default
    private List<Answer> answers = new ArrayList<>();
    
    private String correctAnswerId;  // Đáp án đúng (với trắc nghiệm)
    
    private double points;
    private int orderIndex;
    
    @Builder.Default
    private String difficulty = "MEDIUM";  // EASY, MEDIUM, HARD
    
    @Builder.Default
    private long createdAt = System.currentTimeMillis();
}