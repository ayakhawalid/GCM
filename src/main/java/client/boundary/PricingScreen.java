package client.boundary;

import client.GCMClient;
import client.LoginController;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CityPriceInfo;
import common.dto.SubmitPricingRequest;
import common.dto.PricingRequestDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * Controller for Pricing Management screen.
 * Allows ContentManager to view prices and submit pricing requests.
 */
public class PricingScreen implements GCMClient.MessageHandler {

    @FXML
    private TableView<CityPriceInfo> pricesTable;
    @FXML
    private TableColumn<CityPriceInfo, Integer> cityIdCol;
    @FXML
    private TableColumn<CityPriceInfo, String> cityNameCol;
    @FXML
    private TableColumn<CityPriceInfo, String> currentPriceCol;

    @FXML
    private Label selectedCityLabel;
    @FXML
    private Label currentPriceLabel;
    @FXML
    private TextField newPriceField;
    @FXML
    private Label priceChangeLabel;
    @FXML
    private TextArea reasonField;
    @FXML
    private Button submitBtn;
    @FXML
    private Label errorLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private HBox pendingRequestsBox;

    private GCMClient client;
    private CityPriceInfo selectedCity;
    private ObservableList<CityPriceInfo> pricesList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        System.out.println("PricingScreen: Initializing");

        // Setup table columns
        cityIdCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCityId()).asObject());
        cityNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCityName()));
        currentPriceCol.setCellValueFactory(
                data -> new SimpleStringProperty(String.format("₪%.2f", data.getValue().getOneTimePrice())));

        pricesTable.setItems(pricesList);

        // Table selection listener
        pricesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> onCitySelected(newVal));

        // Price field listener for real-time change preview
        newPriceField.textProperty().addListener((obs, oldVal, newVal) -> updatePriceChangePreview());

        // Connect and load data
        try {
            client = GCMClient.getInstance();
            client.setMessageHandler(this);
            refreshPrices(null);
        } catch (IOException e) {
            showError("Failed to connect to server");
            e.printStackTrace();
        }
    }

    private void onCitySelected(CityPriceInfo city) {
        selectedCity = city;
        if (city != null) {
            selectedCityLabel.setText(city.getCityName());
            currentPriceLabel.setText(String.format("₪%.2f", city.getOneTimePrice()));
            submitBtn.setDisable(false);
        } else {
            selectedCityLabel.setText("(Select a city from the table)");
            currentPriceLabel.setText("₪0.00");
            submitBtn.setDisable(true);
        }
        updatePriceChangePreview();
    }

    private void updatePriceChangePreview() {
        if (selectedCity == null) {
            priceChangeLabel.setText("");
            return;
        }

        try {
            double newPrice = Double.parseDouble(newPriceField.getText().trim());
            double currentPrice = selectedCity.getOneTimePrice();
            double change = ((newPrice - currentPrice) / currentPrice) * 100;

            String sign = change >= 0 ? "+" : "";
            String color = change >= 0 ? "#27ae60" : "#e74c3c";
            priceChangeLabel.setText(String.format("Change: %s%.1f%%", sign, change));
            priceChangeLabel.setStyle("-fx-text-fill: " + color + ";");
        } catch (NumberFormatException e) {
            priceChangeLabel.setText("");
        }
    }

    @FXML
    public void refreshPrices(ActionEvent event) {
        try {
            statusLabel.setText("Loading...");
            String token = LoginController.currentSessionToken;
            Request request = new Request(MessageType.GET_CURRENT_PRICES, null, token);
            client.sendToServer(request);
        } catch (IOException e) {
            showError("Failed to load prices");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleSubmit(ActionEvent event) {
        clearError();

        // Validate selection
        if (selectedCity == null) {
            showError("Please select a city first");
            return;
        }

        // Validate price
        double newPrice;
        try {
            newPrice = Double.parseDouble(newPriceField.getText().trim());
        } catch (NumberFormatException e) {
            showError("Please enter a valid price");
            return;
        }

        if (newPrice <= 0) {
            showError("Price must be greater than 0");
            return;
        }

        if (newPrice > 10000) {
            showError("Price cannot exceed ₪10,000");
            return;
        }

        if (Math.abs(newPrice - selectedCity.getOneTimePrice()) < 0.01) {
            showError("New price must be different from current price");
            return;
        }

        // Validate reason
        String reason = reasonField.getText().trim();
        if (reason.length() < 10) {
            showError("Reason must be at least 10 characters");
            return;
        }

        // Submit request
        try {
            statusLabel.setText("Submitting...");
            SubmitPricingRequest payload = new SubmitPricingRequest(
                    selectedCity.getCityId(), newPrice, reason);
            String token = LoginController.currentSessionToken;
            int userId = LoginController.currentUserId;
            Request request = new Request(MessageType.SUBMIT_PRICING_REQUEST, payload, token, userId);
            client.sendToServer(request);
        } catch (IOException e) {
            showError("Failed to submit pricing request");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        navigateTo("/client/dashboard.fxml", "GCM Dashboard", 1000, 700);
    }

    @Override
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (!(msg instanceof Response))
                return;

            Response response = (Response) msg;
            statusLabel.setText("Ready");

            if (response.getRequestType() == MessageType.GET_CURRENT_PRICES) {
                if (response.isOk()) {
                    @SuppressWarnings("unchecked")
                    List<CityPriceInfo> prices = (List<CityPriceInfo>) response.getPayload();
                    pricesList.clear();
                    pricesList.addAll(prices);
                    System.out.println("PricingScreen: Loaded " + prices.size() + " prices");
                } else {
                    showError(response.getErrorMessage());
                }
            }

            else if (response.getRequestType() == MessageType.SUBMIT_PRICING_REQUEST) {
                if (response.isOk()) {
                    PricingRequestDTO created = (PricingRequestDTO) response.getPayload();
                    showSuccess("Pricing request submitted successfully!\n" +
                            "Request ID: " + created.getId());
                    // Clear form
                    newPriceField.clear();
                    reasonField.clear();
                    pricesTable.getSelectionModel().clearSelection();
                } else {
                    showError(response.getErrorMessage());
                }
            }
        });
    }

    private void showError(String message) {
        errorLabel.setText("❌ " + message);
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText("✅ Request Submitted");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void clearError() {
        errorLabel.setText("");
    }

    private void navigateTo(String fxml, String title, int width, int height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage stage = (Stage) pricesTable.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Could not navigate to screen");
            e.printStackTrace();
        }
    }
}
