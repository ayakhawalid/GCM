package client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import java.io.IOException;
import java.util.ArrayList;

public class PrimaryController {

    @FXML
    private TextArea resultArea;

    // Box for "Get Maps"
    @FXML
    private TextField cityIdField;

    // Boxes for "Update Price"
    @FXML
    private TextField updateIdField; // <--- NEW!
    @FXML
    private TextField priceField;

    private GCMClient client;

    public void setClient(GCMClient client) {
        this.client = client;
    }

    // --- BUTTON ACTIONS ---

    @FXML
    void getAllCities(ActionEvent event) {
        sendMessage("get_cities");
    }

    @FXML
    void getMaps(ActionEvent event) {
        String id = cityIdField.getText();
        if (id.isEmpty()) {
            resultArea.setText("Error: Please enter a City ID first.");
            return;
        }
        sendMessage("get_maps " + id);
    }

    @FXML
    void updatePrice(ActionEvent event) {
        // Now reads from the NEW dedicated ID box
        String id = updateIdField.getText();
        String price = priceField.getText();

        if (id.isEmpty() || price.isEmpty()) {
            resultArea.setText("Error: Enter both ID and Price.");
            return;
        }
        sendMessage("update_price " + id + " " + price);
    }

    // Helper to send messages safely
    private void sendMessage(String msg) {
        try {
            client.sendToServer(msg);
        } catch (IOException e) {
            resultArea.setText("Error: Could not send message to server.");
        }
    }

    public void displayMessage(Object msg) {
        Platform.runLater(() -> {
            if (msg instanceof ArrayList) {
                StringBuilder sb = new StringBuilder();
                ArrayList<?> list = (ArrayList<?>) msg;
                for (Object o : list) {
                    sb.append(o.toString()).append("\n");
                }
                resultArea.setText(sb.toString());
            } else {
                resultArea.setText(msg.toString());
            }
        });
    }

    @FXML
    void openSearchScreen(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/client/catalog_search.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) resultArea.getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle("GCM - City Catalog");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            resultArea.setText("Error: Could not open search screen.");
            e.printStackTrace();
        }
    }

    @FXML
    void openMapEditor(ActionEvent event) {
        // Check user role - guests cannot access map editor
        LoginController.UserRole role = LoginController.currentUserRole;

        // Block anonymous users (guests)
        if (role == LoginController.UserRole.ANONYMOUS) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Access Denied");
            alert.setHeaderText("Login Required");
            alert.setContentText("Map Editor requires login.\n" +
                    "Please login to access this feature.");
            alert.showAndWait();
            return;
        }

        // Show info message for customers (their changes will be submitted as requests)
        if (role == LoginController.UserRole.CUSTOMER) {
            javafx.scene.control.Alert info = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            info.setTitle("Edit Request Mode");
            info.setHeaderText("You are in Request Mode");
            info.setContentText("As a customer, your changes will be submitted as requests.\n" +
                    "An employee will review and approve your changes.");
            info.showAndWait();
        }

        // Open the map editor
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/client/map_editor.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) resultArea.getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle("GCM - Map Editor");
            stage.setMaximized(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            resultArea.setText("Error: Could not open map editor.");
            e.printStackTrace();
        }
    }
}