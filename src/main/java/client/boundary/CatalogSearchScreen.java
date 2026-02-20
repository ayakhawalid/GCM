package client.boundary;

import client.control.SearchControl;
import common.dto.CitySearchResult;
import common.dto.MapSummary;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * Controller for the Catalog Search screen.
 * Provides search functionality without requiring login.
 */
public class CatalogSearchScreen implements SearchControl.SearchResultCallback {

    @FXML
    private RadioButton cityModeRadio;
    @FXML
    private RadioButton poiModeRadio;
    @FXML
    private RadioButton bothModeRadio;
    @FXML
    private ToggleGroup searchModeGroup;

    @FXML
    private VBox cityInputBox;
    @FXML
    private VBox poiInputBox;
    @FXML
    private TextField citySearchField;
    @FXML
    private TextField poiSearchField;

    @FXML
    private Button searchButton;
    @FXML
    private Button showAllButton;
    @FXML
    private Button backButton;

    @FXML
    private Label statusLabel;
    @FXML
    private Label resultCountLabel;

    @FXML
    private ListView<CitySearchResult> resultsListView;
    @FXML
    private ListView<MapSummary> mapsListView;

    @FXML
    private VBox detailsCard;
    @FXML
    private VBox emptyState;
    @FXML
    private VBox mapDetailsBox;

    @FXML
    private Label cityNameLabel;
    @FXML
    private Label cityDescLabel;
    @FXML
    private Label priceLabel;
    @FXML
    private Label mapNameLabel;
    @FXML
    private Label mapDescLabel;
    @FXML
    private Label poiCountLabel;
    @FXML
    private Label tourCountLabel;

    private SearchControl searchControl;
    private ObservableList<CitySearchResult> searchResults;
    private ObservableList<MapSummary> mapsList;

    @FXML
    public void initialize() {
        searchResults = FXCollections.observableArrayList();
        mapsList = FXCollections.observableArrayList();

        resultsListView.setItems(searchResults);
        mapsListView.setItems(mapsList);

        // Custom cell factory for results list
        resultsListView.setCellFactory(lv -> new ListCell<CitySearchResult>() {
            @Override
            protected void updateItem(CitySearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText("🏙️ " + item.getCityName() + " (" + item.getTotalMaps() + " maps)");
                    setStyle(
                            "-fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10; -fx-background-color: transparent;");
                }
            }
        });

