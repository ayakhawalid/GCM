package common.dto;

import common.Poi;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO containing complete map content for editing.
 * Includes map details, all POIs, and all tours.
 */
public class MapContent implements Serializable {
    private static final long serialVersionUID = 1L;

    // Map basic info
    private int mapId;
    private int cityId;
    private String cityName;
    private String mapName;
    private String shortDescription;

    // Map content
    private List<Poi> pois;
    private List<TourDTO> tours;

    // Metadata
    private String createdAt;
    private String updatedAt;

    /** Pending unlinks (remove from map) – from DRAFT request, waiting for manager approval. */
    private List<MapChanges.PoiMapLink> pendingPoiMapUnlinks;
    /** Pending full deletions – from DRAFT request, waiting for manager approval. */
    private List<Integer> pendingDeletedPoiIds;
    /** Pending added tours (id=0) – from DRAFT request, awaiting manager approval. */
    private List<TourDTO> pendingAddedTours;
    /** Pending deleted tour IDs – from DRAFT request, awaiting manager approval. */
    private List<Integer> pendingDeletedTourIds;
    /** Full DRAFT MapChanges when loading – used to restore pendingChanges on client. */
    private MapChanges pendingDraftChanges;

    /** When non-null, this map is the tour route map for that tour (POIs + segments for drawing lines). */
    private Integer tourId;
    /** Segment list for tour maps: from/to coords and distance for each leg (including circle back). */
    private List<TourSegmentDTO> tourSegments;

    public MapContent() {
        this.pois = new ArrayList<>();
        this.tours = new ArrayList<>();
        this.tourSegments = new ArrayList<>();
        this.pendingPoiMapUnlinks = new ArrayList<>();
        this.pendingDeletedPoiIds = new ArrayList<>();
        this.pendingAddedTours = new ArrayList<>();
        this.pendingDeletedTourIds = new ArrayList<>();
    }

    public MapContent(int mapId, int cityId, String cityName, String mapName, String shortDescription) {
        this.mapId = mapId;
        this.cityId = cityId;
        this.cityName = cityName;
        this.mapName = mapName;
        this.shortDescription = shortDescription;
        this.pois = new ArrayList<>();
        this.tours = new ArrayList<>();
        this.tourSegments = new ArrayList<>();
        this.pendingPoiMapUnlinks = new ArrayList<>();
        this.pendingDeletedPoiIds = new ArrayList<>();
        this.pendingAddedTours = new ArrayList<>();
        this.pendingDeletedTourIds = new ArrayList<>();
    }

    public Integer getTourId() { return tourId; }
    public void setTourId(Integer tourId) { this.tourId = tourId; }
    public List<TourSegmentDTO> getTourSegments() { return tourSegments != null ? tourSegments : new ArrayList<>(); }
    public void setTourSegments(List<TourSegmentDTO> tourSegments) { this.tourSegments = tourSegments != null ? tourSegments : new ArrayList<>(); }

    // Add methods
    public void addPoi(Poi poi) {
        this.pois.add(poi);
    }

    public void addTour(TourDTO tour) {
        this.tours.add(tour);
    }

    // Getters and Setters
    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public int getCityId() {
        return cityId;
    }

    public void setCityId(int cityId) {
        this.cityId = cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public List<Poi> getPois() {
        return pois;
    }

    public void setPois(List<Poi> pois) {
        this.pois = pois;
    }

    public List<TourDTO> getTours() {
        return tours;
    }

    public void setTours(List<TourDTO> tours) {
        this.tours = tours;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<MapChanges.PoiMapLink> getPendingPoiMapUnlinks() {
        return pendingPoiMapUnlinks != null ? pendingPoiMapUnlinks : new ArrayList<>();
    }

    public void setPendingPoiMapUnlinks(List<MapChanges.PoiMapLink> pendingPoiMapUnlinks) {
        this.pendingPoiMapUnlinks = pendingPoiMapUnlinks != null ? pendingPoiMapUnlinks : new ArrayList<>();
    }

    public List<Integer> getPendingDeletedPoiIds() {
        return pendingDeletedPoiIds != null ? pendingDeletedPoiIds : new ArrayList<>();
    }

    public void setPendingDeletedPoiIds(List<Integer> pendingDeletedPoiIds) {
        this.pendingDeletedPoiIds = pendingDeletedPoiIds != null ? pendingDeletedPoiIds : new ArrayList<>();
    }

    public List<TourDTO> getPendingAddedTours() {
        return pendingAddedTours != null ? pendingAddedTours : new ArrayList<>();
    }

    public void setPendingAddedTours(List<TourDTO> pendingAddedTours) {
        this.pendingAddedTours = pendingAddedTours != null ? pendingAddedTours : new ArrayList<>();
    }

    public List<Integer> getPendingDeletedTourIds() {
        return pendingDeletedTourIds != null ? pendingDeletedTourIds : new ArrayList<>();
    }

    public void setPendingDeletedTourIds(List<Integer> pendingDeletedTourIds) {
        this.pendingDeletedTourIds = pendingDeletedTourIds != null ? pendingDeletedTourIds : new ArrayList<>();
    }

    public MapChanges getDraftChangesToRestore() {
        return pendingDraftChanges;
    }

    public void setDraftChangesToRestore(MapChanges pendingDraftChanges) {
        this.pendingDraftChanges = pendingDraftChanges;
    }

    @Override
    public String toString() {
        return mapName + " [" + pois.size() + " POIs, " + tours.size() + " Tours]";
    }
}
