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
public class Answer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String answerId;
    private String questionId;
    
    private String answerText;
    private String answerLabel;    // A, B, C, D
    
    @Builder.Default
    private boolean isCorrect = false;
    
    private int orderIndex;
}