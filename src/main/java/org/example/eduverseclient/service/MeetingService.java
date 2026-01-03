package org.example.eduverseclient.service;


import common.model.Meeting;
import common.model.MeetingEnrollment;
import common.model.Peer;
import common.model.User;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MeetingService {
    private static MeetingService instance;
    private final RMIClient rmiClient;
    
    private MeetingService() {
        this.rmiClient = RMIClient.getInstance();
    }
    
    public static synchronized MeetingService getInstance() {
        if (instance == null) {
            instance = new MeetingService();
        }
        return instance;
    }
    
    /**
     * Tạo meeting mới
     */
    public Meeting createMeeting(String courseId, String title, long scheduledTime) {
        try {
            User currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) {
                log.error("User not logged in");
                return null;
            }
            
            Meeting meeting = rmiClient.getMeetingService().createMeeting(
                courseId,
                currentUser.getUserId(),
                title,
                scheduledTime
            );
            
            log.info("✅ Meeting created: {}", meeting.getMeetingId());
            return meeting;
            
        } catch (Exception e) {
            log.error("❌ Create meeting failed", e);
            return null;
        }
    }
    
    /**
     * Lấy danh sách meetings của course
     */
    public List<Meeting> getMeetingsByCourse(String courseId) {
        try {
            return rmiClient.getMeetingService().getMeetingsByCourse(courseId);
        } catch (Exception e) {
            log.error("❌ Get meetings failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Start meeting (chỉ HOST)
     */
    public boolean startMeeting(String meetingId) {
        try {
            User currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            boolean success = rmiClient.getMeetingService().startMeeting(meetingId, currentUser.getUserId());
            
            if (success) {
                log.info("✅ Meeting started: {}", meetingId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("❌ Start meeting failed", e);
            return false;
        }
    }
    
    /**
     * Join meeting
     */
    public MeetingEnrollment joinMeeting(String meetingId) {
        try {
            User currentUser = rmiClient.getCurrentUser();
            Peer myPeer = rmiClient.getMyPeer();
            
            if (currentUser == null || myPeer == null) {
                log.error("User not logged in or peer not available");
                return null;
            }
            
            MeetingEnrollment enrollment = rmiClient.getMeetingService().joinMeeting(
                meetingId,
                currentUser.getUserId(),
                myPeer.getIpAddress(),
                myPeer.getVideoPort(),
                myPeer.getAudioPort(),
                myPeer.getChatPort()
            );
            
            if (enrollment != null) {
                log.info("✅ Joined meeting: {} as {}", meetingId, enrollment.getRole());
            }
            
            return enrollment;
            
        } catch (Exception e) {
            log.error("❌ Join meeting failed", e);
            return null;
        }
    }
    
    /**
     * Leave meeting
     */
    public boolean leaveMeeting(String meetingId) {
        try {
            User currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            boolean success = rmiClient.getMeetingService().leaveMeeting(meetingId, currentUser.getUserId());
            
            if (success) {
                log.info("✅ Left meeting: {}", meetingId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("❌ Leave meeting failed", e);
            return false;
        }
    }
    
    /**
     * End meeting (chỉ HOST)
     */
    public boolean endMeeting(String meetingId) {
        try {
            User currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            boolean success = rmiClient.getMeetingService().endMeeting(meetingId, currentUser.getUserId());
            
            if (success) {
                log.info("✅ Meeting ended: {}", meetingId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("❌ End meeting failed", e);
            return false;
        }
    }
    
    /**
     * Lấy danh sách participants
     */

    public List<MeetingEnrollment> getParticipants(String meetingId) {
        try {
            return rmiClient.getMeetingService().getParticipants(meetingId);
        } catch (Exception e) {
            // ✨ SỬA: Đừng in lỗi đỏ lòm và đừng return list rỗng nữa.

            // Kiểm tra nếu là lỗi mất kết nối
            if (e instanceof java.rmi.ConnectException || e.getCause() instanceof java.net.ConnectException) {
                // Ném lỗi ra ngoài để Controller bắt được
                throw new RuntimeException("Server connection lost");
            }

            // Nếu là lỗi khác thì log như thường
            log.error("❌ Get participants failed", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Cập nhật trạng thái (mute, camera, hand)
     */
    public boolean updateStatus(String meetingId, boolean isMuted, boolean isCameraOn, boolean isHandRaised) {
        try {
            User currentUser = rmiClient.getCurrentUser();
            if (currentUser == null) return false;
            
            return rmiClient.getMeetingService().updateParticipantStatus(
                meetingId,
                currentUser.getUserId(),
                isMuted,
                isCameraOn,
                isHandRaised
            );
            
        } catch (Exception e) {
            log.error("❌ Update status failed", e);
            return false;
        }
    }
    
    /**
     * Lấy HOST peer (để gửi UDP)
     */
    public Peer getHostPeer(String meetingId) {
        try {
            return rmiClient.getMeetingService().getHostPeer(meetingId);
        } catch (Exception e) {
            log.error("❌ Get host peer failed", e);
            return null;
        }
    }
    
    /**
     * Lấy tất cả peers (để hiển thị video grid)
     */
    public List<Peer> getAllPeers(String meetingId) {
        try {
            return rmiClient.getMeetingService().getAllPeers(meetingId);
        } catch (Exception e) {
            log.error("❌ Get peers failed", e);
            return new ArrayList<>();
        }
    }
}