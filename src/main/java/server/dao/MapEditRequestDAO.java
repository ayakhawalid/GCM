package server.dao;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import common.Poi;
import common.dto.MapChanges;
import common.dto.MapEditRequestDTO;
import common.dto.TourDTO;
import common.dto.TourStopDTO;
import server.DBConnector;

public class MapEditRequestDAO {

    private static final Gson gson = new Gson();
    private static final Type LIST_POI = new TypeToken<List<Poi>>(){}.getType();
    private static final Type LIST_TOUR = new TypeToken<List<TourDTO>>(){}.getType();
    private static final Type LIST_TOUR_STOP = new TypeToken<List<TourStopDTO>>(){}.getType();

    static {
        createTable();
    }

    private static void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS map_edit_requests (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "map_id INT, " +
                "city_id INT, " +
                "user_id INT, " +
                "changes_json MEDIUMTEXT, " +
                "status VARCHAR(20) DEFAULT 'PENDING', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX (status)" +
                ")";

        try (Connection conn = DBConnector.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error creating map_edit_requests table: " + e.getMessage());
        }
    }

    public static int createRequest(Connection conn, int mapId, int cityId, int userId, MapChanges changes)
            throws SQLException {
        return createRequest(conn, mapId, cityId, userId, changes, changes.isDraft() ? "DRAFT" : "PENDING");
    }

