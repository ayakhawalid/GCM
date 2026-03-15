package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a support ticket.
 * Contains ticket metadata and list of messages.
 */
public class SupportTicketDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        OPEN, BOT_RESPONDED, ESCALATED, CLOSED
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }

    private int id;
    private int userId;
    private String username;
    private String subject;
    private Status status;
    private Priority priority;
    private Integer assignedAgentId;
    private String assignedAgentName;
    private Timestamp createdAt;
    private Timestamp closedAt;
    private List<TicketMessageDTO> messages;

    public SupportTicketDTO() {
        this.messages = new ArrayList<>();
        this.status = Status.OPEN;
        this.priority = Priority.MEDIUM;
    }

    // Getters and setters
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Integer getAssignedAgentId() {
        return assignedAgentId;
    }

    public void setAssignedAgentId(Integer assignedAgentId) {
        this.assignedAgentId = assignedAgentId;
    }

    public String getAssignedAgentName() {
        return assignedAgentName;
    }

    public void setAssignedAgentName(String assignedAgentName) {
        this.assignedAgentName = assignedAgentName;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Timestamp closedAt) {
        this.closedAt = closedAt;
    }

    public List<TicketMessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<TicketMessageDTO> messages) {
        this.messages = messages;
    }

    public void addMessage(TicketMessageDTO message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    /**
     * Get status display string for UI.
     */
    public String getStatusDisplay() {
        switch (status) {
            case OPEN:
                return "🔵 Open";
            case BOT_RESPONDED:
                return "🤖 Bot Responded";
            case ESCALATED:
                return "🟠 Escalated";
            case CLOSED:
                return "✅ Closed";
            default:
                return status.name();
        }
    }

    @Override
    public String toString() {
        return "SupportTicketDTO{id=" + id + ", subject='" + subject + "', status=" + status + "}";
    }
}
