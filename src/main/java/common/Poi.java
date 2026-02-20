package common;

import java.io.Serializable;

/**
 * Entity representing a Point of Interest (POI).
 * POIs can be linked to multiple maps and used in tours.
 */
public class Poi implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int cityId;
    private String name;
    private String location; // e.g., "32.7940,34.9896" (lat,lng) or description
    private Double latitude;  // for map display and distance calculation
    private Double longitude;
    private String category; // e.g., "Beach", "Museum", "Restaurant", "Historic"
    private String shortExplanation; // Brief description
    private boolean accessible; // Wheelchair accessible

    public Poi() {
    }

    public Poi(int id, int cityId, String name, String location,
            String category, String shortExplanation, boolean accessible) {
        this(id, cityId, name, location, null, null, category, shortExplanation, accessible);
    }

    public Poi(int id, int cityId, String name, String location,
            Double latitude, Double longitude, String category, String shortExplanation, boolean accessible) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.shortExplanation = shortExplanation;
        this.accessible = accessible;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCityId() {
        return cityId;
    }

    public void setCityId(int cityId) {
        this.cityId = cityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getShortExplanation() {
        return shortExplanation;
    }

    public void setShortExplanation(String shortExplanation) {
        this.shortExplanation = shortExplanation;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public void setAccessible(boolean accessible) {
        this.accessible = accessible;
    }

    @Override
    public String toString() {
        return name + " [" + category + "] - " + shortExplanation;
    }
}
