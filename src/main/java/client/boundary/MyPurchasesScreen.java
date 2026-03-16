package client.boundary;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import common.Poi;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.EntitlementInfo;
import common.dto.MapContent;
import common.dto.MapSummary;
import common.dto.TourSegmentDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.Group;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for My Purchases screen.
 */
public class MyPurchasesScreen implements GCMClient.MessageHandler {

    @FXML
    private Label statusLabel;
    @FXML
    private TableView<PurchaseItem> subscriptionsTable;
    @FXML
    private TableColumn<PurchaseItem, String> subCityCol;
    @FXML
    private TableColumn<PurchaseItem, String> subStatusCol;
    @FXML
    private TableColumn<PurchaseItem, String> subExpiryCol;
    @FXML
    private TableColumn<PurchaseItem, String> subActionCol;

    @FXML
    private TableView<PurchaseItem> purchasesTable;
    @FXML
    private TableColumn<PurchaseItem, String> purchaseCityCol;
    @FXML
    private TableColumn<PurchaseItem, String> purchaseDateCol;
    @FXML
    private TableColumn<PurchaseItem, String> purchaseActionCol;

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
    private ObservableList<PurchaseItem> subscriptionsList = FXCollections.observableArrayList();
    private ObservableList<PurchaseItem> purchasesList = FXCollections.observableArrayList();

