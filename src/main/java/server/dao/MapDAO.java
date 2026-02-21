package server.dao;

import common.Poi;
import common.dto.MapContent;
import common.dto.MapSummary;
import common.dto.TourDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Map operations.
 */
public class MapDAO {

    /**
     * Get all maps for a city visible to the current user: approved maps plus maps created by this user.
     * @param currentUserId if <= 0, only approved maps are returned.
     */
    public static List<MapSummary> getMapsForCity(int cityId, int currentUserId) {
        List<MapSummary> maps = new ArrayList<>();

        String query;
        String poiCountSubquery = "(SELECT COUNT(*) FROM map_pois mp WHERE mp.map_id = m.id AND (mp.approved = 1 OR mp.approved IS NULL))";
        if (currentUserId > 0) {
            query = "SELECT m.id, m.name, m.short_description, " +
                    poiCountSubquery + " as poi_count, " +
                    "(SELECT COUNT(*) FROM tours WHERE city_id = m.city_id) as tour_count " +
                    "FROM maps m WHERE m.city_id = ? AND (m.approved = 1 OR m.created_by = ?) ORDER BY m.name";
        } else {
            query = "SELECT m.id, m.name, m.short_description, " +
                    poiCountSubquery + " as poi_count, " +
                    "(SELECT COUNT(*) FROM tours WHERE city_id = m.city_id) as tour_count " +
                    "FROM maps m WHERE m.city_id = ? AND m.approved = 1 ORDER BY m.name";
        }

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return maps;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            if (currentUserId > 0) stmt.setInt(2, currentUserId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                maps.add(new MapSummary(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("short_description"),
                        rs.getInt("poi_count"),
                        rs.getInt("tour_count")));
            }

            System.out.println("MapDAO: Retrieved " + maps.size() + " maps for city " + cityId);

        } catch (SQLException e) {
            try {
                return getMapsForCityLegacy(cityId);
            } catch (SQLException e2) {
                e.printStackTrace();
            }
        }

