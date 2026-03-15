package client.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Unified dialog system for consistent error/success feedback.
 * Phase 17: Usability - Good UX with consistent dialogs.
 */
public class DialogUtils {

    private static final String DIALOG_CSS = "-fx-font-family: 'Segoe UI', Arial; -fx-font-size: 14px;";

    /**
     * Show an error dialog.
     */
    public static void showError(String title, String message) {
        showAlert(AlertType.ERROR, title, "Error", message);
    }

    /**
     * Show an error dialog with details.
     */
    public static void showError(String title, String message, String details) {
        runOnFxThread(() -> {
            Alert alert = createStyledAlert(AlertType.ERROR, title, "Error", message);

            if (details != null && !details.isEmpty()) {
                TextArea textArea = new TextArea(details);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);
                VBox.setVgrow(textArea, Priority.ALWAYS);

                alert.getDialogPane().setExpandableContent(textArea);
            }

            alert.showAndWait();
        });
    }

    /**
     * Show a success dialog.
     */
    public static void showSuccess(String title, String message) {
        showAlert(AlertType.INFORMATION, title, "Success", message);
    }

    /**
     * Show a warning dialog.
     */
    public static void showWarning(String title, String message) {
        showAlert(AlertType.WARNING, title, "Warning", message);
    }

    /**
     * Show an info dialog.
     */
    public static void showInfo(String title, String message) {
        showAlert(AlertType.INFORMATION, title, null, message);
    }

    /**
     * Show a confirmation dialog.
     * Returns true if user clicked OK/Yes.
     */
    public static boolean showConfirm(String title, String message) {
        Alert alert = createStyledAlert(AlertType.CONFIRMATION, title, "Confirm", message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Show a confirmation dialog with custom buttons.
     */
    public static Optional<ButtonType> showConfirm(String title, String message,
            ButtonType... buttons) {
        Alert alert = createStyledAlert(AlertType.CONFIRMATION, title, null, message);
        alert.getButtonTypes().setAll(buttons);
        return alert.showAndWait();
    }

    /**
     * Generic alert display.
     */
    private static void showAlert(AlertType type, String title, String header, String message) {
        runOnFxThread(() -> {
            Alert alert = createStyledAlert(type, title, header, message);
            alert.showAndWait();
        });
    }

    /**
     * Create a styled alert dialog.
     */
    private static Alert createStyledAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Apply styling
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(DIALOG_CSS);

        // Add icon based on type
        String icon = switch (type) {
            case ERROR -> "❌ ";
            case WARNING -> "⚠️ ";
            case INFORMATION -> "ℹ️ ";
            case CONFIRMATION -> "❓ ";
            default -> "";
        };

        if (header != null) {
            alert.setHeaderText(icon + header);
        }

        // Make resizable for long content
        alert.setResizable(true);

        return alert;
    }

    /**
     * Run code on JavaFX Application Thread.
     * Phase 16: Threading - UI updates on UI thread.
     */
    public static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Show a network error dialog.
     */
    public static void showNetworkError() {
        showError("Connection Error",
                "Unable to connect to server. Please check your connection and try again.");
    }

    /**
     * Show a validation error dialog.
     */
    public static void showValidationError(String fieldErrors) {
        showError("Validation Error",
                "Please correct the following errors:\n\n" + fieldErrors);
    }

    /**
     * Show a login required dialog.
     */
    public static void showLoginRequired() {
        showWarning("Login Required",
                "You must be logged in to perform this action.");
    }

    /**
     * Show an access denied dialog.
     */
    public static void showAccessDenied() {
        showError("Access Denied",
                "You don't have permission to perform this action.");
    }

    /**
     * Show operation in progress info (non-blocking).
     */
    public static void showProgress(String message) {
        runOnFxThread(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Please Wait");
            alert.setHeaderText(null);
            alert.setContentText("⏳ " + message);
            alert.getButtonTypes().clear();
            alert.show();
        });
    }
}
