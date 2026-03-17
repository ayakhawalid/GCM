package common.dto;

import java.io.Serializable;

/**
 * DTO containing summary information about a map.
 * Used in search results.
 */
public class MapSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String shortDescription;
    private String cityDescription;
    private int poiCount;
    private int tourCount;
    /** City ID this map belongs to (for filtering pending maps by city). */
    private int cityId;
    /** True when map is draft (not yet approved); show [Draft] in UI. */
    private boolean draft;
    /** True when current user has a PENDING map_edit_request for this map (waiting for manager approval). */
    private boolean waitingForApproval;
    /** When non-null and > 0, this map is the tour route map for that tour (show under "Tours" section). */
    private Integer tourId;

    public MapSummary() {
    }

    public MapSummary(int id, String name, String shortDescription, int poiCount, int tourCount) {
        this.id = id;
        this.name = name;
        this.shortDescription = shortDescription;
        this.poiCount = poiCount;
        this.tourCount = tourCount;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getCityDescription() {
        return cityDescription;
    }

    public void setCityDescription(String cityDescription) {
        this.cityDescription = cityDescription;
    }

    public int getPoiCount() {
        return poiCount;
    }

    public void setPoiCount(int poiCount) {
        this.poiCount = poiCount;
    }

    public int getTourCount() {
        return tourCount;
    }

    public void setTourCount(int tourCount) {
        this.tourCount = tourCount;
    }

    public int getCityId() { return cityId; }
    public void setCityId(int cityId) { this.cityId = cityId; }
    public boolean isDraft() { return draft; }
    public void setDraft(boolean draft) { this.draft = draft; }
    public boolean isWaitingForApproval() { return waitingForApproval; }
    public void setWaitingForApproval(boolean waitingForApproval) { this.waitingForApproval = waitingForApproval; }
    public Integer getTourId() { return tourId; }
    public void setTourId(Integer tourId) { this.tourId = tourId; }

    @Override
    public String toString() {
        return name + " - " + shortDescription + " (" + poiCount + " POIs, " + tourCount + " tours)";
    }
}
