package common.model;


import common.enums.MeetingRole;
import common.enums.ParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingEnrollment implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String userName;
    private String avatarUrl;
    private String meetingId;
    
    private MeetingRole role;
    
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.ONLINE;
    
    @Builder.Default
    private long joinedAt = System.currentTimeMillis();
    private long leftAt;
    
    @Builder.Default
    private boolean isMuted = false;
    
    @Builder.Default
    private boolean isCameraOn = true;
    
    @Builder.Default
    private boolean isHandRaised = false;

    public boolean isHost() {
        return this.role == MeetingRole.HOST;
    }
}