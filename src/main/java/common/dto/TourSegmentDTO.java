package common.dto;

import java.io.Serializable;

/**
 * One segment of a tour route (between two consecutive POIs, or last→first for circle back).
 * Used to draw the route on a tour map and show distance on hover.
 */
public class TourSegmentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int fromPoiId;
    private int toPoiId;
    private Double fromLat;
    private Double fromLon;
    private Double toLat;
    private Double toLon;
    private Double distanceMeters;

    public TourSegmentDTO() {
    }

    public TourSegmentDTO(int fromPoiId, int toPoiId, Double fromLat, Double fromLon, Double toLat, Double toLon, Double distanceMeters) {
        this.fromPoiId = fromPoiId;
        this.toPoiId = toPoiId;
        this.fromLat = fromLat;
        this.fromLon = fromLon;
        this.toLat = toLat;
        this.toLon = toLon;
        this.distanceMeters = distanceMeters;
    }

    public int getFromPoiId() { return fromPoiId; }
    public void setFromPoiId(int fromPoiId) { this.fromPoiId = fromPoiId; }
    public int getToPoiId() { return toPoiId; }
    public void setToPoiId(int toPoiId) { this.toPoiId = toPoiId; }
    public Double getFromLat() { return fromLat; }
    public void setFromLat(Double fromLat) { this.fromLat = fromLat; }
    public Double getFromLon() { return fromLon; }
    public void setFromLon(Double fromLon) { this.fromLon = fromLon; }
    public Double getToLat() { return toLat; }
    public void setToLat(Double toLat) { this.toLat = toLat; }
    public Double getToLon() { return toLon; }
    public void setToLon(Double toLon) { this.toLon = toLon; }
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
}
