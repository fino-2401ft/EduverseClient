package common.model.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Violation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String violationId;
    private String examId;
    private String userId;
    private String userName;
    private String violationType;  // no_face, multiple_faces, looking_away, gaze_left, phone_detected, etc.
    private double suspicionScore;
    private String decision;  // OK, WARNING, VIOLATION
    private List<String> flags;
    private long timestamp;
    private String screenshot;  // base64, optional
}

