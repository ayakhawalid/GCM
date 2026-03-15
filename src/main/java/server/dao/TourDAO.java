package server.dao;

import common.dto.TourDTO;
import common.dto.TourStopDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Tour operations.
 */
public class TourDAO {

    /**
     * Get all tours for a city with their stops.
     */
    public static List<TourDTO> getToursForCity(int cityId) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return new ArrayList<>();
            return getToursForCity(conn, cityId);
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Get all tours for a city with their stops (uses given connection).
     */
    public static List<TourDTO> getToursForCity(Connection conn, int cityId) throws SQLException {
        List<TourDTO> tours = new ArrayList<>();
        String query = "SELECT * FROM tours WHERE city_id = ? ORDER BY name";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, cityId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            TourDTO tour = extractTour(rs);
            List<TourStopDTO> stops = getTourStops(conn, tour.getId());
            tour.setStops(stops);
            tours.add(tour);
        }
        return tours;
    }

    /**
     * Get a tour by ID with stops.
     */
    public static TourDTO getTourById(int tourId) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return null;
            return getTourById(conn, tourId);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Find a tour id by city and name (first match). Used to avoid creating duplicate when client sends id=0.
     */
    public static Integer findTourIdByCityAndName(Connection conn, int cityId, String name) throws SQLException {
        return findTourIdByCityAndNameExcluding(conn, cityId, name, null);
    }

    /**
     * Find a tour id by city and name, excluding given IDs (e.g. tours pending deletion).
     * So that after deleting a tour and creating a new one with the same name we get a new row.
     */
    public static Integer findTourIdByCityAndNameExcluding(Connection conn, int cityId, String name,
            java.util.Set<Integer> excludeTourIds) throws SQLException {
        if (name == null || name.trim().isEmpty()) return null;
        String query = "SELECT id FROM tours WHERE city_id = ? AND TRIM(name) = TRIM(?) ORDER BY id";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, cityId);
        stmt.setString(2, name.trim());
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            int id = rs.getInt("id");
            if (excludeTourIds != null && excludeTourIds.contains(id)) continue;
            return id;
        }
        return null;
    }

    /**
     * Get a tour by ID with stops (uses given connection, for use inside transactions).
     */
    public static TourDTO getTourById(Connection conn, int tourId) throws SQLException {
        String query = "SELECT * FROM tours WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, tourId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            TourDTO tour = extractTour(rs);
            tour.setStops(getTourStops(conn, tourId));
            return tour;
        }
        return null;
    }

    /**
     * Get stops for a tour, with distanceToNextMeters set for each stop (except last).
     */
    private static List<TourStopDTO> getTourStops(Connection conn, int tourId) throws SQLException {
        List<TourStopDTO> stops = new ArrayList<>();
        String query = "SELECT ts.id, ts.tour_id, ts.poi_id, ts.stop_order, ts.notes, p.name as poi_name, p.category as poi_category " +
                "FROM tour_stops ts " +
                "JOIN pois p ON p.id = ts.poi_id " +
                "WHERE ts.tour_id = ? " +
                "ORDER BY ts.stop_order";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, tourId);
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            stops.add(new TourStopDTO(
                    rs.getInt("id"),
                    rs.getInt("tour_id"),
                    rs.getInt("poi_id"),
                    rs.getString("poi_name"),
                    rs.getString("poi_category"),
                    rs.getInt("stop_order"),
                    rs.getString("notes")));
        }

        for (int i = 0; i < stops.size() - 1; i++) {
            Double d = PoiDistanceDAO.getDistance(conn, stops.get(i).getPoiId(), stops.get(i + 1).getPoiId());
            stops.get(i).setDistanceToNextMeters(d);
        }
        // Last stop has no "next" segment (no circle-back to first POI)
        return stops;
    }

    /**
     * Create a new tour.
     *
     * @return created tour ID, or -1 on failure
     */
    public static int createTour(Connection conn, TourDTO tour) throws SQLException {
        String query = "INSERT INTO tours (city_id, name, general_description, total_distance_meters) " +
                "VALUES (?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, tour.getCityId());
        stmt.setString(2, tour.getName());
        stmt.setString(3, tour.getDescription());
        setDistanceParam(stmt, 4, tour.getTotalDistanceMeters());

        int affected = stmt.executeUpdate();

        if (affected > 0) {
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int tourId = keys.getInt(1);
                System.out.println("TourDAO: Created tour with ID " + tourId);
                // Caller must add stops (with valid poi_id) to avoid FK violation on tour_stops.poi_id
                return tourId;
            }
        }

        return -1;
    }

    /**
     * Create a new tour (standalone).
     */
    public static int createTour(TourDTO tour) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return -1;
            return createTour(conn, tour);
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Update an existing tour (metadata only, not stops).
     */
    public static boolean updateTour(Connection conn, TourDTO tour) throws SQLException {
        String query = "UPDATE tours SET name = ?, general_description = ?, total_distance_meters = ? WHERE id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, tour.getName());
        stmt.setString(2, tour.getDescription());
        setDistanceParam(stmt, 3, tour.getTotalDistanceMeters());
        stmt.setInt(4, tour.getId());

        int affected = stmt.executeUpdate();
        System.out.println("TourDAO: Updated tour " + tour.getId() + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Delete a tour and all its stops.
     */
    public static boolean deleteTour(Connection conn, int tourId) throws SQLException {
        // Stops will be deleted by CASCADE
        String query = "DELETE FROM tours WHERE id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, tourId);

        int affected = stmt.executeUpdate();
        System.out.println("TourDAO: Deleted tour " + tourId + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Add a stop to a tour. Uses ON DUPLICATE KEY UPDATE so Save-then-Publish does not fail when
     * the same (tour_id, stop_order) was already inserted on a prior Save (draft).
     *
     * @return created stop ID, or existing stop ID on update, or -1 on failure
     */
    public static int addTourStop(Connection conn, TourStopDTO stop) throws SQLException {
        String query = "INSERT INTO tour_stops (tour_id, poi_id, stop_order, notes) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE poi_id = VALUES(poi_id), notes = VALUES(notes)";

        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, stop.getTourId());
        stmt.setInt(2, stop.getPoiId());
        stmt.setInt(3, stop.getStopOrder());
        stmt.setString(4, stop.getNotes() != null ? stop.getNotes() : "");

        int affected = stmt.executeUpdate();

        if (affected > 0) {
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int stopId = keys.getInt(1);
                System.out.println("TourDAO: Added/updated stop " + stopId + " for tour " + stop.getTourId());
                return stopId;
            }
            // ON DUPLICATE KEY UPDATE does not return generated key for updated row; affected is 2
            if (affected == 2) {
                Integer existingId = findStopIdByTourAndOrder(conn, stop.getTourId(), stop.getStopOrder());
                if (existingId != null) return existingId;
            }
        }

        return -1;
    }

    /** Find stop id by (tour_id, stop_order) for fallback when INSERT...ON DUPLICATE KEY UPDATE updates. */
    private static Integer findStopIdByTourAndOrder(Connection conn, int tourId, int stopOrder) throws SQLException {
        String sql = "SELECT id FROM tour_stops WHERE tour_id = ? AND stop_order = ? LIMIT 1";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, tourId);
        stmt.setInt(2, stopOrder);
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? rs.getInt("id") : null;
    }

    /**
     * Update a tour stop.
     */
    public static boolean updateTourStop(Connection conn, TourStopDTO stop) throws SQLException {
        String query = "UPDATE tour_stops SET poi_id = ?, stop_order = ?, notes = ? WHERE id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, stop.getPoiId());
        stmt.setInt(2, stop.getStopOrder());
        stmt.setString(3, stop.getNotes());
        stmt.setInt(4, stop.getId());

        int affected = stmt.executeUpdate();
        System.out.println("TourDAO: Updated stop " + stop.getId() + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Remove a tour stop.
     */
    public static boolean removeTourStop(Connection conn, int stopId) throws SQLException {
        String query = "DELETE FROM tour_stops WHERE id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, stopId);

        int affected = stmt.executeUpdate();
        System.out.println("TourDAO: Removed stop " + stopId + ", affected: " + affected);
        return affected > 0;
    }

    /**
     * Extract TourDTO from ResultSet.
     */
    private static TourDTO extractTour(ResultSet rs) throws SQLException {
        TourDTO t = new TourDTO(
                rs.getInt("id"),
                rs.getInt("city_id"),
                rs.getString("name"),
                rs.getString("general_description"));
        try {
            double d = rs.getDouble("total_distance_meters");
            if (!rs.wasNull()) t.setTotalDistanceMeters(d);
        } catch (SQLException ignored) { /* column may not exist before migration */ }
        return t;
    }

    /**
     * Get tour_id for a tour stop (e.g. to find affected tour when a stop is deleted).
     */
    public static Integer getTourIdForStop(Connection conn, int stopId) throws SQLException {
        String sql = "SELECT tour_id FROM tour_stops WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, stopId);
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? rs.getInt("tour_id") : null;
    }

    /**
     * Check if a POI exists.
     */
    public static boolean poiExists(Connection conn, int poiId) throws SQLException {
        String query = "SELECT COUNT(*) FROM pois WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, poiId);
        ResultSet rs = stmt.executeQuery();
        return rs.next() && rs.getInt(1) > 0;
    }

    private static void setDistanceParam(PreparedStatement stmt, int index, Double meters) throws SQLException {
        if (meters != null) stmt.setDouble(index, meters); else stmt.setNull(index, Types.DOUBLE);
    }

    /**
     * Recompute total route distance from consecutive POI-to-POI distances and update the tour.
     * Call after adding/updating/removing tour stops.
     */
    public static void recomputeAndUpdateTourDistance(Connection conn, int tourId) throws SQLException {
        List<TourStopDTO> stops = getTourStops(conn, tourId);
        if (stops.size() < 2) {
            updateTourDistance(conn, tourId, null);
            return;
        }
        double total = 0;
        for (int i = 1; i < stops.size(); i++) {
            int a = stops.get(i - 1).getPoiId();
            int b = stops.get(i).getPoiId();
            Double d = PoiDistanceDAO.getDistance(conn, a, b);
            if (d != null) total += d;
        }
        // No circle-back: total is sum of consecutive legs only
        updateTourDistance(conn, tourId, total);
    }

    /**
     * Set total_distance_meters for a tour (used after recomputing from stops).
     */
    public static void updateTourDistance(Connection conn, int tourId, Double totalMeters) throws SQLException {
        String sql = "UPDATE tours SET total_distance_meters = ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        setDistanceParam(stmt, 1, totalMeters);
        stmt.setInt(2, tourId);
        stmt.executeUpdate();
    }
}