    public static int createRequest(Connection conn, int mapId, int cityId, int userId, MapChanges changes, String status)
            throws SQLException {
        // Store display names so pending list shows real names even if city/map is deleted later
        if (cityId > 0 && (changes.getDisplayCityName() == null || changes.getDisplayCityName().isEmpty())) {
            String name = getCityName(conn, cityId);
            if (name != null) changes.setDisplayCityName(name);
        }
        if (mapId > 0 && (changes.getDisplayMapName() == null || changes.getDisplayMapName().isEmpty())) {
            String name = getMapName(conn, mapId);
            if (name != null) changes.setDisplayMapName(name);
        }
        String json = gson.toJson(changes);
        String sql = "INSERT INTO map_edit_requests (map_id, city_id, user_id, changes_json, status) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setObject(1, mapId > 0 ? mapId : null, Types.INTEGER);
            stmt.setObject(2, cityId > 0 ? cityId : null, Types.INTEGER);
            stmt.setInt(3, userId);
            stmt.setString(4, json);
            stmt.setString(5, status != null ? status : "PENDING");

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    public static List<MapEditRequestDTO> getPendingRequests() {
        List<MapEditRequestDTO> requests = new ArrayList<>();
        String sql = "SELECT r.*, u.username, m.name as map_name, c.name as city_name " +
                "FROM map_edit_requests r " +
                "LEFT JOIN users u ON r.user_id = u.id " +
                "LEFT JOIN maps m ON r.map_id = m.id " +
                "LEFT JOIN cities c ON r.city_id = c.id " +
                "WHERE r.status = 'PENDING' " +
                "ORDER BY r.created_at ASC";

        System.out.println("MapEditRequestDAO: Fetching pending requests...");

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                requests.add(mapResultSetToDTO(rs));
            }
            System.out.println("MapEditRequestDAO: Found " + requests.size() + " pending requests");
        } catch (SQLException e) {
            System.err.println("Error getting pending requests: " + e.getMessage());
            e.printStackTrace();
        }
        return requests;
    }

    /**
     * Get PENDING map edit requests for a specific editor + city scope.
     * Used by manager approval flow (map_versions) to apply deletions that were
     * stored as map_edit_requests.
     *
     * @param conn open DB connection (transaction-aware)
     * @param userId editor user id
     * @param cityId city scope id
     */
    public static List<MapEditRequestDTO> getPendingRequestsForUserAndCity(Connection conn, int userId, int cityId)
            throws SQLException {
        List<MapEditRequestDTO> requests = new ArrayList<>();
        if (userId <= 0 || cityId <= 0) return requests;

        String sql = "SELECT r.*, u.username, m.name as map_name, c.name as city_name " +
                "FROM map_edit_requests r " +
                "LEFT JOIN users u ON r.user_id = u.id " +
                "LEFT JOIN maps m ON r.map_id = m.id " +
                "LEFT JOIN cities c ON r.city_id = c.id " +
                "WHERE r.status = 'PENDING' AND r.user_id = ? AND r.city_id = ? " +
                "ORDER BY r.created_at ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapResultSetToDTO(rs));
                }
            }
        }
        return requests;
    }

    /**
     * Get map IDs that have a PENDING request by this user for this city.
     * Used to show "(waiting for approval)" in the map editor for the employee.
     */
    public static Set<Integer> getMapIdsWithPendingRequestByUser(int userId, int cityId) {
        Set<Integer> mapIds = new HashSet<>();
        if (userId <= 0) return mapIds;
        String sql = "SELECT map_id FROM map_edit_requests WHERE user_id = ? AND status = 'PENDING' AND city_id = ? AND map_id IS NOT NULL AND map_id > 0";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int mid = rs.getInt("map_id");
                if (mid > 0) mapIds.add(mid);
            }
        } catch (SQLException e) {
            System.err.println("MapEditRequestDAO: getMapIdsWithPendingRequestByUser failed: " + e.getMessage());
        }
        return mapIds;
    }

    /**
     * Get tour IDs that appear in a PENDING request by this user for this city (added or updated tours).
     * Used to show "(waiting for approval)" in the map editor for the employee.
     */
    public static Set<Integer> getTourIdsWithPendingRequestByUser(int userId, int cityId) {
        Set<Integer> tourIds = new HashSet<>();
        if (userId <= 0) return tourIds;
        String sql = "SELECT changes_json FROM map_edit_requests WHERE user_id = ? AND status = 'PENDING' AND city_id = ?";
        try (Connection conn = DBConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, cityId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String json = rs.getString("changes_json");
                if (json == null || json.isEmpty()) continue;
                MapChanges ch = deserializeMapChanges(json);
                if (ch == null) continue;
                if (ch.getAddedTours() != null) {
                    for (common.dto.TourDTO t : ch.getAddedTours()) {
                        if (t.getId() > 0) tourIds.add(t.getId());
                    }
                }
                if (ch.getUpdatedTours() != null) {
                    for (common.dto.TourDTO t : ch.getUpdatedTours()) {
                        if (t.getId() > 0) tourIds.add(t.getId());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("MapEditRequestDAO: getTourIdsWithPendingRequestByUser failed: " + e.getMessage());
        }
        return tourIds;
    }

    /**
     * Get the DRAFT map edit request for this map and user, if any.
     * Used to restore pending unlinks/deletes when loading map content for the editor.
     */
    public static MapEditRequestDTO getDraftRequestForMapUser(int mapId, int userId) {
        String sql = "SELECT r.*, u.username, m.name as map_name, c.name as city_name " +
                "FROM map_edit_requests r " +
                "LEFT JOIN users u ON r.user_id = u.id " +
                "LEFT JOIN maps m ON r.map_id = m.id " +
                "LEFT JOIN cities c ON r.city_id = c.id " +
                "WHERE r.map_id = ? AND r.user_id = ? AND r.status = 'DRAFT' " +
                "ORDER BY r.created_at DESC LIMIT 1";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mapId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDTO(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting draft request: " + e.getMessage());
        }
        return null;
    }

    /**
     * Delete any existing DRAFT for this map+user, then insert a new one if there are pending unlinks/deletes.
     * Used when employee saves as draft with pending unlinks/deletes.
     */
    public static void upsertDraftRequest(Connection conn, int mapId, int cityId, int userId, MapChanges changes) throws SQLException {
        String deleteSql = "DELETE FROM map_edit_requests WHERE map_id = ? AND user_id = ? AND status = 'DRAFT'";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, mapId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
        boolean hasUnlinks = changes.getPoiMapUnlinks() != null && !changes.getPoiMapUnlinks().isEmpty();
        boolean hasDeletes = changes.getDeletedPoiIds() != null && !changes.getDeletedPoiIds().isEmpty();
        boolean hasTourAdds = changes.getAddedTours() != null && !changes.getAddedTours().isEmpty();
        boolean hasTourDeletes = changes.getDeletedTourIds() != null && !changes.getDeletedTourIds().isEmpty();
        boolean hasTourUpdates = changes.getUpdatedTours() != null && !changes.getUpdatedTours().isEmpty();
        boolean hasUpdatedPois = changes.getUpdatedPois() != null && !changes.getUpdatedPois().isEmpty();
        // Map info edits (name/description) should also be stored in the draft record so the editor keeps the changes after reload.
        boolean hasMapInfoEdits = (changes.getNewMapName() != null) || (changes.getNewMapDescription() != null);
        boolean hasStopChanges = (changes.getAddedStops() != null && !changes.getAddedStops().isEmpty()) ||
                (changes.getUpdatedStops() != null && !changes.getUpdatedStops().isEmpty()) ||
                (changes.getDeletedStopIds() != null && !changes.getDeletedStopIds().isEmpty());
        boolean hasMapDeletes = changes.getDeletedMapIds() != null && !changes.getDeletedMapIds().isEmpty();
        boolean hasCityDeletes = changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty();
        if (hasUnlinks || hasDeletes || hasTourAdds || hasTourDeletes || hasTourUpdates || hasStopChanges || hasMapDeletes || hasCityDeletes
                || hasUpdatedPois || hasMapInfoEdits) {
            createRequest(conn, mapId, cityId, userId, changes, "DRAFT");
        }
    }

    /**
     * Upsert a user-level draft (no map/city context). Used when manager saves "delete city" only.
     * Stores one row per user with map_id NULL, city_id NULL.
     */
    public static void upsertUserDraft(Connection conn, int userId, MapChanges changes) throws SQLException {
        String deleteSql = "DELETE FROM map_edit_requests WHERE user_id = ? AND map_id IS NULL AND status = 'DRAFT'";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
        boolean hasCityDeletes = changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty();
        if (hasCityDeletes) {
            createRequest(conn, 0, 0, userId, changes, "DRAFT");
        }
    }

    /**
     * Delete the user-level DRAFT (map_id IS NULL) for this user. Call after Publish applies city deletions.
     */
    public static void deleteUserDraft(Connection conn, int userId) throws SQLException {
        String sql = "DELETE FROM map_edit_requests WHERE user_id = ? AND map_id IS NULL AND status = 'DRAFT'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    /**
     * Get the user-level DRAFT (map_id IS NULL) for this user, if any. Used to restore "delete city" draft.
     */
    public static MapEditRequestDTO getDraftRequestForUser(int userId) {
        String sql = "SELECT r.*, u.username, m.name as map_name, c.name as city_name " +
                "FROM map_edit_requests r " +
                "LEFT JOIN users u ON r.user_id = u.id " +
                "LEFT JOIN maps m ON r.map_id = m.id " +
                "LEFT JOIN cities c ON r.city_id = c.id " +
                "WHERE r.map_id IS NULL AND r.user_id = ? AND r.status = 'DRAFT' " +
                "ORDER BY r.created_at DESC LIMIT 1";
        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDTO(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting user draft: " + e.getMessage());
        }
        return null;
    }

    /**
     * Delete DRAFT request for this map+user when they submit for manager approval.
     */
    public static void deleteDraftForMapUser(Connection conn, int mapId, int userId) throws SQLException {
        String sql = "DELETE FROM map_edit_requests WHERE map_id = ? AND user_id = ? AND status = 'DRAFT'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mapId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }

    public static MapEditRequestDTO getRequest(int id) {
        String sql = "SELECT r.*, u.username, m.name as map_name, c.name as city_name " +
                "FROM map_edit_requests r " +
                "LEFT JOIN users u ON r.user_id = u.id " +
                "LEFT JOIN maps m ON r.map_id = m.id " +
                "LEFT JOIN cities c ON r.city_id = c.id " +
                "WHERE r.id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDTO(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting request: " + e.getMessage());
        }
        return null;
    }

    public static boolean updateStatus(Connection conn, int id, String status) throws SQLException {
        String sql = "UPDATE map_edit_requests SET status = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Get tour IDs that appear in REJECTED requests from this user for this map/city.
     * Used so we block resubmitting the same delete-tour request until the employee performs the action again.
     */
    public static Set<Integer> getRejectedDeletedTourIdsForUserAndScope(Connection conn, int userId, int mapId, int cityId) throws SQLException {
        Set<Integer> out = new HashSet<>();
        String sql = "SELECT changes_json FROM map_edit_requests WHERE user_id = ? AND status = 'REJECTED' AND COALESCE(map_id, 0) = ? AND COALESCE(city_id, 0) = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, mapId > 0 ? mapId : 0);
            stmt.setInt(3, cityId > 0 ? cityId : 0);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String json = rs.getString("changes_json");
                if (json == null || json.isEmpty()) continue;
                MapChanges ch = deserializeMapChanges(json);
                if (ch != null && ch.getDeletedTourIds() != null) {
                    for (Integer tid : ch.getDeletedTourIds()) {
                        if (tid != null && tid > 0) out.add(tid);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Get POI IDs that appear in REJECTED requests from this user for this map/city.
     * Used so we block resubmitting the same delete-POI request until the employee performs the action again.
     */
    public static Set<Integer> getRejectedDeletedPoiIdsForUserAndScope(Connection conn, int userId, int mapId, int cityId) throws SQLException {
        Set<Integer> out = new HashSet<>();
        String sql = "SELECT changes_json FROM map_edit_requests WHERE user_id = ? AND status = 'REJECTED' AND COALESCE(map_id, 0) = ? AND COALESCE(city_id, 0) = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, mapId > 0 ? mapId : 0);
            stmt.setInt(3, cityId > 0 ? cityId : 0);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String json = rs.getString("changes_json");
                if (json == null || json.isEmpty()) continue;
                MapChanges ch = deserializeMapChanges(json);
                if (ch != null && ch.getDeletedPoiIds() != null) {
                    for (Integer pid : ch.getDeletedPoiIds()) {
                        if (pid != null && pid > 0) out.add(pid);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Delete PENDING requests from this user for the given (mapId, cityId) pairs.
     * Used so that when an employee sends again, we replace previous pending requests for the same scope.
     */
    public static void deletePendingRequestsByUserForPairs(Connection conn, int userId,
            List<Integer> mapIds, List<Integer> cityIds) throws SQLException {
        if (mapIds == null || cityIds == null || mapIds.size() != cityIds.size() || mapIds.isEmpty()) return;
        StringBuilder sb = new StringBuilder("DELETE FROM map_edit_requests WHERE user_id = ? AND status = 'PENDING' AND (");
        for (int i = 0; i < mapIds.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("(COALESCE(map_id, 0) = ? AND COALESCE(city_id, 0) = ?)");
        }
        sb.append(")");
        try (PreparedStatement stmt = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            stmt.setInt(idx++, userId);
            for (int i = 0; i < mapIds.size(); i++) {
                stmt.setInt(idx++, mapIds.get(i) != null && mapIds.get(i) > 0 ? mapIds.get(i) : 0);
                stmt.setInt(idx++, cityIds.get(i) != null && cityIds.get(i) > 0 ? cityIds.get(i) : 0);
            }
            stmt.executeUpdate();
        }
    }

    private static String getCityName(Connection conn, int cityId) {
        try {
            String sql = "SELECT name FROM cities WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("name") : null;
        } catch (SQLException e) {
            return null;
        }
    }

    private static String getMapName(Connection conn, int mapId) {
        try {
            String sql = "SELECT name FROM maps WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("name") : null;
        } catch (SQLException e) {
            return null;
        }
    }

    private static MapEditRequestDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        MapEditRequestDTO dto = new MapEditRequestDTO();
        dto.setId(rs.getInt("id"));
        dto.setMapId(rs.getInt("map_id"));
        dto.setCityId(rs.getInt("city_id"));
        dto.setUserId(rs.getInt("user_id"));
        dto.setStatus(rs.getString("status"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));

        dto.setUsername(rs.getString("username"));
        String mapName = rs.getString("map_name");
        String cityName = rs.getString("city_name");
        dto.setMapName(mapName);
        dto.setCityName(cityName);

        String json = rs.getString("changes_json");
        if (json != null && !json.isEmpty()) {
            try {
                MapChanges ch = deserializeMapChanges(json);
                dto.setChanges(ch);
                // Use display names from stored JSON when JOIN returned null (e.g. city/map already deleted)
                if ((cityName == null || cityName.isEmpty()) && ch != null && ch.getDisplayCityName() != null && !ch.getDisplayCityName().isEmpty())
                    dto.setCityName(ch.getDisplayCityName());
                if ((mapName == null || mapName.isEmpty()) && ch != null && ch.getDisplayMapName() != null && !ch.getDisplayMapName().isEmpty())
                    dto.setMapName(ch.getDisplayMapName());
            } catch (Exception e) {
                System.err.println("Error deserializing map changes: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return dto;
    }

    /**
     * Deserialize MapChanges from JSON with correct types for List&lt;Poi&gt; so POI details
     * (name, category, description, etc.) are preserved for the manager approval view.
     */
    private static MapChanges deserializeMapChanges(String json) {
        MapChanges changes = gson.fromJson(json, MapChanges.class);
        if (changes == null) return null;
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if (obj != null) {
                if (obj.has("addedPois") && obj.get("addedPois").isJsonArray()) {
                    List<Poi> added = gson.fromJson(obj.get("addedPois"), LIST_POI);
                    if (added != null) {
                        changes.getAddedPois().clear();
                        changes.getAddedPois().addAll(added);
                    }
                }
                if (obj.has("updatedPois") && obj.get("updatedPois").isJsonArray()) {
                    List<Poi> updated = gson.fromJson(obj.get("updatedPois"), LIST_POI);
                    if (updated != null) {
                        changes.getUpdatedPois().clear();
                        changes.getUpdatedPois().addAll(updated);
                    }
                }
                if (obj.has("addedTours") && obj.get("addedTours").isJsonArray()) {
                    List<TourDTO> added = gson.fromJson(obj.get("addedTours"), LIST_TOUR);
                    if (added != null) {
                        changes.getAddedTours().clear();
                        changes.getAddedTours().addAll(added);
                    }
                }
                if (obj.has("updatedTours") && obj.get("updatedTours").isJsonArray()) {
                    List<TourDTO> updated = gson.fromJson(obj.get("updatedTours"), LIST_TOUR);
                    if (updated != null) {
                        changes.getUpdatedTours().clear();
                        changes.getUpdatedTours().addAll(updated);
                    }
                }
                if (obj.has("addedStops") && obj.get("addedStops").isJsonArray()) {
                    List<TourStopDTO> added = gson.fromJson(obj.get("addedStops"), LIST_TOUR_STOP);
                    if (added != null) {
                        changes.getAddedStops().clear();
                        changes.getAddedStops().addAll(added);
                    }
                }
                if (obj.has("updatedStops") && obj.get("updatedStops").isJsonArray()) {
                    List<TourStopDTO> updated = gson.fromJson(obj.get("updatedStops"), LIST_TOUR_STOP);
                    if (updated != null) {
                        changes.getUpdatedStops().clear();
                        changes.getUpdatedStops().addAll(updated);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("MapEditRequestDAO: Fallback list deserialization failed: " + e.getMessage());
        }
        return changes;
    }
}
