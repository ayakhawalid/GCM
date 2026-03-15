package common;

import java.io.Serializable;
import java.util.UUID;

/**
 * Universal response wrapper for server-client communication.
 * Contains success/failure status, payload, and error information.
 */
public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Request ID this response corresponds to */
    private final UUID requestId;

    /** Whether the operation succeeded */
    private final boolean ok;

    /** Response payload (DTO specific to the message type) */
    private final Object payload;

    /** Error code for failed operations */
    private final String errorCode;

    /** Human-readable error message */
    private final String errorMessage;

    /** Request message type this response corresponds to */
    private final MessageType requestType;

    /**
     * Creates a successful response.
     */
    private Response(UUID requestId, Object payload, MessageType requestType) {
        this.requestId = requestId;
        this.ok = true;
        this.payload = payload;
        this.errorCode = null;
        this.errorMessage = null;
        this.requestType = requestType;
    }

    /**
     * Creates an error response.
     */
    private Response(UUID requestId, String errorCode, String errorMessage, MessageType requestType) {
        this.requestId = requestId;
        this.ok = false;
        this.payload = null;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.requestType = requestType;
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a successful response with payload.
     */
    public static Response success(UUID requestId, Object payload) {
        return new Response(requestId, payload, null);
    }

    /**
     * Creates a successful response with no payload.
     */
    public static Response success(UUID requestId) {
        return new Response(requestId, null, null);
    }

    /**
     * Creates an error response.
     */
    public static Response error(UUID requestId, String errorCode, String errorMessage) {
        return new Response(requestId, errorCode, errorMessage, null);
    }

    /**
     * Creates an error response from a Request.
     */
    public static Response error(Request request, String errorCode, String errorMessage) {
        return new Response(request.getRequestId(), errorCode, errorMessage, request.getType());
    }

    /**
     * Creates a success response from a Request.
     */
    public static Response success(Request request, Object payload) {
        return new Response(request.getRequestId(), payload, request.getType());
    }

    // ==================== Common Error Codes ====================
    public static final String ERR_NOT_FOUND = "NOT_FOUND";
    public static final String ERR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERR_FORBIDDEN = "FORBIDDEN";
    public static final String ERR_VALIDATION = "VALIDATION_ERROR";
    public static final String ERR_DATABASE = "DATABASE_ERROR";
    public static final String ERR_INTERNAL = "INTERNAL_ERROR";
    public static final String ERR_SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String ERR_ALREADY_LOGGED_IN = "ALREADY_LOGGED_IN";
    public static final String ERR_AUTHENTICATION = "AUTHENTICATION_REQUIRED";

    // Getters
    public UUID getRequestId() {
        return requestId;
    }

    public boolean isOk() {
        return ok;
    }

    public Object getPayload() {
        return payload;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets payload cast to specific type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadAs(Class<T> clazz) {
        return (T) payload;
    }

    @Override
    public String toString() {
        if (ok) {
            return "Response{OK, id=" + requestId.toString().substring(0, 8) + "..., hasPayload=" + (payload != null)
                    + "}";
        } else {
            return "Response{ERROR, id=" + requestId.toString().substring(0, 8) + "..., code=" + errorCode + ", msg="
                    + errorMessage + "}";
        }
    }

    public MessageType getRequestType() {
        return requestType;
    }
}
