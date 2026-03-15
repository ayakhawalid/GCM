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
    /** When creating a new map in a city that may be created in same batch, resolve city by name. */
    private String newMapCityName;

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
    /** City removal (manager only). On Publish, all maps and tours for the city are deleted, then the city. */
    private List<Integer> deletedCityIds;

    /** New cities to create (city only, no first map). Sent separately from createNewCity (city+map). */
    private List<NewCityRequest> newCities;
    /** New maps to create in existing cities. */
    private List<NewMapRequest> newMaps;
    /** New city with first map (create city and map in one go). */
    private List<CityWithMapRequest> newCityWithMap;

    /** If true, save as draft (employee only); if false, send to content manager for approval. */
    private boolean draft;

    /** Optional display names set by server when creating a request, so list shows real names even if city/map is later deleted. */
    private String displayCityName;
    private String displayMapName;
    /** Display names for deleted POIs (same order as deletedPoiIds), so manager sees "Delete POI: Name" instead of ID. */
    private List<String> deletedPoiDisplayNames;
    /** Display names for deleted tours (same order as deletedTourIds), so manager sees "Delete tour: Name" instead of ID. */
    private List<String> deletedTourDisplayNames;

    /** Request to create a new city (name, description, price). */
    public static class NewCityRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String description;
        private double price;

        public NewCityRequest() {}
        public NewCityRequest(String name, String description, double price) {
            this.name = name;
            this.description = description;
            this.price = price;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
    }

    /** Request to create a new map in an existing city. */
    public static class NewMapRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private int cityId;
        private String name;
        private String description;

        public NewMapRequest() {}
        public NewMapRequest(int cityId, String name, String description) {
            this.cityId = cityId;
            this.name = name;
            this.description = description != null ? description : "";
        }
        public int getCityId() { return cityId; }
        public void setCityId(int cityId) { this.cityId = cityId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /** Request to create a new city with its first map. */
    public static class CityWithMapRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String cityName;
        private String cityDescription;
        private double cityPrice;
        private String mapName;
        private String mapDescription;

        public CityWithMapRequest() {}
        public CityWithMapRequest(String cityName, String cityDescription, double cityPrice, String mapName, String mapDescription) {
            this.cityName = cityName;
            this.cityDescription = cityDescription != null ? cityDescription : "";
            this.cityPrice = cityPrice;
            this.mapName = mapName;
            this.mapDescription = mapDescription != null ? mapDescription : "";
        }
        public String getCityName() { return cityName; }
        public void setCityName(String cityName) { this.cityName = cityName; }
        public String getCityDescription() { return cityDescription; }
        public void setCityDescription(String cityDescription) { this.cityDescription = cityDescription; }
        public double getCityPrice() { return cityPrice; }
        public void setCityPrice(double cityPrice) { this.cityPrice = cityPrice; }
        public String getMapName() { return mapName; }
        public void setMapName(String mapName) { this.mapName = mapName; }
        public String getMapDescription() { return mapDescription; }
        public void setMapDescription(String mapDescription) { this.mapDescription = mapDescription; }
    }

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
        this.deletedCityIds = new ArrayList<>();
        this.newCities = new ArrayList<>();
        this.newMaps = new ArrayList<>();
        this.newCityWithMap = new ArrayList<>();
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

    public String getNewMapCityName() {
        return newMapCityName;
    }

    public void setNewMapCityName(String newMapCityName) {
        this.newMapCityName = newMapCityName;
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

    public List<Integer> getDeletedCityIds() {
        return deletedCityIds;
    }

    public void setDeletedCityIds(List<Integer> deletedCityIds) {
        this.deletedCityIds = deletedCityIds != null ? deletedCityIds : new ArrayList<>();
    }

    public List<NewCityRequest> getNewCities() {
        return newCities;
    }

    public void setNewCities(List<NewCityRequest> newCities) {
        this.newCities = newCities != null ? newCities : new ArrayList<>();
    }

    public List<NewMapRequest> getNewMaps() {
        return newMaps;
    }

    public void setNewMaps(List<NewMapRequest> newMaps) {
        this.newMaps = newMaps != null ? newMaps : new ArrayList<>();
    }

    public List<CityWithMapRequest> getNewCityWithMap() {
        return newCityWithMap;
    }

    public void setNewCityWithMap(List<CityWithMapRequest> newCityWithMap) {
        this.newCityWithMap = newCityWithMap != null ? newCityWithMap : new ArrayList<>();
    }

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public String getDisplayCityName() {
        return displayCityName;
    }

    public void setDisplayCityName(String displayCityName) {
        this.displayCityName = displayCityName;
    }

    public String getDisplayMapName() {
        return displayMapName;
    }

    public void setDisplayMapName(String displayMapName) {
        this.displayMapName = displayMapName;
    }

    public List<String> getDeletedPoiDisplayNames() {
        return deletedPoiDisplayNames;
    }

    public void setDeletedPoiDisplayNames(List<String> deletedPoiDisplayNames) {
        this.deletedPoiDisplayNames = deletedPoiDisplayNames;
    }

    public List<String> getDeletedTourDisplayNames() {
        return deletedTourDisplayNames;
    }

    public void setDeletedTourDisplayNames(List<String> deletedTourDisplayNames) {
        this.deletedTourDisplayNames = deletedTourDisplayNames;
    }

    public boolean hasChanges() {
        return createNewCity || newMapName != null || newMapDescription != null ||
                !addedPois.isEmpty() || !updatedPois.isEmpty() || !deletedPoiIds.isEmpty() ||
                !poiMapLinks.isEmpty() || !poiMapUnlinks.isEmpty() ||
                !addedTours.isEmpty() || !updatedTours.isEmpty() || !deletedTourIds.isEmpty() ||
                !addedStops.isEmpty() || !updatedStops.isEmpty() || !deletedStopIds.isEmpty() ||
                (deletedMapIds != null && !deletedMapIds.isEmpty()) ||
                (deletedCityIds != null && !deletedCityIds.isEmpty()) ||
                (newCities != null && !newCities.isEmpty()) ||
                (newMaps != null && !newMaps.isEmpty()) ||
                (newCityWithMap != null && !newCityWithMap.isEmpty());
    }
}
