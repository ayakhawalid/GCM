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
     * Get all maps for a city.
     */
    public static List<MapSummary> getMapsForCity(int cityId) {
        List<MapSummary> maps = new ArrayList<>();

        String query = "SELECT m.id, m.name, m.short_description, " +
                "(SELECT COUNT(*) FROM map_pois WHERE map_id = m.id) as poi_count, " +
                "(SELECT COUNT(*) FROM tours WHERE city_id = m.city_id) as tour_count " +
                "FROM maps m WHERE m.city_id = ? ORDER BY m.name";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return maps;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
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
            e.printStackTrace();
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
     * 
     * @return created map ID, or -1 on failure
     */
    public static int createMap(Connection conn, int cityId, String name, String description) throws SQLException {
        String query = "INSERT INTO maps (city_id, name, short_description) VALUES (?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, cityId);
        stmt.setString(2, name);
        stmt.setString(3, description);

        int affected = stmt.executeUpdate();

        if (affected > 0) {
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int mapId = keys.getInt(1);
                System.out.println("MapDAO: Created map with ID " + mapId);
                return mapId;
            }
        }

        return -1;
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
