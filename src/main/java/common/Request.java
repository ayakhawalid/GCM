package common;

import java.io.Serializable;
import java.util.UUID;

/**
 * Universal request wrapper for client-server communication.
 * All GUI actions create Request objects that are sent to the server.
 */
public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique identifier for request-response correlation */
    private final UUID requestId;

    /** Type of operation to perform */
    private final MessageType type;

    /** Request payload (DTO specific to the message type) */
    private final Object payload;

    /** Session token for authenticated requests (null for guest operations) */
    private final String sessionToken;

    /** User ID of the requester (set after authentication) */
    private int userId;

    /**
     * Creates a new request with auto-generated ID.
     * 
     * @param type         The message type
     * @param payload      The request payload
     * @param sessionToken Session token (null for guest/unauthenticated)
     */
    public Request(MessageType type, Object payload, String sessionToken) {
        this.requestId = UUID.randomUUID();
        this.type = type;
        this.payload = payload;
        this.sessionToken = sessionToken;
        this.userId = 0;
    }

    /**
     * Creates a new request with user ID.
     */
    public Request(MessageType type, Object payload, String sessionToken, int userId) {
        this.requestId = UUID.randomUUID();
        this.type = type;
        this.payload = payload;
        this.sessionToken = sessionToken;
        this.userId = userId;
    }

    /**
     * Creates a guest request (no authentication).
     * 
     * @param type    The message type
     * @param payload The request payload
     */
    public Request(MessageType type, Object payload) {
        this(type, payload, null);
    }

    /**
     * Creates a request with no payload.
     * 
     * @param type The message type
     */
    public Request(MessageType type) {
        this(type, null, null);
    }

    // Getters
    public UUID getRequestId() {
        return requestId;
    }

    public MessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    /**
     * Checks if this request has authentication.
     */
    public boolean isAuthenticated() {
        return sessionToken != null && !sessionToken.isEmpty();
    }

    @Override
    public String toString() {
        return "Request{" +
                "id=" + requestId.toString().substring(0, 8) + "..." +
                ", type=" + type +
                ", hasPayload=" + (payload != null) +
                ", authenticated=" + isAuthenticated() +
                '}';
    }
}
