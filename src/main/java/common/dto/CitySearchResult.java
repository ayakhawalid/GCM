package common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO containing search results for a single city.
 * Groups maps by city for display in search results.
 */
public class CitySearchResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private int cityId;
    private String cityName;
    private String cityDescription;
    private double cityPrice;
    private int totalMaps;
    private List<MapSummary> maps;

    public CitySearchResult() {
        this.maps = new ArrayList<>();
    }

    public CitySearchResult(int cityId, String cityName, String cityDescription, double cityPrice) {
        this.cityId = cityId;
        this.cityName = cityName;
        this.cityDescription = cityDescription;
        this.cityPrice = cityPrice;
        this.maps = new ArrayList<>();
        this.totalMaps = 0;
    }

    /**
     * Adds a map to this city's results.
     */
    public void addMap(MapSummary map) {
        this.maps.add(map);
        this.totalMaps = this.maps.size();
    }

    // Getters and Setters
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

    public String getCityDescription() {
        return cityDescription;
    }

    public void setCityDescription(String cityDescription) {
        this.cityDescription = cityDescription;
    }

    public double getCityPrice() {
        return cityPrice;
    }

    public void setCityPrice(double cityPrice) {
        this.cityPrice = cityPrice;
    }

    public int getTotalMaps() {
        return totalMaps;
    }

    public void setTotalMaps(int totalMaps) {
        this.totalMaps = totalMaps;
    }

    public List<MapSummary> getMaps() {
        return maps;
    }

    public void setMaps(List<MapSummary> maps) {
        this.maps = maps;
        this.totalMaps = maps.size();
    }

    @Override
    public String toString() {
        return cityName + " (" + totalMaps + " maps) - $" + cityPrice;
    }
}
