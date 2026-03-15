package common.dto;

import java.io.Serializable;

/**
 * Request DTO for creating a new support ticket.
 */
public class CreateTicketRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String subject;
    private String message;
    private SupportTicketDTO.Priority priority;

    public CreateTicketRequest() {
        this.priority = SupportTicketDTO.Priority.MEDIUM;
    }

    public CreateTicketRequest(String subject, String message) {
        this.subject = subject;
        this.message = message;
        this.priority = SupportTicketDTO.Priority.MEDIUM;
    }

    public CreateTicketRequest(String subject, String message, SupportTicketDTO.Priority priority) {
        this.subject = subject;
        this.message = message;
        this.priority = priority;
    }

    // Getters and setters
    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SupportTicketDTO.Priority getPriority() {
        return priority;
    }

    public void setPriority(SupportTicketDTO.Priority priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "CreateTicketRequest{subject='" + subject + "', priority=" + priority + "}";
    }
}
