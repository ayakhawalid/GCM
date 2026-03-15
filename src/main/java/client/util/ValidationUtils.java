package client.util;

import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Field validation utilities with inline error display and hints.
 * Phase 17: Usability - Field validation with inline hints.
 */
public class ValidationUtils {

    // Common validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9]{7,15}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");

    // CSS styles for validation states
    private static final String STYLE_VALID = "-fx-border-color: #4CAF50;";
    private static final String STYLE_INVALID = "-fx-border-color: #F44336;";
    private static final String STYLE_DEFAULT = "";

    /**
     * Validation result holder.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();

        public void addError(String fieldName, String message) {
            errors.add(fieldName + ": " + message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorString() {
            return String.join("\n", errors);
        }
    }

    // ==================== Field Validators ====================

    /**
     * Validate required field.
     */
    public static boolean validateRequired(TextField field, String fieldName, ValidationResult result) {
        String value = field.getText();
        if (value == null || value.trim().isEmpty()) {
            markInvalid(field, fieldName + " is required");
            result.addError(fieldName, "This field is required");
            return false;
        }
        markValid(field);
        return true;
    }

    /**
     * Validate email format.
     */
    public static boolean validateEmail(TextField field, String fieldName, ValidationResult result) {
        String value = field.getText();
        if (value != null && !value.isEmpty() && !EMAIL_PATTERN.matcher(value).matches()) {
            markInvalid(field, "Invalid email format");
            result.addError(fieldName, "Invalid email format (example: user@domain.com)");
            return false;
        }
        markValid(field);
        return true;
    }

    /**
     * Validate phone number format.
     */
    public static boolean validatePhone(TextField field, String fieldName, ValidationResult result) {
        String value = field.getText();
        if (value != null && !value.isEmpty()) {
            String cleaned = value.replaceAll("[\\s-()]", "");
            if (!PHONE_PATTERN.matcher(cleaned).matches()) {
                markInvalid(field, "Invalid phone format");
                result.addError(fieldName, "Invalid phone format (7-15 digits)");
                return false;
            }
        }
        markValid(field);
        return true;
    }

    /**
     * Validate username format.
     */
    public static boolean validateUsername(TextField field, String fieldName, ValidationResult result) {
        String value = field.getText();
        if (value != null && !value.isEmpty() && !USERNAME_PATTERN.matcher(value).matches()) {
            markInvalid(field, "Invalid username format");
            result.addError(fieldName, "Username must be 3-30 chars (letters, numbers, underscore)");
            return false;
        }
        markValid(field);
        return true;
    }

    /**
     * Validate minimum length.
     */
    public static boolean validateMinLength(TextField field, String fieldName, int minLength,
            ValidationResult result) {
        String value = field.getText();
        if (value == null || value.length() < minLength) {
            markInvalid(field, "Minimum " + minLength + " characters");
            result.addError(fieldName, "Must be at least " + minLength + " characters");
            return false;
        }
        markValid(field);
        return true;
    }

    /**
     * Validate maximum length.
     */
    public static boolean validateMaxLength(TextField field, String fieldName, int maxLength,
            ValidationResult result) {
        String value = field.getText();
        if (value != null && value.length() > maxLength) {
            markInvalid(field, "Maximum " + maxLength + " characters");
            result.addError(fieldName, "Must not exceed " + maxLength + " characters");
            return false;
        }
        markValid(field);
        return true;
    }

    /**
     * Validate positive number.
     */
    public static boolean validatePositiveNumber(TextField field, String fieldName,
            ValidationResult result) {
        String value = field.getText();
        try {
            double num = Double.parseDouble(value);
            if (num <= 0) {
                markInvalid(field, "Must be positive");
                result.addError(fieldName, "Must be a positive number");
                return false;
            }
            markValid(field);
            return true;
        } catch (NumberFormatException e) {
            markInvalid(field, "Invalid number");
            result.addError(fieldName, "Must be a valid number");
            return false;
        }
    }

    /**
     * Validate integer.
     */
    public static boolean validateInteger(TextField field, String fieldName, ValidationResult result) {
        String value = field.getText();
        try {
            Integer.parseInt(value);
            markValid(field);
            return true;
        } catch (NumberFormatException e) {
            markInvalid(field, "Invalid integer");
            result.addError(fieldName, "Must be a whole number");
            return false;
        }
    }

    /**
     * Validate matching fields (e.g., password confirmation).
     */
    public static boolean validateMatch(TextField field1, TextField field2, String fieldName,
            ValidationResult result) {
        String value1 = field1.getText();
        String value2 = field2.getText();
        if (value1 == null || !value1.equals(value2)) {
            markInvalid(field2, "Does not match");
            result.addError(fieldName, "Fields do not match");
            return false;
        }
        markValid(field2);
        return true;
    }

    // ==================== Visual Feedback ====================

    /**
     * Mark field as valid (green border).
     */
    public static void markValid(Control field) {
        field.setStyle(STYLE_VALID);
        field.setTooltip(null);
    }

    /**
     * Mark field as invalid (red border + tooltip).
     */
    public static void markInvalid(Control field, String message) {
        field.setStyle(STYLE_INVALID);
        Tooltip tooltip = new Tooltip(message);
        tooltip.setShowDelay(Duration.millis(100));
        tooltip.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");
        field.setTooltip(tooltip);
    }

    /**
     * Reset field to default style.
     */
    public static void resetStyle(Control field) {
        field.setStyle(STYLE_DEFAULT);
        field.setTooltip(null);
    }

    /**
     * Reset all fields to default style.
     */
    public static void resetAll(Control... fields) {
        for (Control field : fields) {
            resetStyle(field);
        }
    }

    // ==================== Tooltip Helpers ====================

    /**
     * Add a help tooltip to a control.
     */
    public static void addHelpTooltip(Control control, String helpText) {
        Tooltip tooltip = new Tooltip(helpText);
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.setStyle("-fx-font-size: 12px;");
        control.setTooltip(tooltip);
    }

    /**
     * Add a placeholder (prompt text) to a text field.
     */
    public static void setPlaceholder(TextField field, String placeholder) {
        field.setPromptText(placeholder);
    }

    /**
     * Add a placeholder to a text area.
     */
    public static void setPlaceholder(TextArea area, String placeholder) {
        area.setPromptText(placeholder);
    }

    // ==================== Character Counters ====================

    /**
     * Add a real-time character counter/limit to a text field.
     * Returns the remaining characters.
     */
    public static void addCharacterLimit(TextField field, int maxChars) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > maxChars) {
                field.setText(oldVal);
            }
        });
    }
}
