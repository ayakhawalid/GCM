package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO representing a single message in a support ticket conversation.
 */
public class TicketMessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum SenderType {
        CUSTOMER, BOT, AGENT
    }

    private int id;
    private int ticketId;
    private SenderType senderType;
    private Integer senderId;
    private String senderName;
    private String message;
    private Timestamp createdAt;

    public TicketMessageDTO() {
    }

    public TicketMessageDTO(int ticketId, SenderType senderType, Integer senderId, String message) {
        this.ticketId = ticketId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.message = message;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public SenderType getSenderType() {
        return senderType;
    }

    public void setSenderType(SenderType senderType) {
        this.senderType = senderType;
    }

    public Integer getSenderId() {
        return senderId;
    }

    public void setSenderId(Integer senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Get display name for the sender based on type.
     */
    public String getSenderDisplay() {
        switch (senderType) {
            case CUSTOMER:
                return "You";
            case BOT:
                return "🤖 Support Bot";
            case AGENT:
                return "👤 Agent" + (senderName != null ? " " + senderName : "");
            default:
                return senderType.name();
        }
    }

    @Override
    public String toString() {
        return "TicketMessageDTO{ticketId=" + ticketId + ", sender=" + senderType + ", message='" +
                (message.length() > 50 ? message.substring(0, 50) + "..." : message) + "'}";
    }
}
