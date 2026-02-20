package server.dao;

import common.Poi;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for POI operations.
 */
public class PoiDAO {

    /**
     * Get all POIs linked to a map.
     */
    public static List<Poi> getPoisForMap(int mapId) {
        List<Poi> pois = new ArrayList<>();

        String query = "SELECT p.* FROM pois p " +
                "JOIN map_pois mp ON mp.poi_id = p.id " +
                "WHERE mp.map_id = ? " +
                "ORDER BY mp.display_order";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return pois;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                pois.add(extractPoi(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return pois;
    }

    /**
     * Get all POIs for a city (not linked to any specific map).
     */
    public static List<Poi> getPoisForCity(int cityId) {
        List<Poi> pois = new ArrayList<>();

        String query = "SELECT * FROM pois WHERE city_id = ? ORDER BY name";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return pois;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                pois.add(extractPoi(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return pois;
    }

    /**
     * Create a new POI.
     * 
     * @return created POI ID, or -1 on failure
     */
    public static int createPoi(Connection conn, Poi poi) throws SQLException {
        String query = "INSERT INTO pois (city_id, name, location, latitude, longitude, category, short_explanation, is_accessible) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, poi.getCityId());
        stmt.setString(2, poi.getName());
        stmt.setString(3, poi.getLocation());
        setDoubleOrNull(stmt, 4, poi.getLatitude());
        setDoubleOrNull(stmt, 5, poi.getLongitude());
        stmt.setString(6, poi.getCategory());
        stmt.setString(7, poi.getShortExplanation());
        stmt.setBoolean(8, poi.isAccessible());

        int affected = stmt.executeUpdate();

        if (affected > 0) {
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int poiId = keys.getInt(1);
                System.out.println("PoiDAO: Created POI with ID " + poiId);
                return poiId;
            }
        }

        return -1;
    }

    /**
     * Create a new POI (standalone).
     */
    public static int createPoi(Poi poi) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return -1;
            return createPoi(conn, poi);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Update an existing POI.
     */
    public static boolean updatePoi(Connection conn, Poi poi) throws SQLException {
        String query = "UPDATE pois SET name = ?, location = ?, latitude = ?, longitude = ?, category = ?, " +
                "short_explanation = ?, is_accessible = ? WHERE id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, poi.getName());
        stmt.setString(2, poi.getLocation());
        setDoubleOrNull(stmt, 3, poi.getLatitude());
        setDoubleOrNull(stmt, 4, poi.getLongitude());
        stmt.setString(5, poi.getCategory());
        stmt.setString(6, poi.getShortExplanation());
        stmt.setBoolean(7, poi.isAccessible());
        stmt.setInt(8, poi.getId());

        int affected = stmt.executeUpdate();
        System.out.println("PoiDAO: Updated POI " + poi.getId() + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Update an existing POI (standalone).
     */
    public static boolean updatePoi(Poi poi) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return false;
            return updatePoi(conn, poi);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a POI.
     * NOTE: This will fail if the POI is referenced by any tour stops.
     */
    public static boolean deletePoi(Connection conn, int poiId) throws SQLException {
        // Check if POI is used in any tour
        if (isPoiUsedInTour(conn, poiId)) {
            throw new SQLException("Cannot delete POI " + poiId + " - it is used in a tour");
        }

        // First unlink from all maps
        unlinkPoiFromAllMaps(conn, poiId);

        // Then delete the POI
        String query = "DELETE FROM pois WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, poiId);

        int affected = stmt.executeUpdate();
        System.out.println("PoiDAO: Deleted POI " + poiId + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Check if a POI is used in any tour.
     */
    public static boolean isPoiUsedInTour(Connection conn, int poiId) throws SQLException {
        String query = "SELECT COUNT(*) FROM tour_stops WHERE poi_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, poiId);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return rs.getInt(1) > 0;
        }

        return false;
    }

    /**
     * Check if POI is used in tour (standalone).
     */
    public static boolean isPoiUsedInTour(int poiId) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return false;
            return isPoiUsedInTour(conn, poiId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Link a POI to a map.
     */
    public static boolean linkPoiToMap(Connection conn, int mapId, int poiId, int displayOrder) throws SQLException {
        String query = "INSERT INTO map_pois (map_id, poi_id, display_order) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE display_order = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        stmt.setInt(2, poiId);
        stmt.setInt(3, displayOrder);
        stmt.setInt(4, displayOrder);

        int affected = stmt.executeUpdate();
        System.out.println("PoiDAO: Linked POI " + poiId + " to map " + mapId);
        return affected > 0;
    }

    /**
     * Unlink a POI from a map.
     */
    public static boolean unlinkPoiFromMap(Connection conn, int mapId, int poiId) throws SQLException {
        String query = "DELETE FROM map_pois WHERE map_id = ? AND poi_id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        stmt.setInt(2, poiId);

        int affected = stmt.executeUpdate();
        System.out.println("PoiDAO: Unlinked POI " + poiId + " from map " + mapId);
        return affected > 0;
    }

    /**
     * Unlink a POI from all maps.
     */
    private static void unlinkPoiFromAllMaps(Connection conn, int poiId) throws SQLException {
        String query = "DELETE FROM map_pois WHERE poi_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, poiId);
        stmt.executeUpdate();
    }

    /**
     * Get a POI by ID.
     */
    public static Poi getPoiById(int poiId) {
        String query = "SELECT * FROM pois WHERE id = ?";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return null;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, poiId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return extractPoi(rs);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Extract POI from ResultSet.
     */
    private static Poi extractPoi(ResultSet rs) throws SQLException {
        Double lat = null, lng = null;
        try {
            double d = rs.getDouble("latitude");
            if (!rs.wasNull()) lat = d;
            d = rs.getDouble("longitude");
            if (!rs.wasNull()) lng = d;
        } catch (SQLException ignored) {
            // columns may not exist in older DB
        }
        return new Poi(
                rs.getInt("id"),
                rs.getInt("city_id"),
                rs.getString("name"),
                rs.getString("location"),
                lat,
                lng,
                rs.getString("category"),
                rs.getString("short_explanation"),
                rs.getBoolean("is_accessible"));
    }

    private static void setDoubleOrNull(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value != null) {
            stmt.setDouble(index, value);
        } else {
            stmt.setNull(index, java.sql.Types.DOUBLE);
        }
    }
}
