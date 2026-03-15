package common.dto;

import java.io.Serializable;

/**
 * Registration request DTO for customer registration.
 */
public class RegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String email;
    private String password;
    private String phone;
    private String paymentToken; // Mock payment token
    private String cardLast4; // Last 4 digits of card

    public RegisterRequest() {
    }

    public RegisterRequest(String username, String email, String password,
            String phone, String paymentToken, String cardLast4) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.paymentToken = paymentToken;
        this.cardLast4 = cardLast4;
    }

    // Getters and Setters
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    @Override
    public String toString() {
        return "RegisterRequest{username='" + username + "', email='" + email + "'}";
    }
}
