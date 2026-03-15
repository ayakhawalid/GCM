package server.dao;

import common.dto.ApprovalDTO;
import server.DBConnector;

import java.sql.*;

/**
 * Data Access Object for Approval records.
 * Tracks approval workflow for map versions and other entities.
 */
public class ApprovalDAO {

    public static final String ENTITY_MAP_VERSION = "MAP_VERSION";
    public static final String ENTITY_PRICING_REQUEST = "PRICING_REQUEST";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    /**
     * Create a new approval record with PENDING status.
     * 
     * @param conn       Database connection
     * @param entityType Type of entity (MAP_VERSION, PRICING_REQUEST)
     * @param entityId   ID of the entity
     * @return New approval ID, or -1 on failure
     */
    public static int createApproval(Connection conn, String entityType, int entityId)
            throws SQLException {
        String sql = "INSERT INTO approvals (entity_type, entity_id, status) VALUES (?, ?, 'PENDING')";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, entityType);
            stmt.setInt(2, entityId);

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
     * Update approval status.
     * 
     * @param conn       Database connection
     * @param entityType Entity type
     * @param entityId   Entity ID
     * @param status     New status
     * @param approvedBy User who approved/rejected
     * @param reason     Reason (for rejection)
     * @return true if updated
     */
    public static boolean updateApproval(Connection conn, String entityType, int entityId,
            String status, int approvedBy, String reason)
            throws SQLException {
        String sql = "UPDATE approvals SET status = ?, approved_by = ?, reason = ?, " +
                "updated_at = NOW() WHERE entity_type = ? AND entity_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, approvedBy);
            stmt.setString(3, reason);
            stmt.setString(4, entityType);
            stmt.setInt(5, entityId);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Get approval by entity type and ID.
     */
    public static ApprovalDTO getApproval(String entityType, int entityId) {
        String sql = "SELECT a.*, u.username as approved_by_username " +
                "FROM approvals a " +
                "LEFT JOIN users u ON a.approved_by = u.id " +
                "WHERE a.entity_type = ? AND a.entity_id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entityType);
            stmt.setInt(2, entityId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDTO(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting approval: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Map ResultSet to ApprovalDTO.
     */
    private static ApprovalDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        ApprovalDTO dto = new ApprovalDTO();
        dto.setId(rs.getInt("id"));
        dto.setEntityType(rs.getString("entity_type"));
        dto.setEntityId(rs.getInt("entity_id"));
        dto.setStatus(rs.getString("status"));
        dto.setReason(rs.getString("reason"));
        dto.setApprovedBy(rs.getObject("approved_by") != null ? rs.getInt("approved_by") : null);
        dto.setApprovedByUsername(rs.getString("approved_by_username"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        dto.setUpdatedAt(rs.getTimestamp("updated_at"));
        return dto;
    }
}
