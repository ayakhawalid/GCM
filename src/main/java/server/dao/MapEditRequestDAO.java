package server.dao;

import com.google.gson.Gson;
import common.dto.MapChanges;
import common.dto.MapEditRequestDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MapEditRequestDAO {

    private static final Gson gson = new Gson();

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

    private static MapEditRequestDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        MapEditRequestDTO dto = new MapEditRequestDTO();
        dto.setId(rs.getInt("id"));
        dto.setMapId(rs.getInt("map_id"));
        dto.setCityId(rs.getInt("city_id"));
        dto.setUserId(rs.getInt("user_id"));
        dto.setStatus(rs.getString("status"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));

        dto.setUsername(rs.getString("username"));
        dto.setMapName(rs.getString("map_name"));
        dto.setCityName(rs.getString("city_name"));

        String json = rs.getString("changes_json");
        if (json != null && !json.isEmpty()) {
            try {
                dto.setChanges(gson.fromJson(json, MapChanges.class));
            } catch (Exception e) {
                System.err.println("Error deserializing map changes: " + e.getMessage());
            }
        }

        return dto;
    }
}
