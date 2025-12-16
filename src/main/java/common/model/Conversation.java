package common.model;


import common.enums.ConversationType;
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
public class Conversation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String conversationId;
    private String conversationName;
    private ConversationType conversationType;
    private String relatedId;  // courseId, meetingId, or "userId1_userId2"
    
    @Builder.Default
    private List<String> memberIds = new ArrayList<>();
    
    private String lastMessageText;
    private long lastMessageTime;
    
    @Builder.Default
    private long createdAt = System.currentTimeMillis();
}