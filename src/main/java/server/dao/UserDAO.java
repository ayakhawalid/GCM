package server.dao;

import server.DBConnector;

import java.sql.*;

/**
 * Data Access Object for user authentication and registration.
 * Uses plain text passwords for simplicity (university project).
 */
public class UserDAO {

    /**
     * User info holder (returned from findByUsername).
     */
    public static class UserInfo {
        public final int id;
        public final String username;
        public final String email;
        public final String role;
        public final boolean isActive;

        public UserInfo(int id, String username, String email, String role, boolean isActive) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
            this.isActive = isActive;
        }
    }

    /**
     * Find user by username.
     * 
     * @param username Username to search
     * @return UserInfo or null if not found
     */
    public static UserInfo findByUsername(String username) {
        String sql = "SELECT id, username, email, role, is_active FROM users WHERE username = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new UserInfo(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getBoolean("is_active"));
            }
        } catch (SQLException e) {
            System.err.println("Error finding user by username: " + e.getMessage());
        }
        return null;
    }

    /**
     * Find user by email.
     * 
     * @param email Email to search
     * @return UserInfo or null if not found
     */
    public static UserInfo findByEmail(String email) {
        String sql = "SELECT id, username, email, role, is_active FROM users WHERE email = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new UserInfo(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getBoolean("is_active"));
            }
        } catch (SQLException e) {
            System.err.println("Error finding user by email: " + e.getMessage());
        }
        return null;
    }

    /**
     * Authenticate user with username and password.
     * 
     * @param username Username
     * @param password Password (plain text)
     * @return UserInfo if authentication successful, null otherwise
     */
    public static UserInfo authenticate(String username, String password) {
        String sql = "SELECT id, username, email, role, is_active FROM users " +
                "WHERE username = ? AND password_hash = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password); // Plain text comparison
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                boolean isActive = rs.getBoolean("is_active");
                if (!isActive) {
                    System.out.println("User " + username + " is deactivated");
                    return null;
                }
                return new UserInfo(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("role"),
                        isActive);
            }
        } catch (SQLException e) {
            System.err.println("Error authenticating user: " + e.getMessage());
        }
        return null;
    }

    /**
     * Find user by ID.
     */
    public static UserInfo findById(int userId) {
        String sql = "SELECT id, username, email, role, is_active FROM users WHERE id = ?";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new UserInfo(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getBoolean("is_active"));
            }
        } catch (SQLException e) {
            System.err.println("Error finding user by id: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get user IDs of all CONTENT_MANAGER and COMPANY_MANAGER users (for notifications).
     */
    public static java.util.List<Integer> getContentManagerUserIds() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        String sql = "SELECT id FROM users WHERE role IN ('CONTENT_MANAGER', 'COMPANY_MANAGER') AND is_active = TRUE";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting manager user IDs: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Create a new customer user.
     * 
     * @param username     Username (must be unique)
     * @param email        Email (must be unique)
     * @param password     Password (stored as plain text)
     * @param phone        Phone number (optional)
     * @param paymentToken Mock payment token
     * @param cardLast4    Last 4 digits of card
     * @return Created user ID, or -1 if failed
     */
    public static int createCustomer(String username, String email, String password,
            String phone, String paymentToken, String cardLast4, String cardExpiry) {
        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // 1. Create user
            String userSql = "INSERT INTO users (username, email, password_hash, role, phone, is_active) " +
                    "VALUES (?, ?, ?, 'CUSTOMER', ?, TRUE)";
            PreparedStatement userStmt = conn.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS);
            userStmt.setString(1, username);
            userStmt.setString(2, email);
            userStmt.setString(3, password); // Plain text
            userStmt.setString(4, phone);
            userStmt.executeUpdate();

            ResultSet keys = userStmt.getGeneratedKeys();
            if (!keys.next()) {
                conn.rollback();
                return -1;
            }
            int userId = keys.getInt(1);

            // 2. Create customer record
            String customerSql = "INSERT INTO customers (user_id, payment_token, card_last4, card_expiry) VALUES (?, ?, ?, ?)";
            PreparedStatement custStmt = conn.prepareStatement(customerSql);
            custStmt.setInt(1, userId);
            custStmt.setString(2, paymentToken);
            custStmt.setString(3, cardLast4);
            custStmt.setString(4, cardExpiry != null && !cardExpiry.isEmpty() ? cardExpiry : null);
            custStmt.executeUpdate();

            conn.commit();
            System.out.println("✓ Created new customer: " + username + " (ID: " + userId + ")");
            return userId;

        } catch (SQLException e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (SQLException ex) {
            }

            // Check for duplicate key errors
            if (e.getMessage().contains("Duplicate")) {
                System.out.println("Registration failed - duplicate username or email");
            } else {
                System.err.println("Error creating customer: " + e.getMessage());
            }
            return -1;
        } finally {
            try {
                if (conn != null)
                    conn.setAutoCommit(true);
            } catch (SQLException e) {
            }
        }
    }

    /**
     * Check if username already exists.
     */
    public static boolean usernameExists(String username) {
        return findByUsername(username) != null;
    }

    /**
     * Check if email already exists.
     */
    public static boolean emailExists(String email) {
        return findByEmail(email) != null;
    }

    /**
     * Update last login timestamp for user.
     */
    public static void updateLastLogin(int userId) {
        String sql = "UPDATE users SET last_login_at = NOW() WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating last login: " + e.getMessage());
        }
    }

    // ==================== Phase 6: Customer Profile Methods ====================

    /**
     * Get customer profile by user ID.
     *
     * @param userId User ID
     * @return CustomerProfileDTO or null if not found
     */
    public static common.dto.CustomerProfileDTO getProfile(int userId) {
        String sql = """
                SELECT u.id, u.username, u.email, u.phone, u.created_at, u.last_login_at,
                       c.card_last4, c.card_expiry,
                       (SELECT COUNT(*) FROM purchases WHERE user_id = u.id) as purchase_count,
                       (SELECT COUNT(*) FROM subscriptions WHERE user_id = u.id) as sub_count,
                       (SELECT COALESCE(SUM(price_paid), 0) FROM purchases WHERE user_id = u.id) +
                       (SELECT COALESCE(SUM(price_paid), 0) FROM subscriptions WHERE user_id = u.id) as total_spent
                FROM users u
                LEFT JOIN customers c ON u.id = c.user_id
                WHERE u.id = ?
                """;

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                common.dto.CustomerProfileDTO profile = new common.dto.CustomerProfileDTO();
                profile.setUserId(rs.getInt("id"));
                profile.setUsername(rs.getString("username"));
                profile.setEmail(rs.getString("email"));
                profile.setPhone(rs.getString("phone"));
                profile.setCardLast4(rs.getString("card_last4"));
                profile.setCardExpiry(rs.getString("card_expiry"));
                profile.setCreatedAt(rs.getTimestamp("created_at"));
                profile.setLastLoginAt(rs.getTimestamp("last_login_at"));
                profile.setTotalPurchases(rs.getInt("purchase_count") + rs.getInt("sub_count"));
                profile.setTotalSpent(rs.getDouble("total_spent"));
                return profile;
            }
        } catch (SQLException e) {
            System.err.println("Error getting profile: " + e.getMessage());
        }
        return null;
    }

    /**
     * Update customer profile.
     *
     * @param userId     User ID
     * @param email      New email (null to keep current)
     * @param phone      New phone (null to keep current)
     * @param cardNumber New card number (null to keep current, empty string to
     *                   remove)
     * @param cardExpiry New card expiry (null to keep current, empty string to
     *                   remove)
     * @return true if updated
     */
    public static boolean updateProfile(int userId, String email, String phone, String cardNumber, String cardExpiry) {
        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // 1. Update users table

            StringBuilder sqlUser = new StringBuilder("UPDATE users SET ");
            java.util.List<Object> paramsUser = new java.util.ArrayList<>();

            if (email != null) {
                sqlUser.append("email = ?, ");
                paramsUser.add(email);
            }
            if (phone != null) {
                sqlUser.append("phone = ?, ");
                paramsUser.add(phone);
            }

            if (!paramsUser.isEmpty()) {
                sqlUser.setLength(sqlUser.length() - 2); // Remove trailing comma
                sqlUser.append(" WHERE id = ?");
                paramsUser.add(userId);

                try (PreparedStatement stmt = conn.prepareStatement(sqlUser.toString())) {
                    for (int i = 0; i < paramsUser.size(); i++) {
                        stmt.setObject(i + 1, paramsUser.get(i));
                    }
                    stmt.executeUpdate();

                }
            }

            // 2. Update customers table (for card)

            if (cardNumber != null) {
                if (cardNumber.isEmpty()) {
                    // Remove card request
                    String sqlCust = "UPDATE customers SET card_last4 = NULL, card_expiry = NULL WHERE user_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlCust)) {
                        stmt.setInt(1, userId);
                        stmt.executeUpdate();
                    }
                } else {
                    // Update card request
                    String last4 = cardNumber.length() > 4 ? cardNumber.substring(cardNumber.length() - 4) : cardNumber;
                    String sqlCust = "UPDATE customers SET card_last4 = ?, card_expiry = ? WHERE user_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlCust)) {
                        stmt.setString(1, last4);
                        stmt.setString(2, cardExpiry);
                        stmt.setInt(3, userId);
                        stmt.executeUpdate();
                    }
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (SQLException ex) {
            }
            System.err.println("Error updating profile: " + e.getMessage());
            return false;
        } finally {
            try {
                if (conn != null)
                    conn.setAutoCommit(true);
            } catch (SQLException e) {
            }
        }
    }

    // ==================== User Management (Company Manager) ====================

    /**
     * List all staff users (roles above CUSTOMER).
     */
    public static java.util.List<common.dto.StaffUserDTO> listStaffUsers() {
        java.util.List<common.dto.StaffUserDTO> staff = new java.util.ArrayList<>();
        String sql = "SELECT id, username, email, role, created_at, is_active FROM users " +
                "WHERE role IN ('CONTENT_EDITOR','CONTENT_MANAGER','COMPANY_MANAGER','SUPPORT_AGENT') " +
                "ORDER BY FIELD(role,'COMPANY_MANAGER','CONTENT_MANAGER','CONTENT_EDITOR','SUPPORT_AGENT'), username";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                common.dto.StaffUserDTO dto = new common.dto.StaffUserDTO(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at"),
                        rs.getBoolean("is_active"));
                staff.add(dto);
            }
        } catch (SQLException e) {
            System.err.println("Error listing staff users: " + e.getMessage());
        }
        return staff;
    }

    /**
     * Update a user's role. Only allows setting to staff roles.
     *
     * @return true if updated successfully
     */
    public static boolean updateUserRole(int userId, String newRole) {
        if (!isValidStaffRole(newRole)) return false;

        String sql = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newRole);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating user role: " + e.getMessage());
            return false;
        }
    }

    /**
     * Revert a staff user back to CUSTOMER role.
     */
    public static boolean revokeRole(int userId) {
        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            String updateSql = "UPDATE users SET role = 'CUSTOMER' WHERE id = ? AND role != 'CUSTOMER'";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setInt(1, userId);
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // Ensure a customers record exists for the reverted user
            String checkSql = "SELECT 1 FROM customers WHERE user_id = ?";
            boolean hasCustomerRow = false;
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setInt(1, userId);
                hasCustomerRow = stmt.executeQuery().next();
            }
            if (!hasCustomerRow) {
                String insertSql = "INSERT INTO customers (user_id) VALUES (?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
            System.err.println("Error revoking role: " + e.getMessage());
            return false;
        } finally {
            try { if (conn != null) conn.setAutoCommit(true); } catch (SQLException e) {}
        }
    }

    /**
     * Create a new staff user with a given role.
     *
     * @return created user ID, or -1 on failure
     */
    public static int createStaffUser(String username, String email, String password, String role) {
        if (!isValidStaffRole(role)) return -1;

        String sql = "INSERT INTO users (username, email, password_hash, role, is_active) " +
                "VALUES (?, ?, ?, ?, TRUE)";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setString(4, role);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                System.out.println("Created staff user: " + username + " (" + role + ") id=" + id);
                return id;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                System.out.println("Staff user creation failed - duplicate username or email");
            } else {
                System.err.println("Error creating staff user: " + e.getMessage());
            }
        }
        return -1;
    }

    private static boolean isValidStaffRole(String role) {
        return "CONTENT_EDITOR".equals(role) || "CONTENT_MANAGER".equals(role) ||
               "COMPANY_MANAGER".equals(role) || "SUPPORT_AGENT".equals(role);
    }

    /**
     * List all customers (for admin).
     *
     * @param lastMonthOnly Filter stats to only include last 1 month
     * @return List of CustomerListItemDTO
     */
    public static java.util.List<common.dto.CustomerListItemDTO> listAllCustomers(boolean lastMonthOnly) {
        java.util.List<common.dto.CustomerListItemDTO> customers = new java.util.ArrayList<>();

        String dateFilterPurchases = lastMonthOnly ? " AND purchased_at >= DATE_SUB(NOW(), INTERVAL 1 MONTH)" : "";
        String dateFilterSubs = lastMonthOnly ? " AND start_date >= DATE_SUB(NOW(), INTERVAL 1 MONTH)" : "";

        String sql = "SELECT u.id, u.username, u.email, u.phone, u.created_at, u.is_active, " +
                "(SELECT COUNT(*) FROM purchases WHERE user_id = u.id" + dateFilterPurchases + ") as purchase_count, " +
                "(SELECT COUNT(*) FROM subscriptions WHERE user_id = u.id" + dateFilterSubs + ") as sub_count, " +
                "(SELECT COALESCE(SUM(price_paid), 0) FROM purchases WHERE user_id = u.id" + dateFilterPurchases
                + ") + " +
                "(SELECT COALESCE(SUM(price_paid), 0) FROM subscriptions WHERE user_id = u.id" + dateFilterSubs
                + ") as total_spent, " +
                "(SELECT MAX(purchased_at) FROM purchases WHERE user_id = u.id" + dateFilterPurchases
                + ") as last_purchase " +
                "FROM users u " +
                "WHERE u.role = 'CUSTOMER' " +
                "ORDER BY u.created_at DESC";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                common.dto.CustomerListItemDTO item = new common.dto.CustomerListItemDTO();
                item.setUserId(rs.getInt("id"));
                item.setUsername(rs.getString("username"));
                item.setEmail(rs.getString("email"));
                item.setPhone(rs.getString("phone"));
                item.setPurchaseCount(rs.getInt("purchase_count"));
                item.setSubscriptionCount(rs.getInt("sub_count"));
                item.setTotalSpent(rs.getDouble("total_spent"));
                item.setLastPurchaseAt(rs.getTimestamp("last_purchase"));
                item.setRegisteredAt(rs.getTimestamp("created_at"));
                item.setActive(rs.getBoolean("is_active"));
                customers.add(item);
            }
        } catch (SQLException e) {
            System.err.println("Error listing customers: " + e.getMessage());
        }

        return customers;
    }
}
