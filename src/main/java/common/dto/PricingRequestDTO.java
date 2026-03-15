package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Data Transfer Object for pricing requests.
 * Used to transfer pricing request data between client and server.
 */
public class PricingRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int cityId;
    private String cityName;
    private double currentPrice;
    private double proposedPrice;
    private String status; // PENDING, APPROVED, REJECTED
    private String reason; // Justification for price change
    private String rejectionReason;
    private int createdBy;
    private String createdByName;
    private Timestamp createdAt;
    private Integer approvedBy;
    private String approvedByName;
    private Timestamp processedAt;

    // Default constructor
    public PricingRequestDTO() {
    }

    // Full constructor
    public PricingRequestDTO(int id, int cityId, String cityName, double currentPrice,
            double proposedPrice, String status, String reason,
            String rejectionReason, int createdBy, String createdByName,
            Timestamp createdAt, Integer approvedBy, String approvedByName,
            Timestamp processedAt) {
        this.id = id;
        this.cityId = cityId;
        this.cityName = cityName;
        this.currentPrice = currentPrice;
        this.proposedPrice = proposedPrice;
        this.status = status;
        this.reason = reason;
        this.rejectionReason = rejectionReason;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
        this.approvedBy = approvedBy;
        this.approvedByName = approvedByName;
        this.processedAt = processedAt;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getProposedPrice() {
        return proposedPrice;
    }

    public void setProposedPrice(double proposedPrice) {
        this.proposedPrice = proposedPrice;
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

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
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

    public String getApprovedByName() {
        return approvedByName;
    }

    public void setApprovedByName(String approvedByName) {
        this.approvedByName = approvedByName;
    }

    public Timestamp getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Timestamp processedAt) {
        this.processedAt = processedAt;
    }

    /**
     * Calculate price change percentage.
     */
    public double getPriceChangePercent() {
        if (currentPrice == 0)
            return 0;
        return ((proposedPrice - currentPrice) / currentPrice) * 100;
    }

    /**
     * Check if this is a price increase.
     */
    public boolean isPriceIncrease() {
        return proposedPrice > currentPrice;
    }

    @Override
    public String toString() {
        return String.format("PricingRequest[id=%d, city=%s, %.2f→%.2f (%+.1f%%), status=%s]",
                id, cityName, currentPrice, proposedPrice, getPriceChangePercent(), status);
    }
}
