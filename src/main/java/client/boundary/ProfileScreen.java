package client.boundary;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Controller for the Profile screen.
 * Allows customers to view/edit their profile and see purchase history.
 */
public class ProfileScreen implements GCMClient.MessageHandler {
    private static final String BACK_BTN_BASE_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #7f8c8d; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 10; -fx-background-radius: 10;";
    private static final String BACK_BTN_HOVER_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #111111; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 10; -fx-background-radius: 10;";

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
    private Label cardLabel;
    @FXML
    private Button removeCardBtn;
    @FXML
    private Label lastLoginLabel;
    @FXML
    private Label userInfoLabel;
    @FXML
    private WebView navbarLogoView1;
    @FXML
    private WebView profileAvatarView;
    @FXML
    private VBox guestDashboardPane;
    @FXML private Button mapEditorNavBtn;
    @FXML private Button myPurchasesNavBtn;
    @FXML private Button profileNavBtn;
    @FXML private Button customersNavBtn;
    @FXML private Button pricingNavBtn;
    @FXML private Button pricingApprovalNavBtn;
    @FXML private Button supportNavBtn;
    @FXML private Button agentConsoleNavBtn;
    @FXML private Button editApprovalsNavBtn;
    @FXML private Button reportsNavBtn;
    @FXML private Button userManagementNavBtn;
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

    private GCMClient gcmClient;
    private ObservableList<PurchaseRow> purchaseRows = FXCollections.observableArrayList();
    private CustomerProfileDTO currentProfile;

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    private static final String NAVBAR_LOGO_SVG_RESOURCE = "/client/assets/favicon.svg";
    private static final String PROFILE_AVATAR_SVG_RESOURCE = "/client/assets/profile-avatar.svg";

    @FXML
    public void initialize() {
        System.out.println("ProfileScreen: Initializing");
        applyNavbarLogoSvg();
        applyProfileAvatarSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        setupTable();
        connectAndLoad();
    }

    private void applyNavbarLogoSvg() {
        if (navbarLogoView1 == null) return;
        java.net.URL svgUrl = getClass().getResource(NAVBAR_LOGO_SVG_RESOURCE);
        if (svgUrl == null) {
            navbarLogoView1.setVisible(false);
            navbarLogoView1.setManaged(false);
            return;
        }
        try {
            navbarLogoView1.getEngine().load(svgUrl.toExternalForm());
        } catch (Exception e) {
            navbarLogoView1.setVisible(false);
            navbarLogoView1.setManaged(false);
        }
    }

    private void applyProfileAvatarSvg() {
        if (profileAvatarView == null) return;
        try (java.io.InputStream in = getClass().getResourceAsStream(PROFILE_AVATAR_SVG_RESOURCE)) {
            if (in == null) {
                profileAvatarView.setVisible(false);
                profileAvatarView.setManaged(false);
                return;
            }
            byte[] svgBytes = in.readAllBytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(svgBytes);
            String dataUri = "data:image/svg+xml;base64," + base64;
            String html = "<!DOCTYPE html><html><head><style>"
                    + "html,body{margin:0;padding:0;overflow:hidden;background:transparent;} "
                    + "img{width:64px;height:64px;display:block;}"
                    + "</style></head><body><img src=\"" + dataUri + "\"/></body></html>";
            profileAvatarView.setPageFill(Color.TRANSPARENT);
            profileAvatarView.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;");
            profileAvatarView.getEngine().loadContent(html);
        } catch (Exception e) {
            profileAvatarView.setVisible(false);
            profileAvatarView.setManaged(false);
        }
    }

    @FXML
    private void toggleGuestDashboard(ActionEvent event) {
        if (guestDashboardPane == null) return;
        boolean nextVisible = !guestDashboardPane.isVisible();
        guestDashboardPane.setVisible(nextVisible);
        guestDashboardPane.setManaged(nextVisible);
    }

