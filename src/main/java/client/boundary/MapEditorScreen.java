package client.boundary;

import client.LoginController;
import client.control.ContentManagementControl;
import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import common.Poi;
import common.dto.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Pair;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Controller for the Map Editor screen.
 * Handles all map editing operations including POIs and Tours.
 */
public class MapEditorScreen implements ContentManagementControl.ContentCallback {

    // Header
    @FXML
    private Label statusLabel;
    @FXML
    private Button backButton;
    @FXML
    private Button sendToManagerButton;

    // Left panel - City/Map selection
    @FXML
    private ComboBox<CityDTO> cityComboBox;
    @FXML
    private ListView<MapSummary> mapsListView;
    @FXML
    private Button createCityBtn;
    @FXML
    private Button createMapBtn;
    @FXML
    private Button removeMapBtn;
    @FXML
    private Button editPoiBtn;
    @FXML
    private Button removePoiFromMapBtn;
    @FXML
    private Button addExistingPoiToMapBtn;
    @FXML
    private Button editTourBtn;
    @FXML
    private Button removeStopBtn;
    @FXML
    private Button editStopBtn;

    // Center - Tabs
    @FXML
    private TabPane contentTabs;

    // POIs Tab
    @FXML
    private ListView<Poi> poisListView;
    @FXML
    private VBox poiEditForm;
    @FXML
    private TextField poiNameField;
    @FXML
    private ComboBox<String> poiCategoryCombo;
    @FXML
    private TextField poiLocationField;
    @FXML
    private CheckBox poiAccessibleCheck;
    @FXML
    private TextArea poiDescArea;

    // Tours Tab
    @FXML
    private ListView<TourDTO> toursListView;
    @FXML
    private VBox tourStopsSection;
    @FXML
    private Label tourNameLabel;
    @FXML
    private ListView<TourStopDTO> tourStopsListView;
    @FXML
    private VBox tourEditForm;
    @FXML
    private TextField tourNameField;
    @FXML
    private TextField tourDurationField;
    @FXML
    private TextArea tourDescArea;

    // Map Tab – Gluon Maps (native JavaFX, no WebView)
    @FXML
    private AnchorPane mapTabAnchorPane;
    private MapView mapView;
    private PoiMarkerLayer poiMarkerLayer;

    // Map Info Tab
    @FXML
    private TextField mapNameField;
    @FXML
    private TextArea mapDescArea;

    // State
    private ContentManagementControl control;
    private CityDTO selectedCity;
    private MapSummary selectedMap;
    private MapContent currentMapContent;
    private Poi editingPoi;
    private TourDTO editingTour;
    private boolean hasUnsavedChanges = false;
    private int pendingSelectCityId = -1;
    private int pendingSelectMapId = -1;
    /** City ID for which we last requested maps (so we ignore stale responses). */
    private int lastRequestedCityId = -1;
    private MapChanges pendingChanges = new MapChanges(); // Collect changes locally
    /** When we receive city POIs, run this (e.g. show Add Stop or Add existing POI dialog). */
    private Runnable pendingCityPoisCallback = null;
    /** Last city POIs received (for use in pending callback). */
    private List<Poi> lastCityPois = null;

    /**
     * Initialize the controller.
     */
    @FXML
    public void initialize() {
        System.out.println("MapEditorScreen: Initializing");

        // Show "Publish" for content managers, "Send to content manager" for editors
        if (sendToManagerButton != null && LoginController.currentUserRole == LoginController.UserRole.MANAGER) {
            sendToManagerButton.setText("📤 Publish");
        }

        // Setup POI categories
        poiCategoryCombo.setItems(FXCollections.observableArrayList(
                "Museum", "Beach", "Historic", "Religious", "Park", "Shopping",
                "Restaurant", "Entertainment", "Cultural", "Nature", "Other"));

        // Connect to server
        try {
            control = new ContentManagementControl("localhost", 5555);
            control.setCallback(this);

            // Load cities
            control.getCities();

        } catch (IOException e) {
            showError("Failed to connect to server");
            e.printStackTrace();
        }

        // City selection: both listener and onAction so maps refresh when user changes city from dropdown
        Runnable onCityChanged = () -> {
            if (control == null) return;
            CityDTO newCity = cityComboBox.getValue();
            selectedCity = newCity;
            if (selectedCity != null) {
                lastRequestedCityId = selectedCity.getId();
                mapsListView.getItems().clear();
                mapsListView.getSelectionModel().clearSelection();
                clearMapContent();
                control.getMapsForCity(selectedCity.getId());
                createMapBtn.setDisable(false);
                setStatus("Loading maps for " + selectedCity.getName() + "...");
            } else {
                lastRequestedCityId = -1;
                createMapBtn.setDisable(false); // allow "Create new map" → creates new city + map when no city selected
                mapsListView.getItems().clear();
                mapsListView.getSelectionModel().clearSelection();
                clearMapContent();
                setStatus("No city selected – you can still create a new map (and city)");
            }
        };

        cityComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> onCityChanged.run());
        cityComboBox.setOnAction(e -> onCityChanged.run());

