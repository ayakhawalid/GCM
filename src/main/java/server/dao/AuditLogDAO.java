package server.dao;

import java.sql.*;

/**
 * Data Access Object for Audit Log entries.
 * Records all significant actions for tracking and compliance.
 */
public class AuditLogDAO {

    // Action constants
    public static final String ACTION_MAP_UPDATED = "MAP_UPDATED";
    public static final String ACTION_VERSION_CREATED = "VERSION_CREATED";
    public static final String ACTION_VERSION_PUBLISHED = "VERSION_PUBLISHED";
    public static final String ACTION_VERSION_APPROVED = "VERSION_APPROVED";
    public static final String ACTION_VERSION_REJECTED = "VERSION_REJECTED";
    public static final String ACTION_CITY_CREATED = "CITY_CREATED";
    public static final String ACTION_POI_CREATED = "POI_CREATED";
    public static final String ACTION_TOUR_CREATED = "TOUR_CREATED";

    // Entity type constants
    public static final String ENTITY_MAP_VERSION = "MAP_VERSION";
    public static final String ENTITY_MAP = "MAP";
    public static final String ENTITY_CITY = "CITY";
    public static final String ENTITY_POI = "POI";
    public static final String ENTITY_TOUR = "TOUR";

    /**
     * Log an action to the audit log.
     * 
     * @param conn        Database connection
     * @param action      Action performed
     * @param actorId     User who performed the action
     * @param entityType  Type of entity affected
     * @param entityId    ID of affected entity
     * @param detailsJson JSON string with additional details
     */
    public static void log(Connection conn, String action, int actorId,
            String entityType, int entityId, String detailsJson)
            throws SQLException {
        String sql = "INSERT INTO audit_log (action, actor, entity_type, entity_id, details_json) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, action);
            stmt.setInt(2, actorId);
            stmt.setString(3, entityType);
            stmt.setInt(4, entityId);
            stmt.setString(5, detailsJson);
            stmt.executeUpdate();
        }
    }

    /**
     * Log an action without a database connection (creates its own).
     * Use this for simple logging outside of transactions.
     */
    public static void logSimple(String action, int actorId, String entityType,
            int entityId, String detailsJson) {
        String sql = "INSERT INTO audit_log (action, actor, entity_type, entity_id, details_json) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = server.DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, action);
            stmt.setInt(2, actorId);
            stmt.setString(3, entityType);
            stmt.setInt(4, entityId);
            stmt.setString(5, detailsJson);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error logging audit entry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Convenience method to log with JSON details as key-value pairs.
     */
    public static void log(Connection conn, String action, int actorId,
            String entityType, int entityId, String... keyValues)
            throws SQLException {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            if (i > 0)
                json.append(", ");
            json.append("\"").append(keyValues[i]).append("\": \"")
                    .append(keyValues[i + 1]).append("\"");
        }
        json.append("}");
        log(conn, action, actorId, entityType, entityId, json.toString());
    }
}
