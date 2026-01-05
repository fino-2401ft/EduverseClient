package org.example.eduverseclient.service;

import common.model.User;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class UserService {
    private static UserService instance;
    private final RMIClient rmiClient;
    
    private UserService() {
        this.rmiClient = RMIClient.getInstance();
    }
    
    public static synchronized UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }
        return instance;
    }
    
    /**
     * Lấy danh sách users theo role
     */
    public List<User> getUsersByRole(String role) {
        try {
            if (rmiClient.getAuthService() == null) {
                return new ArrayList<>();
            }
            return rmiClient.getAuthService().getUsersByRole(role);
        } catch (Exception e) {
            log.error("❌ Get users by role failed", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy danh sách teachers
     */
    public List<User> getTeachers() {
        return getUsersByRole("TEACHER");
    }
}

