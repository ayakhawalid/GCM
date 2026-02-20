package client.boundary;

import client.GCMClient;
import client.LoginController;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.NotificationDTO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern Dashboard screen controller.
 * Combines all features in a unified interface.
 */
public class DashboardScreen implements GCMClient.MessageHandler {

    @FXML
    private Label userInfoLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TextArea resultArea;
    @FXML
    private TextField cityIdField;
    @FXML
    private Button logoutBtn;
    @FXML
    private Label notificationBadge;
    @FXML
    private VBox adminCustomersCard;
    @FXML
    private VBox pricingCard;
    @FXML
    private VBox pricingApprovalCard;
    @FXML
    private VBox supportCard;
    @FXML
    private VBox agentConsoleCard;
    @FXML
    private VBox editApprovalsCard;
    @FXML
    private TextField updateIdField;
    @FXML
    private TextField priceField;
    @FXML
    private VBox updatePriceCard;
    @FXML
    private VBox mapEditorCard;
    @FXML
    private VBox myPurchasesCard;

    private GCMClient client;

    @FXML
    public void initialize() {
        System.out.println("DashboardScreen: Initializing");

        // Set user info
        updateUserInfo();

        LoginController.UserRole role = LoginController.currentUserRole;

        // My Purchases only visible to CUSTOMER
        if (role != LoginController.UserRole.CUSTOMER) {
            if (myPurchasesCard != null) {
                myPurchasesCard.setVisible(false);
                myPurchasesCard.setManaged(false);
            }
        }

        // Show admin card for managers
        if (LoginController.currentUserRole == LoginController.UserRole.MANAGER) {
            adminCustomersCard.setVisible(true);
            adminCustomersCard.setManaged(true);
        }

        // Hide Map Editor from customers and anonymous users
        if (role == LoginController.UserRole.CUSTOMER || role == LoginController.UserRole.ANONYMOUS) {
            if (mapEditorCard != null) {
                mapEditorCard.setVisible(false);
                mapEditorCard.setManaged(false);
            }
        }

        // Show pricing card for employees only (they submit price change requests)
        if (role == LoginController.UserRole.EMPLOYEE) {
            pricingCard.setVisible(true);
            pricingCard.setManaged(true);
        }
        if (role == LoginController.UserRole.MANAGER) {
            // Only CompanyManager can approve prices and update prices directly
            pricingApprovalCard.setVisible(true);
            pricingApprovalCard.setManaged(true);
            if (updatePriceCard != null) {
                updatePriceCard.setVisible(true);
                updatePriceCard.setManaged(true);
            }
        }

        if (role == LoginController.UserRole.SUPPORT_AGENT) {
            // Support Agent
            agentConsoleCard.setVisible(true);
            agentConsoleCard.setManaged(true);
        }

        // Show edit approvals for Managers
        if (role == LoginController.UserRole.MANAGER) {
            if (editApprovalsCard != null) {
                editApprovalsCard.setVisible(true);
                editApprovalsCard.setManaged(true);
            }
        }

        // Connect to server
        try {
            client = GCMClient.getInstance();
            client.setMessageHandler(this);
            statusLabel.setText("Connected to server");
            statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px;");

            // Load notification count
            loadNotificationCount();
        } catch (IOException e) {
            statusLabel.setText("Connection Lost");
            statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
            e.printStackTrace();
        }
    }

    private void updateUserInfo() {
        String username = LoginController.currentUsername;
        LoginController.UserRole role = LoginController.currentUserRole;

        if (username == null || username.isEmpty()) {
            userInfoLabel.setText("Welcome, Guest");
        } else {
            String roleStr = role != null ? " (" + role.name() + ")" : "";
            userInfoLabel.setText("Welcome, " + username + roleStr);
        }
    }

