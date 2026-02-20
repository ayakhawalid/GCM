package server.dao;

import common.dto.CityPriceInfo;
import common.dto.EntitlementInfo;
import server.DBConnector;

import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Access Object for purchases and subscriptions.
 */
public class PurchaseDAO {

    /**
     * Get pricing info for a city.
     */
    public static CityPriceInfo getCityPrice(int cityId) {
        String query = "SELECT id, name, price FROM cities WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                double price = rs.getDouble("price");

                // Calculate subscription prices (mock calculation based on base price)
                // 1 Month: 1.2 x Base
                // 3 Months: 3.2 x Base (discounted)
                // 6 Months: 6.0 x Base (discounted)
                Map<Integer, Double> subPrices = new HashMap<>();
                subPrices.put(1, price * 1.2);
                subPrices.put(2, price * 2.4);
                subPrices.put(3, price * 3.2);
                subPrices.put(4, price * 4.4);
                subPrices.put(5, price * 5.5);
                subPrices.put(6, price * 6.0);

                return new CityPriceInfo(cityId, name, price, subPrices);
            }
        } catch (SQLException e) {
            System.err.println("Error getting city price: " + e.getMessage());
        }
        return null;
    }

    /**
     * Record a one-time purchase.
     */
    public static boolean purchaseOneTime(int userId, int cityId) {
        // First check price
        CityPriceInfo priceInfo = getCityPrice(cityId);
        if (priceInfo == null)
            return false;

        // Get current approved version of the city map (if any)
        // For simplicity in Phase 5, we'll just store NULL if no specific version logic
        // yet,
        // or we could fetch the latest approved version from map_versions.
        // Let's keep it simple for now.

        String query = "INSERT INTO purchases (user_id, city_id, price_paid) VALUES (?, ?, ?)";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            stmt.setDouble(3, priceInfo.getOneTimePrice());

            int affected = stmt.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            System.err.println("Error recording purchase: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if user has any previous subscription for this city.
     */
    public static boolean hasPreviousSubscription(int userId, int cityId) {
        String sql = "SELECT 1 FROM subscriptions WHERE user_id = ? AND city_id = ? LIMIT 1";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking previous subscription: " + e.getMessage());
            return false;
        }
    }

    /**
     * Record a subscription purchase.
     */
    public static boolean purchaseSubscription(int userId, int cityId, int months) {
        if (months < 1 || months > 6)
            return false;

        CityPriceInfo priceInfo = getCityPrice(cityId);
        if (priceInfo == null)
            return false;

        Double price = priceInfo.getSubscriptionPrices().get(months);
        if (price == null)
            return false; // Should not happen given logic above

        String query = "INSERT INTO subscriptions (user_id, city_id, months, price_paid, end_date) " +
                "VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? MONTH))";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            stmt.setInt(3, months);
            stmt.setDouble(4, price);
            stmt.setInt(5, months);

            int affected = stmt.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            System.err.println("Error recording subscription: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get user's entitlement for a city.
     * Checks both active subscriptions and past one-time purchases.
     */
    public static EntitlementInfo getEntitlement(int userId, int cityId) {
        // 1. Check active subscription
        String subQuery = "SELECT end_date FROM subscriptions " +
                "WHERE user_id = ? AND city_id = ? AND is_active = TRUE " +
                "AND end_date > NOW() ORDER BY end_date DESC LIMIT 1";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(subQuery)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                LocalDate expiryDate = rs.getTimestamp("end_date").toLocalDateTime().toLocalDate();
                return new EntitlementInfo(cityId, EntitlementInfo.EntitlementType.SUBSCRIPTION,
                        expiryDate, true, true);
            }
        } catch (SQLException e) {
            System.err.println("Error checking subscription: " + e.getMessage());
        }

        // 2. Check one-time purchase
        String purchaseQuery = "SELECT purchased_at FROM purchases " +
                "WHERE user_id = ? AND city_id = ? LIMIT 1";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(purchaseQuery)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // One-time: allow one download per purchase for this city
                int purchaseCount = getOneTimePurchaseCount(userId, cityId);
                int downloadCount = getDownloadCount(userId, cityId);
                boolean canDownload = downloadCount < purchaseCount;
                return new EntitlementInfo(cityId, EntitlementInfo.EntitlementType.ONE_TIME,
                        null, false, canDownload);
            }
        } catch (SQLException e) {
            System.err.println("Error checking purchase: " + e.getMessage());
        }

        return new EntitlementInfo(cityId, EntitlementInfo.EntitlementType.NONE, null, false, false);
    }

    /**
     * Returns how many one-time purchases the user has for this city.
     * Each one-time purchase entitles the user to one download.
     */
    public static int getOneTimePurchaseCount(int userId, int cityId) {
        String query = "SELECT COUNT(*) FROM purchases WHERE user_id = ? AND city_id = ?";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Error getting one-time purchase count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns how many times the user has recorded a download for this city
     * (used for one-time entitlement: we only record when downloading via one-time).
     */
    public static int getDownloadCount(int userId, int cityId) {
        String query = "SELECT COUNT(*) FROM download_events WHERE user_id = ? AND city_id = ?";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Error getting download count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Record a download event.
     */
    public static void recordDownload(int userId, int cityId) {
        String query = "INSERT INTO download_events (user_id, city_id) VALUES (?, ?)";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error recording download: " + e.getMessage());
        }
    }

    /**
     * Record a view event.
     */
    public static void recordView(int userId, int cityId, int mapId) {
        String query = "INSERT INTO view_events (user_id, city_id, map_id) VALUES (?, ?, ?)";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            stmt.setInt(3, mapId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error recording view: " + e.getMessage());
        }
    }

    /**
     * Get all purchases and subscriptions for a user.
     */
    public static java.util.List<EntitlementInfo> getUserPurchases(int userId) {
        java.util.List<EntitlementInfo> purchases = new java.util.ArrayList<>();

        // Get subscriptions (subscriptions table has start_date, not created_at)
        String subQuery = """
                SELECT s.city_id, c.name, s.end_date
                FROM subscriptions s
                JOIN cities c ON s.city_id = c.id
                WHERE s.user_id = ?
                ORDER BY s.start_date DESC
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(subQuery)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                LocalDate expiryDate = rs.getTimestamp("end_date") != null
                        ? rs.getTimestamp("end_date").toLocalDateTime().toLocalDate()
                        : null;
                boolean isActive = expiryDate != null && !expiryDate.isBefore(LocalDate.now());

                purchases.add(new EntitlementInfo(
                        rs.getInt("city_id"),
                        rs.getString("name"),
                        EntitlementInfo.EntitlementType.SUBSCRIPTION,
                        expiryDate,
                        isActive,
                        isActive));
            }
        } catch (SQLException e) {
            System.err.println("PurchaseDAO.getUserPurchases (subscriptions): " + e.getMessage());
        }

        // Get one-time purchases
        String purchaseQuery = """
                SELECT p.city_id, c.name, p.purchased_at
                FROM purchases p
                JOIN cities c ON p.city_id = c.id
                WHERE p.user_id = ?
                ORDER BY p.purchased_at DESC
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(purchaseQuery)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int cid = rs.getInt("city_id");
                // One-time: one download per purchase for this city
                int purchaseCount = getOneTimePurchaseCount(userId, cid);
                boolean canDownload = getDownloadCount(userId, cid) < purchaseCount;
                purchases.add(new EntitlementInfo(
                        cid,
                        rs.getString("name"),
                        EntitlementInfo.EntitlementType.ONE_TIME,
                        null, // No expiry for one-time
                        true, // Can view
                        canDownload
                ));
            }
        } catch (SQLException e) {
            System.err.println("PurchaseDAO.getUserPurchases (purchases): " + e.getMessage());
        }

        return purchases;
    }

    // ==================== Phase 6: Detailed Purchase Methods ====================

    /**
     * Get detailed purchases for a user (for profile view).
     *
     * @param userId User ID
     * @return List of CustomerPurchaseDTO
     */
    public static java.util.List<common.dto.CustomerPurchaseDTO> getPurchasesDetailed(int userId) {
        java.util.List<common.dto.CustomerPurchaseDTO> purchases = new java.util.ArrayList<>();

        // Get subscriptions (table has start_date, not created_at)
        String subQuery = """
                SELECT s.id, s.city_id, c.name, s.price_paid, s.start_date,
                       s.months, s.end_date, s.is_active
                FROM subscriptions s
                JOIN cities c ON s.city_id = c.id
                WHERE s.user_id = ?
                ORDER BY s.start_date DESC
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(subQuery)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                LocalDate startDate = rs.getTimestamp("start_date") != null
                        ? rs.getTimestamp("start_date").toLocalDateTime().toLocalDate()
                        : null;
                LocalDate expiryDate = rs.getTimestamp("end_date") != null
                        ? rs.getTimestamp("end_date").toLocalDateTime().toLocalDate()
                        : null;
                boolean isActive = rs.getBoolean("is_active") &&
                        (expiryDate == null || !expiryDate.isBefore(LocalDate.now()));

                purchases.add(common.dto.CustomerPurchaseDTO.subscription(
                        rs.getInt("id"),
                        rs.getInt("city_id"),
                        rs.getString("name"),
                        rs.getDouble("price_paid"),
                        rs.getTimestamp("start_date"),
                        rs.getInt("months"),
                        startDate,
                        expiryDate,
                        isActive));
            }
        } catch (SQLException e) {
            System.err.println("PurchaseDAO.getPurchasesDetailed (subscriptions): " + e.getMessage());
        }

        // Get one-time purchases
        String purchaseQuery = """
                SELECT p.id, p.city_id, c.name, p.price_paid, p.purchased_at
                FROM purchases p
                JOIN cities c ON p.city_id = c.id
                WHERE p.user_id = ?
                ORDER BY p.purchased_at DESC
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(purchaseQuery)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                purchases.add(common.dto.CustomerPurchaseDTO.oneTime(
                        rs.getInt("id"),
                        rs.getInt("city_id"),
                        rs.getString("name"),
                        rs.getDouble("price_paid"),
                        rs.getTimestamp("purchased_at")));
            }
        } catch (SQLException e) {
            System.err.println("PurchaseDAO.getPurchasesDetailed (purchases): " + e.getMessage());
        }

        return purchases;
    }

    /**
     * Get all user IDs who have purchased or have an active subscription for a
     * city.
     * Used for sending map update notifications.
     *
     * @param cityId City ID
     * @return List of user IDs
     */
    public static java.util.List<Integer> getCustomerIdsForCity(int cityId) {
        java.util.Set<Integer> userIds = new java.util.HashSet<>();

        // Get users with one-time purchases
        String purchaseQuery = "SELECT DISTINCT user_id FROM purchases WHERE city_id = ?";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(purchaseQuery)) {
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                userIds.add(rs.getInt("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting purchase customers: " + e.getMessage());
        }

        // Get users with active subscriptions
        String subQuery = "SELECT DISTINCT user_id FROM subscriptions WHERE city_id = ? AND is_active = TRUE AND end_date > NOW()";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(subQuery)) {
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                userIds.add(rs.getInt("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting subscription customers: " + e.getMessage());
        }

        return new java.util.ArrayList<>(userIds);
    }

    // ==================== Phase 7: Subscription Expiry Methods
    // ====================

    /**
     * DTO for expiring subscription info.
     */
    public static class ExpiringSubscription {
        public final int subscriptionId;
        public final int userId;
        public final String username;
        public final String email;
        public final String phone;
        public final int cityId;
        public final String cityName;
        public final LocalDate expiryDate;
        public final int daysUntilExpiry;

        public ExpiringSubscription(int subscriptionId, int userId, String username,
                String email, String phone, int cityId, String cityName,
                LocalDate expiryDate, int daysUntilExpiry) {
            this.subscriptionId = subscriptionId;
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.cityId = cityId;
            this.cityName = cityName;
            this.expiryDate = expiryDate;
            this.daysUntilExpiry = daysUntilExpiry;
        }
    }

    /**
     * Get subscriptions expiring within N days.
     *
     * @param daysUntilExpiry Number of days until expiry
     * @return List of expiring subscriptions
     */
    public static java.util.List<ExpiringSubscription> getExpiringSubscriptions(int daysUntilExpiry) {
        java.util.List<ExpiringSubscription> expiring = new java.util.ArrayList<>();

        String sql = """
                SELECT s.id as sub_id, s.user_id, u.username, u.email, u.phone,
                       s.city_id, c.name as city_name, s.end_date,
                       DATEDIFF(s.end_date, CURDATE()) as days_remaining
                FROM subscriptions s
                JOIN users u ON s.user_id = u.id
                JOIN cities c ON s.city_id = c.id
                WHERE s.is_active = TRUE
                AND s.end_date > CURDATE()
                AND DATEDIFF(s.end_date, CURDATE()) <= ?
                ORDER BY s.end_date ASC
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, daysUntilExpiry);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                LocalDate expiryDate = rs.getTimestamp("end_date") != null
                        ? rs.getTimestamp("end_date").toLocalDateTime().toLocalDate()
                        : null;

                expiring.add(new ExpiringSubscription(
                        rs.getInt("sub_id"),
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getInt("city_id"),
                        rs.getString("city_name"),
                        expiryDate,
                        rs.getInt("days_remaining")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting expiring subscriptions: " + e.getMessage());
        }

        return expiring;
    }

    /**
     * Check if reminder has been sent for a subscription.
     *
     * @param subscriptionId Subscription ID
     * @param reminderType   Reminder type (3_DAYS, 1_DAY, EXPIRED)
     * @return true if reminder already sent
     */
    public static boolean hasReminderBeenSent(int subscriptionId, String reminderType) {
        String sql = "SELECT 1 FROM subscription_reminders WHERE subscription_id = ? AND reminder_type = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);
            stmt.setString(2, reminderType);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            // Table might not exist yet
            if (e.getMessage().contains("doesn't exist")) {
                createReminderTable();
                return false;
            }
            System.err.println("Error checking reminder: " + e.getMessage());
            return false;
        }
    }

    /**
     * Record that a reminder has been sent.
     *
     * @param subscriptionId Subscription ID
     * @param reminderType   Reminder type
     * @return true if recorded successfully
     */
    public static boolean recordReminderSent(int subscriptionId, String reminderType) {
        String sql = "INSERT INTO subscription_reminders (subscription_id, reminder_type) VALUES (?, ?)";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, subscriptionId);
            stmt.setString(2, reminderType);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate")) {
                return true; // Already recorded
            }
            System.err.println("Error recording reminder: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create reminder table if it doesn't exist.
     */
    private static void createReminderTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS subscription_reminders (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    subscription_id INT NOT NULL,
                    reminder_type VARCHAR(20) NOT NULL,
                    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_reminder (subscription_id, reminder_type)
                )
                """;

        try (Connection conn = DBConnector.getConnection();
                java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Created subscription_reminders table");
        } catch (SQLException e) {
            System.err.println("Error creating reminder table: " + e.getMessage());
        }
    }
}
