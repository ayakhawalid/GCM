package common.dto;

import java.io.Serializable;

/**
 * DTO for search request parameters.
 * Used as payload in SEARCH_BY_* requests.
 */
public class SearchRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * City name to search for (used in SEARCH_BY_CITY_NAME and
     * SEARCH_BY_CITY_AND_POI)
     */
    private String cityName;

    /**
     * POI name to search for (used in SEARCH_BY_POI_NAME and
     * SEARCH_BY_CITY_AND_POI)
     */
    private String poiName;

    public SearchRequest() {
    }

    /**
     * Creates a city-only search request.
     */
    public static SearchRequest byCity(String cityName) {
        SearchRequest req = new SearchRequest();
        req.cityName = cityName;
        return req;
    }

    /**
     * Creates a POI-only search request.
     */
    public static SearchRequest byPoi(String poiName) {
        SearchRequest req = new SearchRequest();
        req.poiName = poiName;
        return req;
    }

    /**
     * Creates a combined city + POI search request.
     */
    public static SearchRequest byCityAndPoi(String cityName, String poiName) {
        SearchRequest req = new SearchRequest();
        req.cityName = cityName;
        req.poiName = poiName;
        return req;
    }

    // Getters and Setters
    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getPoiName() {
        return poiName;
    }

    public void setPoiName(String poiName) {
        this.poiName = poiName;
    }

    @Override
    public String toString() {
        return "SearchRequest{cityName='" + cityName + "', poiName='" + poiName + "'}";
    }
}
