package client.boundary;

import client.GCMClient;
import client.LoginController;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CustomerProfileDTO;
import common.dto.CustomerPurchaseDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for the Profile screen.
 * Allows customers to view/edit their profile and see purchase history.
 */
public class ProfileScreen implements GCMClient.MessageHandler {

    // FXML Elements - Profile
    @FXML
    private Label usernameLabel;
    @FXML
    private Label memberSinceLabel;
    @FXML
    private Label totalPurchasesLabel;
    @FXML
    private Label totalSpentLabel;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField cardField;
    @FXML
    private Label lastLoginLabel;
    @FXML
    private Label userInfoLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Button saveBtn;

    // FXML Elements - Purchase Table
    @FXML
    private TableView<PurchaseRow> purchasesTable;
    @FXML
    private TableColumn<PurchaseRow, String> cityCol;
    @FXML
    private TableColumn<PurchaseRow, String> typeCol;
    @FXML
    private TableColumn<PurchaseRow, String> priceCol;
    @FXML
    private TableColumn<PurchaseRow, String> dateCol;
    @FXML
    private TableColumn<PurchaseRow, String> statusCol;
    @FXML
    private TableColumn<PurchaseRow, String> expiryCol;
    @FXML
    private TableColumn<PurchaseRow, String> actionCol;

    private GCMClient gcmClient;
    private ObservableList<PurchaseRow> purchaseRows = FXCollections.observableArrayList();
    private CustomerProfileDTO currentProfile;

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @FXML
    public void initialize() {
        System.out.println("ProfileScreen: Initializing");
        setupTable();
        connectAndLoad();
    }

