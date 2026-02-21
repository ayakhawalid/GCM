package server.dao;

import common.dto.MapVersionDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for MapVersion operations.
 * Handles version creation, status updates, and queries.
 */
public class MapVersionDAO {

    /**
     * Create a new map version with PENDING status.
     */
    public static int createVersion(Connection conn, int mapId, int createdBy, String descriptionText)
            throws SQLException {
        return createVersion(conn, mapId, createdBy, descriptionText, "PENDING");
    }

    /**
     * Create a new map version with the given initial status (e.g. PENDING, DRAFT).
     *
     * @param conn            Database connection (for transaction support)
     * @param mapId           Map ID
     * @param createdBy       User ID of creator
     * @param descriptionText Description of changes
     * @param initialStatus   Status to set (e.g. PENDING, DRAFT)
     * @return New version ID, or -1 on failure
     */
    public static int createVersion(Connection conn, int mapId, int createdBy, String descriptionText, String initialStatus)
            throws SQLException {
        // Get next version number for this map
        int nextVersion = getNextVersionNumber(conn, mapId);
        String status = (initialStatus != null && !initialStatus.isEmpty()) ? initialStatus : "PENDING";

        String sql = "INSERT INTO map_versions (map_id, version_number, status, description_text, created_by) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, mapId);
            stmt.setInt(2, nextVersion);
            stmt.setString(3, status);
            stmt.setString(4, descriptionText);
            stmt.setInt(5, createdBy);

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Get the next version number for a map.
     */
    private static int getNextVersionNumber(Connection conn, int mapId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(version_number), 0) + 1 FROM map_versions WHERE map_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1;
    }

    /**
     * Get a version by ID with all related info.
     */
    public static MapVersionDTO getVersionById(int versionId) {
        String sql = "SELECT mv.*, m.name as map_name, c.id as city_id, c.name as city_name, " +
                "u1.username as created_by_username, u2.username as approved_by_username " +
                "FROM map_versions mv " +
                "JOIN maps m ON mv.map_id = m.id " +
                "JOIN cities c ON m.city_id = c.id " +
                "LEFT JOIN users u1 ON mv.created_by = u1.id " +
                "LEFT JOIN users u2 ON mv.approved_by = u2.id " +
                "WHERE mv.id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, versionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDTO(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting version by ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * List all PENDING versions for approval.
     */
    public static List<MapVersionDTO> listPendingVersions() {
        List<MapVersionDTO> versions = new ArrayList<>();
        String sql = "SELECT mv.*, m.name as map_name, c.id as city_id, c.name as city_name, " +
                "u1.username as created_by_username, u2.username as approved_by_username " +
                "FROM map_versions mv " +
                "JOIN maps m ON mv.map_id = m.id " +
                "JOIN cities c ON m.city_id = c.id " +
                "LEFT JOIN users u1 ON mv.created_by = u1.id " +
                "LEFT JOIN users u2 ON mv.approved_by = u2.id " +
                "WHERE mv.status = 'PENDING' " +
                "ORDER BY mv.created_at DESC";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                versions.add(mapResultSetToDTO(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error listing pending versions: " + e.getMessage());
            e.printStackTrace();
        }
        return versions;
    }

    /**
     * Update version status (APPROVED or REJECTED).
     * 
     * @param conn       Database connection
     * @param versionId  Version ID
     * @param status     New status (APPROVED or REJECTED)
     * @param approvedBy User ID of approver
     * @param reason     Rejection reason (null for approval)
     * @return true if updated
     */
    public static boolean updateStatus(Connection conn, int versionId, String status,
            int approvedBy, String reason) throws SQLException {
        String sql = "UPDATE map_versions SET status = ?, approved_by = ?, approved_at = NOW(), " +
                "rejection_reason = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, approvedBy);
            stmt.setString(3, reason);
            stmt.setInt(4, versionId);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Get the latest APPROVED version for a map (what customers see).
     */
    public static MapVersionDTO getLatestApprovedVersion(int mapId) {
        String sql = "SELECT mv.*, m.name as map_name, c.id as city_id, c.name as city_name, " +
                "u1.username as created_by_username, u2.username as approved_by_username " +
                "FROM map_versions mv " +
                "JOIN maps m ON mv.map_id = m.id " +
                "JOIN cities c ON m.city_id = c.id " +
                "LEFT JOIN users u1 ON mv.created_by = u1.id " +
                "LEFT JOIN users u2 ON mv.approved_by = u2.id " +
                "WHERE mv.map_id = ? AND mv.status = 'APPROVED' " +
                "ORDER BY mv.version_number DESC LIMIT 1";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDTO(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting latest approved version: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get city ID for a version (used for notifications).
     */
    public static int getCityIdForVersion(int versionId) {
        String sql = "SELECT c.id FROM map_versions mv " +
                "JOIN maps m ON mv.map_id = m.id " +
                "JOIN cities c ON m.city_id = c.id " +
                "WHERE mv.id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, versionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting city ID for version: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Map ResultSet row to MapVersionDTO.
     */
    private static MapVersionDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        MapVersionDTO dto = new MapVersionDTO();
        dto.setId(rs.getInt("id"));
        dto.setMapId(rs.getInt("map_id"));
        dto.setMapName(rs.getString("map_name"));
        dto.setCityId(rs.getInt("city_id"));
        dto.setCityName(rs.getString("city_name"));
        dto.setVersionNumber(rs.getInt("version_number"));
        dto.setStatus(rs.getString("status"));
        dto.setDescriptionText(rs.getString("description_text"));
        dto.setCreatedBy(rs.getInt("created_by"));
        dto.setCreatedByUsername(rs.getString("created_by_username"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        dto.setApprovedBy(rs.getObject("approved_by") != null ? rs.getInt("approved_by") : null);
        dto.setApprovedByUsername(rs.getString("approved_by_username"));
        dto.setApprovedAt(rs.getTimestamp("approved_at"));
        dto.setRejectionReason(rs.getString("rejection_reason"));
        return dto;
    }
}
