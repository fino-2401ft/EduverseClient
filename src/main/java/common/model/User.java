package common.model;

import common.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String email;
    private String password;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
    private String avatarUrl;
    
    @Builder.Default
    private long createdAt = System.currentTimeMillis();
    private long lastLogin;
    
    @Builder.Default
    private boolean isOnline = false;
    
    @Builder.Default
    private boolean isActive = true;
}