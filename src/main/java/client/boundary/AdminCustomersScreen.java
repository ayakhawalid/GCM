package client.boundary;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CustomerListItemDTO;
import common.dto.CustomerPurchaseDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Controller for Admin Customer Management screen.
 * Allows managers to view all customers and their purchase history.
 */
public class AdminCustomersScreen implements GCMClient.MessageHandler {

    // Stats labels
    @FXML
    private Label totalCustomersLabel;
    @FXML
    private Label totalRevenueLabel;
    @FXML
    private Label avgSpendLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label adminLabel;

    // Search
    @FXML
    private TextField searchField;

    // Customers table
    @FXML
    private TableView<CustomerRow> customersTable;
    @FXML
    private TableColumn<CustomerRow, String> usernameCol;
    @FXML
    private TableColumn<CustomerRow, String> emailCol;
    @FXML
    private TableColumn<CustomerRow, String> phoneCol;
    @FXML
    private TableColumn<CustomerRow, String> purchasesCol;
    @FXML
    private TableColumn<CustomerRow, String> subscriptionsCol;
    @FXML
    private TableColumn<CustomerRow, String> spentCol;
    @FXML
    private TableColumn<CustomerRow, String> registeredCol;
    @FXML
    private TableColumn<CustomerRow, String> statusCol;
    @FXML
    private TableColumn<CustomerRow, String> actionCol;

    // Purchases panel
    @FXML
    private VBox purchasesPanel;
    @FXML
    private Label selectedCustomerLabel;
    @FXML
    private TableView<PurchaseRow> purchasesTable;
    @FXML
    private TableColumn<PurchaseRow, String> pCityCol;
    @FXML
    private TableColumn<PurchaseRow, String> pTypeCol;
    @FXML
    private TableColumn<PurchaseRow, String> pPriceCol;
    @FXML
    private TableColumn<PurchaseRow, String> pDateCol;
    @FXML
    private TableColumn<PurchaseRow, String> pStatusCol;

    @FXML
    private CheckBox lastMonthCheck;
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
    private ObservableList<CustomerRow> allCustomers = FXCollections.observableArrayList();
    private FilteredList<CustomerRow> filteredCustomers;
    private ObservableList<PurchaseRow> purchaseRows = FXCollections.observableArrayList();

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        System.out.println("AdminCustomersScreen: Initializing");

        // Check admin access
        if (LoginController.currentUserRole != LoginController.UserRole.MANAGER) {
            showAlert(Alert.AlertType.ERROR, "Access Denied",
                    "This screen is only available to managers.");
            handleBack(null);
            return;
        }

