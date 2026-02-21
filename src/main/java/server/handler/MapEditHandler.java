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
import java.sql.SQLException;
import java.util.List;

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

                case SUBMIT_MAP_CHANGES:
                    return handleSubmitMapChanges(request);

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
        System.out.println("MapEditHandler: Getting content for map " + mapId);

        MapContent content = MapDAO.getMapContent(mapId);
        if (content == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Map not found");
        }

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
        try (Connection conn = DBConnector.getConnection()) {
            if (MapEditRequestDAO.updateStatus(conn, reqId, "REJECTED")) {
                return Response.success(request, ValidationResult.success("Request rejected"));
            }
        } catch (SQLException e) {
            return Response.error(request, Response.ERR_DATABASE, e.getMessage());
        }
        return Response.error(request, Response.ERR_DATABASE, "Failed to reject request");
    }

    private static Response handleSubmitMapChanges(Request request) {
        if (!(request.getPayload() instanceof MapChanges)) {
            return Response.error(request, Response.ERR_VALIDATION, "MapChanges required");
        }

        MapChanges changes = (MapChanges) request.getPayload();
        System.out.println("MapEditHandler: Submitting changes for approval");
        System.out.println("  userId=" + request.getUserId() + ", mapId=" + changes.getMapId() + ", cityId="
                + changes.getCityId());
        System.out.println("  hasChanges=" + changes.hasChanges() + ", addedPois=" + changes.getAddedPois().size());

        // First validate all changes
        ValidationResult validation = validateAllChanges(changes);
        if (!validation.isValid()) {
            System.out.println("MapEditHandler: Validation failed - " + validation.getErrorSummary());
            return Response.success(request, validation);
        }

        // Resolve user and role from session (content editor → submit for approval; content manager → release directly)
        String token = request.getSessionToken();
        SessionManager.SessionInfo session = token != null ? SessionManager.getInstance().validateSession(token) : null;
        int userId = (session != null ? session.userId : request.getUserId()) > 0
                ? (session != null ? session.userId : request.getUserId()) : 1;
        boolean isContentManager = session != null && isManagerRole(session.role);

        boolean asDraft = changes.isDraft();

        if (isContentManager) {
            // Content Manager: apply changes and release directly (no approval step)
            try (Connection conn = DBConnector.getConnection()) {
                if (conn == null) {
                    return Response.error(request, Response.ERR_DATABASE, "Database connection failed");
                }
                conn.setAutoCommit(false);
                validation = new ValidationResult();
                try {
                    applyMapChanges(conn, changes, userId, userId, 0, validation, false);
                    conn.commit();
                    validation.setSuccessMessage("Changes applied and released. Customers can see the new version.");
                    Integer cityId = changes.getCityId();
                    if (cityId != null && cityId > 0) {
                        notifyCustomersAboutMapUpdate(cityId, changes);
                    }
                    return Response.success(request, validation);
                } catch (SQLException e) {
                    conn.rollback();
                    return Response.error(request, Response.ERR_DATABASE, "Transaction failed: " + e.getMessage());
                }
            } catch (SQLException e) {
                return Response.error(request, Response.ERR_DATABASE, e.getMessage());
            }
        }

        // Content Editor: draft = apply to DB with DRAFT version (persisted, visible on reload); otherwise submit request for approval
        if (asDraft) {
            try (Connection conn = DBConnector.getConnection()) {
                if (conn == null) {
                    return Response.error(request, Response.ERR_DATABASE, "Database connection failed");
                }
                conn.setAutoCommit(false);
                validation = new ValidationResult();
                try {
                    applyMapChanges(conn, changes, userId, userId, 0, validation, true);
                    conn.commit();
                    validation.setSuccessMessage("Changes saved. They are stored as draft and visible only to you until sent for approval.");
                    return Response.success(request, validation);
                } catch (SQLException e) {
                    conn.rollback();
                    return Response.error(request, Response.ERR_DATABASE, "Transaction failed: " + e.getMessage());
                }
            } catch (SQLException e) {
                return Response.error(request, Response.ERR_DATABASE, e.getMessage());
            }
        }

        // Submit for manager approval (not draft): include any draft (unverified) POIs on this map so manager sees them
        try (Connection conn = DBConnector.getConnection()) {
            enrichMapChangesWithDraftPois(conn, changes);

            int reqId = MapEditRequestDAO.createRequest(conn,
                    changes.getMapId() != null ? changes.getMapId() : 0,
                    changes.getCityId() != null ? changes.getCityId() : 0,
                    userId,
                    changes);

            System.out.println("MapEditHandler: Created request with ID=" + reqId + " (status=PENDING)");

            if (reqId > 0) {
                validation = ValidationResult.success("Changes submitted for manager approval. Request ID: " + reqId);
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
     * When submitting for manager approval, add any draft (unverified) POIs currently on this map
     * so the manager sees what the employee had saved earlier with "Save changes".
     */
    private static void enrichMapChangesWithDraftPois(Connection conn, MapChanges changes) {
        Integer mapId = changes.getMapId();
        if (mapId == null || mapId <= 0) return;
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
            // approved column may not exist yet
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

                conn.commit();
                validation.setSuccessMessage("Request approved and changes applied successfully.");
                System.out.println("MapEditHandler: Approved request " + reqId);

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
                    cityId = CityDAO.createCity(conn,
                            changes.getNewCityName(),
                            changes.getNewCityDescription(),
                            changes.getNewCityPrice() != null ? changes.getNewCityPrice() : 0,
                            creatorUserId,
                            true);
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

        // Add POIs (only record ids that were actually created so FK references are valid)
        for (Poi poi : changes.getAddedPois()) {
            int poiId = PoiDAO.createPoi(conn, poi);
            if (poiId > 0) validation.getCreatedPoiIds().add(poiId);
        }

        // Link newly created POIs to the current map (approved = false when draft, so catalog doesn't count them yet)
        boolean linkApproved = !asDraft;
        if (changes.getMapId() != null && changes.getMapId() > 0 && !validation.getCreatedPoiIds().isEmpty()) {
            int order = 0;
            for (int poiId : validation.getCreatedPoiIds()) {
                if (poiId > 0) PoiDAO.linkPoiToMap(conn, changes.getMapId(), poiId, order++, linkApproved);
            }
        }

        // Update POIs
        for (Poi poi : changes.getUpdatedPois()) {
            PoiDAO.updatePoi(conn, poi);
        }

        // Link/unlink POIs (POI can appear in multiple maps of the same city)
        // Resolve poi_id=0 (new POI from this request) to created POI id so FK is valid
        java.util.List<Integer> createdPoiIds = validation.getCreatedPoiIds();
        int nextCreatedPoiIndex = 0;
        for (MapChanges.PoiMapLink link : changes.getPoiMapLinks()) {
            int poiId = link.poiId;
            if (poiId <= 0 && createdPoiIds != null && nextCreatedPoiIndex < createdPoiIds.size()) {
                poiId = createdPoiIds.get(nextCreatedPoiIndex++);
            }
            if (poiId <= 0) continue;
            PoiDAO.linkPoiToMap(conn, link.mapId, poiId, link.displayOrder, linkApproved);
        }
        nextCreatedPoiIndex = 0;
        for (MapChanges.PoiMapLink link : changes.getPoiMapUnlinks()) {
            int poiId = link.poiId;
            if (poiId <= 0 && createdPoiIds != null && nextCreatedPoiIndex < createdPoiIds.size()) {
                poiId = createdPoiIds.get(nextCreatedPoiIndex++);
            }
            if (poiId <= 0) continue;
            PoiDAO.unlinkPoiFromMap(conn, link.mapId, poiId);
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

        // Add tours (tours can include POIs from different maps in the city)
        java.util.List<Integer> createdTourIds = validation.getCreatedTourIds();
        for (TourDTO tour : changes.getAddedTours()) {
            int tourId = TourDAO.createTour(conn, tour);
            createdTourIds.add(tourId);
            // Add stops that belong to this new tour (client sends tour_id=0 for new tours)
            if (tour.getStops() != null && !tour.getStops().isEmpty()) {
                for (TourStopDTO stop : tour.getStops()) {
                    int effectiveTourId = stop.getTourId() > 0 ? stop.getTourId() : tourId;
                    int effectivePoiId = resolvePoiId(stop.getPoiId(), validation.getCreatedPoiIds());
                    if (effectivePoiId <= 0 || !TourDAO.poiExists(conn, effectivePoiId)) continue;
                    TourStopDTO resolvedStop = new TourStopDTO(0, effectiveTourId, effectivePoiId,
                            stop.getPoiName(), stop.getPoiCategory(), stop.getStopOrder(),
                            stop.getDurationMinutes(), stop.getNotes());
                    TourDAO.addTourStop(conn, resolvedStop);
                }
            }
        }

        // Update tours
        for (TourDTO tour : changes.getUpdatedTours()) {
            TourDAO.updateTour(conn, tour);
        }

        // Delete tours
        for (int tourId : changes.getDeletedTourIds()) {
            TourDAO.deleteTour(conn, tourId);
        }

        // Tour stops for existing tours only (new tour stops were added above)
        for (TourStopDTO stop : changes.getAddedStops()) {
            if (stop.getTourId() <= 0) continue; // already handled via tour.getStops()
            int effectivePoiId = resolvePoiId(stop.getPoiId(), validation.getCreatedPoiIds());
            if (effectivePoiId <= 0 || !TourDAO.poiExists(conn, effectivePoiId)) continue;
            TourStopDTO toInsert = new TourStopDTO(0, stop.getTourId(), effectivePoiId, stop.getPoiName(),
                    stop.getPoiCategory(), stop.getStopOrder(), stop.getDurationMinutes(), stop.getNotes());
            TourDAO.addTourStop(conn, toInsert);
        }
        for (TourStopDTO stop : changes.getUpdatedStops()) {
            // Resolve poi_id=0 so tour_stops FK to pois(id) is satisfied
            int effectivePoiId = resolvePoiId(stop.getPoiId(), validation.getCreatedPoiIds());
            if (effectivePoiId <= 0 || !TourDAO.poiExists(conn, effectivePoiId)) continue;
            TourStopDTO toUpdate = new TourStopDTO(stop.getId(), stop.getTourId(), effectivePoiId,
                    stop.getPoiName(), stop.getPoiCategory(), stop.getStopOrder(),
                    stop.getDurationMinutes(), stop.getNotes());
            TourDAO.updateTourStop(conn, toUpdate);
        }
        for (int stopId : changes.getDeletedStopIds()) {
            TourDAO.removeTourStop(conn, stopId);
        }

        // Recompute total_distance_meters for all tours in this city (POI-to-POI consecutive distances)
        Integer cityIdForTours = changes.getCityId();
        if (cityIdForTours != null && cityIdForTours > 0) {
            java.util.Set<Integer> tourIds = new java.util.HashSet<>();
            for (int tid : validation.getCreatedTourIds()) tourIds.add(tid);
            for (TourDTO t : changes.getUpdatedTours()) tourIds.add(t.getId());
            for (TourStopDTO s : changes.getAddedStops()) if (s.getTourId() > 0) tourIds.add(s.getTourId());
            for (TourStopDTO s : changes.getUpdatedStops()) tourIds.add(s.getTourId());
            for (int stopId : changes.getDeletedStopIds()) {
                Integer tid = TourDAO.getTourIdForStop(conn, stopId);
                if (tid != null) tourIds.add(tid);
            }
            for (int tourId : tourIds) {
                try {
                    TourDAO.recomputeAndUpdateTourDistance(conn, tourId);
                } catch (SQLException e) {
                    System.err.println("MapEditHandler: Failed to recompute tour distance for tour " + tourId + ": " + e.getMessage());
                }
            }
        }

        // Delete POIs only after all tour_stops no longer reference them (FK poi_id -> pois(id))
        for (int poiId : changes.getDeletedPoiIds()) {
            PoiDAO.deletePoi(conn, poiId);
        }

        // Create map version — DRAFT (editor only) or APPROVED (customers can see)
        boolean mapBeingDeleted = changes.getDeletedMapIds() != null && changes.getDeletedMapIds().contains(changes.getMapId());
        if (changes.getMapId() != null && changes.getMapId() > 0 && !mapBeingDeleted) {
            String description = buildChangesDescription(changes);
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

        // Delete maps (e.g. Remove map)
        if (changes.getDeletedMapIds() != null) {
            for (int mapIdToDelete : changes.getDeletedMapIds()) {
                MapDAO.deleteMap(conn, mapIdToDelete);
            }
        }

        if (mapEditRequestId > 0) {
            MapEditRequestDAO.updateStatus(conn, mapEditRequestId, "APPROVED");
        }
    }

    /**
     * Notify all customers who purchased a city about a map update.
     */
    private static void notifyCustomersAboutMapUpdate(int cityId, MapChanges changes) {
        try {
            // Get city name
            CityDTO city = CityDAO.getCityById(cityId);
            String cityName = city != null ? city.getName() : "City #" + cityId;

            // Build notification message
            String title = "🗺️ Map Update: " + cityName;
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
     * Resolve POI id for tour stop / map_pois: use as-is if > 0; if 0 (new POI from this request), use first created id.
     * Only uses created ids that are > 0 so tour_stops.poi_id FK to pois(id) is satisfied.
     */
    private static int resolvePoiId(int poiId, java.util.List<Integer> createdPoiIds) {
        if (poiId > 0) return poiId;
        if (createdPoiIds != null) {
            for (int id : createdPoiIds) {
                if (id > 0) return id;
            }
        }
        return 0;
    }

    /**
     * Build a description of changes for the version.
     */
    private static String buildChangesDescription(MapChanges changes) {
        StringBuilder desc = new StringBuilder();

        if (!changes.getAddedPois().isEmpty()) {
            desc.append("Added ").append(changes.getAddedPois().size()).append(" POI(s). ");
        }
        if (!changes.getUpdatedPois().isEmpty()) {
            desc.append("Updated ").append(changes.getUpdatedPois().size()).append(" POI(s). ");
        }
        if (!changes.getDeletedPoiIds().isEmpty()) {
            desc.append("Deleted ").append(changes.getDeletedPoiIds().size()).append(" POI(s). ");
        }
        if (!changes.getAddedTours().isEmpty()) {
            desc.append("Added ").append(changes.getAddedTours().size()).append(" tour(s). ");
        }
        if (!changes.getUpdatedTours().isEmpty()) {
            desc.append("Updated ").append(changes.getUpdatedTours().size()).append(" tour(s). ");
        }
        if (!changes.getDeletedTourIds().isEmpty()) {
            desc.append("Deleted ").append(changes.getDeletedTourIds().size()).append(" tour(s). ");
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

        if (tour.getEstimatedDurationMinutes() <= 0) {
            result.addError("duration", "Duration must be greater than 0");
        }

        // Validate stops
        for (int i = 0; i < tour.getStops().size(); i++) {
            TourStopDTO stop = tour.getStops().get(i);
            if (stop.getPoiId() <= 0) {
                result.addError("stop[" + i + "].poiId", "Invalid POI reference");
            }
            if (stop.getDurationMinutes() <= 0) {
                result.addError("stop[" + i + "].duration", "Stop duration must be greater than 0");
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

        if (stop.getDurationMinutes() <= 0) {
            result.addError("duration", "Duration must be greater than 0");
        }

        if (stop.getStopOrder() <= 0) {
            result.addError("order", "Stop order must be greater than 0");
        }

        return result;
    }

    private static ValidationResult validateAllChanges(MapChanges changes) {
        ValidationResult result = new ValidationResult();

        // Validate new city
        if (changes.isCreateNewCity()) {
            if (changes.getNewCityName() == null || changes.getNewCityName().trim().isEmpty()) {
                result.addError("cityName", "City name is required");
            }
            if (changes.getNewCityPrice() != null && changes.getNewCityPrice() < 0) {
                result.addError("cityPrice", "City price must be non-negative");
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
            if (tour.getEstimatedDurationMinutes() <= 0) {
                result.addError("addedTour[" + i + "].duration", "Duration must be > 0");
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
                type == MessageType.SUBMIT_MAP_CHANGES ||
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
