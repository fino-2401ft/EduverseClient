package common.model.exam;

import lombok.*;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamParticipant implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String userName;
    private String examId;
    
    @Builder.Default
    private long joinedAt = System.currentTimeMillis();
    
    private long leftAt;
    
    @Builder.Default
    private boolean isCameraOn = true;
    
    @Builder.Default
    private String status = "ONLINE";  // ONLINE, OFFLINE, SUSPICIOUS
}