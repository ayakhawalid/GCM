package common.dto;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO for expiring subscription details.
 */
public class ExpiringSubscriptionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int subscriptionId;
    private int userId;
    private String username;
    private String email;
    private String phone;
    private String cityName;
    private Timestamp expiryDate;
    private int daysUntilExpiry;

    public ExpiringSubscriptionDTO(int subscriptionId, int userId, String username, String email,
            String phone, String cityName, Timestamp expiryDate, int daysUntilExpiry) {
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.cityName = cityName;
        this.expiryDate = expiryDate;
        this.daysUntilExpiry = daysUntilExpiry;
    }

    // Getters
    public int getSubscriptionId() {
        return subscriptionId;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getCityName() {
        return cityName;
    }

    public Timestamp getExpiryDate() {
        return expiryDate;
    }

    public int getDaysUntilExpiry() {
        return daysUntilExpiry;
    }
}
