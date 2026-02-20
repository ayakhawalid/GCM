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
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import client.util.MapClickBridge;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

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

    // Map Tab (WebView + Leaflet map.html)
    @FXML
    private AnchorPane mapTabAnchorPane;
    @FXML
    private WebView mapWebView;
    private MapClickBridge mapClickBridge;
    private boolean mapLoaded = false;

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
                createMapBtn.setDisable(true);
                mapsListView.getItems().clear();
                mapsListView.getSelectionModel().clearSelection();
                clearMapContent();
                setStatus("No city selected");
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

        // Map tab: WebView + Leaflet (map.html) – loads OSM map, click to add POI
        mapClickBridge = new MapClickBridge();
        mapClickBridge.setOnMapClickConsumer(this::addPoiAtLocation);
        mapClickBridge.setOnMapReadyConsumer(() -> {
            mapLoaded = true;
            refreshMapMarkers();
        });
        if (contentTabs != null && mapWebView != null) {
            if (mapTabAnchorPane != null) {
                mapTabAnchorPane.prefWidthProperty().bind(contentTabs.widthProperty());
                mapTabAnchorPane.prefHeightProperty().bind(contentTabs.heightProperty());
            }
            mapWebView.setPrefSize(800, 600);
            mapWebView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            AnchorPane.setTopAnchor(mapWebView, 0.0);
            AnchorPane.setBottomAnchor(mapWebView, 0.0);
            AnchorPane.setLeftAnchor(mapWebView, 0.0);
            AnchorPane.setRightAnchor(mapWebView, 0.0);
            WebEngine engine = mapWebView.getEngine();
            engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
                    try {
                        JSObject window = (JSObject) engine.executeScript("window");
                        window.setMember("app", mapClickBridge);
                        window.setMember("javafx", mapClickBridge);
                        Platform.runLater(() -> {
                            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
                            pause.setOnFinished(e -> engine.executeScript("initMap();"));
                            pause.play();
                        });
                    } catch (Exception e) {
                        System.err.println("MapEditorScreen: set bridge failed: " + e.getMessage());
                    }
                }
            });
            java.net.URL mapUrl = getClass().getResource("/client/map.html");
            if (mapUrl != null) {
                engine.load(mapUrl.toExternalForm());
            }
            contentTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null && newTab.getText() != null && newTab.getText().contains("Map")) {
                    if (!mapLoaded && mapWebView.getEngine().getDocument() != null) {
                        mapLoaded = true;
                        refreshMapMarkers();
                    } else {
                        refreshMapMarkers();
                    }
                    invalidateMapSizeDelayed();
                    Platform.runLater(() -> runMapInvalidateSize(mapWebView.getEngine()));
                    new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(300), e -> runMapInvalidateSize(mapWebView.getEngine()))
                    ).play();
                }
            });
            mapWebView.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> invalidateMapSizeDelayed());
        }

        setStatus("Ready - Select a city to begin");
    }

    private javafx.animation.KeyFrame invalidateMapSizeKeyFrame;
    private javafx.animation.Timeline invalidateMapSizeTimeline;

    private void invalidateMapSizeDelayed() {
        if (mapWebView == null || !mapLoaded) return;
        if (invalidateMapSizeTimeline != null) invalidateMapSizeTimeline.stop();
        invalidateMapSizeKeyFrame = new javafx.animation.KeyFrame(javafx.util.Duration.millis(120), e -> runMapInvalidateSize(mapWebView.getEngine()));
        invalidateMapSizeTimeline = new javafx.animation.Timeline(invalidateMapSizeKeyFrame);
        invalidateMapSizeTimeline.play();
    }

    /** Tell Leaflet to recalculate size so tiles don't shift (WebView + Leaflet fix). */
    private void runMapInvalidateSize(WebEngine engine) {
        if (engine == null) return;
        try {
            engine.executeScript("if (typeof mapInvalidateSize === 'function') mapInvalidateSize();");
        } catch (Exception ignored) {}
    }

    /** Cairo coords for demo view when no city/map selected (OSM only, to test if map works without app data). */
    private static final double DEMO_CAIRO_LAT = 30.0444;
    private static final double DEMO_CAIRO_LNG = 31.2357;

    private void refreshMapMarkers() {
        if (mapWebView == null || !mapLoaded) return;
        WebEngine engine = mapWebView.getEngine();
        try {
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
            if ((pois == null || pois.isEmpty()) && selectedCity != null) {
                double[] def = defaultCenterForCity(selectedCity);
                centerLat = def[0];
                centerLng = def[1];
            }
            engine.executeScript("if (typeof setCenter === 'function') setCenter(" + centerLat + ", " + centerLng + ", 13);");
            engine.executeScript("if (typeof clearMarkers === 'function') clearMarkers();");
            if (pois != null) {
                for (Poi p : pois) {
                    double lat = p.getLatitude() != null ? p.getLatitude() : parseLatLon(p.getLocation(), true);
                    double lng = p.getLongitude() != null ? p.getLongitude() : parseLatLon(p.getLocation(), false);
                    if (Double.isNaN(lat) || Double.isNaN(lng)) continue;
                    String name = (p.getName() != null ? p.getName() : "").replace("'", "\\'").replace("\n", " ");
                    engine.executeScript("if (typeof addMarker === 'function') addMarker(" + p.getId() + ", " + lat + ", " + lng + ", '" + name + "');");
                }
            }
        } catch (Exception e) {
            System.err.println("MapEditorScreen: refreshMapMarkers failed: " + e.getMessage());
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

    /** Default map center when the map has no POIs; uses city name so e.g. Cairo shows Cairo, not Haifa. */
    private static double[] defaultCenterForCity(CityDTO city) {
        if (city == null || city.getName() == null) return new double[] { 32.8, 34.99 };
        String name = city.getName().trim().toLowerCase();
        if (name.contains("cairo")) return new double[] { 30.0444, 31.2357 };
        if (name.contains("haifa")) return new double[] { 32.8, 34.99 };
        if (name.contains("tel aviv")) return new double[] { 32.0853, 34.7818 };
        if (name.contains("jerusalem")) return new double[] { 31.7683, 35.2137 };
        return new double[] { 32.8, 34.99 };
    }

    /**
     * Called when user clicks on the OSM map. Add a new POI at that location and show the edit form.
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
        editingPoi = new Poi(0, selectedCity.getId(), "", "", "", "", true);
        editingPoi.setLatitude(lat);
        editingPoi.setLongitude(lng);
        editingPoi.setLocation(String.format("%.6f,%.6f", lat, lng));
        showPoiEditForm(editingPoi);
        contentTabs.getSelectionModel().selectNext(); // switch to POIs tab to show the form
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
        dialog.setContentText("City name:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                setStatus("Creating city...");
                control.createCity(name.trim(), "New city description", 50.0);
            }
        });
    }

    // ==================== Map Operations ====================

    @FXML
    private void handleCreateMap() {
        System.out.println("DEBUG: handleCreateMap called. SelectedCity: "
                + (selectedCity != null ? selectedCity.getName() : "null"));
        if (selectedCity == null) {
            showError("Please select a city first");
            return;
        }

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

        // Add to pending changes and show in list
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
        setStatus(asDraft ? "Saving changes as draft..." : "Sending to content manager...");
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
                // No city to select - clear value explicitly
                cityComboBox.setValue(null);
                selectedCity = null;
                createMapBtn.setDisable(true);
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