    /** When non-null, the map viewer popup is open; used to route GET_MAPS_FOR_CITY / GET_MAP_CONTENT responses. */
    private MapViewerPopupContext viewerPopup;

    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        setupTables();
        connectToServer();
    }

    private void setupTables() {
        // Subscriptions Columns
        subCityCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cityName));
        subStatusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status));
        subExpiryCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().dateInfo));

        subActionCol.setCellFactory(col -> new TableCell<PurchaseItem, String>() {
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(5);
            private final Button viewBtn = new Button("View maps");

            {
                viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                viewBtn.setOnAction(e -> {
                    PurchaseItem row = getTableView().getItems().get(getIndex());
                    handleDownload(row);
                });

                box.getChildren().add(viewBtn);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    PurchaseItem row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) {
                        viewBtn.setDisable(!row.canDownload);
                        viewBtn.setTooltip(!row.canDownload ? new Tooltip("Subscription expired")
                                : new Tooltip("View all city maps"));
                    }
                    setGraphic(box);
                }
            }
        });

        // Purchases Columns
        purchaseCityCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cityName));
        purchaseDateCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().dateInfo));

        purchaseActionCol.setCellFactory(col -> new TableCell<PurchaseItem, String>() {
            private final Button btn = new Button("Download map");
            {
                btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                btn.setOnAction(e -> {
                    PurchaseItem row = getTableView().getItems().get(getIndex());
                    handleDownload(row);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    PurchaseItem row = getTableRow() != null ? getTableRow().getItem() : null;
                    btn.setDisable(row != null && !row.canDownload);
                    btn.setTooltip(
                            row != null && !row.canDownload ? new Tooltip("One-time purchase: already downloaded")
                                    : null);
                    setGraphic(btn);
                }
            }
        });

        subscriptionsTable.setItems(subscriptionsList);
        purchasesTable.setItems(purchasesList);
    }

    private void connectToServer() {
        try {
            gcmClient = GCMClient.getInstance();
            gcmClient.setMessageHandler(this);
            statusLabel.setText("Connected");
            loadData();
        } catch (IOException e) {
            statusLabel.setText("Connection failed");
            e.printStackTrace();
        }
    }

    private void loadData() {
        if (gcmClient == null)
            return;
        String token = LoginController.currentSessionToken;
        if (token == null || token.isEmpty()) {
            statusLabel.setText("Login required to view purchases.");
            return;
        }
        try {
            statusLabel.setText("Loading purchases...");
            gcmClient.sendToServer(new Request(MessageType.GET_MY_PURCHASES, null, token));
        } catch (IOException e) {
            statusLabel.setText("Failed to load purchases");
            e.printStackTrace();
        }
    }

    private void handleDownload(PurchaseItem item) {
        if (gcmClient == null)
            return;
        String token = LoginController.currentSessionToken;
        if (token == null || token.isEmpty())
            return;
        try {
            // Record download/view event
            gcmClient.sendToServer(new Request(MessageType.DOWNLOAD_MAP_VERSION, item.cityId, token));
            statusLabel.setText(item.isSubscription ? "Opening " + item.cityName + " maps..."
                    : "Opening " + item.cityName + " maps...");
            openMapViewerPopup(item.cityId, item.cityName, token);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openMapViewerPopup(int cityId, String cityName, String token) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Maps - " + cityName);
        popupStage.setWidth(1000);
        popupStage.setHeight(700);

        Label popupStatusLabel = new Label("Loading maps...");
        popupStatusLabel.setStyle("-fx-text-fill: #7f8c8d;");

        ListView<MapSummary> mapsListView = new ListView<>();
        mapsListView.setPrefWidth(220);
        mapsListView.setCellFactory(lv -> new ListCell<MapSummary>() {
            @Override
            protected void updateItem(MapSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "📍 " + item.getName());
            }
        });

        // Placeholder until user selects a map – avoids creating Gluon MapView (and loading tiles) on open, reducing memory
        Label mapPlaceholder = new Label("Select a map from the list to view");
        mapPlaceholder.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
        StackPane mapPane = new StackPane(mapPlaceholder);
        mapPane.setMinWidth(400);
        mapPane.setStyle("-fx-background-color: #bdc3c7;");

        Label detailsLabel = new Label("Select a map to view POIs and tours.");
        detailsLabel.setWrapText(true);
        detailsLabel.setMaxWidth(280);
        detailsLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px;");

        VBox leftPanel = new VBox(10, new Label("Maps in " + cityName), mapsListView);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(240);
        leftPanel.setStyle("-fx-background-color: #ecf0f1;");

        VBox rightPanel = new VBox(10, new Label("Details"), detailsLabel);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setPrefWidth(300);
        rightPanel.setStyle("-fx-background-color: #ecf0f1;");

        SplitPane split = new SplitPane(leftPanel, mapPane, rightPanel);
        split.setDividerPositions(0.22, 0.78);

        BorderPane root = new BorderPane();
        root.setCenter(split);
        root.setBottom(popupStatusLabel);
        BorderPane.setMargin(popupStatusLabel, new Insets(5, 10, 10, 10));
        root.setStyle("-fx-background-color: #f5f6fa;");

        viewerPopup = new MapViewerPopupContext(popupStage, mapsListView, mapPane, popupStatusLabel, detailsLabel, cityId, token);
        popupStage.setScene(new Scene(root));
        popupStage.setOnHidden(e -> {
            if (viewerPopup != null) {
                viewerPopup.dispose();
                viewerPopup = null;
            }
        });

        mapsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && viewerPopup != null) {
                viewerPopup.requestMapContent(selected.getId());
            }
        });

        try {
            gcmClient.sendToServer(new Request(MessageType.GET_MAPS_FOR_CITY, cityId, token));
        } catch (IOException e) {
            viewerPopup.setError("Failed to request maps");
        }
        popupStage.show();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        navigateTo("/client/dashboard.fxml", "GCM Dashboard", 1000, 700);
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
    private void navigateTo(String fxml, String title, int width, int height) {
        try {
            // Do NOT close connection - singleton persistent session
            // if (gcmClient != null) gcmClient.closeConnection();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (msg instanceof Response) {
                Response response = (Response) msg;
                if (!response.isOk()) {
                    if (viewerPopup != null && (response.getRequestType() == MessageType.GET_MAPS_FOR_CITY
                            || response.getRequestType() == MessageType.GET_MAP_CONTENT)) {
                        viewerPopup.setError(response.getErrorMessage());
                    } else {
                        statusLabel.setText("Error: " + response.getErrorMessage());
                        statusLabel.setStyle("-fx-text-fill: red;");
                    }
                    return;
                }

                if (response.getRequestType() == MessageType.GET_MAPS_FOR_CITY && viewerPopup != null) {
                    Object payload = response.getPayload();
                    if (payload instanceof List) {
                        viewerPopup.applyMapsList((List<MapSummary>) payload);
                    }
                    return;
                }
                if (response.getRequestType() == MessageType.GET_MAP_CONTENT && viewerPopup != null) {
                    Object payload = response.getPayload();
                    if (payload instanceof MapContent) {
                        viewerPopup.applyMapContent((MapContent) payload);
                    }
                    return;
                }

                if (response.getRequestType() == MessageType.DOWNLOAD_MAP_VERSION) {
                    statusLabel.setText("Content loaded.");
                    statusLabel.setStyle("-fx-text-fill: green;");
                    // No alert when popup is open – popup shows the maps
                    if (viewerPopup == null) {
                        showAlert("Success", "Content loaded successfully. You can view or download the map.");
                    }
                } else if (response.getRequestType() == MessageType.GET_MY_PURCHASES) {
                    Object payload = response.getPayload();
                    if (payload instanceof List) {
                        List<EntitlementInfo> items = (List<EntitlementInfo>) payload;
                        populateTables(items);
                        statusLabel.setText("Loaded " + items.size() + " items");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    }
                }
            }
        });
    }

    private void populateTables(List<EntitlementInfo> items) {
        subscriptionsList.clear();
        purchasesList.clear();

        for (EntitlementInfo item : items) {
            String dateInfo = item.getExpiryDate() != null
                    ? item.getExpiryDate().toString()
                    : "Permanent";
            String status = item.isActive() ? (item.isCanceled() ? "Canceled" : "Active") : "Expired";

            PurchaseItem pi = new PurchaseItem(
                    item.getCityId(),
                    item.getCityName(),
                    status,
                    dateInfo,
                    item.isSubscription(),
                    item.isCanDownload());

            if (item.isSubscription()) {
                if (!item.isActive()) {
                    continue; // Skip expired subscriptions so they are removed from the list
                }
                subscriptionsList.add(pi);
            } else {
                purchasesList.add(pi);
            }
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ---------- Map viewer popup context and layers ----------

    private static Node createPinMarker() {
        Color red = Color.web("#e74c3c");
        double w = 10;
        double h = 22;
        double tipY = 0;
        double topY = -h;
        double shoulderY = -h + 6;
        Path teardrop = new Path();
        teardrop.getElements().add(new MoveTo(0, tipY));
        teardrop.getElements().add(new LineTo(-w, shoulderY));
        ArcTo arcTop = new ArcTo();
        arcTop.setRadiusX(w);
        arcTop.setRadiusY(w);
        arcTop.setX(w);
        arcTop.setY(shoulderY);
        arcTop.setSweepFlag(true);
        teardrop.getElements().add(arcTop);
        teardrop.getElements().add(new LineTo(0, tipY));
        teardrop.setFill(red);
        teardrop.setStroke(red.darker());
        teardrop.setStrokeWidth(0.5);
        Circle cutout = new Circle(0, topY + 6, 3.5);
        cutout.setFill(Color.WHITE);
        return new Group(teardrop, cutout);
    }

    private static class ViewerPoiMarkerLayer extends MapLayer {
        private final List<Pair<MapPoint, Node>> entries = new ArrayList<>();

        void addPoi(Poi p) {
            if (p == null) return;
            double lat = p.getLatitude() != null ? p.getLatitude() : 0;
            double lng = p.getLongitude() != null ? p.getLongitude() : 0;
            String name = p.getName() != null ? p.getName() : "";
            Node pin = createPinMarker();
            pin.setUserData(p.getId());
            StringBuilder tipText = new StringBuilder();
            if (!name.isEmpty()) tipText.append(name);
            if (p.getCategory() != null && !p.getCategory().isEmpty())
                tipText.append(tipText.length() > 0 ? "\n" : "").append("Category: ").append(p.getCategory());
            if (p.getShortExplanation() != null && !p.getShortExplanation().isEmpty())
                tipText.append(tipText.length() > 0 ? "\n" : "").append(p.getShortExplanation());
            if (tipText.length() == 0) tipText.append("POI");
            Tooltip.install(pin, new Tooltip(tipText.toString()));
            MapPoint point = new MapPoint(lat, lng);
            entries.add(new Pair<>(point, pin));
            getChildren().add(pin);
            markDirty();
        }

        void clearPois() {
            entries.clear();
            getChildren().clear();
            markDirty();
        }

        @Override
        protected void layoutLayer() {
            for (Pair<MapPoint, Node> entry : entries) {
                MapPoint mp = entry.getKey();
                Node node = entry.getValue();
                Point2D screen = baseMap.getMapPoint(mp.getLatitude(), mp.getLongitude());
                node.setVisible(true);
                node.setTranslateX(screen.getX());
                node.setTranslateY(screen.getY());
            }
        }
    }

    private static class ViewerTourRouteLayer extends MapLayer {
        private final List<Pair<TourSegmentDTO, Line>> segments = new ArrayList<>();

        void setSegments(List<TourSegmentDTO> list) {
            segments.clear();
            getChildren().clear();
            if (list == null) {
                markDirty();
                return;
            }
            for (TourSegmentDTO seg : list) {
                if (seg.getFromLat() == null || seg.getFromLon() == null || seg.getToLat() == null || seg.getToLon() == null) continue;
                Line line = new Line(0, 0, 0, 0);
                line.setStroke(Color.web("#3498db"));
                line.setStrokeWidth(2);
                String distText = seg.getDistanceMeters() != null ? String.format("%.0f m", seg.getDistanceMeters()) : "? m";
                Tooltip.install(line, new Tooltip(distText));
                segments.add(new Pair<>(seg, line));
                getChildren().add(line);
            }
            markDirty();
        }

        void clearSegments() {
            setSegments(null);
        }

        @Override
        protected void layoutLayer() {
            for (Pair<TourSegmentDTO, Line> entry : segments) {
                TourSegmentDTO seg = entry.getKey();
                Line line = entry.getValue();
                Point2D from = baseMap.getMapPoint(seg.getFromLat(), seg.getFromLon());
                Point2D to = baseMap.getMapPoint(seg.getToLat(), seg.getToLon());
                line.setStartX(from.getX());
                line.setStartY(from.getY());
                line.setEndX(to.getX());
                line.setEndY(to.getY());
                line.setVisible(true);
            }
        }
    }

    private class MapViewerPopupContext {
        private final ListView<MapSummary> mapsListView;
        private final StackPane mapPane;
        private final Label statusLabel;
        private final Label detailsLabel;
        private final String token;
        private MapView mapView;
        private ViewerPoiMarkerLayer poiLayer;
        private ViewerTourRouteLayer tourLayer;
        private static final double FALLBACK_LAT = 32.8;
        private static final double FALLBACK_LNG = 34.99;

        MapViewerPopupContext(Stage stage, ListView<MapSummary> mapsListView, StackPane mapPane,
                Label statusLabel, Label detailsLabel, int cityId, String token) {
            this.mapsListView = mapsListView;
            this.mapPane = mapPane;
            this.statusLabel = statusLabel;
            this.detailsLabel = detailsLabel;
            this.token = token;
        }

        /** Create MapView and layers only when first map content is loaded, to avoid loading tiles (and memory) on popup open. */
        private void ensureMapViewCreated() {
            if (mapView != null) return;
            mapView = new MapView();
            mapView.setZoom(12);
            poiLayer = new ViewerPoiMarkerLayer();
            tourLayer = new ViewerTourRouteLayer();
            mapView.addLayer(poiLayer);
            mapView.addLayer(tourLayer);
            mapPane.getChildren().clear();
            mapPane.getChildren().add(mapView);
        }

        void setError(String message) {
            statusLabel.setText("Error: " + message);
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }

        void applyMapsList(List<MapSummary> maps) {
            mapsListView.getItems().clear();
            if (maps != null && !maps.isEmpty()) {
                mapsListView.getItems().addAll(maps);
                statusLabel.setText(maps.size() + " map(s) – select one to view");
                statusLabel.setStyle("-fx-text-fill: #27ae60;");
            } else {
                statusLabel.setText("No maps in this city.");
                statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
            }
            if (poiLayer != null) poiLayer.clearPois();
            if (tourLayer != null) tourLayer.clearSegments();
            detailsLabel.setText("Select a map to view POIs and tours.");
        }

        void applyMapContent(MapContent content) {
            if (content == null) return;
            ensureMapViewCreated();
            poiLayer.clearPois();
            tourLayer.clearSegments();

            double centerLat = FALLBACK_LAT;
            double centerLng = FALLBACK_LNG;
            List<Poi> pois = content.getPois();
            if (pois != null && !pois.isEmpty()) {
                for (Poi p : pois) {
                    double lat = p.getLatitude() != null ? p.getLatitude() : parseLatLon(p.getLocation(), true);
                    double lng = p.getLongitude() != null ? p.getLongitude() : parseLatLon(p.getLocation(), false);
                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                        poiLayer.addPoi(p);
                        if (centerLat == FALLBACK_LAT) {
                            centerLat = lat;
                            centerLng = lng;
                        }
                    }
                }
            }
            List<TourSegmentDTO> segs = content.getTourSegments();
            if (segs != null && !segs.isEmpty()) {
                tourLayer.setSegments(segs);
            }
            mapView.flyTo(0, new MapPoint(centerLat, centerLng), 0.2);

            StringBuilder details = new StringBuilder();
            details.append(content.getMapName()).append("\n");
            details.append(pois != null ? pois.size() : 0).append(" POI(s)");
            if (content.getTours() != null && !content.getTours().isEmpty()) {
                details.append(", ").append(content.getTours().size()).append(" tour(s)");
            }
            details.append(".");
            detailsLabel.setText(details.toString());
            statusLabel.setText("Viewing: " + content.getMapName());
            statusLabel.setStyle("-fx-text-fill: #27ae60;");
        }

        void requestMapContent(int mapId) {
            statusLabel.setText("Loading map...");
            statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
            try {
                gcmClient.sendToServer(new Request(MessageType.GET_MAP_CONTENT, mapId, token));
            } catch (IOException e) {
                setError("Failed to load map");
            }
        }

        void dispose() {
            if (poiLayer != null) poiLayer.clearPois();
            if (tourLayer != null) tourLayer.clearSegments();
            mapPane.getChildren().clear();
            mapsListView.getItems().clear();
            mapView = null;
            poiLayer = null;
            tourLayer = null;
        }
    }

    private static double parseLatLon(String location, boolean wantLat) {
        if (location == null || location.trim().isEmpty()) return Double.NaN;
        String[] parts = location.split(",");
        if (parts.length < 2) return Double.NaN;
        try {
            return Double.parseDouble(parts[wantLat ? 0 : 1].trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // Helper class for table rows
    public static class PurchaseItem {
        int cityId;
        String cityName;
        String status;
        String dateInfo; // Expiry or Purchase Date
        boolean isSubscription;
        boolean canDownload;

        public PurchaseItem(int cityId, String cityName, String status, String dateInfo, boolean isSubscription) {
            this(cityId, cityName, status, dateInfo, isSubscription, true);
        }

        public PurchaseItem(int cityId, String cityName, String status, String dateInfo, boolean isSubscription,
                boolean canDownload) {
            this.cityId = cityId;
            this.cityName = cityName;
            this.status = status;
            this.dateInfo = dateInfo;
            this.isSubscription = isSubscription;
            this.canDownload = canDownload;
        }
    }
}
