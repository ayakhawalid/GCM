package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO representing a map version for the approval workflow.
 * Contains version status, metadata, and approval information.
 */
public class MapVersionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int mapId;
    private String mapName;
    private int cityId;
    private String cityName;
    private int versionNumber;
    private String status; // DRAFT, PENDING, APPROVED, REJECTED
    private String descriptionText;
    private int createdBy;
    private String createdByUsername;
    private Timestamp createdAt;
    private Integer approvedBy;
    private String approvedByUsername;
    private Timestamp approvedAt;
    private String rejectionReason;

    // ==================== Constructors ====================

    public MapVersionDTO() {
    }

    public MapVersionDTO(int id, int mapId, int versionNumber, String status) {
        this.id = id;
        this.mapId = mapId;
        this.versionNumber = versionNumber;
        this.status = status;
    }

    // ==================== Getters and Setters ====================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public int getCityId() {
        return cityId;
    }

    public void setCityId(int cityId) {
        this.cityId = cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public void setDescriptionText(String descriptionText) {
        this.descriptionText = descriptionText;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
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

    public Timestamp getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Timestamp approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    @Override
    public String toString() {
        return "MapVersionDTO{" +
                "id=" + id +
                ", mapName='" + mapName + '\'' +
                ", cityName='" + cityName + '\'' +
                ", versionNumber=" + versionNumber +
                ", status='" + status + '\'' +
                '}';
    }
}
