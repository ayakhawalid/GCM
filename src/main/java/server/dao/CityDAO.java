package server.dao;

import common.dto.CityDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for City operations.
 */
public class CityDAO {

    private static volatile boolean citiesApprovalColumnsMigrated = false;

    /**
     * Ensures cities table has approved and created_by columns. Safe to call multiple times; runs migration at most once.
     */
    public static void ensureCitiesApprovalColumns() {
        if (citiesApprovalColumnsMigrated) return;
        synchronized (CityDAO.class) {
            if (citiesApprovalColumnsMigrated) return;
            try (Connection conn = DBConnector.getConnection()) {
                if (conn == null) return;
                // Add approved if missing (MySQL has no IF NOT EXISTS for columns; catch duplicate)
                try {
                    conn.createStatement().executeUpdate("ALTER TABLE cities ADD COLUMN approved TINYINT DEFAULT 1");
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1060 && (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")))
                        throw e;
                }
                try {
                    conn.createStatement().executeUpdate("ALTER TABLE cities ADD COLUMN created_by INT DEFAULT NULL");
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1060 && (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")))
                        throw e;
                }
                conn.createStatement().executeUpdate("UPDATE cities SET approved = 1 WHERE approved IS NULL");
                citiesApprovalColumnsMigrated = true;
                System.out.println("CityDAO: cities approval columns migration completed.");
            } catch (SQLException e) {
                System.out.println("CityDAO: migration skipped or failed: " + e.getMessage());
            }
        }
    }

    /**
     * Get all cities visible to the current user: approved cities (including legacy NULL) plus cities created by this user (drafts).
     * @param currentUserId logged-in user ID; if <= 0, only approved cities are returned (no drafts).
     */
    public static List<CityDTO> getAllCities(int currentUserId) {
        ensureCitiesApprovalColumns();
        List<CityDTO> cities = new ArrayList<>();

        String query;
        if (currentUserId > 0) {
            // Show approved cities to all; show draft (approved=0) only to creator. NULL treated as approved so approved cities stay non-draft after migration/backfill.
            query = "SELECT c.id, c.name, c.description, c.price, COALESCE(c.approved, 1) as approved, " +
                    "(SELECT COUNT(DISTINCT TRIM(m2.name)) FROM maps m2 WHERE m2.city_id = c.id AND (m2.approved = 1 OR m2.created_by = ?)) as map_count " +
                    "FROM cities c WHERE (c.approved = 1) OR (c.created_by = ?) ORDER BY c.name";
        } else {
            query = "SELECT c.id, c.name, c.description, c.price, 1 as approved, " +
                    "(SELECT COUNT(DISTINCT TRIM(name)) FROM maps WHERE city_id = c.id AND (approved = 1 OR approved IS NULL)) as map_count " +
                    "FROM cities c WHERE (c.approved = 1 OR c.approved IS NULL) ORDER BY c.name";
        }

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return cities;

            PreparedStatement stmt = conn.prepareStatement(query);
            if (currentUserId > 0) {
                stmt.setInt(1, currentUserId);
                stmt.setInt(2, currentUserId);
            }
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                CityDTO dto = new CityDTO(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
                        rs.getInt("map_count"));
                try {
                    dto.setDraft(rs.getInt("approved") == 0);
                } catch (SQLException e) {
                    // column may not exist
                }
                cities.add(dto);
            }

            System.out.println("CityDAO: Retrieved " + cities.size() + " cities for user " + currentUserId);

        } catch (SQLException e) {
            try {
                // Fallback if approved/created_by columns don't exist yet
                return getAllCitiesLegacy();
            } catch (Exception e2) {
                System.out.println("CityDAO: Error getting cities");
                e.printStackTrace();
            }
        }

        return cities;
    }

