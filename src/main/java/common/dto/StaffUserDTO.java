package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO for staff/employee user data (non-customer roles).
 * Used by the User Management screen (COMPANY_MANAGER only).
 */
public class StaffUserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String username;
    private String email;
    private String role;
    private Timestamp createdAt;
    private boolean active;

    public StaffUserDTO() {
    }

    public StaffUserDTO(int userId, String username, String email, String role,
                        Timestamp createdAt, boolean active) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.active = active;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "StaffUserDTO{userId=" + userId + ", username='" + username +
               "', role='" + role + "'}";
    }
}
