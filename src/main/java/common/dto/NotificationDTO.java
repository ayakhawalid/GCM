package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO representing a user notification.
 * Used to notify customers about map updates and editors about approval
 * decisions.
 */
public class NotificationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int userId;
    private String channel; // IN_APP, EMAIL, SMS
    private String title;
    private String body;
    private Timestamp createdAt;
    private Timestamp sentAt;
    private boolean isRead;

    // ==================== Constructors ====================

    public NotificationDTO() {
    }

    public NotificationDTO(int userId, String title, String body) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.channel = "IN_APP";
        this.isRead = false;
    }

    // ==================== Getters and Setters ====================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }

    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    @Override
    public String toString() {
        return "NotificationDTO{" +
                "id=" + id +
                ", userId=" + userId +
                ", title='" + title + '\'' +
                ", isRead=" + isRead +
                '}';
    }
}
