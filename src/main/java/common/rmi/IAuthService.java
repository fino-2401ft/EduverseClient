package common.rmi;

import common.model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IAuthService extends Remote {
    User login(String email, String password, String clientIP) throws RemoteException;

    boolean logout(String userId) throws RemoteException;
    User register(String email, String password, String fullName, String role) throws RemoteException;
    boolean isEmailExists(String email) throws RemoteException;
    User getUserById(String userId) throws RemoteException;
}