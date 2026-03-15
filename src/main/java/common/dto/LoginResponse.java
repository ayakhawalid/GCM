package common.dto;

import java.io.Serializable;

/**
 * Login response DTO containing session info and user details.
 */
public class LoginResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionToken;
    private int userId;
    private String username;
    private String role;
    private boolean isSubscribed;

    public LoginResponse() {
    }

    public LoginResponse(String sessionToken, int userId, String username,
            String role, boolean isSubscribed) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.isSubscribed = isSubscribed;
    }

    // Getters and Setters
    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isSubscribed() {
        return isSubscribed;
    }

    public void setSubscribed(boolean isSubscribed) {
        this.isSubscribed = isSubscribed;
    }

    @Override
    public String toString() {
        return "LoginResponse{userId=" + userId + ", username='" + username +
                "', role='" + role + "', subscribed=" + isSubscribed + "}";
    }
}
