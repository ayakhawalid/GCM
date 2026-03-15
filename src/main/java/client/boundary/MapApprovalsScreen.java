package client.boundary;

import client.MenuNavigationHelper;
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
import javafx.scene.web.WebView;
import javafx.event.ActionEvent;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

public class MapApprovalsScreen implements ContentManagementControl.ContentCallback {

    @FXML
    private Label statusLabel;
    @FXML
    private Button backButton;
    @FXML
    private WebView navbarLogoView1;
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
    private static final String NAVBAR_LOGO_SVG_RESOURCE = "/client/assets/favicon.svg";
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
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
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
                    String title = getRequestDisplayName(item);
                    setText(title + " – by " + item.getUsername());
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

        if (isCityOnlyRequest(request)) {
            String cityName = request.getCityName();
            if ((cityName == null || cityName.isEmpty()) && request.getChanges() != null) {
                MapChanges ch = request.getChanges();
                if (ch.getNewCityWithMap() != null && !ch.getNewCityWithMap().isEmpty())
                    cityName = ch.getNewCityWithMap().get(0).getCityName();
                else if (ch.getNewCityName() != null) cityName = ch.getNewCityName();
                else if (ch.getNewCities() != null && !ch.getNewCities().isEmpty())
                    cityName = ch.getNewCities().get(0).getName();
                else if (ch.getDisplayCityName() != null) cityName = ch.getDisplayCityName();
            }
            mapNameLabel.setText("New City Request");
            cityNameLabel.setText(cityName != null && !cityName.isEmpty() ? cityName : "—");
        } else {
            mapNameLabel.setText(request.getMapName() != null ? request.getMapName() : "New Map Request");
            cityNameLabel.setText(request.getCityName() != null ? request.getCityName() : "—");
        }
        userLabel.setText("Submitted by: " + request.getUsername());
        dateLabel.setText("Date: " + (request.getCreatedAt() != null ? request.getCreatedAt().toString() : "Unknown"));

