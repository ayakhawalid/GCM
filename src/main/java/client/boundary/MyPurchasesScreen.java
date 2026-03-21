package client.boundary;

import client.GCMClient;
import client.LoginController;
import client.MenuNavigationHelper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for My Purchases screen.
 */
public class MyPurchasesScreen implements GCMClient.MessageHandler {
    private static final String BACK_BTN_BASE_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #7f8c8d; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";
    private static final String BACK_BTN_HOVER_STYLE =
            "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #111111; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10; -fx-padding: 6 10;";

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

    /** If set (e.g. after one-time purchase), auto-open map viewer for this city once purchases load. */
    public static volatile Integer AUTO_OPEN_CITY_ID = null;
    public static volatile String AUTO_OPEN_CITY_NAME = null;

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
        centerTextColumn(subCityCol);
        centerTextColumn(subStatusCol);
        centerTextColumn(subExpiryCol);

        subActionCol.setCellFactory(col -> new TableCell<PurchaseItem, String>() {
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(5);
            private final Button viewBtn = new Button("View maps");

            {
                box.setAlignment(Pos.CENTER);
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
                setAlignment(Pos.CENTER);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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
        centerTextColumn(purchaseCityCol);
        centerTextColumn(purchaseDateCol);

        // One-time purchases: no Actions column (removed from FXML). Keep null-safe for older FXMLs.
        if (purchaseActionCol != null) {
            purchaseActionCol.setCellFactory(col -> new TableCell<PurchaseItem, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setAlignment(Pos.CENTER);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setGraphic(null);
                }
            });
        }

        subscriptionsTable.setItems(subscriptionsList);
        purchasesTable.setItems(purchasesList);
    }

