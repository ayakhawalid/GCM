package client;

import common.MessageType;
import common.Request;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * Main application class to launch the Login page.
 * Run this class to start the login window.
 * On window close, sends LOGOUT so the server frees the session (avoids "already logged in" after restart).
 */
public class LoginApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Must run before login FXML loads — LoginController calls GCMClient.getInstance() in initialize().
        applyServerEndpointFromArgsAndSystemProperties();

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

    /**
     * Remote client: pass server machine IP/hostname as the first program argument, optional port as second.
     * Example: {@code java -jar GCM-Client.jar 192.168.1.50 5555}
     * <p>
     * Or set system properties: {@code -Dgcm.server.host=192.168.1.50 -Dgcm.server.port=5555}
     * <p>
     * Optional classpath file {@code /gcm-client.properties} ({@code gcm.server.host},
     * {@code gcm.server.port}) sets defaults before system properties (edit without recompiling when IP changes).
     * <p>
     * Precedence (lowest to highest): built-in default → {@code gcm-client.properties} → {@code -D} → CLI args.
     */
    private void applyServerEndpointFromArgsAndSystemProperties() {
        String host = "localhost";
        int port = 5555;

        try (InputStream in = LoginApp.class.getResourceAsStream("/gcm-client.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String fileHost = p.getProperty("gcm.server.host");
                if (fileHost != null && !fileHost.isBlank()) {
                    host = fileHost.trim();
                }
                String filePort = p.getProperty("gcm.server.port");
                if (filePort != null && !filePort.isBlank()) {
                    try {
                        port = Integer.parseInt(filePort.trim());
                    } catch (NumberFormatException ignored) {
                        /* keep default */
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("GCM: could not read gcm-client.properties: " + e.getMessage());
        }

        String propHost = System.getProperty("gcm.server.host");
        if (propHost != null && !propHost.isBlank()) {
            host = propHost.trim();
        }
        String propPort = System.getProperty("gcm.server.port");
        if (propPort != null && !propPort.isBlank()) {
            try {
                port = Integer.parseInt(propPort.trim());
            } catch (NumberFormatException ignored) {
                /* keep default */
            }
        }

        List<String> raw = getParameters().getRaw();
        if (!raw.isEmpty()) {
            host = raw.get(0).trim();
        }
        if (raw.size() > 1) {
            try {
                port = Integer.parseInt(raw.get(1).trim());
            } catch (NumberFormatException ignored) {
                /* keep previous port */
            }
        }

        GCMClient.configureEndpoint(host, port);
        System.out.println("GCM client will use server " + host + ":" + port);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
