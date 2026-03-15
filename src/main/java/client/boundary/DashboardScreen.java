package client.boundary;

import client.GCMClient;
import client.LoginController;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CityDTO;
import common.dto.NotificationDTO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Modern Dashboard screen controller.
 * Combines all features in a unified interface.
 */
public class DashboardScreen implements GCMClient.MessageHandler {

    @FXML
    private BorderPane rootPane;
    @FXML
    private Label statusLabel;
    @FXML
    private Button logoutBtn;
    @FXML
    private Label notificationBadge;
    @FXML
    private VBox guestDashboardPane;
    @FXML
    private AnchorPane guestMapAnchorPane;
    @FXML
    private WebView navbarLogoView;

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


    private GCMClient client;
    private boolean guestMode;
    private MapView guestMapView;
    private GuestPoiMarkerLayer guestPoiMarkerLayer;
    private static final ConcurrentHashMap<String, double[]> guestCityGeocodeCache = new ConcurrentHashMap<>();
    private static final String NAVBAR_LOGO_SVG_RESOURCE = "/client/assets/favicon.svg";
    private static final double GUEST_INITIAL_ZOOM = 3.2;
    private static final double GUEST_ZOOM_STEP = 0.4;
    private static final double GUEST_MIN_ZOOM = GUEST_INITIAL_ZOOM;

    @FXML
    public void initialize() {
        System.out.println("DashboardScreen: Initializing");

        applyNavbarLogoSvg();

        LoginController.UserRole role = LoginController.currentUserRole;
        String exactRole = LoginController.currentRoleString;
        guestMode = role == LoginController.UserRole.ANONYMOUS;

        if (guestMode && logoutBtn != null) {
            logoutBtn.setText("← Back");
        }

        configureSidebarButtons(role, exactRole);
        initializeGuestMap();

        try {
            client = GCMClient.getInstance();
            client.setMessageHandler(this);
            if (statusLabel != null) {
                statusLabel.setText("Connected to server");
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px;");
            }
            loadNotificationCount();
            requestCitiesForGuestMap();
        } catch (IOException e) {
            if (statusLabel != null) {
                statusLabel.setText("Connection Lost");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
            }
            e.printStackTrace();
        }
    }

    private void configureSidebarButtons(LoginController.UserRole role, String exactRole) {
        boolean isManager = role == LoginController.UserRole.MANAGER;
        boolean isCompanyManager = "COMPANY_MANAGER".equals(exactRole);
        boolean isSupportAgent = role == LoginController.UserRole.SUPPORT_AGENT;
        boolean isCustomer = role == LoginController.UserRole.CUSTOMER;
        boolean isAnonymous = role == LoginController.UserRole.ANONYMOUS;

        showButton(profileNavBtn, !isAnonymous);
        showButton(myPurchasesNavBtn, isCustomer);
        showButton(mapEditorNavBtn, !isCustomer && !isAnonymous);
        showButton(customersNavBtn, isManager);
        showButton(pricingNavBtn, isManager);
        showButton(pricingApprovalNavBtn, isCompanyManager);
        showButton(supportNavBtn, !isAnonymous);
        showButton(agentConsoleNavBtn, isSupportAgent);
        showButton(editApprovalsNavBtn, isManager);
        showButton(reportsNavBtn, isManager);
        showButton(userManagementNavBtn, isCompanyManager);
    }

    private void showButton(Button btn, boolean show) {
        if (btn != null) {
            btn.setVisible(show);
            btn.setManaged(show);
        }
    }

    private void applyNavbarLogoSvg() {
        if (navbarLogoView == null) {
            return;
        }
        java.net.URL svgUrl = getClass().getResource(NAVBAR_LOGO_SVG_RESOURCE);
        if (svgUrl == null) {
            navbarLogoView.setVisible(false);
            navbarLogoView.setManaged(false);
            return;
        }
        try {
            navbarLogoView.getEngine().load(svgUrl.toExternalForm());
        } catch (Exception e) {
            navbarLogoView.setVisible(false);
            navbarLogoView.setManaged(false);
        }
    }

