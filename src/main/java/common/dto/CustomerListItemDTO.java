package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO for customer list view (admin).
 * Contains summary information about each customer.
 */
public class CustomerListItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String username;
    private String email;
    private String phone;
    private int purchaseCount;
    private int subscriptionCount;
    private double totalSpent;
    private Timestamp lastPurchaseAt;
    private Timestamp registeredAt;
    private boolean isActive;

    // ==================== Constructors ====================

    public CustomerListItemDTO() {
    }

    public CustomerListItemDTO(int userId, String username, String email) {
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    // ==================== Getters and Setters ====================

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getPurchaseCount() {
        return purchaseCount;
    }

    public void setPurchaseCount(int purchaseCount) {
        this.purchaseCount = purchaseCount;
    }

    public int getSubscriptionCount() {
        return subscriptionCount;
    }

    public void setSubscriptionCount(int subscriptionCount) {
        this.subscriptionCount = subscriptionCount;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public Timestamp getLastPurchaseAt() {
        return lastPurchaseAt;
    }

    public void setLastPurchaseAt(Timestamp lastPurchaseAt) {
        this.lastPurchaseAt = lastPurchaseAt;
    }

    public Timestamp getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Timestamp registeredAt) {
        this.registeredAt = registeredAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getTotalTransactions() {
        return purchaseCount + subscriptionCount;
    }

    @Override
    public String toString() {
        return "CustomerListItemDTO{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", totalSpent=" + totalSpent +
                '}';
    }
}
