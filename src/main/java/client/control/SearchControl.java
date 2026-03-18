package client.control;

import java.io.IOException;
import java.util.List;

import client.GCMClient;
import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CitySearchResult;
import common.dto.CustomerProfileDTO;
import common.dto.SearchRequest;
import client.LoginController;

/**
 * Client-side controller for search operations.
 * Manages communication with server for all search-related functionality.
 */
public class SearchControl implements GCMClient.MessageHandler {

    /** Callback for displaying search results */
    private SearchResultCallback resultCallback;
    private GCMClient client;

    /**
     * Callback interface for search results.
     */
    public interface SearchResultCallback {
        void onSearchResults(List<CitySearchResult> results);

        void onDiscountEligibility(boolean isEligible);

        void onError(String errorCode, String errorMessage);
    }

    /** Callback for receiving profile (e.g. saved card) when opening checkout. */
    public interface ProfileCallback {
        void onProfile(CustomerProfileDTO profile);
    }

    private ProfileCallback pendingProfileCallback;

    public SearchControl(String host, int port) throws IOException {
        // Host/port ignored as we use singleton
        client = GCMClient.getInstance();
        client.setMessageHandler(this);
        System.out.println("SearchControl: Connected via Singleton Client");
    }

    /**
     * Set the callback for receiving search results.
     */
    public void setResultCallback(SearchResultCallback callback) {
        this.resultCallback = callback;
    }

    /**
     * Get the full cities catalog.
     */
    public void getCatalog() {
        Request request = new Request(MessageType.GET_CITIES_CATALOG);
        sendRequest(request);
    }

    /**
     * Search by city name.
     */
    public void searchByCityName(String cityName) {
        SearchRequest searchReq = SearchRequest.byCity(cityName);
        Request request = new Request(MessageType.SEARCH_BY_CITY_NAME, searchReq);
        sendRequest(request);
    }

    /**
     * Search by POI name.
     */
    public void searchByPoiName(String poiName) {
        SearchRequest searchReq = SearchRequest.byPoi(poiName);
        Request request = new Request(MessageType.SEARCH_BY_POI_NAME, searchReq);
        sendRequest(request);
    }

    /**
     * Search by both city and POI name.
     */
    public void searchByCityAndPoi(String cityName, String poiName) {
        SearchRequest searchReq = SearchRequest.byCityAndPoi(cityName, poiName);
        Request request = new Request(MessageType.SEARCH_BY_CITY_AND_POI, searchReq);
        sendRequest(request);
    }

    public void checkDiscountEligibility(int cityId, int months) {
        // Must send auth token
        String token = LoginController.currentSessionToken;
        if (token == null || token.isEmpty()) {
            if (resultCallback != null)
                resultCallback.onDiscountEligibility(false);
            return;
        }

        common.dto.DiscountCheckRequest reqPayload = new common.dto.DiscountCheckRequest(cityId, months);
        Request request = new Request(MessageType.CHECK_DISCOUNT_ELIGIBILITY, reqPayload, token);
        sendRequest(request);
    }

    /**
     * Fetch current user's profile (e.g. for saved card in checkout).
     * Invokes callback with CustomerProfileDTO on success, or null on error/not found.
     */
    public void getMyProfile(ProfileCallback callback) {
        String token = LoginController.currentSessionToken;
        if (token == null || token.isEmpty()) {
            if (callback != null)
                callback.onProfile(null);
            return;
        }
        pendingProfileCallback = callback;
        Request request = new Request(MessageType.GET_MY_PROFILE, null, token);
        sendRequest(request);
    }

    /**
     * Send a request to the server.
     */
    private void sendRequest(Request request) {
        try {
            System.out.println("SearchControl: Sending request - " + request.getType());
            client.sendToServer(request);
        } catch (IOException e) {
            System.out.println("SearchControl: Error sending request");
            e.printStackTrace();
            if (resultCallback != null) {
                resultCallback.onError("CONNECTION_ERROR", "Failed to send request to server");
            }
        }
    }

    public void sendPurchaseRequest(Request request) {
        try {
            client.sendToServer(request);
        } catch (IOException e) {
            if (resultCallback != null)
                resultCallback.onError("NETWORK", "Failed to send purchase request");
        }
    }

    @Override
    public void displayMessage(Object msg) {
        if (msg instanceof Response) {
            Response response = (Response) msg;
            MessageType type = response.getRequestType();
            System.out.println("SearchControl: Received response for " + type);
            handleResponse(response);
        } else {
            System.out.println(
                    "SearchControl: Unknown message type: " + (msg != null ? msg.getClass().getName() : "null"));
        }
    }

    /** Message types that this controller handles (catalog/search). */
    private static boolean isSearchResponse(MessageType type) {
        return type == MessageType.GET_CITIES_CATALOG
                || type == MessageType.SEARCH_BY_CITY_NAME
                || type == MessageType.SEARCH_BY_POI_NAME
                || type == MessageType.SEARCH_BY_CITY_AND_POI;
    }

    /**
     * Handle a response from the server.
     * Only processes responses for catalog/search requests so we don't consume or
     * misinterpret other responses (e.g. GET_UNREAD_COUNT).
     */
    @SuppressWarnings("unchecked")
    private void handleResponse(Response response) {
        if (resultCallback == null) {
            return;
        }

        MessageType requestType = response.getRequestType();
        if (requestType == null)
            return;

        if (requestType == MessageType.CHECK_DISCOUNT_ELIGIBILITY) {
            if (response.isOk() && response.getPayload() instanceof Boolean) {
                resultCallback.onDiscountEligibility((Boolean) response.getPayload());
            } else {
                resultCallback.onDiscountEligibility(false); // Default to no discount on error
            }
            return;
        }

        if (requestType == MessageType.GET_MY_PROFILE) {
            ProfileCallback cb = pendingProfileCallback;
            pendingProfileCallback = null;
            if (cb != null) {
                CustomerProfileDTO profile = null;
                if (response.isOk() && response.getPayload() instanceof CustomerProfileDTO) {
                    profile = (CustomerProfileDTO) response.getPayload();
                }
                cb.onProfile(profile);
            }
            return;
        }

        if (!isSearchResponse(requestType)) {
            // Not a search response – ignore (e.g. from Dashboard or another screen)
            return;
        }

        if (response.isOk()) {
            Object payload = response.getPayload();
            if (payload instanceof List) {
                List<?> list = (List<?>) payload;
                if (list.isEmpty() || list.get(0) instanceof CitySearchResult) {
                    resultCallback.onSearchResults((List<CitySearchResult>) payload);
                } else {
                    resultCallback.onError("INVALID_RESPONSE", "Server returned unexpected data format");
                }
            } else if (payload == null) {
                // Success with no payload = empty catalog
                resultCallback.onSearchResults(java.util.Collections.emptyList());
            } else {
                resultCallback.onError("INVALID_RESPONSE", "Server returned unexpected data format");
            }
        } else {
            resultCallback.onError(
                    response.getErrorCode() != null ? response.getErrorCode() : "ERROR",
                    response.getErrorMessage() != null ? response.getErrorMessage() : "Request failed");
        }
    }

    /**
     * Disconnect from server.
     */
    public void disconnect() {
        // Do NOT close connection as it is shared singleton. just remove handler
        if (client != null)
            client.setMessageHandler(null);
    }
}
