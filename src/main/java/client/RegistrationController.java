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
import javafx.scene.web.WebView;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
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
    private TextField passwordVisibleField;
    @FXML
    private Button passwordToggleBtn;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private TextField confirmPasswordVisibleField;
    @FXML
    private Button confirmPasswordToggleBtn;
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

    private static final String EYE_SVG = "/client/assets/eye.svg";
    private static final String EYE_OFF_SVG = "/client/assets/eye-off.svg";
    private static final int EYE_ICON_SIZE = 20;

    @FXML
    public void initialize() {
        // Real-time validation listeners
        usernameField.textProperty().addListener((obs, old, val) -> validateUsername(val));
        emailField.textProperty().addListener((obs, old, val) -> validateEmail(val));
        passwordField.textProperty().addListener((obs, old, val) -> {
            validatePassword(val);
            validateConfirmPassword(getConfirmPassword());
        });
        confirmPasswordField.textProperty().addListener((obs, old, val) -> validateConfirmPassword(val));
        if (passwordVisibleField != null) {
            passwordVisibleField.textProperty().addListener((obs, old, val) -> {
                validatePassword(val);
                validateConfirmPassword(getConfirmPassword());
            });
        }
        if (confirmPasswordVisibleField != null) {
            confirmPasswordVisibleField.textProperty().addListener((obs, old, val) -> validateConfirmPassword(val));
        }

        applyPasswordEyeIcon(false);
        applyConfirmPasswordEyeIcon(false);
        if (passwordToggleBtn != null && passwordToggleBtn.getParent() instanceof StackPane) {
            StackPane.setAlignment(passwordToggleBtn, Pos.CENTER_RIGHT);
        }
        if (confirmPasswordToggleBtn != null && confirmPasswordToggleBtn.getParent() instanceof StackPane) {
            StackPane.setAlignment(confirmPasswordToggleBtn, Pos.CENTER_RIGHT);
        }
    }

    private void applyPasswordEyeIcon(boolean passwordVisible) {
        if (passwordToggleBtn == null) return;
        String res = passwordVisible ? EYE_OFF_SVG : EYE_SVG;
        try (java.io.InputStream in = getClass().getResourceAsStream(res)) {
            if (in == null) return;
            byte[] svgBytes = in.readAllBytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(svgBytes);
            String dataUri = "data:image/svg+xml;base64," + base64;
            String html = "<!DOCTYPE html><html><head><style>"
                    + "body{margin:0;padding:0;overflow:hidden;background:transparent;} "
                    + "img{width:" + EYE_ICON_SIZE + "px;height:" + EYE_ICON_SIZE + "px;display:block;}"
                    + "</style></head><body><img src=\"" + dataUri + "\"/></body></html>";
            WebView wv = new WebView();
            wv.setPrefSize(EYE_ICON_SIZE, EYE_ICON_SIZE);
            wv.setMinSize(EYE_ICON_SIZE, EYE_ICON_SIZE);
            wv.setMaxSize(EYE_ICON_SIZE, EYE_ICON_SIZE);
            wv.setStyle("-fx-background-color: transparent;");
            wv.getEngine().loadContent(html);
            passwordToggleBtn.setGraphic(wv);
        } catch (Exception ignored) {
        }
    }

    private void applyConfirmPasswordEyeIcon(boolean passwordVisible) {
        if (confirmPasswordToggleBtn == null) return;
        String res = passwordVisible ? EYE_OFF_SVG : EYE_SVG;
        try (java.io.InputStream in = getClass().getResourceAsStream(res)) {
            if (in == null) return;
            byte[] svgBytes = in.readAllBytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(svgBytes);
            String dataUri = "data:image/svg+xml;base64," + base64;
            String html = "<!DOCTYPE html><html><head><style>"
                    + "body{margin:0;padding:0;overflow:hidden;background:transparent;} "
                    + "img{width:" + EYE_ICON_SIZE + "px;height:" + EYE_ICON_SIZE + "px;display:block;}"
                    + "</style></head><body><img src=\"" + dataUri + "\"/></body></html>";
            WebView wv = new WebView();
            wv.setPrefSize(EYE_ICON_SIZE, EYE_ICON_SIZE);
            wv.setMinSize(EYE_ICON_SIZE, EYE_ICON_SIZE);
            wv.setMaxSize(EYE_ICON_SIZE, EYE_ICON_SIZE);
            wv.setStyle("-fx-background-color: transparent;");
            wv.getEngine().loadContent(html);
            confirmPasswordToggleBtn.setGraphic(wv);
        } catch (Exception ignored) {
        }
    }

    private String getPassword() {
        return (passwordVisibleField != null && passwordVisibleField.isVisible())
                ? passwordVisibleField.getText() : passwordField.getText();
    }

    private String getConfirmPassword() {
        return (confirmPasswordVisibleField != null && confirmPasswordVisibleField.isVisible())
                ? confirmPasswordVisibleField.getText() : confirmPasswordField.getText();
    }

    @FXML
    private void togglePasswordVisibility() {
        if (passwordVisibleField.isVisible()) {
            passwordField.setText(passwordVisibleField.getText());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            applyPasswordEyeIcon(false);
        } else {
            passwordVisibleField.setText(passwordField.getText());
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            applyPasswordEyeIcon(true);
        }
    }

    @FXML
    private void toggleConfirmPasswordVisibility() {
        if (confirmPasswordVisibleField.isVisible()) {
            confirmPasswordField.setText(confirmPasswordVisibleField.getText());
            confirmPasswordVisibleField.setVisible(false);
            confirmPasswordVisibleField.setManaged(false);
            confirmPasswordField.setVisible(true);
            confirmPasswordField.setManaged(true);
            applyConfirmPasswordEyeIcon(false);
        } else {
            confirmPasswordVisibleField.setText(confirmPasswordField.getText());
            confirmPasswordVisibleField.setVisible(true);
            confirmPasswordVisibleField.setManaged(true);
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            applyConfirmPasswordEyeIcon(true);
        }
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
        String password = getPassword();
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
        valid &= validatePassword(getPassword());
        valid &= validateConfirmPassword(getConfirmPassword());

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
                getPassword(),
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
            double w = Screen.getPrimary().getVisualBounds().getWidth();
            double h = Screen.getPrimary().getVisualBounds().getHeight();
            stage.setScene(new Scene(root, w, h));
            stage.setTitle("GCM - Login");
            stage.setMaximized(true);
        } catch (IOException e) {
            statusLabel.setText("Error loading login page");
            e.printStackTrace();
        }
    }
}
