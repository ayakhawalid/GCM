package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CitySearchResult;
import common.dto.SearchRequest;
import server.dao.SearchDAO;

import java.util.List;

/**
 * Handler for all search-related message types.
 * Processes search requests and returns search results.
 */
public class SearchHandler {

    /**
     * Handle a search-related request.
     * 
     * @param request The incoming request
     * @return Response with search results or error
     */
    public static Response handle(Request request) {
        MessageType type = request.getType();

        try {
            switch (type) {
                case GET_CITIES_CATALOG:
                    return handleGetCatalog(request);

                case SEARCH_BY_CITY_NAME:
                    return handleSearchByCityName(request);

                case SEARCH_BY_POI_NAME:
                    return handleSearchByPoiName(request);

                case SEARCH_BY_CITY_AND_POI:
                    return handleSearchByCityAndPoi(request);

                default:
                    return Response.error(request, Response.ERR_INTERNAL,
                            "Unknown search message type: " + type);
            }
        } catch (Exception e) {
            System.out.println("SearchHandler: Error processing request - " + e.getMessage());
            e.printStackTrace();
            return Response.error(request, Response.ERR_INTERNAL,
                    "Error processing search: " + e.getMessage());
        }
    }

    /**
     * Get all cities catalog.
     */
    private static Response handleGetCatalog(Request request) {
        System.out.println("SearchHandler: Getting cities catalog");
        List<CitySearchResult> results = SearchDAO.getCitiesCatalog();
        return Response.success(request, results);
    }

    /**
     * Search by city name.
     */
    private static Response handleSearchByCityName(Request request) {
        SearchRequest searchReq = getSearchRequest(request);
        if (searchReq == null || searchReq.getCityName() == null) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "City name is required for city search");
        }

        System.out.println("SearchHandler: Searching by city name: " + searchReq.getCityName());
        List<CitySearchResult> results = SearchDAO.searchByCityName(searchReq.getCityName());
        return Response.success(request, results);
    }

    /**
     * Search by POI name.
     */
    private static Response handleSearchByPoiName(Request request) {
        SearchRequest searchReq = getSearchRequest(request);
        if (searchReq == null || searchReq.getPoiName() == null) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "POI name is required for POI search");
        }

        System.out.println("SearchHandler: Searching by POI name: " + searchReq.getPoiName());
        List<CitySearchResult> results = SearchDAO.searchByPoiName(searchReq.getPoiName());
        return Response.success(request, results);
    }

    /**
     * Search by both city and POI name.
     */
    private static Response handleSearchByCityAndPoi(Request request) {
        SearchRequest searchReq = getSearchRequest(request);
        if (searchReq == null) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "Search request is required");
        }

        // At least one criterion must be provided
        if ((searchReq.getCityName() == null || searchReq.getCityName().isEmpty()) &&
                (searchReq.getPoiName() == null || searchReq.getPoiName().isEmpty())) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "At least city name or POI name must be provided");
        }

        System.out.println("SearchHandler: Searching by city='" + searchReq.getCityName() +
                "' and POI='" + searchReq.getPoiName() + "'");
        List<CitySearchResult> results = SearchDAO.searchByCityAndPoi(
                searchReq.getCityName(), searchReq.getPoiName());
        return Response.success(request, results);
    }

    /**
     * Extract SearchRequest from request payload.
     */
    private static SearchRequest getSearchRequest(Request request) {
        Object payload = request.getPayload();
        if (payload instanceof SearchRequest) {
            return (SearchRequest) payload;
        }
        return null;
    }

    /**
     * Check if a message type is handled by this handler.
     */
    public static boolean canHandle(MessageType type) {
        return type == MessageType.GET_CITIES_CATALOG ||
                type == MessageType.SEARCH_BY_CITY_NAME ||
                type == MessageType.SEARCH_BY_POI_NAME ||
                type == MessageType.SEARCH_BY_CITY_AND_POI;
    }
}
