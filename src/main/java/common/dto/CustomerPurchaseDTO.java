package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * DTO for detailed purchase information.
 * Used for purchase history display.
 */
public class CustomerPurchaseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum PurchaseType {
        ONE_TIME,
        SUBSCRIPTION
    }

    private int purchaseId;
    private int cityId;
    private String cityName;
    private PurchaseType purchaseType;
    private double pricePaid;
    private Timestamp purchasedAt;

    // Subscription-specific fields
    private int months;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private boolean isActive;

    // ==================== Constructors ====================

    public CustomerPurchaseDTO() {
    }

    /**
     * Constructor for one-time purchase.
     */
    public static CustomerPurchaseDTO oneTime(int purchaseId, int cityId, String cityName,
            double pricePaid, Timestamp purchasedAt) {
        CustomerPurchaseDTO dto = new CustomerPurchaseDTO();
        dto.purchaseId = purchaseId;
        dto.cityId = cityId;
        dto.cityName = cityName;
        dto.purchaseType = PurchaseType.ONE_TIME;
        dto.pricePaid = pricePaid;
        dto.purchasedAt = purchasedAt;
        dto.isActive = true; // One-time purchases are always "active"
        return dto;
    }

    /**
     * Constructor for subscription.
     */
    public static CustomerPurchaseDTO subscription(int purchaseId, int cityId, String cityName,
            double pricePaid, Timestamp purchasedAt, int months, LocalDate startDate,
            LocalDate expiryDate, boolean isActive) {
        CustomerPurchaseDTO dto = new CustomerPurchaseDTO();
        dto.purchaseId = purchaseId;
        dto.cityId = cityId;
        dto.cityName = cityName;
        dto.purchaseType = PurchaseType.SUBSCRIPTION;
        dto.pricePaid = pricePaid;
        dto.purchasedAt = purchasedAt;
        dto.months = months;
        dto.startDate = startDate;
        dto.expiryDate = expiryDate;
        dto.isActive = isActive;
        return dto;
    }

    // ==================== Getters and Setters ====================

    public int getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(int purchaseId) {
        this.purchaseId = purchaseId;
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

    public PurchaseType getPurchaseType() {
        return purchaseType;
    }

    public void setPurchaseType(PurchaseType purchaseType) {
        this.purchaseType = purchaseType;
    }

    public double getPricePaid() {
        return pricePaid;
    }

    public void setPricePaid(double pricePaid) {
        this.pricePaid = pricePaid;
    }

    public Timestamp getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(Timestamp purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    public int getMonths() {
        return months;
    }

    public void setMonths(int months) {
        this.months = months;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isSubscription() {
        return purchaseType == PurchaseType.SUBSCRIPTION;
    }

    public String getStatusText() {
        if (purchaseType == PurchaseType.ONE_TIME) {
            return "Permanent";
        }
        return isActive ? "Active" : "Expired";
    }

    @Override
    public String toString() {
        return "CustomerPurchaseDTO{" +
                "cityName='" + cityName + '\'' +
                ", type=" + purchaseType +
                ", price=" + pricePaid +
                ", active=" + isActive +
                '}';
    }
}
