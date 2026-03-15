package common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO containing validation results.
 * Used to return validation errors from server.
 */
public class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean valid;
    private List<ValidationError> errors;
    private String successMessage;

    // Result data (IDs of created entities)
    private Integer createdCityId;
    private Integer createdMapId;
    private Integer createdVersionId; // Phase 3: map version for approval
    private List<Integer> createdPoiIds;
    private List<Integer> createdTourIds;

    public ValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.createdPoiIds = new ArrayList<>();
        this.createdTourIds = new ArrayList<>();
    }

    /**
     * Create a successful result.
     */
    public static ValidationResult success(String message) {
        ValidationResult result = new ValidationResult();
        result.valid = true;
        result.successMessage = message;
        return result;
    }

    /**
     * Create an error result with a single error.
     */
    public static ValidationResult error(String field, String message) {
        ValidationResult result = new ValidationResult();
        result.valid = false;
        result.addError(field, message);
        return result;
    }

    /**
     * Add a validation error.
     */
    public ValidationResult addError(String field, String message) {
        this.valid = false;
        this.errors.add(new ValidationError(field, message));
        return this;
    }

    /**
     * Add a general error (not field-specific).
     */
    public ValidationResult addGeneralError(String message) {
        return addError("_general", message);
    }

    /**
     * Validation error details.
     */
    public static class ValidationError implements Serializable {
        private static final long serialVersionUID = 1L;

        private String field;
        private String message;

        public ValidationError() {
        }

        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return field + ": " + message;
        }
    }

    // Getters and Setters
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public void setSuccessMessage(String successMessage) {
        this.successMessage = successMessage;
    }

    public Integer getCreatedCityId() {
        return createdCityId;
    }

    public void setCreatedCityId(Integer createdCityId) {
        this.createdCityId = createdCityId;
    }

    public Integer getCreatedMapId() {
        return createdMapId;
    }

    public void setCreatedMapId(Integer createdMapId) {
        this.createdMapId = createdMapId;
    }

    public Integer getCreatedVersionId() {
        return createdVersionId;
    }

    public void setCreatedVersionId(Integer createdVersionId) {
        this.createdVersionId = createdVersionId;
    }

    public List<Integer> getCreatedPoiIds() {
        return createdPoiIds;
    }

    public void setCreatedPoiIds(List<Integer> createdPoiIds) {
        this.createdPoiIds = createdPoiIds;
    }

    public List<Integer> getCreatedTourIds() {
        return createdTourIds;
    }

    public void setCreatedTourIds(List<Integer> createdTourIds) {
        this.createdTourIds = createdTourIds;
    }

    /**
     * Get all error messages as a single string.
     */
    public String getErrorSummary() {
        if (errors.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (ValidationError error : errors) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("• ").append(error.getMessage());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{VALID, " + successMessage + "}";
        } else {
            return "ValidationResult{INVALID, " + errors.size() + " errors}";
        }
    }
}