        if (adminLabel != null) adminLabel.setText("👑 " + LoginController.currentUsername);
        setupTables();
        connectAndLoad();
    }

    private void setupTables() {
        // Customer table columns
        usernameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        emailCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().email));
        phoneCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().phone));
        purchasesCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().purchaseCount)));
        subscriptionsCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().subCount)));
        spentCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().totalSpent));
        registeredCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().registered));
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));

        // Action column with View button
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("👁 View");
            {
                btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px;");
                btn.setOnAction(e -> {
                    CustomerRow row = getTableView().getItems().get(getIndex());
                    viewCustomerPurchases(row);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        // Status column styling
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

        // Filtered list for search
        filteredCustomers = new FilteredList<>(allCustomers, p -> true);
        customersTable.setItems(filteredCustomers);

        // Search filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredCustomers.setPredicate(customer -> {
                if (newVal == null || newVal.isEmpty())
                    return true;
                String lower = newVal.toLowerCase();
                return customer.username.toLowerCase().contains(lower) ||
                        customer.email.toLowerCase().contains(lower) ||
                        (customer.phone != null && customer.phone.contains(lower));
            });
        });

        // Purchases table columns
        pCityCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().city));
        pTypeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type));
        pPriceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().price));
        pDateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date));
        pStatusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));

        purchasesTable.setItems(purchaseRows);
    }

    private void connectAndLoad() {
        try {
            gcmClient = GCMClient.getInstance();
            gcmClient.setMessageHandler(this);
            loadCustomers();
        } catch (IOException e) {
            statusLabel.setText("Connection failed");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            e.printStackTrace();
        }
    }

    private void loadCustomers() {
        if (gcmClient == null)
            return;

        try {
            String token = LoginController.currentSessionToken;
            boolean lastMonthOnly = lastMonthCheck != null && lastMonthCheck.isSelected();
            Request request = new Request(MessageType.ADMIN_LIST_CUSTOMERS, lastMonthOnly, token);
            gcmClient.sendToServer(request);
            statusLabel.setText("Loading customers...");
        } catch (IOException e) {
            statusLabel.setText("Failed to load");
            e.printStackTrace();
        }
    }

    private void viewCustomerPurchases(CustomerRow customer) {
        if (gcmClient == null)
            return;

        try {
            String token = LoginController.currentSessionToken;
            boolean lastMonthOnly = lastMonthCheck != null && lastMonthCheck.isSelected();

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("userId", customer.userId);
            payload.put("lastMonthOnly", lastMonthOnly);

            Request request = new Request(MessageType.ADMIN_GET_CUSTOMER_PURCHASES,
                    payload, token);
            gcmClient.sendToServer(request);

            selectedCustomerLabel.setText("Purchases for: " + customer.username);
            purchasesPanel.setVisible(true);
            purchasesPanel.setManaged(true);
            statusLabel.setText("Loading purchases...");
        } catch (IOException e) {
            statusLabel.setText("Failed to load purchases");
            e.printStackTrace();
        }
    }

    @FXML
    private void closePurchasesPanel(ActionEvent event) {
        purchasesPanel.setVisible(false);
        purchasesPanel.setManaged(false);
        purchaseRows.clear();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        // Search is handled by filter listener
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadCustomers();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        navigateTo("/client/dashboard.fxml", "GCM Dashboard", 1000, 700);
    }

    @Override
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (!(msg instanceof Response))
                return;

            Response response = (Response) msg;

            if (!response.isOk()) {
                statusLabel.setText("Error: " + response.getErrorMessage());
                statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                return;
            }

            switch (response.getRequestType()) {
                case ADMIN_LIST_CUSTOMERS:
                    handleCustomersResponse(response);
                    break;
                case ADMIN_GET_CUSTOMER_PURCHASES:
                    handlePurchasesResponse(response);
                    break;
                default:
                    break;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleCustomersResponse(Response response) {
        if (!(response.getPayload() instanceof List))
            return;

        List<CustomerListItemDTO> customers = (List<CustomerListItemDTO>) response.getPayload();
        allCustomers.clear();

        double totalRevenue = 0;
        for (CustomerListItemDTO c : customers) {
            String registered = c.getRegisteredAt() != null
                    ? c.getRegisteredAt().toLocalDateTime().format(DATE_FMT)
                    : "-";

            allCustomers.add(new CustomerRow(
                    c.getUserId(),
                    c.getUsername(),
                    c.getEmail() != null ? c.getEmail() : "-",
                    c.getPhone() != null ? c.getPhone() : "-",
                    c.getPurchaseCount(),
                    c.getSubscriptionCount(),
                    CURRENCY.format(c.getTotalSpent()),
                    registered,
                    c.isActive() ? "Active" : "Inactive"));
            totalRevenue += c.getTotalSpent();
        }

        // Update stats
        totalCustomersLabel.setText(String.valueOf(customers.size()));
        totalRevenueLabel.setText(CURRENCY.format(totalRevenue));
        avgSpendLabel.setText(customers.isEmpty() ? "$0" : CURRENCY.format(totalRevenue / customers.size()));

        statusLabel.setText("Loaded " + customers.size() + " customers");
        statusLabel.setStyle("-fx-text-fill: #27ae60;");
    }

    @SuppressWarnings("unchecked")
    private void handlePurchasesResponse(Response response) {
        if (!(response.getPayload() instanceof List))
            return;

        List<CustomerPurchaseDTO> purchases = (List<CustomerPurchaseDTO>) response.getPayload();
        purchaseRows.clear();

        for (CustomerPurchaseDTO p : purchases) {
            String type = p.isSubscription() ? "Subscription" : "One-time";
            String date = p.getPurchasedAt() != null
                    ? p.getPurchasedAt().toLocalDateTime().format(DATE_FMT)
                    : "-";

            purchaseRows.add(new PurchaseRow(
                    p.getCityName(),
                    type,
                    CURRENCY.format(p.getPricePaid()),
                    date,
                    p.getStatusText()));
        }

        statusLabel.setText("Loaded " + purchases.size() + " purchases");
        statusLabel.setStyle("-fx-text-fill: #27ae60;");
    }

    private void navigateTo(String fxml, String title, int width, int height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) customersTable.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    // Table row models
    public static class CustomerRow {
        int userId;
        String username;
        String email;
        String phone;
        int purchaseCount;
        int subCount;
        String totalSpent;
        String registered;
        String status;

        public CustomerRow(int userId, String username, String email, String phone,
                int purchaseCount, int subCount, String totalSpent, String registered, String status) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.purchaseCount = purchaseCount;
            this.subCount = subCount;
            this.totalSpent = totalSpent;
            this.registered = registered;
            this.status = status;
        }
    }

    public static class PurchaseRow {
        String city;
        String type;
        String price;
        String date;
        String status;

        public PurchaseRow(String city, String type, String price, String date, String status) {
            this.city = city;
            this.type = type;
            this.price = price;
            this.date = date;
            this.status = status;
        }
    }

}
