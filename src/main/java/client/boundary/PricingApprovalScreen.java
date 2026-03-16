package client.boundary;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.ApprovePricingRequest;
import common.dto.PricingRequestDTO;
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
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

/**
 * Controller for Pricing Approval screen.
 * Allows CompanyManager to review and approve/reject pricing requests.
 */
public class PricingApprovalScreen implements GCMClient.MessageHandler {

    @FXML
    private TableView<PricingRequestDTO> requestsTable;
    @FXML
    private TableColumn<PricingRequestDTO, String> cityCol;
    @FXML
    private TableColumn<PricingRequestDTO, String> currentCol;
    @FXML
    private TableColumn<PricingRequestDTO, String> proposedCol;
    @FXML
    private TableColumn<PricingRequestDTO, String> changeCol;
    @FXML
    private TableColumn<PricingRequestDTO, String> submitterCol;
    @FXML
    private TableColumn<PricingRequestDTO, String> dateCol;

    @FXML
    private Label detailCityLabel;
    @FXML
    private Label detailCurrentLabel;
    @FXML
    private Label detailProposedLabel;
    @FXML
    private Label detailChangeLabel;
    @FXML
    private Label detailSubmitterLabel;
    @FXML
    private TextArea detailReasonArea;

    @FXML
    private Button approveBtn;
    @FXML
    private Button rejectBtn;
    @FXML
    private Label errorLabel;
    @FXML
    private Label statusLabel;
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

    private GCMClient client;
    private PricingRequestDTO selectedRequest;
    private ObservableList<PricingRequestDTO> requestsList = FXCollections.observableArrayList();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        System.out.println("PricingApprovalScreen: Initializing");

