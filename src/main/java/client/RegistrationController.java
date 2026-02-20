package client;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.RegisterRequest;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Controller for the Registration page.
 * Handles customer registration with validation.
 */
public class RegistrationController {

    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 20;
    private static final int PASSWORD_MIN_LENGTH = 4;

    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField cardLast4Field;

    @FXML
    private Label usernameErrorLabel;
    @FXML
    private Label emailErrorLabel;
    @FXML
    private Label passwordErrorLabel;
    @FXML
    private Label confirmPasswordErrorLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private Button registerButton;
    @FXML
    private Hyperlink loginLink;

    @FXML
    public void initialize() {
        // Real-time validation listeners
        usernameField.textProperty().addListener((obs, old, val) -> validateUsername(val));
        emailField.textProperty().addListener((obs, old, val) -> validateEmail(val));
        passwordField.textProperty().addListener((obs, old, val) -> {
            validatePassword(val);
            validateConfirmPassword(confirmPasswordField.getText());
        });
        confirmPasswordField.textProperty().addListener((obs, old, val) -> validateConfirmPassword(val));
    }

    private boolean validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            usernameErrorLabel.setText("Username is required");
            return false;
        }
        if (username.length() < USERNAME_MIN_LENGTH) {
            usernameErrorLabel.setText("Minimum " + USERNAME_MIN_LENGTH + " characters");
            return false;
        }
        if (username.length() > USERNAME_MAX_LENGTH) {
            usernameErrorLabel.setText("Maximum " + USERNAME_MAX_LENGTH + " characters");
            return false;
        }
        usernameErrorLabel.setText("✓");
        usernameErrorLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 10px;");
        return true;
    }

    private boolean validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            emailErrorLabel.setText("Email is required");
            return false;
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            emailErrorLabel.setText("Invalid email format");
            return false;
        }
        emailErrorLabel.setText("✓");
        emailErrorLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 10px;");
        return true;
    }

    private boolean validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            passwordErrorLabel.setText("Password is required");
            return false;
        }
        if (password.length() < PASSWORD_MIN_LENGTH) {
            passwordErrorLabel.setText("Minimum " + PASSWORD_MIN_LENGTH + " characters");
            return false;
        }
        passwordErrorLabel.setText("✓");
        passwordErrorLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 10px;");
        return true;
    }

    private boolean validateConfirmPassword(String confirmPassword) {
        String password = passwordField.getText();
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            confirmPasswordErrorLabel.setText("Please confirm password");
            return false;
        }
        if (!confirmPassword.equals(password)) {
            confirmPasswordErrorLabel.setText("Passwords do not match");
            confirmPasswordErrorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 10px;");
            return false;
        }
        confirmPasswordErrorLabel.setText("✓");
        confirmPasswordErrorLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 10px;");
        return true;
    }

    @FXML
    private void handleRegister() {
        // Validate all fields
        boolean valid = true;
        valid &= validateUsername(usernameField.getText());
        valid &= validateEmail(emailField.getText());
        valid &= validatePassword(passwordField.getText());
        valid &= validateConfirmPassword(confirmPasswordField.getText());

        if (!valid) {
            statusLabel.setText("Please fix the errors above");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        // Validate card (optional but if provided, must be 4 digits)
        String cardLast4 = cardLast4Field.getText().trim();
        if (!cardLast4.isEmpty() && !cardLast4.matches("\\d{4}")) {
            statusLabel.setText("Card must be exactly 4 digits");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        statusLabel.setText("Creating account...");
        statusLabel.setStyle("-fx-text-fill: #3498db;");

        // Build registration request
        RegisterRequest regRequest = new RegisterRequest(
                usernameField.getText().trim(),
                emailField.getText().trim(),
                passwordField.getText(),
                phoneField.getText().trim(),
                "tok_mock_" + System.currentTimeMillis(), // Mock payment token
                cardLast4.isEmpty() ? "0000" : cardLast4);

        // Send to server
        try {
            Socket socket = new Socket("localhost", 5555);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Request request = new Request(MessageType.REGISTER_CUSTOMER, regRequest);
            out.writeObject(request);
            out.flush();

            Object response = in.readObject();
            socket.close();

            if (response instanceof Response) {
                Response resp = (Response) response;
                if (resp.isOk()) {
                    statusLabel.setText("✓ Registration successful! Redirecting to login...");
                    statusLabel.setStyle("-fx-text-fill: #27ae60;");

                    // Use proper JavaFX timer instead of Thread.sleep (Phase 14)
                    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                            javafx.util.Duration.millis(1500));
                    pause.setOnFinished(e -> navigateToLogin());
                    pause.play();
                } else {
                    statusLabel.setText("✗ " + resp.getErrorMessage());
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                }
            }
        } catch (Exception e) {
            statusLabel.setText("Connection error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLoginLink() {
        navigateToLogin();
    }

    private void navigateToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("GCM - Login");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            statusLabel.setText("Error loading login page");
            e.printStackTrace();
        }
    }
}
