package server.service;

import common.dto.*;
import java.util.List;

/**
 * Map management service interface.
 * Phase 15: Maintainability - Strict separation with interfaces.
 * 
 * Designed to be mappable to REST endpoints for future web migration.
 */
public interface IMapService {

    /**
     * Get all cities.
     * REST mapping: GET /api/cities
     */
    List<CityDTO> getAllCities();

    /**
     * Get city by ID.
     * REST mapping: GET /api/cities/{id}
     */
    CityDTO getCityById(int cityId);

    /**
     * Get maps for a city.
     * REST mapping: GET /api/cities/{cityId}/maps
     */
    List<MapSummary> getMapsForCity(int cityId);

    /**
     * Get map content with POIs and tours.
     * REST mapping: GET /api/maps/{mapId}
     */
    MapContent getMapContent(int mapId);

    /**
     * Create a new city.
     * REST mapping: POST /api/cities
     * 
     * @return Created city ID, or -1 on failure
     */
    int createCity(String name, String description, double price);

    /**
     * Create a new map.
     * REST mapping: POST /api/cities/{cityId}/maps
     * 
     * @return Created map ID, or -1 on failure
     */
    int createMap(int cityId, String name, String description);

    /**
     * Submit map changes for approval.
     * REST mapping: POST /api/maps/{mapId}/submit
     */
    boolean submitMapChanges(int mapId, MapEditRequestDTO editRequest, int userId);

    /**
     * Get pending map versions for approval.
     * REST mapping: GET /api/approvals/maps/pending
     */
    List<MapVersionDTO> getPendingMapVersions();

    /**
     * Approve a map version.
     * REST mapping: POST /api/approvals/maps/{versionId}/approve
     */
    boolean approveMapVersion(int versionId, int approverId, String reason);

    /**
     * Reject a map version.
     * REST mapping: POST /api/approvals/maps/{versionId}/reject
     */
    boolean rejectMapVersion(int versionId, int approverId, String reason);
}
