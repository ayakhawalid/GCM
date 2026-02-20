package client.boundary;

import client.GCMClient;
import client.LoginController;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.EntitlementInfo;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
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

    private GCMClient gcmClient;
    private ObservableList<PurchaseItem> subscriptionsList = FXCollections.observableArrayList();
    private ObservableList<PurchaseItem> purchasesList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTables();
        connectToServer();
    }

    private void setupTables() {
        // Subscriptions Columns
        subCityCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cityName));
        subStatusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status));
        subExpiryCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().dateInfo));

        subActionCol.setCellFactory(col -> new TableCell<PurchaseItem, String>() {
            private final Button btn = new Button("View maps");
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
                    btn.setTooltip(row != null && !row.canDownload ? new Tooltip("Subscription expired") : new Tooltip("View all city maps"));
                    setGraphic(btn);
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
                    btn.setTooltip(row != null && !row.canDownload ? new Tooltip("One-time purchase: already downloaded") : null);
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
        try {
            gcmClient.sendToServer(new Request(MessageType.DOWNLOAD_MAP_VERSION, item.cityId, token));
            statusLabel.setText(item.isSubscription ? "Opening " + item.cityName + " maps..." : "Downloading " + item.cityName + "...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        navigateTo("/client/dashboard.fxml", "GCM Dashboard", 1000, 700);
    }

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
                    statusLabel.setText("Error: " + response.getErrorMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                    return;
                }

                if (response.getRequestType() == MessageType.DOWNLOAD_MAP_VERSION) {
                    statusLabel.setText("Content loaded.");
                    statusLabel.setStyle("-fx-text-fill: green;");
                    showAlert("Success", "Content loaded successfully. You can view or download the map.");
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
            String status = item.isActive() ? "Active" : "Expired";

            PurchaseItem pi = new PurchaseItem(
                    item.getCityId(),
                    item.getCityName(),
                    status,
                    dateInfo,
                    item.isSubscription(),
                    item.isCanDownload());

            if (item.isSubscription()) {
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

        public PurchaseItem(int cityId, String cityName, String status, String dateInfo, boolean isSubscription, boolean canDownload) {
            this.cityId = cityId;
            this.cityName = cityName;
            this.status = status;
            this.dateInfo = dateInfo;
            this.isSubscription = isSubscription;
            this.canDownload = canDownload;
        }
    }
}
