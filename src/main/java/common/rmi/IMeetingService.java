package common.rmi;

import common.model.Meeting;
import common.model.MeetingEnrollment;
import common.model.Peer;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IMeetingService extends Remote {
    Meeting createMeeting(String courseId, String hostId, String title, long scheduledTime) throws RemoteException;
    boolean startMeeting(String meetingId, String hostId) throws RemoteException;
    boolean endMeeting(String meetingId, String hostId) throws RemoteException;

    MeetingEnrollment joinMeeting(String meetingId, String userId, String ipAddress,
                                  int videoPort, int audioPort, int chatPort) throws RemoteException;
    boolean leaveMeeting(String meetingId, String userId) throws RemoteException;

    Meeting getMeetingById(String meetingId) throws RemoteException;
    List<Meeting> getMeetingsByCourse(String courseId) throws RemoteException;
    List<MeetingEnrollment> getParticipants(String meetingId) throws RemoteException;

    boolean updateParticipantStatus(String meetingId, String userId, boolean isMuted,
                                    boolean isCameraOn, boolean isHandRaised) throws RemoteException;

    Peer getHostPeer(String meetingId) throws RemoteException;
    List<Peer> getAllPeers(String meetingId) throws RemoteException;
}