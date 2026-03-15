package client;

import common.MessageType;
import common.Request;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main application class to launch the Login page.
 * Run this class to start the login window.
 * On window close, sends LOGOUT so the server frees the session (avoids "already logged in" after restart).
 */
public class LoginApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the login FXML file from resources/client/
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/login.fxml"));
        Parent root = loader.load();

        // Create the scene with the login form
        Scene scene = new Scene(root, 500, 600);

        // Configure the stage (window)
        primaryStage.setTitle("GCM - Login");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(500);
        primaryStage.setMaximized(true);

        // When user closes the window, logout and close connection so next run can log in
        primaryStage.setOnCloseRequest(e -> {
            String token = LoginController.currentSessionToken;
            if (token != null && !token.isEmpty()) {
                try {
                    GCMClient client = GCMClient.getInstance();
                    client.sendToServer(new Request(MessageType.LOGOUT, token, token));
                    client.closeConnection();
                } catch (IOException | RuntimeException ex) {
                    // Ignore if already disconnected or not yet connected
                }
                LoginController.currentSessionToken = null;
                LoginController.currentUsername = null;
            }
        });

        // Show the window
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
