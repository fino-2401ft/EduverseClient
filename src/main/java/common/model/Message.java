package common.model;


import common.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String conversationId;
    private String senderId;

    private MessageType type;
    private String content;

    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    @Builder.Default
    private boolean seen = false;
}