package client.boundary;

import client.GCMClient;
import client.LoginController;
import common.MessageType;
import common.Request;
import common.Response;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;

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
    private VBox reportsCard;
    @FXML
    private TextField updateIdField;
    @FXML
    private TextField priceField;

    private GCMClient client;

    @FXML
    public void initialize() {
        System.out.println("DashboardScreen: Initializing");

        // Set user info
        updateUserInfo();

        // Show admin card for managers
        if (LoginController.currentUserRole == LoginController.UserRole.MANAGER) {
            adminCustomersCard.setVisible(true);
            adminCustomersCard.setManaged(true);
        }

        // Show pricing cards based on role
        LoginController.UserRole role = LoginController.currentUserRole;
        if (role == LoginController.UserRole.MANAGER || role == LoginController.UserRole.EMPLOYEE) {
            // ContentManager and CompanyManager can access pricing
            pricingCard.setVisible(true);
            pricingCard.setManaged(true);
        }
        if (role == LoginController.UserRole.MANAGER) {
            // Only CompanyManager can approve prices
            pricingApprovalCard.setVisible(true);
            pricingApprovalCard.setManaged(true);
        }

        if (role == LoginController.UserRole.SUPPORT_AGENT) {
            // Support Agent
            agentConsoleCard.setVisible(true);
            agentConsoleCard.setManaged(true);
        }

        // Show reports for Managers
        if (role == LoginController.UserRole.MANAGER) {
            if (reportsCard != null) {
                reportsCard.setVisible(true);
                reportsCard.setManaged(true);
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
            showAlert(Alert.AlertType.INFORMATION, "Request Mode", "You are in Request Mode",
                    "As a customer, your changes will be submitted as requests.\n" +
                            "An employee will review and approve your changes.");
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
        if (LoginController.currentUserRole != LoginController.UserRole.SUPPORT_AGENT) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Agent Access Required",
                    "Only support agents can access the console.");
            return;
        }

        navigateTo("/client/agent_console.fxml", "Agent Console", 1000, 700);
    }

    @FXML
    private void openReportsScreen(MouseEvent event) {
        if (LoginController.currentUserRole != LoginController.UserRole.MANAGER) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Manager Access Required",
                    "Only managers can access activity reports.");
            return;
        }

        // Pass this dashboard controller to the reports controller if needed
        // But navigateTo creates a fresh scene.
        // If we want to keep "back" functionality, we might need a different nav
        // approach.
        // For now, standard navigateTo.

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/reports.fxml"));
            Parent root = loader.load();

            ReportsController controller = loader.getController();
            controller.setClient(client);
            // controller.setDashboardController(this); // If we want to pass context

            Stage stage = (Stage) resultArea.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Activity Reports");
            stage.centerOnScreen();

        } catch (IOException e) {
            resultArea.setText("Error: Could not navigate to Reports.");
            e.printStackTrace();
        }
    }

    private void showNotificationsDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Notifications");
        alert.setHeaderText("🔔 Your Notifications");
        alert.setContentText("Loading notifications...\n\n" +
                "Notifications include:\n" +
                "• Subscription expiry reminders\n" +
                "• Map update alerts\n" +
                "• System announcements");

        // Load and display notifications
        if (client != null) {
            try {
                String token = LoginController.currentSessionToken;
                Request request = new Request(MessageType.GET_MY_NOTIFICATIONS, null, token);
                client.sendToServer(request);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        alert.showAndWait();
    }

    // ==================== City Operations ====================

    @FXML
    private void getAllCities(ActionEvent event) {
        sendMessage("get_cities");
    }

    @FXML
    private void getMaps(ActionEvent event) {
        String id = cityIdField.getText();
        if (id.isEmpty()) {
            resultArea.setText("Error: Please enter a City ID first.");
            return;
        }
        sendMessage("get_maps " + id);
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
            stage.setWidth(width);
            stage.setHeight(height);
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

    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (msg instanceof Response) {
                Response response = (Response) msg;
                if (response.getRequestType() == MessageType.GET_UNREAD_COUNT && response.isOk()) {
                    int count = (Integer) response.getPayload();
                    updateNotificationBadge(count);
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
