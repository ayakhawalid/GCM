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

    /**
     * Get all cities visible to the current user: approved cities (including legacy NULL) plus cities created by this user (drafts).
     * @param currentUserId logged-in user ID; if <= 0, only approved cities are returned (no drafts).
     */
    public static List<CityDTO> getAllCities(int currentUserId) {
        List<CityDTO> cities = new ArrayList<>();

        String query;
        if (currentUserId > 0) {
            query = "SELECT c.id, c.name, c.description, c.price, " +
                    "(SELECT COUNT(*) FROM maps m2 WHERE m2.city_id = c.id AND (m2.approved = 1 OR m2.approved IS NULL OR m2.created_by = ?)) as map_count " +
                    "FROM cities c WHERE (c.approved = 1 OR c.approved IS NULL) OR c.created_by = ? ORDER BY c.name";
        } else {
            query = "SELECT c.id, c.name, c.description, c.price, " +
                    "(SELECT COUNT(*) FROM maps WHERE city_id = c.id AND (approved = 1 OR approved IS NULL)) as map_count " +
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
                cities.add(new CityDTO(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
                        rs.getInt("map_count")));
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
                "(SELECT COUNT(*) FROM maps WHERE city_id = c.id) as map_count " +
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
                return executeCreateCity(stmt);
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
     * Get a city by ID.
     */
    public static CityDTO getCityById(int cityId) {
        String query = "SELECT c.id, c.name, c.description, c.price, " +
                "(SELECT COUNT(*) FROM maps WHERE city_id = c.id) as map_count " +
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

    /**
     * Set a city as approved so it appears in the catalog for everyone.
     */
    public static boolean setCityApproved(Connection conn, int cityId) throws SQLException {
        try {
            String sql = "UPDATE cities SET approved = 1 WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cityId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("approved")) return false;
            throw e;
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
