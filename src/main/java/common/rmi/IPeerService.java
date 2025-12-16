package common.rmi;


import common.model.Peer;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IPeerService extends Remote {
    boolean registerGlobalPeer(String userId, String ipAddress, int videoPort, 
                               int audioPort, int chatPort) throws RemoteException;
    boolean unregisterGlobalPeer(String userId) throws RemoteException;
    
    Peer getGlobalPeer(String userId) throws RemoteException;
    boolean isUserOnline(String userId) throws RemoteException;
    
    boolean heartbeat(String userId) throws RemoteException;
}