        changesArea.setText(generateSummary(request.getChanges(), request));
    }

    private String generateSummary(MapChanges changes, MapEditRequestDTO request) {
        if (changes == null)
            return "No changes data available.";

        StringBuilder sb = new StringBuilder();

        // Map removal: show clear message with map name, city name, and date
        if (changes.getDeletedMapIds() != null && !changes.getDeletedMapIds().isEmpty()) {
            String mapName = request != null && request.getMapName() != null && !request.getMapName().isEmpty()
                    ? request.getMapName() : "this map";
            String cityName = request != null && request.getCityName() != null && !request.getCityName().isEmpty()
                    ? request.getCityName() : "the city";
            String dateStr = "Unknown";
            if (request != null && request.getCreatedAt() != null) {
                try {
                    dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(request.getCreatedAt());
                } catch (Exception ignored) { }
            }
            sb.append("• Employee requested to remove map \"").append(mapName)
                    .append("\" from city \"").append(cityName)
                    .append("\". Request date: ").append(dateStr).append("\n\n");
        }

        // New tour(s): show clear message so manager sees employee created a new tour
        if (changes.getAddedTours() != null && !changes.getAddedTours().isEmpty()) {
            sb.append("• Employee created new tour(s):\n");
            for (TourDTO t : changes.getAddedTours()) {
                String name = (t.getName() != null && !t.getName().trim().isEmpty()) ? t.getName().trim() : "—";
                sb.append("  – \"").append(name).append("\"\n");
            }
            sb.append("\n");
        }

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
            if (changes.getDeletedPoiDisplayNames() != null && changes.getDeletedPoiDisplayNames().size() == changes.getDeletedPoiIds().size()) {
                for (String name : changes.getDeletedPoiDisplayNames()) {
                    sb.append("  Delete POI: \"").append(name != null ? name : "—").append("\"\n");
                }
            } else {
                sb.append("  Deleted ").append(changes.getDeletedPoiIds().size()).append(" POI(s)\n");
            }
        }
        if (changes.getAddedPois().isEmpty() && changes.getUpdatedPois().isEmpty() && changes.getDeletedPoiIds().isEmpty()) {
            if (changes.getDeletedMapIds() != null && !changes.getDeletedMapIds().isEmpty()) {
                sb.append("  (This request removes the entire map.)\n");
            } else {
                sb.append("  (no POI changes in this request)\n");
            }
        }
        sb.append("\n");

        if (!changes.getAddedTours().isEmpty()) {
            sb.append("• Added ").append(changes.getAddedTours().size()).append(" tour(s):\n");
            changes.getAddedTours().forEach(t -> {
                String dist = t.getTotalDistanceMeters() != null ? String.format("%.0f m", t.getTotalDistanceMeters()) : "? m";
                sb.append("  + ").append(t.getName()).append(" (").append(dist).append(")\n");
            });
            sb.append("\n");
        }
        if (!changes.getUpdatedTours().isEmpty()) {
            sb.append("• Updated ").append(changes.getUpdatedTours().size()).append(" tour(s)\n\n");
        }
        if (!changes.getDeletedTourIds().isEmpty()) {
            if (changes.getDeletedTourDisplayNames() != null && changes.getDeletedTourDisplayNames().size() == changes.getDeletedTourIds().size()) {
                for (String name : changes.getDeletedTourDisplayNames()) {
                    sb.append("• Delete tour: \"").append(name != null ? name : "—").append("\"\n");
                }
                sb.append("\n");
            } else {
                sb.append("• Deleted ").append(changes.getDeletedTourIds().size()).append(" tour(s)\n\n");
            }
        }

        // Tour stops: add/update/remove so manager sees what changed
        if (changes.getAddedStops() != null && !changes.getAddedStops().isEmpty()) {
            sb.append("• Employee added ").append(changes.getAddedStops().size()).append(" tour stop(s):\n");
            for (TourStopDTO s : changes.getAddedStops()) {
                String poiName = (s.getPoiName() != null && !s.getPoiName().isEmpty()) ? s.getPoiName() : "POI id " + s.getPoiId();
                sb.append("  – ").append(poiName).append(" (order ").append(s.getStopOrder()).append(")\n");
            }
            sb.append("\n");
        }
        if (changes.getUpdatedStops() != null && !changes.getUpdatedStops().isEmpty()) {
            sb.append("• Employee updated ").append(changes.getUpdatedStops().size()).append(" tour stop(s)\n\n");
        }
        if (changes.getDeletedStopIds() != null && !changes.getDeletedStopIds().isEmpty()) {
            sb.append("• Employee removed ").append(changes.getDeletedStopIds().size()).append(" tour stop(s)\n\n");
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

    /** True when the request is primarily for a city (add city, delete city, or add city+first map). Used to show "New City Request" instead of "New Map Request". */
    private static boolean isCityOnlyRequest(MapEditRequestDTO request) {
        if (request == null) return false;
        MapChanges ch = request.getChanges();
        if (ch == null) return request.getMapName() == null && request.getCityName() != null;
        if (ch.getDeletedCityIds() != null && !ch.getDeletedCityIds().isEmpty() && (ch.getMapId() == null || ch.getMapId() <= 0) && (ch.getNewMapName() == null || ch.getNewMapName().isEmpty()))
            return true;
        if (ch.isCreateNewCity() || (ch.getNewCities() != null && !ch.getNewCities().isEmpty())) return true;
        if (ch.getNewCityWithMap() != null && !ch.getNewCityWithMap().isEmpty()) return true;
        return false;
    }

    /** Display title for the list: "Delete city: X", "Add map: Y", "Update POI: Z", etc. */
    private static String getRequestDisplayName(MapEditRequestDTO req) {
        MapChanges ch = req.getChanges();
        String cityName = req.getCityName();
        String mapName = req.getMapName();
        if (ch != null) {
            String dispCity = (cityName != null && !cityName.isEmpty()) ? cityName : (ch.getDisplayCityName() != null ? ch.getDisplayCityName() : null);
            String dispMap = (mapName != null && !mapName.isEmpty()) ? mapName : (ch.getDisplayMapName() != null ? ch.getDisplayMapName() : null);
            // Prefer "add" over "delete" so mixed payloads (e.g. draft delete + new add city) show the intended action
            // Add city + first map
            if (ch.getNewCityWithMap() != null && !ch.getNewCityWithMap().isEmpty()) {
                MapChanges.CityWithMapRequest cwm = ch.getNewCityWithMap().get(0);
                String c = nullToEmpty(cwm.getCityName());
                String m = nullToEmpty(cwm.getMapName());
                if (!m.isEmpty()) return "Add city and map: " + c + " / " + m;
                return "Add city: " + c;
            }
            // Add city only
            if (ch.isCreateNewCity() || (ch.getNewCities() != null && !ch.getNewCities().isEmpty())) {
                String name = ch.getNewCityName();
                if (name == null && ch.getNewCities() != null && !ch.getNewCities().isEmpty())
                    name = ch.getNewCities().get(0).getName();
                return "Add city: " + nullToEmpty(name);
            }
            // Add first map of new city (city created in same request)
            if (ch.getNewMapCityName() != null && !ch.getNewMapCityName().trim().isEmpty() && ch.getNewMapName() != null && !ch.getNewMapName().isEmpty()) {
                return "Add first map: " + ch.getNewMapName() + " (city: " + ch.getNewMapCityName().trim() + ")";
            }
            // Add map (new map in existing city)
            if (ch.getNewMapName() != null && !ch.getNewMapName().isEmpty() && (ch.getMapId() == null || ch.getMapId() <= 0) && (ch.getCityId() == null || ch.getCityId() > 0)) {
                String inCity = (dispCity != null && !dispCity.isEmpty()) ? " (" + dispCity + ")" : "";
                return "Add map: " + ch.getNewMapName() + inCity;
            }
            // Delete city (only when no add-city/add-map intent)
            if (ch.getDeletedCityIds() != null && !ch.getDeletedCityIds().isEmpty()) {
                return "Delete city: " + (dispCity != null && !dispCity.isEmpty() ? dispCity : "ID " + ch.getDeletedCityIds().get(0));
            }
            // Delete map
            if (ch.getDeletedMapIds() != null && !ch.getDeletedMapIds().isEmpty()) {
                return "Delete map: " + (dispMap != null && !dispMap.isEmpty() ? dispMap + (dispCity != null && !dispCity.isEmpty() ? " (" + dispCity + ")" : "") : "ID " + ch.getDeletedMapIds().get(0));
            }
            // Add POI
            if (ch.getAddedPois() != null && !ch.getAddedPois().isEmpty()) {
                String n = ch.getAddedPois().get(0).getName();
                return "Add POI: " + (n != null && !n.isEmpty() ? n : "—");
            }
            // Update POI
            if (ch.getUpdatedPois() != null && !ch.getUpdatedPois().isEmpty()) {
                String n = ch.getUpdatedPois().get(0).getName();
                return "Update POI: " + (n != null && !n.isEmpty() ? n : "—");
            }
            // Delete POI
            if (ch.getDeletedPoiIds() != null && !ch.getDeletedPoiIds().isEmpty()) {
                String name = (ch.getDeletedPoiDisplayNames() != null && !ch.getDeletedPoiDisplayNames().isEmpty())
                        ? ch.getDeletedPoiDisplayNames().get(0) : ("id: " + ch.getDeletedPoiIds().get(0));
                return "Delete POI: \"" + name + "\"";
            }
            // Add tour
            if (ch.getAddedTours() != null && !ch.getAddedTours().isEmpty()) {
                String n = ch.getAddedTours().get(0).getName();
                return "Add tour: " + (n != null && !n.isEmpty() ? n : "—");
            }
            // Update tour
            if (ch.getUpdatedTours() != null && !ch.getUpdatedTours().isEmpty()) {
                String n = ch.getUpdatedTours().get(0).getName();
                return "Update tour: " + (n != null && !n.isEmpty() ? n : "—");
            }
            // Delete tour
            if (ch.getDeletedTourIds() != null && !ch.getDeletedTourIds().isEmpty()) {
                String name = (ch.getDeletedTourDisplayNames() != null && !ch.getDeletedTourDisplayNames().isEmpty())
                        ? ch.getDeletedTourDisplayNames().get(0) : ("id: " + ch.getDeletedTourIds().get(0));
                return "Delete tour: \"" + name + "\"";
            }
            // Update map info (name/description)
            if (ch.getNewMapName() != null && !ch.getNewMapName().isEmpty()) {
                return "Update map: " + ch.getNewMapName() + (dispCity != null && !dispCity.isEmpty() ? " (" + dispCity + ")" : "");
            }
            // Tour stops / links / unlinks
            if (ch.getAddedStops() != null && !ch.getAddedStops().isEmpty()) return "Add tour stop(s)";
            if (ch.getUpdatedStops() != null && !ch.getUpdatedStops().isEmpty()) return "Update tour stop(s)";
            if (ch.getDeletedStopIds() != null && !ch.getDeletedStopIds().isEmpty()) return "Remove tour stop(s)";
            if (ch.getPoiMapLinks() != null && !ch.getPoiMapLinks().isEmpty()) return "Link POI to map";
            if (ch.getPoiMapUnlinks() != null && !ch.getPoiMapUnlinks().isEmpty()) return "Unlink POI from map";
        }
        String dispCity = req.getCityName();
        if (ch != null && (dispCity == null || dispCity.isEmpty()) && ch.getDisplayCityName() != null) dispCity = ch.getDisplayCityName();
        String dispMap = req.getMapName();
        if (ch != null && (dispMap == null || dispMap.isEmpty()) && ch.getDisplayMapName() != null) dispMap = ch.getDisplayMapName();
        // Fallback
        if (dispMap != null && !dispMap.isEmpty())
            return "Update map: " + dispMap + (dispCity != null && !dispCity.isEmpty() ? " (" + dispCity + ")" : "");
        if (dispCity != null && !dispCity.isEmpty())
            return "Request: " + dispCity;
        return "Map edit request";
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
    public void onMyDraftReceived(MapEditRequestDTO draft) {
        // Not used in approvals screen
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
