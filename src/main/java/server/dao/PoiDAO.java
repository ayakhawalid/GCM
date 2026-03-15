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
     * Get POIs linked to a map that are still draft (unapproved).
     * Used when building an approval request so the manager sees POIs the employee saved as draft.
     * Returns empty list if the approved column doesn't exist or on error.
     */
    public static List<Poi> getDraftPoisForMap(Connection conn, int mapId) throws SQLException {
        List<Poi> pois = new ArrayList<>();
        String query = "SELECT p.* FROM pois p " +
                "JOIN map_pois mp ON mp.poi_id = p.id AND mp.map_id = ? " +
                "WHERE mp.approved = 0 " +
                "ORDER BY mp.display_order";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            pois.add(extractPoi(rs));
        }
        return pois;
    }

    /**
     * Get POIs linked to a map that are approved (visible on the map).
     * Draft POIs (approved=0) are not returned, so they do not appear on the map until the manager approves or the manager publishes.
     */
    public static List<Poi> getPoisForMap(int mapId) {
        List<Poi> pois = new ArrayList<>();

        String query = "SELECT p.* FROM pois p " +
                "JOIN map_pois mp ON mp.poi_id = p.id AND mp.map_id = ? " +
                "WHERE mp.approved = 1 " +
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
            return getPoisForMapLegacy(mapId);
        }

        return pois;
    }

    /**
     * Get POIs for a map when loading in the editor: approved POIs plus draft POIs linked by this user (so the employee sees their own drafts).
     * Other users get only approved POIs via getPoisForMap. When userId is null or <= 0, returns approved only.
     * Each POI has draft=true when unapproved on this map (for UI tagging).
     */
    public static List<Poi> getPoisForMapForEditor(int mapId, int userId) {
        if (userId <= 0) return getPoisForMap(mapId);
        List<Poi> pois = new ArrayList<>();
        // Include approved POIs and draft POIs (approved=0) for this user; also include draft where linked_by_user_id IS NULL so manager's draft shows even if column wasn't set
        String query = "SELECT p.*, mp.approved as map_approved FROM pois p " +
                "JOIN map_pois mp ON mp.poi_id = p.id AND mp.map_id = ? " +
                "WHERE mp.approved = 1 OR (mp.approved = 0 AND (mp.linked_by_user_id = ? OR mp.linked_by_user_id IS NULL)) " +
                "ORDER BY mp.display_order";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return pois;
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, mapId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Poi poi = extractPoi(rs);
                int mapApproved = 0;
                try {
                    Object a = rs.getObject("map_approved");
                    if (a != null) {
                        if (a instanceof Number) mapApproved = ((Number) a).intValue();
                        else if (a instanceof Boolean) mapApproved = Boolean.TRUE.equals(a) ? 1 : 0;
                    }
                } catch (SQLException ignored) { }
                poi.setDraft(mapApproved == 0);
                pois.add(poi);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("linked_by_user_id")) {
                // DB has approved but not linked_by_user_id: still return draft POIs (approved=0) so new POIs show with [Draft]
                return getPoisForMapIncludingDrafts(mapId);
            }
            return getPoisForMapLegacy(mapId);
        }
        return pois;
    }

    /**
     * Get all POIs for a map (approved and draft) with draft flag set. Used when linked_by_user_id column is missing
     * so that newly saved POIs (approved=0) still appear in the editor with [Draft].
     */
    private static List<Poi> getPoisForMapIncludingDrafts(int mapId) {
        List<Poi> pois = new ArrayList<>();
        String query = "SELECT p.*, mp.approved as map_approved FROM pois p " +
                "JOIN map_pois mp ON mp.poi_id = p.id AND mp.map_id = ? " +
                "ORDER BY mp.display_order";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return pois;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, mapId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Poi poi = extractPoi(rs);
                    int mapApproved = 1;
                    try {
                        Object a = rs.getObject("map_approved");
                        if (a != null) {
                            if (a instanceof Number) mapApproved = ((Number) a).intValue();
                            else if (a instanceof Boolean) mapApproved = Boolean.TRUE.equals(a) ? 1 : 0;
                        }
                    } catch (SQLException ignored) { }
                    poi.setDraft(mapApproved == 0);
                    pois.add(poi);
                }
            }
        } catch (SQLException e) {
            return getPoisForMapLegacy(mapId);
        }
        return pois;
    }

    /** Fallback: try approved=1 only. Never returns draft POIs; if approved column missing, returns empty list. */
    private static List<Poi> getPoisForMapLegacy(int mapId) {
        List<Poi> pois = new ArrayList<>();
        String queryWithApproved = "SELECT p.* FROM pois p " +
                "JOIN map_pois mp ON mp.poi_id = p.id AND mp.map_id = ? AND mp.approved = 1 " +
                "ORDER BY mp.display_order";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return pois;
            try (PreparedStatement stmt = conn.prepareStatement(queryWithApproved)) {
                stmt.setInt(1, mapId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) pois.add(extractPoi(rs));
            }
        } catch (SQLException e) {
            // Column may not exist; do not return all POIs (would include draft). Return empty.
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
     * Return true if this POI has at least one approved link to a map. If false, POI is draft-only and can be deleted immediately on Save.
     */
    public static boolean hasPoiAnyApprovedLink(Connection conn, int poiId) throws SQLException {
        if (poiId <= 0) return true; // treat as approved to avoid immediate delete
        try {
            String sql = "SELECT 1 FROM map_pois WHERE poi_id = ? AND approved = 1 LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, poiId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("approved")) return false;
            throw e;
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
     * Link a POI to a map (approved = true for catalog visibility).
     */
    public static boolean linkPoiToMap(Connection conn, int mapId, int poiId, int displayOrder) throws SQLException {
        return linkPoiToMap(conn, mapId, poiId, displayOrder, true, 0);
    }

    /**
     * Link a POI to a map. When approved is false (draft), the POI is not counted/shown in catalog until approved.
     */
    public static boolean linkPoiToMap(Connection conn, int mapId, int poiId, int displayOrder, boolean approved) throws SQLException {
        return linkPoiToMap(conn, mapId, poiId, displayOrder, approved, 0);
    }

    /**
     * Link a POI to a map. When approved is false (draft), linkedByUserId is stored so only that user sees the draft in the editor.
     */
    public static boolean linkPoiToMap(Connection conn, int mapId, int poiId, int displayOrder, boolean approved, int linkedByUserId) throws SQLException {
        try {
            String query = "INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE display_order = VALUES(display_order), approved = VALUES(approved), linked_by_user_id = VALUES(linked_by_user_id)";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, mapId);
            stmt.setInt(2, poiId);
            stmt.setInt(3, displayOrder);
            stmt.setInt(4, approved ? 1 : 0);
            stmt.setObject(5, (!approved && linkedByUserId > 0) ? linkedByUserId : null, java.sql.Types.INTEGER);
            int affected = stmt.executeUpdate();
            System.out.println("PoiDAO: Linked POI " + poiId + " to map " + mapId + " (approved=" + approved + ", linkedBy=" + linkedByUserId + ")");
            return affected > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("approved") || e.getMessage().contains("linked_by_user_id"))) {
                // Fallback when linked_by_user_id column missing: still honour approved so draft stays draft
                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO map_pois (map_id, poi_id, display_order, approved) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE display_order = VALUES(display_order), approved = VALUES(approved)");
                stmt.setInt(1, mapId);
                stmt.setInt(2, poiId);
                stmt.setInt(3, displayOrder);
                stmt.setInt(4, approved ? 1 : 0);
                return stmt.executeUpdate() > 0;
            }
            throw e;
        }
    }

    /**
     * Approve all draft POI links on a map (set approved=1, linked_by_user_id=NULL).
     * Call when manager publishes so POIs they saved as draft become visible.
     */
    public static int approveAllDraftLinksForMap(Connection conn, int mapId) throws SQLException {
        try {
            String sql = "UPDATE map_pois SET approved = 1, linked_by_user_id = NULL WHERE map_id = ? AND (approved = 0 OR approved IS NULL)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, mapId);
            int n = stmt.executeUpdate();
            if (n > 0) System.out.println("PoiDAO: Approved " + n + " draft link(s) for map " + mapId);
            return n;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("approved") || e.getMessage().contains("linked_by_user_id")))
                return 0;
            throw e;
        }
    }

    /**
     * Remove all POI links for a map (e.g. when deleting a map).
     */
    public static int deleteAllLinksForMap(Connection conn, int mapId) throws SQLException {
        String query = "DELETE FROM map_pois WHERE map_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        return stmt.executeUpdate();
    }

    /**
     * Remove only approved (non-draft) POI links for a map whose poi_id is not in the keep set.
     * Used when syncing a tour map: drop approved links no longer in the tour, but never remove draft (approved=0) links.
     */
    public static int deleteApprovedLinksForMapNotIn(Connection conn, int mapId, java.util.Set<Integer> keepPoiIds) throws SQLException {
        if (keepPoiIds == null || keepPoiIds.isEmpty()) {
            try {
                String sql = "DELETE FROM map_pois WHERE map_id = ? AND approved = 1";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, mapId);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("approved")) return 0;
                throw e;
            }
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < keepPoiIds.size(); i++) {
            if (i > 0) placeholders.append(",?");
            else placeholders.append("?");
        }
        try {
            String sql = "DELETE FROM map_pois WHERE map_id = ? AND approved = 1 AND poi_id NOT IN (" + placeholders + ")";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, mapId);
            int idx = 2;
            for (Integer id : keepPoiIds) {
                stmt.setInt(idx++, id);
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("approved")) return 0;
            throw e;
        }
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
     * Count how many maps still link to this POI (in map_pois). Used after an unlink to decide if the POI
     * should be deleted fully from the city (so it does not appear in "Add existing POI to map").
     */
    public static int countMapsLinkedToPoi(Connection conn, int poiId) throws SQLException {
        String query = "SELECT COUNT(*) FROM map_pois WHERE poi_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, poiId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
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
     * Get all POI IDs linked to a map (from map_pois). Used when deleting a map so we can delete those POIs everywhere.
     */
    public static List<Integer> getPoiIdsLinkedToMap(Connection conn, int mapId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String query = "SELECT poi_id FROM map_pois WHERE map_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            ids.add(rs.getInt("poi_id"));
        }
        return ids;
    }

    /**
     * Delete all tour stops that reference this POI (so the POI can be removed from the system).
     */
    public static int deleteTourStopsForPoi(Connection conn, int poiId) throws SQLException {
        String query = "DELETE FROM tour_stops WHERE poi_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, poiId);
        return stmt.executeUpdate();
    }

    /**
     * Delete a POI completely: remove from tour_stops, unlink from all maps, then delete the POI row.
     * Use when deleting a map so its POIs are removed everywhere (including from tours).
     */
    public static boolean deletePoiCompletely(Connection conn, int poiId) throws SQLException {
        deleteTourStopsForPoi(conn, poiId);
        unlinkPoiFromAllMaps(conn, poiId);
        String query = "DELETE FROM pois WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, poiId);
        int affected = stmt.executeUpdate();
        if (affected > 0) {
            System.out.println("PoiDAO: Deleted POI " + poiId + " completely (tour stops + map links + row).");
        }
        return affected > 0;
    }

    /**
     * Get a POI by ID (uses its own connection).
     */
    public static Poi getPoiById(int poiId) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return null;
            return getPoiById(conn, poiId);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get a POI by ID using the given connection (for use inside transactions).
     */
    public static Poi getPoiById(Connection conn, int poiId) throws SQLException {
        String query = "SELECT * FROM pois WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, poiId);
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? extractPoi(rs) : null;
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
