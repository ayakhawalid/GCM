package common.dto;

import java.io.Serializable;

/**
 * Request DTO for approve/reject operations.
 * Used as payload for APPROVE_MAP_VERSION and REJECT_MAP_VERSION.
 */
public class ApprovalRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int versionId;
    private String reason; // Required for rejection, optional for approval

    // ==================== Constructors ====================

    public ApprovalRequest() {
    }

    public ApprovalRequest(int versionId) {
        this.versionId = versionId;
    }

    public ApprovalRequest(int versionId, String reason) {
        this.versionId = versionId;
        this.reason = reason;
    }

    // ==================== Static Factory Methods ====================

    public static ApprovalRequest approve(int versionId) {
        return new ApprovalRequest(versionId);
    }

    public static ApprovalRequest reject(int versionId, String reason) {
        return new ApprovalRequest(versionId, reason);
    }

    // ==================== Getters and Setters ====================

    public int getVersionId() {
        return versionId;
    }

    public void setVersionId(int versionId) {
        this.versionId = versionId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "ApprovalRequest{" +
                "versionId=" + versionId +
                ", reason='" + reason + '\'' +
                '}';
    }
}
