package common.dto;

import common.Poi;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO containing changes to submit for a map.
 * Used to batch multiple changes into a single transaction.
 */
public class MapChanges implements Serializable {
    private static final long serialVersionUID = 1L;

    // Target map (null for creating new map)
    private Integer mapId;
    private Integer cityId;

    // Map info changes
    private String newMapName;
    private String newMapDescription;

    // City changes (for creating new city with first map)
    private boolean createNewCity;
    private String newCityName;
    private String newCityDescription;
    private Double newCityPrice;

    // POI changes
    private List<Poi> addedPois;
    private List<Poi> updatedPois;
    private List<Integer> deletedPoiIds;
    private List<PoiMapLink> poiMapLinks;
    private List<PoiMapLink> poiMapUnlinks;

    // Tour changes
    private List<TourDTO> addedTours;
    private List<TourDTO> updatedTours;
    private List<Integer> deletedTourIds;

    // Tour stop changes
    private List<TourStopDTO> addedStops;
    private List<TourStopDTO> updatedStops;
    private List<Integer> deletedStopIds;

    // Map removal (delete map)
    private List<Integer> deletedMapIds;

    /** If true, save as draft (employee only); if false, send to content manager for approval. */
    private boolean draft;

    public MapChanges() {
        this.addedPois = new ArrayList<>();
        this.updatedPois = new ArrayList<>();
        this.deletedPoiIds = new ArrayList<>();
        this.poiMapLinks = new ArrayList<>();
        this.poiMapUnlinks = new ArrayList<>();
        this.addedTours = new ArrayList<>();
        this.updatedTours = new ArrayList<>();
        this.deletedTourIds = new ArrayList<>();
        this.addedStops = new ArrayList<>();
        this.updatedStops = new ArrayList<>();
        this.deletedStopIds = new ArrayList<>();
        this.deletedMapIds = new ArrayList<>();
        this.draft = false;
    }

    /**
     * Helper class for POI-Map linking.
     */
    public static class PoiMapLink implements Serializable {
        private static final long serialVersionUID = 1L;
        public int mapId;
        public int poiId;
        public int displayOrder;

        public PoiMapLink() {
        }

        public PoiMapLink(int mapId, int poiId, int displayOrder) {
            this.mapId = mapId;
            this.poiId = poiId;
            this.displayOrder = displayOrder;
        }
    }

    // ==================== Builder-style methods ====================

    public MapChanges forMap(int mapId) {
        this.mapId = mapId;
        return this;
    }

    public MapChanges forCity(int cityId) {
        this.cityId = cityId;
        return this;
    }

    public MapChanges withNewCity(String name, String description, double price) {
        this.createNewCity = true;
        this.newCityName = name;
        this.newCityDescription = description;
        this.newCityPrice = price;
        return this;
    }

    public MapChanges addPoi(Poi poi) {
        this.addedPois.add(poi);
        return this;
    }

    public MapChanges updatePoi(Poi poi) {
        this.updatedPois.add(poi);
        return this;
    }

    public MapChanges deletePoi(int poiId) {
        this.deletedPoiIds.add(poiId);
        return this;
    }

    public MapChanges addTour(TourDTO tour) {
        this.addedTours.add(tour);
        return this;
    }

    public MapChanges updateTour(TourDTO tour) {
        this.updatedTours.add(tour);
        return this;
    }

    public MapChanges deleteTour(int tourId) {
        this.deletedTourIds.add(tourId);
        return this;
    }

    // ==================== Getters and Setters ====================

    public Integer getMapId() {
        return mapId;
    }

    public void setMapId(Integer mapId) {
        this.mapId = mapId;
    }

    public Integer getCityId() {
        return cityId;
    }

    public void setCityId(Integer cityId) {
        this.cityId = cityId;
    }

    public String getNewMapName() {
        return newMapName;
    }

    public void setNewMapName(String newMapName) {
        this.newMapName = newMapName;
    }

    public String getNewMapDescription() {
        return newMapDescription;
    }