    private void loadNotificationCount() {
        if (client == null || LoginController.currentUserRole == LoginController.UserRole.ANONYMOUS) {
            notificationBadge.setText("");
            notificationBadge.setVisible(false);
            return;
        }

        try {
            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.GET_UNREAD_COUNT, null, token);
            client.sendToServer(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== Quick Action Cards ====================

    @FXML
    private void openSearchScreen(MouseEvent event) {
        navigateTo("/client/catalog_search.fxml", "GCM - City Catalog", 1100, 750);
    }

    @FXML
    private void openMapEditor(MouseEvent event) {
        // Check user role
        LoginController.UserRole role = LoginController.currentUserRole;

        if (role == LoginController.UserRole.ANONYMOUS) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Login Required",
                    "Map Editor requires login.\nPlease login to access this feature.");
            return;
        }

        if (role == LoginController.UserRole.CUSTOMER) {
            showAlert(Alert.AlertType.INFORMATION, "Map Editor Not Available", "Employee Access Only",
                    "Map editing is only available to employees.\n\n" +
                            "If you found an error in a map, please use the Support Center " +
                            "to report the issue and our team will correct it.");
            return;
        }

        navigateTo("/client/map_editor.fxml", "GCM - Map Editor", 1200, 800);
    }

    @FXML
    private void openMyPurchases(MouseEvent event) {
        LoginController.UserRole role = LoginController.currentUserRole;

        if (role == LoginController.UserRole.ANONYMOUS) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Login Required",
                    "Please login to view your purchases.");
            return;
        }

        navigateTo("/client/my_purchases.fxml", "My Purchases", 900, 600);
    }

    @FXML
    private void openProfile(MouseEvent event) {
        LoginController.UserRole role = LoginController.currentUserRole;

        if (role == LoginController.UserRole.ANONYMOUS) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Login Required",
                    "Please login to view your profile.");
            return;
        }

        navigateTo("/client/profile.fxml", "My Profile", 1000, 700);
    }

    @FXML
    private void openNotifications(MouseEvent event) {
        LoginController.UserRole role = LoginController.currentUserRole;

        if (role == LoginController.UserRole.ANONYMOUS) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Login Required",
                    "Please login to view notifications.");
            return;
        }

        // Show notifications in a dialog for now
        showNotificationsDialog();
    }

    @FXML
    private void openAdminCustomers(MouseEvent event) {
        if (LoginController.currentUserRole != LoginController.UserRole.MANAGER) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Manager Access Required",
                    "Only managers can access customer management.");
            return;
        }

        navigateTo("/client/admin_customers.fxml", "Customer Management", 1200, 800);
    }

    @FXML
    private void openPricingScreen(MouseEvent event) {
        LoginController.UserRole role = LoginController.currentUserRole;

        if (role != LoginController.UserRole.MANAGER && role != LoginController.UserRole.EMPLOYEE) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Employee Access Required",
                    "Only content managers and company managers can access pricing.");
            return;
        }

        navigateTo("/client/pricing_screen.fxml", "Pricing Management", 1100, 700);
    }

    @FXML
    private void openPricingApproval(MouseEvent event) {
        if (LoginController.currentUserRole != LoginController.UserRole.MANAGER) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Manager Access Required",
                    "Only company managers can approve pricing requests.");
            return;
        }

        navigateTo("/client/pricing_approval.fxml", "Pricing Approval", 1100, 700);
    }

    @FXML
    private void openSupportScreen(MouseEvent event) {
        if (LoginController.currentUserRole == LoginController.UserRole.ANONYMOUS) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Login Required",
                    "Please login to access support.");
            return;
        }

        navigateTo("/client/support_screen.fxml", "GCM Support Center", 800, 600);
    }

    @FXML
    private void openAgentConsole(MouseEvent event) {
        System.out.println("openAgentConsole clicked - Current role: " + LoginController.currentUserRole);

        if (LoginController.currentUserRole != LoginController.UserRole.SUPPORT_AGENT) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Agent Access Required",
                    "Only support agents can access the console.\nYour role: " + LoginController.currentUserRole);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/agent_console.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) resultArea.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("Agent Console");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            System.err.println("Error loading agent_console.fxml: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Navigation Failed",
                    "Could not open Agent Console: " + e.getMessage());
        }
    }

    @FXML
    private void openEditApprovals(MouseEvent event) {
        if (LoginController.currentUserRole != LoginController.UserRole.MANAGER) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Manager Access Required",
                    "Only managers can approve edit requests.");
            return;
        }

        navigateTo("/client/map_approvals.fxml", "Edit Request Approvals", 1150, 750);
    }

    private void showNotificationsDialog() {
        // Create dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Notifications");
        dialog.setHeaderText("🔔 Your Notifications");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(500, 400);

        // Create content VBox
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f8f9fa;");

        Label loadingLabel = new Label("Loading notifications...");
        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        content.getChildren().add(loadingLabel);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(300);
        dialog.getDialogPane().setContent(scrollPane);

        // Request notifications from server
        if (client != null) {
            try {
                String token = LoginController.currentSessionToken;
                Request request = new Request(MessageType.GET_MY_NOTIFICATIONS, null, token);
                client.sendToServer(request);

                // Store reference to update later
                pendingNotificationsContent = content;
            } catch (IOException e) {
                loadingLabel.setText("Failed to load notifications: " + e.getMessage());
                e.printStackTrace();
            }
        }

        dialog.showAndWait();
        pendingNotificationsContent = null;
    }

    // Temporary storage for notification dialog content
    private VBox pendingNotificationsContent = null;

    @SuppressWarnings("unchecked")
    private void displayNotifications(List<NotificationDTO> notifications) {
        if (pendingNotificationsContent == null)
            return;

        pendingNotificationsContent.getChildren().clear();

        if (notifications.isEmpty()) {
            Label emptyLabel = new Label("📭 No notifications");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #888;");
            pendingNotificationsContent.getChildren().add(emptyLabel);
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

        for (NotificationDTO n : notifications) {
            VBox card = new VBox(5);
            card.setPadding(new Insets(10));
            String bgColor = n.isRead() ? "#ffffff" : "#e3f2fd";
            card.setStyle("-fx-background-color: " + bgColor
                    + "; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ddd; -fx-border-width: 1;");

            Label titleLabel = new Label((n.isRead() ? "" : "🔵 ") + n.getTitle());
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            Label bodyLabel = new Label(n.getBody());
            bodyLabel.setWrapText(true);
            bodyLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");

            String dateStr = n.getCreatedAt() != null
                    ? n.getCreatedAt().toLocalDateTime().format(fmt)
                    : "";
            Label dateLabel = new Label(dateStr);
            dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

            card.getChildren().addAll(titleLabel, bodyLabel, dateLabel);
            pendingNotificationsContent.getChildren().add(card);
        }
    }

    // ==================== City Operations ====================

    @FXML
    private void getAllCities(ActionEvent event) {
        if (client == null) {
            resultArea.setText("Error: Not connected to server.");
            return;
        }
        try {
            client.sendToServer(new Request(MessageType.GET_CITIES, null));
            resultArea.setText("Loading cities...");
        } catch (IOException e) {
            resultArea.setText("Error: Could not send request.");
            e.printStackTrace();
        }
    }

    @FXML
    private void getMaps(ActionEvent event) {
        String idStr = cityIdField.getText();
        if (idStr == null || idStr.trim().isEmpty()) {
            resultArea.setText("Error: Please enter a City ID first.");
            return;
        }
        int cityId;
        try {
            cityId = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            resultArea.setText("Error: City ID must be a number.");
            return;
        }
        if (client == null) {
            resultArea.setText("Error: Not connected to server.");
            return;
        }
        try {
            client.sendToServer(new Request(MessageType.GET_MAPS_FOR_CITY, cityId));
            resultArea.setText("Loading maps for city " + cityId + "...");
        } catch (IOException e) {
            resultArea.setText("Error: Could not send request.");
            e.printStackTrace();
        }
    }

    @FXML
    private void clearResults(ActionEvent event) {
        resultArea.clear();
    }

    @FXML
    private void updatePrice(ActionEvent event) {
        String id = updateIdField.getText();
        String price = priceField.getText();
        if (id.isEmpty() || price.isEmpty()) {
            resultArea.setText("Error: Please enter both City ID and Price.");
            return;
        }
        try {
            Double.parseDouble(price);
        } catch (NumberFormatException e) {
            resultArea.setText("Error: Price must be a valid number.");
            return;
        }
        sendMessage("update_price " + id + " " + price);
    }

    // ==================== Navigation ====================

    @FXML
    private void handleLogout(ActionEvent event) {
        // Tell the server to invalidate the session so the same user can log in again later
        String token = LoginController.currentSessionToken;
        if (token != null && !token.isEmpty() && client != null) {
            try {
                // Wait for server to process LOGOUT before closing (avoids "already logged in" on next login)
                client.sendRequestSync(new Request(MessageType.LOGOUT, token, token));
            } catch (Exception e) {
                // Continue with local logout even if send fails or times out
            }
        }

        LoginController.currentUsername = null;
        LoginController.currentUserRole = LoginController.UserRole.ANONYMOUS;
        LoginController.currentSessionToken = null;
        LoginController.currentUserId = 0;

        if (client != null) {
            try {
                client.closeConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        navigateTo("/client/login.fxml", "GCM Login", 500, 600);
    }

    private void navigateTo(String fxml, String title, int width, int height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) resultArea.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            resultArea.setText("Error: Could not navigate to screen.");
            e.printStackTrace();
        }
    }

    // ==================== Server Communication ====================

    private void sendMessage(String msg) {
        if (client == null) {
            resultArea.setText("Error: Not connected to server.");
            return;
        }
        try {
            client.sendToServer(msg);
        } catch (IOException e) {
            resultArea.setText("Error: Could not send message to server.");
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (msg instanceof Response) {
                Response response = (Response) msg;
                if (response.getRequestType() == MessageType.GET_UNREAD_COUNT && response.isOk()) {
                    int count = (Integer) response.getPayload();
                    updateNotificationBadge(count);
                    return;
                }
                if (response.getRequestType() == MessageType.GET_MY_NOTIFICATIONS && response.isOk()) {
                    List<NotificationDTO> notifications = (List<NotificationDTO>) response.getPayload();
                    displayNotifications(notifications);
                    loadNotificationCount();
                    return;
                }
                // City operations: display result or error in result area
                if (response.getRequestType() == MessageType.GET_CITIES || response.getRequestType() == MessageType.GET_MAPS_FOR_CITY) {
                    if (response.isOk() && response.getPayload() instanceof List) {
                        List<?> list = (List<?>) response.getPayload();
                        StringBuilder sb = new StringBuilder();
                        for (Object o : list) {
                            sb.append(o.toString()).append("\n");
                        }
                        resultArea.setText(sb.length() > 0 ? sb.toString() : "(No items)");
                    } else {
                        resultArea.setText(response.isOk() ? "(No data)" : "Error: " + (response.getErrorMessage() != null ? response.getErrorMessage() : response.getErrorCode()));
                    }
                    return;
                }
                return;
            }

            if (msg instanceof ArrayList) {
                StringBuilder sb = new StringBuilder();
                ArrayList<?> list = (ArrayList<?>) msg;
                for (Object o : list) {
                    sb.append(o.toString()).append("\n");
                }
                resultArea.setText(sb.toString());
            } else {
                resultArea.setText(msg.toString());
            }
        });
    }

    private void updateNotificationBadge(int count) {
        if (count > 0) {
            notificationBadge.setText(count > 9 ? "9+" : String.valueOf(count));
            notificationBadge.setVisible(true);
        } else {
            notificationBadge.setText("");
            notificationBadge.setVisible(false);
        }
    }

    // ==================== Helpers ====================

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
