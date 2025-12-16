package common.rmi;


import common.model.Meeting;
import common.model.Message;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Client callback interface - Server gọi để push notification
 */
public interface IClientCallback extends Remote {
    void onMeetingStarted(Meeting meeting) throws RemoteException;
    void onMeetingEnded(String meetingId) throws RemoteException;
    void onUserJoinedMeeting(String meetingId, String userId, String userName) throws RemoteException;
    void onUserLeftMeeting(String meetingId, String userId, String userName) throws RemoteException;
    void onNewMessage(Message message) throws RemoteException;
}