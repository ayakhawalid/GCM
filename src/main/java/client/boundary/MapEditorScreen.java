package client.boundary;

import client.LoginController;
import client.MenuNavigationHelper;
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
import javafx.scene.web.WebView;
import javafx.event.ActionEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.SVGPath;
import javafx.scene.Group;
import javafx.stage.Stage;
import javafx.util.Pair;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Controller for the Map Editor screen.
 * Handles all map editing operations including POIs and Tours.
 */
public class MapEditorScreen implements ContentManagementControl.ContentCallback {

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
    private Button sendToManagerButton;

    // Left panel - City/Map selection
    @FXML
    private ComboBox<CityDTO> cityComboBox;
    @FXML
    private ListView<MapSummary> mapsListView;
    @FXML
    private ListView<MapSummary> tourMapsListView;
    @FXML
    private Button createCityBtn;
    @FXML
    private Button deleteCityBtn;
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
    private TextArea tourDescArea;

    // Map Tab – Gluon Maps (native JavaFX, no WebView)
    @FXML
    private AnchorPane mapTabAnchorPane;
    private MapView mapView;
    private PoiMarkerLayer poiMarkerLayer;
    private TourRouteLayer tourRouteLayer;

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
    /** New cities (city only) not yet saved – saved as draft on Save, sent for approval on Send. */
    private java.util.List<MapChanges.NewCityRequest> pendingNewCities = new java.util.ArrayList<>();
    /** New city + first map not yet saved. */
    private java.util.List<MapChanges.CityWithMapRequest> pendingNewCityWithMap = new java.util.ArrayList<>();
    /** New maps in existing cities not yet saved. */
    private java.util.List<MapChanges.NewMapRequest> pendingNewMaps = new java.util.ArrayList<>();
    /** When we receive city POIs, run this (e.g. show Add Stop or Add existing POI dialog). */
    private Runnable pendingCityPoisCallback = null;
    /** Last city POIs received (for use in pending callback). */
    private List<Poi> lastCityPois = null;
    /** True when user added a map or city removal but has not saved yet; employees must Save before Send. */
    private boolean pendingMapOrCityDeletionUnsaved = false;

    /**
     * Initialize the controller.
     */
    @FXML
    public void initialize() {
        applyNavbarLogoSvg();
        MenuNavigationHelper.configureSidebarButtons(mapEditorNavBtn, myPurchasesNavBtn, profileNavBtn, customersNavBtn, pricingNavBtn, pricingApprovalNavBtn, supportNavBtn, agentConsoleNavBtn, editApprovalsNavBtn, reportsNavBtn, userManagementNavBtn);
        System.out.println("MapEditorScreen: Initializing");

        // Show "Publish" for content managers, "Send to content manager" for editors
        if (sendToManagerButton != null && LoginController.currentUserRole == LoginController.UserRole.MANAGER) {
            sendToManagerButton.setText("📤 Publish");
        }
        // Delete City and Remove map available for both employee and manager (Save stores draft; manager can Publish)

        // Setup POI categories
        poiCategoryCombo.setItems(FXCollections.observableArrayList(
                "Museum", "Beach", "Historic", "Religious", "Park", "Shopping",
                "Restaurant", "Entertainment", "Cultural", "Nature", "Other"));

        // Connect to server
        try {
            control = new ContentManagementControl("localhost", 5555);
            control.setCallback(this);

            // Load cities and user-level draft (e.g. delete-city-only)
            control.getCities();
            control.getMyDraft();

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
                mapsListView.getSelectionModel().clearSelection();
                if (tourMapsListView != null) tourMapsListView.getSelectionModel().clearSelection();
                clearMapContent();
                if (selectedCity.getId() < 0) {
                    // Synthetic (pending) city: show pending maps in real time without calling server
                    List<MapSummary> pendingMaps = buildPendingMapsForSelectedCity();
                    mapsListView.setItems(FXCollections.observableArrayList(pendingMaps));
                    if (tourMapsListView != null) tourMapsListView.getItems().clear();
                    createMapBtn.setDisable(false);
                    setStatus(selectedCity.getName() + " (unsaved) – Save to create.");
                } else {
                    mapsListView.getItems().clear();
                    if (tourMapsListView != null) tourMapsListView.getItems().clear();
                    control.getMapsForCity(selectedCity.getId());
                    createMapBtn.setDisable(false);
                    setStatus("Loading maps for " + selectedCity.getName() + "...");
                }
                if (deleteCityBtn != null) deleteCityBtn.setDisable(selectedCity.getId() <= 0);
            } else {
                lastRequestedCityId = -1;
                if (deleteCityBtn != null) deleteCityBtn.setDisable(true);
                createMapBtn.setDisable(false); // allow "Create new map" → creates new city + map when no city selected
                mapsListView.getItems().clear();
                mapsListView.getSelectionModel().clearSelection();
                if (tourMapsListView != null) {
                    tourMapsListView.getItems().clear();
                    tourMapsListView.getSelectionModel().clearSelection();
                }
                clearMapContent();
                setStatus("No city selected – you can still create a new map (and city)");
            }
        };

        cityComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> onCityChanged.run());
        cityComboBox.setOnAction(e -> onCityChanged.run());

        // Map selection listener (normal maps list)
        mapsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newMap) -> {
            if (newMap != null && tourMapsListView != null) tourMapsListView.getSelectionModel().clearSelection();
            selectedMap = newMap;
            if (removeMapBtn != null) removeMapBtn.setDisable(selectedMap == null);
            if (control == null) return;
            if (selectedMap != null) {
                if (selectedMap.getId() > 0) {
                    System.out.println("MapEditorScreen: map selected (Maps list), mapId=" + selectedMap.getId() + ", name=" + selectedMap.getName());
                    control.getMapContent(selectedMap.getId());
                } else {
                    clearMapContent();
                    setStatus("Unsaved map – Save to create.");
                }
            } else {
                clearMapContent();
            }
        });
        // Tour map selection listener (tour maps list)
        if (tourMapsListView != null) {
            tourMapsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newMap) -> {
                if (newMap != null) mapsListView.getSelectionModel().clearSelection();
                selectedMap = newMap;
                if (removeMapBtn != null) removeMapBtn.setDisable(selectedMap == null);
                if (control == null) return;
                if (selectedMap != null && selectedMap.getId() > 0) {
                    System.out.println("MapEditorScreen: map selected (Tours list), mapId=" + selectedMap.getId() + ", name=" + selectedMap.getName());
                    control.getMapContent(selectedMap.getId());
                } else {
                    clearMapContent();
                }
            });
        }

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
            tourRouteLayer = new TourRouteLayer();
            mapView.addLayer(tourRouteLayer);

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

    /** Creates a red map-pin node (teardrop with white circular cutout). Tip is at (0,0) for correct anchoring. */
    private static Node createPinMarker() {
        Color red = Color.web("#e74c3c");
        double w = 10;
        double h = 22;
        double tipY = 0;
        double topY = -h;
        double shoulderY = -h + 6;
        // Teardrop path: tip (0,0) -> left shoulder -> arc at top -> right shoulder -> tip
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
        // White circle (cutout) centered in the rounded top
        Circle cutout = new Circle(0, topY + 6, 3.5);
        cutout.setFill(Color.WHITE);
        return new Group(teardrop, cutout);
    }

    /** Inner class: a Gluon MapLayer that shows POI pin markers. */
    private static class PoiMarkerLayer extends MapLayer {
        private final List<Pair<MapPoint, Node>> entries = new ArrayList<>();

        void addPoi(Poi p) {
            if (p == null) return;
            double lat = p.getLatitude() != null ? p.getLatitude() : 0;
            double lng = p.getLongitude() != null ? p.getLongitude() : 0;
            String name = p.getName() != null ? p.getName() : "";
            // Red map-pin shape (teardrop with circular cutout), tip at anchor
            Node pin = createPinMarker();
            pin.setUserData(p.getId());

            StringBuilder tipText = new StringBuilder();
            if (!name.isEmpty()) tipText.append(name);
            if (p.isDraft()) tipText.append(tipText.length() > 0 ? "\n" : "").append("[Draft]");
            else if (p.isPendingDeletion()) tipText.append(tipText.length() > 0 ? "\n" : "").append("[Pending deletion]");
            else if (p.isPendingRemoval()) tipText.append(tipText.length() > 0 ? "\n" : "").append("[Pending removal]");
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

    /** Layer that draws tour route segments (straight lines) and shows distance on hover. */
    private static class TourRouteLayer extends MapLayer {
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
                line.setMouseTransparent(false);
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

        if (tourRouteLayer != null) {
            if (currentMapContent != null && currentMapContent.getTourSegments() != null && !currentMapContent.getTourSegments().isEmpty()) {
                tourRouteLayer.setSegments(currentMapContent.getTourSegments());
            } else {
                tourRouteLayer.clearSegments();
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

    /** Loaded list of (country display name, ISO 3166-1 alpha-2 code) for OSM city selection. */
    private static List<Pair<String, String>> countryList = null;

    /** Simple DTO for a place returned by Nominatim (display name + coordinates). */
    private static final class OSMPlace {
        final String displayName;
        final double lat;
        final double lon;
        OSMPlace(String displayName, double lat, double lon) {
            this.displayName = displayName;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** Parse a JSON number or string as double; returns defaultValue if missing or invalid. */
    private static double parseJsonDouble(JsonObject ob, String key, double defaultValue) {
        if (!ob.has(key)) return defaultValue;
        try {
            var el = ob.get(key);
            if (el.isJsonPrimitive()) {
                var p = el.getAsJsonPrimitive();
                return p.isNumber() ? p.getAsDouble() : Double.parseDouble(p.getAsString());
            }
        } catch (Exception ignored) { }
        return defaultValue;
    }

    /** Load countries from resource (name, ISO code). Returns empty list on error. */
    private static List<Pair<String, String>> loadCountries() {
        if (countryList != null) return countryList;
        List<Pair<String, String>> list = new ArrayList<>();
        try (var in = MapEditorScreen.class.getResourceAsStream("/client/countries.txt")) {
            if (in == null) return Collections.emptyList();
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    int tab = line.indexOf('\t');
                    if (tab > 0 && tab < line.length() - 1)
                        list.add(new Pair<>(line.substring(0, tab).trim(), line.substring(tab + 1).trim().toLowerCase()));
                }
            }
            countryList = list;
        } catch (Exception e) {
            System.err.println("MapEditorScreen: could not load countries: " + e.getMessage());
            countryList = Collections.emptyList();
        }
        return countryList;
    }

    /**
     * Search OSM Nominatim for places in a country; runs on background thread, calls onResult on JavaFX thread.
     * @param query search term (e.g. city name)
     * @param countryCode ISO 3166-1 alpha-2 (e.g. "il", "us")
     * @param onResult list of OSMPlace (may be empty) or null on error
     */
    private void searchCitiesInCountry(String query, String countryCode, java.util.function.Consumer<List<OSMPlace>> onResult) {
        if (query == null || query.trim().isEmpty() || countryCode == null || countryCode.trim().isEmpty()) {
            Platform.runLater(() -> onResult.accept(Collections.emptyList()));
            return;
        }
        String q = query.trim();
        String cc = countryCode.trim().toLowerCase();
        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
                URI uri = URI.create(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded + "&countrycodes=" + cc + "&format=json&limit=15");
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "GCM-System/1.0 (Java)")
                    .GET()
                    .build();
                java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    Platform.runLater(() -> onResult.accept(null));
                    return;
                }
                JsonArray arr = new Gson().fromJson(response.body(), JsonArray.class);
                List<OSMPlace> places = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject ob = arr.get(i).getAsJsonObject();
                        String name = ob.has("display_name") ? ob.get("display_name").getAsString() : "";
                        double lat = parseJsonDouble(ob, "lat", 0);
                        double lon = parseJsonDouble(ob, "lon", 0);
                        if (!name.isEmpty()) places.add(new OSMPlace(name, lat, lon));
                    }
                }
                List<OSMPlace> finalList = places;
                Platform.runLater(() -> onResult.accept(finalList));
            } catch (Exception e) {
                System.err.println("MapEditorScreen: OSM city search failed: " + e.getMessage());
                Platform.runLater(() -> onResult.accept(null));
            }
        }, "OSM-Search").start();
    }

    /**
     * Fetch all cities and towns in a country from OSM Overpass API; runs on background thread, calls onResult on JavaFX thread.
     * @param countryCode ISO 3166-1 alpha-2 (e.g. "il", "us") – will be sent uppercase to Overpass
     * @param onResult list of OSMPlace (may be empty), or null on error
     */
    private void fetchAllCitiesInCountry(String countryCode, java.util.function.Consumer<List<OSMPlace>> onResult) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            Platform.runLater(() -> onResult.accept(Collections.emptyList()));
            return;
        }
        String cc = countryCode.trim().toUpperCase();
        new Thread(() -> {
            try {
                // Overpass: areas with ISO3166-1, then nodes/ways with place=city or place=town
                String query = "[out:json][timeout:45];"
                    + "area[\"ISO3166-1\"=\"" + cc + "\"]->.a;"
                    + "( node(area.a)[\"place\"~\"^(city|town)$\"]; way(area.a)[\"place\"~\"^(city|town)$\"]; );"
                    + "out center 600;";
                URI uri = URI.create("https://overpass-api.de/api/interpreter");
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "GCM-System/1.0 (Java)")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(query))
                    .build();
                java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    Platform.runLater(() -> onResult.accept(null));
                    return;
                }
                JsonObject root = new Gson().fromJson(response.body(), JsonObject.class);
                JsonArray elements = root != null && root.has("elements") ? root.getAsJsonArray("elements") : null;
                List<OSMPlace> places = new ArrayList<>();
                if (elements != null) {
                    for (int i = 0; i < elements.size(); i++) {
                        JsonObject el = elements.get(i).getAsJsonObject();
                        JsonObject tags = el.has("tags") ? el.getAsJsonObject("tags") : null;
                        if (tags == null || !tags.has("name")) continue;
                        String name = tags.get("name").getAsString();
                        if (name == null || name.trim().isEmpty()) continue;
                        String displayName = name;
                        double lat, lon;
                        if (el.has("lat") && el.has("lon")) {
                            lat = parseJsonDouble(el, "lat", 0);
                            lon = parseJsonDouble(el, "lon", 0);
                        } else if (el.has("center")) {
                            JsonObject center = el.getAsJsonObject("center");
                            lat = parseJsonDouble(center, "lat", 0);
                            lon = parseJsonDouble(center, "lon", 0);
                        } else continue;
                        places.add(new OSMPlace(displayName, lat, lon));
                    }
                }
                places.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName));
                List<OSMPlace> finalList = places;
                Platform.runLater(() -> onResult.accept(finalList));
            } catch (Exception e) {
                System.err.println("MapEditorScreen: Overpass fetch cities failed: " + e.getMessage());
                Platform.runLater(() -> onResult.accept(null));
            }
        }, "Overpass-Cities").start();
    }

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
                } else {
                    String text = city.getName() + " (" + city.getMapCount() + " maps)" + (city.isDraft() ? " [Draft]" : "");
                    setText(text);
                    setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 14px;");
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
                    setText(city.getName() + " (" + city.getMapCount() + " maps)" + (city.isDraft() ? " [Draft]" : ""));
                    // Dropdown list has white background, so use black text
                    setStyle("-fx-text-fill: black; -fx-font-size: 14px;");
                }
            }
        });

        // Maps list (normal maps) — employee sees "(waiting for approval)" for unapproved maps
        mapsListView.setCellFactory(lv -> new ListCell<MapSummary>() {
            @Override
            protected void updateItem(MapSummary map, boolean empty) {
                super.updateItem(map, empty);
                if (empty || map == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String suffix = map.isWaitingForApproval()
                            ? " (waiting for approval)"
                            : (map.isDraft() ? " [Draft]" : "");
                    setText(map.getName() + " [" + map.getPoiCount() + " POIs]" + suffix);
                    setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px;");
                }
            }
        });
        if (tourMapsListView != null) {
            tourMapsListView.setCellFactory(lv -> new ListCell<MapSummary>() {
                @Override
                protected void updateItem(MapSummary map, boolean empty) {
                    super.updateItem(map, empty);
                    if (empty || map == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        String suffix = map.isWaitingForApproval()
                                ? " (waiting for approval)"
                                : (map.isDraft() ? " [Draft]" : "");
                        setText(map.getName() + " [" + map.getPoiCount() + " POIs]" + suffix);
                        setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px;");
                    }
                }
            });
        }

        // POIs list
        poisListView.setCellFactory(lv -> new ListCell<Poi>() {
            @Override
            protected void updateItem(Poi poi, boolean empty) {
                super.updateItem(poi, empty);
                if (empty || poi == null) {
                    setText(null);
                } else {
                    String base = "📍 " + poi.getName() + " [" + poi.getCategory() + "]" +
                            (poi.isAccessible() ? " ♿" : "");
                    if (poi.isDraft()) {
                        base += " [Draft]";
                    } else if (poi.isPendingDeletion()) {
                        base += " [Pending deletion]";
                    } else if (poi.isPendingRemoval()) {
                        base += " [Pending removal]";
                    }
                    setText(base);
                    setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px;");
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
                    double totalM = tour.getTotalDistanceMeters() != null ? tour.getTotalDistanceMeters() : 0;
                    if (totalM == 0 && tour.getStops() != null) {
                        for (TourStopDTO s : tour.getStops()) {
                            if (s.getDistanceToNextMeters() != null) totalM += s.getDistanceToNextMeters();
                        }
                    }
                    String dist = totalM > 0 ? String.format("%.0f m", totalM) : "? m";
                    int totalStops = tour.getStops() != null ? tour.getStops().size() : 0;
                    long draftStops = tour.getStops() != null ? tour.getStops().stream().filter(s -> s.getId() == 0).count() : 0;
                    String stopsLabel = draftStops > 0 ? totalStops + " stops (" + draftStops + " draft), " : totalStops + " stops, ";
                    String base = "🚶 " + tour.getName() + " (" + stopsLabel + dist + ")";
                    if (tour.isWaitingForApproval()) {
                        base += " (waiting for approval)";
                    } else if (tour.isDraft()) {
                        base += " [Draft]";
                    } else if (tour.isPendingDeletion()) {
                        base += " [Pending deletion]";
                    }
                    setText(base);
                    setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 13px;");
                }
            }
        });

        // Tour stops list (show [Draft] when this stop's POI is draft or stop is not yet persisted, id=0)
        // Display number = position in list (1-based) so numbering stays 1,2,3,... after removals
        tourStopsListView.setCellFactory(lv -> new ListCell<TourStopDTO>() {
            @Override
            protected void updateItem(TourStopDTO stop, boolean empty) {
                super.updateItem(stop, empty);
                if (empty || stop == null) {
                    setText(null);
                } else {
                    String toNext = stop.getDistanceToNextMeters() != null ? String.format(" → %.0f m", stop.getDistanceToNextMeters()) : "";
                    int displayNum = lv.getItems().indexOf(stop) + 1;
                    if (displayNum <= 0) displayNum = stop.getStopOrder();
                    String base = displayNum + ". " + stop.getPoiName() + toNext;
                    boolean isDraftStop = (stop.getId() == 0);
                    if (!isDraftStop && currentMapContent != null && currentMapContent.getPois() != null) {
                        for (common.Poi p : currentMapContent.getPois()) {
                            if (p.getId() == stop.getPoiId() && p.isDraft()) {
                                isDraftStop = true;
                                break;
                            }
                        }
                    }
                    if (isDraftStop) base += " [Draft]";
                    setText(base);
                    setStyle("-fx-text-fill: #9b59b6; -fx-font-size: 12px;");
                }
            }
        });
    }

    // ==================== City Operations ====================

    @FXML
    private void handleCreateCity() {
        List<Pair<String, String>> countries = loadCountries();
        if (countries.isEmpty()) {
            showError("Could not load country list.");
            return;
        }
        Dialog<OSMPlace> dialog = new Dialog<>();
        dialog.setTitle("Create City");
        dialog.setHeaderText("Choose a country, then search and select a city from OpenStreetMap.");

        ButtonType createButtonType = new ButtonType("Create city", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        ComboBox<Pair<String, String>> countryCombo = new ComboBox<>(FXCollections.observableList(countries));
        countryCombo.setPromptText("Select country");
        countryCombo.setConverter(new javafx.util.StringConverter<Pair<String, String>>() {
            @Override
            public String toString(Pair<String, String> p) { return p == null ? "" : p.getKey(); }
            @Override
            public Pair<String, String> fromString(String s) { return null; }
        });
        if (!countries.isEmpty()) countryCombo.getSelectionModel().select(0);

        TextField citySearchField = new TextField();
        citySearchField.setPromptText("Type city name and click Search (optional)");
        citySearchField.setMinWidth(220);
        Button searchBtn = new Button("Search");
        Button loadAllBtn = new Button("Load all cities in country");
        loadAllBtn.setTooltip(new Tooltip("Fetch all cities and towns from OpenStreetMap for the selected country. May take a few seconds."));
        javafx.collections.ObservableList<OSMPlace> searchResults = FXCollections.observableArrayList();
        ListView<OSMPlace> resultsList = new ListView<>(searchResults);
        resultsList.setPlaceholder(new Label("Select a country, then click \"Load all cities in country\" or search by name."));
        resultsList.setPrefHeight(200);
        resultsList.setCellFactory(lv -> new ListCell<OSMPlace>() {
            @Override
            protected void updateItem(OSMPlace item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName);
            }
        });
        Button createBtn = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        createBtn.setDisable(true);
        resultsList.getSelectionModel().selectedItemProperty().addListener((o, prev, cur) ->
            createBtn.setDisable(cur == null));

        searchBtn.setOnAction(e -> {
            Pair<String, String> sel = countryCombo.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showError("Please select a country first.");
                return;
            }
            String query = citySearchField.getText() != null ? citySearchField.getText().trim() : "";
            if (query.isEmpty()) {
                showError("Enter a city name to search.");
                return;
            }
            setStatus("Searching OpenStreetMap...");
            searchResults.clear();
            searchCitiesInCountry(query, sel.getValue(), list -> {
                setStatus("Ready");
                if (list == null) {
                    showError("Search failed. Check your connection and try again.");
                    return;
                }
                searchResults.setAll(list);
                if (list.isEmpty())
                    showError("No places found. Try a different name or country.");
            });
        });

        loadAllBtn.setOnAction(e -> {
            Pair<String, String> sel = countryCombo.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showError("Please select a country first.");
                return;
            }
            setStatus("Loading cities from OpenStreetMap (may take a few seconds)...");
            loadAllBtn.setDisable(true);
            searchResults.clear();
            fetchAllCitiesInCountry(sel.getValue(), list -> {
                loadAllBtn.setDisable(false);
                setStatus("Ready");
                if (list == null) {
                    showError("Could not load cities. Check your connection and try again.");
                    return;
                }
                searchResults.setAll(list);
                if (list.isEmpty())
                    showError("No cities or towns found for this country in OpenStreetMap.");
                else
                    setStatus("Loaded " + list.size() + " cities/towns. Select one below.");
            });
        });

        VBox content = new VBox(10);
        content.getChildren().addAll(
            new Label("Country:"),
            countryCombo,
            new Label("City:"),
            new HBox(8, citySearchField, searchBtn, loadAllBtn),
            new Label("Select a city (load all or search by name):"),
            resultsList
        );
