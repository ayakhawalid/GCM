package server.dao;

import common.dto.CityPriceInfo;
import common.dto.PricingRequestDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for pricing requests.
 * Handles all database operations related to price changes.
 */
public class PricingDAO {

    /**
     * Get all cities with their current prices.
     * Used for ContentManager to view current pricing.
     */
    public static List<CityPriceInfo> getAllCurrentPrices() {
        List<CityPriceInfo> prices = new ArrayList<>();
        String sql = "SELECT id, name, price FROM cities ORDER BY name";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                prices.add(new CityPriceInfo(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        null // No subscription prices needed for this view
                ));
            }
        } catch (SQLException e) {
            System.err.println("PricingDAO.getAllCurrentPrices: " + e.getMessage());
        }
        return prices;
    }

    /**
     * Create a new pricing request.
     * 
     * @param cityId        City to change price for
     * @param proposedPrice New proposed price
     * @param reason        Justification for the change
     * @param creatorId     User creating the request
     * @return Created request ID, or -1 if failed
     */
    public static int createPricingRequest(int cityId, double proposedPrice,
            String reason, int creatorId) {
        // First get current price
        double currentPrice = getCurrentPrice(cityId);
        if (currentPrice < 0) {
            return -1; // City not found
        }

        String sql = """
                INSERT INTO pricing_requests
                (city_id, current_price, proposed_price, reason, created_by, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, cityId);
            stmt.setDouble(2, currentPrice);
            stmt.setDouble(3, proposedPrice);
            stmt.setString(4, reason);
            stmt.setInt(5, creatorId);

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("PricingDAO.createPricingRequest: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Get current price for a city.
     */
    private static double getCurrentPrice(int cityId) {
        String sql = "SELECT price FROM cities WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getDouble("price");
            }
        } catch (SQLException e) {
            System.err.println("PricingDAO.getCurrentPrice: " + e.getMessage());
        }
        return -1;
    }

    /**
     * List all pending pricing requests.
     * Used by CompanyManager to review requests.
     */
    public static List<PricingRequestDTO> listPendingRequests() {
        return listRequestsByStatus("PENDING");
    }

    /**
     * List pricing requests by status.
     */
    public static List<PricingRequestDTO> listRequestsByStatus(String status) {
        List<PricingRequestDTO> requests = new ArrayList<>();

        String sql = """
                SELECT pr.*, c.name as city_name,
                       u1.username as created_by_name,
                       u2.username as approved_by_name
                FROM pricing_requests pr
                JOIN cities c ON pr.city_id = c.id
                JOIN users u1 ON pr.created_by = u1.id
                LEFT JOIN users u2 ON pr.approved_by = u2.id
                WHERE pr.status = ?
                ORDER BY pr.created_at DESC
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                requests.add(mapResultSetToDTO(rs));
            }
        } catch (SQLException e) {
            System.err.println("PricingDAO.listRequestsByStatus: " + e.getMessage());
        }
        return requests;
    }

    /**
     * Get a specific pricing request by ID.
     */
    public static PricingRequestDTO getRequestById(int requestId) {
        String sql = """
                SELECT pr.*, c.name as city_name,
                       u1.username as created_by_name,
                       u2.username as approved_by_name
                FROM pricing_requests pr
                JOIN cities c ON pr.city_id = c.id
                JOIN users u1 ON pr.created_by = u1.id
                LEFT JOIN users u2 ON pr.approved_by = u2.id
                WHERE pr.id = ?
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToDTO(rs);
            }
        } catch (SQLException e) {
            System.err.println("PricingDAO.getRequestById: " + e.getMessage());
        }
        return null;
    }

    /**
     * Approve a pricing request and apply the new price.
     * 
     * @param conn       Database connection (for transaction)
     * @param requestId  Request to approve
     * @param approverId User approving
     * @return true if successful
     */
    public static boolean approveRequest(Connection conn, int requestId, int approverId)
            throws SQLException {
        // 1. Get the request details
        PricingRequestDTO request = getRequestById(requestId);
        if (request == null || !"PENDING".equals(request.getStatus())) {
            return false;
        }

        // 2. Update request status
        String updateRequestSql = """
                UPDATE pricing_requests
                SET status = 'APPROVED', approved_by = ?, processed_at = NOW()
                WHERE id = ? AND status = 'PENDING'
                """;

        try (PreparedStatement stmt = conn.prepareStatement(updateRequestSql)) {
            stmt.setInt(1, approverId);
            stmt.setInt(2, requestId);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                return false;
            }
        }

        // 3. Apply new price to city
        String updatePriceSql = "UPDATE cities SET price = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(updatePriceSql)) {
            stmt.setDouble(1, request.getProposedPrice());
            stmt.setInt(2, request.getCityId());
            stmt.executeUpdate();
        }

        return true;
    }

    /**
     * Reject a pricing request.
     * 
     * @param conn       Database connection (for transaction)
     * @param requestId  Request to reject
     * @param approverId User rejecting
     * @param reason     Reason for rejection
     * @return true if successful
     */
    public static boolean rejectRequest(Connection conn, int requestId,
            int approverId, String reason) throws SQLException {
        String sql = """
                UPDATE pricing_requests
                SET status = 'REJECTED', approved_by = ?, rejection_reason = ?,
                    processed_at = NOW()
                WHERE id = ? AND status = 'PENDING'
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, approverId);
            stmt.setString(2, reason);
            stmt.setInt(3, requestId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Check if there's already a pending request for a city.
     */
    public static boolean hasPendingRequest(int cityId) {
        String sql = "SELECT 1 FROM pricing_requests WHERE city_id = ? AND status = 'PENDING'";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, cityId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("PricingDAO.hasPendingRequest: " + e.getMessage());
        }
        return false;
    }

    /**
     * Create the pricing_requests table if it doesn't exist.
     */
    public static void ensureTableExists() {
        String sql = """
                CREATE TABLE IF NOT EXISTS pricing_requests (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    city_id INT NOT NULL,
                    current_price DOUBLE NOT NULL,
                    proposed_price DOUBLE NOT NULL,
                    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
                    reason VARCHAR(500),
                    rejection_reason VARCHAR(500),
                    created_by INT NOT NULL,
                    approved_by INT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    processed_at TIMESTAMP NULL,
                    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
                    INDEX idx_pricing_status (status),
                    INDEX idx_pricing_city (city_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        try (Connection conn = DBConnector.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ pricing_requests table ready");
        } catch (SQLException e) {
            System.err.println("PricingDAO.ensureTableExists: " + e.getMessage());
        }
    }

    /**
     * Map ResultSet row to PricingRequestDTO.
     */
    private static PricingRequestDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        return new PricingRequestDTO(
                rs.getInt("id"),
                rs.getInt("city_id"),
                rs.getString("city_name"),
                rs.getDouble("current_price"),
                rs.getDouble("proposed_price"),
                rs.getString("status"),
                rs.getString("reason"),
                rs.getString("rejection_reason"),
                rs.getInt("created_by"),
                rs.getString("created_by_name"),
                rs.getTimestamp("created_at"),
                rs.getObject("approved_by") != null ? rs.getInt("approved_by") : null,
                rs.getString("approved_by_name"),
                rs.getTimestamp("processed_at"));
    }
}
