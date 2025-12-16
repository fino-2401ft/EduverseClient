package common.model;


import common.enums.MessageType;
import lombok.*;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String messageId;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    
    private MessageType type;
    private String textContent;
    
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    
    @Builder.Default
    private boolean isRead = false;
}