        // Setup table columns
        cityCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCityName()));
        currentCol.setCellValueFactory(
                data -> new SimpleStringProperty(String.format("₪%.2f", data.getValue().getCurrentPrice())));
        proposedCol.setCellValueFactory(
                data -> new SimpleStringProperty(String.format("₪%.2f", data.getValue().getProposedPrice())));
        changeCol.setCellValueFactory(data -> {
            double change = data.getValue().getPriceChangePercent();
            String sign = change >= 0 ? "+" : "";
            return new SimpleStringProperty(String.format("%s%.1f%%", sign, change));
        });
        submitterCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCreatedByName()));
        dateCol.setCellValueFactory(data -> {
            if (data.getValue().getCreatedAt() != null) {
                return new SimpleStringProperty(dateFormat.format(data.getValue().getCreatedAt()));
            }
            return new SimpleStringProperty("-");
        });

        // Style change column based on value
        changeCol.setCellFactory(col -> new TableCell<PricingRequestDTO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    }
                }
            }
        });

        requestsTable.setItems(requestsList);

        // Table selection listener
        requestsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> onRequestSelected(newVal));

        // Initial button state
        approveBtn.setDisable(true);
        rejectBtn.setDisable(true);

        // Connect and load data
        try {
            client = GCMClient.getInstance();
            client.setMessageHandler(this);
            refreshRequests(null);
        } catch (IOException e) {
            showError("Failed to connect to server");
            e.printStackTrace();
        }
    }

    private void onRequestSelected(PricingRequestDTO request) {
        selectedRequest = request;
        if (request != null) {
            detailCityLabel.setText(request.getCityName());
            detailCurrentLabel.setText(String.format("₪%.2f", request.getCurrentPrice()));
            detailProposedLabel.setText(String.format("₪%.2f", request.getProposedPrice()));

            double change = request.getPriceChangePercent();
            String sign = change >= 0 ? "+" : "";
            detailChangeLabel.setText(String.format("%s%.1f%%", sign, change));
            if (change >= 0) {
                detailChangeLabel.setStyle(
                        "-fx-text-fill: white; -fx-background-color: #27ae60; -fx-background-radius: 20; -fx-padding: 5 15;");
            } else {
                detailChangeLabel.setStyle(
                        "-fx-text-fill: white; -fx-background-color: #e74c3c; -fx-background-radius: 20; -fx-padding: 5 15;");
            }

            detailSubmitterLabel.setText(request.getCreatedByName() +
                    (request.getCreatedAt() != null ? " on " + dateFormat.format(request.getCreatedAt()) : ""));
            detailReasonArea.setText(request.getReason() != null ? request.getReason() : "(No reason provided)");

            approveBtn.setDisable(false);
            rejectBtn.setDisable(false);
        } else {
            clearDetails();
        }
    }

    private void clearDetails() {
        detailCityLabel.setText("(Select a request)");
        detailCurrentLabel.setText("₪0.00");
        detailProposedLabel.setText("₪0.00");
        detailChangeLabel.setText("");
        detailChangeLabel.setStyle("");
        detailSubmitterLabel.setText("-");
        detailReasonArea.setText("");
        approveBtn.setDisable(true);
        rejectBtn.setDisable(true);
    }

    @FXML
    public void refreshRequests(ActionEvent event) {
        try {
            statusLabel.setText("Loading...");
            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.LIST_PENDING_PRICING_REQUESTS, null, token);
            client.sendToServer(request);
        } catch (IOException e) {
            showError("Failed to load requests");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleApprove(ActionEvent event) {
        if (selectedRequest == null)
            return;
        clearError();

        // Confirm dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Approval");
        confirm.setHeaderText("Approve Price Change?");
        confirm.setContentText(String.format(
                "City: %s\nPrice: ₪%.2f → ₪%.2f\n\nThis action will immediately update the city price.",
                selectedRequest.getCityName(),
                selectedRequest.getCurrentPrice(),
                selectedRequest.getProposedPrice()));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                statusLabel.setText("Approving...");
                ApprovePricingRequest payload = new ApprovePricingRequest(selectedRequest.getId());
                String token = LoginController.currentSessionToken;
                int userId = LoginController.currentUserId;
                Request request = new Request(MessageType.APPROVE_PRICING_REQUEST, payload, token, userId);
                client.sendToServer(request);
            } catch (IOException e) {
                showError("Failed to approve request");
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleReject(ActionEvent event) {
        if (selectedRequest == null)
            return;
        clearError();

        // Get rejection reason
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject Pricing Request");
        dialog.setHeaderText("Please provide a reason for rejection");
        dialog.setContentText("Reason:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String reason = result.get().trim();
            try {
                statusLabel.setText("Rejecting...");
                ApprovePricingRequest payload = new ApprovePricingRequest(selectedRequest.getId(), reason);
                String token = LoginController.currentSessionToken;
                int userId = LoginController.currentUserId;
                Request request = new Request(MessageType.REJECT_PRICING_REQUEST, payload, token, userId);
                client.sendToServer(request);
            } catch (IOException e) {
                showError("Failed to reject request");
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        navigateTo("/client/dashboard.fxml", "GCM Dashboard", 1000, 700);
    }

    @Override
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (!(msg instanceof Response))
                return;

            Response response = (Response) msg;
            statusLabel.setText("Ready");

            if (response.getRequestType() == MessageType.LIST_PENDING_PRICING_REQUESTS) {
                if (response.isOk()) {
                    @SuppressWarnings("unchecked")
                    List<PricingRequestDTO> requests = (List<PricingRequestDTO>) response.getPayload();
                    requestsList.clear();
                    requestsList.addAll(requests);
                    clearDetails();
                    System.out.println("PricingApprovalScreen: Loaded " + requests.size() + " pending requests");
                } else {
                    showError(response.getErrorMessage());
                }
            }

            else if (response.getRequestType() == MessageType.APPROVE_PRICING_REQUEST) {
                if (response.isOk()) {
                    showSuccess("Pricing request approved!\nNew price has been applied.");
                    refreshRequests(null);
                } else {
                    showError(response.getErrorMessage());
                }
            }

            else if (response.getRequestType() == MessageType.REJECT_PRICING_REQUEST) {
                if (response.isOk()) {
                    showSuccess("Pricing request rejected.\nThe submitter has been notified.");
                    refreshRequests(null);
                } else {
                    showError(response.getErrorMessage());
                }
            }
        });
    }

    private void showError(String message) {
        errorLabel.setText("❌ " + message);
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText("✅ Action Completed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void clearError() {
        errorLabel.setText("");
    }

    private void navigateTo(String fxml, String title, int width, int height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) requestsTable.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Could not navigate to screen");
            e.printStackTrace();
        }
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
}
