package server.handler;

import common.MessageType;
import common.Poi;
import common.Request;
import common.Response;
import common.dto.*;
import server.DBConnector;
import server.SessionManager;
import server.dao.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler for all map editing operations.
 * Includes validation and transaction support.
 */
public class MapEditHandler {

    /**
     * Handle a map editing request.
     */
    public static Response handle(Request request) {
        MessageType type = request.getType();

        try {
            switch (type) {
                case GET_CITIES:
                    return handleGetCities(request);

                case GET_MAPS_FOR_CITY:
                    return handleGetMapsForCity(request);

                case GET_MAP_CONTENT:
                    return handleGetMapContent(request);

                case GET_POIS_FOR_CITY:
                    return handleGetPoisForCity(request);

                case SAVE_MAP_CHANGES:
                    return handleSaveMapChanges(request);
                case SUBMIT_MAP_CHANGES:
                    return handleSubmitMapChanges(request);

                case GET_MY_DRAFT:
                    return handleGetMyDraft(request);

                case GET_PENDING_MAP_EDITS:
                    return handleGetPendingMapEdits(request);

                case APPROVE_MAP_EDIT:
                    return handleApproveMapEdit(request);

                case REJECT_MAP_EDIT:
                    return handleRejectMapEdit(request);

                case CREATE_CITY:
                    return handleCreateCity(request);

                case UPDATE_CITY:
                    return handleUpdateCity(request);

                case CREATE_MAP:
                    return handleCreateMap(request);

                case ADD_POI:
                    return handleAddPoi(request);

                case UPDATE_POI:
                    return handleUpdatePoi(request);

                case DELETE_POI:
                    return handleDeletePoi(request);

                case CREATE_TOUR:
                    return handleCreateTour(request);

                case UPDATE_TOUR:
                    return handleUpdateTour(request);

                case DELETE_TOUR:
                    return handleDeleteTour(request);

                case ADD_TOUR_STOP:
                    return handleAddTourStop(request);

                case UPDATE_TOUR_STOP:
                    return handleUpdateTourStop(request);

                case REMOVE_TOUR_STOP:
                    return handleRemoveTourStop(request);

                default:
                    return Response.error(request, Response.ERR_INTERNAL,
                            "Unknown map edit message type: " + type);
            }
        } catch (Exception e) {
            System.out.println("MapEditHandler: Error processing request - " + e.getMessage());
            e.printStackTrace();
            return Response.error(request, Response.ERR_INTERNAL,
                    "Error processing request: " + e.getMessage());
        }
    }

    // ==================== GET Operations ====================

    private static Response handleGetCities(Request request) {
        int userId = resolveUserId(request);
        System.out.println("MapEditHandler: Getting all cities for user " + userId);
        List<CityDTO> cities = CityDAO.getAllCities(userId);
        return Response.success(request, cities);
    }

