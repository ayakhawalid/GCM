package common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for tour data with all stops.
 * Represents a complete tour with ordered stops and duration info.
 */
public class TourDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int cityId;
    private String name;
    private String description;
    private int estimatedDurationMinutes;
    /** Total route distance in meters (sum of consecutive POI-to-POI distances). */
    private Double totalDistanceMeters;
    private List<TourStopDTO> stops;

    public TourDTO() {
        this.stops = new ArrayList<>();
    }

    public TourDTO(int id, int cityId, String name, String description, int estimatedDurationMinutes) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.description = description;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.stops = new ArrayList<>();
    }

    /**
     * Add a stop to this tour.
     */
    public void addStop(TourStopDTO stop) {
        this.stops.add(stop);
    }

    /**
     * Calculate total duration from stops.
     */
    public int calculateTotalDuration() {
        return stops.stream().mapToInt(TourStopDTO::getDurationMinutes).sum();
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public void setEstimatedDurationMinutes(int estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public Double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public void setTotalDistanceMeters(Double totalDistanceMeters) {
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public List<TourStopDTO> getStops() {
        return stops;
    }

    public void setStops(List<TourStopDTO> stops) {
        this.stops = stops;
    }

    @Override
    public String toString() {
        return name + " (" + stops.size() + " stops, ~" + estimatedDurationMinutes + " min)";
    }
}
