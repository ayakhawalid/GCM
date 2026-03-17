package common.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Data Transfer Object for user entitlement info.
 */
public class EntitlementInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum EntitlementType {
        NONE,
        ONE_TIME,
        SUBSCRIPTION
    }

    private int cityId;
    private String cityName;
    private EntitlementType type;
    private LocalDate expiryDate; // Null for one-time or none
    private boolean canView;
    private boolean canDownload;
    private boolean isCanceled; // default false
    private Double pricePaid;
    private LocalDate purchaseDate;

    public EntitlementInfo(int cityId, EntitlementType type, LocalDate expiryDate, boolean canView,
            boolean canDownload) {
        this.cityId = cityId;
        this.cityName = "";
        this.type = type;
        this.expiryDate = expiryDate;
        this.canView = canView;
        this.canDownload = canDownload;
    }

    /**
     * Constructor with city name for display purposes.
     */
    public EntitlementInfo(int cityId, String cityName, EntitlementType type, LocalDate expiryDate,
            boolean canView, boolean canDownload) {
        this.cityId = cityId;
        this.cityName = cityName;
        this.type = type;
        this.expiryDate = expiryDate;
        this.canView = canView;
        this.canDownload = canDownload;
    }

    public int getCityId() {
        return cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public EntitlementType getType() {
        return type;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public boolean isCanView() {
        return canView;
    }

    public boolean isCanDownload() {
        return canDownload;
    }

    public Double getPricePaid() {
        return pricePaid;
    }

    public void setPricePaid(Double pricePaid) {
        this.pricePaid = pricePaid;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public boolean isCanceled() {
        return isCanceled;
    }

    public void setCanceled(boolean canceled) {
        this.isCanceled = canceled;
    }

    public boolean isSubscription() {
        return type == EntitlementType.SUBSCRIPTION;
    }

    public boolean isActive() {
        if (type == EntitlementType.ONE_TIME) {
            return true; // One-time purchases never expire
        }
        if (type == EntitlementType.SUBSCRIPTION && expiryDate != null) {
            return !expiryDate.isBefore(LocalDate.now());
        }
        return false;
    }
}