    private static Response handleGetMapsForCity(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "City ID required");
        }

        int cityId = (Integer) request.getPayload();
        int userId = resolveUserId(request);
        System.out.println("MapEditHandler: Getting maps for city " + cityId + " for user " + userId);

        List<MapSummary> maps = MapDAO.getMapsForCity(cityId, userId);
        return Response.success(request, maps);
    }

    private static Response handleGetMapContent(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Map ID required");
        }

        int mapId = (Integer) request.getPayload();
        int userId = resolveUserId(request);
        System.out.println("MapEditHandler.GET_MAP_CONTENT: mapId=" + mapId + ", userId=" + userId);
        System.out.flush();
        System.err.println("[MapEditHandler] ABOUT TO CALL MapDAO.getMapContent(" + mapId + ", " + (userId > 0 ? userId : "null") + ")");

        MapContent content = MapDAO.getMapContent(mapId, userId > 0 ? userId : null);

        System.out.println("MapEditHandler.GET_MAP_CONTENT: getMapContent returned, content=" + (content != null));
        System.out.flush();

        if (content == null) {
            System.out.println("MapEditHandler.GET_MAP_CONTENT: map not found, mapId=" + mapId);
            return Response.error(request, Response.ERR_NOT_FOUND, "Map not found");
        }

        System.out.println("MapEditHandler.GET_MAP_CONTENT: success mapId=" + mapId + ", pois=" + (content.getPois() != null ? content.getPois().size() : 0) + ", tours=" + (content.getTours() != null ? content.getTours().size() : 0));
        return Response.success(request, content);
    }

    private static Response handleGetPoisForCity(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "City ID required");
        }
        int cityId = (Integer) request.getPayload();
        List<Poi> pois = PoiDAO.getPoisForCity(cityId);
        return Response.success(request, pois);
    }

    // ==================== CREATE Operations ====================

    private static Response handleCreateCity(Request request) {
        if (!(request.getPayload() instanceof CityDTO)) {
            return Response.error(request, Response.ERR_VALIDATION, "CityDTO required");
        }

        CityDTO city = (CityDTO) request.getPayload();

        // Validate
        ValidationResult validation = validateCity(city);
        if (!validation.isValid()) {
            return Response.success(request, validation);
        }

        // Create
        int cityId = CityDAO.createCity(city.getName(), city.getDescription(), city.getPrice());
        if (cityId > 0) {
            validation = ValidationResult.success("City created successfully");
            validation.setCreatedCityId(cityId);
            return Response.success(request, validation);
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to create city");
    }

    private static Response handleUpdateCity(Request request) {
        if (!(request.getPayload() instanceof CityDTO)) {
            return Response.error(request, Response.ERR_VALIDATION, "CityDTO required");
        }

        CityDTO city = (CityDTO) request.getPayload();

        // Validate
        ValidationResult validation = validateCity(city);
        if (!validation.isValid()) {
            return Response.success(request, validation);
        }

        // Update
        if (CityDAO.updateCity(city.getId(), city.getName(), city.getDescription(), city.getPrice())) {
            return Response.success(request, ValidationResult.success("City updated successfully"));
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to update city");
    }

    private static Response handleCreateMap(Request request) {
        if (!(request.getPayload() instanceof MapContent)) {
            return Response.error(request, Response.ERR_VALIDATION, "MapContent required");
        }

        MapContent map = (MapContent) request.getPayload();

        // Validate
        ValidationResult validation = validateMap(map);
        if (!validation.isValid()) {
            return Response.success(request, validation);
        }

        // Create
        int mapId = MapDAO.createMap(map.getCityId(), map.getMapName(), map.getShortDescription());
        if (mapId > 0) {
            validation = ValidationResult.success("Map created successfully");
            validation.setCreatedMapId(mapId);
            server.dao.DailyStatsDAO.increment(map.getCityId(), server.dao.DailyStatsDAO.Metric.MAPS_COUNT);
            return Response.success(request, validation);
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to create map");
    }

    // ==================== POI Operations ====================

    private static Response handleAddPoi(Request request) {
        if (!(request.getPayload() instanceof Poi)) {
            return Response.error(request, Response.ERR_VALIDATION, "Poi required");
        }

        Poi poi = (Poi) request.getPayload();

        // Validate
        ValidationResult validation = validatePoi(poi);
        if (!validation.isValid()) {
            return Response.success(request, validation);
        }

        // Create
        int poiId = PoiDAO.createPoi(poi);
        if (poiId > 0) {
            validation = ValidationResult.success("POI created successfully");
            validation.getCreatedPoiIds().add(poiId);
            return Response.success(request, validation);
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to create POI");
    }

    private static Response handleUpdatePoi(Request request) {
        if (!(request.getPayload() instanceof Poi)) {
            return Response.error(request, Response.ERR_VALIDATION, "Poi required");
        }

        Poi poi = (Poi) request.getPayload();

        // Validate
        ValidationResult validation = validatePoi(poi);
        if (!validation.isValid()) {
            return Response.success(request, validation);
        }

        // Update
        if (PoiDAO.updatePoi(poi)) {
            return Response.success(request, ValidationResult.success("POI updated successfully"));
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to update POI");
    }

    private static Response handleDeletePoi(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "POI ID required");
        }

        int poiId = (Integer) request.getPayload();

        // Check if POI is used in any tour
        if (PoiDAO.isPoiUsedInTour(poiId)) {
            ValidationResult result = ValidationResult.error("poi",
                    "Cannot delete POI - it is used in one or more tours. Remove it from all tours first.");
            return Response.success(request, result);
        }

        try (Connection conn = DBConnector.getConnection()) {
            if (PoiDAO.deletePoi(conn, poiId)) {
                return Response.success(request, ValidationResult.success("POI deleted successfully"));
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to delete POI");
    }

    // ==================== Tour Operations ====================

    private static Response handleCreateTour(Request request) {
        if (!(request.getPayload() instanceof TourDTO)) {
            return Response.error(request, Response.ERR_VALIDATION, "TourDTO required");
        }

        TourDTO tour = (TourDTO) request.getPayload();

        // Validate
        ValidationResult validation = validateTour(tour);
        if (!validation.isValid()) {
            return Response.success(request, validation);
        }

        // Create
        int tourId = TourDAO.createTour(tour);
        if (tourId > 0) {
            validation = ValidationResult.success("Tour created successfully");
            validation.getCreatedTourIds().add(tourId);
            return Response.success(request, validation);
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to create tour");
    }

    private static Response handleUpdateTour(Request request) {
        if (!(request.getPayload() instanceof TourDTO)) {
            return Response.error(request, Response.ERR_VALIDATION, "TourDTO required");
        }

        TourDTO tour = (TourDTO) request.getPayload();

        try (Connection conn = DBConnector.getConnection()) {
            if (TourDAO.updateTour(conn, tour)) {
                return Response.success(request, ValidationResult.success("Tour updated successfully"));
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to update tour");
    }

    private static Response handleDeleteTour(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Tour ID required");
        }

        int tourId = (Integer) request.getPayload();

        try (Connection conn = DBConnector.getConnection()) {
            if (TourDAO.deleteTour(conn, tourId)) {
                return Response.success(request, ValidationResult.success("Tour deleted successfully"));
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to delete tour");
    }

    // ==================== Tour Stop Operations ====================

    private static Response handleAddTourStop(Request request) {
        if (!(request.getPayload() instanceof TourStopDTO)) {
            return Response.error(request, Response.ERR_VALIDATION, "TourStopDTO required");
        }

        TourStopDTO stop = (TourStopDTO) request.getPayload();

        // Validate
        ValidationResult validation = validateTourStop(stop);
        if (!validation.isValid()) {
            return Response.success(request, validation);
        }

        try (Connection conn = DBConnector.getConnection()) {
            if (stop.getPoiId() <= 0 || !TourDAO.poiExists(conn, stop.getPoiId())) {
                return Response.error(request, Response.ERR_VALIDATION, "Invalid or non-existent POI for tour stop");
            }
            int stopId = TourDAO.addTourStop(conn, stop);
            if (stopId > 0) {
                ValidationResult result = ValidationResult.success("Tour stop added successfully");
                return Response.success(request, result);
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to add tour stop");
    }

    private static Response handleUpdateTourStop(Request request) {
        if (!(request.getPayload() instanceof TourStopDTO)) {
            return Response.error(request, Response.ERR_VALIDATION, "TourStopDTO required");
        }

        TourStopDTO stop = (TourStopDTO) request.getPayload();

        try (Connection conn = DBConnector.getConnection()) {
            if (stop.getPoiId() <= 0 || !TourDAO.poiExists(conn, stop.getPoiId())) {
                return Response.error(request, Response.ERR_VALIDATION, "Invalid or non-existent POI for tour stop");
            }
            if (TourDAO.updateTourStop(conn, stop)) {
                return Response.success(request, ValidationResult.success("Tour stop updated successfully"));
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to update tour stop");
    }

    private static Response handleRemoveTourStop(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Stop ID required");
        }

        int stopId = (Integer) request.getPayload();

        try (Connection conn = DBConnector.getConnection()) {
            if (TourDAO.removeTourStop(conn, stopId)) {
                return Response.success(request, ValidationResult.success("Tour stop removed successfully"));
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }

        return Response.error(request, Response.ERR_DATABASE, "Failed to remove tour stop");
    }

    // ==================== Batch Submit ====================

    private static Response handleGetMyDraft(Request request) {
        int userId = resolveUserId(request);
        MapEditRequestDTO draft = MapEditRequestDAO.getDraftRequestForUser(userId);
        return Response.success(request, draft);
    }

    private static Response handleGetPendingMapEdits(Request request) {
        List<MapEditRequestDTO> requests = MapEditRequestDAO.getPendingRequests();
        System.out.println("MapEditHandler: Found " + requests.size() + " pending map edit requests");
        return Response.success(request, requests);
    }

    private static Response handleRejectMapEdit(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Request ID required");
        }
        int reqId = (Integer) request.getPayload();

        MapEditRequestDTO reqDTO = MapEditRequestDAO.getRequest(reqId);

        try (Connection conn = DBConnector.getConnection()) {
            if (MapEditRequestDAO.updateStatus(conn, reqId, "REJECTED")) {
                if (reqDTO != null && reqDTO.getUserId() > 0) {
                    notifyEditorAboutDecision(conn, reqDTO, false);
                }
                return Response.success(request, ValidationResult.success("Request rejected"));
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }
        return Response.error(request, Response.ERR_DATABASE, "Failed to reject request");
    }

    /**
     * Save map changes as draft only. Uses SAVE_MAP_CHANGES so publishing never depends on payload draft field
     * (avoids Java deserialization issues where draft arrives as false).
     */
    private static Response handleSaveMapChanges(Request request) {
        if (!(request.getPayload() instanceof MapChanges)) {
            return Response.error(request, Response.ERR_VALIDATION, "MapChanges required");
        }
        MapChanges changes = (MapChanges) request.getPayload();
        changes.setDraft(true); // ensure Save never publishes; only "Send to manager" + manager approval publishes
        System.out.println("MapEditHandler: SAVE_MAP_CHANGES (always draft, payload draft ignored)");
        return applyMapChangesAsDraftOrSubmit(request, changes, true);
    }

    /**
     * Apply map changes as draft (applyAsDraft=true) or publish (applyAsDraft=false).
     * Non-managers must only call with applyAsDraft=true; only content managers may publish.
     */
    private static Response applyMapChangesAsDraftOrSubmit(Request request, MapChanges changes, boolean applyAsDraft) {
        String token = request.getSessionToken();
        SessionManager.SessionInfo session = token != null ? SessionManager.getInstance().validateSession(token) : null;
        int userId = (session != null ? session.userId : request.getUserId()) > 0
                ? (session != null ? session.userId : request.getUserId()) : 1;
        boolean isContentManager = session != null && isManagerRole(session.role);
        if (!applyAsDraft && !isContentManager) {
            System.out.println("MapEditHandler: BLOCKED publish path for non-manager; forcing draft");
            applyAsDraft = true;
        }
        System.out.println("MapEditHandler: applyMapChangesAsDraftOrSubmit applyAsDraft=" + applyAsDraft + " isContentManager=" + isContentManager);

        ValidationResult validation = validateAllChanges(changes);
        if (!validation.isValid()) {
            System.out.println("MapEditHandler: Validation failed - " + validation.getErrorSummary());
            return Response.success(request, validation);
        }
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) {
                return Response.error(request, Response.ERR_DATABASE, "Database connection failed");
            }
            conn.setAutoCommit(false);
            try {
                applyMapChanges(conn, changes, userId, userId, 0, validation, applyAsDraft);
                if (applyAsDraft) {
                    Integer mapId = changes.getMapId();
                    Integer cityId = changes.getCityId();
                    if (mapId != null && mapId > 0 && cityId != null) {
                        MapEditRequestDAO.upsertDraftRequest(conn, mapId, cityId, userId, changes);
                    } else if (changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty()) {
                        MapEditRequestDAO.upsertUserDraft(conn, userId, changes);
                    }
                    validation.setSuccessMessage("Changes saved. They are stored as draft and visible only to you until sent for approval.");
                } else {
                    validation.setSuccessMessage("Changes applied and released. Customers can see the new version.");
                    Integer cityId = changes.getCityId();
                    if (cityId != null && cityId > 0) {
                        notifyCustomersAboutMapUpdate(cityId, changes);
                    }
                }
                conn.commit();
                return Response.success(request, validation);
            } catch (SQLException e) {
                conn.rollback();
                return Response.error(request, Response.ERR_DATABASE, "Transaction failed: " + e.getMessage());
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }
    }

    private static Response handleSubmitMapChanges(Request request) {
        if (!(request.getPayload() instanceof MapChanges)) {
            return Response.error(request, Response.ERR_VALIDATION, "MapChanges required");
        }

        MapChanges changes = (MapChanges) request.getPayload();
        boolean asDraftFromPayload = changes.isDraft();
        System.out.println("MapEditHandler: SUBMIT_MAP_CHANGES payload.isDraft()=" + asDraftFromPayload);

        // Resolve user and role from session
        String token = request.getSessionToken();
        SessionManager.SessionInfo session = token != null ? SessionManager.getInstance().validateSession(token) : null;
        int userId = (session != null ? session.userId : request.getUserId()) > 0
                ? (session != null ? session.userId : request.getUserId()) : 1;
        boolean isContentManager = session != null && isManagerRole(session.role);

        System.out.println("MapEditHandler: userId=" + userId + ", isContentManager=" + isContentManager
                + ", mapId=" + changes.getMapId() + ", cityId=" + changes.getCityId());

        // Route: Save (draft) vs Send/Publish. Save always uses SAVE_MAP_CHANGES (never this handler).
        // Only apply as draft when payload says draft; only publish when manager explicitly chose Publish (draft=false).
        if (asDraftFromPayload) {
            return applyMapChangesAsDraftOrSubmit(request, changes, true);
        }
        if (isContentManager) {
            System.out.println("MapEditHandler: branch=PUBLISH (manager, payload draft=false)");
            return applyMapChangesAsDraftOrSubmit(request, changes, false);
        }
        System.out.println("MapEditHandler: branch=CREATE_REQUESTS (editor Send to manager)");

        // Enrich with draft POIs so manager sees drafts the employee saved earlier
        try (Connection conn = DBConnector.getConnection()) {
            if (conn != null) {
                enrichMapChangesWithDraftPoisForCity(conn, changes);
            }
        } catch (SQLException e) {
            System.out.println("MapEditHandler: Enrich draft POIs failed - " + e.getMessage());
        }

        // Validate then create approval requests
        ValidationResult validation = validateAllChanges(changes);
        if (!validation.isValid()) {
            System.out.println("MapEditHandler: Validation failed - " + validation.getErrorSummary());
            return Response.success(request, validation);
        }

        // Editor: submit for manager approval (create requests; no apply here)
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) {
                return Response.error(request, Response.ERR_DATABASE, "Database connection failed");
            }

            // Keep DRAFT so draft tours/POIs stay visible until manager approves or rejects
            Integer mapId = changes.getMapId();
            Integer cityId = changes.getCityId();
            // If client sent city but no map (e.g. map content not loaded), resolve single draft map for this city
            if ((mapId == null || mapId <= 0) && cityId != null && cityId > 0) {
                List<MapSummary> cityMaps = MapDAO.getMapsForCity(cityId, userId);
                if (cityMaps.size() == 1) {
                    mapId = cityMaps.get(0).getId();
                    changes.setMapId(mapId);
                }
            }
            if (mapId != null && mapId > 0 && cityId != null) {
                // Block resending a previously rejected delete-tour intent until the employee performs the action again
                try {
                    Set<Integer> rejectedTourIds = MapEditRequestDAO.getRejectedDeletedTourIdsForUserAndScope(conn, userId, mapId, cityId);
                    if (!rejectedTourIds.isEmpty() && changes.getDeletedTourIds() != null && !changes.getDeletedTourIds().isEmpty()) {
                        List<Integer> overlapping = new ArrayList<>();
                        for (Integer tid : changes.getDeletedTourIds()) {
                            if (tid != null && rejectedTourIds.contains(tid)) overlapping.add(tid);
                        }
                        if (!overlapping.isEmpty()) {
                            Collections.sort(overlapping);
                            String idsList = overlapping.stream().map(String::valueOf).collect(Collectors.joining(","));
                            ValidationResult err = ValidationResult.error("_general",
                                    "A request to delete this tour was already rejected. Please press Delete on the tour again, then send.");
                            err.addError("rejectedDeletedTourIds", idsList);
                            return Response.success(request, err);
                        }
                    }
                } catch (SQLException e) {
                    return Response.error(request, Response.ERR_DATABASE, e.getMessage());
                }
                // Block resending a previously rejected delete-POI intent until the employee performs the action again
                try {
                    Set<Integer> rejectedPoiIds = MapEditRequestDAO.getRejectedDeletedPoiIdsForUserAndScope(conn, userId, mapId, cityId);
                    if (!rejectedPoiIds.isEmpty() && changes.getDeletedPoiIds() != null && !changes.getDeletedPoiIds().isEmpty()) {
                        List<Integer> overlapping = new ArrayList<>();
                        for (Integer pid : changes.getDeletedPoiIds()) {
                            if (pid != null && rejectedPoiIds.contains(pid)) overlapping.add(pid);
                        }
                        if (!overlapping.isEmpty()) {
                            Collections.sort(overlapping);
                            String idsList = overlapping.stream().map(String::valueOf).collect(Collectors.joining(","));
                            ValidationResult err = ValidationResult.error("_general",
                                    "A request to delete this POI was already rejected. Please press Delete on the POI again, then send.");
                            err.addError("rejectedDeletedPoiIds", idsList);
                            return Response.success(request, err);
                        }
                    }
                } catch (SQLException e) {
                    return Response.error(request, Response.ERR_DATABASE, e.getMessage());
                }
                MapEditRequestDAO.upsertDraftRequest(conn, mapId, cityId, userId, changes);
            }

            // Split into granular requests so manager can approve/reject each POI and tour individually
            List<MapChanges> granular = splitIntoGranularRequests(changes);

            // Collect (mapId, cityId) scope so we replace any existing PENDING requests from this user for same scope
            Set<String> scopePairs = new HashSet<>();
            List<Integer> scopeMapIds = new ArrayList<>();
            List<Integer> scopeCityIds = new ArrayList<>();
            for (MapChanges gc : granular) {
                int m = gc.getMapId() != null ? gc.getMapId() : 0;
                int c = gc.getCityId() != null ? gc.getCityId() : 0;
                String key = m + "," + c;
                if (!scopePairs.contains(key)) {
                    scopePairs.add(key);
                    scopeMapIds.add(m);
                    scopeCityIds.add(c);
                }
            }
            if (mapId != null && mapId > 0 && cityId != null && cityId > 0) {
                String key = mapId + "," + cityId;
                if (!scopePairs.contains(key)) {
                    scopePairs.add(key);
                    scopeMapIds.add(mapId);
                    scopeCityIds.add(cityId);
                }
            }
            if (cityId != null && cityId > 0) {
                String key = "0," + cityId;
                if (!scopePairs.contains(key)) {
                    scopePairs.add(key);
                    scopeMapIds.add(0);
                    scopeCityIds.add(cityId);
                }
            }
            if (changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty()) {
                int firstCityId = changes.getDeletedCityIds().get(0);
                String key = "0," + firstCityId;
                if (!scopePairs.contains(key)) {
                    scopePairs.add(key);
                    scopeMapIds.add(0);
                    scopeCityIds.add(firstCityId);
                }
            }
            if (cityId != null && cityId > 0) {
                List<MapSummary> cityMapsForScope = MapDAO.getMapsForCity(cityId, userId);
                Integer mainMapIdForScope = mapId;
                for (MapSummary m : cityMapsForScope) {
                    int mid = m.getId();
                    if (mainMapIdForScope != null && mid == mainMapIdForScope) continue;
                    String key = mid + "," + cityId;
                    if (!scopePairs.contains(key)) {
                        scopePairs.add(key);
                        scopeMapIds.add(mid);
                        scopeCityIds.add(cityId);
                    }
                }
            }
            // Include each deleted map in scope (resolve cityId so we replace the right PENDING request and UI shows "(waiting for approval)")
            if (changes.getDeletedMapIds() != null && !changes.getDeletedMapIds().isEmpty()) {
                for (int mid : changes.getDeletedMapIds()) {
                    if (mid <= 0) continue;
                    int cid = 0;
                    try {
                        cid = MapDAO.getCityIdForMap(conn, mid);
                    } catch (SQLException e) {
                        System.err.println("MapEditHandler: getCityIdForMap(" + mid + ") failed: " + e.getMessage());
                    }
                    if (cid > 0) {
                        String key = mid + "," + cid;
                        if (!scopePairs.contains(key)) {
                            scopePairs.add(key);
                            scopeMapIds.add(mid);
                            scopeCityIds.add(cid);
                        }
                    }
                }
            }
            if (!scopeMapIds.isEmpty()) {
                MapEditRequestDAO.deletePendingRequestsByUserForPairs(conn, userId, scopeMapIds, scopeCityIds);
            }

            int createdCount = 0;
            for (MapChanges gc : granular) {
                enrichDeletedPoiDisplayNames(conn, gc);
                enrichDeletedTourDisplayNames(conn, gc);
                int reqMapId = gc.getMapId() != null ? gc.getMapId() : 0;
                int reqCityId = gc.getCityId() != null ? gc.getCityId() : 0;
                // Resolve map_id/city_id for delete-map-only requests so we replace the right row and UI shows "(waiting for approval)"
                if ((reqMapId == 0 || reqCityId == 0) && gc.getDeletedMapIds() != null && !gc.getDeletedMapIds().isEmpty()) {
                    int firstMid = gc.getDeletedMapIds().get(0);
                    if (firstMid > 0) {
                        try {
                            int cid = MapDAO.getCityIdForMap(conn, firstMid);
                            if (cid > 0) {
                                reqMapId = firstMid;
                                reqCityId = cid;
                                gc.setMapId(firstMid);
                                gc.setCityId(cid);
                            }
                        } catch (SQLException e) {
                            System.err.println("MapEditHandler: getCityIdForMap for request failed: " + e.getMessage());
                        }
                    }
                }
                if (MapEditRequestDAO.createRequest(conn, reqMapId, reqCityId, userId, gc) > 0) {
                    createdCount++;
                }
            }

            // Extra requests for draft POIs on other maps (split per POI)
            Integer mainMapId = mapId;
            if (cityId != null && cityId > 0) {
                List<MapSummary> cityMaps = MapDAO.getMapsForCity(cityId, userId);
                for (MapSummary m : cityMaps) {
                    int mid = m.getId();
                    if (mainMapId != null && mid == mainMapId) continue;
                    try {
                        List<Poi> draftPois = PoiDAO.getDraftPoisForMap(conn, mid);
                        for (Poi p : draftPois) {
                            MapChanges extra = new MapChanges();
                            extra.setMapId(mid);
                            extra.setCityId(cityId);
                            extra.setDraft(false);
                            extra.getAddedPois().add(p);
                            if (MapEditRequestDAO.createRequest(conn, mid, cityId, userId, extra) > 0) {
                                createdCount++;
                            }
                        }
                    } catch (SQLException e) {
                        System.out.println("MapEditHandler: Failed to create request for map " + mid + ": " + e.getMessage());
                    }
                }
            }

            // If no granular requests, only create a single "map" request when there were actual changes (avoid empty request when user just selected map and sent)
            if (createdCount == 0 && mapId != null && mapId > 0 && cityId != null && cityId > 0 && changes.hasChanges()) {
                MapChanges single = new MapChanges();
                single.setMapId(mapId);
                single.setCityId(cityId);
                single.setDraft(false);
                if (MapEditRequestDAO.createRequest(conn, mapId, cityId, userId, single) > 0) {
                    createdCount = 1;
                }
            }
            // New city with no map resolved yet (e.g. getMapsForCity returned 0): still create one request so manager can approve
            if (createdCount == 0 && cityId != null && cityId > 0) {
                MapChanges toCreate = changes;
                try {
                    // If this city is a draft (new city created by employee), always store as "Add city" so approvals show correctly (not "Delete city")
                    if (CityDAO.isCityDraft(conn, cityId)) {
                        String name = CityDAO.getCityName(conn, cityId);
                        if (name != null && !name.trim().isEmpty()) {
                            toCreate = new MapChanges();
                            toCreate.setDraft(false);
                            toCreate.setCreateNewCity(true);
                            toCreate.setNewCityName(name.trim());
                            toCreate.setNewCityDescription(changes.getNewCityDescription() != null ? changes.getNewCityDescription() : "");
                            toCreate.setNewCityPrice(changes.getNewCityPrice() != null ? changes.getNewCityPrice() : 0.0);
                            toCreate.setCityId(cityId);
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("MapEditHandler: resolve draft city for request - " + e.getMessage());
                }
                if (MapEditRequestDAO.createRequest(conn, mapId != null && mapId > 0 ? mapId : 0, cityId, userId, toCreate) > 0) {
                    createdCount = 1;
                }
            }
            // Employee sent "delete city" only (selection cleared so mapId/cityId null): create one request per deleted city for manager approval
            if (createdCount == 0 && changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty()) {
                int firstCityId = changes.getDeletedCityIds().get(0);
                MapChanges toCreate = changes;
                try {
                    // If the "deleted" city is actually a draft (new city), treat as "Add city" so it shows correctly in approvals
                    if (firstCityId > 0 && CityDAO.isCityDraft(conn, firstCityId)) {
                        String name = CityDAO.getCityName(conn, firstCityId);
                        if (name != null && !name.trim().isEmpty()) {
                            toCreate = new MapChanges();
                            toCreate.setDraft(false);
                            toCreate.setCreateNewCity(true);
                            toCreate.setNewCityName(name.trim());
                            toCreate.setNewCityDescription("");
                            toCreate.setNewCityPrice(0.0);
                            toCreate.setCityId(firstCityId);
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("MapEditHandler: resolve draft city in delete block - " + e.getMessage());
                }
                if (MapEditRequestDAO.createRequest(conn, 0, firstCityId, userId, toCreate) > 0) {
                    createdCount = 1;
                }
            }

            System.out.println("MapEditHandler: Created " + createdCount + " granular request(s) for approval");

            if (createdCount > 0) {
                notifyManagersAboutNewRequest(userId, changes, createdCount);
                String msg = createdCount + " item(s) submitted for manager approval. You can approve or reject each one individually.";
                validation = ValidationResult.success(msg);
                return Response.success(request, validation);
            }
            // Only say "No changes to send" when there was no draft city to submit (cityId not set); otherwise a draft-city-only submit should have been handled above
            Integer cid = changes.getCityId();
            if (!changes.hasChanges() && (cid == null || cid <= 0)) {
                validation = ValidationResult.success("No changes to send.");
                return Response.success(request, validation);
            }
        } catch (SQLException e) {
            System.out.println("MapEditHandler: Database error - " + e.getMessage());
            return Response.error(request, Response.ERR_DATABASE, "Database error: " + e.getMessage());
        }
        return Response.error(request, Response.ERR_DATABASE, "Failed to submit request");
    }

    private static int resolveUserId(Request request) {
        String token = request.getSessionToken();
        SessionManager.SessionInfo session = token != null ? SessionManager.getInstance().validateSession(token) : null;
        int uid = session != null ? session.userId : request.getUserId();
        return uid > 0 ? uid : 0;
    }

    /**
     * Split MapChanges into granular requests so the manager can approve/reject each POI and tour individually.
     */
    private static List<MapChanges> splitIntoGranularRequests(MapChanges changes) {
        List<MapChanges> result = new ArrayList<>();
        Integer mapId = changes.getMapId();
        Integer cityId = changes.getCityId();

        java.util.function.Supplier<MapChanges> base = () -> {
            MapChanges c = new MapChanges();
            c.setMapId(mapId);
            c.setCityId(cityId);
            c.setDraft(false);
            return c;
        };

        if (changes.isCreateNewCity()) {
            MapChanges c = base.get();
            c.setCreateNewCity(true);
            c.setNewCityName(changes.getNewCityName());
            c.setNewCityDescription(changes.getNewCityDescription());
            c.setNewCityPrice(changes.getNewCityPrice());
            c.setNewMapName(changes.getNewMapName());
            c.setNewMapDescription(changes.getNewMapDescription());
            result.add(c);
        }
        if (!changes.isCreateNewCity() && (changes.getNewMapName() != null || changes.getNewMapDescription() != null)
                && mapId != null && mapId > 0) {
            MapChanges c = base.get();
            c.setNewMapName(changes.getNewMapName());
            c.setNewMapDescription(changes.getNewMapDescription());
            result.add(c);
        }
        // New map in existing city (no createNewCity, mapId not set)
        if (!changes.isCreateNewCity() && changes.getNewMapName() != null && !changes.getNewMapName().isEmpty()
                && cityId != null && cityId > 0 && (mapId == null || mapId == 0)) {
            MapChanges c = base.get();
            c.setNewMapName(changes.getNewMapName());
            c.setNewMapDescription(changes.getNewMapDescription());
            result.add(c);
        }
        // Each "new city only" (from newCities list)
        if (changes.getNewCities() != null) {
            for (MapChanges.NewCityRequest req : changes.getNewCities()) {
                if (req.getName() == null || req.getName().trim().isEmpty()) continue;
                MapChanges c = base.get();
                c.setCreateNewCity(true);
                c.setNewCityName(req.getName().trim());
                c.setNewCityDescription(req.getDescription() != null ? req.getDescription() : "");
                c.setNewCityPrice(req.getPrice());
                result.add(c);
            }
        }
        // Each "new map in existing city" (from newMaps list)
        if (changes.getNewMaps() != null) {
            for (MapChanges.NewMapRequest req : changes.getNewMaps()) {
                if (req.getCityId() <= 0 || req.getName() == null || req.getName().trim().isEmpty()) continue;
                MapChanges c = base.get();
                c.setCityId(req.getCityId());
                c.setNewMapName(req.getName().trim());
                c.setNewMapDescription(req.getDescription() != null ? req.getDescription() : "");
                result.add(c);
            }
        }
        // City + first map: split into separate city request and map request (manager sees separately)
        if (changes.getNewCityWithMap() != null) {
            for (MapChanges.CityWithMapRequest req : changes.getNewCityWithMap()) {
                if (req.getCityName() == null || req.getCityName().trim().isEmpty()) continue;
                MapChanges cCity = base.get();
                cCity.setCreateNewCity(true);
                cCity.setNewCityName(req.getCityName().trim());
                cCity.setNewCityDescription(req.getCityDescription() != null ? req.getCityDescription() : "");
                cCity.setNewCityPrice(req.getCityPrice());
                result.add(cCity);
                if (req.getMapName() != null && !req.getMapName().trim().isEmpty()) {
                    MapChanges cMap = base.get();
                    cMap.setNewMapCityName(req.getCityName().trim());
                    cMap.setNewMapName(req.getMapName().trim());
                    cMap.setNewMapDescription(req.getMapDescription() != null ? req.getMapDescription() : "");
                    result.add(cMap);
                }
            }
        }
        Set<Integer> seenPoiIds = new HashSet<>();
        for (Poi p : changes.getAddedPois()) {
            int pid = p.getId();
            if (pid > 0 && seenPoiIds.contains(pid)) continue;
            if (pid > 0) seenPoiIds.add(pid);
            MapChanges c = base.get();
            c.getAddedPois().add(p);
            result.add(c);
        }
        seenPoiIds.clear();
        for (Poi p : changes.getUpdatedPois()) {
            if (seenPoiIds.contains(p.getId())) continue;
            seenPoiIds.add(p.getId());
            MapChanges c = base.get();
            c.getUpdatedPois().add(p);
            result.add(c);
        }
        for (int poiId : changes.getDeletedPoiIds()) {
            MapChanges c = base.get();
            c.getDeletedPoiIds().add(poiId);
            result.add(c);
        }
        for (MapChanges.PoiMapLink link : changes.getPoiMapLinks()) {
            MapChanges c = base.get();
            c.getPoiMapLinks().add(link);
            result.add(c);
        }
        for (MapChanges.PoiMapLink link : changes.getPoiMapUnlinks()) {
            MapChanges c = base.get();
            c.getPoiMapUnlinks().add(link);
            result.add(c);
        }
        Set<Integer> seenAddedTourIds = new HashSet<>();
        for (TourDTO t : changes.getAddedTours()) {
            if (t.getId() > 0 && seenAddedTourIds.contains(t.getId())) continue;
            if (t.getId() > 0) seenAddedTourIds.add(t.getId());
            MapChanges c = base.get();
            c.getAddedTours().add(t);
            result.add(c);
        }
        Set<Integer> updatedTourIds = new HashSet<>();
        for (TourDTO t : changes.getUpdatedTours()) updatedTourIds.add(t.getId());
        Set<Integer> seenUpdatedTourIds = new HashSet<>();
        for (TourDTO t : changes.getUpdatedTours()) {
            if (seenUpdatedTourIds.contains(t.getId())) continue;
            seenUpdatedTourIds.add(t.getId());
            MapChanges c = base.get();
            c.getUpdatedTours().add(t);
            int tid = t.getId();
            if (changes.getAddedStops() != null) {
                for (TourStopDTO s : changes.getAddedStops()) {
                    if (s.getTourId() == tid) c.getAddedStops().add(s);
                }
            }
            if (changes.getUpdatedStops() != null) {
                for (TourStopDTO s : changes.getUpdatedStops()) {
                    if (s.getTourId() == tid) c.getUpdatedStops().add(s);
                }
            }
            result.add(c);
        }
        java.util.Map<Integer, List<TourStopDTO>> addedByTour = new java.util.HashMap<>();
        java.util.Map<Integer, List<TourStopDTO>> updatedByTour = new java.util.HashMap<>();
        if (changes.getAddedStops() != null) {
            for (TourStopDTO s : changes.getAddedStops()) {
                if (s.getTourId() > 0 && !updatedTourIds.contains(s.getTourId())) {
                    addedByTour.computeIfAbsent(s.getTourId(), k -> new ArrayList<>()).add(s);
                }
            }
        }
        if (changes.getUpdatedStops() != null) {
            for (TourStopDTO s : changes.getUpdatedStops()) {
                if (s.getTourId() > 0 && !updatedTourIds.contains(s.getTourId())) {
                    updatedByTour.computeIfAbsent(s.getTourId(), k -> new ArrayList<>()).add(s);
                }
            }
        }
        Set<Integer> stopChangeTours = new HashSet<>(addedByTour.keySet());
        stopChangeTours.addAll(updatedByTour.keySet());
        for (int tid : stopChangeTours) {
            MapChanges c = base.get();
            c.getAddedStops().addAll(addedByTour.getOrDefault(tid, List.of()));
            c.getUpdatedStops().addAll(updatedByTour.getOrDefault(tid, List.of()));
            result.add(c);
        }
        if (changes.getDeletedStopIds() != null && !changes.getDeletedStopIds().isEmpty()) {
            MapChanges c = base.get();
            c.getDeletedStopIds().addAll(changes.getDeletedStopIds());
            result.add(c);
        }
        for (int tourId : changes.getDeletedTourIds()) {
            MapChanges c = base.get();
            c.getDeletedTourIds().add(tourId);
            result.add(c);
        }
        if (changes.getDeletedMapIds() != null) {
            for (int mid : changes.getDeletedMapIds()) {
                MapChanges c = base.get();
                c.getDeletedMapIds().add(mid);
                result.add(c);
            }
        }
        return result;
    }

    /**
     * When submitting for manager approval, add all draft POIs for the current map and/or city
     * so the manager sees what the employee saved earlier with "Save changes".
     * If mapId is set: enrich with draft POIs for that map. If mapId is missing but cityId is set:
     * pick the first map in the city that has draft POIs and use it for the main request.
     */
    private static void enrichMapChangesWithDraftPoisForCity(Connection conn, MapChanges changes) {
        Integer mapId = changes.getMapId();
        Integer cityId = changes.getCityId();

        if (mapId == null || mapId <= 0) {
            if (cityId == null || cityId <= 0) return;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM maps WHERE city_id = ? ORDER BY id")) {
                stmt.setInt(1, cityId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int mid = rs.getInt("id");
                    List<Poi> draftPois = PoiDAO.getDraftPoisForMap(conn, mid);
                    if (!draftPois.isEmpty()) {
                        changes.setMapId(mid);
                        java.util.Set<Integer> seen = new java.util.HashSet<>();
                        for (Poi p : changes.getAddedPois()) seen.add(p.getId());
                        for (Poi p : draftPois) {
                            if (p.getId() > 0 && !seen.contains(p.getId())) {
                                changes.getAddedPois().add(p);
                                seen.add(p.getId());
                            }
                        }
                        System.out.println("MapEditHandler: Enriched with " + draftPois.size() + " draft POI(s) for map " + mid + " (city " + cityId + ")");
                        return;
                    }
                }
            } catch (SQLException e) {
                System.err.println("MapEditHandler: Failed to resolve map for city " + cityId + ": " + e.getMessage());
            }
            return;
        }

        try {
            List<Poi> draftPois = PoiDAO.getDraftPoisForMap(conn, mapId);
            if (draftPois.isEmpty()) return;
            java.util.Set<Integer> alreadyInRequest = new java.util.HashSet<>();
            for (Poi p : changes.getAddedPois()) alreadyInRequest.add(p.getId());
            for (Poi p : changes.getUpdatedPois()) alreadyInRequest.add(p.getId());
            int added = 0;
            for (Poi p : draftPois) {
                if (p.getId() > 0 && !alreadyInRequest.contains(p.getId())) {
                    changes.getAddedPois().add(p);
                    alreadyInRequest.add(p.getId());
                    added++;
                }
            }
            if (added > 0) {
                System.out.println("MapEditHandler: Enriched request with " + added + " draft POI(s) for map " + mapId);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && !e.getMessage().contains("approved")) {
                System.err.println("MapEditHandler: Failed to load draft POIs for map " + mapId + ": " + e.getMessage());
            }
        }
    }

    private static boolean isManagerRole(String role) {
        return "CONTENT_MANAGER".equals(role) || "COMPANY_MANAGER".equals(role);
    }

    private static Response handleApproveMapEdit(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Request ID required");
        }
        int reqId = (Integer) request.getPayload();

        MapEditRequestDTO reqDTO = MapEditRequestDAO.getRequest(reqId);
        if (reqDTO == null)
            return Response.error(request, Response.ERR_NOT_FOUND, "Request not found");

        MapChanges changes = reqDTO.getChanges();
        if (changes == null)
            return Response.error(request, Response.ERR_INTERNAL, "Invalid request data");
        // Ensure mapId is set so approveAllDraftLinksForMap runs for the correct map (e.g. granular or legacy requests)
        if ((changes.getMapId() == null || changes.getMapId() <= 0) && reqDTO.getMapId() > 0)
            changes.setMapId(reqDTO.getMapId());

        // Execute changes in transaction
        ValidationResult validation = new ValidationResult();
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) {
                return Response.error(request, Response.ERR_DATABASE, "Database connection failed");
            }

            conn.setAutoCommit(false);

            try {
                int creatorId = reqDTO.getUserId() > 0 ? reqDTO.getUserId() : 2;
                // approved_by must reference a valid user (FK); avoid 0
                int approverId = request.getUserId() > 0 ? request.getUserId() : creatorId;
                applyMapChanges(conn, changes, creatorId, approverId, reqId, validation);

                // Create tour route maps for any approved tours (added or updated)
                java.util.Set<Integer> tourIds = new java.util.HashSet<>();
                tourIds.addAll(validation.getCreatedTourIds());
                for (TourDTO t : changes.getUpdatedTours()) tourIds.add(t.getId());
                for (Integer tid : tourIds) {
                    if (tid == null || tid <= 0) continue;
                    try {
                        TourDTO tour = TourDAO.getTourById(conn, tid);
                        if (tour != null && tour.getStops() != null && !tour.getStops().isEmpty()) {
                            int mapId = MapDAO.createTourMap(conn, tour, approverId);
                            if (mapId > 0) {
                                System.out.println("MapEditHandler: Created tour map " + mapId + " for tour " + tid);
                            }
                        }
                    } catch (SQLException e) {
                        System.err.println("MapEditHandler: Failed to create tour map for tour " + tid + ": " + e.getMessage());
                    }
                }

                conn.commit();
                validation.setSuccessMessage("Request approved and changes applied successfully.");
                System.out.println("MapEditHandler: Approved request " + reqId);

                if (reqDTO.getUserId() > 0) {
                    notifyEditorAboutDecision(null, reqDTO, true);
                }

                Integer cityId = changes.getCityId();
                if (cityId != null && cityId > 0) {
                    notifyCustomersAboutMapUpdate(cityId, changes);
                }
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("MapEditHandler: Transaction rolled back - " + e.getMessage());
                return Response.error(request, Response.ERR_DATABASE, "Transaction failed: " + e.getMessage());
            }

        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }

        return Response.success(request, validation);
    }

    /**
     * Applies map changes in a transaction (caller must commit).
     * Creates/updates city, map, POIs, POI-map links, tours, tour stops, and a map version (APPROVED).
     *
     * @param conn               open connection (autoCommit false)
     * @param changes            the change set
     * @param creatorUserId      user id for version created_by
     * @param approverUserId     user id for version approved_by and audit log
     * @param mapEditRequestId   if > 0, request is marked APPROVED; if 0, direct release (no request)
     * @param validation         result to fill with created IDs and messages
     */
    private static void applyMapChanges(Connection conn, MapChanges changes, int creatorUserId, int approverUserId,
            int mapEditRequestId, ValidationResult validation) throws SQLException {
        applyMapChanges(conn, changes, creatorUserId, approverUserId, mapEditRequestId, validation, false);
    }

    private static void applyMapChanges(Connection conn, MapChanges changes, int creatorUserId, int approverUserId,
            int mapEditRequestId, ValidationResult validation, boolean asDraft) throws SQLException {
        // approved_by must reference a valid user (FK)
        if (approverUserId <= 0) approverUserId = creatorUserId;

        // Manager: delete cities (and all their maps and tours) when not draft
        if (!asDraft && changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty()) {
            for (int cityId : changes.getDeletedCityIds()) {
                if (cityId <= 0) continue;
                List<Integer> mapIds = MapDAO.getMapIdsByCityId(conn, cityId);
                for (int mapId : mapIds) {
                    MapDAO.deleteMap(conn, mapId); // deletes map's POIs everywhere (tour stops, map_pois, pois) then the map
                }
                List<TourDTO> tours = TourDAO.getToursForCity(conn, cityId);
                for (TourDTO t : tours) {
                    TourDAO.deleteTour(conn, t.getId());
                }
                CityDAO.deleteCity(conn, cityId);
            }
            MapEditRequestDAO.deleteUserDraft(conn, approverUserId);
        }

        // Employee Save: immediately delete draft city/map/POI (no need to send to manager)
        if (asDraft) {
            if (changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty()) {
                List<Integer> toRemove = new ArrayList<>();
                for (int cityId : changes.getDeletedCityIds()) {
                    if (cityId <= 0) continue;
                    try {
                        if (CityDAO.isCityDraft(conn, cityId)) {
                            List<Integer> mapIds = MapDAO.getMapIdsByCityId(conn, cityId);
                            for (int mapId : mapIds) {
                                MapDAO.deleteMap(conn, mapId); // deletes map's POIs everywhere then the map
                            }
                            List<TourDTO> tours = TourDAO.getToursForCity(conn, cityId);
                            for (TourDTO t : tours) {
                                TourDAO.deleteTour(conn, t.getId());
                            }
                            CityDAO.deleteCity(conn, cityId);
                            toRemove.add(cityId);
                        }
                    } catch (SQLException e) {
                        System.err.println("MapEditHandler: Immediate delete draft city " + cityId + ": " + e.getMessage());
                    }
                }
                changes.getDeletedCityIds().removeAll(toRemove);
                if (changes.getDeletedCityIds().isEmpty()) {
                    MapEditRequestDAO.deleteUserDraft(conn, creatorUserId);
                }
            }
            if (changes.getDeletedMapIds() != null && !changes.getDeletedMapIds().isEmpty()) {
                List<Integer> toRemove = new ArrayList<>();
                for (int mapId : changes.getDeletedMapIds()) {
                    if (mapId <= 0) continue;
                    try {
                        if (MapDAO.isMapDraft(conn, mapId)) {
                            MapDAO.deleteMap(conn, mapId); // deletes map's POIs everywhere then the map
                            toRemove.add(mapId);
                        }
                    } catch (SQLException e) {
                        System.err.println("MapEditHandler: Immediate delete draft map " + mapId + ": " + e.getMessage());
                    }
                }
                changes.getDeletedMapIds().removeAll(toRemove);
            }
            if (changes.getDeletedPoiIds() != null && !changes.getDeletedPoiIds().isEmpty()) {
                List<Integer> toRemove = new ArrayList<>();
                for (int poiId : changes.getDeletedPoiIds()) {
                    if (poiId <= 0) continue;
                    try {
                        if (!PoiDAO.hasPoiAnyApprovedLink(conn, poiId)) {
                            if (!PoiDAO.isPoiUsedInTour(conn, poiId)) {
                                PoiDAO.deletePoi(conn, poiId);
                                toRemove.add(poiId);
                            }
                        }
                    } catch (SQLException e) {
                        System.err.println("MapEditHandler: Immediate delete draft POI " + poiId + ": " + e.getMessage());
                    }
                }
                changes.getDeletedPoiIds().removeAll(toRemove);
            }
        }

        // Create new city if requested (approved = visible to all only when !asDraft)
        if (changes.isCreateNewCity()) {
            int cityId;
            if (!asDraft) {
                // Manager approval: approve existing draft city if one exists, so catalog shows it
                Integer existingId = CityDAO.findUnapprovedCityByName(conn, changes.getNewCityName());
                if (existingId != null) {
                    CityDAO.setCityApproved(conn, existingId);
                    cityId = existingId;
                } else {
                    // Fallback: city may exist but findUnapprovedCityByName returned null (e.g. DB column missing); approve by name
                    Integer anyId = CityDAO.findCityIdByName(conn, changes.getNewCityName() != null ? changes.getNewCityName().trim() : null);
                    if (anyId != null) {
                        CityDAO.setCityApproved(conn, anyId);
                        cityId = anyId;
                    } else {
                        cityId = CityDAO.createCity(conn,
                                changes.getNewCityName(),
                                changes.getNewCityDescription(),
                                changes.getNewCityPrice() != null ? changes.getNewCityPrice() : 0,
                                creatorUserId,
                                true);
                    }
                }
            } else {
                cityId = CityDAO.createCity(conn,
                        changes.getNewCityName(),
                        changes.getNewCityDescription(),
                        changes.getNewCityPrice() != null ? changes.getNewCityPrice() : 0,
                        creatorUserId,
                        false);
            }
            validation.setCreatedCityId(cityId);
            changes.setCityId(cityId);
        }

        // Create new city + first map (from newCityWithMap list) – each as draft or approved
        if (changes.getNewCityWithMap() != null) {
            for (MapChanges.CityWithMapRequest req : changes.getNewCityWithMap()) {
                if (req.getCityName() == null || req.getCityName().trim().isEmpty()) continue;
                int cid;
                if (!asDraft) {
                    Integer existingId = CityDAO.findUnapprovedCityByName(conn, req.getCityName().trim());
                    if (existingId != null) {
                        CityDAO.setCityApproved(conn, existingId);
                        cid = existingId;
                    } else {
                        Integer anyId = CityDAO.findCityIdByName(conn, req.getCityName().trim());
                        if (anyId != null) {
                            CityDAO.setCityApproved(conn, anyId);
                            cid = anyId;
                        } else {
                            cid = CityDAO.createCity(conn, req.getCityName().trim(),
                                    req.getCityDescription() != null ? req.getCityDescription() : "",
                                    req.getCityPrice(), creatorUserId, true);
                        }
                    }
                } else {
                    cid = CityDAO.createCity(conn, req.getCityName().trim(),
                            req.getCityDescription() != null ? req.getCityDescription() : "",
                            req.getCityPrice(), creatorUserId, false);
                }
                if (cid > 0 && req.getMapName() != null && !req.getMapName().trim().isEmpty()) {
                    if (!asDraft) {
                        Integer existingMapId = MapDAO.findUnapprovedMapByCityAndName(conn, cid, req.getMapName().trim());
                        if (existingMapId != null) {
                            MapDAO.setMapApproved(conn, existingMapId);
                        } else {
                            MapDAO.createMap(conn, cid, req.getMapName().trim(),
                                    req.getMapDescription() != null ? req.getMapDescription() : "",
                                    creatorUserId, true);
                        }
                    } else {
                        MapDAO.createMap(conn, cid, req.getMapName().trim(),
                                req.getMapDescription() != null ? req.getMapDescription() : "",
                                creatorUserId, false);
                    }
                }
                if (validation.getCreatedCityId() == null || validation.getCreatedCityId() <= 0) {
                    validation.setCreatedCityId(cid);
                }
            }
        }

        // Create additional new cities (city only, no first map) – each as draft or approved
        if (changes.getNewCities() != null) {
            for (MapChanges.NewCityRequest req : changes.getNewCities()) {
                if (req.getName() == null || req.getName().trim().isEmpty()) continue;
                int cid;
                if (!asDraft) {
                    Integer existingId = CityDAO.findUnapprovedCityByName(conn, req.getName().trim());
                    if (existingId != null) {
                        CityDAO.setCityApproved(conn, existingId);
                        cid = existingId;
                    } else {
                        Integer anyId = CityDAO.findCityIdByName(conn, req.getName().trim());
                        if (anyId != null) {
                            CityDAO.setCityApproved(conn, anyId);
                            cid = anyId;
                        } else {
                            cid = CityDAO.createCity(conn, req.getName().trim(),
                                    req.getDescription() != null ? req.getDescription() : "",
                                    req.getPrice(), creatorUserId, true);
                        }
                    }
                } else {
                    cid = CityDAO.createCity(conn, req.getName().trim(),
                            req.getDescription() != null ? req.getDescription() : "",
                            req.getPrice(), creatorUserId, false);
                }
                if (cid > 0) {
                    validation.setCreatedCityId(cid);
                    changes.setCityId(cid);
                }
            }
        }

        // When manager publishes: if the request has a cityId (e.g. draft city from editor or add-city request), ensure that city is approved
        if (!asDraft && changes.getCityId() != null && changes.getCityId() > 0) {
            try {
                if (CityDAO.isCityDraft(conn, changes.getCityId())) {
                    CityDAO.setCityApproved(conn, changes.getCityId());
                }
            } catch (SQLException e) {
                System.err.println("MapEditHandler: approve city by id on publish - " + e.getMessage());
            }
        }

        // Create new map only when there is no existing map (employee created a new map in the request)
        // If mapId is already set, we're editing an existing map — update it, do not create a duplicate
        boolean hasExistingMap = changes.getMapId() != null && changes.getMapId() > 0;
        if (!hasExistingMap && changes.getNewMapName() != null && !changes.getNewMapName().isEmpty() && changes.getCityId() != null) {
            int mapId;
            if (!asDraft) {
                // Manager approval: approve existing draft map if one exists
                Integer existingMapId = MapDAO.findUnapprovedMapByCityAndName(conn, changes.getCityId(), changes.getNewMapName());
                if (existingMapId != null) {
                    MapDAO.setMapApproved(conn, existingMapId);
                    mapId = existingMapId;
                } else {
                    mapId = MapDAO.createMap(conn,
                            changes.getCityId(),
                            changes.getNewMapName(),
                            changes.getNewMapDescription() != null ? changes.getNewMapDescription() : "",
                            creatorUserId,
                            true);
                }
                // When approving a map, ensure its city is also approved so city does not stay [draft]
                CityDAO.setCityApproved(conn, changes.getCityId());
            } else {
                mapId = MapDAO.createMap(conn,
                        changes.getCityId(),
                        changes.getNewMapName(),
                        changes.getNewMapDescription() != null ? changes.getNewMapDescription() : "",
                        creatorUserId,
                        false);
            }
            validation.setCreatedMapId(mapId);
            changes.setMapId(mapId);
        } else if (hasExistingMap && (changes.getNewMapName() != null || changes.getNewMapDescription() != null)) {
            // Update existing map name/description (employee changed map info)
            MapDAO.updateMap(conn, changes.getMapId().intValue(),
                    changes.getNewMapName() != null ? changes.getNewMapName() : "",
                    changes.getNewMapDescription() != null ? changes.getNewMapDescription() : "");
        }

        // Create new maps in existing cities (from newMaps list)
        if (changes.getNewMaps() != null) {
            for (MapChanges.NewMapRequest req : changes.getNewMaps()) {
                if (req.getCityId() <= 0 || req.getName() == null || req.getName().trim().isEmpty()) continue;
                int mapId = -1;
                if (!asDraft) {
                    Integer existingId = MapDAO.findUnapprovedMapByCityAndName(conn, req.getCityId(), req.getName().trim());
                    if (existingId != null) {
                        MapDAO.setMapApproved(conn, existingId);
                        mapId = existingId;
                    } else {
                        mapId = MapDAO.createMap(conn, req.getCityId(), req.getName().trim(),
                                req.getDescription() != null ? req.getDescription() : "",
                                creatorUserId, true);
                    }
                    CityDAO.setCityApproved(conn, req.getCityId());
                } else {
                    mapId = MapDAO.createMap(conn, req.getCityId(), req.getName().trim(),
                            req.getDescription() != null ? req.getDescription() : "",
                            creatorUserId, false);
                }
                if (mapId > 0) {
                    if (validation.getCreatedMapId() == null)
                        validation.setCreatedMapId(mapId);
                    changes.setMapId(mapId); // so POI linking and rest of applyMapChanges use this map
                }
            }
        }

        // Create new map in city resolved by name (when city was created in same batch / separate request)
        if (changes.getNewMapName() != null && !changes.getNewMapName().isEmpty()
                && changes.getNewMapCityName() != null && !changes.getNewMapCityName().trim().isEmpty()
                && (changes.getCityId() == null || changes.getCityId() == 0)) {
            Integer resolvedCityId = CityDAO.findUnapprovedCityByName(conn, changes.getNewMapCityName().trim());
            if (resolvedCityId == null) {
                resolvedCityId = CityDAO.findCityIdByName(conn, changes.getNewMapCityName().trim());
            }
            if (resolvedCityId != null && resolvedCityId > 0) {
                if (!asDraft) {
                    Integer existingMapId = MapDAO.findUnapprovedMapByCityAndName(conn, resolvedCityId, changes.getNewMapName().trim());
                    if (existingMapId != null) {
                        MapDAO.setMapApproved(conn, existingMapId);
                    } else {
                        MapDAO.createMap(conn, resolvedCityId, changes.getNewMapName().trim(),
                                changes.getNewMapDescription() != null ? changes.getNewMapDescription() : "",
                                creatorUserId, true);
                    }
                    CityDAO.setCityApproved(conn, resolvedCityId);
                } else {
                    MapDAO.createMap(conn, resolvedCityId, changes.getNewMapName().trim(),
                            changes.getNewMapDescription() != null ? changes.getNewMapDescription() : "",
                            creatorUserId, false);
                }
            }
        }

        // Add POIs: create only new ones (id==0); for existing draft POIs (id>0), just update map_pois to approved (draft save and publish both persist them)
        // Skip any POI that is in deletedPoiIds (user deleted the draft POI this save)
        java.util.Set<Integer> deletedPoiIdSet = (changes.getDeletedPoiIds() != null && !changes.getDeletedPoiIds().isEmpty())
                ? new java.util.HashSet<>(changes.getDeletedPoiIds()) : java.util.Collections.emptySet();
        boolean linkApproved = !asDraft;
        int linkedByUserId = linkApproved ? 0 : creatorUserId;
        int displayOrder = 0;
        List<Poi> addedPoisList = changes.getAddedPois();
        if (addedPoisList == null) addedPoisList = java.util.Collections.emptyList();
        for (Poi poi : addedPoisList) {
            if (deletedPoiIdSet.contains(poi.getId())) continue;
            int poiId;
            if (poi.getId() > 0) {
                // Existing draft POI: do NOT create duplicate; update map_pois link to approved
                poiId = poi.getId();
                if (changes.getMapId() != null && changes.getMapId() > 0) {
                    PoiDAO.linkPoiToMap(conn, changes.getMapId(), poiId, displayOrder++, linkApproved, linkedByUserId);
                }
            } else {
                // New POI: create and link
                poiId = PoiDAO.createPoi(conn, poi);
                if (poiId > 0) {
                    validation.getCreatedPoiIds().add(poiId);
                    if (changes.getMapId() != null && changes.getMapId() > 0) {
                        PoiDAO.linkPoiToMap(conn, changes.getMapId(), poiId, displayOrder++, linkApproved, linkedByUserId);
                    }
                }
            }
        }

        // Update POIs
        for (Poi poi : changes.getUpdatedPois()) {
            PoiDAO.updatePoi(conn, poi);
        }

        // Link/unlink POIs (POI can appear in multiple maps of the same city)
        // Resolve poi_id=0 (new POI from this request) to created POI id; single index shared with tour stops
        java.util.List<Integer> createdPoiIds = validation.getCreatedPoiIds();
        int[] newPoiIndex = {0};
        if (changes.getPoiMapLinks() != null) {
        for (MapChanges.PoiMapLink link : changes.getPoiMapLinks()) {
            int poiId = link.poiId > 0 ? link.poiId : resolvePoiId(link.poiId, createdPoiIds, newPoiIndex);
            if (poiId <= 0 || deletedPoiIdSet.contains(poiId)) continue;
            PoiDAO.linkPoiToMap(conn, link.mapId, poiId, link.displayOrder, linkApproved, linkedByUserId);
        }
        }
        // Unlink POIs from map – skip when asDraft (employee Save changes); requires manager approval
        if (!asDraft && changes.getPoiMapUnlinks() != null) {
            for (MapChanges.PoiMapLink link : changes.getPoiMapUnlinks()) {
                int poiId = link.poiId > 0 ? link.poiId : resolvePoiId(link.poiId, createdPoiIds, newPoiIndex);
                if (poiId <= 0) continue;
                PoiDAO.unlinkPoiFromMap(conn, link.mapId, poiId);
                // If no other map uses this POI, delete it fully from the city so it does not appear in "Add existing POI to map"
                try {
                    if (PoiDAO.countMapsLinkedToPoi(conn, poiId) == 0) {
                        PoiDAO.deletePoiCompletely(conn, poiId);
                    }
                } catch (SQLException e) {
                    System.err.println("MapEditHandler: delete POI after unlink failed for " + poiId + ": " + e.getMessage());
                }
            }
        }

        // When manager publishes: approve all draft POI links on this map (so Save-then-Publish works) and the map itself
        if (!asDraft && changes.getMapId() != null && changes.getMapId() > 0) {
            PoiDAO.approveAllDraftLinksForMap(conn, changes.getMapId());
            MapDAO.setMapApproved(conn, changes.getMapId());
        }

        // Recompute and store distances between POIs on this map (for tour planning)
        if (changes.getMapId() != null && changes.getMapId() > 0) {
            List<Poi> mapPois = PoiDAO.getPoisForMap(changes.getMapId());
            if (mapPois != null && mapPois.size() >= 2) {
                try {
                    PoiDistanceDAO.recomputeAndStoreDistances(conn, mapPois);
                } catch (SQLException e) {
                    System.err.println("MapEditHandler: Failed to recompute POI distances: " + e.getMessage());
                }
            }
        }

        // Add/update tours and stops – apply on both Save (draft) and Publish so the same tour row exists; Publish then just "publishes" it (no duplicate).
        // Delete tours/stops – only when !asDraft (manager approval).
        java.util.List<Integer> createdTourIds = validation.getCreatedTourIds();
        for (TourDTO tour : changes.getAddedTours()) {
            int tourId;
            // Ensure tour has cityId from request so Save creates with correct city_id and Publish lookup finds it (avoids duplicate tour in catalogue).
            if (changes.getCityId() != null && changes.getCityId() > 0 && tour.getCityId() <= 0) {
                tour.setCityId(changes.getCityId());
            }
            if (tour.getId() > 0) {
                // Tour already in DB (e.g. created on prior Save draft); update only – do not create duplicate
                tourId = tour.getId();
                TourDAO.updateTour(conn, tour);
                createdTourIds.add(tourId);
            } else {
                // Resolve by city+name so Save-then-Publish updates the same tour instead of creating a duplicate.
                // Exclude tours pending deletion so delete-then-create-same-name yields a new tour, not the old data.
                java.util.Set<Integer> excludeIds = (changes.getDeletedTourIds() != null && !changes.getDeletedTourIds().isEmpty())
                        ? new java.util.HashSet<>(changes.getDeletedTourIds()) : null;
                int lookupCityId = tour.getCityId() > 0 ? tour.getCityId() : (changes.getCityId() != null ? changes.getCityId() : 0);
                Integer existingId = lookupCityId > 0 && tour.getName() != null && !tour.getName().trim().isEmpty()
                        ? TourDAO.findTourIdByCityAndNameExcluding(conn, lookupCityId, tour.getName().trim(), excludeIds)
                        : null;
                if (existingId != null && existingId > 0) {
                    tourId = existingId;
                    tour.setId(tourId);
                    TourDAO.updateTour(conn, tour);
                    createdTourIds.add(tourId);
                    if (!asDraft && tour.getStops() != null && !tour.getStops().isEmpty()) {
                        for (TourStopDTO stop : tour.getStops()) {
                            int effectiveTourId = stop.getTourId() > 0 ? stop.getTourId() : tourId;
                            int effectivePoiId = resolvePoiId(stop.getPoiId(), validation.getCreatedPoiIds(), newPoiIndex);
                            if (effectivePoiId <= 0 || !TourDAO.poiExists(conn, effectivePoiId)) continue;
                            TourStopDTO resolvedStop = new TourStopDTO(0, effectiveTourId, effectivePoiId,
                                    stop.getPoiName(), stop.getPoiCategory(), stop.getStopOrder(), stop.getNotes());
                            TourDAO.addTourStop(conn, resolvedStop);
                        }
                    }
                } else {
                    tourId = TourDAO.createTour(conn, tour);
                    createdTourIds.add(tourId);
                    if (!asDraft && tour.getStops() != null && !tour.getStops().isEmpty()) {
                        for (TourStopDTO stop : tour.getStops()) {
                            int effectiveTourId = stop.getTourId() > 0 ? stop.getTourId() : tourId;
                            int effectivePoiId = resolvePoiId(stop.getPoiId(), validation.getCreatedPoiIds(), newPoiIndex);
                            if (effectivePoiId <= 0 || !TourDAO.poiExists(conn, effectivePoiId)) continue;
                            TourStopDTO resolvedStop = new TourStopDTO(0, effectiveTourId, effectivePoiId,
                                    stop.getPoiName(), stop.getPoiCategory(), stop.getStopOrder(), stop.getNotes());
                            TourDAO.addTourStop(conn, resolvedStop);
                        }
                    }
                }
            }
        }
        for (TourDTO tour : changes.getUpdatedTours()) {
            TourDAO.updateTour(conn, tour);
        }
        if (!asDraft && changes.getDeletedTourIds() != null) {
            java.util.Set<Integer> addedOrUpdatedTourIds = new java.util.HashSet<>();
            for (TourDTO t : changes.getAddedTours()) { if (t.getId() > 0) addedOrUpdatedTourIds.add(t.getId()); }
            for (TourDTO t : changes.getUpdatedTours()) addedOrUpdatedTourIds.add(t.getId());
            addedOrUpdatedTourIds.addAll(validation.getCreatedTourIds()); // include ids we created this request
            for (int tourId : changes.getDeletedTourIds()) {
                if (addedOrUpdatedTourIds.contains(tourId)) continue; // same request adds/updates this tour – do not delete
                TourDAO.deleteTour(conn, tourId);
            }
        }
        // Add/update/delete tour stops only on Publish – on Save (draft) we only persist POIs and store intent in draft; applying stops here would make the new POI appear published
        // Process deletes BEFORE updates so unique (tour_id, stop_order) is not violated when renumbering
        if (!asDraft) {
            if (changes.getDeletedStopIds() != null) {
                for (int stopId : changes.getDeletedStopIds()) {
                    TourDAO.removeTourStop(conn, stopId);
                }
            }
            if (changes.getAddedStops() != null) {
                for (TourStopDTO stop : changes.getAddedStops()) {
                    if (stop.getTourId() <= 0) continue;
                    int effectivePoiId = resolvePoiId(stop.getPoiId(), validation.getCreatedPoiIds(), newPoiIndex);
                    if (effectivePoiId <= 0 || !TourDAO.poiExists(conn, effectivePoiId)) continue;
                    TourStopDTO toInsert = new TourStopDTO(0, stop.getTourId(), effectivePoiId, stop.getPoiName(),
                            stop.getPoiCategory(), stop.getStopOrder(), stop.getNotes());
                    TourDAO.addTourStop(conn, toInsert);
                }
            }
            if (changes.getUpdatedStops() != null) {
                for (TourStopDTO stop : changes.getUpdatedStops()) {
                    int effectivePoiId = resolvePoiId(stop.getPoiId(), validation.getCreatedPoiIds(), newPoiIndex);
                    if (effectivePoiId <= 0 || !TourDAO.poiExists(conn, effectivePoiId)) continue;
                    TourStopDTO toUpdate = new TourStopDTO(stop.getId(), stop.getTourId(), effectivePoiId,
                            stop.getPoiName(), stop.getPoiCategory(), stop.getStopOrder(), stop.getNotes());
                    TourDAO.updateTourStop(conn, toUpdate);
                }
            }
        }
        Integer cityIdForTours = changes.getCityId();
        if (cityIdForTours != null && cityIdForTours > 0) {
            java.util.Set<Integer> tourIds = new java.util.HashSet<>();
            for (int tid : validation.getCreatedTourIds()) tourIds.add(tid);
            for (TourDTO t : changes.getUpdatedTours()) tourIds.add(t.getId());
            for (TourStopDTO s : changes.getAddedStops()) if (s.getTourId() > 0) tourIds.add(s.getTourId());
            for (TourStopDTO s : changes.getUpdatedStops()) tourIds.add(s.getTourId());
            if (!asDraft) {
                for (int stopId : changes.getDeletedStopIds()) {
                    Integer tid = TourDAO.getTourIdForStop(conn, stopId);
                    if (tid != null) tourIds.add(tid);
                }
            }
            for (int tourId : tourIds) {
                try {
                    TourDAO.recomputeAndUpdateTourDistance(conn, tourId);
                } catch (SQLException e) {
                    System.err.println("MapEditHandler: Failed to recompute tour distance for tour " + tourId + ": " + e.getMessage());
                }
            }
        }

        // Delete POIs – skip when asDraft (employee Save changes); requires manager approval
        if (!asDraft && changes.getDeletedPoiIds() != null) {
            for (int poiId : changes.getDeletedPoiIds()) {
                PoiDAO.deletePoi(conn, poiId);
            }
        }

        // Create map version — DRAFT (editor only) or APPROVED (customers can see)
        // Skip if map was explicitly deleted or CASCADE-deleted (e.g. deleting a tour deletes its dedicated tour map)
        boolean mapBeingDeleted = changes.getDeletedMapIds() != null && changes.getDeletedMapIds().contains(changes.getMapId());
        boolean mapStillExists = false;
        if (changes.getMapId() != null && changes.getMapId() > 0 && !mapBeingDeleted) {
            try {
                mapStillExists = MapDAO.getCityIdForMap(conn, changes.getMapId()) > 0;
            } catch (SQLException e) {
                mapStillExists = false;
            }
        }
        if (mapStillExists) {
            String description = buildChangesDescription(conn, changes);
            String initialStatus = asDraft ? "DRAFT" : "PENDING";
            int versionId = MapVersionDAO.createVersion(conn, changes.getMapId(), creatorUserId, description, initialStatus);
            if (versionId > 0) {
                validation.setCreatedVersionId(versionId);
                if (!asDraft) {
                    MapVersionDAO.updateStatus(conn, versionId, "APPROVED", approverUserId, null);
                    AuditLogDAO.log(conn, AuditLogDAO.ACTION_VERSION_PUBLISHED, approverUserId,
                            AuditLogDAO.ENTITY_MAP_VERSION, versionId,
                            "from_request", mapEditRequestId > 0 ? String.valueOf(mapEditRequestId) : "direct");
                }
            }
        }

        // Delete maps (e.g. Remove map) – only when not draft; employee Save stores in draft, manager approval executes
        if (!asDraft && changes.getDeletedMapIds() != null) {
            for (int mapIdToDelete : changes.getDeletedMapIds()) {
                MapDAO.deleteMap(conn, mapIdToDelete);
            }
        }

        if (mapEditRequestId > 0) {
            MapEditRequestDAO.updateStatus(conn, mapEditRequestId, "APPROVED");
        }

        // Clear draft for this map+user when publishing so the UI does not show duplicate (draft + published)
        if (!asDraft && changes.getMapId() != null && changes.getMapId() > 0) {
            MapEditRequestDAO.deleteDraftForMapUser(conn, changes.getMapId(), creatorUserId);
        }
    }

    /**
     * Notify all content managers and company managers when an employee submits a map edit request.
     */
    private static void notifyManagersAboutNewRequest(int editorUserId, MapChanges changes, int requestCount) {
        try {
            server.dao.UserDAO.UserInfo editor = server.dao.UserDAO.findById(editorUserId);
            String editorName = editor != null ? editor.username : "An employee";

            String title = "New Map Edit Request(s)";

            StringBuilder body = new StringBuilder();
            body.append(editorName).append(" has submitted ").append(requestCount)
                    .append(requestCount == 1 ? " edit request" : " edit requests").append(" for approval.\n\n");
            String mapName = null;
            String cityName = null;
            if (changes != null) {
                mapName = changes.getNewMapName();
                if ((mapName == null || mapName.isEmpty()) && changes.getNewMaps() != null && !changes.getNewMaps().isEmpty())
                    mapName = changes.getNewMaps().get(0).getName();
                cityName = changes.getNewCityName();
                if ((cityName == null || cityName.isEmpty()) && changes.getNewCityWithMap() != null && !changes.getNewCityWithMap().isEmpty())
                    cityName = changes.getNewCityWithMap().get(0).getCityName();
            }
            if (mapName != null && !mapName.isEmpty()) body.append("Map: ").append(mapName).append("\n");
            if (cityName != null && !cityName.isEmpty()) body.append("City: ").append(cityName).append("\n");
            body.append("\nReview in Map Approvals.");

            java.util.List<Integer> managerIds = server.dao.UserDAO.getContentManagerUserIds();
            try (Connection conn = DBConnector.getConnection()) {
                for (int managerId : managerIds) {
                    if (managerId == editorUserId) continue;
                    if (NotificationDAO.createNotification(conn, managerId, title, body.toString()) > 0) {
                        System.out.println("MapEditHandler: Notified manager " + managerId + " about new request(s) from " + editorName);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("MapEditHandler: Failed to notify managers: " + e.getMessage());
        }
    }

    /**
     * Notify the editor who submitted a request about the manager's decision.
     * Builds a summary of what was in the request (cities, maps, POIs, tours).
     */
    private static void notifyEditorAboutDecision(Connection conn, MapEditRequestDTO reqDTO, boolean approved) {
        try {
            String decision = approved ? "Approved" : "Rejected";
            MapChanges changes = reqDTO.getChanges();
            String mapName = reqDTO.getMapName() != null ? reqDTO.getMapName() : "";
            String cityName = reqDTO.getCityName() != null ? reqDTO.getCityName() : "";

            String title = "Edit Request " + decision;

            StringBuilder body = new StringBuilder();
            body.append("Your edit request");
            if (!mapName.isEmpty()) body.append(" for map \"").append(mapName).append("\"");
            if (!cityName.isEmpty()) body.append(" in ").append(cityName);
            body.append(" has been ").append(decision.toLowerCase()).append(".\n\n");

            if (changes != null) {
                body.append("Changes summary:\n");

                if (changes.getNewCities() != null && !changes.getNewCities().isEmpty())
                    body.append("  Add city: ").append(changes.getNewCities().size()).append(" city(s)\n");
                if (changes.getDeletedCityIds() != null && !changes.getDeletedCityIds().isEmpty())
                    body.append("  Delete city: ").append(changes.getDeletedCityIds().size()).append(" city(s)\n");

                if (changes.getNewMaps() != null && !changes.getNewMaps().isEmpty())
                    body.append("  Add map: ").append(changes.getNewMaps().size()).append(" map(s)\n");
                if (changes.getDeletedMapIds() != null && !changes.getDeletedMapIds().isEmpty())
                    body.append("  Remove map: ").append(changes.getDeletedMapIds().size()).append(" map(s)\n");
                if (changes.getNewMapName() != null && !changes.getNewMapName().isEmpty())
                    body.append("  Edit map: name/description updated\n");

                if (changes.getAddedPois() != null && !changes.getAddedPois().isEmpty())
                    body.append("  Add POI: ").append(changes.getAddedPois().size()).append(" POI(s)\n");
                if (changes.getUpdatedPois() != null && !changes.getUpdatedPois().isEmpty())
                    body.append("  Edit POI: ").append(changes.getUpdatedPois().size()).append(" POI(s)\n");
                if (changes.getDeletedPoiIds() != null && !changes.getDeletedPoiIds().isEmpty())
                    body.append("  Delete POI: ").append(changes.getDeletedPoiIds().size()).append(" POI(s)\n");

                if (changes.getAddedTours() != null && !changes.getAddedTours().isEmpty())
                    body.append("  Add tour: ").append(changes.getAddedTours().size()).append(" tour(s)\n");
                if (changes.getUpdatedTours() != null && !changes.getUpdatedTours().isEmpty())
                    body.append("  Edit tour: ").append(changes.getUpdatedTours().size()).append(" tour(s)\n");
                if (changes.getDeletedTourIds() != null && !changes.getDeletedTourIds().isEmpty())
                    body.append("  Remove tour: ").append(changes.getDeletedTourIds().size()).append(" tour(s)\n");
            }

            // Use a separate connection so the insert commits (approve flow uses conn with autoCommit=false)
            try (Connection notifConn = DBConnector.getConnection()) {
                if (NotificationDAO.createNotification(notifConn, reqDTO.getUserId(), title, body.toString().trim()) > 0) {
                    System.out.println("MapEditHandler: Notified editor " + reqDTO.getUserId() + " about " + decision + " request #" + reqDTO.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("MapEditHandler: Failed to notify editor: " + e.getMessage());
        }
    }

    private static void notifyCustomersAboutMapUpdate(int cityId, MapChanges changes) {
        try {
            // Get city name
            CityDTO city = CityDAO.getCityById(cityId);
            String cityName = city != null ? city.getName() : "City #" + cityId;

            // Build notification message
            String title = "Map Update: " + cityName;
            String body = buildUpdateNotificationBody(changes, cityName);

            // Get all customers who purchased this city
            java.util.List<Integer> customerIds = PurchaseDAO.getCustomerIdsForCity(cityId);
            System.out.println(
                    "MapEditHandler: Notifying " + customerIds.size() + " customers about map update for " + cityName);

            // Send notification to each customer
            try (Connection conn = DBConnector.getConnection()) {
                for (int userId : customerIds) {
                    int notifId = NotificationDAO.createNotification(conn, userId, title, body);
                    if (notifId > 0) {
                        System.out.println("  → Sent notification #" + notifId + " to user " + userId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending map update notifications: " + e.getMessage());
        }
    }

    /**
     * Build notification body for map update.
     */
    private static String buildUpdateNotificationBody(MapChanges changes, String cityName) {
        StringBuilder body = new StringBuilder();
        body.append("Good news! The map for ").append(cityName).append(" has been updated.\n\n");

        if (!changes.getAddedPois().isEmpty()) {
            body.append("• ").append(changes.getAddedPois().size()).append(" new point(s) of interest added\n");
        }
        if (!changes.getUpdatedPois().isEmpty()) {
            body.append("• ").append(changes.getUpdatedPois().size()).append(" point(s) of interest updated\n");
        }
        if (!changes.getAddedTours().isEmpty()) {
            body.append("• ").append(changes.getAddedTours().size()).append(" new tour(s) added\n");
        }
        if (!changes.getUpdatedTours().isEmpty()) {
            body.append("• ").append(changes.getUpdatedTours().size()).append(" tour(s) updated\n");
        }

        body.append("\nView the updated map in your purchases.");
        return body.toString();
    }

    /**
     * Resolve POI id for tour stop: use as-is if > 0; if 0 (new POI from this request), use next created id by order.
     * newPoiIndex[0] is advanced each time a new POI id is consumed so multiple new POIs map correctly to stops.
     */
    private static int resolvePoiId(int poiId, java.util.List<Integer> createdPoiIds, int[] newPoiIndex) {
        if (poiId > 0) return poiId;
        if (createdPoiIds != null && newPoiIndex != null && newPoiIndex[0] < createdPoiIds.size()) {
            int id = createdPoiIds.get(newPoiIndex[0]++);
            if (id > 0) return id;
        }
        return 0;
    }

    /**
     * Populate deletedPoiDisplayNames so the manager sees POI names instead of IDs in the request list.
     */
    private static void enrichDeletedPoiDisplayNames(Connection conn, MapChanges changes) {
        if (conn == null || changes.getDeletedPoiIds() == null || changes.getDeletedPoiIds().isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (int poiId : changes.getDeletedPoiIds()) {
            try {
                Poi poi = PoiDAO.getPoiById(conn, poiId);
                String name = (poi != null && poi.getName() != null && !poi.getName().trim().isEmpty())
                        ? poi.getName().trim() : ("id: " + poiId);
                names.add(name);
            } catch (SQLException e) {
                names.add("id: " + poiId);
            }
        }
        changes.setDeletedPoiDisplayNames(names);
    }

    /**
     * Populate deletedTourDisplayNames so the manager sees tour names instead of IDs in the request list.
     */
    private static void enrichDeletedTourDisplayNames(Connection conn, MapChanges changes) {
        if (conn == null || changes.getDeletedTourIds() == null || changes.getDeletedTourIds().isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (int tourId : changes.getDeletedTourIds()) {
            try {
                TourDTO tour = TourDAO.getTourById(conn, tourId);
                String name = (tour != null && tour.getName() != null && !tour.getName().trim().isEmpty())
                        ? tour.getName().trim() : ("id: " + tourId);
                names.add(name);
            } catch (SQLException e) {
                names.add("id: " + tourId);
            }
        }
        changes.setDeletedTourDisplayNames(names);
    }

    /**
     * Build a description of changes for the version.
     * Uses conn to look up POI/tour names so the manager sees names instead of IDs.
     */
    private static String buildChangesDescription(Connection conn, MapChanges changes) {
        StringBuilder desc = new StringBuilder();

        if (!changes.getAddedPois().isEmpty()) {
            desc.append("Added ").append(changes.getAddedPois().size()).append(" POI(s). ");
        }
        if (!changes.getUpdatedPois().isEmpty()) {
            desc.append("Updated ").append(changes.getUpdatedPois().size()).append(" POI(s). ");
        }
        if (!changes.getDeletedPoiIds().isEmpty()) {
            for (int poiId : changes.getDeletedPoiIds()) {
                Poi poi = null;
                if (conn != null) {
                    try {
                        poi = PoiDAO.getPoiById(conn, poiId);
                    } catch (SQLException e) {
                        // use id fallback
                    }
                }
                String name = (poi != null && poi.getName() != null && !poi.getName().trim().isEmpty())
                        ? poi.getName().trim() : ("id: " + poiId);
                desc.append("Delete POI \"").append(name).append("\". ");
            }
        }
        if (!changes.getAddedTours().isEmpty()) {
            desc.append("Added ").append(changes.getAddedTours().size()).append(" tour(s). ");
        }
        if (!changes.getUpdatedTours().isEmpty()) {
            desc.append("Updated ").append(changes.getUpdatedTours().size()).append(" tour(s). ");
        }
        if (!changes.getDeletedTourIds().isEmpty()) {
            for (int tourId : changes.getDeletedTourIds()) {
                TourDTO tour = null;
                if (conn != null) {
                    try {
                        tour = TourDAO.getTourById(conn, tourId);
                    } catch (SQLException e) {
                        // use id fallback
                    }
                }
                String name = (tour != null && tour.getName() != null && !tour.getName().trim().isEmpty())
                        ? tour.getName().trim() : ("id: " + tourId);
                desc.append("Delete tour \"").append(name).append("\". ");
            }
        }

        return desc.length() > 0 ? desc.toString().trim() : "Map content updated.";
    }

    // ==================== Validation Methods ====================

    private static ValidationResult validateCity(CityDTO city) {
        ValidationResult result = new ValidationResult();

        if (city.getName() == null || city.getName().trim().isEmpty()) {
            result.addError("name", "City name is required");
        } else if (CityDAO.cityNameExists(city.getName())) {
            result.addError("name", "A city with this name already exists");
        }

        if (city.getPrice() < 0) {
            result.addError("price", "Price must be non-negative");
        }

        return result;
    }

    private static ValidationResult validateMap(MapContent map) {
        ValidationResult result = new ValidationResult();

        if (map.getMapName() == null || map.getMapName().trim().isEmpty()) {
            result.addError("name", "Map name is required");
        }

        if (map.getCityId() <= 0) {
            result.addError("cityId", "Valid city ID is required");
        }

        return result;
    }

    private static ValidationResult validatePoi(Poi poi) {
        ValidationResult result = new ValidationResult();

        if (poi.getName() == null || poi.getName().trim().isEmpty()) {
            result.addError("name", "POI name is required");
        }

        if (poi.getCityId() <= 0) {
            result.addError("cityId", "Valid city ID is required");
        }

        return result;
    }

    private static ValidationResult validateTour(TourDTO tour) {
        ValidationResult result = new ValidationResult();

        if (tour.getName() == null || tour.getName().trim().isEmpty()) {
            result.addError("name", "Tour name is required");
        }

        if (tour.getCityId() <= 0) {
            result.addError("cityId", "Valid city ID is required");
        }

        // Validate stops
        for (int i = 0; i < tour.getStops().size(); i++) {
            TourStopDTO stop = tour.getStops().get(i);
            if (stop.getPoiId() <= 0) {
                result.addError("stop[" + i + "].poiId", "Invalid POI reference");
            }
        }

        return result;
    }

    private static ValidationResult validateTourStop(TourStopDTO stop) {
        ValidationResult result = new ValidationResult();

        if (stop.getTourId() <= 0) {
            result.addError("tourId", "Valid tour ID is required");
        }

        if (stop.getPoiId() <= 0) {
            result.addError("poiId", "Valid POI ID is required");
        }

        if (stop.getStopOrder() <= 0) {
            result.addError("order", "Stop order must be greater than 0");
        }

        return result;
    }

    private static ValidationResult validateAllChanges(MapChanges changes) {
        ValidationResult result = new ValidationResult();

        // Validate new city (single createNewCity)
        if (changes.isCreateNewCity()) {
            if (changes.getNewCityName() == null || changes.getNewCityName().trim().isEmpty()) {
                result.addError("cityName", "City name is required");
            }
            if (changes.getNewCityPrice() != null && changes.getNewCityPrice() < 0) {
                result.addError("cityPrice", "City price must be non-negative");
            }
        }
        // Validate new cities list
        if (changes.getNewCities() != null) {
            for (int i = 0; i < changes.getNewCities().size(); i++) {
                MapChanges.NewCityRequest r = changes.getNewCities().get(i);
                if (r.getName() == null || r.getName().trim().isEmpty()) {
                    result.addError("newCities[" + i + "].name", "City name is required");
                }
            }
        }
        // Validate new maps list
        if (changes.getNewMaps() != null) {
            for (int i = 0; i < changes.getNewMaps().size(); i++) {
                MapChanges.NewMapRequest r = changes.getNewMaps().get(i);
                if (r.getCityId() <= 0) {
                    result.addError("newMaps[" + i + "].cityId", "Valid city is required");
                }
                if (r.getName() == null || r.getName().trim().isEmpty()) {
                    result.addError("newMaps[" + i + "].name", "Map name is required");
                }
            }
        }
        // Validate new city+map list
        if (changes.getNewCityWithMap() != null) {
            for (int i = 0; i < changes.getNewCityWithMap().size(); i++) {
                MapChanges.CityWithMapRequest r = changes.getNewCityWithMap().get(i);
                if (r.getCityName() == null || r.getCityName().trim().isEmpty()) {
                    result.addError("newCityWithMap[" + i + "].cityName", "City name is required");
                }
            }
        }

        // Validate added POIs
        for (int i = 0; i < changes.getAddedPois().size(); i++) {
            Poi poi = changes.getAddedPois().get(i);
            if (poi.getName() == null || poi.getName().trim().isEmpty()) {
                result.addError("addedPoi[" + i + "].name", "POI name is required");
            }
        }

        // Validate deleted POIs aren't in tours
        for (int poiId : changes.getDeletedPoiIds()) {
            if (PoiDAO.isPoiUsedInTour(poiId)) {
                result.addError("deletedPoi[" + poiId + "]",
                        "Cannot delete POI - it is used in a tour");
            }
        }

        // Validate added tours
        for (int i = 0; i < changes.getAddedTours().size(); i++) {
            TourDTO tour = changes.getAddedTours().get(i);
            if (tour.getName() == null || tour.getName().trim().isEmpty()) {
                result.addError("addedTour[" + i + "].name", "Tour name is required");
            }
        }

        return result;
    }

    /**
     * Check if a message type is handled by this handler.
     */
    public static boolean canHandle(MessageType type) {
        return type == MessageType.GET_CITIES ||
                type == MessageType.GET_MAPS_FOR_CITY ||
                type == MessageType.GET_MAP_CONTENT ||
                type == MessageType.GET_POIS_FOR_CITY ||
                type == MessageType.SAVE_MAP_CHANGES ||
                type == MessageType.SUBMIT_MAP_CHANGES ||
                type == MessageType.GET_MY_DRAFT ||
                type == MessageType.GET_PENDING_MAP_EDITS ||
                type == MessageType.APPROVE_MAP_EDIT ||
                type == MessageType.REJECT_MAP_EDIT ||
                type == MessageType.CREATE_CITY ||
                type == MessageType.UPDATE_CITY ||
                type == MessageType.CREATE_MAP ||
                type == MessageType.UPDATE_MAP ||
                type == MessageType.DELETE_MAP ||
                type == MessageType.ADD_POI ||
                type == MessageType.UPDATE_POI ||
                type == MessageType.DELETE_POI ||
                type == MessageType.LINK_POI_TO_MAP ||
                type == MessageType.UNLINK_POI_FROM_MAP ||
                type == MessageType.CREATE_TOUR ||
                type == MessageType.UPDATE_TOUR ||
                type == MessageType.DELETE_TOUR ||
                type == MessageType.ADD_TOUR_STOP ||
                type == MessageType.UPDATE_TOUR_STOP ||
                type == MessageType.REMOVE_TOUR_STOP;
    }
}