        // Map selection listener
        mapsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newMap) -> {
            selectedMap = newMap;
            if (removeMapBtn != null) removeMapBtn.setDisable(selectedMap == null);
            if (control == null) return;
            if (selectedMap != null) {
                control.getMapContent(selectedMap.getId());
            } else {
                clearMapContent();
            }
        });

        // POI selection listener: show edit form and enable Edit / Remove from map
        poisListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newPoi) -> {
            if (newPoi != null) {
                showPoiEditForm(newPoi);
                if (editPoiBtn != null) editPoiBtn.setDisable(false);
                if (removePoiFromMapBtn != null) removePoiFromMapBtn.setDisable(false);
            } else {
                if (editPoiBtn != null) editPoiBtn.setDisable(true);
                if (removePoiFromMapBtn != null) removePoiFromMapBtn.setDisable(true);
            }
        });

        // Tour selection listener: show stops and edit form, enable Edit Tour button
        toursListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newTour) -> {
            if (newTour != null) {
                showTourDetails(newTour);
                showTourEditForm(newTour);
                if (editTourBtn != null) editTourBtn.setDisable(false);
            } else {
                tourStopsSection.setVisible(false);
                tourStopsSection.setManaged(false);
                if (editTourBtn != null) editTourBtn.setDisable(true);
            }
        });

        // Tour stop selection: enable Edit Stop and Remove Stop
        tourStopsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newStop) -> {
            if (removeStopBtn != null) removeStopBtn.setDisable(newStop == null);
            if (editStopBtn != null) editStopBtn.setDisable(newStop == null);
        });

        // Custom cell factories
        setupCellFactories();

        // ── Gluon Maps setup ─────────────────────────────────────────────────
        if (mapTabAnchorPane != null) {
            // Fix for slow tile loading (30s+ delays):
            // 1. OSM heavily limits default Java HTTP clients; we must provide a real User-Agent.
            System.setProperty("http.agent", "GCM-System/1.0 (your_email@example.com)");
            // 2. Windows sometimes stalls for ~30s trying IPv6 resolving before falling back to IPv4.
            System.setProperty("java.net.preferIPv4Stack", "true");

            mapView = new MapView();
            mapView.setZoom(13);
            mapView.flyTo(0, new MapPoint(32.8, 34.99), 0.1); // default: Haifa

            poiMarkerLayer = new PoiMarkerLayer();
            mapView.addLayer(poiMarkerLayer);

            // Click-to-add-POI: distinguish a real click from a pan/scroll gesture.
            // MOUSE_CLICKED fires even after drag/scroll in Gluon Maps, so we track
            // the press position and only act if the mouse barely moved (< 6 px).
            final double[] pressXY = {0, 0};
            mapView.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                pressXY[0] = e.getX();
                pressXY[1] = e.getY();
            });
            mapView.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                double dx = Math.abs(e.getX() - pressXY[0]);
                double dy = Math.abs(e.getY() - pressXY[1]);
                if (dx < 6 && dy < 6) { // genuine click – not a drag or scroll
                    MapPoint clicked = mapView.getMapPosition(e.getX(), e.getY());
                    if (clicked != null) {
                        addPoiAtLocation(clicked.getLatitude(), clicked.getLongitude());
                    }
                }
            });

            AnchorPane.setTopAnchor(mapView, 0.0);
            AnchorPane.setBottomAnchor(mapView, 0.0);
            AnchorPane.setLeftAnchor(mapView, 0.0);
            AnchorPane.setRightAnchor(mapView, 0.0);
            mapTabAnchorPane.getChildren().add(mapView);
        }

        setStatus("Ready - Select a city to begin");
    }

    /** Inner class: a Gluon MapLayer that shows POI pin markers. */
    private static class PoiMarkerLayer extends MapLayer {
        private final List<Pair<MapPoint, Node>> entries = new ArrayList<>();

        void addPoi(Poi p) {
            if (p == null) return;
            double lat = p.getLatitude() != null ? p.getLatitude() : 0;
            double lng = p.getLongitude() != null ? p.getLongitude() : 0;
            String name = p.getName() != null ? p.getName() : "";
            // Red pin: filled circle with a smaller white dot
            Circle outer = new Circle(10, Color.web("#e74c3c"));
            Circle inner = new Circle(4, Color.WHITE);
            StackPane pin = new StackPane(outer, inner);
            pin.setUserData(p.getId());

            StringBuilder tipText = new StringBuilder();
            if (!name.isEmpty()) tipText.append(name);
            if (p.getCategory() != null && !p.getCategory().isEmpty())
                tipText.append(tipText.length() > 0 ? "\n" : "").append("Category: ").append(p.getCategory());
            if (p.getShortExplanation() != null && !p.getShortExplanation().isEmpty())
                tipText.append(tipText.length() > 0 ? "\n" : "").append(p.getShortExplanation());
            if (tipText.length() == 0) tipText.append("POI");
            Tooltip tip = new Tooltip(tipText.toString());
            tip.setWrapText(true);
            tip.setMaxWidth(300);
            Tooltip.install(pin, tip);

            MapPoint point = new MapPoint(lat, lng);
            entries.add(new Pair<>(point, pin));
            getChildren().add(pin);
            this.markDirty();
        }

        void addPoi(int id, double lat, double lng, String name) {
            Poi p = new Poi(id, 0, name, null, null, null, true);
            p.setLatitude(lat);
            p.setLongitude(lng);
            addPoi(p);
        }

        void clearPois() {
            entries.clear();
            getChildren().clear();
            this.markDirty();
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

    private static final double DEMO_CAIRO_LAT = 30.0444;
    private static final double DEMO_CAIRO_LNG = 31.2357;

    /** Recentres Gluon MapView and redraws all POI markers. */
    private void refreshMapMarkers() {
        if (mapView == null || poiMarkerLayer == null) return;
        double centerLat = DEMO_CAIRO_LAT;
        double centerLng = DEMO_CAIRO_LNG;
        List<Poi> pois = null;

        if (currentMapContent != null) {
            pois = currentMapContent.getPois();
            if (pois != null && !pois.isEmpty()) {
                Poi first = pois.get(0);
                if (first.getLatitude() != null && first.getLongitude() != null) {
                    centerLat = first.getLatitude();
                    centerLng = first.getLongitude();
                }
            }
        }
        boolean noPoisWithCity = (pois == null || pois.isEmpty()) && selectedCity != null;
        if (noPoisWithCity) {
            double[] fallback = fallbackCenter();
            centerLat = fallback[0];
            centerLng = fallback[1];
        }

        mapView.flyTo(0, new MapPoint(centerLat, centerLng), 0.1);
        poiMarkerLayer.clearPois();

        if (pois != null) {
            for (Poi p : pois) {
                double lat = p.getLatitude() != null ? p.getLatitude() : parseLatLon(p.getLocation(), true);
                double lng = p.getLongitude() != null ? p.getLongitude() : parseLatLon(p.getLocation(), false);
                if (Double.isNaN(lat) || Double.isNaN(lng)) continue;
                poiMarkerLayer.addPoi(p);
            }
        }

        // When map has no POIs, center by OSM geocoding the city name (replaces hardcoded 4 cities)
        if (noPoisWithCity) {
            resolveCenterForCityWithNoPois(selectedCity, (lat, lon) -> {
                if (mapView != null) mapView.flyTo(0, new MapPoint(lat, lon), 0.3);
            });
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

    /** Fallback map center when geocoding fails or no city. */
    private static double[] fallbackCenter() {
        return new double[] { 32.8, 34.99 };
    }

    /** Cache for OSM Nominatim geocoding results (city name -> [lat, lon]). */
    private static final ConcurrentHashMap<String, double[]> geocodeCache = new ConcurrentHashMap<>();

    /** Cache for city bounding box (city name -> [minLat, maxLat, minLon, maxLon]). */
    private static final ConcurrentHashMap<String, double[]> cityBoundsCache = new ConcurrentHashMap<>();

    /**
     * Resolve map center when there are no POIs: use OSM Nominatim to geocode the city name.
     * Runs in background; calls onResult on the JavaFX thread with (lat, lon) or fallback on failure.
     */
    private void resolveCenterForCityWithNoPois(CityDTO city, BiConsumer<Double, Double> onResult) {
        double[] fallback = fallbackCenter();
        if (city == null || city.getName() == null || city.getName().trim().isEmpty()) {
            Platform.runLater(() -> onResult.accept(fallback[0], fallback[1]));
            return;
        }
        String name = city.getName().trim();
        String cacheKey = name.toLowerCase();
        double[] cached = geocodeCache.get(cacheKey);
        if (cached != null) {
            Platform.runLater(() -> onResult.accept(cached[0], cached[1]));
            return;
        }
        new Thread(() -> {
            try {
                String q = URLEncoder.encode(name, StandardCharsets.UTF_8);
                URI uri = URI.create(
                    "https://nominatim.openstreetmap.org/search?q=" + q + "&format=json&limit=1");
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "GCM-System/1.0 (Java)")
                    .GET()
                    .build();
                java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    Platform.runLater(() -> onResult.accept(fallback[0], fallback[1]));
                    return;
                }
                JsonArray arr = new Gson().fromJson(response.body(), JsonArray.class);
                if (arr == null || arr.isEmpty()) {
                    Platform.runLater(() -> onResult.accept(fallback[0], fallback[1]));
                    return;
                }
                JsonObject first = arr.get(0).getAsJsonObject();
                double lat = first.get("lat").getAsDouble();
                double lon = first.get("lon").getAsDouble();
                geocodeCache.put(cacheKey, new double[] { lat, lon });
                double finalLat = lat;
                double finalLon = lon;
                Platform.runLater(() -> onResult.accept(finalLat, finalLon));
            } catch (Exception e) {
                System.err.println("MapEditorScreen: geocode failed for '" + name + "': " + e.getMessage());
                Platform.runLater(() -> onResult.accept(fallback[0], fallback[1]));
            }
        }, "Geocode-" + name).start();
    }

    /**
     * Check that (lat, lng) is inside the city's bounding box from OSM.
     * Runs async if bounds not cached; calls onInside or onOutside on the JavaFX thread.
     */
    private void validatePoiLocationInCity(CityDTO city, double lat, double lng, Runnable onInside, Runnable onOutside) {
        if (city == null || city.getName() == null || city.getName().trim().isEmpty()) {
            Platform.runLater(onOutside);
            return;
        }
        String name = city.getName().trim();
        String cacheKey = name.toLowerCase();
        double[] bounds = cityBoundsCache.get(cacheKey);
        if (bounds != null) {
            boolean inside = isPointInBounds(lat, lng, bounds);
            Platform.runLater(inside ? onInside : onOutside);
            return;
        }
        new Thread(() -> {
            try {
                String q = URLEncoder.encode(name, StandardCharsets.UTF_8);
                URI uri = URI.create(
                    "https://nominatim.openstreetmap.org/search?q=" + q + "&format=json&limit=1");
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(8))
                    .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "GCM-System/1.0 (Java)")
                    .GET()
                    .build();
                java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    Platform.runLater(onOutside);
                    return;
                }
                JsonArray arr = new Gson().fromJson(response.body(), JsonArray.class);
                if (arr == null || arr.isEmpty()) {
                    Platform.runLater(onOutside);
                    return;
                }
                JsonObject first = arr.get(0).getAsJsonObject();
                if (!first.has("boundingbox")) {
                    Platform.runLater(onOutside);
                    return;
                }
                com.google.gson.JsonArray bbox = first.getAsJsonArray("boundingbox");
                if (bbox == null || bbox.size() < 4) {
                    Platform.runLater(onOutside);
                    return;
                }
                double minLat = Double.parseDouble(bbox.get(0).getAsString());
                double maxLat = Double.parseDouble(bbox.get(1).getAsString());
                double minLon = Double.parseDouble(bbox.get(2).getAsString());
                double maxLon = Double.parseDouble(bbox.get(3).getAsString());
                double[] b = new double[] { minLat, maxLat, minLon, maxLon };
                cityBoundsCache.put(cacheKey, b);
                boolean inside = isPointInBounds(lat, lng, b);
                Platform.runLater(inside ? onInside : onOutside);
            } catch (Exception e) {
                System.err.println("MapEditorScreen: fetch city bounds failed: " + e.getMessage());
                Platform.runLater(onOutside);
            }
        }, "CityBounds-" + name).start();
    }

    private static boolean isPointInBounds(double lat, double lng, double[] bounds) {
        if (bounds == null || bounds.length < 4) return false;
        double minLat = bounds[0], maxLat = bounds[1], minLon = bounds[2], maxLon = bounds[3];
        return lat >= minLat && lat <= maxLat && lng >= minLon && lng <= maxLon;
    }

    /**
     * Called when user clicks on the OSM map. Validate location is inside the city, then add POI.
     */
    private void addPoiAtLocation(double lat, double lng) {
        if (selectedCity == null) {
            showError("Please select a city first");
            return;
        }
        if (currentMapContent == null) {
            showError("Please select a map first");
            return;
        }
        setStatus("Checking location...");
        validatePoiLocationInCity(selectedCity, lat, lng,
            () -> {
                setStatus("Adding POI...");
                editingPoi = new Poi(0, selectedCity.getId(), "", "", "", "", true);
                editingPoi.setLatitude(lat);
                editingPoi.setLongitude(lng);
                editingPoi.setLocation(String.format("%.6f,%.6f", lat, lng));
                showPoiEditForm(editingPoi);
                contentTabs.getSelectionModel().selectNext();
                setStatus("Ready");
            },
            () -> {
                showError("This location is outside " + selectedCity.getName() + ". Please place the POI inside the city area.");
                setStatus("Ready");
            });
    }

    private void clearMapContent() {
        currentMapContent = null;

        // Clear Lists
        poisListView.getItems().clear();
        toursListView.getItems().clear();
        tourStopsListView.getItems().clear();

        // Clear Fields
        mapNameField.clear();
        mapDescArea.clear();

        // Clear Forms
        handleCancelPoiEdit();
        handleCancelTourEdit();

        // Hide/Reset Info
        tourStopsSection.setVisible(false);

        setStatus("No map selected");
    }

    private void setupCellFactories() {
        // City combo display
        cityComboBox.setButtonCell(new ListCell<CityDTO>() {
            @Override
            protected void updateItem(CityDTO city, boolean empty) {
                super.updateItem(city, empty);
                if (empty || city == null) {
                    setText(null);
                    System.out.println("DEBUG: ButtonCell update - empty/null");
                } else {
                    String text = city.getName() + " (" + city.getMapCount() + " maps)";
                    setText(text);
                    setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
                    System.out.println("DEBUG: ButtonCell update - " + text);
                }
            }
        });
        cityComboBox.setCellFactory(lv -> new ListCell<CityDTO>() {
            @Override
            protected void updateItem(CityDTO city, boolean empty) {
                super.updateItem(city, empty);
                if (empty || city == null) {
                    setText(null);
                } else {
                    setText(city.getName() + " (" + city.getMapCount() + " maps)");
                    // Dropdown list has white background, so use black text
                    setStyle("-fx-text-fill: black; -fx-font-size: 14px;");
                }
            }
        });

        // Maps list
        mapsListView.setCellFactory(lv -> new ListCell<MapSummary>() {
            @Override
            protected void updateItem(MapSummary map, boolean empty) {
                super.updateItem(map, empty);
                if (empty || map == null) {
                    setText(null);
                    setStyle("-fx-text-fill: white;");
                } else {
                    setText(map.getName() + " [" + map.getPoiCount() + " POIs]");
                    setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                }
            }
        });

        // POIs list
        poisListView.setCellFactory(lv -> new ListCell<Poi>() {
            @Override
            protected void updateItem(Poi poi, boolean empty) {
                super.updateItem(poi, empty);
                if (empty || poi == null) {
                    setText(null);
                } else {
                    setText("📍 " + poi.getName() + " [" + poi.getCategory() + "]" +
                            (poi.isAccessible() ? " ♿" : ""));
                    setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                }
            }
        });

        // Tours list
        toursListView.setCellFactory(lv -> new ListCell<TourDTO>() {
            @Override
            protected void updateItem(TourDTO tour, boolean empty) {
                super.updateItem(tour, empty);
                if (empty || tour == null) {
                    setText(null);
                } else {
                    setText("🚶 " + tour.getName() + " (" + tour.getStops().size() + " stops, ~" +
                            tour.getEstimatedDurationMinutes() + " min)");
                    setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                }
            }
        });

        // Tour stops list
        tourStopsListView.setCellFactory(lv -> new ListCell<TourStopDTO>() {
            @Override
            protected void updateItem(TourStopDTO stop, boolean empty) {
                super.updateItem(stop, empty);
                if (empty || stop == null) {
                    setText(null);
                } else {
                    setText(stop.getStopOrder() + ". " + stop.getPoiName() + " (" +
                            stop.getDurationMinutes() + " min)");
                    setStyle("-fx-text-fill: #9b59b6; -fx-font-size: 12px;");
                }
            }
        });
    }

    // ==================== City Operations ====================

    @FXML
    private void handleCreateCity() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create City");
        dialog.setHeaderText("Create a new city");
        dialog.setContentText("City name (must be a real place on the map):");

        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) return;
            setStatus("Validating city name...");
            validateCityNameAndCreate(trimmed);
        });
    }

    /** Check with OSM Nominatim that the place exists; only then create the city. */
    private void validateCityNameAndCreate(String name) {
        new Thread(() -> {
            try {
                String q = URLEncoder.encode(name, StandardCharsets.UTF_8);
                URI uri = URI.create(
                    "https://nominatim.openstreetmap.org/search?q=" + q + "&format=json&limit=1");
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(8))
                    .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "GCM-System/1.0 (Java)")
                    .GET()
                    .build();
                java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    Platform.runLater(() -> {
                        showError("Could not validate city. Please try again.");
                        setStatus("Ready");
                    });
                    return;
                }
                JsonArray arr = new Gson().fromJson(response.body(), JsonArray.class);
                if (arr == null || arr.isEmpty()) {
                    Platform.runLater(() -> {
                        showError("\"" + name + "\" was not found on the map. Please enter a valid city or place name (e.g. London, Paris, Tel Aviv).");
                        setStatus("Ready");
                    });
                    return;
                }
                String finalName = name;
                Platform.runLater(() -> {
                    setStatus("Creating city...");
                    control.createCity(finalName, "New city description", 50.0);
                });
            } catch (Exception e) {
                System.err.println("MapEditorScreen: validate city failed: " + e.getMessage());
                Platform.runLater(() -> {
                    showError("Could not validate city. Check your connection and try again.");
                    setStatus("Ready");
                });
            }
        }, "ValidateCity-" + name).start();
    }

    // ==================== Map Operations ====================

    @FXML
    private void handleCreateMap() {
        if (selectedCity == null) {
            // Create new city + new map in one go (no city selected)
            Dialog<String[]> dialog = new Dialog<>();
            dialog.setTitle("Create New City and Map");
            dialog.setHeaderText("Enter the new city name and the first map for it. The city will be created and must be approved by a content manager.");

            ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

            TextField cityNameField = new TextField();
            cityNameField.setPromptText("City name (e.g. Tel Aviv)");
            cityNameField.setMinWidth(280);
            TextField newMapNameField = new TextField();
            newMapNameField.setPromptText("Map name");
            newMapNameField.setMinWidth(280);
            TextArea newMapDescArea = new TextArea();
            newMapDescArea.setPromptText("Map description (optional)");
            newMapDescArea.setPrefRowCount(3);
            newMapDescArea.setWrapText(true);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.add(new Label("City name:"), 0, 0);
            grid.add(cityNameField, 1, 0);
            grid.add(new Label("Map name:"), 0, 1);
            grid.add(newMapNameField, 1, 1);
            grid.add(new Label("Map description:"), 0, 2);
            grid.add(newMapDescArea, 1, 2);
            dialog.getDialogPane().setContent(grid);

            dialog.setResultConverter(btn -> {
                if (btn == createButtonType) {
                    String cityName = cityNameField.getText() != null ? cityNameField.getText().trim() : "";
                    String mapName = newMapNameField.getText() != null ? newMapNameField.getText().trim() : "";
                    String mapDesc = newMapDescArea.getText() != null ? newMapDescArea.getText().trim() : "";
                    if (cityName.isEmpty() || mapName.isEmpty()) return null;
                    return new String[]{ cityName, mapName, mapDesc };
                }
                return null;
            });

            Optional<String[]> result = dialog.showAndWait();
            result.ifPresent(arr -> {
                MapChanges ch = new MapChanges();
                ch.setCreateNewCity(true);
                ch.setNewCityName(arr[0]);
                ch.setNewMapName(arr[1]);
                ch.setNewMapDescription(arr[2]);
                ch.setDraft(true);
                setStatus("Creating new city and map...");
                control.submitMapChanges(ch);
                control.getCities();
            });
            return;
        }

        // Existing city: add a new map for it
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Create Map");
        dialog.setHeaderText("Add a new map for " + selectedCity.getName());

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Map name");
        nameField.setMinWidth(280);

        TextArea descArea = new TextArea();
        descArea.setPromptText("Map description (optional)");
        descArea.setPrefRowCount(4);
        descArea.setWrapText(true);
        GridPane.setVgrow(descArea, Priority.ALWAYS);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Map name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descArea, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == createButtonType) {
                String name = nameField.getText() != null ? nameField.getText().trim() : "";
                String desc = descArea.getText() != null ? descArea.getText().trim() : "";
                if (name.isEmpty()) return null;
                return new String[]{ name, desc };
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(nameAndDesc -> {
            setStatus("Creating map...");
            control.createMap(selectedCity.getId(), nameAndDesc[0], nameAndDesc[1]);
        });
    }

    @FXML
    private void handleSaveMapInfo() {
        if (currentMapContent == null) {
            showError("No map selected");
            return;
        }

        // Add to pending changes instead of sending directly
        pendingChanges.setMapId(currentMapContent.getMapId());
        pendingChanges.setNewMapName(mapNameField.getText().trim());
        pendingChanges.setNewMapDescription(mapDescArea.getText().trim());
        hasUnsavedChanges = true;
        setStatus("Map info added to pending changes. Click 'Save All Changes' to submit for approval.");
    }

    // ==================== POI Operations ====================

    @FXML
    private void handleAddPoi() {
        if (selectedCity == null) {
            showError("Please select a city first");
            return;
        }
        if (currentMapContent == null) {
            showError("Please select a map first");
            return;
        }
        editingPoi = new Poi(0, selectedCity.getId(), "", "", "", "", true);
        showPoiEditForm(editingPoi);
    }

    @FXML
    private void handleEditPoi() {
        Poi selected = poisListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        showPoiEditForm(selected);
    }

    @FXML
    private void handleRemovePoiFromMap() {
        Poi selected = poisListView.getSelectionModel().getSelectedItem();
        if (selected == null || currentMapContent == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove from map");
        confirm.setHeaderText("Remove \"" + selected.getName() + "\" from this map?");
        confirm.setContentText("The POI stays in the city and can be added to other maps. Click Save or Send to manager to apply.");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                pendingChanges.getPoiMapUnlinks().add(new MapChanges.PoiMapLink(currentMapContent.getMapId(), selected.getId(), 0));
                currentMapContent.getPois().removeIf(p -> p.getId() == selected.getId());
                poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                hasUnsavedChanges = true;
                handleCancelPoiEdit();
                setStatus("POI removal from map added to pending changes.");
            }
        });
    }

    @FXML
    private void handleAddExistingPoiToMap() {
        if (currentMapContent == null) {
            showError("Select a map first");
            return;
        }
        int cityId = selectedCity != null ? selectedCity.getId() : currentMapContent.getCityId();
        setStatus("Loading city POIs...");
        pendingCityPoisCallback = () -> {
            // Start with POIs from server (DB) for this city
            List<Poi> cityPois = new java.util.ArrayList<>(lastCityPois != null ? lastCityPois : java.util.Collections.emptyList());
            // Include pending POIs for this city (e.g. added to other maps this session, not yet in DB)
            if (pendingChanges != null && pendingChanges.getAddedPois() != null) {
                for (Poi p : pendingChanges.getAddedPois()) {
                    if (p.getCityId() == cityId) {
                        boolean already = cityPois.stream().anyMatch(cp -> cp.getId() == p.getId() && p.getId() != 0);
                        if (!already) cityPois.add(p);
                    }
                }
            }
            if (cityPois.isEmpty()) {
                showError("No POIs in this city. Add POIs to the city first (on any of its maps).");
                return;
            }
            java.util.Set<Integer> onMapIds = new java.util.HashSet<>();
            List<Poi> mapPois = currentMapContent.getPois();
            if (mapPois != null) {
                for (Poi p : mapPois) onMapIds.add(p.getId());
            }
            // For new POIs (id=0) also consider same instance as "on map" if they're in current map
            List<Poi> notOnMap = new java.util.ArrayList<>();
            for (Poi p : cityPois) {
                if (p.getId() != 0 && onMapIds.contains(p.getId())) continue;
                if (p.getId() == 0 && mapPois != null && mapPois.contains(p)) continue;
                notOnMap.add(p);
            }
            if (notOnMap.isEmpty()) {
                showError("All city POIs are already on this map.");
                return;
            }
            ChoiceDialog<Poi> dialog = new ChoiceDialog<>(notOnMap.get(0), notOnMap);
            dialog.setTitle("Add existing POI to map");
            dialog.setHeaderText("POIs in this city that are not on the current map");
            dialog.setContentText("Choose POI:");
            dialog.showAndWait().ifPresent(poi -> {
                int order = currentMapContent.getPois().size();
                pendingChanges.getPoiMapLinks().add(new MapChanges.PoiMapLink(currentMapContent.getMapId(), poi.getId(), order));
                currentMapContent.getPois().add(poi);
                poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                hasUnsavedChanges = true;
                setStatus("POI added to map in pending changes.");
            });
        };
        lastCityPois = null;
        control.getPoisForCity(cityId);
    }

    private void showPoiEditForm(Poi poi) {
        editingPoi = poi;
        poiNameField.setText(poi.getName());
        poiCategoryCombo.setValue(poi.getCategory());
        poiLocationField.setText(poi.getLocation());
        poiAccessibleCheck.setSelected(poi.isAccessible());
        poiDescArea.setText(poi.getShortExplanation());

        poiEditForm.setVisible(true);
        poiEditForm.setManaged(true);
    }

    @FXML
    private void handleCancelPoiEdit() {
        poiEditForm.setVisible(false);
        poiEditForm.setManaged(false);
        editingPoi = null;
        if (editPoiBtn != null) editPoiBtn.setDisable(true);
        if (removePoiFromMapBtn != null) removePoiFromMapBtn.setDisable(true);
        poisListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSavePoi() {
        if (editingPoi == null)
            return;

        if (poiNameField.getText().trim().isEmpty()) {
            showError("POI name is required");
            return;
        }

        editingPoi.setName(poiNameField.getText().trim());
        editingPoi.setCategory(poiCategoryCombo.getValue() != null ? poiCategoryCombo.getValue() : "Other");
        editingPoi.setLocation(poiLocationField.getText().trim());
        editingPoi.setAccessible(poiAccessibleCheck.isSelected());
        editingPoi.setShortExplanation(poiDescArea.getText().trim());

        double lat = editingPoi.getLatitude() != null ? editingPoi.getLatitude() : parseLatLon(editingPoi.getLocation(), true);
        double lng = editingPoi.getLongitude() != null ? editingPoi.getLongitude() : parseLatLon(editingPoi.getLocation(), false);
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            showError("Valid coordinates are required (e.g. lat,lng or use the map to place the POI).");
            return;
        }
        if (selectedCity == null) {
            showError("No city selected.");
            return;
        }

        setStatus("Checking location...");
        validatePoiLocationInCity(selectedCity, lat, lng,
            () -> {
                editingPoi.setLatitude(lat);
                editingPoi.setLongitude(lng);
                if (editingPoi.getId() == 0) {
                    pendingChanges.addPoi(editingPoi);
                    if (currentMapContent != null) {
                        currentMapContent.getPois().add(editingPoi);
                        poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                    }
                    setStatus("POI added. Save or Send to manager to submit.");
                } else {
                    pendingChanges.updatePoi(editingPoi);
                    poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                    setStatus("POI updated in pending changes. Save or Send to manager to submit.");
                }
                hasUnsavedChanges = true;
                handleCancelPoiEdit();
            },
            () -> {
                showError("This location is outside " + selectedCity.getName() + ". Please place the POI inside the city area.");
                setStatus("Ready");
            });
    }

    @FXML
    private void handleDeletePoi() {
        if (editingPoi == null || editingPoi.getId() == 0)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete POI");
        confirm.setHeaderText("Delete " + editingPoi.getName() + "?");
        confirm.setContentText("This will be submitted for manager approval.");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                pendingChanges.deletePoi(editingPoi.getId());
                handleCancelPoiEdit();
                hasUnsavedChanges = true;
                setStatus("POI deletion added to pending changes. Click 'Save All Changes' to submit.");
            }
        });
    }

    // ==================== Tour Operations ====================

    @FXML
    private void handleAddTour() {
        if (selectedCity == null) {
            showError("Please select a city first");
            return;
        }
        if (currentMapContent == null) {
            showError("Please select a map first");
            return;
        }
        editingTour = new TourDTO(0, selectedCity.getId(), "", "", 60);
        showTourEditForm(editingTour);
    }

    @FXML
    private void handleEditTour() {
        TourDTO selected = toursListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        showTourEditForm(selected);
    }

    private void showTourDetails(TourDTO tour) {
        tourNameLabel.setText("Stops for: " + tour.getName());
        tourStopsListView.setItems(FXCollections.observableArrayList(tour.getStops()));

        tourStopsSection.setVisible(true);
        tourStopsSection.setManaged(true);
    }

    private void showTourEditForm(TourDTO tour) {
        editingTour = tour;
        tourNameField.setText(tour.getName());
        tourDurationField.setText(String.valueOf(tour.getEstimatedDurationMinutes()));
        tourDescArea.setText(tour.getDescription());

        tourEditForm.setVisible(true);
        tourEditForm.setManaged(true);
    }

    @FXML
    private void handleCancelTourEdit() {
        tourEditForm.setVisible(false);
        tourEditForm.setManaged(false);
        editingTour = null;
        if (editTourBtn != null) editTourBtn.setDisable(true);
        toursListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSaveTour() {
        if (editingTour == null)
            return;

        if (tourNameField.getText().trim().isEmpty()) {
            showError("Tour name is required");
            return;
        }

        try {
            int duration = Integer.parseInt(tourDurationField.getText().trim());
            if (duration <= 0) {
                showError("Duration must be greater than 0");
                return;
            }
            editingTour.setEstimatedDurationMinutes(duration);
        } catch (NumberFormatException e) {
            showError("Invalid duration");
            return;
        }

        editingTour.setName(tourNameField.getText().trim());
        editingTour.setDescription(tourDescArea.getText().trim());

        // Add to pending changes and show in list so you can add stops to the new tour
        if (editingTour.getId() == 0) {
            pendingChanges.addTour(editingTour);
            if (currentMapContent != null) {
                currentMapContent.getTours().add(editingTour);
                toursListView.setItems(FXCollections.observableArrayList(currentMapContent.getTours()));
            }
            setStatus("Tour added. Add stops then Save or Send to manager.");
        } else {
            pendingChanges.updateTour(editingTour);
            if (currentMapContent != null) {
                toursListView.setItems(FXCollections.observableArrayList(currentMapContent.getTours()));
            }
            setStatus("Tour updated. Save or Send to manager to submit.");
        }

        hasUnsavedChanges = true;
        handleCancelTourEdit();
    }

    @FXML
    private void handleDeleteTour() {
        if (editingTour == null || editingTour.getId() == 0)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Tour");
        confirm.setHeaderText("Delete " + editingTour.getName() + "?");
        confirm.setContentText("This will be submitted for manager approval.");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                pendingChanges.deleteTour(editingTour.getId());
                handleCancelTourEdit();
                hasUnsavedChanges = true;
                setStatus("Tour deletion added to pending changes. Click 'Save All Changes' to submit.");
            }
        });
    }

    @FXML
    private void handleAddTourStop() {
        TourDTO selectedTour = toursListView.getSelectionModel().getSelectedItem();
        if (selectedTour == null) {
            showError("Please select a tour first");
            return;
        }
        if (selectedCity == null) {
            showError("No city selected");
            return;
        }
        final TourDTO tourForStop = selectedTour;
        setStatus("Loading city POIs for tour stop...");
        pendingCityPoisCallback = () -> {
            List<Poi> cityPois = lastCityPois != null ? lastCityPois : new java.util.ArrayList<>();
            // Include POIs from current map (newly added, not yet in DB) so they can be used as stops
            java.util.Set<Integer> seenIds = new java.util.HashSet<>();
            List<Poi> combined = new java.util.ArrayList<>();
            for (Poi p : cityPois) {
                combined.add(p);
                seenIds.add(p.getId());
            }
            if (currentMapContent != null && currentMapContent.getPois() != null) {
                for (Poi p : currentMapContent.getPois()) {
                    if (p.getId() == 0 || !seenIds.contains(p.getId())) {
                        combined.add(p);
                        seenIds.add(p.getId());
                    }
                }
            }
            if (combined.isEmpty()) {
                showError("No POIs available. Add POIs to the map first, then try again.");
                return;
            }
            ChoiceDialog<Poi> dialog = new ChoiceDialog<>(combined.get(0), combined);
            dialog.setTitle("Add Tour Stop");
            dialog.setHeaderText("Select POI (includes POIs from this map and the city)");
            dialog.setContentText("POI:");
            dialog.showAndWait().ifPresent(poi -> {
                TourStopDTO stop = new TourStopDTO();
                stop.setTourId(tourForStop.getId());
                stop.setPoiId(poi.getId());
                stop.setPoiName(poi.getName());
                stop.setPoiCategory(poi.getCategory());
                stop.setStopOrder(tourForStop.getStops().size() + 1);
                stop.setDurationMinutes(15);
                stop.setNotes("");
                tourForStop.getStops().add(stop);
                if (tourForStop.getId() > 0) {
                    pendingChanges.getAddedStops().add(stop);
                }
                tourStopsListView.setItems(FXCollections.observableArrayList(tourForStop.getStops()));
                hasUnsavedChanges = true;
                setStatus("Tour stop added. Save or Send to manager to submit.");
            });
        };
        lastCityPois = null;
        control.getPoisForCity(selectedCity.getId());
    }

    @FXML
    private void handleEditTourStop() {
        TourStopDTO selectedStop = tourStopsListView.getSelectionModel().getSelectedItem();
        TourDTO selectedTour = toursListView.getSelectionModel().getSelectedItem();
        if (selectedStop == null || selectedTour == null) return;

        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Edit Tour Stop");
        dialog.setHeaderText(selectedStop.getPoiName());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField orderField = new TextField(String.valueOf(selectedStop.getStopOrder()));
        TextField durationField = new TextField(String.valueOf(selectedStop.getDurationMinutes()));
        TextArea notesArea = new TextArea(selectedStop.getNotes() != null ? selectedStop.getNotes() : "");
        notesArea.setPrefRowCount(2);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Stop order:"), 0, 0);
        grid.add(orderField, 1, 0);
        grid.add(new Label("Duration (min):"), 0, 1);
        grid.add(durationField, 1, 1);
        grid.add(new Label("Notes:"), 0, 2);
        grid.add(notesArea, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveButtonType) {
                try {
                    int order = Integer.parseInt(orderField.getText().trim());
                    int dur = Integer.parseInt(durationField.getText().trim());
                    if (order <= 0 || dur <= 0) return null;
                    return new String[]{ String.valueOf(order), String.valueOf(dur), notesArea.getText().trim() };
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(arr -> {
            selectedStop.setStopOrder(Integer.parseInt(arr[0]));
            selectedStop.setDurationMinutes(Integer.parseInt(arr[1]));
            selectedStop.setNotes(arr[2]);
            if (selectedStop.getId() > 0) {
                pendingChanges.getUpdatedStops().add(selectedStop);
            }
            tourStopsListView.setItems(FXCollections.observableArrayList(selectedTour.getStops()));
            hasUnsavedChanges = true;
            setStatus("Tour stop updated in pending changes.");
        });
    }

    @FXML
    private void handleRemoveTourStop() {
        TourStopDTO selectedStop = tourStopsListView.getSelectionModel().getSelectedItem();
        TourDTO selectedTour = toursListView.getSelectionModel().getSelectedItem();
        if (selectedStop == null || selectedTour == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Stop");
        confirm.setHeaderText("Remove \"" + selectedStop.getPoiName() + "\" from this tour?");
        confirm.setContentText("Click 'Save All Changes' to submit.");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                if (selectedStop.getId() > 0) {
                    pendingChanges.getDeletedStopIds().add(selectedStop.getId());
                }
                selectedTour.getStops().remove(selectedStop);
                tourStopsListView.setItems(FXCollections.observableArrayList(selectedTour.getStops()));
                hasUnsavedChanges = true;
                setStatus("Tour stop removal added to pending changes. Click 'Save All Changes' to submit.");
            }
        });
    }

    // ==================== Batch Operations ====================

    private void submitPendingChanges(boolean asDraft) {
        if (currentMapContent == null && selectedCity == null && !pendingChanges.hasChanges()) {
            showError("Please select a city or map first, or make some changes.");
            return;
        }
        if (currentMapContent != null) {
            pendingChanges.setMapId(currentMapContent.getMapId());
        }
        if (selectedCity != null) {
            pendingChanges.setCityId(selectedCity.getId());
        }
        if (mapNameField.getText() != null && !mapNameField.getText().isEmpty()) {
            pendingChanges.setNewMapName(mapNameField.getText().trim());
        }
        if (mapDescArea.getText() != null) {
            pendingChanges.setNewMapDescription(mapDescArea.getText().trim());
        }
        pendingChanges.setDraft(asDraft);
        control.submitMapChanges(pendingChanges);
        boolean isManager = LoginController.currentUserRole == LoginController.UserRole.MANAGER;
        setStatus(asDraft ? "Saving changes as draft..." : (isManager ? "Publishing..." : "Sending to content manager..."));
        pendingChanges = new MapChanges();
        hasUnsavedChanges = false;
    }

    @FXML
    private void handleSaveChanges() {
        submitPendingChanges(true);
    }

    @FXML
    private void handleSendToManager() {
        submitPendingChanges(false);
    }

    @FXML
    private void handleRemoveMap() {
        if (selectedMap == null || currentMapContent == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove map");
        confirm.setHeaderText("Remove map \"" + selectedMap.getName() + "\"?");
        confirm.setContentText("This cannot be undone. Save or Send to manager to apply.");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                pendingChanges.getDeletedMapIds().add(currentMapContent.getMapId());
                hasUnsavedChanges = true;
                setStatus("Map removal added to pending changes.");
                mapsListView.getSelectionModel().clearSelection();
            }
        });
    }

    @FXML
    private void handleDiscardChanges() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Discard Changes");
        confirm.setHeaderText("Discard all changes?");
        confirm.setContentText("All unsaved changes will be lost.");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                if (currentMapContent != null) {
                    control.getMapContent(currentMapContent.getMapId());
                }
                hasUnsavedChanges = false;
                setStatus("Changes discarded - reloaded from server");
            }
        });
    }

    // ==================== Navigation ====================

    @FXML
    private void handleBack() {
        if (hasUnsavedChanges) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Unsaved Changes");
            confirm.setHeaderText("You have unsaved changes");
            confirm.setContentText("Discard changes and go back?");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        if (control != null) {
            control.disconnect(); // This now only clears the handler
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("GCM Dashboard");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== Callbacks ====================

    @Override
    public void onCitiesReceived(List<CityDTO> cities) {
        Platform.runLater(() -> {
            // Save current selection ID if no pending selection
            int targetId = pendingSelectCityId;
            if (targetId == -1 && selectedCity != null) {
                targetId = selectedCity.getId();
            }

            // Store the items
            cityComboBox.setItems(FXCollections.observableArrayList(cities));

            // Restore selection
            CityDTO cityToSelect = null;
            if (targetId != -1) {
                for (CityDTO c : cities) {
                    if (c.getId() == targetId) {
                        cityToSelect = c;
                        break;
                    }
                }
            }

            if (cityToSelect != null) {
                // Use setValue instead of select to ensure ButtonCell updates
                cityComboBox.setValue(cityToSelect);
                selectedCity = cityToSelect;
                control.getMapsForCity(selectedCity.getId());
                createMapBtn.setDisable(false);
            } else {
                // No city to select - clear value explicitly; keep Create new map enabled (creates new city + map)
                cityComboBox.setValue(null);
                selectedCity = null;
                createMapBtn.setDisable(false);
            }

            pendingSelectCityId = -1; // Reset
            setStatus("Loaded " + cities.size() + " cities");
        });
    }

    @Override
    public void onMapsReceived(List<MapSummary> maps) {
        Platform.runLater(() -> {
            // Only apply if this response is for the currently selected city (avoid stale response overwriting)
            if (selectedCity != null && lastRequestedCityId == selectedCity.getId()) {
                mapsListView.setItems(FXCollections.observableArrayList(maps));

                // Auto-select newly created map if requested
                int targetMapId = pendingSelectMapId;
                pendingSelectMapId = -1;
                if (targetMapId > 0) {
                    for (MapSummary m : maps) {
                        if (m.getId() == targetMapId) {
                            mapsListView.getSelectionModel().select(m);
                            selectedMap = m;
                            control.getMapContent(m.getId());
                            break;
                        }
                    }
                }

                setStatus("Loaded " + maps.size() + " map(s) for " + selectedCity.getName());
            } else {
                // Stale response or no city selected — clear "Loading..." so the page doesn't stay stuck
                if (selectedCity != null) {
                    setStatus("Ready – " + selectedCity.getName());
                } else {
                    setStatus("Ready – Select a city to begin");
                }
            }
        });
    }

    @Override
    public void onPoisForCityReceived(List<Poi> pois) {
        lastCityPois = pois;
        if (pendingCityPoisCallback != null) {
            Runnable cb = pendingCityPoisCallback;
            pendingCityPoisCallback = null;
            // Run on JavaFX thread so dialogs and UI updates work
            Platform.runLater(cb);
        }
    }

    @Override
    public void onMapContentReceived(MapContent content) {
        Platform.runLater(() -> {
            currentMapContent = content;

            // Update POIs tab
            poisListView.setItems(FXCollections.observableArrayList(content.getPois()));

            // Update Tours tab
            toursListView.setItems(FXCollections.observableArrayList(content.getTours()));

            // Update Map Info tab
            mapNameField.setText(content.getMapName());
            mapDescArea.setText(content.getShortDescription());

            setStatus("Editing: " + content.getMapName());

            // Update the map view and markers so the map shows the new city/map, not the previous one
            refreshMapMarkers();
        });
    }

    @Override
    public void onValidationResult(ValidationResult result) {
        Platform.runLater(() -> {
            if (result.isValid()) {
                setStatus("✓ " + result.getSuccessMessage());

                // Auto-select newly created city
                if (result.getCreatedCityId() != null && result.getCreatedCityId() > 0) {
                    pendingSelectCityId = result.getCreatedCityId();
                }

                // Auto-select newly created map (refresh maps list; onMapsReceived will select it)
                if (result.getCreatedMapId() != null && result.getCreatedMapId() > 0) {
                    pendingSelectMapId = result.getCreatedMapId();
                }

                // Refresh data
                if (selectedCity != null) {
                    control.getMapsForCity(selectedCity.getId());
                }
                if (currentMapContent != null) {
                    control.getMapContent(currentMapContent.getMapId());
                }
                control.getCities();
            } else {
                showError(result.getErrorSummary());
            }
        });
    }

    @Override
    public void onError(String errorCode, String errorMessage) {
        Platform.runLater(() -> {
            setStatus("Error – " + errorMessage);
            showError(errorCode + ": " + errorMessage);
        });
    }

    @Override
    public void onPendingRequestsReceived(List<MapEditRequestDTO> requests) {
        // Not used in this screen
    }

    // ==================== Helpers ====================

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        statusLabel.setText("⚠️ " + message);
        statusLabel.setStyle("-fx-text-fill: #e74c3c;");

        // Use proper JavaFX timer instead of Thread.sleep (Phase 14)
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(3000));
        pause.setOnFinished(e -> statusLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.8);"));
        pause.play();
    }
}
