package common.dto;

import java.io.Serializable;

/**
 * Request payload for approving or rejecting a pricing request.
 * Sent by CompanyManager when processing a pending request.
 */
public class ApprovePricingRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int requestId;
    private String reason; // For rejection, the reason why

    // Default constructor
    public ApprovePricingRequest() {
    }

    // Constructor for approval (no reason needed)
    public ApprovePricingRequest(int requestId) {
        this.requestId = requestId;
    }

    // Constructor for rejection (reason required)
    public ApprovePricingRequest(int requestId, String reason) {
        this.requestId = requestId;
        this.reason = reason;
    }

    // Getters and Setters
    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return String.format("ApprovePricingRequest[requestId=%d, reason=%s]",
                requestId, reason);
    }
}
