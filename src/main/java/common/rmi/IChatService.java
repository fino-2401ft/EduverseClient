package common.rmi;


import common.enums.MessageType;
import common.model.Conversation;
import common.model.Message;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IChatService extends Remote {
    Message sendMessage(String conversationId, String senderId, String textContent) throws RemoteException;
    Message sendFileMessage(String conversationId, String senderId, MessageType type, String fileUrl) throws RemoteException;
    List<Message> getMessages(String conversationId, int limit) throws RemoteException;
    List<Message> getMessagesAfter(String conversationId, long timestamp) throws RemoteException;

    Conversation createPrivateConversation(String userId1, String userId2) throws RemoteException;
    Conversation getCourseConversation(String courseId) throws RemoteException;
    Conversation getMeetingConversation(String meetingId) throws RemoteException;

    List<Conversation> getUserConversations(String userId) throws RemoteException;

    boolean markAsRead(String conversationId, String userId) throws RemoteException;
}