package client.boundary;

import client.control.ContentManagementControl;
import common.Poi;
import common.dto.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class MapApprovalsScreen implements ContentManagementControl.ContentCallback {

    @FXML
    private Label statusLabel;
    @FXML
    private Button backButton;
    @FXML
    private ListView<MapEditRequestDTO> requestsListView;
    @FXML
    private VBox detailsPanel;
    @FXML
    private Label mapNameLabel;
    @FXML
    private Label cityNameLabel;
    @FXML
    private Label userLabel;
    @FXML
    private Label dateLabel;
    @FXML
    private TextArea changesArea;

    private ContentManagementControl control;
    private MapEditRequestDTO selectedRequest;

    @FXML
    public void initialize() {
        try {
            control = new ContentManagementControl("localhost", 5555);
            control.setCallback(this);
            setupListView();
            handleRefresh();
        } catch (IOException e) {
            showError("Connection failed");
            e.printStackTrace();
        }
    }

    private void setupListView() {
        requestsListView.setCellFactory(lv -> new ListCell<MapEditRequestDTO>() {
            @Override
            protected void updateItem(MapEditRequestDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-control-inner-background: #0f0f23;");
                } else {
                    setText((item.getMapName() != null ? item.getMapName() : "[New Map]") +
                            " (" + item.getCityName() + ") - by " + item.getUsername());
                    setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-control-inner-background: #0f0f23;");
                }
            }
        });

        requestsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                showDetails(newVal);
            }
        });
    }

    private void showDetails(MapEditRequestDTO request) {
        selectedRequest = request;
        detailsPanel.setVisible(true);

        mapNameLabel.setText(request.getMapName() != null ? request.getMapName() : "New Map Request");
        cityNameLabel.setText(request.getCityName());
        userLabel.setText("Submitted by: " + request.getUsername());
        dateLabel.setText("Date: " + (request.getCreatedAt() != null ? request.getCreatedAt().toString() : "Unknown"));

        changesArea.setText(generateSummary(request.getChanges()));
    }

    private String generateSummary(MapChanges changes) {
        if (changes == null)
            return "No changes data available.";

        StringBuilder sb = new StringBuilder();
        if (changes.isCreateNewCity()) {
            sb.append("• Creating new city: ").append(changes.getNewCityName())
                    .append("\n  Price: ").append(changes.getNewCityPrice()).append("\n\n");
        }

        if (changes.getNewMapName() != null) {
            sb.append("• Updating/Creating Map Info:\n  Name: ").append(changes.getNewMapName())
                    .append("\n  Desc: ").append(nullToEmpty(changes.getNewMapDescription())).append("\n\n");
        }

        // POIs section – always show so manager sees POI details when present
        sb.append("• POIs:\n");
        if (!changes.getAddedPois().isEmpty()) {
            sb.append("  Added ").append(changes.getAddedPois().size()).append(" POI(s):\n");
            for (Poi p : changes.getAddedPois()) {
                sb.append("  ─────────────────\n");
                sb.append("  Name: ").append(nullToEmpty(p.getName())).append("\n");
                sb.append("  Category: ").append(nullToEmpty(p.getCategory())).append("\n");
                if (p.getShortExplanation() != null && !p.getShortExplanation().isEmpty())
                    sb.append("  Description: ").append(p.getShortExplanation()).append("\n");
                String loc = formatPoiLocation(p);
                if (!loc.isEmpty()) sb.append("  Location: ").append(loc).append("\n");
                sb.append("  Accessible: ").append(p.isAccessible()).append("\n");
            }
        }
        if (!changes.getUpdatedPois().isEmpty()) {
            sb.append("  Updated ").append(changes.getUpdatedPois().size()).append(" POI(s):\n");
            for (Poi p : changes.getUpdatedPois()) {
                sb.append("  - ").append(nullToEmpty(p.getName()))
                        .append(" (").append(nullToEmpty(p.getCategory())).append(")");
                if (p.getShortExplanation() != null && !p.getShortExplanation().isEmpty())
                    sb.append(" — ").append(truncate(p.getShortExplanation(), 60));
                sb.append("\n");
            }
        }
        if (!changes.getDeletedPoiIds().isEmpty()) {
            sb.append("  Deleted ").append(changes.getDeletedPoiIds().size()).append(" POI(s)\n");
        }
        if (changes.getAddedPois().isEmpty() && changes.getUpdatedPois().isEmpty() && changes.getDeletedPoiIds().isEmpty()) {
            sb.append("  (no POI changes in this request)\n");
        }
        sb.append("\n");

        if (!changes.getAddedTours().isEmpty()) {
            sb.append("• Added ").append(changes.getAddedTours().size()).append(" tour(s):\n");
            changes.getAddedTours().forEach(
                    t -> sb.append("  + ").append(t.getName()).append(" (").append(t.getEstimatedDurationMinutes()).append(" min)\n"));
            sb.append("\n");
        }
        if (!changes.getUpdatedTours().isEmpty()) {
            sb.append("• Updated ").append(changes.getUpdatedTours().size()).append(" tour(s)\n\n");
        }
        if (!changes.getDeletedTourIds().isEmpty()) {
            sb.append("• Deleted ").append(changes.getDeletedTourIds().size()).append(" tour(s)\n\n");
        }

        if (sb.length() == 0)
            return "No visible changes recorded.";
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String formatPoiLocation(Poi p) {
        if (p.getLatitude() != null && p.getLongitude() != null)
            return String.format("%.5f, %.5f", p.getLatitude(), p.getLongitude());
        if (p.getLocation() != null && !p.getLocation().trim().isEmpty())
            return p.getLocation().trim();
        return "";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @FXML
    private void handleRefresh() {
        setStatus("Refreshing...");
        control.getPendingMapEdits();
    }

    @FXML
    private void handleApprove() {
        if (selectedRequest == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Approval");
        confirm.setHeaderText("Approve and Publish?");
        confirm.setContentText("This will apply changes immediately and create a new version.");

        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                setStatus("Approving...");
                control.approveMapEdit(selectedRequest.getId());
            }
        });
    }

    @FXML
    private void handleReject() {
        if (selectedRequest == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Rejection");
        confirm.setHeaderText("Reject Request?");

        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                setStatus("Rejecting...");
                control.rejectMapEdit(selectedRequest.getId());
            }
        });
    }

    @FXML
    private void handleBack() {
        if (control != null)
            control.disconnect();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setMaximized(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPendingRequestsReceived(List<MapEditRequestDTO> requests) {
        Platform.runLater(() -> {
            requestsListView.setItems(FXCollections.observableArrayList(requests));
            setStatus("Loaded " + requests.size() + " pending requests");
            if (requests.isEmpty()) {
                detailsPanel.setVisible(false);
            }
        });
    }

    @Override
    public void onValidationResult(ValidationResult result) {
        Platform.runLater(() -> {
            if (result.isValid()) {
                setStatus("Success: " + result.getSuccessMessage());
                handleRefresh();
                detailsPanel.setVisible(false);
                selectedRequest = null;
            } else {
                showError(result.getErrorSummary());
            }
        });
    }

    @Override
    public void onCitiesReceived(List<CityDTO> cities) {
    }

    @Override
    public void onMapsReceived(List<MapSummary> maps) {
    }

    @Override
    public void onMapContentReceived(MapContent content) {
    }

    @Override
    public void onPoisForCityReceived(List<Poi> pois) {
        // Not used on approvals screen
    }

    @Override
    public void onError(String errorCode, String errorMessage) {
        Platform.runLater(() -> showError(errorCode + ": " + errorMessage));
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: white;");
    }

    private void showError(String msg) {
        statusLabel.setText("⚠️ " + msg);
        statusLabel.setStyle("-fx-text-fill: #e74c3c;");
    }
}