    private void initializeGuestMap() {
        if (guestMapAnchorPane == null) {
            return;
        }

        System.setProperty("http.agent", "GCM-System/1.0 (team@gcm.local)");
        System.setProperty("java.net.preferIPv4Stack", "true");

        guestMapView = new MapView();
        guestMapView.setZoom(GUEST_INITIAL_ZOOM);
        guestMapView.flyTo(0, new MapPoint(20.0, 0.0), 0.2);

        guestPoiMarkerLayer = new GuestPoiMarkerLayer();
        guestMapView.addLayer(guestPoiMarkerLayer);
        guestMapView.addEventFilter(ScrollEvent.SCROLL, e -> {
            e.consume();
            if (guestMapView == null) {
                return;
            }
            if (e.getDeltaY() > 0) {
                guestMapView.setZoom(guestMapView.getZoom() + GUEST_ZOOM_STEP);
            } else if (e.getDeltaY() < 0) {
                guestMapView.setZoom(Math.max(GUEST_MIN_ZOOM, guestMapView.getZoom() - GUEST_ZOOM_STEP));
            }
            if (guestPoiMarkerLayer != null) {
                guestPoiMarkerLayer.refresh();
            }
        });

        AnchorPane.setTopAnchor(guestMapView, 0.0);
        AnchorPane.setBottomAnchor(guestMapView, 0.0);
        AnchorPane.setLeftAnchor(guestMapView, 0.0);
        AnchorPane.setRightAnchor(guestMapView, 0.0);
        guestMapAnchorPane.getChildren().clear();
        guestMapAnchorPane.getChildren().add(guestMapView);

        // Ensure initial world framing is applied after first layout pass.
        Platform.runLater(() -> {
            if (guestMapView != null) {
                guestMapView.setZoom(GUEST_INITIAL_ZOOM);
                guestMapView.flyTo(0, new MapPoint(20.0, 0.0), 0.2);
            }
        });
    }

    @FXML
    private void zoomInGuestMap(ActionEvent event) {
        if (guestMapView == null) {
            return;
        }
        guestMapView.setZoom(guestMapView.getZoom() + GUEST_ZOOM_STEP);
        if (guestPoiMarkerLayer != null) {
            guestPoiMarkerLayer.refresh();
        }
    }

    @FXML
    private void zoomOutGuestMap(ActionEvent event) {
        if (guestMapView == null) {
            return;
        }
        guestMapView.setZoom(Math.max(GUEST_MIN_ZOOM, guestMapView.getZoom() - GUEST_ZOOM_STEP));
        if (guestPoiMarkerLayer != null) {
            guestPoiMarkerLayer.refresh();
        }
    }

    private void requestCitiesForGuestMap() {
        if (client == null) {
            return;
        }
        try {
            client.sendToServer(new Request(MessageType.GET_CITIES, null));
        } catch (IOException e) {
            System.err.println("DashboardScreen: failed to request cities for guest map: " + e.getMessage());
        }
    }

