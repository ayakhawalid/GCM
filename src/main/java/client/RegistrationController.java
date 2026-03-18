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
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
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
    private CheckBox addPaymentDetailsCheckBox;
    @FXML
    private Label paymentStatusLabel;

    @FXML
    private Label usernameErrorLabel;

    /** Stored after user fills payment details popup (card last 4 digits and expiry). */
    private String savedCardLast4 = "";
    private String savedCardExpiry = "";
    @FXML
    private Label emailErrorLabel;
    @FXML
    private Label passwordErrorLabel;
    @FXML
    private Label confirmPasswordErrorLabel;
    @FXML
    private Label phoneErrorLabel;
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
        if (phoneField != null) {
            phoneField.textProperty().addListener((obs, old, val) -> validatePhone(val));
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
    private void onPaymentDetailsCheckboxChanged() {
        if (addPaymentDetailsCheckBox == null) return;
        if (addPaymentDetailsCheckBox.isSelected()) {
            showPaymentDetailsDialog();
        } else {
            savedCardLast4 = "";
            savedCardExpiry = "";
            if (paymentStatusLabel != null)
                paymentStatusLabel.setText("No card added");
        }
    }

    private void showPaymentDetailsDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Payment details (optional)");

        VBox root = new VBox(15);
        root.setStyle("-fx-background-color: #f5f6fa; -fx-padding: 20; -fx-border-color: #dcdfe3; -fx-border-width: 1;");
        root.setPrefWidth(400);

        Label title = new Label("Enter card details");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: #2c3e50; -fx-font-weight: bold;");

        TextField cardField = new TextField();
        cardField.setPromptText("Credit Card Number (13-16 digits)");
        cardField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");

        TextField idField = new TextField();
        idField.setPromptText("Israeli ID (9 digits)");
        idField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");

        HBox row = new HBox(10);
        TextField expField = new TextField();
        expField.setPromptText("Expiry MM/YY");
        expField.setPrefWidth(120);
        expField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");
        TextField cvvField = new TextField();
        cvvField.setPromptText("CVV (3 digits)");
        cvvField.setPrefWidth(100);
        cvvField.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #95a5a6;");
        row.getChildren().addAll(expField, cvvField);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> {
            if (addPaymentDetailsCheckBox != null)
                addPaymentDetailsCheckBox.setSelected(false);
            dialog.close();
        });
        Button saveBtn = new Button("Save");
        saveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> {
            String err = validatePaymentFields(cardField.getText(), idField.getText(), expField.getText(), cvvField.getText());
            if (err != null) {
                errorLabel.setText(err);
            } else {
                String cardNumber = cardField.getText().trim();
                savedCardLast4 = cardNumber.length() > 4 ? cardNumber.substring(cardNumber.length() - 4) : cardNumber;
                savedCardExpiry = expField.getText().trim();
                if (paymentStatusLabel != null) {
                    paymentStatusLabel.setText(String.format("Card **** %s saved (Exp: %s)", savedCardLast4, savedCardExpiry));
                    paymentStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px;");
                }
                dialog.close();
            }
        });

        btnBox.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().addAll(title, cardField, idField, row, errorLabel, btnBox);

        dialog.setScene(new Scene(root));
        dialog.sizeToScene();
        dialog.showAndWait();
    }

    private String validatePaymentFields(String card, String id, String exp, String cvv) {
        if (card == null || !card.matches("\\d{13,16}"))
            return "Card must be 13-16 digits.";
        if (id == null || !id.matches("\\d{9}"))
            return "ID must be exactly 9 digits.";
        if (cvv == null || !cvv.matches("\\d{3}"))
            return "CVV must be exactly 3 digits.";
        if (exp == null || !exp.matches("(0[1-9]|1[0-2])/\\d{2}"))
            return "Expiry must be MM/YY.";
        try {
            String[] parts = exp.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]) + 2000;
            java.time.YearMonth current = java.time.YearMonth.now();
            java.time.YearMonth expYield = java.time.YearMonth.of(year, month);
            if (expYield.isBefore(current))
                return "Card is expired.";
        } catch (Exception e) {
            return "Invalid expiry date.";
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = card.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(card.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) n = (n % 10) + 1;
            }
            sum += n;
            alternate = !alternate;
        }
        if (sum % 10 != 0)
            return "Invalid credit card number (Luhn check failed).";
        return null;
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

    private boolean validatePhone(String phone) {
        if (phoneErrorLabel == null) return true;
        if (phone == null || phone.trim().isEmpty()) {
            phoneErrorLabel.setText("");
            return true;
        }
        String digits = phone.trim();
        if (!digits.matches("\\d{9}")) {
            phoneErrorLabel.setText("Phone must be exactly 9 digits");
            phoneErrorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 10px;");
            return false;
        }
        phoneErrorLabel.setText("✓");
        phoneErrorLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 10px;");
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
        valid &= validatePhone(phoneField.getText());

        if (!valid) {
            statusLabel.setText("Please fix the errors above");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        String cardLast4 = (savedCardLast4 != null && !savedCardLast4.isEmpty()) ? savedCardLast4 : "0000";
        String cardExpiry = (savedCardExpiry != null && !savedCardExpiry.isEmpty()) ? savedCardExpiry : null;

        statusLabel.setText("Creating account...");
        statusLabel.setStyle("-fx-text-fill: #3498db;");

        // Build registration request
        RegisterRequest regRequest = new RegisterRequest(
                usernameField.getText().trim(),
                emailField.getText().trim(),
                getPassword(),
                phoneField.getText().trim(),
                "tok_mock_" + System.currentTimeMillis(), // Mock payment token
                cardLast4,
                cardExpiry);

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
