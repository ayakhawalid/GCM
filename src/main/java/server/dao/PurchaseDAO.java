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

                // Calculate subscription prices (Dynamic pricing based on one-time price)
                // 1 Month: 0.80 x Base
                // 3 Months: 2.10 x Base
                // 6 Months: 3.60 x Base
                Map<Integer, Double> subPrices = new HashMap<>();
                subPrices.put(1, price * 0.80);
                subPrices.put(3, price * 2.10);
                subPrices.put(6, price * 3.60);

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
     * True if the user has any active (not expired) subscription row for this city.
     * Used for analytics: a follow-up subscription purchase while still subscribed counts as a renewal.
     */
    public static boolean hasActiveSubscriptionForCity(int userId, int cityId) {
        String sql = "SELECT 1 FROM subscriptions WHERE user_id = ? AND city_id = ? AND end_date > CURDATE() LIMIT 1";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking active subscription for city: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if user has an active subscription for this city and duration that
     * expires within 3 days.
     */
    public static boolean hasActiveExpiringSubscription(int userId, int cityId, int months) {
        String sql = "SELECT 1 FROM subscriptions " +
                "WHERE user_id = ? AND city_id = ? AND months = ? " +
                "AND end_date > CURDATE() AND DATEDIFF(end_date, CURDATE()) <= 3 LIMIT 1";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            stmt.setInt(3, months);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking active expiring subscription: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if user has an active subscription for this city and exact duration.
     * Used to decide whether to show "Renew subscription..." in the catalog.
     */
    public static boolean hasActiveSubscriptionForDuration(int userId, int cityId, int months) {
        String sql = "SELECT 1 FROM subscriptions " +
                "WHERE user_id = ? AND city_id = ? AND months = ? " +
                "AND end_date > CURDATE() LIMIT 1";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            stmt.setInt(3, months);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking active subscription duration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Record a subscription purchase.
     * If user already has an active subscription for this city, extend its end_date
     * by the new months instead of creating a new row.
     */
    public static boolean purchaseSubscription(int userId, int cityId, int months) {
        if (months != 1 && months != 3 && months != 6) {
            return false;
        }

        CityPriceInfo priceInfo = getCityPrice(cityId);
        if (priceInfo == null)
            return false;

        Double price = priceInfo.getSubscriptionPrices().get(months);
        if (price == null)
            return false; // Should not happen given logic above

        try (Connection conn = DBConnector.getConnection()) {
            // If user has an active subscription for this city, extend it
            String findActive = "SELECT id, end_date FROM subscriptions " +
                    "WHERE user_id = ? AND city_id = ? AND end_date > CURDATE() ORDER BY end_date DESC LIMIT 1";
            try (PreparedStatement findStmt = conn.prepareStatement(findActive)) {
                findStmt.setInt(1, userId);
                findStmt.setInt(2, cityId);
                ResultSet rs = findStmt.executeQuery();
                if (rs.next()) {
                    int subId = rs.getInt("id");
                    // Apply 10% discount when renewing same city+duration (active subscription for that duration)
                    if (hasActiveSubscriptionForDuration(userId, cityId, months)) {
                        price = price * 0.90;
                    }
                    String extend = "UPDATE subscriptions SET end_date = DATE_ADD(end_date, INTERVAL ? MONTH), " +
                            "price_paid = price_paid + ?, months = ?, is_active = TRUE WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(extend)) {
                        updateStmt.setInt(1, months);
                        updateStmt.setDouble(2, price);
                        updateStmt.setInt(3, months);
                        updateStmt.setInt(4, subId);
                        int affected = updateStmt.executeUpdate();
                        return affected > 0;
                    }
                }
            }

            // No active subscription: insert new row
            if (hasActiveSubscriptionForDuration(userId, cityId, months)) {
                price = price * 0.90;
            }
            String query = "INSERT INTO subscriptions (user_id, city_id, months, price_paid, start_date, end_date, is_active) " +
                    "VALUES (?, ?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? MONTH), TRUE)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, cityId);
                stmt.setInt(3, months);
                stmt.setDouble(4, price);
                stmt.setInt(5, months);
                return stmt.executeUpdate() > 0;
            }
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
        // 1. Check active subscription (even if cancelled auto-renew, user keeps access
        // until end_date)
        String subQuery = "SELECT end_date FROM subscriptions " +
                "WHERE user_id = ? AND city_id = ? " +
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
     * (used for one-time entitlement: we only record when downloading via
     * one-time).
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

        // Get subscriptions: one row per city with latest end_date (combined expiry)
        String subQuery = """
                SELECT s.city_id, c.name, MAX(s.end_date) AS end_date,
                       SUM(s.price_paid) AS price_paid, MIN(s.start_date) AS start_date
                FROM subscriptions s
                JOIN cities c ON s.city_id = c.id
                WHERE s.user_id = ?
                GROUP BY s.city_id, c.name
                HAVING MAX(s.end_date) > CURDATE()
                ORDER BY end_date DESC
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

                EntitlementInfo info = new EntitlementInfo(
                        rs.getInt("city_id"),
                        rs.getString("name"),
                        EntitlementInfo.EntitlementType.SUBSCRIPTION,
                        expiryDate,
                        isActive,
                        isActive);
                info.setCanceled(false);
                info.setPricePaid(rs.getDouble("price_paid"));
                info.setPurchaseDate(
                        rs.getTimestamp("start_date") != null
                                ? rs.getTimestamp("start_date").toLocalDateTime().toLocalDate()
                                : null);
                purchases.add(info);
            }
        } catch (SQLException e) {
            System.err.println("PurchaseDAO.getUserPurchases (subscriptions): " + e.getMessage());
        }

        // Get one-time purchases ordered by city then oldest first, so we can assign
        // canDownload per row: only the first (purchaseCount - downloadCount) rows per city get true
        String purchaseQuery = """
                SELECT p.city_id, c.name, p.purchased_at, p.price_paid
                FROM purchases p
                JOIN cities c ON p.city_id = c.id
                WHERE p.user_id = ?
                ORDER BY p.city_id, p.purchased_at ASC
                """;

        java.util.List<EntitlementInfo> oneTimeList = new java.util.ArrayList<>();
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(purchaseQuery)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            int lastCityId = -1;
            int downloadCountForCity = 0;
            int slotIndexForCity = 0;

            while (rs.next()) {
                int cid = rs.getInt("city_id");
                if (cid != lastCityId) {
                    lastCityId = cid;
                    downloadCountForCity = getDownloadCount(userId, cid);
                    slotIndexForCity = 0;
                }
                // This row's download is still available iff we haven't "used" this slot yet
                boolean canDownload = slotIndexForCity >= downloadCountForCity;
                slotIndexForCity++;

                EntitlementInfo info = new EntitlementInfo(
                        cid,
                        rs.getString("name"),
                        EntitlementInfo.EntitlementType.ONE_TIME,
                        null, // No expiry for one-time
                        true, // Can view
                        canDownload);
                info.setPricePaid(rs.getDouble("price_paid"));
                info.setPurchaseDate(
                        rs.getTimestamp("purchased_at") != null
                                ? rs.getTimestamp("purchased_at").toLocalDateTime().toLocalDate()
                                : null);
                oneTimeList.add(info);
            }
        } catch (SQLException e) {
            System.err.println("PurchaseDAO.getUserPurchases (purchases): " + e.getMessage());
        }
        // Show not-yet-downloaded (canDownload=true) first in the one-time table
        oneTimeList.sort((a, b) -> Boolean.compare(b.isCanDownload(), a.isCanDownload()));
        purchases.addAll(oneTimeList);

        return purchases;
    }

    // ==================== Phase 6: Detailed Purchase Methods ====================

    /**
     * Get detailed purchases for a user (for profile view).
     *
     * @param userId        User ID
     * @param lastMonthOnly Filter purchases to last 1 month
     * @return List of CustomerPurchaseDTO
     */
    public static java.util.List<common.dto.CustomerPurchaseDTO> getPurchasesDetailed(int userId,
            boolean lastMonthOnly) {
        java.util.List<common.dto.CustomerPurchaseDTO> purchases = new java.util.ArrayList<>();

        String dateFilterSubs = lastMonthOnly ? " AND s.start_date >= DATE_SUB(NOW(), INTERVAL 1 MONTH)" : "";

        String subQuery = "SELECT s.id, s.city_id, c.name, s.price_paid, s.start_date, " +
                "s.months, s.end_date, s.is_active " +
                "FROM subscriptions s JOIN cities c ON s.city_id = c.id " +
                "WHERE s.user_id = ?" + dateFilterSubs +
                " ORDER BY s.start_date DESC";

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

        String dateFilterPurchases = lastMonthOnly ? " AND p.purchased_at >= DATE_SUB(NOW(), INTERVAL 1 MONTH)" : "";

        String purchaseQuery = "SELECT p.id, p.city_id, c.name, p.price_paid, p.purchased_at " +
                "FROM purchases p JOIN cities c ON p.city_id = c.id " +
                "WHERE p.user_id = ?" + dateFilterPurchases +
                " ORDER BY p.purchased_at DESC";

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

        // Get users with active subscriptions (including cancelled ones that haven't
        // expired)
        String subQuery = "SELECT DISTINCT user_id FROM subscriptions WHERE city_id = ? AND end_date > NOW()";
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
                WHERE s.end_date > CURDATE()
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
