package server.dao;

import common.Poi;
import server.DBConnector;
import server.service.OsrmClient;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores and retrieves distances between POI pairs (for tour planning).
 * Uses OSRM public API for road-network distance; falls back to haversine if OSRM fails.
 * We store only one direction: poi_id_a < poi_id_b.
 */
public class PoiDistanceDAO {

    /**
     * Get distance in meters between two POIs (order-independent).
     */
    public static Double getDistance(int poiId1, int poiId2) {
        int a = Math.min(poiId1, poiId2);
        int b = Math.max(poiId1, poiId2);
        if (a == b) return 0.0;

        String query = "SELECT distance_meters FROM poi_distances WHERE poi_id_a = ? AND poi_id_b = ?";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return null;
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, a);
            stmt.setInt(2, b);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("distance_meters");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get all stored distances for POIs in the given list (map (poiId1, poiId2) -> meters).
     */
    public static Map<String, Double> getDistancesForPois(java.util.List<Integer> poiIds) {
        Map<String, Double> out = new HashMap<>();
        if (poiIds == null || poiIds.size() < 2) return out;

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return out;
            for (int i = 0; i < poiIds.size(); i++) {
                for (int j = i + 1; j < poiIds.size(); j++) {
                    int a = Math.min(poiIds.get(i), poiIds.get(j));
                    int b = Math.max(poiIds.get(i), poiIds.get(j));
                    Double d = getDistanceInternal(conn, a, b);
                    if (d != null) {
                        out.put(a + "_" + b, d);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * Get distance in meters between two POIs using the given connection (for use inside a transaction).
     */
    public static Double getDistance(Connection conn, int poiId1, int poiId2) throws SQLException {
        int a = Math.min(poiId1, poiId2);
        int b = Math.max(poiId1, poiId2);
        if (a == b) return 0.0;
        return getDistanceInternal(conn, a, b);
    }

    private static Double getDistanceInternal(Connection conn, int a, int b) throws SQLException {
        String query = "SELECT distance_meters FROM poi_distances WHERE poi_id_a = ? AND poi_id_b = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, a);
        stmt.setInt(2, b);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return rs.getDouble("distance_meters");
        // Not stored: compute via OSRM (road distance) and store
        Double meters = computeAndStoreDistance(conn, a, b);
        return meters;
    }

    /**
     * Compute road distance between two POIs using OSRM, store in DB, and return.
     * Falls back to haversine if POIs lack coordinates or OSRM fails.
     */
    private static Double computeAndStoreDistance(Connection conn, int poiId1, int poiId2) throws SQLException {
        int a = Math.min(poiId1, poiId2);
        int b = Math.max(poiId1, poiId2);
        if (a == b) return 0.0;
        Poi p1 = PoiDAO.getPoiById(conn, a);
        Poi p2 = PoiDAO.getPoiById(conn, b);
        if (p1 == null || p2 == null || p1.getLatitude() == null || p1.getLongitude() == null
                || p2.getLatitude() == null || p2.getLongitude() == null) {
            return null;
        }
        Double meters = OsrmClient.getInstance().getDistanceMeters(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude());
        if (meters == null) {
            meters = haversineMeters(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude());
        }
        setDistance(conn, a, b, meters);
        return meters;
    }

    /**
     * Upsert distance between two POIs (order normalized to a < b).
     */
    public static void setDistance(Connection conn, int poiId1, int poiId2, double distanceMeters) throws SQLException {
        int a = Math.min(poiId1, poiId2);
        int b = Math.max(poiId1, poiId2);
        if (a == b) return;

        String query = "INSERT INTO poi_distances (poi_id_a, poi_id_b, distance_meters) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE distance_meters = VALUES(distance_meters)";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, a);
        stmt.setInt(2, b);
        stmt.setDouble(3, distanceMeters);
        stmt.executeUpdate();
    }

    /**
     * Recompute and store distances for all pairs in the given POI list using OSRM (road network).
     * Falls back to haversine if OSRM fails. POIs without lat/lon are skipped.
     */
    public static void recomputeAndStoreDistances(Connection conn, java.util.List<Poi> pois) throws SQLException {
        if (pois == null || pois.size() < 2) return;
        OsrmClient osrm = OsrmClient.getInstance();

        for (int i = 0; i < pois.size(); i++) {
            Poi p1 = pois.get(i);
            if (p1.getLatitude() == null || p1.getLongitude() == null || p1.getId() <= 0) continue;
            for (int j = i + 1; j < pois.size(); j++) {
                Poi p2 = pois.get(j);
                if (p2.getLatitude() == null || p2.getLongitude() == null || p2.getId() <= 0) continue;
                Double meters = osrm.getDistanceMeters(
                        p1.getLatitude(), p1.getLongitude(),
                        p2.getLatitude(), p2.getLongitude());
                if (meters == null) {
                    meters = haversineMeters(
                            p1.getLatitude(), p1.getLongitude(),
                            p2.getLatitude(), p2.getLongitude());
                }
                setDistance(conn, p1.getId(), p2.getId(), meters);
            }
        }
    }

    /**
     * Haversine formula: distance in meters between two (lat, lon) points.
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
