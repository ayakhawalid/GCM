package server.dao;

import common.dto.NotificationDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Notification operations.
 * Creates and retrieves user notifications.
 */
public class NotificationDAO {

    /**
     * Create a notification for a user.
     * 
     * @param conn   Database connection
     * @param userId Target user ID
     * @param title  Notification title
     * @param body   Notification body
     * @return New notification ID, or -1 on failure
     */
    public static int createNotification(Connection conn, int userId, String title, String body)
            throws SQLException {
        String sql = "INSERT INTO notifications (user_id, channel, title, body) VALUES (?, 'IN_APP', ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setString(2, title);
            stmt.setString(3, body);

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
     * Get all notifications for a user.
     */
    public static List<NotificationDTO> getNotificationsForUser(int userId) {
        List<NotificationDTO> notifications = new ArrayList<>();
        String sql = "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                notifications.add(mapResultSetToDTO(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error getting notifications: " + e.getMessage());
            e.printStackTrace();
        }
        return notifications;
    }

    /**
     * Get unread notifications count for a user.
     */
    public static int getUnreadCount(int userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting unread count: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Mark notification as read.
     */
    public static boolean markAsRead(int notificationId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, notificationId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error marking notification as read: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get all customer user IDs who purchased a specific city.
     * Note: Requires purchases table (Phase 5). Returns empty list if not
     * available.
     */
    public static List<Integer> getCustomersWhoPurchasedCity(int cityId) {
        List<Integer> userIds = new ArrayList<>();

        // Check if purchases table exists
        String checkSql = "SELECT 1 FROM information_schema.tables WHERE table_name = 'purchases'";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                ResultSet checkRs = checkStmt.executeQuery()) {

            if (!checkRs.next()) {
                // Purchases table doesn't exist yet (Phase 5 not implemented)
                System.out.println("Note: purchases table not yet created. Customer notifications skipped.");
                return userIds;
            }

            // Get customers who purchased this city
            String sql = "SELECT DISTINCT c.user_id FROM purchases p " +
                    "JOIN customers c ON p.customer_id = c.id " +
                    "WHERE p.city_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, cityId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    userIds.add(rs.getInt("user_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting customers for city: " + e.getMessage());
        }
        return userIds;
    }

    /**
     * Notify all customers who purchased a city about a map update.
     * 
     * @param conn    Database connection
     * @param cityId  City ID
     * @param mapName Name of the updated map
     * @return Number of notifications created
     */
    public static int notifyCustomersAboutMapUpdate(Connection conn, int cityId, String mapName)
            throws SQLException {
        List<Integer> customerUserIds = getCustomersWhoPurchasedCity(cityId);
        int count = 0;

        String title = "Map Updated: " + mapName;
        String body = "A new version of the map '" + mapName + "' is now available. " +
                "Download the latest version to see the updates.";

        for (int userId : customerUserIds) {
            if (createNotification(conn, userId, title, body) > 0) {
                count++;
            }
        }

        return count;
    }

    /**
     * Map ResultSet to NotificationDTO.
     */
    private static NotificationDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(rs.getInt("id"));
        dto.setUserId(rs.getInt("user_id"));
        dto.setChannel(rs.getString("channel"));
        dto.setTitle(rs.getString("title"));
        dto.setBody(rs.getString("body"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        dto.setSentAt(rs.getTimestamp("sent_at"));
        dto.setRead(rs.getBoolean("is_read"));
        return dto;
    }
}