    public void setNewMapDescription(String newMapDescription) {
        this.newMapDescription = newMapDescription;
    }

    public boolean isCreateNewCity() {
        return createNewCity;
    }

    public void setCreateNewCity(boolean createNewCity) {
        this.createNewCity = createNewCity;
    }

    public String getNewCityName() {
        return newCityName;
    }

    public void setNewCityName(String newCityName) {
        this.newCityName = newCityName;
    }

    public String getNewCityDescription() {
        return newCityDescription;
    }

    public void setNewCityDescription(String newCityDescription) {
        this.newCityDescription = newCityDescription;
    }

    public Double getNewCityPrice() {
        return newCityPrice;
    }

    public void setNewCityPrice(Double newCityPrice) {
        this.newCityPrice = newCityPrice;
    }

    public List<Poi> getAddedPois() {
        return addedPois;
    }

    public void setAddedPois(List<Poi> addedPois) {
        this.addedPois = addedPois;
    }

    public List<Poi> getUpdatedPois() {
        return updatedPois;
    }

    public void setUpdatedPois(List<Poi> updatedPois) {
        this.updatedPois = updatedPois;
    }

    public List<Integer> getDeletedPoiIds() {
        return deletedPoiIds;
    }

    public void setDeletedPoiIds(List<Integer> deletedPoiIds) {
        this.deletedPoiIds = deletedPoiIds;
    }

    public List<PoiMapLink> getPoiMapLinks() {
        return poiMapLinks;
    }

    public void setPoiMapLinks(List<PoiMapLink> poiMapLinks) {
        this.poiMapLinks = poiMapLinks;
    }

    public List<PoiMapLink> getPoiMapUnlinks() {
        return poiMapUnlinks;
    }

    public void setPoiMapUnlinks(List<PoiMapLink> poiMapUnlinks) {
        this.poiMapUnlinks = poiMapUnlinks;
    }

    public List<TourDTO> getAddedTours() {
        return addedTours;
    }

    public void setAddedTours(List<TourDTO> addedTours) {
        this.addedTours = addedTours;
    }

    public List<TourDTO> getUpdatedTours() {
        return updatedTours;
    }

    public void setUpdatedTours(List<TourDTO> updatedTours) {
        this.updatedTours = updatedTours;
    }

    public List<Integer> getDeletedTourIds() {
        return deletedTourIds;
    }

    public void setDeletedTourIds(List<Integer> deletedTourIds) {
        this.deletedTourIds = deletedTourIds;
    }

    public List<TourStopDTO> getAddedStops() {
        return addedStops;
    }

    public void setAddedStops(List<TourStopDTO> addedStops) {
        this.addedStops = addedStops;
    }

    public List<TourStopDTO> getUpdatedStops() {
        return updatedStops;
    }

    public void setUpdatedStops(List<TourStopDTO> updatedStops) {
        this.updatedStops = updatedStops;
    }

    public List<Integer> getDeletedStopIds() {
        return deletedStopIds;
    }

    public void setDeletedStopIds(List<Integer> deletedStopIds) {
        this.deletedStopIds = deletedStopIds;
    }

    /**
     * Check if there are any changes.
     */
    public List<Integer> getDeletedMapIds() {
        return deletedMapIds;
    }

    public void setDeletedMapIds(List<Integer> deletedMapIds) {
        this.deletedMapIds = deletedMapIds;
    }

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public boolean hasChanges() {
        return createNewCity || newMapName != null || newMapDescription != null ||
                !addedPois.isEmpty() || !updatedPois.isEmpty() || !deletedPoiIds.isEmpty() ||
                !poiMapLinks.isEmpty() || !poiMapUnlinks.isEmpty() ||
                !addedTours.isEmpty() || !updatedTours.isEmpty() || !deletedTourIds.isEmpty() ||
                !addedStops.isEmpty() || !updatedStops.isEmpty() || !deletedStopIds.isEmpty() ||
                (deletedMapIds != null && !deletedMapIds.isEmpty());
    }
}
