package common.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String conversationId;
    private String type; //private, courseChat

    @Builder.Default
    private List<String> participants = new ArrayList<>();

    private String lastMessageId;
    private long lastUpdate;

}
