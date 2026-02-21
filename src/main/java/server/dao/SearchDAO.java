package server.dao;

import common.Poi;
import common.dto.CitySearchResult;
import common.dto.MapSummary;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for search operations.
 * Provides case-insensitive search across cities, maps, and POIs.
 */
public class SearchDAO {

    /**
     * Get all cities with their map counts (catalog view).
     * 
     * @return List of CitySearchResult with map summaries
     */
    public static List<CitySearchResult> getCitiesCatalog() {
        List<CitySearchResult> results = new ArrayList<>();

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) {
                System.out.println("SearchDAO: Database connection failed");
                return results;
            }

            String cityQuery = "SELECT c.id, c.name, c.description, c.price, " +
                    "(SELECT COUNT(*) FROM maps m2 WHERE m2.city_id = c.id AND (m2.approved = 1 OR m2.approved IS NULL)) as map_count " +
                    "FROM cities c WHERE (c.approved = 1 OR c.approved IS NULL) ORDER BY c.name";

            try (PreparedStatement stmt = conn.prepareStatement(cityQuery);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CitySearchResult cityResult = new CitySearchResult(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getDouble("price"));
                    List<MapSummary> maps = getMapsForCity(conn, rs.getInt("id"));
                    cityResult.setMaps(maps);
                    results.add(cityResult);
                }
            } catch (SQLException e) {
                // Fallback when approved column doesn't exist (no migration run yet)
                return getCitiesCatalogLegacy();
            }

            System.out.println("SearchDAO: Retrieved " + results.size() + " cities for catalog");

        } catch (SQLException e) {
            System.out.println("SearchDAO: Error getting cities catalog");
            e.printStackTrace();
        }

        return results;
    }

    /** Catalog without approved filter (when cities/maps tables have no approved column). */
    private static List<CitySearchResult> getCitiesCatalogLegacy() {
        List<CitySearchResult> results = new ArrayList<>();
        String cityQuery = "SELECT c.id, c.name, c.description, c.price, " +
                "(SELECT COUNT(*) FROM maps WHERE city_id = c.id) as map_count " +
                "FROM cities c ORDER BY c.name";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return results;
            try (PreparedStatement stmt = conn.prepareStatement(cityQuery);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CitySearchResult cityResult = new CitySearchResult(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getDouble("price"));
                    List<MapSummary> maps = getMapsForCityLegacy(conn, rs.getInt("id"));
                    cityResult.setMaps(maps);
                    results.add(cityResult);
                }
            }
            System.out.println("SearchDAO: Retrieved " + results.size() + " cities for catalog (legacy)");
        } catch (SQLException e) {
            System.out.println("SearchDAO: Error getting cities catalog (legacy)");
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Search maps by city name (case-insensitive LIKE search).
     * 
     * @param cityName The city name to search for
     * @return List of CitySearchResult matching the search
     */
    public static List<CitySearchResult> searchByCityName(String cityName) {
        List<CitySearchResult> results = new ArrayList<>();

        if (cityName == null || cityName.trim().isEmpty()) {
            return results;
        }

        String query = "SELECT c.id, c.name, c.description, c.price " +
                "FROM cities c " +
                "WHERE LOWER(TRIM(c.name)) LIKE ? AND (c.approved = 1 OR c.approved IS NULL) " +
                "ORDER BY c.name";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return results;

            String pattern = "%" + cityName.trim().toLowerCase() + "%";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, pattern);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    CitySearchResult cityResult = new CitySearchResult(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getDouble("price"));
                    List<MapSummary> maps = getMapsForCity(conn, rs.getInt("id"));
                    cityResult.setMaps(maps);
                    results.add(cityResult);
                }
            } catch (SQLException e) {
                // Fallback when approved column doesn't exist
                return searchByCityNameLegacy(cityName);
            }

            System.out.println("SearchDAO: Found " + results.size() + " cities matching '" + cityName + "'");

        } catch (SQLException e) {
            System.out.println("SearchDAO: Error searching by city name");
            e.printStackTrace();
        }

        return results;
    }

    /** Search by city name without approved filter (when column doesn't exist). */
    private static List<CitySearchResult> searchByCityNameLegacy(String cityName) {
        List<CitySearchResult> results = new ArrayList<>();
        String pattern = "%" + cityName.trim().toLowerCase() + "%";
        String query = "SELECT c.id, c.name, c.description, c.price FROM cities c " +
                "WHERE LOWER(TRIM(c.name)) LIKE ? ORDER BY c.name";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return results;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, pattern);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    CitySearchResult cityResult = new CitySearchResult(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getDouble("price"));
                    cityResult.setMaps(getMapsForCityLegacy(conn, rs.getInt("id")));
                    results.add(cityResult);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Search maps by POI name (case-insensitive).
     * Returns cities containing maps that have matching POIs.
     * 
     * @param poiName The POI name to search for
     * @return List of CitySearchResult with maps containing matching POIs
     */
    public static List<CitySearchResult> searchByPoiName(String poiName) {
        List<CitySearchResult> results = new ArrayList<>();

        if (poiName == null || poiName.trim().isEmpty()) {
            return results;
        }

        // First, find all maps that contain matching POIs
        String query = "SELECT DISTINCT c.id as city_id, c.name as city_name, c.description as city_desc, c.price, " +
                "       m.id as map_id, m.name as map_name, m.short_description as map_desc " +
                "FROM cities c " +
                "JOIN maps m ON m.city_id = c.id " +
                "JOIN map_pois mp ON mp.map_id = m.id AND (mp.approved = 1 OR mp.approved IS NULL) " +
                "JOIN pois p ON p.id = mp.poi_id " +
                "WHERE LOWER(TRIM(p.name)) LIKE ? " +
                "ORDER BY c.name, m.name";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return results;

            String pattern = "%" + poiName.trim().toLowerCase() + "%";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, pattern);
            ResultSet rs = stmt.executeQuery();

            // Group results by city
            Map<Integer, CitySearchResult> cityMap = new HashMap<>();

            while (rs.next()) {
                int cityId = rs.getInt("city_id");

                CitySearchResult cityResult = cityMap.get(cityId);
                if (cityResult == null) {
                    cityResult = new CitySearchResult(
                            cityId,
                            rs.getString("city_name"),
                            rs.getString("city_desc"),
                            rs.getDouble("price"));
                    cityMap.put(cityId, cityResult);
                }

                // Get POI and tour counts for this map
                int mapId = rs.getInt("map_id");
                int poiCount = getPoiCountForMap(conn, mapId);
                int tourCount = getTourCountForCity(conn, cityId);

                MapSummary mapSummary = new MapSummary(
                        mapId,
                        rs.getString("map_name"),
                        rs.getString("map_desc"),
                        poiCount,
                        tourCount);

                cityResult.addMap(mapSummary);
            }

            results.addAll(cityMap.values());
            System.out.println("SearchDAO: Found " + results.size() + " cities with POI matching '" + poiName + "'");

        } catch (SQLException e) {
            System.out.println("SearchDAO: Error searching by POI name");
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Search maps by both city name AND POI name.
     * Returns only maps in matching cities that contain matching POIs.
     * 
     * @param cityName The city name to search for
     * @param poiName  The POI name to search for
     * @return List of CitySearchResult with maps matching both criteria
     */
    public static List<CitySearchResult> searchByCityAndPoi(String cityName, String poiName) {
        List<CitySearchResult> results = new ArrayList<>();

        if ((cityName == null || cityName.trim().isEmpty()) &&
                (poiName == null || poiName.trim().isEmpty())) {
            return results;
        }

        // If only one criterion is provided, delegate to appropriate method
        if (poiName == null || poiName.trim().isEmpty()) {
            return searchByCityName(cityName);
        }
        if (cityName == null || cityName.trim().isEmpty()) {
            return searchByPoiName(poiName);
        }

        String query = "SELECT DISTINCT c.id as city_id, c.name as city_name, c.description as city_desc, c.price, " +
                "       m.id as map_id, m.name as map_name, m.short_description as map_desc " +
                "FROM cities c " +
                "JOIN maps m ON m.city_id = c.id " +
                "JOIN map_pois mp ON mp.map_id = m.id AND (mp.approved = 1 OR mp.approved IS NULL) " +
                "JOIN pois p ON p.id = mp.poi_id " +
                "WHERE LOWER(TRIM(c.name)) LIKE ? " +
                "  AND LOWER(TRIM(p.name)) LIKE ? " +
                "ORDER BY c.name, m.name";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return results;

            String cityPattern = "%" + cityName.trim().toLowerCase() + "%";
            String poiPattern = "%" + (poiName != null ? poiName.trim() : "").toLowerCase() + "%";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, cityPattern);
            stmt.setString(2, poiPattern);
            ResultSet rs = stmt.executeQuery();

            // Group results by city
            Map<Integer, CitySearchResult> cityMap = new HashMap<>();

            while (rs.next()) {
                int cityId = rs.getInt("city_id");

                CitySearchResult cityResult = cityMap.get(cityId);
                if (cityResult == null) {
                    cityResult = new CitySearchResult(
                            cityId,
                            rs.getString("city_name"),
                            rs.getString("city_desc"),
                            rs.getDouble("price"));
                    cityMap.put(cityId, cityResult);
                }

                int mapId = rs.getInt("map_id");
                int poiCount = getPoiCountForMap(conn, mapId);
                int tourCount = getTourCountForCity(conn, cityId);

                MapSummary mapSummary = new MapSummary(
                        mapId,
                        rs.getString("map_name"),
                        rs.getString("map_desc"),
                        poiCount,
                        tourCount);

                cityResult.addMap(mapSummary);
            }

            results.addAll(cityMap.values());
            System.out.println("SearchDAO: Found " + results.size() + " cities matching city='" + cityName
                    + "' AND poi='" + poiName + "'");

        } catch (SQLException e) {
            System.out.println("SearchDAO: Error searching by city and POI");
            e.printStackTrace();
        }

        return results;
    }

    // ==================== Helper Methods ====================

    /**
     * Get all maps for a city with summary info.
     */
    private static List<MapSummary> getMapsForCity(Connection conn, int cityId) throws SQLException {
        List<MapSummary> maps = new ArrayList<>();

        String query = "SELECT m.id, m.name, m.short_description, " +
                "(SELECT COUNT(*) FROM map_pois mp WHERE mp.map_id = m.id AND (mp.approved = 1 OR mp.approved IS NULL)) as poi_count " +
                "FROM maps m WHERE m.city_id = ? AND (m.approved = 1 OR m.approved IS NULL) ORDER BY m.name";

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, cityId);
        ResultSet rs = stmt.executeQuery();

        int tourCount = getTourCountForCity(conn, cityId);

        while (rs.next()) {
            maps.add(new MapSummary(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("short_description"),
                    rs.getInt("poi_count"),
                    tourCount));
        }

        return maps;
    }

    /** Maps for city without approved filter (when column doesn't exist). */
    private static List<MapSummary> getMapsForCityLegacy(Connection conn, int cityId) throws SQLException {
        List<MapSummary> maps = new ArrayList<>();
        String query = "SELECT m.id, m.name, m.short_description, " +
                "(SELECT COUNT(*) FROM map_pois WHERE map_id = m.id) as poi_count " +
                "FROM maps m WHERE m.city_id = ? ORDER BY m.name";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, cityId);
        ResultSet rs = stmt.executeQuery();
        int tourCount = getTourCountForCity(conn, cityId);
        while (rs.next()) {
            maps.add(new MapSummary(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("short_description"),
                    rs.getInt("poi_count"),
                    tourCount));
        }
        return maps;
    }

    /**
     * Get POI count for a specific map.
     */
    private static int getPoiCountForMap(Connection conn, int mapId) throws SQLException {
        String query = "SELECT COUNT(*) FROM map_pois WHERE map_id = ? AND (approved = 1 OR approved IS NULL)";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    /**
     * Get tour count for a city.
     */
    private static int getTourCountForCity(Connection conn, int cityId) throws SQLException {
        String query = "SELECT COUNT(*) FROM tours WHERE city_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, cityId);
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }

    /**
     * Get POIs for a specific map.
     */
    public static List<Poi> getPoisForMap(int mapId) {
        List<Poi> pois = new ArrayList<>();

        String query = "SELECT p.* FROM pois p " +
                "JOIN map_pois mp ON mp.poi_id = p.id " +
                "WHERE mp.map_id = ? AND (mp.approved = 1 OR mp.approved IS NULL) " +
                "ORDER BY mp.display_order";

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return pois;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                pois.add(new Poi(
                        rs.getInt("id"),
                        rs.getInt("city_id"),
                        rs.getString("name"),
                        rs.getString("location"),
                        rs.getString("category"),
                        rs.getString("short_explanation"),
                        rs.getBoolean("is_accessible")));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return pois;
    }
}
