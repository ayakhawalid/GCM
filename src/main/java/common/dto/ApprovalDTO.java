package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO representing an approval record.
 * Used for tracking approval workflow status.
 */
public class ApprovalDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String entityType; // MAP_VERSION, PRICING_REQUEST
    private int entityId;
    private String status; // PENDING, APPROVED, REJECTED
    private String reason;
    private Integer approvedBy;
    private String approvedByUsername;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // ==================== Constructors ====================

    public ApprovalDTO() {
    }

    public ApprovalDTO(int id, String entityType, int entityId, String status) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.status = status;
    }

    // ==================== Getters and Setters ====================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Integer approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovedByUsername() {
        return approvedByUsername;
    }

    public void setApprovedByUsername(String approvedByUsername) {
        this.approvedByUsername = approvedByUsername;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ApprovalDTO{" +
                "id=" + id +
                ", entityType='" + entityType + '\'' +
                ", entityId=" + entityId +
                ", status='" + status + '\'' +
                '}';
    }
}
