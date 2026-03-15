package client;

/**
 * Main launcher for the GCM Client application.
 * 
 * This class serves as the entry point for the executable JAR.
 * 
 * IMPORTANT: For JavaFX applications to work from a JAR file,
 * the main class must NOT extend Application directly.
 * This launcher class calls the actual JavaFX Application class.
 */
public class Launcher {

    /**
     * Main method - Entry point for the JAR file.
     * 
     * @param args Command line arguments (optional: server IP address)
     */
    public static void main(String[] args) {
        // Launch the JavaFX application
        // This indirect call is required for JAR execution
        LoginApp.main(args);
    }
}