    private void centerTextColumn(TableColumn<PurchaseItem, String> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
                setText(empty ? null : item);
            }
        });
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
            // One-time: consume download slot; subscription: authorize (no report stats here)
            gcmClient.sendToServer(new Request(MessageType.DOWNLOAD_MAP_VERSION, item.cityId, token));
            statusLabel.setText(item.isSubscription ? "Opening " + item.cityName + " maps..."
                    : "Opening " + item.cityName + " maps...");
            // One-time purchase: this click uses the download slot — disable button immediately
            if (!item.isSubscription) {
                item.canDownload = false;
                purchasesTable.refresh();
            }
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
                setText(empty || item == null ? null : item.getName());
            }
        });
        ListView<MapSummary> toursListView = new ListView<>();
        toursListView.setPrefHeight(150);
        toursListView.setCellFactory(lv -> new ListCell<MapSummary>() {
            @Override
            protected void updateItem(MapSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        // Placeholder until user selects a map – avoids creating Gluon MapView (and loading tiles) on open, reducing memory
        Label mapPlaceholder = new Label("Select a map from the list to view");
        mapPlaceholder.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
        StackPane mapPane = new StackPane(mapPlaceholder);
        mapPane.setMinWidth(400);
        mapPane.setStyle("-fx-background-color: #bdc3c7;");

        Label mapsHeader = new Label("Maps in " + cityName);
        mapsHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Label toursHeader = new Label("Tours");
        toursHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        VBox leftPanel = new VBox(10, mapsHeader, mapsListView, new Separator(), toursHeader, toursListView);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(240);
        leftPanel.setStyle("-fx-background-color: #ecf0f1;");

        Label poisHeader = new Label("POIs");
        poisHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        ListView<String> poisListView = new ListView<>();
        poisListView.setPrefHeight(200);
        poisListView.setPlaceholder(new Label("No POIs in this map."));
        Label cityDescHeader = new Label("City Description");
        cityDescHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Label cityDescValueLabel = new Label("No city description available.");
        cityDescValueLabel.setWrapText(true);
        cityDescValueLabel.setStyle("-fx-text-fill: #34495e;");

        Label mapDescHeader = new Label("Map Description");
        mapDescHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Label mapDescValueLabel = new Label("");
        mapDescValueLabel.setWrapText(true);
        mapDescValueLabel.setStyle("-fx-text-fill: #34495e;");
        VBox mapDescSection = new VBox(4, mapDescHeader, mapDescValueLabel);
        mapDescSection.setVisible(false);
        mapDescSection.setManaged(false);

        VBox rightPanel = new VBox(10, poisHeader, poisListView, new Separator(), cityDescHeader, cityDescValueLabel, mapDescSection);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setPrefWidth(300);
        rightPanel.setStyle("-fx-background-color: #ecf0f1;");

        SplitPane split = new SplitPane(leftPanel, mapPane, rightPanel);
        split.setDividerPositions(0.22, 0.78);

        BorderPane root = new BorderPane();
        root.setCenter(split);
        // Bottom bar: status + dummy download button (demo only)
        Button dummyDownloadBtn = new Button("⬇");
        dummyDownloadBtn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #3498db; -fx-font-weight: bold; -fx-font-size: 22px; -fx-padding: 2 10; -fx-cursor: hand;");
        dummyDownloadBtn.setMinWidth(52);
        dummyDownloadBtn.setMinHeight(38);
        Tooltip dlTip = new Tooltip("Download (demo only)");
        dlTip.setStyle("-fx-text-fill: black; -fx-font-size: 12px; -fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        dummyDownloadBtn.setTooltip(dlTip);
        final int reportCityId = cityId;
        dummyDownloadBtn.setOnAction(e -> {
            popupStatusLabel.setText("Downloaded (demo).");
            try {
                gcmClient.sendToServer(new Request(MessageType.RECORD_DUMMY_MAP_DOWNLOAD,
                        String.valueOf(reportCityId), token));
            } catch (IOException ignored) {
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomBar = new HBox(10, popupStatusLabel, spacer, dummyDownloadBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(bottomBar, new Insets(5, 10, 10, 10));
        root.setBottom(bottomBar);
        root.setStyle("-fx-background-color: #f5f6fa;");

        viewerPopup = new MapViewerPopupContext(popupStage, mapsListView, toursListView, poisListView, cityDescValueLabel, mapDescValueLabel, mapDescSection, mapPane, popupStatusLabel, cityName, token);
        popupStage.setScene(new Scene(root));
        popupStage.setOnHidden(e -> {
            if (viewerPopup != null) {
                viewerPopup.dispose();
                viewerPopup = null;
            }
        });

        mapsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && viewerPopup != null) {
                toursListView.getSelectionModel().clearSelection();
                viewerPopup.requestMapContent(selected.getId());
            }
        });
        toursListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && viewerPopup != null) {
                mapsListView.getSelectionModel().clearSelection();
                viewerPopup.requestMapContent(selected.getId());
            }
        });

        try {
            gcmClient.sendToServer(new Request(MessageType.GET_MAPS_FOR_CITY, cityId, token));
        } catch (IOException e) {
            viewerPopup.setError("Failed to request maps");
        }
        popupStage.show();
        try {
            gcmClient.sendToServer(new Request(MessageType.RECORD_VIEW_EVENT, String.valueOf(cityId), token));
        } catch (IOException ignored) {
        }
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

                        // Auto-open maps popup once (used after one-time purchase).
                        if (AUTO_OPEN_CITY_ID != null) {
                            Integer cityId = AUTO_OPEN_CITY_ID;
                            String cityName = AUTO_OPEN_CITY_NAME;
                            AUTO_OPEN_CITY_ID = null;
                            AUTO_OPEN_CITY_NAME = null;

                            if (cityName == null || cityName.isEmpty()) {
                                for (EntitlementInfo e : items) {
                                    if (e != null && e.getCityId() == cityId.intValue()) {
                                        cityName = e.getCityName();
                                        break;
                                    }
                                }
                            }
                            if (cityName == null || cityName.isEmpty()) {
                                cityName = "City " + cityId;
                            }
                            String token = LoginController.currentSessionToken;
                            if (token != null && !token.isEmpty()) {
                                openMapViewerPopup(cityId, cityName, token);
                            }
                        }
                    }
                }
            }
        });
    }

    private void populateTables(List<EntitlementInfo> items) {
        subscriptionsList.clear();
        purchasesList.clear();

        for (EntitlementInfo item : items) {
            String dateInfo;
            if (item.isSubscription()) {
                dateInfo = item.getExpiryDate() != null ? item.getExpiryDate().toString() : "";
            } else {
                dateInfo = item.getPurchaseDate() != null ? item.getPurchaseDate().toString() : "";
            }
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
        private final ListView<MapSummary> toursListView;
        private final ListView<String> poisListView;
        private final Label cityDescriptionLabel;
        private final Label mapDescriptionLabel;
        private final VBox mapDescriptionSection;
        private final StackPane mapPane;
        private final Label statusLabel;
        private final String cityName;
        private final String token;
        private MapView mapView;
        private ViewerPoiMarkerLayer poiLayer;
        private ViewerTourRouteLayer tourLayer;
        private static final double FALLBACK_LAT = 32.8;
        private static final double FALLBACK_LNG = 34.99;
        private static final ConcurrentHashMap<String, double[]> geocodeCache = new ConcurrentHashMap<>();

        MapViewerPopupContext(Stage stage, ListView<MapSummary> mapsListView, ListView<MapSummary> toursListView,
                ListView<String> poisListView, Label cityDescriptionLabel, Label mapDescriptionLabel, VBox mapDescriptionSection, StackPane mapPane, Label statusLabel, String cityName, String token) {
            this.mapsListView = mapsListView;
            this.toursListView = toursListView;
            this.poisListView = poisListView;
            this.cityDescriptionLabel = cityDescriptionLabel;
            this.mapDescriptionLabel = mapDescriptionLabel;
            this.mapDescriptionSection = mapDescriptionSection;
            this.mapPane = mapPane;
            this.statusLabel = statusLabel;
            this.cityName = cityName;
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
            toursListView.getItems().clear();
            if (maps != null && !maps.isEmpty()) {
                for (MapSummary map : maps) {
                    if (map != null && map.getTourId() != null && map.getTourId() > 0) {
                        toursListView.getItems().add(map);
                    } else {
                        mapsListView.getItems().add(map);
                    }
                }
                int totalCount = mapsListView.getItems().size() + toursListView.getItems().size();
                statusLabel.setText(totalCount + " map(s) – select one to view");
                statusLabel.setStyle("-fx-text-fill: #27ae60;");
            } else {
                statusLabel.setText("No maps in this city.");
                statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
            }
            if (poiLayer != null) poiLayer.clearPois();
            if (tourLayer != null) tourLayer.clearSegments();
            poisListView.getItems().clear();
            String cityDescFromList = null;
            if (!mapsListView.getItems().isEmpty()) {
                cityDescFromList = mapsListView.getItems().get(0).getCityDescription();
            } else if (!toursListView.getItems().isEmpty()) {
                cityDescFromList = toursListView.getItems().get(0).getCityDescription();
            }
            cityDescriptionLabel.setText(cityDescFromList != null && !cityDescFromList.isBlank()
                    ? cityDescFromList
                    : "No city description available.");
            mapDescriptionLabel.setText("");
            mapDescriptionSection.setManaged(false);
            mapDescriptionSection.setVisible(false);
        }

        void applyMapContent(MapContent content) {
            if (content == null) return;
            ensureMapViewCreated();
            poiLayer.clearPois();
            tourLayer.clearSegments();
            poisListView.getItems().clear();

            double centerLat = FALLBACK_LAT;
            double centerLng = FALLBACK_LNG;
            List<Poi> pois = content.getPois();
            if (pois != null && !pois.isEmpty()) {
                for (Poi p : pois) {
                    double lat = p.getLatitude() != null ? p.getLatitude() : parseLatLon(p.getLocation(), true);
                    double lng = p.getLongitude() != null ? p.getLongitude() : parseLatLon(p.getLocation(), false);
                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                        poiLayer.addPoi(p);
                        String poiName = p.getName() != null && !p.getName().isBlank() ? p.getName() : "POI";
                        poisListView.getItems().add(poiName);
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
                if (centerLat == FALLBACK_LAT) {
                    TourSegmentDTO first = segs.get(0);
                    if (first.getFromLat() != null && first.getFromLon() != null) {
                        centerLat = first.getFromLat();
                        centerLng = first.getFromLon();
                    }
                }
            }
            if (centerLat == FALLBACK_LAT && cityName != null && !cityName.isBlank()) {
                resolveCityCenter(cityName, coords -> {
                    double lat = coords != null ? coords[0] : FALLBACK_LAT;
                    double lon = coords != null ? coords[1] : FALLBACK_LNG;
                    mapView.flyTo(0, new MapPoint(lat, lon), 0.2);
                });
            } else {
                mapView.flyTo(0, new MapPoint(centerLat, centerLng), 0.2);
            }
            statusLabel.setText("Viewing: " + content.getMapName());
            statusLabel.setStyle("-fx-text-fill: #27ae60;");
            String cityDesc = content.getCityDescription();
            cityDescriptionLabel.setText(cityDesc != null && !cityDesc.isBlank() ? cityDesc : "No city description available.");
            String mapDesc = content.getShortDescription();
            mapDescriptionLabel.setText(mapDesc != null && !mapDesc.isBlank() ? mapDesc : "No map description available.");
            mapDescriptionSection.setManaged(true);
            mapDescriptionSection.setVisible(true);
        }

        private void resolveCityCenter(String city, java.util.function.Consumer<double[]> onResult) {
            String key = city.toLowerCase().trim();
            double[] cached = geocodeCache.get(key);
            if (cached != null) {
                onResult.accept(cached);
                return;
            }
            new Thread(() -> {
                try {
                    String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
                    URI uri = URI.create("https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1");
                    java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(7))
                            .build();
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(uri)
                            .header("User-Agent", "GCM-System/1.0 (Java)")
                            .GET()
                            .build();
                    java.net.http.HttpResponse<String> res = http.send(req,
                            java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (res.statusCode() != 200) {
                        Platform.runLater(() -> onResult.accept(null));
                        return;
                    }
                    JsonArray arr = new Gson().fromJson(res.body(), JsonArray.class);
                    if (arr == null || arr.isEmpty()) {
                        Platform.runLater(() -> onResult.accept(null));
                        return;
                    }
                    JsonObject first = arr.get(0).getAsJsonObject();
                    double lat = Double.parseDouble(first.get("lat").getAsString());
                    double lon = Double.parseDouble(first.get("lon").getAsString());
                    double[] coords = new double[] { lat, lon };
                    geocodeCache.put(key, coords);
                    Platform.runLater(() -> onResult.accept(coords));
                } catch (Exception e) {
                    Platform.runLater(() -> onResult.accept(null));
                }
            }, "MyPurchases-Geocode-" + key).start();
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
            toursListView.getItems().clear();
            poisListView.getItems().clear();
            cityDescriptionLabel.setText("");
            mapDescriptionLabel.setText("");
            mapDescriptionSection.setManaged(false);
            mapDescriptionSection.setVisible(false);
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
