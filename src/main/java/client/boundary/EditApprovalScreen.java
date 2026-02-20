package client.boundary;

import client.GCMClient;
import client.LoginController;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.ApprovalRequest;
import common.dto.MapVersionDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

/**
 * Controller for Edit Approval screen.
 * Allows Content Managers to review and approve/reject map edit requests.
 * Features a premium UI with rejection notes functionality.
 */
public class EditApprovalScreen implements GCMClient.MessageHandler {

    @FXML
    private TableView<MapVersionDTO> versionsTable;
    @FXML
    private TableColumn<MapVersionDTO, String> mapNameCol;
    @FXML
    private TableColumn<MapVersionDTO, String> cityNameCol;
    @FXML
    private TableColumn<MapVersionDTO, String> editorCol;
    @FXML
    private TableColumn<MapVersionDTO, String> versionCol;
    @FXML
    private TableColumn<MapVersionDTO, String> dateCol;

    @FXML
    private Label detailMapLabel;
    @FXML
    private Label detailCityLabel;
    @FXML
    private Label detailVersionLabel;
    @FXML
    private Label detailStatusLabel;
    @FXML
    private Label detailEditorLabel;
    @FXML
    private Label detailDateLabel;
    @FXML
    private TextArea detailChangesArea;

    @FXML
    private Button approveBtn;
    @FXML
    private Button rejectBtn;
    @FXML
    private Label errorLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label pendingCountLabel;

    private GCMClient client;
    private MapVersionDTO selectedVersion;
    private final ObservableList<MapVersionDTO> versionsList = FXCollections.observableArrayList();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        System.out.println("EditApprovalScreen: Initializing");

