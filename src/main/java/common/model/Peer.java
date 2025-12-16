package common.model;


import common.enums.MeetingRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Peer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String userName;
    private String sessionId;      // meetingId, examId
    private String sessionType;    // MEETING, EXAM
    
    private MeetingRole role;
    
    private String ipAddress;
    private int videoPort;
    private int audioPort;
    private int chatPort;
    
    @Builder.Default
    private boolean isConnected = false;
    
    @Builder.Default
    private long connectedAt = System.currentTimeMillis();
    
    private long lastHeartbeat;
    
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    public boolean isAlive(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat < timeoutMs;
    }
    
    public boolean isHost() {
        return role == MeetingRole.HOST;
    }
}