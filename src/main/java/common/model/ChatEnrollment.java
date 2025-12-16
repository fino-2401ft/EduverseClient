package common.model;

import lombok.*;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatEnrollment implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String userName;
    private String conversationId;
    
    @Builder.Default
    private long joinedAt = System.currentTimeMillis();
    
    private long lastSeenAt;
    
    @Builder.Default
    private int unreadCount = 0;
}