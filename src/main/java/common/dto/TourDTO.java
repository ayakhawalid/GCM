package common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for tour data with all stops.
 * Represents a complete tour with ordered stops and total road distance (meters).
 */
public class TourDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int cityId;
    private String name;
    private String description;
    /** Total route distance in meters (sum of consecutive POI-to-POI road distances). */
    private Double totalDistanceMeters;
    private List<TourStopDTO> stops;
    /** True when tour is new and awaiting manager approval. */
    private boolean draft;
    /** True when current user has a PENDING map_edit_request that includes this tour (waiting for manager approval). */
    private boolean waitingForApproval;
    /** True when tour is pending deletion, awaiting manager approval. */
    private boolean pendingDeletion;

    public TourDTO() {
        this.stops = new ArrayList<>();
    }

    public TourDTO(int id, int cityId, String name, String description) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.description = description;
        this.stops = new ArrayList<>();
    }

    /**
     * Add a stop to this tour.
     */
    public void addStop(TourStopDTO stop) {
        this.stops.add(stop);
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

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public boolean isWaitingForApproval() {
        return waitingForApproval;
    }

    public void setWaitingForApproval(boolean waitingForApproval) {
        this.waitingForApproval = waitingForApproval;
    }

    public boolean isPendingDeletion() {
        return pendingDeletion;
    }

    public void setPendingDeletion(boolean pendingDeletion) {
        this.pendingDeletion = pendingDeletion;
    }

    @Override
    public String toString() {
        String dist = totalDistanceMeters != null ? String.format("%.0f m", totalDistanceMeters) : "? m";
        return name + " (" + stops.size() + " stops, " + dist + ")";
    }
}
