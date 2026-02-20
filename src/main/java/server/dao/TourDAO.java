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
        List<TourDTO> tours = new ArrayList<>();

        String query = "SELECT * FROM tours WHERE city_id = ? ORDER BY name";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return tours;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                TourDTO tour = extractTour(rs);
                // Load stops for this tour
                List<TourStopDTO> stops = getTourStops(conn, tour.getId());
                tour.setStops(stops);
                tours.add(tour);
            }

            System.out.println("TourDAO: Retrieved " + tours.size() + " tours for city " + cityId);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tours;
    }

    /**
     * Get a tour by ID with stops.
     */
    public static TourDTO getTourById(int tourId) {
        String query = "SELECT * FROM tours WHERE id = ?";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return null;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, tourId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                TourDTO tour = extractTour(rs);
                tour.setStops(getTourStops(conn, tourId));
                return tour;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get stops for a tour.
     */
    private static List<TourStopDTO> getTourStops(Connection conn, int tourId) throws SQLException {
        List<TourStopDTO> stops = new ArrayList<>();

        String query = "SELECT ts.*, p.name as poi_name, p.category as poi_category " +
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
                    rs.getInt("recommended_duration_minutes"),
                    rs.getString("notes")));
        }

        return stops;
    }

    /**
     * Create a new tour.
     * 
     * @return created tour ID, or -1 on failure
     */
    public static int createTour(Connection conn, TourDTO tour) throws SQLException {
        String query = "INSERT INTO tours (city_id, name, general_description, estimated_duration_minutes) " +
                "VALUES (?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, tour.getCityId());
        stmt.setString(2, tour.getName());
        stmt.setString(3, tour.getDescription());
        stmt.setInt(4, tour.getEstimatedDurationMinutes());

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
        String query = "UPDATE tours SET name = ?, general_description = ?, " +
                "estimated_duration_minutes = ? WHERE id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, tour.getName());
        stmt.setString(2, tour.getDescription());
        stmt.setInt(3, tour.getEstimatedDurationMinutes());
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
     * Add a stop to a tour.
     * 
     * @return created stop ID, or -1 on failure
     */
    public static int addTourStop(Connection conn, TourStopDTO stop) throws SQLException {
        String query = "INSERT INTO tour_stops (tour_id, poi_id, stop_order, recommended_duration_minutes, notes) " +
                "VALUES (?, ?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, stop.getTourId());
        stmt.setInt(2, stop.getPoiId());
        stmt.setInt(3, stop.getStopOrder());
        stmt.setInt(4, stop.getDurationMinutes());
        stmt.setString(5, stop.getNotes());

        int affected = stmt.executeUpdate();

        if (affected > 0) {
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int stopId = keys.getInt(1);
                System.out.println("TourDAO: Added stop " + stopId + " to tour " + stop.getTourId());
                return stopId;
            }
        }

        return -1;
    }

    /**
     * Update a tour stop.
     */
    public static boolean updateTourStop(Connection conn, TourStopDTO stop) throws SQLException {
        String query = "UPDATE tour_stops SET poi_id = ?, stop_order = ?, " +
                "recommended_duration_minutes = ?, notes = ? WHERE id = ?";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, stop.getPoiId());
        stmt.setInt(2, stop.getStopOrder());
        stmt.setInt(3, stop.getDurationMinutes());
        stmt.setString(4, stop.getNotes());
        stmt.setInt(5, stop.getId());

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
        return new TourDTO(
                rs.getInt("id"),
                rs.getInt("city_id"),
                rs.getString("name"),
                rs.getString("general_description"),
                rs.getInt("estimated_duration_minutes"));
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
}