    /** Legacy query when approval columns are not present. */
    private static List<CityDTO> getAllCitiesLegacy() throws SQLException {
        List<CityDTO> cities = new ArrayList<>();
        String query = "SELECT c.id, c.name, c.description, c.price, " +
                "(SELECT COUNT(DISTINCT TRIM(name)) FROM maps WHERE city_id = c.id) as map_count " +
                "FROM cities c ORDER BY c.name";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return cities;
            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                cities.add(new CityDTO(rs.getInt("id"), rs.getString("name"), rs.getString("description"),
                        rs.getDouble("price"), rs.getInt("map_count")));
            }
        }
        return cities;
    }

    /**
     * Create a new city.
     * @param createdBy user ID who created it; unapproved cities are visible only to this user until approved.
     * @param approved 1 if content manager created/approved, 0 if pending approval.
     * @return the created city ID, or -1 on failure
     */
    public static int createCity(Connection conn, String name, String description, double price, int createdBy, boolean approved) throws SQLException {
        ensureCitiesApprovalColumns();
        try {
            String query = "INSERT INTO cities (name, description, price, created_by, approved) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setDouble(3, price);
            stmt.setInt(4, createdBy <= 0 ? 1 : createdBy);
            stmt.setInt(5, approved ? 1 : 0);
            return executeCreateCity(stmt);
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("approved") || e.getMessage().contains("created_by"))) {
                PreparedStatement stmt = conn.prepareStatement("INSERT INTO cities (name, description, price) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, name);
                stmt.setString(2, description);
                stmt.setDouble(3, price);
                int cityId = executeCreateCity(stmt);
                // After 3-column fallback, ensure approval columns exist and set approved=1 when intended
                if (cityId > 0 && approved) {
                    ensureCitiesApprovalColumns();
                    try {
                        PreparedStatement up = conn.prepareStatement("UPDATE cities SET approved = 1 WHERE id = ?");
                        up.setInt(1, cityId);
                        up.executeUpdate();
                    } catch (SQLException e2) {
                        // column may still not exist in this connection's view
                    }
                }
                return cityId;
            }
            throw e;
        }
    }

    private static int executeCreateCity(PreparedStatement stmt) throws SQLException {
        int affected = stmt.executeUpdate();
        if (affected > 0) {
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int cityId = keys.getInt(1);
                System.out.println("CityDAO: Created city with ID " + cityId);
                return cityId;
            }
        }
        return -1;
    }

    /** Create city without approval (backward compatibility). */
    public static int createCity(Connection conn, String name, String description, double price) throws SQLException {
        return createCity(conn, name, description, price, 1, true);
    }

    /**
     * Create a new city (standalone, auto-commits).
     */
    public static int createCity(String name, String description, double price) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return -1;
            return createCity(conn, name, description, price);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Update an existing city.
     */
    public static boolean updateCity(int cityId, String name, String description, double price) {
        String query = "UPDATE cities SET name = ?, description = ?, price = ? WHERE id = ?";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return false;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setDouble(3, price);
            stmt.setInt(4, cityId);

            int affected = stmt.executeUpdate();
            System.out.println("CityDAO: Updated city " + cityId + ", affected: " + affected);
            return affected > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a city by ID (uses given connection, for use in transaction).
     * Caller must delete maps and tours for the city first.
     */
    public static boolean deleteCity(Connection conn, int cityId) throws SQLException {
        if (cityId <= 0) return false;
        String query = "DELETE FROM cities WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, cityId);
        int affected = stmt.executeUpdate();
        return affected > 0;
    }

    /**
     * Delete a city by ID (uses own connection). Cascades to maps and related data. Use for test cleanup only.
     */
    public static boolean deleteCity(int cityId) {
        if (cityId <= 0) return false;
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return false;
            return deleteCity(conn, cityId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get a city by ID.
     */
    public static CityDTO getCityById(int cityId) {
        String query = "SELECT c.id, c.name, c.description, c.price, " +
                "(SELECT COUNT(DISTINCT TRIM(name)) FROM maps WHERE city_id = c.id) as map_count " +
                "FROM cities c WHERE c.id = ?";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return null;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new CityDTO(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
                        rs.getInt("map_count"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Find an unapproved (draft) city by exact name. Used when manager approves a request so we approve the existing city instead of creating a duplicate.
     * Returns null if no such city or if approved column doesn't exist.
     */
    public static Integer findUnapprovedCityByName(Connection conn, String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) return null;
        try {
            String sql = "SELECT id FROM cities WHERE TRIM(name) = ? AND (approved = 0 OR approved IS NULL) LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, name.trim());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : null;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("approved")) return null;
            throw e;
        }
    }

    /** Find any city by exact name (approved or not). */
    public static Integer findCityIdByName(Connection conn, String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) return null;
        String sql = "SELECT id FROM cities WHERE TRIM(name) = ? LIMIT 1";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, name.trim());
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? rs.getInt("id") : null;
    }

    /**
     * Return true if the city is a draft (unapproved). Employee can delete draft cities immediately on Save.
     */
    public static boolean isCityDraft(Connection conn, int cityId) throws SQLException {
        if (cityId <= 0) return false;
        try {
            String sql = "SELECT 1 FROM cities WHERE id = ? AND (approved = 0 OR approved IS NULL) LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("approved")) return false;
            throw e;
        }
    }

    /**
     * Get city name by ID. Returns null if not found.
     */
    public static String getCityName(Connection conn, int cityId) throws SQLException {
        if (cityId <= 0 || conn == null) return null;
        String sql = "SELECT name FROM cities WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("name") : null;
        }
    }

    /**
     * Set a city as approved so it appears in the catalog for everyone.
     */
    public static boolean setCityApproved(Connection conn, int cityId) throws SQLException {
        if (cityId <= 0) return false;
        ensureCitiesApprovalColumns();
        try {
            String sql = "UPDATE cities SET approved = 1 WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cityId);
            int updated = stmt.executeUpdate();
            if (updated > 0) return true;
        } catch (SQLException e) {
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("approved")) throw e;
        }
        // Column may have been missing; ensure migration ran and retry once
        ensureCitiesApprovalColumns();
        try {
            PreparedStatement stmt = conn.prepareStatement("UPDATE cities SET approved = 1 WHERE id = ?");
            stmt.setInt(1, cityId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Check if a city name already exists.
     */
    public static boolean cityNameExists(String name) {
        String query = "SELECT COUNT(*) FROM cities WHERE LOWER(name) = LOWER(?)";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return false;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
}