        // Setup table columns
        mapNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMapName()));
        cityNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCityName()));
        editorCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCreatedByUsername()));
        versionCol.setCellValueFactory(data -> new SimpleStringProperty("v" + data.getValue().getVersionNumber()));
        dateCol.setCellValueFactory(data -> {
            if (data.getValue().getCreatedAt() != null) {
                return new SimpleStringProperty(new SimpleDateFormat("MMM dd").format(data.getValue().getCreatedAt()));
            }
            return new SimpleStringProperty("-");
        });

        // Style version column with badge look
        versionCol.setCellFactory(col -> new TableCell<MapVersionDTO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
                }
            }
        });

        versionsTable.setItems(versionsList);

        // Table selection listener
        versionsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> onVersionSelected(newVal));

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

    private void onVersionSelected(MapVersionDTO version) {
        selectedVersion = version;
        if (version != null) {
            detailMapLabel.setText(version.getMapName());
            detailCityLabel.setText(version.getCityName());
            detailVersionLabel.setText("v" + version.getVersionNumber());
            detailStatusLabel.setText(version.getStatus());

            // Style status label
            switch (version.getStatus()) {
                case "PENDING":
                    detailStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
                    break;
                case "APPROVED":
                    detailStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
                    break;
                case "REJECTED":
                    detailStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                    break;
            }

            detailEditorLabel
                    .setText(version.getCreatedByUsername() != null ? version.getCreatedByUsername() : "Unknown");
            detailDateLabel.setText(version.getCreatedAt() != null ? dateFormat.format(version.getCreatedAt()) : "-");

            // Show changes description
            String changes = version.getDescriptionText();
            if (changes != null && !changes.isEmpty()) {
                detailChangesArea.setText(changes);
            } else {
                detailChangesArea.setText("(No description provided for this edit)");
            }

            approveBtn.setDisable(false);
            rejectBtn.setDisable(false);
        } else {
            clearDetails();
        }
    }

    private void clearDetails() {
        detailMapLabel.setText("(Select a request)");
        detailCityLabel.setText("-");
        detailVersionLabel.setText("v-");
        detailStatusLabel.setText("PENDING");
        detailStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
        detailEditorLabel.setText("-");
        detailDateLabel.setText("-");
        detailChangesArea.setText("");
        approveBtn.setDisable(true);
        rejectBtn.setDisable(true);
    }

    @FXML
    public void refreshRequests(ActionEvent event) {
        try {
            statusLabel.setText("Loading...");
            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.LIST_PENDING_MAP_VERSIONS, null, token);
            client.sendToServer(request);
        } catch (IOException e) {
            showError("Failed to load requests");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleApprove(ActionEvent event) {
        if (selectedVersion == null)
            return;
        clearError();

        // Confirmation dialog with modern style
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Approval");
        confirm.setHeaderText("✓ Approve Map Edit?");
        confirm.setContentText(String.format(
                "Map: %s\nCity: %s\nVersion: v%d\nEditor: %s\n\nThis will publish the changes and notify customers.",
                selectedVersion.getMapName(),
                selectedVersion.getCityName(),
                selectedVersion.getVersionNumber(),
                selectedVersion.getCreatedByUsername()));

        // Style the dialog
        DialogPane dialogPane = confirm.getDialogPane();
        dialogPane.setStyle("-fx-font-size: 13px;");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                statusLabel.setText("Approving...");
                approveBtn.setDisable(true);
                rejectBtn.setDisable(true);

                ApprovalRequest payload = new ApprovalRequest(selectedVersion.getId());
                String token = LoginController.currentSessionToken;
                Request request = new Request(MessageType.APPROVE_MAP_VERSION, payload, token);
                client.sendToServer(request);
            } catch (IOException e) {
                showError("Failed to approve request");
                approveBtn.setDisable(false);
                rejectBtn.setDisable(false);
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleReject(ActionEvent event) {
        if (selectedVersion == null)
            return;
        clearError();

        // Custom rejection dialog with styled TextArea
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Reject Edit Request");
        dialog.setHeaderText("✗ Reject: " + selectedVersion.getMapName());

        // Set up buttons
        ButtonType rejectButtonType = new ButtonType("Reject", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(rejectButtonType, ButtonType.CANCEL);

        // Create content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);

        Label infoLabel = new Label(
                "Please provide a reason for rejection.\nThe editor will be notified with this message.");
        infoLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
        infoLabel.setWrapText(true);

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText(
                "Enter rejection reason (required)...\n\nExamples:\n• Missing POI descriptions\n• Incorrect map coordinates\n• Incomplete tour information");
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(5);
        reasonArea.setStyle("-fx-font-size: 13px;");

        Label charCountLabel = new Label("0 / 500 characters");
        charCountLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");

        reasonArea.textProperty().addListener((obs, oldVal, newVal) -> {
            int len = newVal != null ? newVal.length() : 0;
            charCountLabel.setText(len + " / 500 characters");
            if (len > 500) {
                reasonArea.setText(oldVal);
            }
        });

        content.getChildren().addAll(infoLabel, reasonArea, charCountLabel);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(450);

        // Disable reject button until text is entered
        Button rejectButton = (Button) dialog.getDialogPane().lookupButton(rejectButtonType);
        rejectButton.setDisable(true);
        rejectButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");

        reasonArea.textProperty().addListener((obs, oldVal, newVal) -> {
            rejectButton.setDisable(newVal == null || newVal.trim().length() < 10);
        });

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == rejectButtonType) {
                return reasonArea.getText().trim();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().isEmpty()) {
            String reason = result.get();
            try {
                statusLabel.setText("Rejecting...");
                approveBtn.setDisable(true);
                rejectBtn.setDisable(true);

                ApprovalRequest payload = new ApprovalRequest(selectedVersion.getId(), reason);
                String token = LoginController.currentSessionToken;
                Request request = new Request(MessageType.REJECT_MAP_VERSION, payload, token);
                client.sendToServer(request);
            } catch (IOException e) {
                showError("Failed to reject request");
                approveBtn.setDisable(false);
                rejectBtn.setDisable(false);
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

            if (response.getRequestType() == MessageType.LIST_PENDING_MAP_VERSIONS) {
                if (response.isOk()) {
                    @SuppressWarnings("unchecked")
                    List<MapVersionDTO> versions = (List<MapVersionDTO>) response.getPayload();
                    versionsList.clear();
                    versionsList.addAll(versions);
                    clearDetails();
                    pendingCountLabel.setText(versions.size() + " pending");
                    System.out.println("EditApprovalScreen: Loaded " + versions.size() + " pending versions");
                } else {
                    showError(response.getErrorMessage());
                }
            }

            else if (response.getRequestType() == MessageType.APPROVE_MAP_VERSION) {
                if (response.isOk()) {
                    showSuccess("Map Edit Approved!",
                            "The changes have been published.\nCustomers with access to this city have been notified.");
                    refreshRequests(null);
                } else {
                    showError(response.getErrorMessage());
                    approveBtn.setDisable(false);
                    rejectBtn.setDisable(false);
                }
            }

            else if (response.getRequestType() == MessageType.REJECT_MAP_VERSION) {
                if (response.isOk()) {
                    showSuccess("Edit Request Rejected",
                            "The editor has been notified with your feedback.\nThey can revise and resubmit.");
                    refreshRequests(null);
                } else {
                    showError(response.getErrorMessage());
                    approveBtn.setDisable(false);
                    rejectBtn.setDisable(false);
                }
            }
        });
    }

    private void showError(String message) {
        errorLabel.setText("❌ " + message);
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText("✅ " + title);
        alert.setContentText(message);

        // Style the success dialog
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-font-size: 13px;");

        alert.showAndWait();
    }

    private void clearError() {
        errorLabel.setText("");
    }

    private void navigateTo(String fxml, String title, int width, int height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) versionsTable.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Could not navigate to screen");
            e.printStackTrace();
        }
    }
}
