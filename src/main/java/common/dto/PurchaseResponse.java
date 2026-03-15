package common.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Data Transfer Object for purchase responses.
 */
public class PurchaseResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private EntitlementInfo.EntitlementType entitlementType;
    private LocalDate expiryDate;

    public PurchaseResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public PurchaseResponse(boolean success, String message, EntitlementInfo.EntitlementType entitlementType,
            LocalDate expiryDate) {
        this.success = success;
        this.message = message;
        this.entitlementType = entitlementType;
        this.expiryDate = expiryDate;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public EntitlementInfo.EntitlementType getEntitlementType() {
        return entitlementType;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
