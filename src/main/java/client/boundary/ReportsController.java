package client.boundary;

import client.GCMClient;
import common.City;
import common.DailyStat;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.ReportRequest;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class ReportsController implements GCMClient.MessageHandler {

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private ComboBox<City> cityComboBox;

    @FXML
    private BarChart<String, Number> activityChart;

    @FXML
    private Label statusLabel;

    private GCMClient client;
    // We navigate to new Dashboard instance on back, so we don't strictly need the
    // old controller
    // unless we want to preserve state. For simplicity, we navigate fresh.

    public void setClient(GCMClient client) {
        this.client = client;
        if (client != null) {
            client.setMessageHandler(this);
        }
    }

    @FXML
    public void initialize() {
        // Set default dates (last 30 days)
        endDatePicker.setValue(LocalDate.now());
        startDatePicker.setValue(LocalDate.now().minusDays(30));

        // Configure City ComboBox
        configureCityComboBox();

        // Load cities
        loadCities();
    }

    private void configureCityComboBox() {
        cityComboBox.setConverter(new StringConverter<City>() {
            @Override
            public String toString(City city) {
                if (city == null)
                    return "All Cities";
                return city.getName();
            }

            @Override
            public City fromString(String string) {
                return null;
            }
        });
    }

    private void loadCities() {
        if (client == null)
            return;

        try {
            client.sendToServer(new Request(MessageType.GET_CITIES, null));
        } catch (IOException e) {
            statusLabel.setText("Error loading cities");
            e.printStackTrace();
        }
    }

    @Override
    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (msg instanceof Response) {
                Response response = (Response) msg;
                handleResponse(response);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleResponse(Response response) {
        if (response.getRequestType() == MessageType.GET_CITIES) {
            if (response.isOk()) {
                List<City> cities = (List<City>) response.getPayload();
                cityComboBox.setItems(FXCollections.observableArrayList(cities));
            }
        } else if (response.getRequestType() == MessageType.GET_ACTIVITY_REPORT) {
            if (response.isOk()) {
                List<DailyStat> stats = (List<DailyStat>) response.getPayload();
                updateChart(stats);
                statusLabel.setText("Report generated successfully.");
            } else {
                statusLabel.setText("Error: " + response.getErrorMessage());
                showAlert("Error", "Failed to generate report: " + response.getErrorMessage());
            }
        }
    }

    @FXML
    void handleGenerateReport(ActionEvent event) {
        LocalDate from = startDatePicker.getValue();
        LocalDate to = endDatePicker.getValue();
        City selectedCity = cityComboBox.getValue(); // Null means all

        if (from == null || to == null) {
            showAlert("Validation Error", "Please select valid start and end dates.");
            return;
        }

        if (from.isAfter(to)) {
            showAlert("Validation Error", "Start date cannot be after end date.");
            return;
        }

        Integer cityId = selectedCity != null ? selectedCity.getId() : null;
        ReportRequest reqPayload = new ReportRequest(from, to, cityId);
        Request request = new Request(MessageType.GET_ACTIVITY_REPORT, reqPayload);

        statusLabel.setText("Generating report...");

        try {
            client.sendToServer(request);
        } catch (IOException e) {
            statusLabel.setText("Error sending request");
            e.printStackTrace();
        }
    }

    private void updateChart(List<DailyStat> stats) {
        activityChart.getData().clear();

        int totalMaps = 0;
        int totalOneTime = 0;
        int totalSubs = 0;
        int totalRenewals = 0;
        int totalViews = 0;
        int totalDownloads = 0;

        for (DailyStat s : stats) {
            totalMaps += s.getMapsCount();
            totalOneTime += s.getOneTimePurchases();
            totalSubs += s.getSubscriptions();
            totalRenewals += s.getRenewals();
            totalViews += s.getViews();
            totalDownloads += s.getDownloads();
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Activity");

        series.getData().add(new XYChart.Data<>("Maps", totalMaps));
        series.getData().add(new XYChart.Data<>("Purchases", totalOneTime));
        series.getData().add(new XYChart.Data<>("Subscriptions", totalSubs));
        series.getData().add(new XYChart.Data<>("Renewals", totalRenewals));
        series.getData().add(new XYChart.Data<>("Views", totalViews));
        series.getData().add(new XYChart.Data<>("Downloads", totalDownloads));

        activityChart.getData().add(series);
    }

    @FXML
    void handleBack(ActionEvent event) {
        try {
            // Navigate back to Dashboard
            // Assuming DashboardScreen.fxml is in /client/boundary/DashboardScreen.fxml
            // based on conventions?
            // Actually previous files suggested it might be a problem finding it.
            // Let's assume standard path: /client/boundary/DashboardScreen.fxml
            // If that file doesn't exist, we need to know where it is.
            // Given DashboardScreen.java is in client.boundary, likely FXML is too.
            // But earlier grep failed to find it?
            // If it's not found, user can fix.

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/dashboard.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("GCM Dashboard");
            stage.setMaximized(true);
            stage.centerOnScreen();

        } catch (IOException e) {
            // Fallback: Try Login if dashboard fails
            // e.printStackTrace();
            // showAlert("Navigation Error", "Could not return to Dashboard.");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/login.fxml"));
                Parent root = loader.load();
                Stage stage = (Stage) statusLabel.getScene().getWindow();
                stage.setScene(new Scene(root, 500, 600));
                stage.setMaximized(true);
            } catch (IOException ex) {
                ex.printStackTrace();
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
}