    @FXML private void navigateToHome(ActionEvent e) { MenuNavigationHelper.navigateToDashboard((Node) e.getSource()); }
    @FXML private void openSearchScreenFromAction(ActionEvent e) { MenuNavigationHelper.navigateToCatalog(guestDashboardPane); }
    @FXML private void openMapEditorFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToMapEditor(guestDashboardPane); }
    @FXML private void openMyPurchasesFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToMyPurchases(guestDashboardPane); }
    @FXML private void openProfileFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToProfile(guestDashboardPane); }
    @FXML private void openAdminCustomersFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToAdminCustomers(guestDashboardPane); }
    @FXML private void openPricingFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToPricing(guestDashboardPane); }
    @FXML private void openPricingApprovalFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToPricingApproval(guestDashboardPane); }
    @FXML private void openSupportFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToSupport(guestDashboardPane); }
    @FXML private void openAgentConsoleFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToAgentConsole(guestDashboardPane); }
    @FXML private void openEditApprovalsFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToEditApprovals(guestDashboardPane); }
    @FXML private void openReportsFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToReports(guestDashboardPane); }
    @FXML private void openUserManagementFromMenu(ActionEvent e) { MenuNavigationHelper.navigateToUserManagement(guestDashboardPane); }
    private void setupTable() {
        cityCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cityName));
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type));
        priceCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().price));
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().date));
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status));
        expiryCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().expiry));
        centerTextColumn(cityCol);
        centerTextColumn(typeCol);
        centerTextColumn(priceCol);
        centerTextColumn(dateCol);
        centerTextColumn(expiryCol);

        // Style status column
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
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

    private void centerTextColumn(TableColumn<PurchaseRow, String> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
                setText(empty ? null : item);
            }
        });
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

        clearUI();

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

    private void displayLocalProfile() {
        clearUI();
        usernameLabel.setText(LoginController.currentUsername != null ? LoginController.currentUsername : "User");
        userInfoLabel
                .setText("@" + (LoginController.currentUsername != null ? LoginController.currentUsername : "user"));
        saveBtn.setDisable(true);
    }

    /**
     * Clears all fields to prevent FXML placeholder leakage on error
     */
    private void clearUI() {
        usernameLabel.setText("");
        userInfoLabel.setText("");
        memberSinceLabel.setText("");
        totalPurchasesLabel.setText("0");
        totalSpentLabel.setText("$0.00");
        emailField.setText("");
        emailField.setPromptText("");
        phoneField.setText("");
        phoneField.setPromptText("");
        cardLabel.setText("");
        removeCardBtn.setVisible(false);
        removeCardBtn.setManaged(false);
        lastLoginLabel.setText("");
        statusLabel.setText("");
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

        // Validate email
        if (!email.isEmpty() && !email.contains("@")) {
            showError("Please enter a valid email address");
            return;
        }

        try {
            Map<String, String> updates = new HashMap<>();
            if (!email.isEmpty())
                updates.put("email", email);
            if (!phone.isEmpty())
                updates.put("phone", phone);

            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.UPDATE_MY_PROFILE, updates, token);
            gcmClient.sendToServer(request);

            statusLabel.setText("Saving...");
            statusLabel.setStyle("-fx-text-fill: #3498db;");
            saveBtn.setDisable(true);
        } catch (IOException e) {
            showError("Failed to refresh profile");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRemoveCard(ActionEvent event) {
        if (gcmClient == null)
            return;
        try {
            Map<String, String> updates = new HashMap<>();
            updates.put("card", ""); // empty indicates removal
            updates.put("cardExpiry", "");

            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.UPDATE_MY_PROFILE, updates, token);
            gcmClient.sendToServer(request);

            statusLabel.setText("Removing card...");
            statusLabel.setStyle("-fx-text-fill: #3498db;");
        } catch (IOException e) {
            showError("Failed to remove card");
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

    @FXML
    private void handleBackHoverEnter(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            button.setStyle(BACK_BTN_HOVER_STYLE);
        }
    }

    @FXML
    private void handleBackHoverExit(MouseEvent event) {
        if (event.getSource() instanceof Button button) {
            button.setStyle(BACK_BTN_BASE_STYLE);
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
                if (response.getRequestType() == MessageType.UPDATE_MY_PROFILE) {
                    saveBtn.setDisable(false);
                } else if (response.getRequestType() == MessageType.GET_MY_PROFILE) {
                    saveBtn.setDisable(true);
                }
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
            String expiry = profile.getCardExpiry() != null ? profile.getCardExpiry() : "MM/YY";
            if (expiry.equals("**/**")) {
                cardLabel.setText("Credit Card: **** **** **** ****"); // Masked for managers
                removeCardBtn.setVisible(false);
                removeCardBtn.setManaged(false);
            } else {
                cardLabel.setText(
                        String.format("Credit Card: **** **** **** %s\nExpiry: %s", profile.getCardLast4(), expiry));
                removeCardBtn.setVisible(true);
                removeCardBtn.setManaged(true);
            }
        } else {
            cardLabel.setText("No saved credit card");
            removeCardBtn.setVisible(false);
            removeCardBtn.setManaged(false);
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
            String type = e.isSubscription() ? "Subscription" : "One-time";
            String expiry = e.getExpiryDate() != null ? e.getExpiryDate().format(DATE_FORMAT) : "-";
            String status = e.isActive() ? "Active" : "Expired";
            String price = e.getPricePaid() != null ? CURRENCY_FORMAT.format(e.getPricePaid()) : "-";
            String date = e.getPurchaseDate() != null ? e.getPurchaseDate().format(DATE_FORMAT) : "-";

            purchaseRows.add(new PurchaseRow(
                    e.getCityId(),
                    e.getCityName() != null && !e.getCityName().isEmpty() ? e.getCityName() : "City #" + e.getCityId(),
                    type,
                    price,
                    date,
                    status,
                    expiry));
        }
    }

    private void displayPurchases(List<CustomerPurchaseDTO> purchases) {
        purchaseRows.clear();

        for (CustomerPurchaseDTO p : purchases) {
            String type = p.isSubscription() ? "Subscription" : "One-time";
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
