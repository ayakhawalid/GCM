package client.boundary;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.StaffUserDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManagementScreen implements GCMClient.MessageHandler {

    private static final String[] STAFF_ROLES = {
        "CONTENT_EDITOR", "CONTENT_MANAGER", "COMPANY_MANAGER", "SUPPORT_AGENT"
    };
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @FXML private Label managerLabel;
    @FXML private Label totalStaffLabel;
    @FXML private Label editorsLabel;
    @FXML private Label managersLabel;
    @FXML private Label agentsLabel;
    @FXML private Label statusLabel;
    @FXML private TextField searchField;
    @FXML private TableView<StaffRow> staffTable;
    @FXML private TableColumn<StaffRow, String> usernameCol;
    @FXML private TableColumn<StaffRow, String> emailCol;
    @FXML private TableColumn<StaffRow, String> roleCol;
    @FXML private TableColumn<StaffRow, String> registeredCol;
    @FXML private TableColumn<StaffRow, String> statusCol;
    @FXML private TableColumn<StaffRow, String> editRoleCol;
    @FXML private TableColumn<StaffRow, String> revokeCol;

    @FXML private WebView navbarLogoView1;
    @FXML private VBox guestDashboardPane;
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
    private static final String NAVBAR_LOGO_SVG_RESOURCE = "/client/assets/favicon.svg";

    private GCMClient gcmClient;
    private final ObservableList<StaffRow> allStaff = FXCollections.observableArrayList();
    private FilteredList<StaffRow> filteredStaff;

    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        if (!"COMPANY_MANAGER".equals(LoginController.currentRoleString)) {
            showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "Only Company Managers can access user management.");
            handleBack(null);
            return;
        }

        if (managerLabel != null) managerLabel.setText(LoginController.currentUsername);
        setupTable();
        connectAndLoad();
    }

    private void setupTable() {
        usernameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        emailCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().email));
        roleCol.setCellValueFactory(c -> new SimpleStringProperty(formatRole(c.getValue().role)));
        registeredCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().registered));
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().active ? "Active" : "Inactive"));

        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("Active".equals(item)
                            ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                            : "-fx-text-fill: #e74c3c;");
                }
            }
        });

        // Role-colored badge
        roleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    String color = switch (item) {
                        case "Company Manager" -> "#8e44ad";
                        case "Content Manager" -> "#27ae60";
                        case "Content Editor"  -> "#3498db";
                        case "Support Agent"   -> "#e67e22";
                        default -> "#2c3e50";
                    };
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            }
        });

        // Edit Role column with ComboBox
        editRoleCol.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>();
            private final Button applyBtn = new Button("Apply");
            private final HBox box = new HBox(5, combo, applyBtn);
            {
                combo.getItems().addAll(STAFF_ROLES);
                combo.setStyle("-fx-font-size: 11px;");
                applyBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 10;");
                applyBtn.setOnAction(e -> {
                    StaffRow row = getTableView().getItems().get(getIndex());
                    String newRole = combo.getValue();
                    if (newRole != null && !newRole.equals(row.role)) {
                        confirmUpdateRole(row, newRole);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    StaffRow row = getTableView().getItems().get(getIndex());
                    if (row.userId == LoginController.currentUserId) {
                        setGraphic(new Label("(you)"));
                    } else {
                        combo.setValue(row.role);
                        setGraphic(box);
                    }
                }
            }
        });

        // Revoke column
        revokeCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Revoke");
            {
                btn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 12;");
                btn.setOnAction(e -> {
                    StaffRow row = getTableView().getItems().get(getIndex());
                    confirmRevokeRole(row);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    StaffRow row = getTableView().getItems().get(getIndex());
                    setGraphic(row.userId == LoginController.currentUserId ? null : btn);
                }
            }
        });

        filteredStaff = new FilteredList<>(allStaff, p -> true);
        staffTable.setItems(filteredStaff);

        searchField.textProperty().addListener((obs, oldVal, newVal) ->
            filteredStaff.setPredicate(row -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String lower = newVal.toLowerCase();
                return row.username.toLowerCase().contains(lower) ||
                       row.email.toLowerCase().contains(lower) ||
                       row.role.toLowerCase().contains(lower);
            })
        );
    }

    private void connectAndLoad() {
        try {
            gcmClient = GCMClient.getInstance();
            gcmClient.setMessageHandler(this);
            loadStaff();
        } catch (IOException e) {
            statusLabel.setText("Connection failed");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    private void loadStaff() {
        if (gcmClient == null) return;
        try {
            String token = LoginController.currentSessionToken;
            gcmClient.sendToServer(new Request(MessageType.ADMIN_LIST_STAFF, null, token));
            statusLabel.setText("Loading...");
        } catch (IOException e) {
            statusLabel.setText("Failed to load");
        }
    }

    // ==================== Actions ====================

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadStaff();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) staffTable.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("GCM Dashboard");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddUser(ActionEvent event) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Add New Staff User");
        dialog.setHeaderText("Create a new user with a staff role");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Username");
        TextField emailField = new TextField();
        emailField.setPromptText("user@example.com");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Min 4 characters");
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll(STAFF_ROLES);
        roleCombo.setValue("CONTENT_EDITOR");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passField, 1, 2);
        grid.add(new Label("Role:"), 0, 3);
        grid.add(roleCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Map<String, String> result = new HashMap<>();
                result.put("username", nameField.getText());
                result.put("email", emailField.getText());
                result.put("password", passField.getText());
                result.put("role", roleCombo.getValue());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(data -> {
            String username = data.get("username");
            String email = data.get("email");
            String password = data.get("password");
            String role = data.get("role");

            if (username == null || username.trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Username is required.");
                return;
            }
            if (email == null || !email.contains("@")) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Valid email is required.");
                return;
            }
            if (password == null || password.length() < 4) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Password must be at least 4 characters.");
                return;
            }

            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("username", username.trim());
                payload.put("email", email.trim());
                payload.put("password", password);
                payload.put("role", role);
                String token = LoginController.currentSessionToken;
                gcmClient.sendToServer(new Request(MessageType.ADMIN_CREATE_STAFF_USER, payload, token));
                statusLabel.setText("Creating user...");
            } catch (IOException e) {
                statusLabel.setText("Failed to create user");
            }
        });
    }

    private void confirmUpdateRole(StaffRow row, String newRole) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Change Role");
        confirm.setHeaderText("Change role for " + row.username + "?");
        confirm.setContentText(formatRole(row.role) + "  ->  " + formatRole(newRole));
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("userId", row.userId);
                    payload.put("role", newRole);
                    String token = LoginController.currentSessionToken;
                    gcmClient.sendToServer(new Request(MessageType.ADMIN_UPDATE_USER_ROLE, payload, token));
                    statusLabel.setText("Updating role...");
                } catch (IOException e) {
                    statusLabel.setText("Failed to update role");
                }
            }
        });
    }

    private void confirmRevokeRole(StaffRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Revoke Role");
        confirm.setHeaderText("Revoke role for " + row.username + "?");
        confirm.setContentText("This will revert " + row.username + " to a regular CUSTOMER.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String token = LoginController.currentSessionToken;
                    gcmClient.sendToServer(new Request(MessageType.ADMIN_REVOKE_ROLE, row.userId, token));
                    statusLabel.setText("Revoking role...");
                } catch (IOException e) {
                    statusLabel.setText("Failed to revoke role");
                }
            }
        });
    }

    // ==================== Server Response ====================

    @Override
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (!(msg instanceof Response)) return;
            Response response = (Response) msg;

            if (!response.isOk()) {
                statusLabel.setText("Error: " + response.getErrorMessage());
                statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                showAlert(Alert.AlertType.ERROR, "Error", response.getErrorMessage());
                return;
            }

            switch (response.getRequestType()) {
                case ADMIN_LIST_STAFF:
                case ADMIN_UPDATE_USER_ROLE:
                case ADMIN_REVOKE_ROLE:
                case ADMIN_CREATE_STAFF_USER:
                    handleStaffListResponse(response);
                    break;
                default:
                    break;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleStaffListResponse(Response response) {
        if (!(response.getPayload() instanceof List)) return;
        List<StaffUserDTO> staff = (List<StaffUserDTO>) response.getPayload();
        allStaff.clear();

        int editors = 0, mgrs = 0, agents = 0;
        for (StaffUserDTO dto : staff) {
            String regDate = dto.getCreatedAt() != null
                    ? dto.getCreatedAt().toLocalDateTime().format(DATE_FMT) : "-";
            allStaff.add(new StaffRow(
                    dto.getUserId(), dto.getUsername(),
                    dto.getEmail() != null ? dto.getEmail() : "-",
                    dto.getRole(), regDate, dto.isActive()));

            switch (dto.getRole()) {
                case "CONTENT_EDITOR" -> editors++;
                case "CONTENT_MANAGER", "COMPANY_MANAGER" -> mgrs++;
                case "SUPPORT_AGENT" -> agents++;
            }
        }

        totalStaffLabel.setText(String.valueOf(staff.size()));
        editorsLabel.setText(String.valueOf(editors));
        managersLabel.setText(String.valueOf(mgrs));
        agentsLabel.setText(String.valueOf(agents));

        String action = switch (response.getRequestType()) {
            case ADMIN_CREATE_STAFF_USER -> "User created";
            case ADMIN_UPDATE_USER_ROLE -> "Role updated";
            case ADMIN_REVOKE_ROLE -> "Role revoked";
            default -> "Loaded " + staff.size() + " staff users";
        };
        statusLabel.setText(action);
        statusLabel.setStyle("-fx-text-fill: #27ae60;");
    }

    // ==================== Helpers ====================

    private static String formatRole(String role) {
        if (role == null) return "";
        return switch (role) {
            case "CONTENT_EDITOR"   -> "Content Editor";
            case "CONTENT_MANAGER"  -> "Content Manager";
            case "COMPANY_MANAGER"  -> "Company Manager";
            case "SUPPORT_AGENT"    -> "Support Agent";
            default -> role;
        };
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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

    @FXML
    private void toggleGuestDashboard(ActionEvent event) {
        if (guestDashboardPane == null) return;
        boolean nextVisible = !guestDashboardPane.isVisible();
        guestDashboardPane.setVisible(nextVisible);
        guestDashboardPane.setManaged(nextVisible);
    }

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
    // ==================== Row Model ====================

    public static class StaffRow {
        final int userId;
        final String username;
        final String email;
        final String role;
        final String registered;
        final boolean active;

        public StaffRow(int userId, String username, String email, String role,
                        String registered, boolean active) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.role = role;
            this.registered = registered;
            this.active = active;
        }
    }

}