        // Custom cell factory for maps list
        mapsListView.setCellFactory(lv -> new ListCell<MapSummary>() {
            @Override
            protected void updateItem(MapSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText("📍 " + item.getName());
                    setStyle(
                            "-fx-text-fill: #ccc; -fx-font-size: 13px; -fx-padding: 8; -fx-background-color: transparent;");
                }
            }
        });

        // Selection listeners
        resultsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showCityDetails(newVal));

        mapsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showMapDetails(newVal));

        // Search mode change listeners
        searchModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            updateSearchInputs();
        });

        // Enter key triggers search
        citySearchField.setOnAction(e -> handleSearch());
        if (poiSearchField != null) {
            poiSearchField.setOnAction(e -> handleSearch());
        }

        // Connect to server
        connectToServer();
    }

    private void connectToServer() {
        try {
            searchControl = new SearchControl("localhost", 5555);
            searchControl.setResultCallback(this);
            updateStatus("✅ Connected to server. Ready to search!", "#27ae60");
        } catch (IOException e) {
            updateStatus("❌ Could not connect to server. Is it running?", "#e74c3c");
            searchButton.setDisable(true);
            showAllButton.setDisable(true);
        }
    }

    private void updateSearchInputs() {
        boolean showCity = cityModeRadio.isSelected() || bothModeRadio.isSelected();
        boolean showPoi = poiModeRadio.isSelected() || bothModeRadio.isSelected();

        cityInputBox.setVisible(showCity);
        cityInputBox.setManaged(showCity);
        poiInputBox.setVisible(showPoi);
        poiInputBox.setManaged(showPoi);

        if (!showCity)
            citySearchField.clear();
        if (!showPoi && poiSearchField != null)
            poiSearchField.clear();
    }

    @FXML
    private void handleSearch() {
        if (searchControl == null) {
            updateStatus("❌ Not connected to server", "#e74c3c");
            return;
        }

        String cityName = citySearchField.getText().trim();
        String poiName = poiSearchField != null ? poiSearchField.getText().trim() : "";

        updateStatus("🔍 Searching...", "#667eea");

        if (cityModeRadio.isSelected()) {
            if (cityName.isEmpty()) {
                updateStatus("⚠️ Please enter a city name", "#f39c12");
                return;
            }
            searchControl.searchByCityName(cityName);
        } else if (poiModeRadio.isSelected()) {
            if (poiName.isEmpty()) {
                updateStatus("⚠️ Please enter a POI name", "#f39c12");
                return;
            }
            searchControl.searchByPoiName(poiName);
        } else { // bothModeRadio
            if (cityName.isEmpty() && poiName.isEmpty()) {
                updateStatus("⚠️ Please enter at least one search term", "#f39c12");
                return;
            }
            searchControl.searchByCityAndPoi(cityName, poiName);
        }
    }

    @FXML
    private void handleShowAll() {
        if (searchControl == null) {
            updateStatus("❌ Not connected to server", "#e74c3c");
            return;
        }

        updateStatus("📋 Loading catalog...", "#667eea");
        searchControl.getCatalog();
    }

    @FXML
    private void handleBack() {
        try {
            // Disconnect from server
            if (searchControl != null) {
                searchControl.disconnect();
            }

            // Determine where to go based on login state
            String targetFxml = "/client/dashboard.fxml";
            String title = "GCM Dashboard";
            int width = 1000;
            int height = 700;

            String username = client.LoginController.getCurrentUsername();
            if (username == null || username.isEmpty()) {
                // Not logged in (accessed via "Browse Catalog") -> Go back to Login
                targetFxml = "/client/login.fxml";
                title = "GCM Login";
                width = 500;
                height = 600;
            }

            // Load Target
            FXMLLoader loader = new FXMLLoader(getClass().getResource(targetFxml));
            Parent root = loader.load();

            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, width, height));
            stage.setTitle(title);
            stage.centerOnScreen();
            javafx.application.Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            e.printStackTrace();
            updateStatus("Error returning to previous screen", "#e74c3c");
        }
    }

    @FXML
    private VBox purchaseBox;

    private void showCityDetails(CitySearchResult city) {
        if (city == null) {
            detailsCard.setVisible(false);
            detailsCard.setManaged(false);
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            mapDetailsBox.setVisible(false);
            mapDetailsBox.setManaged(false);
            return;
        }

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        detailsCard.setVisible(true);
        detailsCard.setManaged(true);

        cityNameLabel.setText(city.getCityName());
        cityDescLabel
                .setText(city.getCityDescription() != null ? city.getCityDescription() : "No description available");
        priceLabel.setText(String.format("$%.2f", city.getCityPrice()));

        // Maps
        mapsList.clear();
        mapsList.addAll(city.getMaps());
        mapDetailsBox.setVisible(false);
        mapDetailsBox.setManaged(false);

        // Purchase Buttons (Phase 5)
        // Only show if not just browsing (conceptually, though here we allow clicking
        // but it might fail if not logged in)
        // We will make it visible and rely on server rejection if anonymous.
        purchaseBox.setVisible(true);
        purchaseBox.setManaged(true);
        purchaseBox.getChildren().clear();

        Label lbl = new Label("Purchase Options:");
        lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        purchaseBox.getChildren().add(lbl);

        // One Time
        Button buyBtn = new Button("Buy One-Time ($" + city.getCityPrice() + ")");
        buyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand;");
        buyBtn.setMaxWidth(Double.MAX_VALUE);
        buyBtn.setOnAction(e -> handlePurchase(city.getCityId(), 0));
        purchaseBox.getChildren().add(buyBtn);

        // Subscription
        HBox subRow = new HBox(10);
        subRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        ComboBox<Integer> monthsLink = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6));
        monthsLink.setValue(1);
        monthsLink.setPrefWidth(70);

        Button subBtn = new Button("Subscribe (Months)");
        subBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
        subBtn.setOnAction(e -> handlePurchase(city.getCityId(), monthsLink.getValue()));

        subRow.getChildren().addAll(monthsLink, subBtn);
        purchaseBox.getChildren().add(subRow);
    }

    private void handlePurchase(int cityId, int months) {
        // Guest restriction: Block guests from purchasing (Phase 5 fix)
        if (client.LoginController.isAnonymousUser()) {
            updateStatus("⚠️ Guests cannot purchase - Please log in first", "#f39c12");
            return;
        }

        // Send request (include session token so server can identify the customer)
        String token = client.LoginController.currentSessionToken;
        if (token == null || token.isEmpty()) {
            updateStatus("⚠️ Session expired - Please log in again", "#f39c12");
            return;
        }
        try {
            common.Request req;
            if (months == 0) {
                req = new common.Request(common.MessageType.PURCHASE_ONE_TIME, new common.dto.PurchaseRequest(cityId), token);
            } else {
                req = new common.Request(common.MessageType.PURCHASE_SUBSCRIPTION,
                        new common.dto.PurchaseRequest(cityId, months), token);
            }
            // Use searchControl to access client, but searchControl is designed for simple
            // search.
            // We need to access the client connection.
            // SearchControl hides the client. Adding a helper getter or executing raw
            // command.
            // Unfortunate coupling here.
            // Actually SearchControl has getClient() ? No.
            // We can add sendRequest to SearchControl.

            // For now, let's assume we can't easily buy from CatalogSearchScreen without
            // modifying SearchControl
            // to support generic requests or exposing client.
            // Let's modify SearchControl really quick to support sending arbitrary requests
            // or add sendPurchaseRequest.
            if (searchControl != null) {
                searchControl.sendPurchaseRequest(req);
                updateStatus("Purchase request sent!", "blue");
            }
        } catch (Exception e) {
            updateStatus("Purchase failed: " + e.getMessage(), "red");
        }
    }

    private void showMapDetails(MapSummary map) {
        if (map == null) {
            mapDetailsBox.setVisible(false);
            mapDetailsBox.setManaged(false);
            return;
        }

        mapDetailsBox.setVisible(true);
        mapDetailsBox.setManaged(true);

        mapNameLabel.setText(map.getName());
        mapDescLabel.setText(map.getShortDescription() != null ? map.getShortDescription() : "No description");
        poiCountLabel.setText("📍 " + map.getPoiCount() + " Points of Interest");
        tourCountLabel.setText("🚶 " + map.getTourCount() + " Tours");
    }

    private void updateStatus(String message, String color) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        });
    }

    // ==================== SearchResultCallback Implementation ====================

    @Override
    public void onSearchResults(List<CitySearchResult> results) {
        Platform.runLater(() -> {
            searchResults.clear();
            searchResults.addAll(results);

            if (results.isEmpty()) {
                updateStatus("🔍 No results found. Try a different search term.", "#f39c12");
                resultCountLabel.setText("0 results");
            } else {
                int totalMaps = results.stream().mapToInt(CitySearchResult::getTotalMaps).sum();
                updateStatus("✅ Found " + results.size() + " cities with " + totalMaps + " maps", "#27ae60");
                resultCountLabel.setText(results.size() + " cities, " + totalMaps + " maps");
            }

            // Clear selection
            resultsListView.getSelectionModel().clearSelection();
            showCityDetails(null);
        });
    }

    @Override
    public void onError(String errorCode, String errorMessage) {
        Platform.runLater(() -> {
            updateStatus("❌ Error: " + errorMessage, "#e74c3c");
            resultCountLabel.setText("Error");
        });
    }
}
