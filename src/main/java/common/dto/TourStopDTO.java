package common.dto;

import java.io.Serializable;

/**
 * DTO for tour stop data.
 * Represents a single stop in a tour with order; segment distance to next stop is computed server-side when needed.
 */
public class TourStopDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int tourId;
    private int poiId;
    private String poiName; // For display purposes
    private String poiCategory; // For display purposes
    private int stopOrder;
    /** Distance in meters to the next stop (optional, set when loading tour for display). */
    private Double distanceToNextMeters;
    private String notes;

    public TourStopDTO() {
    }

    public TourStopDTO(int id, int tourId, int poiId, String poiName,
            String poiCategory, int stopOrder, String notes) {
        this.id = id;
        this.tourId = tourId;
        this.poiId = poiId;
        this.poiName = poiName;
        this.poiCategory = poiCategory;
        this.stopOrder = stopOrder;
        this.notes = notes;
    }

    /** Constructor when only id, tourId, poiId, notes are needed (e.g. batch). */
    public TourStopDTO(int id, int tourId, int poiId, String notes) {
        this.id = id;
        this.tourId = tourId;
        this.poiId = poiId;
        this.notes = notes;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTourId() {
        return tourId;
    }

    public void setTourId(int tourId) {
        this.tourId = tourId;
    }

    public int getPoiId() {
        return poiId;
    }

    public void setPoiId(int poiId) {
        this.poiId = poiId;
    }

    public String getPoiName() {
        return poiName;
    }

    public void setPoiName(String poiName) {
        this.poiName = poiName;
    }

    public String getPoiCategory() {
        return poiCategory;
    }

    public void setPoiCategory(String poiCategory) {
        this.poiCategory = poiCategory;
    }

    public int getStopOrder() {
        return stopOrder;
    }

    public void setStopOrder(int stopOrder) {
        this.stopOrder = stopOrder;
    }

    public Double getDistanceToNextMeters() {
        return distanceToNextMeters;
    }

    public void setDistanceToNextMeters(Double distanceToNextMeters) {
        this.distanceToNextMeters = distanceToNextMeters;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        String dist = distanceToNextMeters != null ? String.format(" → %.0f m", distanceToNextMeters) : "";
        return stopOrder + ". " + poiName + dist;
    }
}