    private void renderGuestCityMarkers(List<?> payload) {
        if (guestPoiMarkerLayer == null) {
            return;
        }
        guestPoiMarkerLayer.clearPois();
        if (payload == null || payload.isEmpty()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (Object item : payload) {
            if (!(item instanceof CityDTO city) || city.getName() == null) {
                continue;
            }
            String name = city.getName().trim();
            if (name.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase();
            if (!seen.add(key)) {
                continue;
            }
            geocodeCityNameAsync(name, coords -> {
                if (coords != null && guestPoiMarkerLayer != null) {
                    guestPoiMarkerLayer.addCity(name, coords[0], coords[1]);
                }
            });
        }
    }

    private void geocodeCityNameAsync(String cityName, Consumer<double[]> onResult) {
        if (cityName == null || cityName.trim().isEmpty()) {
            Platform.runLater(() -> onResult.accept(null));
            return;
        }
        String trimmed = cityName.trim();
        String key = trimmed.toLowerCase();
        double[] cached = guestCityGeocodeCache.get(key);
        if (cached != null) {
            Platform.runLater(() -> onResult.accept(cached));
            return;
        }

        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
                URI uri = URI.create("https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1");
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(7))
                        .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(uri)
                        .header("User-Agent", "GCM-System/1.0 (Java)")
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> response = httpClient.send(
                        request,
                        java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    Platform.runLater(() -> onResult.accept(null));
                    return;
                }
                JsonArray arr = new Gson().fromJson(response.body(), JsonArray.class);
                if (arr == null || arr.isEmpty()) {
                    Platform.runLater(() -> onResult.accept(null));
                    return;
                }
                JsonObject first = arr.get(0).getAsJsonObject();
                double lat = Double.parseDouble(first.get("lat").getAsString());
                double lon = Double.parseDouble(first.get("lon").getAsString());
                double[] coords = new double[] { lat, lon };
                guestCityGeocodeCache.put(key, coords);
                Platform.runLater(() -> onResult.accept(coords));
            } catch (Exception e) {
                Platform.runLater(() -> onResult.accept(null));
            }
        }, "Guest-City-Geocode-" + key).start();
    }

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

    private static class GuestPoiMarkerLayer extends MapLayer {
        private final List<Pair<MapPoint, Node>> entries = new ArrayList<>();

        void addCity(String cityName, double lat, double lon) {
            Node pin = createPinMarker();
            String name = cityName != null && !cityName.isBlank() ? cityName : "City";
            Tooltip.install(pin, new Tooltip(name));
            entries.add(new Pair<>(new MapPoint(lat, lon), pin));
            getChildren().add(pin);
            markDirty();
        }

        void clearPois() {
            entries.clear();
            getChildren().clear();
            markDirty();
        }

        void refresh() {
            markDirty();
        }

        @Override
        protected void layoutLayer() {
            for (Pair<MapPoint, Node> entry : entries) {
                MapPoint mapPoint = entry.getKey();
                Node node = entry.getValue();
                Point2D screenPoint = baseMap.getMapPoint(mapPoint.getLatitude(), mapPoint.getLongitude());
                node.setVisible(true);
                node.setTranslateX(screenPoint.getX());
                node.setTranslateY(screenPoint.getY());
            }
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
    private void openSearchScreenFromAction(ActionEvent event) {
        navigateTo("/client/catalog_search.fxml", "GCM - City Catalog", 1100, 750);
    }

    @FXML
    private void openMapEditorFromMenu(ActionEvent event) {
        navigateTo("/client/map_editor.fxml", "GCM - Map Editor", 1200, 800);
    }

    @FXML
    private void openMyPurchasesFromMenu(ActionEvent event) {
        navigateTo("/client/my_purchases.fxml", "My Purchases", 900, 600);
    }

    @FXML
    private void openProfileFromMenu(ActionEvent event) {
        navigateTo("/client/profile.fxml", "My Profile", 1000, 700);
    }

    @FXML
    private void openAdminCustomersFromMenu(ActionEvent event) {
        navigateTo("/client/admin_customers.fxml", "Customer Management", 1200, 800);
    }

    @FXML
    private void openPricingFromMenu(ActionEvent event) {
        navigateTo("/client/pricing_screen.fxml", "Pricing Management", 1100, 700);
    }

    @FXML
    private void openPricingApprovalFromMenu(ActionEvent event) {
        navigateTo("/client/pricing_approval.fxml", "Pricing Approval", 1100, 700);
    }

    @FXML
    private void openSupportFromMenu(ActionEvent event) {
        navigateTo("/client/support_screen.fxml", "GCM Support Center", 800, 600);
    }

    @FXML
    private void openAgentConsoleFromMenu(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/agent_console.fxml"));
            Parent root = loader.load();
            Stage stage = getCurrentStage();
            if (stage == null) return;
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("Agent Console");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            System.err.println("Error loading agent_console.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void openEditApprovalsFromMenu(ActionEvent event) {
        navigateTo("/client/map_approvals.fxml", "Edit Request Approvals", 1150, 750);
    }

    @FXML
    private void openReportsFromMenu(ActionEvent event) {
        try {
            java.net.URL reportFxml = getClass().getResource("/client/reports.fxml");
            if (reportFxml == null) {
                reportFxml = getClass().getClassLoader().getResource("client/reports.fxml");
            }
            if (reportFxml == null) {
                showAlert(Alert.AlertType.ERROR, "Cannot open reports",
                        "Resource not found", "reports.fxml was not found.");
                return;
            }
            FXMLLoader loader = new FXMLLoader(reportFxml);
            Parent root = loader.load();
            ReportsController reportsController = loader.getController();
            if (reportsController != null && client != null) {
                reportsController.setClient(client);
            }
            Stage stage = getCurrentStage();
            if (stage == null) return;
            stage.setScene(new Scene(root, 900, 700));
            stage.setTitle("GCM - City Reports");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (Exception e) {
            System.err.println("DashboardScreen: Failed to open reports: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void openUserManagementFromMenu(ActionEvent event) {
        navigateTo("/client/user_management.fxml", "User Management", 1200, 800);
    }

    @FXML
    private void toggleGuestDashboard(ActionEvent event) {
        if (guestDashboardPane == null) {
            return;
        }
        boolean nextVisible = !guestDashboardPane.isVisible();
        guestDashboardPane.setVisible(nextVisible);
        guestDashboardPane.setManaged(nextVisible);
    }

    @FXML
    private void openNotifications(MouseEvent event) {
        if (LoginController.currentUserRole == LoginController.UserRole.ANONYMOUS) {
            showAlert(Alert.AlertType.WARNING, "Access Denied", "Login Required",
                    "Please login to view notifications.");
            return;
        }
        showNotificationsDialog();
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

            // Add 'Mark as Read' button for unread notifications
            if (!n.isRead()) {
                Button markReadBtn = new Button("Mark as Read");
                markReadBtn.setStyle(
                        "-fx-font-size: 11px; -fx-background-color: transparent; -fx-text-fill: #007bff; -fx-cursor: hand; -fx-padding: 0;");
                markReadBtn.setOnAction(e -> {
                    if (client != null) {
                        try {
                            String token = LoginController.currentSessionToken;
                            Request request = new Request(MessageType.MARK_NOTIFICATION_READ, n.getId(), token);
                            client.sendToServer(request);

                            // Visually update the UI inline
                            card.setStyle(
                                    "-fx-background-color: #ffffff; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ddd; -fx-border-width: 1;");
                            titleLabel.setText(n.getTitle()); // Remove the blue dot
                            card.getChildren().remove(markReadBtn); // Hide the button
                            n.setRead(true);

                            // Optimistically decrement badge counter
                            int currentCount = 0;
                            try {
                                String countStr = notificationBadge.getText();
                                if (countStr.equals("9+")) {
                                    // Just reload it in this edge case
                                    loadNotificationCount();
                                } else if (!countStr.isEmpty()) {
                                    currentCount = Integer.parseInt(countStr);
                                    updateNotificationBadge(currentCount - 1);
                                }
                            } catch (Exception ex) {
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                });
                card.getChildren().add(markReadBtn);
            }

            pendingNotificationsContent.getChildren().add(card);
        }
    }

    // ==================== Navigation ====================

    @FXML
    private void handleLogout(ActionEvent event) {
        // Tell the server to invalidate the session so the same user can log in again
        // later
        String token = LoginController.currentSessionToken;
        if (token != null && !token.isEmpty() && client != null) {
            try {
                // Wait for server to process LOGOUT before closing (avoids "already logged in"
                // on next login)
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
            Stage stage = getCurrentStage();
            if (stage == null) {
                return;
            }
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            System.err.println("DashboardScreen: Could not navigate to " + fxml + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Stage getCurrentStage() {
        if (rootPane != null && rootPane.getScene() != null && rootPane.getScene().getWindow() instanceof Stage) {
            return (Stage) rootPane.getScene().getWindow();
        }
        return null;
    }

    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (msg instanceof Response response) {
                if (response.getRequestType() == MessageType.GET_UNREAD_COUNT && response.isOk()) {
                    int count = (Integer) response.getPayload();
                    updateNotificationBadge(count);
                    return;
                }
                if (response.getRequestType() == MessageType.GET_MY_NOTIFICATIONS && response.isOk()) {
                    if (response.getPayload() instanceof List<?> payloadList) {
                        List<NotificationDTO> notifications = new ArrayList<>();
                        for (Object item : payloadList) {
                            if (item instanceof NotificationDTO notification) {
                                notifications.add(notification);
                            }
                        }
                        displayNotifications(notifications);
                    } else {
                        displayNotifications(java.util.Collections.emptyList());
                    }
                    loadNotificationCount();
                    return;
                }
                if (response.getRequestType() == MessageType.GET_CITIES && response.isOk()
                        && response.getPayload() instanceof List<?> list) {
                    renderGuestCityMarkers(list);
                }
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
