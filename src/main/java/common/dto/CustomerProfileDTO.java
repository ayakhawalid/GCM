package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO for customer profile information.
 * Used for viewing and updating customer details.
 */
public class CustomerProfileDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String username;
    private String email;
    private String phone;
    private String cardLast4;
    private String cardExpiry;
    private Timestamp createdAt;
    private Timestamp lastLoginAt;
    private int totalPurchases;
    private double totalSpent;

    // ==================== Constructors ====================

    public CustomerProfileDTO() {
    }

    public CustomerProfileDTO(int userId, String username, String email, String phone) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.phone = phone;
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

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public String getCardExpiry() {
        return cardExpiry;
    }

    public void setCardExpiry(String cardExpiry) {
        this.cardExpiry = cardExpiry;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Timestamp lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public int getTotalPurchases() {
        return totalPurchases;
    }

    public void setTotalPurchases(int totalPurchases) {
        this.totalPurchases = totalPurchases;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    @Override
    public String toString() {
        return "CustomerProfileDTO{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                '}';
    }
}
