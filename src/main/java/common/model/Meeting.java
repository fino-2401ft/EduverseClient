package common.model;


import common.enums.MeetingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meeting implements Serializable {
    private static final long serialVersionUID = 1L;

    private String meetingId;
    private String courseId;
    private String hostId;
    private String hostName;
    private String title;
    private String description;

    private long scheduledTime;
    private long startTime;
    private long endTime;

    @Builder.Default
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Builder.Default
    private Map<String, MeetingEnrollment> participants = new HashMap<>();

    @Builder.Default
    private int maxParticipants = 50;
}