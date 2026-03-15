package common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for purchase requests.
 */
public class PurchaseRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum PurchaseType {
        ONE_TIME,
        SUBSCRIPTION
    }

    private int cityId;
    private PurchaseType purchaseType;
    private int months; // Only for subscription (1-6)

    // Payment info for saving
    private boolean saveCard;
    private String cardLast4;
    private String cardExpiry;

    // Constructor for one-time purchase
    public PurchaseRequest(int cityId, boolean saveCard, String cardLast4, String cardExpiry) {
        this.cityId = cityId;
        this.purchaseType = PurchaseType.ONE_TIME;
        this.months = 0;
        this.saveCard = saveCard;
        this.cardLast4 = cardLast4;
        this.cardExpiry = cardExpiry;
    }

    // Constructor for subscription
    public PurchaseRequest(int cityId, int months, boolean saveCard, String cardLast4, String cardExpiry) {
        this.cityId = cityId;
        this.purchaseType = PurchaseType.SUBSCRIPTION;
        this.months = months;
        this.saveCard = saveCard;
        this.cardLast4 = cardLast4;
        this.cardExpiry = cardExpiry;
    }

    public int getCityId() {
        return cityId;
    }

    public PurchaseType getPurchaseType() {
        return purchaseType;
    }

    public int getMonths() {
        return months;
    }

    public boolean isSaveCard() {
        return saveCard;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public String getCardExpiry() {
        return cardExpiry;
    }
}