    private void setupTable() {
        cityCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cityName));
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type));
        priceCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().price));
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().date));
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status));
        expiryCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().expiry));

        // Action column with Download button
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("📥");
            {
                btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 12px;");
                btn.setOnAction(e -> {
                    PurchaseRow row = getTableView().getItems().get(getIndex());
                    handleDownload(row);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });

        // Style status column
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Active".equals(item) || "Permanent".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c;");
                    }
                }
            }
        });

        purchasesTable.setItems(purchaseRows);
    }

    private void connectAndLoad() {
        try {
            gcmClient = GCMClient.getInstance();
            gcmClient.setMessageHandler(this);
            connectionLabel.setText("Connected");
            connectionLabel.setStyle("-fx-text-fill: #27ae60;");

            // Load profile
            loadProfile();
        } catch (IOException e) {
            connectionLabel.setText("Connection Lost");
            connectionLabel.setStyle("-fx-text-fill: #e74c3c;");
            e.printStackTrace();
        }
    }

    private void loadProfile() {
        if (gcmClient == null)
            return;

        String token = LoginController.currentSessionToken;

        // For legacy login (no token), display local info instead
        if (token == null || token.isEmpty()) {
            displayLocalProfile();
            return;
        }

        try {
            Request request = new Request(MessageType.GET_MY_PROFILE, null, token);
            gcmClient.sendToServer(request);
            statusLabel.setText("Loading profile...");
            statusLabel.setStyle("-fx-text-fill: #3498db;");
        } catch (IOException e) {
            showError("Failed to load profile");
            e.printStackTrace();
        }
    }

    /**
     * Display profile from local login data (for legacy logins without session
     * token).
     */
    private void displayLocalProfile() {
        usernameLabel.setText(LoginController.currentUsername != null ? LoginController.currentUsername : "User");
        userInfoLabel
                .setText("@" + (LoginController.currentUsername != null ? LoginController.currentUsername : "user"));
        memberSinceLabel.setText("Member since: ---");
        totalPurchasesLabel.setText("0");
        totalSpentLabel.setText("$0.00");
        emailField.setPromptText("your@email.com");
        phoneField.setPromptText("+1 234 567 8900");
        cardField.setPromptText("Enter card number");
        cardField.setText("**** **** **** ----");
        lastLoginLabel.setText("---");
        statusLabel.setText("");
        // Disable save for legacy login since no token
        saveBtn.setDisable(true);
    }

    private void loadPurchases() {
        if (gcmClient == null)
            return;

        try {
            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.GET_MY_PURCHASES, null, token);
            gcmClient.sendToServer(request);
        } catch (IOException e) {
            showError("Failed to load purchases");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        if (gcmClient == null)
            return;

        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String cardNumber = cardField.getText().trim();

        // Validate email
        if (!email.isEmpty() && !email.contains("@")) {
            showError("Please enter a valid email address");
            return;
        }

        // Validate card if provided
        if (!cardNumber.isEmpty() && !cardNumber.startsWith("*")) {
            // Basic validation - just checking it looks somewhat like a number
            if (cardNumber.replaceAll("[^0-9]", "").length() < 12) {
                showError("Card number must be at least 12 digits");
                return;
            }
        }

        try {
            Map<String, String> updates = new HashMap<>();
            if (!email.isEmpty())
                updates.put("email", email);
            if (!phone.isEmpty())
                updates.put("phone", phone);
            if (!cardNumber.isEmpty() && !cardNumber.startsWith("*")) {
                // Only update if it's a new number (not the masked one)
                updates.put("card", cardNumber);
            }

            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.UPDATE_MY_PROFILE, updates, token);
            gcmClient.sendToServer(request);

            statusLabel.setText("Saving...");
            statusLabel.setStyle("-fx-text-fill: #3498db;");
            saveBtn.setDisable(true);
        } catch (IOException e) {
            showError("Failed to save profile");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadProfile();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        navigateTo("/client/dashboard.fxml", "GCM Dashboard", 1000, 700);
    }

    private void handleDownload(PurchaseRow row) {
        if (gcmClient == null)
            return;

        try {
            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.DOWNLOAD_MAP_VERSION, row.cityId, token);
            gcmClient.sendToServer(request);
            showInfo("Download started for " + row.cityName);
        } catch (IOException e) {
            showError("Download failed");
            e.printStackTrace();
        }
    }

    @Override
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (!(msg instanceof Response))
                return;

            Response response = (Response) msg;

            if (!response.isOk()) {
                showError(response.getErrorMessage());
                saveBtn.setDisable(false);
                return;
            }

            switch (response.getRequestType()) {
                case GET_MY_PROFILE:
                case UPDATE_MY_PROFILE:
                    handleProfileResponse(response);
                    break;
                case GET_MY_PURCHASES:
                    handlePurchasesResponse(response);
                    break;
                case DOWNLOAD_MAP_VERSION:
                    showInfo("Download complete!");
                    break;
                default:
                    break;
            }
        });
    }

    private void handleProfileResponse(Response response) {
        if (response.getPayload() instanceof CustomerProfileDTO) {
            currentProfile = (CustomerProfileDTO) response.getPayload();
            displayProfile(currentProfile);

            if (response.getRequestType() == MessageType.UPDATE_MY_PROFILE) {
                showSuccess("Profile updated successfully!");
            } else {
                // Also load purchases
                loadPurchases();
            }
        }
        saveBtn.setDisable(false);
    }

    private void displayProfile(CustomerProfileDTO profile) {
        usernameLabel.setText(profile.getUsername());
        userInfoLabel.setText("@" + profile.getUsername());

        if (profile.getCreatedAt() != null) {
            memberSinceLabel.setText("Member since: " + profile.getCreatedAt().toLocalDateTime().format(DATE_FORMAT));
        }

        totalPurchasesLabel.setText(String.valueOf(profile.getTotalPurchases()));
        totalSpentLabel.setText(CURRENCY_FORMAT.format(profile.getTotalSpent()));

        emailField.setText(profile.getEmail() != null ? profile.getEmail() : "");
        phoneField.setText(profile.getPhone() != null ? profile.getPhone() : "");

        if (profile.getCardLast4() != null && !profile.getCardLast4().isEmpty()) {
            cardField.setText("**** **** **** " + profile.getCardLast4());
        } else {
            cardField.setText("");
            cardField.setPromptText("Enter card number");
        }

        if (profile.getLastLoginAt() != null) {
            lastLoginLabel.setText(profile.getLastLoginAt().toLocalDateTime().format(DATE_FORMAT));
        }

        statusLabel.setText("");
    }

    @SuppressWarnings("unchecked")
    private void handlePurchasesResponse(Response response) {
        if (response.getPayload() instanceof List) {
            List<?> rawList = (List<?>) response.getPayload();
            if (rawList.isEmpty()) {
                purchaseRows.clear();
                return;
            }

            // Check what type the server sent
            Object first = rawList.get(0);
            if (first instanceof common.dto.EntitlementInfo) {
                // Server sends EntitlementInfo
                List<common.dto.EntitlementInfo> entitlements = (List<common.dto.EntitlementInfo>) rawList;
                displayEntitlements(entitlements);
            } else if (first instanceof CustomerPurchaseDTO) {
                // Server sends CustomerPurchaseDTO
                List<CustomerPurchaseDTO> purchases = (List<CustomerPurchaseDTO>) rawList;
                displayPurchases(purchases);
            }
        }
    }

    private void displayEntitlements(List<common.dto.EntitlementInfo> entitlements) {
        purchaseRows.clear();

        for (common.dto.EntitlementInfo e : entitlements) {
            String type = e.isSubscription() ? "📅 Subscription" : "🛒 One-time";
            String expiry = e.getExpiryDate() != null ? e.getExpiryDate().format(DATE_FORMAT) : "-";
            String status = e.isActive() ? "Active" : "Expired";

            purchaseRows.add(new PurchaseRow(
                    e.getCityId(),
                    e.getCityName() != null && !e.getCityName().isEmpty() ? e.getCityName() : "City #" + e.getCityId(),
                    type,
                    "-", // Price not available in EntitlementInfo
                    "-", // Purchase date not available
                    status,
                    expiry));
        }
    }

    private void displayPurchases(List<CustomerPurchaseDTO> purchases) {
        purchaseRows.clear();

        for (CustomerPurchaseDTO p : purchases) {
            String type = p.isSubscription() ? "📅 Subscription" : "🛒 One-time";
            String date = p.getPurchasedAt() != null
                    ? p.getPurchasedAt().toLocalDateTime().format(DATE_FORMAT)
                    : "-";
            String expiry = p.getExpiryDate() != null ? p.getExpiryDate().format(DATE_FORMAT) : "-";

            purchaseRows.add(new PurchaseRow(
                    p.getCityId(),
                    p.getCityName(),
                    type,
                    CURRENCY_FORMAT.format(p.getPricePaid()),
                    date,
                    p.getStatusText(),
                    expiry));
        }
    }

    private void navigateTo(String fxml, String title, int width, int height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Navigation failed");
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        statusLabel.setText("❌ " + message);
        statusLabel.setStyle("-fx-text-fill: #e74c3c;");
    }

    private void showSuccess(String message) {
        statusLabel.setText("✓ " + message);
        statusLabel.setStyle("-fx-text-fill: #27ae60;");
    }

    private void showInfo(String message) {
        statusLabel.setText("ℹ️ " + message);
        statusLabel.setStyle("-fx-text-fill: #3498db;");
    }

    // Table row model
    public static class PurchaseRow {
        int cityId;
        String cityName;
        String type;
        String price;
        String date;
        String status;
        String expiry;

        public PurchaseRow(int cityId, String cityName, String type, String price,
                String date, String status, String expiry) {
            this.cityId = cityId;
            this.cityName = cityName;
            this.type = type;
            this.price = price;
            this.date = date;
            this.status = status;
            this.expiry = expiry;
        }
    }
}