dialog.getDialogPane().setContent(content);

            dialog.setResultConverter(btn -> btn == createButtonType ? resultsList.getSelectionModel().getSelectedItem() : null);

            Optional<OSMPlace> result = dialog.showAndWait();
        result.ifPresent(place -> {
            pendingNewCities.add(new MapChanges.NewCityRequest(place.displayName, "New city description", 50.0));
            hasUnsavedChanges = true;
            setStatus("New city \"" + place.displayName + "\" added. Click Save to draft, then Send to manager for approval.");
            control.getCities();
        });
    }

    // ==================== Map Operations ====================

    @FXML
    private void handleCreateMap() {
        if (selectedCity == null) {
            // Create new city + new map: choose country and city from OSM, then map name/description
            List<Pair<String, String>> countries = loadCountries();
            if (countries.isEmpty()) {
                showError("Could not load country list.");
                return;
            }
            Dialog<String[]> dialog = new Dialog<>();
            dialog.setTitle("Create New City and Map");
            dialog.setHeaderText("Choose a country, select a city from OpenStreetMap, then enter the first map name.");

            ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

            ComboBox<Pair<String, String>> countryCombo = new ComboBox<>(FXCollections.observableList(countries));
            countryCombo.setPromptText("Select country");
            countryCombo.setConverter(new javafx.util.StringConverter<Pair<String, String>>() {
                @Override
                public String toString(Pair<String, String> p) { return p == null ? "" : p.getKey(); }
                @Override
                public Pair<String, String> fromString(String s) { return null; }
            });
            if (!countries.isEmpty()) countryCombo.getSelectionModel().select(0);

            TextField citySearchField = new TextField();
            citySearchField.setPromptText("Type city name and click Search (optional)");
            citySearchField.setMinWidth(220);
            Button searchBtn = new Button("Search");
            Button loadAllBtn = new Button("Load all cities in country");
            loadAllBtn.setTooltip(new Tooltip("Fetch all cities and towns from OpenStreetMap for the selected country. May take a few seconds."));
            javafx.collections.ObservableList<OSMPlace> searchResults = FXCollections.observableArrayList();
            ListView<OSMPlace> resultsList = new ListView<>(searchResults);
            resultsList.setPlaceholder(new Label("Select a country, then click \"Load all cities in country\" or search by name."));
            resultsList.setPrefHeight(160);
            resultsList.setCellFactory(lv -> new ListCell<OSMPlace>() {
                @Override
                protected void updateItem(OSMPlace item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.displayName);
                }
            });

            TextField newMapNameField = new TextField();
            newMapNameField.setPromptText("Map name");
            newMapNameField.setMinWidth(280);
            TextArea newMapDescArea = new TextArea();
            newMapDescArea.setPromptText("Map description (optional)");
            newMapDescArea.setPrefRowCount(2);
            newMapDescArea.setWrapText(true);

            searchBtn.setOnAction(e -> {
                Pair<String, String> sel = countryCombo.getSelectionModel().getSelectedItem();
                if (sel == null) {
                    showError("Please select a country first.");
                    return;
                }
                String query = citySearchField.getText() != null ? citySearchField.getText().trim() : "";
                if (query.isEmpty()) {
                    showError("Enter a city name to search.");
                    return;
                }
                setStatus("Searching OpenStreetMap...");
                searchResults.clear();
                searchCitiesInCountry(query, sel.getValue(), list -> {
                    setStatus("Ready");
                    if (list == null) {
                        showError("Search failed. Check your connection and try again.");
                        return;
                    }
                    searchResults.setAll(list);
                    if (list.isEmpty())
                        showError("No places found. Try a different name or country.");
                });
            });

            loadAllBtn.setOnAction(e -> {
                Pair<String, String> sel = countryCombo.getSelectionModel().getSelectedItem();
                if (sel == null) {
                    showError("Please select a country first.");
                    return;
                }
                setStatus("Loading cities from OpenStreetMap (may take a few seconds)...");
                loadAllBtn.setDisable(true);
                searchResults.clear();
                fetchAllCitiesInCountry(sel.getValue(), list -> {
                    loadAllBtn.setDisable(false);
                    setStatus("Ready");
                    if (list == null) {
                        showError("Could not load cities. Check your connection and try again.");
                        return;
                    }
                    searchResults.setAll(list);
                    if (list.isEmpty())
                        showError("No cities or towns found for this country in OpenStreetMap.");
                    else
                        setStatus("Loaded " + list.size() + " cities/towns. Select one below.");
                });
            });

            VBox content = new VBox(10);
            content.getChildren().addAll(
                new Label("Country:"),
                countryCombo,
                new Label("City:"),
                new HBox(8, citySearchField, searchBtn, loadAllBtn),
                new Label("Select a city (load all or search by name):"),
                resultsList,
                new Label("Map name:"),
                newMapNameField,
                new Label("Map description (optional):"),
                newMapDescArea
            );
            dialog.getDialogPane().setContent(content);
            Button createMapBtn = (Button) dialog.getDialogPane().lookupButton(createButtonType);
            if (createMapBtn != null) createMapBtn.setDisable(true);
            Runnable updateCreateEnabled = () -> {
                if (createMapBtn == null) return;
                OSMPlace p = resultsList.getSelectionModel().getSelectedItem();
                String mn = newMapNameField.getText() != null ? newMapNameField.getText().trim() : "";
                createMapBtn.setDisable(p == null || mn.isEmpty());
            };
            resultsList.getSelectionModel().selectedItemProperty().addListener((o, p, c) -> updateCreateEnabled.run());
            newMapNameField.textProperty().addListener((o, p, c) -> updateCreateEnabled.run());

            dialog.setResultConverter(btn -> {
                if (btn == createButtonType) {
                    OSMPlace place = resultsList.getSelectionModel().getSelectedItem();
                    String mapName = newMapNameField.getText() != null ? newMapNameField.getText().trim() : "";
                    String mapDesc = newMapDescArea.getText() != null ? newMapDescArea.getText().trim() : "";
                    if (place == null || mapName.isEmpty()) return null;
                    return new String[]{ place.displayName, mapName, mapDesc };
                }
                return null;
            });

            Optional<String[]> result = dialog.showAndWait();
            result.ifPresent(arr -> {
                pendingNewCityWithMap.add(new MapChanges.CityWithMapRequest(
                        arr[0], "New city description", 50.0, arr[1], arr[2]));
                hasUnsavedChanges = true;
                setStatus("New city \"" + arr[0] + "\" and map \"" + arr[1] + "\" added. Click Save to draft, then Send to manager for approval.");
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
            pendingNewMaps.add(new MapChanges.NewMapRequest(selectedCity.getId(), nameAndDesc[0], nameAndDesc[1]));
            hasUnsavedChanges = true;
            setStatus("New map \"" + nameAndDesc[0] + "\" added. Click Save to draft, then Send to manager for approval.");
            if (selectedCity != null && lastRequestedCityId == selectedCity.getId()) {
                control.getMapsForCity(selectedCity.getId());
            }
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
        editingPoi.setDraft(true); // new POIs are always draft until approved
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
        confirm.setContentText("The POI stays in the city and can be added to other maps. Click Save or Send to manager to apply. Requires manager approval.");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                pendingChanges.getPoiMapUnlinks().add(new MapChanges.PoiMapLink(currentMapContent.getMapId(), selected.getId(), 0));
                selected.setPendingRemoval(true); // Keep in list but tag as pending removal
                poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                hasUnsavedChanges = true;
                handleCancelPoiEdit();
                setStatus("POI removal added to pending changes. Save to submit for approval.");
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

        String newName = poiNameField.getText().trim();
        String newCategory = poiCategoryCombo.getValue() != null ? poiCategoryCombo.getValue() : "Other";
        String newLocation = poiLocationField.getText().trim();
        boolean newAccessible = poiAccessibleCheck.isSelected();
        String newDesc = poiDescArea.getText().trim();

        double lat = editingPoi.getLatitude() != null ? editingPoi.getLatitude() : parseLatLon(newLocation, true);
        double lng = editingPoi.getLongitude() != null ? editingPoi.getLongitude() : parseLatLon(newLocation, false);
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            showError("Valid coordinates are required (e.g. lat,lng or use the map to place the POI).");
            return;
        }
        if (selectedCity == null) {
            showError("No city selected.");
            return;
        }

        // For updates: skip if nothing changed (avoid no-op approval requests)
        if (editingPoi.getId() > 0) {
            boolean same = java.util.Objects.equals(editingPoi.getName(), newName)
                    && java.util.Objects.equals(editingPoi.getCategory(), newCategory)
                    && java.util.Objects.equals(editingPoi.getLocation(), newLocation)
                    && editingPoi.isAccessible() == newAccessible
                    && java.util.Objects.equals(editingPoi.getShortExplanation(), newDesc)
                    && java.lang.Math.abs((editingPoi.getLatitude() != null ? editingPoi.getLatitude() : 0) - lat) < 1e-9
                    && java.lang.Math.abs((editingPoi.getLongitude() != null ? editingPoi.getLongitude() : 0) - lng) < 1e-9;
            if (same) {
                handleCancelPoiEdit();
                setStatus("No changes to save.");
                return;
            }
        }

        editingPoi.setName(newName);
        editingPoi.setCategory(newCategory);
        editingPoi.setLocation(newLocation);
        editingPoi.setAccessible(newAccessible);
        editingPoi.setShortExplanation(newDesc);

        setStatus("Checking location...");
        validatePoiLocationInCity(selectedCity, lat, lng,
            () -> {
                editingPoi.setLatitude(lat);
                editingPoi.setLongitude(lng);
                if (editingPoi.getId() == 0) {
                    editingPoi.setDraft(true); // new POIs stay draft until approved
                    if (pendingChanges.getAddedPois().contains(editingPoi)) {
                        // Already in pending - just updated fields, refresh display (don't duplicate)
                        if (currentMapContent != null) {
                            poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                        }
                        setStatus("POI updated in pending changes. Save or Send to manager to submit.");
                    } else {
                        pendingChanges.addPoi(editingPoi);
                        if (currentMapContent != null) {
                            currentMapContent.getPois().add(editingPoi);
                            poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                        }
                        setStatus("POI added. Save or Send to manager to submit.");
                    }
                } else {
                    // Update: only add if something actually changed; replace if already in updatedPois
                    editingPoi.setDraft(true);
                    java.util.List<common.Poi> updated = pendingChanges.getUpdatedPois();
                    updated.removeIf(p -> p.getId() == editingPoi.getId());
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
                int id = editingPoi.getId();
                pendingChanges.deletePoi(id);
                pendingChanges.getAddedPois().removeIf(p -> p.getId() == id);
                if (pendingChanges.getAddedStops() != null)
                    pendingChanges.getAddedStops().removeIf(s -> s.getPoiId() == id);
                TourDTO sel = toursListView.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getStops() != null)
                    sel.getStops().removeIf(s -> s.getPoiId() == id);
                editingPoi.setPendingDeletion(true); // Keep in list but tag as pending deletion
                poisListView.setItems(FXCollections.observableArrayList(currentMapContent.getPois()));
                if (sel != null) tourStopsListView.setItems(FXCollections.observableArrayList(sel.getStops() != null ? sel.getStops() : java.util.Collections.emptyList()));
                handleCancelPoiEdit();
                hasUnsavedChanges = true;
                setStatus("POI deletion added to pending changes. Save to submit for approval.");
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
        editingTour = new TourDTO(0, selectedCity.getId(), "", "");
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

        String newTourName = tourNameField.getText().trim();
        String newTourDesc = tourDescArea.getText().trim();

        // For updates: skip if nothing changed (avoid no-op approval requests)
        if (editingTour.getId() > 0) {
            boolean same = java.util.Objects.equals(editingTour.getName(), newTourName)
                    && java.util.Objects.equals(editingTour.getDescription(), newTourDesc);
            if (same) {
                handleCancelTourEdit();
                setStatus("No changes to save.");
                return;
            }
        }

        editingTour.setName(newTourName);
        editingTour.setDescription(newTourDesc);

        // Add to pending changes and show in list so you can add stops to the new tour
        if (editingTour.getId() == 0) {
            editingTour.setDraft(true); // new tours await manager approval
            if (pendingChanges.getAddedTours().contains(editingTour)) {
                // Already in pending - just updated fields, refresh display (don't duplicate)
                if (currentMapContent != null) {
                    toursListView.setItems(FXCollections.observableArrayList(currentMapContent.getTours()));
                }
                setStatus("Tour updated. Add stops then Save or Send to manager for approval.");
            } else {
                pendingChanges.addTour(editingTour);
                if (currentMapContent != null) {
                    currentMapContent.getTours().add(editingTour);
                    toursListView.setItems(FXCollections.observableArrayList(currentMapContent.getTours()));
                }
                setStatus("Tour added. Add stops then Save or Send to manager for approval.");
            }
        } else {
            // Replace if already in updatedTours (avoid duplicate approval requests)
            java.util.List<TourDTO> updated = pendingChanges.getUpdatedTours();
            updated.removeIf(t -> t.getId() == editingTour.getId());
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
        if (editingTour == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Tour");
        confirm.setHeaderText("Delete " + editingTour.getName() + "?");
        confirm.setContentText(editingTour.getId() > 0
                ? "Tour will be marked for deletion. Publish to delete permanently (same as POIs)."
                : "This unsaved tour will be removed from the list. Save or Publish to apply.");

        confirm.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            if (editingTour.getId() == 0) {
                // Unsaved tour: remove from list and from pending added tours only
                if (currentMapContent != null && currentMapContent.getTours() != null) {
                    currentMapContent.getTours().remove(editingTour);
                    pendingChanges.getAddedTours().removeIf(t -> t == editingTour);
                    toursListView.setItems(FXCollections.observableArrayList(currentMapContent.getTours()));
                }
                handleCancelTourEdit();
                hasUnsavedChanges = true;
                setStatus("Tour removed. Save or Publish to apply.");
            } else {
                // Saved tour: pending deletion (applied when manager publishes, like POIs)
                pendingChanges.deleteTour(editingTour.getId());
                editingTour.setPendingDeletion(true);
                toursListView.setItems(FXCollections.observableArrayList(currentMapContent.getTours()));
                handleCancelTourEdit();
                hasUnsavedChanges = true;
                setStatus("Tour marked for deletion. Publish to delete permanently.");
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
        TextArea notesArea = new TextArea(selectedStop.getNotes() != null ? selectedStop.getNotes() : "");
        notesArea.setPrefRowCount(2);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Stop order:"), 0, 0);
        grid.add(orderField, 1, 0);
        grid.add(new Label("Notes:"), 0, 1);
        grid.add(notesArea, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveButtonType) {
                try {
                    int order = Integer.parseInt(orderField.getText().trim());
                    if (order <= 0) return null;
                    return new String[]{ String.valueOf(order), notesArea.getText().trim() };
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(arr -> {
            selectedStop.setStopOrder(Integer.parseInt(arr[0]));
            selectedStop.setNotes(arr[1]);
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
                } else {
                    pendingChanges.getAddedStops().removeIf(s ->
                            s.getTourId() == selectedTour.getId() && s.getPoiId() == selectedStop.getPoiId() && s.getStopOrder() == selectedStop.getStopOrder());
                }
                selectedTour.getStops().remove(selectedStop);
                // Renumber remaining stops to 1, 2, 3, ... and record updates for persisted stops
                java.util.List<TourStopDTO> stops = selectedTour.getStops();
                for (int i = 0; i < stops.size(); i++) {
                    TourStopDTO s = stops.get(i);
                    int newOrder = i + 1;
                    if (s.getStopOrder() != newOrder) {
                        s.setStopOrder(newOrder);
                        if (s.getId() > 0) {
                            pendingChanges.getUpdatedStops().removeIf(u -> u.getId() == s.getId());
                            pendingChanges.getUpdatedStops().add(s);
                        }
                    }
                }
                tourStopsListView.setItems(FXCollections.observableArrayList(stops));
                hasUnsavedChanges = true;
                setStatus("Tour stop removal added to pending changes. Click 'Save All Changes' to submit.");
            }
        });
    }

    // ==================== Batch Operations ====================

    /** Build list of pending (unsaved) maps for the currently selected city when it is synthetic (id < 0). */
    private List<MapSummary> buildPendingMapsForSelectedCity() {
        List<MapSummary> out = new ArrayList<>();
        if (selectedCity == null || selectedCity.getId() >= 0) return out;
        int id = selectedCity.getId();
        if (id <= -1000) {
            int index = -(id + 1000);
            if (index >= 0 && index < pendingNewCityWithMap.size()) {
                MapChanges.CityWithMapRequest r = pendingNewCityWithMap.get(index);
                MapSummary s = new MapSummary(-1, r.getMapName() != null ? r.getMapName() : "New map", r.getMapDescription() != null ? r.getMapDescription() : "", 0, 0);
                s.setCityId(id);
                s.setDraft(true);
                out.add(s);
            }
        }
        return out;
    }

    private boolean hasAnyPendingChanges() {
        return pendingChanges.hasChanges()
                || !pendingNewCities.isEmpty()
                || !pendingNewCityWithMap.isEmpty()
                || !pendingNewMaps.isEmpty();
    }

    private void submitPendingChanges(boolean asDraft) {
        if (currentMapContent == null && selectedCity == null && !hasAnyPendingChanges()) {
            // When sending for approval: allow if there is a draft city (with or without maps) to submit
            boolean allowSendWithoutSelection = false;
            if (!asDraft && cityComboBox != null && cityComboBox.getItems() != null) {
                for (CityDTO c : cityComboBox.getItems()) {
                    if (c != null && c.getId() > 0 && c.isDraft()) {
                        allowSendWithoutSelection = true;
                        break;
                    }
                }
            }
            if (!allowSendWithoutSelection) {
                showError("Please select a city or map first, or make some changes.");
                return;
            }
        }
        // Employees must save before sending when there are any unsaved changes that include map/city/tour removal
        boolean hasMapOrCityOrTourDeletion = (pendingChanges.getDeletedMapIds() != null && !pendingChanges.getDeletedMapIds().isEmpty())
                || (pendingChanges.getDeletedCityIds() != null && !pendingChanges.getDeletedCityIds().isEmpty())
                || (pendingChanges.getDeletedTourIds() != null && !pendingChanges.getDeletedTourIds().isEmpty());
        if (!asDraft && LoginController.currentUserRole != LoginController.UserRole.MANAGER
                && hasMapOrCityOrTourDeletion && hasUnsavedChanges) {
            showError("Please save changes first, then send to content manager.");
            setStatus("Save changes before sending map/city/tour removal.");
            return;
        }
        if (currentMapContent != null) {
            pendingChanges.setMapId(currentMapContent.getMapId());
        } else if (selectedMap != null && selectedMap.getId() > 0) {
            pendingChanges.setMapId(selectedMap.getId());
        }
        if (selectedCity != null) {
            pendingChanges.setCityId(selectedCity.getId());
        }
        // When sending for approval with no map: if no city selected, use first draft city so manager can approve the city even before any map exists
        if (!asDraft && pendingChanges.getCityId() == null && currentMapContent == null && cityComboBox != null && cityComboBox.getItems() != null) {
            for (CityDTO c : cityComboBox.getItems()) {
                if (c != null && c.getId() > 0 && c.isDraft()) {
                    pendingChanges.setCityId(c.getId());
                    break;
                }
            }
        }
        // Include pending new cities/maps so they are saved as draft or sent for approval
        pendingChanges.getNewCities().addAll(pendingNewCities);
        pendingChanges.getNewCityWithMap().addAll(pendingNewCityWithMap);
        pendingChanges.getNewMaps().addAll(pendingNewMaps);
        // When saving as draft: skip if user made no new edits (avoids re-saving restored draft and the refresh storm)
        if (asDraft && !hasUnsavedChanges && pendingNewCities.isEmpty() && pendingNewCityWithMap.isEmpty() && pendingNewMaps.isEmpty()) {
            setStatus("No changes to save.");
            return;
        }
        // Also skip when there are literally no changes in the payload
        if (asDraft && !pendingChanges.hasChanges()) {
            setStatus("No changes to save.");
            return;
        }
        // When sending to manager: do not send selected draft city as "delete" (e.g. after Save, new city appears selected; it must show as Add city)
        if (!asDraft && selectedCity != null && selectedCity.getId() > 0 && selectedCity.isDraft()
                && pendingChanges.getNewCities().isEmpty() && pendingChanges.getNewCityWithMap().isEmpty()) {
            pendingChanges.getDeletedCityIds().removeIf(id -> id != null && id == selectedCity.getId());
        }
        if (asDraft) {
            control.saveMapChanges(pendingChanges);
            pendingMapOrCityDeletionUnsaved = false; // Save applied; user can now Send
        } else {
            pendingChanges.setDraft(false);
            control.submitMapChanges(pendingChanges, false);
        }
        setStatus(asDraft ? "Saving changes as draft..." : (LoginController.currentUserRole == LoginController.UserRole.MANAGER ? "Publishing..." : "Sending to content manager..."));
        // Preserve delete intent so Send to manager / Publish still works after Save
        java.util.List<Integer> keptDeletedCityIds = new java.util.ArrayList<>(pendingChanges.getDeletedCityIds());
        java.util.List<Integer> keptDeletedMapIds = new java.util.ArrayList<>(pendingChanges.getDeletedMapIds());
        java.util.List<Integer> keptDeletedPoiIds = new java.util.ArrayList<>(pendingChanges.getDeletedPoiIds());
        java.util.List<Integer> keptDeletedTourIds = new java.util.ArrayList<>(pendingChanges.getDeletedTourIds());
        pendingChanges = new MapChanges();
        pendingChanges.getDeletedCityIds().addAll(keptDeletedCityIds);
        pendingChanges.getDeletedMapIds().addAll(keptDeletedMapIds);
        pendingChanges.getDeletedPoiIds().addAll(keptDeletedPoiIds);
        pendingChanges.getDeletedTourIds().addAll(keptDeletedTourIds);
        pendingNewCities.clear();
        pendingNewCityWithMap.clear();
        pendingNewMaps.clear();
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
                pendingMapOrCityDeletionUnsaved = true; // Must save before send (employee)
                setStatus("Map removal added. Save changes first, then send to content manager.");
                mapsListView.getSelectionModel().clearSelection();
                if (tourMapsListView != null) tourMapsListView.getSelectionModel().clearSelection();
            }
        });
    }

    @FXML
    private void handleDeleteCity() {
        if (selectedCity == null || selectedCity.getId() <= 0) return;
        boolean isManager = LoginController.currentUserRole == LoginController.UserRole.MANAGER;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete City");
        confirm.setHeaderText("Delete city \"" + selectedCity.getName() + "\"?");
        confirm.setContentText("All maps and tours in this city will be removed. "
                + (isManager ? "Press Publish to apply." : "Save to store as draft; send to content manager to submit for approval."));
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                pendingChanges.getDeletedCityIds().add(selectedCity.getId());
                hasUnsavedChanges = true;
                if (!isManager) pendingMapOrCityDeletionUnsaved = true; // Must save before send (employee)
                setStatus("City \"" + selectedCity.getName() + "\" marked for removal. " + (isManager ? "Press Publish to apply." : "Save changes, then Send to content manager."));
                cityComboBox.getSelectionModel().clearSelection();
                selectedCity = null;
                mapsListView.getItems().clear();
                if (tourMapsListView != null) tourMapsListView.getItems().clear();
                clearMapContent();
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
                pendingChanges = new MapChanges();
                pendingNewCities.clear();
                pendingNewCityWithMap.clear();
                pendingNewMaps.clear();
                pendingMapOrCityDeletionUnsaved = false;
                if (currentMapContent != null) {
                    control.getMapContent(currentMapContent.getMapId());
                }
                if (selectedCity != null) {
                    control.getMapsForCity(selectedCity.getId());
                }
                control.getCities();
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
            // Merge pending new cities so employee sees them in real time (before Save)
            List<CityDTO> combined = new ArrayList<>(cities);
            for (int i = 0; i < pendingNewCities.size(); i++) {
                MapChanges.NewCityRequest r = pendingNewCities.get(i);
                CityDTO dto = new CityDTO(-1 - i, r.getName() != null ? r.getName() : "New city", "", 0, 0);
                dto.setDraft(true);
                combined.add(dto);
            }
            for (int i = 0; i < pendingNewCityWithMap.size(); i++) {
                MapChanges.CityWithMapRequest r = pendingNewCityWithMap.get(i);
                CityDTO dto = new CityDTO(-1000 - i, r.getCityName() != null ? r.getCityName() : "New city", "", 0, 1);
                dto.setDraft(true);
                combined.add(dto);
            }
            // Manager: hide cities marked for deletion until Publish
            if (pendingChanges.getDeletedCityIds() != null && !pendingChanges.getDeletedCityIds().isEmpty()) {
                java.util.Set<Integer> toRemove = new java.util.HashSet<>(pendingChanges.getDeletedCityIds());
                combined.removeIf(c -> c.getId() > 0 && toRemove.contains(c.getId()));
            }

            int targetId = pendingSelectCityId;
            if (targetId == -1 && selectedCity != null) {
                targetId = selectedCity.getId();
            }

            cityComboBox.setItems(FXCollections.observableArrayList(combined));

            CityDTO cityToSelect = null;
            if (targetId != -1) {
                for (CityDTO c : combined) {
                    if (c.getId() == targetId) {
                        cityToSelect = c;
                        break;
                    }
                }
            }

            if (cityToSelect != null) {
                cityComboBox.setValue(cityToSelect);
                selectedCity = cityToSelect;
                if (selectedCity.getId() < 0) {
                    List<MapSummary> pendingMaps = buildPendingMapsForSelectedCity();
                    mapsListView.setItems(FXCollections.observableArrayList(pendingMaps));
                    if (tourMapsListView != null) tourMapsListView.getItems().clear();
                } else {
                    control.getMapsForCity(selectedCity.getId());
                }
                createMapBtn.setDisable(false);
            } else {
                cityComboBox.setValue(null);
                selectedCity = null;
                createMapBtn.setDisable(false);
            }

            pendingSelectCityId = -1;
            setStatus("Loaded " + cities.size() + " cities" + (combined.size() > cities.size() ? " (+ " + (combined.size() - cities.size()) + " unsaved)" : ""));
        });
    }

    @Override
    public void onMapsReceived(List<MapSummary> maps) {
        Platform.runLater(() -> {
            if (selectedCity != null && lastRequestedCityId == selectedCity.getId() && selectedCity.getId() > 0) {
                // Split into normal maps (no tour) and tour maps (tourId != null && > 0)
                List<MapSummary> normalMaps = new ArrayList<>();
                List<MapSummary> tourMaps = new ArrayList<>();
                for (MapSummary m : maps) {
                    if (m.getTourId() != null && m.getTourId() > 0) tourMaps.add(m);
                    else normalMaps.add(m);
                }
                // Merge pending new maps into normal list (pending are never tour maps)
                int cityId = selectedCity.getId();
                for (int i = 0; i < pendingNewMaps.size(); i++) {
                    MapChanges.NewMapRequest r = pendingNewMaps.get(i);
                    if (r.getCityId() == cityId) {
                        MapSummary s = new MapSummary(-1 - i, r.getName() != null ? r.getName() : "New map", r.getDescription() != null ? r.getDescription() : "", 0, 0);
                        s.setCityId(cityId);
                        s.setDraft(true);
                        normalMaps.add(s);
                    }
                }
                mapsListView.setItems(FXCollections.observableArrayList(normalMaps));
                if (tourMapsListView != null) tourMapsListView.setItems(FXCollections.observableArrayList(tourMaps));

                int targetMapId = pendingSelectMapId;
                pendingSelectMapId = -1;
                if (targetMapId > 0) {
                    for (MapSummary m : normalMaps) {
                        if (m.getId() == targetMapId) {
                            mapsListView.getSelectionModel().select(m);
                            selectedMap = m;
                            control.getMapContent(m.getId());
                            break;
                        }
                    }
                    if (selectedMap == null || selectedMap.getId() != targetMapId) {
                        for (MapSummary m : tourMaps) {
                            if (m.getId() == targetMapId) {
                                if (tourMapsListView != null) tourMapsListView.getSelectionModel().select(m);
                                selectedMap = m;
                                control.getMapContent(m.getId());
                                break;
                            }
                        }
                    }
                } else if (selectedMap != null && selectedMap.getId() > 0) {
                    // Preserve selection after refresh (e.g. post-save) without requesting getMapContent again (already in flight).
                    for (MapSummary m : normalMaps) {
                        if (m.getId() == selectedMap.getId()) {
                            mapsListView.getSelectionModel().select(m);
                            selectedMap = m;
                            break;
                        }
                    }
                    if (selectedMap != null && (tourMapsListView != null) && mapsListView.getSelectionModel().getSelectedItem() == null) {
                        for (MapSummary m : tourMaps) {
                            if (m.getId() == selectedMap.getId()) {
                                tourMapsListView.getSelectionModel().select(m);
                                selectedMap = m;
                                break;
                            }
                        }
                    }
                } else if (targetMapId != 0) {
                    for (MapSummary m : normalMaps) {
                        if (m.getId() == targetMapId) {
                            mapsListView.getSelectionModel().select(m);
                            selectedMap = m;
                            break;
                        }
                    }
                    if (selectedMap == null || selectedMap.getId() != targetMapId) {
                        for (MapSummary m : tourMaps) {
                            if (m.getId() == targetMapId) {
                                if (tourMapsListView != null) tourMapsListView.getSelectionModel().select(m);
                                selectedMap = m;
                                break;
                            }
                        }
                    }
                }

                setStatus("Loaded " + maps.size() + " map(s) for " + selectedCity.getName() + (normalMaps.size() + tourMaps.size() > maps.size() ? " (+ " + (normalMaps.size() + tourMaps.size() - maps.size()) + " unsaved)" : ""));
            } else {
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
        System.out.println("MapEditorScreen.onMapContentReceived: content=" + (content != null) + (content != null ? " mapId=" + content.getMapId() + " pois=" + (content.getPois() != null ? content.getPois().size() : "null") + " tours=" + (content.getTours() != null ? content.getTours().size() : "null") : ""));
        if (content == null) return;
        Platform.runLater(() -> {
            currentMapContent = content;

            // Restore pending approval items from DRAFT (unlinks, deletes, tour changes).
            // Do NOT repopulate addedPois/updatedPois - those were applied on Save and would cause duplicates.
            common.dto.MapChanges draft = content.getDraftChangesToRestore();
            if (draft != null) {
                pendingChanges.getPoiMapUnlinks().clear();
                if (draft.getPoiMapUnlinks() != null) pendingChanges.getPoiMapUnlinks().addAll(draft.getPoiMapUnlinks());
                pendingChanges.getDeletedPoiIds().clear();
                if (draft.getDeletedPoiIds() != null) pendingChanges.getDeletedPoiIds().addAll(draft.getDeletedPoiIds());
                pendingChanges.getAddedTours().clear();
                if (draft.getAddedTours() != null) {
                    for (common.dto.TourDTO dt : draft.getAddedTours()) {
                        if (dt.getId() == 0 && content.getTours() != null) {
                            for (common.dto.TourDTO ct : content.getTours()) {
                                if (ct.getName() != null && dt.getName() != null && ct.getName().trim().equals(dt.getName().trim())) {
                                    dt.setId(ct.getId());
                                    break;
                                }
                            }
                        }
                        pendingChanges.getAddedTours().add(dt);
                    }
                }
                pendingChanges.getUpdatedTours().clear();
                if (draft.getUpdatedTours() != null) pendingChanges.getUpdatedTours().addAll(draft.getUpdatedTours());
                pendingChanges.getDeletedTourIds().clear();
                if (draft.getDeletedTourIds() != null) pendingChanges.getDeletedTourIds().addAll(draft.getDeletedTourIds());
                pendingChanges.getAddedStops().clear();
                if (draft.getAddedStops() != null) pendingChanges.getAddedStops().addAll(draft.getAddedStops());
                pendingChanges.getUpdatedStops().clear();
                if (draft.getUpdatedStops() != null) pendingChanges.getUpdatedStops().addAll(draft.getUpdatedStops());
                pendingChanges.getDeletedStopIds().clear();
                if (draft.getDeletedStopIds() != null) pendingChanges.getDeletedStopIds().addAll(draft.getDeletedStopIds());
                pendingChanges.getDeletedMapIds().clear();
                if (draft.getDeletedMapIds() != null) pendingChanges.getDeletedMapIds().addAll(draft.getDeletedMapIds());
                pendingChanges.getDeletedCityIds().clear();
                if (draft.getDeletedCityIds() != null) pendingChanges.getDeletedCityIds().addAll(draft.getDeletedCityIds());
            } else {
                // No draft for this map: clear map-specific pending changes so we don't carry over the previous map's edits
                pendingChanges.getPoiMapUnlinks().clear();
                pendingChanges.getPoiMapLinks().clear();
                pendingChanges.getAddedPois().clear();
                pendingChanges.getUpdatedPois().clear();
                pendingChanges.getDeletedPoiIds().clear();
                pendingChanges.getAddedTours().clear();
                pendingChanges.getUpdatedTours().clear();
                pendingChanges.getDeletedTourIds().clear();
                pendingChanges.getAddedStops().clear();
                pendingChanges.getUpdatedStops().clear();
                pendingChanges.getDeletedStopIds().clear();
                pendingChanges.getDeletedMapIds().clear();
                pendingChanges.getDeletedCityIds().clear();
                pendingChanges.setNewMapName(null);
                pendingChanges.setNewMapDescription(null);
            }

            // Update POIs tab (null-safe). Trust server's draft flag (from map_pois.approved): once a POI is published it must not show as [Draft].
            java.util.List<common.Poi> pois = content.getPois();
            if (pois == null) pois = java.util.Collections.emptyList();
            poisListView.setItems(FXCollections.observableArrayList(pois));
            System.out.println("MapEditorScreen.onMapContentReceived: set POIs list, count=" + pois.size());

            // Update Tours tab (null-safe)
            java.util.List<common.dto.TourDTO> tours = content.getTours();
            int tourCount = tours != null ? tours.size() : 0;
            toursListView.setItems(FXCollections.observableArrayList(tours != null ? tours : java.util.Collections.emptyList()));
            System.out.println("MapEditorScreen.onMapContentReceived: set Tours list, count=" + tourCount);

            // Update Map Info tab
            mapNameField.setText(content.getMapName() != null ? content.getMapName() : "");
            mapDescArea.setText(content.getShortDescription() != null ? content.getShortDescription() : "");
            System.out.println("MapEditorScreen.onMapContentReceived: set map info, name=" + content.getMapName());

            setStatus("Editing: " + (content.getMapName() != null ? content.getMapName() : "Map"));

            // Update the map view and markers so the map shows the new city/map, not the previous one
            System.out.println("MapEditorScreen.onMapContentReceived: calling refreshMapMarkers");
            refreshMapMarkers();
            System.out.println("MapEditorScreen.onMapContentReceived: done");
        });
    }

    @Override
    public void onValidationResult(ValidationResult result) {
        Platform.runLater(() -> {
            if (result.isValid()) {
                setStatus("✓ " + result.getSuccessMessage());

                // Auto-select newly created city/map after refresh
                if (result.getCreatedCityId() != null && result.getCreatedCityId() > 0) {
                    pendingSelectCityId = result.getCreatedCityId();
                }
                if (result.getCreatedMapId() != null && result.getCreatedMapId() > 0) {
                    pendingSelectMapId = result.getCreatedMapId();
                }

                // Refresh: when we just created a city, refetch cities so onCitiesReceived can select the new city.
                // Otherwise only refetch current map content (and draft) to avoid a flood of getCities/getMapsForCity/getMapContent.
                Integer createdCityId = result.getCreatedCityId();
                if (createdCityId != null && createdCityId > 0) {
                    control.getCities();
                } else {
                    // After save/publish: refetch only the current map so the list reflects the change. Do not refetch cities or maps list (avoids loading "a whole bunch of stuff").
                    if (currentMapContent != null) {
                        control.getMapContent(currentMapContent.getMapId());
                    }
                }
            } else {
                // If server says these delete-tour intents were already rejected, clear them so employee must perform the action again
                for (ValidationResult.ValidationError e : result.getErrors()) {
                    if ("rejectedDeletedTourIds".equals(e.getField()) && e.getMessage() != null && !e.getMessage().isEmpty()) {
                        for (String s : e.getMessage().split(",")) {
                            try {
                                int id = Integer.parseInt(s.trim());
                                pendingChanges.getDeletedTourIds().remove(Integer.valueOf(id));
                            } catch (NumberFormatException ignored) { }
                        }
                        break;
                    }
                }
                // If server says these delete-POI intents were already rejected, clear them so employee must perform the action again
                for (ValidationResult.ValidationError e : result.getErrors()) {
                    if ("rejectedDeletedPoiIds".equals(e.getField()) && e.getMessage() != null && !e.getMessage().isEmpty()) {
                        for (String s : e.getMessage().split(",")) {
                            try {
                                int id = Integer.parseInt(s.trim());
                                pendingChanges.getDeletedPoiIds().remove(Integer.valueOf(id));
                            } catch (NumberFormatException ignored) { }
                        }
                        break;
                    }
                }
                // Show user-facing message (prefer _general so we don't show raw ids)
                String toShow = "";
                for (ValidationResult.ValidationError e : result.getErrors()) {
                    if ("_general".equals(e.getField())) {
                        toShow = e.getMessage();
                        break;
                    }
                }
                if (toShow.isEmpty()) toShow = result.getErrorSummary();
                showError(toShow);
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

    @Override
    public void onMyDraftReceived(MapEditRequestDTO draft) {
        Platform.runLater(() -> {
            if (draft != null && draft.getChanges() != null && draft.getChanges().getDeletedCityIds() != null
                    && !draft.getChanges().getDeletedCityIds().isEmpty()) {
                pendingChanges.getDeletedCityIds().addAll(draft.getChanges().getDeletedCityIds());
                hasUnsavedChanges = false; // server has it
                control.getCities(); // refresh list so deleted cities are hidden
            }
        });
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