        return maps;
    }

    private static List<MapSummary> getMapsForCityLegacy(int cityId) throws SQLException {
        List<MapSummary> maps = new ArrayList<>();
        String query = "SELECT m.id, m.name, m.short_description, " +
                "(SELECT COUNT(*) FROM map_pois WHERE map_id = m.id) as poi_count, " +
                "(SELECT COUNT(*) FROM tours WHERE city_id = m.city_id) as tour_count " +
                "FROM maps m WHERE m.city_id = ? ORDER BY m.name";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return maps;
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                maps.add(new MapSummary(rs.getInt("id"), rs.getString("name"), rs.getString("short_description"),
                        rs.getInt("poi_count"), rs.getInt("tour_count")));
            }
        }
        return maps;
    }

    /**
     * Get complete map content for editing.
     */
    public static MapContent getMapContent(int mapId) {
        MapContent content = null;

        String mapQuery = "SELECT m.id, m.city_id, c.name as city_name, m.name, m.short_description, " +
                "m.created_at, m.updated_at " +
                "FROM maps m JOIN cities c ON c.id = m.city_id WHERE m.id = ?";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return null;

            // Get map basic info
            PreparedStatement stmt = conn.prepareStatement(mapQuery);
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                content = new MapContent(
                        rs.getInt("id"),
                        rs.getInt("city_id"),
                        rs.getString("city_name"),
                        rs.getString("name"),
                        rs.getString("short_description"));
                content.setCreatedAt(rs.getString("created_at"));
                content.setUpdatedAt(rs.getString("updated_at"));

                // Get POIs for this map
                List<Poi> pois = PoiDAO.getPoisForMap(mapId);
                content.setPois(pois);

                // Get tours for this city
                List<TourDTO> tours = TourDAO.getToursForCity(content.getCityId());
                content.setTours(tours);

                System.out.println("MapDAO: Retrieved content for map " + mapId +
                        " with " + pois.size() + " POIs and " + tours.size() + " tours");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return content;
    }

    /**
     * Create a new map.
     * @param createdBy user who created it; unapproved maps visible only to this user until approved.
     * @param approved true if content manager created/approved.
     */
    public static int createMap(Connection conn, int cityId, String name, String description, int createdBy, boolean approved) throws SQLException {
        try {
            String query = "INSERT INTO maps (city_id, name, short_description, created_by, approved) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, cityId);
            stmt.setString(2, name);
            stmt.setString(3, description);
            stmt.setInt(4, createdBy <= 0 ? 1 : createdBy);
            stmt.setInt(5, approved ? 1 : 0);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    int mapId = keys.getInt(1);
                    System.out.println("MapDAO: Created map with ID " + mapId);
                    return mapId;
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("approved") || e.getMessage().contains("created_by"))) {
                PreparedStatement stmt = conn.prepareStatement("INSERT INTO maps (city_id, name, short_description) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, cityId);
                stmt.setString(2, name);
                stmt.setString(3, description);
                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    ResultSet keys = stmt.getGeneratedKeys();
                    if (keys.next()) return keys.getInt(1);
                }
            } else throw e;
        }
        return -1;
    }

    /** Create map without approval columns (backward compatibility). */
    public static int createMap(Connection conn, int cityId, String name, String description) throws SQLException {
        return createMap(conn, cityId, name, description, 1, true);
    }

    /**
     * Create a new map (standalone).
     */
    public static int createMap(int cityId, String name, String description) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return -1;
            return createMap(conn, cityId, name, description);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Find an unapproved (draft) map by city and name. Used when manager approves so we approve the existing map instead of creating a duplicate.
     */
    public static Integer findUnapprovedMapByCityAndName(Connection conn, int cityId, String mapName) throws SQLException {
        if (mapName == null || mapName.trim().isEmpty()) return null;
        try {
            String sql = "SELECT id FROM maps WHERE city_id = ? AND TRIM(name) = ? AND (approved = 0 OR approved IS NULL) LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cityId);
            stmt.setString(2, mapName.trim());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : null;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("approved") || e.getMessage().contains("created_by"))) return null;
            throw e;
        }
    }

    /**
     * Set a map as approved so it appears in the catalog.
     */
    public static boolean setMapApproved(Connection conn, int mapId) throws SQLException {
        try {
            String sql = "UPDATE maps SET approved = 1 WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, mapId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("approved")) return false;
            throw e;
        }
    }

    /**
     * Update an existing map (uses provided connection for transaction).
     */
    public static boolean updateMap(Connection conn, int mapId, String name, String description) throws SQLException {
        String query = "UPDATE maps SET name = ?, short_description = ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, name != null ? name : "");
        stmt.setString(2, description != null ? description : "");
        stmt.setInt(3, mapId);
        int affected = stmt.executeUpdate();
        System.out.println("MapDAO: Updated map " + mapId + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Update an existing map (uses own connection).
     */
    public static boolean updateMap(int mapId, String name, String description) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return false;
            return updateMap(conn, mapId, name, description);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a map (uses own connection).
     */
    public static boolean deleteMap(int mapId) {
        try (Connection conn = DBConnector.getConnection()) {
            return conn != null && deleteMap(conn, mapId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a map (uses provided connection for transaction).
     */
    public static boolean deleteMap(Connection conn, int mapId) throws SQLException {
        // CASCADE will delete map_pois entries
        String query = "DELETE FROM maps WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        int affected = stmt.executeUpdate();
        System.out.println("MapDAO: Deleted map " + mapId + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Check if map name exists in city.
     */
    public static boolean mapNameExistsInCity(int cityId, String name) {
        String query = "SELECT COUNT(*) FROM maps WHERE city_id = ? AND LOWER(name) = LOWER(?)";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return false;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            stmt.setString(2, name);
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
