package server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import common.Poi;
import common.dto.MapChanges;
import common.dto.MapContent;
import common.dto.MapSummary;
import common.dto.TourDTO;
import common.dto.TourSegmentDTO;
import common.dto.TourStopDTO;
import server.DBConnector;

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
        String poiCountSubquery = "(SELECT COUNT(*) FROM map_pois mp WHERE mp.map_id = m.id AND mp.approved = 1)";
        if (currentUserId > 0) {
            // Show approved maps to all; show draft (approved=0 or NULL) only to creator. Save never "publishes".
            query = "SELECT m.id, m.city_id, c.description as city_description, m.name, m.short_description, COALESCE(m.approved, 0) as approved, COALESCE(m.created_by, 0) as created_by, COALESCE(m.tour_id, 0) as tour_id, " + poiCountSubquery + " as poi_count, " +
                    "(SELECT COUNT(*) FROM tours WHERE city_id = m.city_id) as tour_count " +
                    "FROM maps m JOIN cities c ON c.id = m.city_id WHERE m.city_id = ? AND ((m.approved = 1) OR (m.created_by = ?)) ORDER BY m.name";
        } else {
            query = "SELECT m.id, m.city_id, c.description as city_description, m.name, m.short_description, 1 as approved, COALESCE(m.tour_id, 0) as tour_id, " +
                    poiCountSubquery + " as poi_count, " +
                    "(SELECT COUNT(*) FROM tours WHERE city_id = m.city_id) as tour_count " +
                    "FROM maps m JOIN cities c ON c.id = m.city_id WHERE m.city_id = ? AND m.approved = 1 ORDER BY m.name";
        }

        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null)
                return maps;

            // Ensure every tour in this city has a dedicated map (same name as tour, POIs + lines)
            ensureTourMapsForCity(conn, cityId);

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            if (currentUserId > 0) stmt.setInt(2, currentUserId);
            ResultSet rs = stmt.executeQuery();

            java.util.Map<String, MapSummary> byName = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                String name = rs.getString("name");
                if (name == null) continue;
                String nameKey = name.trim();
                int approved = 0;
                int createdBy = 0;
                try {
                    approved = rs.getInt("approved");
                    createdBy = rs.getInt("created_by");
                } catch (SQLException ignored) { }
                boolean isCurrentUserDraft = (approved == 0 && currentUserId > 0 && createdBy == currentUserId);
                MapSummary s = new MapSummary(
                        rs.getInt("id"),
                        name,
                        rs.getString("short_description"),
                        rs.getInt("poi_count"),
                        rs.getInt("tour_count"));
                try {
                    s.setCityId(rs.getInt("city_id"));
                    s.setCityDescription(rs.getString("city_description"));
                    s.setDraft(approved == 0);
                    int tid = rs.getInt("tour_id");
                    s.setTourId(tid > 0 ? tid : null);
                } catch (SQLException e) {
                    // columns may not exist
                }
                // Dedupe by name: prefer current user's draft over an approved/tour map with same name
                MapSummary existing = byName.get(nameKey);
                if (existing == null) {
                    byName.put(nameKey, s);
                } else if (isCurrentUserDraft && !existing.isDraft()) {
                    byName.put(nameKey, s);
                }
                // else keep existing (first one wins when neither is user's draft)
            }
            maps.addAll(byName.values());

            // Mark maps that have a PENDING request by this user (from map_edit_requests) so UI can show "(waiting for approval)"
            if (currentUserId > 0) {
                try {
                    java.util.Set<Integer> pendingMapIds = MapEditRequestDAO.getMapIdsWithPendingRequestByUser(currentUserId, cityId);
                    int marked = 0;
                    for (MapSummary m : maps) {
                        if (pendingMapIds != null && pendingMapIds.contains(m.getId())) {
                            m.setWaitingForApproval(true);
                            marked++;
                        }
                    }
                    if (marked > 0) System.out.println("MapDAO.getMapsForCity: cityId=" + cityId + ", userId=" + currentUserId + ", marked " + marked + " map(s) waitingForApproval");
                } catch (Exception e) {
                    System.err.println("MapDAO.getMapsForCity: getMapIdsWithPendingRequestByUser failed (non-fatal): " + e.getMessage());
                }
            }

            System.out.println("MapDAO.getMapsForCity: cityId=" + cityId + ", userId=" + currentUserId + ", maps=" + maps.size());

        } catch (SQLException e) {
            try {
                return getMapsForCityLegacy(cityId);
            } catch (SQLException e2) {
                e.printStackTrace();
            }
        }

        return maps;
    }

    /**
     * Create a dedicated map for each tour in the city that does not yet have one.
     * Map name = tour name, contains the tour's POIs in order; when loaded, lines connect them with distance on hover.
     */
    private static void ensureTourMapsForCity(Connection conn, int cityId) {
        try {
            List<TourDTO> tours = TourDAO.getToursForCity(conn, cityId);
            for (TourDTO tour : tours) {
                if (tour.getId() <= 0 || tour.getName() == null) continue;
                boolean hasStops = tour.getStops() != null && !tour.getStops().isEmpty();
                Integer mapId = getMapIdByTourId(conn, tour.getId());
                if (mapId == null) {
                    if (hasStops) {
                        createTourMap(conn, tour, 1); // createdBy 1 = system; no per-tour logging to avoid spam
                    }
                    // Draft tour (0 stops): do not create a map so it does not appear in the catalog
                } else {
                    syncTourMapPois(conn, mapId, tour); // keep map_pois in sync with current tour stops (e.g. 2 POIs)
                }
            }
        } catch (SQLException e) {
            System.err.println("MapDAO: ensureTourMapsForCity failed for city " + cityId + ": " + e.getMessage());
        }
    }

    /** Sync a tour map's linked POIs to match the tour's current stops (order preserved). Never removes draft (approved=0) links so newly added POIs stay visible. */
    private static void syncTourMapPois(Connection conn, int mapId, TourDTO tour) throws SQLException {
        java.util.Set<Integer> tourStopPoiIds = new java.util.HashSet<>();
        if (tour.getStops() != null) {
            for (TourStopDTO stop : tour.getStops()) {
                if (stop.getPoiId() > 0) {
                    tourStopPoiIds.add(stop.getPoiId());
                }
            }
        }
        int order = 0;
        if (tour.getStops() != null) {
            for (TourStopDTO stop : tour.getStops()) {
                if (stop.getPoiId() > 0) {
                    PoiDAO.linkPoiToMap(conn, mapId, stop.getPoiId(), order++, true, 0);
                }
            }
        }
        PoiDAO.deleteApprovedLinksForMapNotIn(conn, mapId, tourStopPoiIds);
    }

    /** Get POIs in tour stop order (for tour map content when map_pois is empty). */
    private static List<Poi> getPoisFromTourStops(Connection conn, int tourId) throws SQLException {
        List<Poi> out = new ArrayList<>();
        TourDTO tour = TourDAO.getTourById(conn, tourId);
        if (tour == null || tour.getStops() == null) return out;
        for (TourStopDTO stop : tour.getStops()) {
            if (stop.getPoiId() > 0) {
                Poi p = PoiDAO.getPoiById(conn, stop.getPoiId());
                if (p != null) out.add(p);
            }
        }
        return out;
    }

    /**
     * Merge draft addedStops/updatedStops/deletedStopIds into the loaded tours so that after Save (draft),
     * the tour list shows the new POI as a stop (with [Draft] on the client via the POI's draft flag).
     */
    private static void mergeDraftStopsIntoTours(List<TourDTO> tours, MapChanges dc, List<Poi> pois) {
        if (tours == null || dc == null) return;
        List<Poi> poilist = pois != null ? pois : new ArrayList<>();
        // Resolve draft addedPois (id=0) to real POI ids by name so addedStops with poiId=0 can be resolved
        java.util.Map<String, Integer> newPoiNameToId = new java.util.HashMap<>();
        if (dc.getAddedPois() != null) {
            for (Poi ap : dc.getAddedPois()) {
                if (ap.getId() != 0) continue;
                String name = ap.getName() != null ? ap.getName().trim() : "";
                if (name.isEmpty()) continue;
                for (Poi p : poilist) {
                    String pName = p.getName() != null ? p.getName().trim() : "";
                    if (name.equals(pName) && ap.getCityId() == p.getCityId()) {
                        newPoiNameToId.put(name, p.getId());
                        break;
                    }
                }
            }
        }
        java.util.List<Integer> deletedIds = dc.getDeletedStopIds() != null ? dc.getDeletedStopIds() : new ArrayList<>();
        java.util.Set<Integer> deletedPoiIds = (dc.getDeletedPoiIds() != null && !dc.getDeletedPoiIds().isEmpty())
                ? new java.util.HashSet<>(dc.getDeletedPoiIds()) : java.util.Collections.emptySet();
        java.util.Map<Integer, TourStopDTO> updatedById = new java.util.HashMap<>();
        if (dc.getUpdatedStops() != null) {
            for (TourStopDTO s : dc.getUpdatedStops()) {
                if (s.getId() > 0) updatedById.put(s.getId(), s);
            }
        }
        for (TourDTO tour : tours) {
            List<TourStopDTO> stops = tour.getStops();
            if (stops == null) stops = new ArrayList<>();
            else stops = new ArrayList<>(stops);
            stops.removeIf(s -> deletedIds.contains(s.getId()));
            for (int i = 0; i < stops.size(); i++) {
                TourStopDTO updated = updatedById.get(stops.get(i).getId());
                if (updated != null) stops.set(i, updated);
            }
            if (dc.getAddedStops() != null) {
                for (TourStopDTO stop : dc.getAddedStops()) {
                    if (stop.getTourId() != tour.getId()) continue;
                    String stopPoiName = stop.getPoiName() != null ? stop.getPoiName().trim() : "";
                    int resolvedPoiId = stop.getPoiId() > 0 ? stop.getPoiId() : (stopPoiName.isEmpty() ? 0 : newPoiNameToId.getOrDefault(stopPoiName, 0));
                    if (resolvedPoiId <= 0 || deletedPoiIds.contains(resolvedPoiId)) continue;
                    TourStopDTO copy = new TourStopDTO(0, tour.getId(), resolvedPoiId, stop.getPoiName(), stop.getPoiCategory(), stop.getStopOrder(), stop.getNotes() != null ? stop.getNotes() : "");
                    stops.add(copy);
                }
            }
            stops.sort((a, b) -> Integer.compare(a.getStopOrder(), b.getStopOrder()));
            tour.setStops(stops);
        }
    }

    /** Legacy: no approved/created_by on maps; poi count without map_pois.approved so it works when column missing. */
    private static List<MapSummary> getMapsForCityLegacy(int cityId) throws SQLException {
        List<MapSummary> maps = new ArrayList<>();
        String poiCountExpr = "(SELECT COUNT(*) FROM map_pois mp WHERE mp.map_id = m.id)";
        String query = "SELECT m.id, m.city_id, c.description as city_description, m.name, m.short_description, " +
                poiCountExpr + " as poi_count, " +
                "(SELECT COUNT(*) FROM tours WHERE city_id = m.city_id) as tour_count " +
                "FROM maps m JOIN cities c ON c.id = m.city_id WHERE m.city_id = ? ORDER BY m.name";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return maps;
            ensureTourMapsForCity(conn, cityId);
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId);
            ResultSet rs = stmt.executeQuery();
            java.util.Set<String> seenNames = new java.util.HashSet<>();
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && !seenNames.add(name.trim())) continue;
                MapSummary s = new MapSummary(rs.getInt("id"), name, rs.getString("short_description"),
                        rs.getInt("poi_count"), rs.getInt("tour_count"));
                try { s.setCityId(rs.getInt("city_id")); } catch (SQLException ignored) { }
                try { s.setCityDescription(rs.getString("city_description")); } catch (SQLException ignored) { }
                maps.add(s);
            }
        } catch (SQLException e) {
            throw e;
        }
        return maps;
    }

    /**
     * Get complete map content for editing. When requestUserId is set, includes that user's draft POIs so they can edit them.
     */
    public static MapContent getMapContent(int mapId) {
        return getMapContent(mapId, null);
    }

    /**
     * Get complete map content for editing. When requestUserId != null and > 0, POI list includes that user's draft POIs.
     */
    public static MapContent getMapContent(int mapId, Integer requestUserId) {
        System.err.println("[MapDAO.getMapContent] ENTER mapId=" + mapId + " requestUserId=" + requestUserId);
        System.out.println("MapDAO.getMapContent: mapId=" + mapId + ", requestUserId=" + requestUserId);
        MapContent content = null;

        // Main query without tour_id so maps load even when migration_tour_maps.sql has not been run
        String mapQuery = "SELECT m.id, m.city_id, c.name as city_name, c.description as city_description, m.name, m.short_description, " +
                "m.created_at, m.updated_at " +
                "FROM maps m JOIN cities c ON c.id = m.city_id WHERE m.id = ?";

        System.err.println("[MapDAO.getMapContent] getting connection...");
        try (Connection conn = DBConnector.getConnection()) {
            System.err.println("[MapDAO.getMapContent] conn=" + (conn != null));
            if (conn == null) {
                System.err.println("[MapDAO.getMapContent] conn is null, returning null");
                return null;
            }

            PreparedStatement stmt = conn.prepareStatement(mapQuery);
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                System.out.println("MapDAO.getMapContent: map not found, mapId=" + mapId);
                return null;
            }
            content = new MapContent(
                    rs.getInt("id"),
                    rs.getInt("city_id"),
                    rs.getString("city_name"),
                    rs.getString("name"),
                    rs.getString("short_description"));
            content.setCityDescription(rs.getString("city_description"));
            content.setCreatedAt(rs.getString("created_at"));
            content.setUpdatedAt(rs.getString("updated_at"));

            // Optional: load tour_id and route segments if column exists (after migration_tour_maps.sql)
            try (PreparedStatement tourStmt = conn.prepareStatement("SELECT tour_id FROM maps WHERE id = ?")) {
                tourStmt.setInt(1, mapId);
                ResultSet tourRs = tourStmt.executeQuery();
                if (tourRs.next()) {
                    Integer tourId = (Integer) tourRs.getObject("tour_id");
                    if (tourId != null) {
                        content.setTourId(tourId);
                        List<TourSegmentDTO> segments = buildTourSegments(conn, tourId);
                        content.setTourSegments(segments);
                    }
                }
            } catch (SQLException ignored) { /* tour_id column may not exist */ }

            // If no tour_id (e.g. migration not run), try to match map name to a tour in this city so route lines still show
            if (content.getTourId() == null && content.getMapName() != null && !content.getMapName().trim().isEmpty()) {
                List<TourDTO> cityTours = TourDAO.getToursForCity(conn, content.getCityId());
                for (TourDTO t : cityTours) {
                    if (t.getName() != null && t.getName().trim().equalsIgnoreCase(content.getMapName().trim())) {
                        content.setTourId(t.getId());
                        List<TourSegmentDTO> segments = buildTourSegments(conn, t.getId());
                        content.setTourSegments(segments);
                        break;
                    }
                }
            }

                // Get POIs: include requestor's draft POIs when userId is set so the employee sees their own drafts
                List<Poi> pois = (requestUserId != null && requestUserId > 0)
                        ? PoiDAO.getPoisForMapForEditor(mapId, requestUserId)
                        : PoiDAO.getPoisForMap(mapId);
                // For tour maps, if map_pois was empty (e.g. tour has 2 stops but wasn't synced yet), fill from tour stops so circles and count show
                if (content.getTourId() != null && (pois == null || pois.isEmpty())) {
                    List<Poi> fromTour = getPoisFromTourStops(conn, content.getTourId());
                    if (!fromTour.isEmpty()) pois = fromTour;
                }
                content.setPois(pois);
                System.err.println("[MapDAO.getMapContent] pois set, count=" + (pois != null ? pois.size() : 0));

                // Fetch DRAFT request for this map+user to restore pending unlinks/deletes/tours (awaiting manager approval)
                common.dto.MapEditRequestDTO draftReq = (requestUserId != null && requestUserId > 0)
                        ? MapEditRequestDAO.getDraftRequestForMapUser(mapId, requestUserId) : null;
                if (requestUserId != null && requestUserId > 0 && draftReq != null && draftReq.getChanges() != null) {
                    common.dto.MapChanges dc = draftReq.getChanges();
                    if (dc.getPoiMapUnlinks() != null && !dc.getPoiMapUnlinks().isEmpty()) {
                        content.setPendingPoiMapUnlinks(dc.getPoiMapUnlinks());
                        java.util.Set<Integer> pendingUnlinkIds = new java.util.HashSet<>();
                        for (common.dto.MapChanges.PoiMapLink link : dc.getPoiMapUnlinks()) {
                            if (link.mapId == mapId) pendingUnlinkIds.add(link.poiId);
                        }
                        for (common.Poi p : pois) {
                            if (pendingUnlinkIds.contains(p.getId())) p.setPendingRemoval(true);
                        }
                    }
                    if (dc.getDeletedPoiIds() != null && !dc.getDeletedPoiIds().isEmpty()) {
                        content.setPendingDeletedPoiIds(dc.getDeletedPoiIds());
                        java.util.Set<Integer> pendingDeleteIds = new java.util.HashSet<>(dc.getDeletedPoiIds());
                        for (common.Poi p : pois) {
                            if (pendingDeleteIds.contains(p.getId())) p.setPendingDeletion(true);
                        }
                    }
                    content.setDraftChangesToRestore(dc); // full changes for client to restore pendingChanges
                }

                // Get tours for this city
                System.err.println("[MapDAO.getMapContent] loading tours for cityId=" + content.getCityId());
                List<TourDTO> tours = TourDAO.getToursForCity(content.getCityId());
                System.err.println("[MapDAO.getMapContent] tours loaded, count=" + (tours != null ? tours.size() : 0));

                // Merge pending tours from DRAFT request (awaiting manager approval).
                // Dedupe: if a draft added tour matches a tour already in DB (same name), mark that one as draft instead of adding a duplicate.
                if (draftReq != null && draftReq.getChanges() != null) {
                    common.dto.MapChanges dc = draftReq.getChanges();
                    if (dc.getAddedTours() != null && !dc.getAddedTours().isEmpty()) {
                        content.setPendingAddedTours(dc.getAddedTours());
                        for (TourDTO t : dc.getAddedTours()) {
                            t.setDraft(true);
                            String name = t.getName() != null ? t.getName().trim() : "";
                            TourDTO existing = tours.stream().filter(tour -> name.equals(tour.getName() != null ? tour.getName().trim() : "")).findFirst().orElse(null);
                            if (existing != null) {
                                existing.setDraft(true);
                                if (t.getId() == 0) t.setId(existing.getId());
                            } else {
                                tours.add(t);
                            }
                        }
                    }
                    if (dc.getDeletedTourIds() != null && !dc.getDeletedTourIds().isEmpty()) {
                        content.setPendingDeletedTourIds(dc.getDeletedTourIds());
                        java.util.Set<Integer> pendingDeleteIds = new java.util.HashSet<>(dc.getDeletedTourIds());
                        for (TourDTO t : tours) {
                            if (pendingDeleteIds.contains(t.getId())) t.setPendingDeletion(true);
                        }
                    }
                    if (dc.getUpdatedTours() != null && !dc.getUpdatedTours().isEmpty()) {
                        java.util.Map<Integer, TourDTO> updatedById = new java.util.HashMap<>();
                        for (TourDTO t : dc.getUpdatedTours()) updatedById.put(t.getId(), t);
                        for (int i = 0; i < tours.size(); i++) {
                            TourDTO updated = updatedById.get(tours.get(i).getId());
                            if (updated != null) tours.set(i, updated);
                        }
                    }
                    // Merge draft addedStops into tours so the new POI (saved as draft) appears in the tour until Publish
                    if (dc.getAddedStops() != null || dc.getUpdatedStops() != null || (dc.getDeletedStopIds() != null && !dc.getDeletedStopIds().isEmpty())) {
                        mergeDraftStopsIntoTours(tours, dc, pois);
                    }
                }

                // Mark tours that appear in a PENDING request by this user so UI can show "(waiting for approval)"
                if (requestUserId != null && requestUserId > 0 && content.getCityId() > 0) {
                    try {
                        System.err.println("[MapDAO.getMapContent] getTourIdsWithPendingRequestByUser...");
                        java.util.Set<Integer> pendingTourIds = MapEditRequestDAO.getTourIdsWithPendingRequestByUser(requestUserId, content.getCityId());
                        System.err.println("[MapDAO.getMapContent] pendingTourIds=" + (pendingTourIds != null ? pendingTourIds.size() : 0));
                        if (pendingTourIds != null) {
                            for (TourDTO t : tours) {
                                if (pendingTourIds.contains(t.getId())) t.setWaitingForApproval(true);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("MapDAO: getTourIdsWithPendingRequestByUser failed (non-fatal): " + e.getMessage());
                    }
                }

                content.setTours(tours);

                System.out.println("MapDAO.getMapContent: loaded mapId=" + mapId + ", pois=" + (pois != null ? pois.size() : 0) + ", tours=" + (tours != null ? tours.size() : 0));

        } catch (SQLException e) {
            System.err.println("MapDAO.getMapContent: SQLException mapId=" + mapId + " - " + e.getMessage());
            e.printStackTrace();
        }

        // Ensure client never gets null lists (avoids NPE in onMapContentReceived)
        if (content != null) {
            if (content.getPois() == null) content.setPois(new ArrayList<>());
            if (content.getTours() == null) content.setTours(new ArrayList<>());
        }
        System.out.println("MapDAO.getMapContent: returning content=" + (content != null) + (content != null ? ", pois=" + content.getPois().size() + ", tours=" + content.getTours().size() : ""));
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
     * Return true if the map is a draft (unapproved). Employee can delete draft maps immediately on Save.
     */
    public static boolean isMapDraft(Connection conn, int mapId) throws SQLException {
        if (mapId <= 0) return false;
        try {
            String sql = "SELECT 1 FROM maps WHERE id = ? AND (approved = 0 OR approved IS NULL) LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, mapId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("approved") || e.getMessage().contains("created_by"))) return false;
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
     * Count public (approved) maps for a city (for activity reports). Excludes drafts and deleted.
     */
    public static int countMapsForCity(int cityId) {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM maps WHERE city_id = ? AND approved = 1")) {
                stmt.setInt(1, cityId);
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && !e.getMessage().contains("approved")) {
                System.err.println("MapDAO.countMapsForCity: " + e.getMessage());
            }
            return 0;
        }
    }

    /**
     * City IDs that have at least one approved map (for "all cities" report so we include every city with maps).
     */
    public static List<Integer> getCityIdsWithApprovedMaps() {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return ids;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT DISTINCT city_id FROM maps WHERE approved = 1 ORDER BY city_id");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("city_id"));
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && !e.getMessage().contains("approved")) {
                System.err.println("MapDAO.getCityIdsWithApprovedMaps: " + e.getMessage());
            }
        }
        return ids;
    }

    /**
     * Count total public (approved) maps across all cities (for "all cities" report).
     */
    public static int countAllMaps() {
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM maps WHERE approved = 1");
                 ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && !e.getMessage().contains("approved")) {
                System.err.println("MapDAO.countAllMaps: " + e.getMessage());
            }
            return 0;
        }
    }

    /**
     * Get city_id for a map (for use when resolving scope for delete-map requests).
     */
    public static int getCityIdForMap(Connection conn, int mapId) throws SQLException {
        if (mapId <= 0) return 0;
        String query = "SELECT city_id FROM maps WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? rs.getInt("city_id") : 0;
    }

    /**
     * Get all map IDs for a city (for use when deleting a city).
     */
    public static List<Integer> getMapIdsByCityId(Connection conn, int cityId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String query = "SELECT id FROM maps WHERE city_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, cityId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            ids.add(rs.getInt("id"));
        }
        return ids;
    }

    /**
     * Delete a map (uses provided connection for transaction).
     * First deletes all POIs linked to this map (from map_pois), including removing them from tour_stops and everywhere else, then deletes the map.
     */
    public static boolean deleteMap(Connection conn, int mapId) throws SQLException {
        List<Integer> poiIds = PoiDAO.getPoiIdsLinkedToMap(conn, mapId);
        for (Integer poiId : poiIds) {
            if (poiId != null && poiId > 0) {
                try {
                    PoiDAO.deletePoiCompletely(conn, poiId);
                } catch (SQLException e) {
                    System.err.println("MapDAO: Failed to delete POI " + poiId + " when deleting map " + mapId + ": " + e.getMessage());
                    throw e;
                }
            }
        }
        String query = "DELETE FROM maps WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setInt(1, mapId);
        int affected = stmt.executeUpdate();
        System.out.println("MapDAO: Deleted map " + mapId + " (and " + poiIds.size() + " POI(s)), affected: " + affected);
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

    /**
     * Build segment list for a tour (consecutive legs only; no circle-back from last to first POI).
     */
    private static List<TourSegmentDTO> buildTourSegments(Connection conn, int tourId) throws SQLException {
        List<TourSegmentDTO> segments = new ArrayList<>();
        TourDTO tour = TourDAO.getTourById(conn, tourId);
        if (tour == null || tour.getStops() == null || tour.getStops().size() < 2) return segments;
        List<TourStopDTO> stops = tour.getStops();
        for (int i = 0; i < stops.size() - 1; i++) {
            int fromPoiId = stops.get(i).getPoiId();
            int toPoiId = stops.get(i + 1).getPoiId();
            Poi fromPoi = PoiDAO.getPoiById(conn, fromPoiId);
            Poi toPoi = PoiDAO.getPoiById(conn, toPoiId);
            if (fromPoi == null || toPoi == null || fromPoi.getLatitude() == null || fromPoi.getLongitude() == null
                    || toPoi.getLatitude() == null || toPoi.getLongitude() == null) continue;
            Double dist = PoiDistanceDAO.getDistance(conn, fromPoiId, toPoiId);
            segments.add(new TourSegmentDTO(fromPoiId, toPoiId,
                    fromPoi.getLatitude(), fromPoi.getLongitude(),
                    toPoi.getLatitude(), toPoi.getLongitude(),
                    dist));
        }
        return segments;
    }

    /**
     * Get map ID for a tour's dedicated route map, or null if none.
     */
    public static Integer getMapIdByTourId(Connection conn, int tourId) throws SQLException {
        try {
            String sql = "SELECT id FROM maps WHERE tour_id = ? LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, tourId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : null;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("tour_id")) return null;
            throw e;
        }
    }

    /**
     * Create a map linked to a tour (tour route map). Name = tour name, contains tour POIs in order.
     * Skips if a map for this tour already exists. If a map with the same name already exists in the city, reuses it (avoids duplicates when multiple requests run).
     */
    public static int createTourMap(Connection conn, TourDTO tour, int createdBy) throws SQLException {
        if (tour == null || tour.getCityId() <= 0 || tour.getName() == null || tour.getId() <= 0) return -1;
        Integer existing = getMapIdByTourId(conn, tour.getId());
        if (existing != null) return existing;
        // Reuse only if map with same name is unassigned or already ours; never steal from another tour (avoids thrash + spam)
        int mapId = getMapIdByCityAndNameForTour(conn, tour.getCityId(), tour.getName(), tour.getId());
        if (mapId <= 0) {
            mapId = createMap(conn, tour.getCityId(), tour.getName(), "Tour route – " + tour.getName(), createdBy, true, tour.getId());
            if (mapId <= 0) {
                mapId = getMapIdByCityAndNameForTour(conn, tour.getCityId(), tour.getName(), tour.getId());
            }
        }
        if (mapId > 0) {
            try (PreparedStatement u = conn.prepareStatement("UPDATE maps SET tour_id = ? WHERE id = ?")) {
                u.setInt(1, tour.getId());
                u.setInt(2, mapId);
                u.executeUpdate();
            } catch (SQLException ignored) { /* tour_id column may not exist */ }
        }
        if (mapId <= 0) return -1;
        if (tour.getStops() != null) {
            int order = 0;
            for (TourStopDTO stop : tour.getStops()) {
                if (stop.getPoiId() > 0) {
                    PoiDAO.linkPoiToMap(conn, mapId, stop.getPoiId(), order++, true, 0);
                }
            }
        }
        return mapId;
    }

    /** Get map id by city and name (any state). Returns -1 if not found. */
    private static int getMapIdByCityAndName(Connection conn, int cityId, String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) return -1;
        String sql = "SELECT id FROM maps WHERE city_id = ? AND TRIM(name) = TRIM(?) LIMIT 1";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, cityId);
        stmt.setString(2, name.trim());
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? rs.getInt("id") : -1;
    }

    /** Get map id by city and name only if approved=1 or already has tour_id; do not reuse employee drafts. */
    private static int getMapIdByCityAndNameApprovedOrTourOnly(Connection conn, int cityId, String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) return -1;
        try {
            String sql = "SELECT id FROM maps WHERE city_id = ? AND TRIM(name) = TRIM(?) AND (approved = 1 OR tour_id IS NOT NULL) LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cityId);
            stmt.setString(2, name.trim());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("tour_id")) {
                String sql = "SELECT id FROM maps WHERE city_id = ? AND TRIM(name) = TRIM(?) AND approved = 1 LIMIT 1";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, cityId);
                stmt.setString(2, name.trim());
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getInt("id") : -1;
            }
            throw e;
        }
    }

    /** Reuse map by city+name only if it is unassigned (tour_id NULL) or already assigned to this tour; never steal from another tour. */
    private static int getMapIdByCityAndNameForTour(Connection conn, int cityId, String name, int tourId) throws SQLException {
        if (name == null || name.trim().isEmpty()) return -1;
        try {
            String sql = "SELECT id FROM maps WHERE city_id = ? AND TRIM(name) = TRIM(?) AND approved = 1 AND (tour_id IS NULL OR tour_id = ?) LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cityId);
            stmt.setString(2, name.trim());
            stmt.setInt(3, tourId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("tour_id"))
                return -1;
            throw e;
        }
    }

    /**
     * Create a new map, optionally linked to a tour (tour_id).
     */
    public static int createMap(Connection conn, int cityId, String name, String description, int createdBy, boolean approved, Integer tourId) throws SQLException {
        if (tourId == null) return createMap(conn, cityId, name, description, createdBy, approved);
        try {
            String query = "INSERT INTO maps (city_id, name, short_description, created_by, approved, tour_id) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, cityId);
            stmt.setString(2, name);
            stmt.setString(3, description != null ? description : "");
            stmt.setInt(4, createdBy <= 0 ? 1 : createdBy);
            stmt.setInt(5, approved ? 1 : 0);
            stmt.setInt(6, tourId);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            // DB may lack tour_id and/or created_by; fall back to 6-arg (which itself falls back to 3-column INSERT)
            if (msg.contains("tour_id") || msg.contains("created_by")) {
                return createMap(conn, cityId, name, description, createdBy, approved);
            }
            throw e;
        }
        return -1;
    }
}
