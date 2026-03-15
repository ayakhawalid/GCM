package common.dto;

import java.io.Serializable;

/**
 * Request payload for submitting a new pricing request.
 * Sent by ContentManager when proposing a price change.
 */
public class SubmitPricingRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int cityId;
    private double proposedPrice;
    private String reason;

    // Default constructor
    public SubmitPricingRequest() {
    }

    // Full constructor
    public SubmitPricingRequest(int cityId, double proposedPrice, String reason) {
        this.cityId = cityId;
        this.proposedPrice = proposedPrice;
        this.reason = reason;
    }

    // Getters and Setters
    public int getCityId() {
        return cityId;
    }

    public void setCityId(int cityId) {
        this.cityId = cityId;
    }

    public double getProposedPrice() {
        return proposedPrice;
    }

    public void setProposedPrice(double proposedPrice) {
        this.proposedPrice = proposedPrice;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return String.format("SubmitPricingRequest[cityId=%d, proposedPrice=%.2f, reason=%s]",
                cityId, proposedPrice, reason);
    }
}
