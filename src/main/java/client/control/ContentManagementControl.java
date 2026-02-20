package client.control;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CityDTO;
import common.dto.MapChanges;
import common.dto.MapContent;
import common.dto.MapSummary;
import common.Poi;
import common.dto.TourDTO;
import common.dto.TourStopDTO;
import common.dto.ValidationResult;
import common.dto.MapEditRequestDTO;

import client.GCMClient;
import client.LoginController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side controller for content management (map editing) operations.
 */
public class ContentManagementControl implements GCMClient.MessageHandler {

    /** Callback for content management operations */
    private ContentCallback callback;
    private GCMClient client;
    private MessageType lastRequestType;

    /**
     * Callback interface for content management results.
     */
    public interface ContentCallback {
        void onCitiesReceived(List<CityDTO> cities);

        void onMapsReceived(List<MapSummary> maps);

        void onMapContentReceived(MapContent content);

        void onPoisForCityReceived(List<common.Poi> pois);

        void onValidationResult(ValidationResult result);

        void onPendingRequestsReceived(List<MapEditRequestDTO> requests);

        void onError(String errorCode, String errorMessage);
    }

    public ContentManagementControl(String host, int port) throws IOException {
        // Host/port ignored, use singleton
        client = GCMClient.getInstance();
        client.setMessageHandler(this);
        System.out.println("ContentManagementControl: Connected via Singleton Client");
    }

    /**
     * Set the callback for receiving results.
     */
    public void setCallback(ContentCallback callback) {
        this.callback = callback;
    }

    // ==================== City Operations ====================

    /**
     * Get all cities for editor.
     */
    public void getCities() {
        Request request = new Request(MessageType.GET_CITIES);
        sendRequest(request);
    }

    /**
     * Create a new city.
     */
    public void createCity(String name, String description, double price) {
        CityDTO city = new CityDTO(0, name, description, price, 0);
        Request request = new Request(MessageType.CREATE_CITY, city);
        sendRequest(request);
    }

    /**
     * Update an existing city.
     */
    public void updateCity(CityDTO city) {
        Request request = new Request(MessageType.UPDATE_CITY, city);
        sendRequest(request);
    }

    // ==================== Map Operations ====================

    /**
     * Get maps for a city.
     */
    public void getMapsForCity(int cityId) {
        Request request = new Request(MessageType.GET_MAPS_FOR_CITY, cityId);
        sendRequest(request);
    }

    /**
     * Get full map content for editing.
     */
    public void getMapContent(int mapId) {
        Request request = new Request(MessageType.GET_MAP_CONTENT, mapId);
        sendRequest(request);
    }

    /**
     * Get all POIs for a city (for adding tour stops from any map, or adding existing POI to current map).
     */
    public void getPoisForCity(int cityId) {
        Request request = new Request(MessageType.GET_POIS_FOR_CITY, cityId);
        sendRequest(request);
    }

    /**
     * Create a new map.
     */
    public void createMap(int cityId, String name, String description) {
        MapContent map = new MapContent(0, cityId, null, name, description);
        Request request = new Request(MessageType.CREATE_MAP, map);
        sendRequest(request);
    }

    // ==================== POI Operations ====================

    /**
     * Add a new POI.
     */
    public void addPoi(Poi poi) {
        Request request = new Request(MessageType.ADD_POI, poi);
        sendRequest(request);
    }

    /**
     * Update an existing POI.
     */
    public void updatePoi(Poi poi) {
        Request request = new Request(MessageType.UPDATE_POI, poi);
        sendRequest(request);
    }

    /**
     * Delete a POI.
     */
    public void deletePoi(int poiId) {
        Request request = new Request(MessageType.DELETE_POI, poiId);
        sendRequest(request);
    }

    // ==================== Tour Operations ====================

    /**
     * Create a new tour.
     */
    public void createTour(TourDTO tour) {
        Request request = new Request(MessageType.CREATE_TOUR, tour);
        sendRequest(request);
    }

    /**
     * Update a tour.
     */
    public void updateTour(TourDTO tour) {
        Request request = new Request(MessageType.UPDATE_TOUR, tour);
        sendRequest(request);
    }

    /**
     * Delete a tour.
     */
    public void deleteTour(int tourId) {
        Request request = new Request(MessageType.DELETE_TOUR, tourId);
        sendRequest(request);
    }

    /**
     * Add a stop to a tour.
     */
    public void addTourStop(TourStopDTO stop) {
        Request request = new Request(MessageType.ADD_TOUR_STOP, stop);
        sendRequest(request);
    }

    /**
     * Update a tour stop.
     */
    public void updateTourStop(TourStopDTO stop) {
        Request request = new Request(MessageType.UPDATE_TOUR_STOP, stop);
        sendRequest(request);
    }

    /**
     * Remove a tour stop.
     */
    public void removeTourStop(int stopId) {
        Request request = new Request(MessageType.REMOVE_TOUR_STOP, stopId);
        sendRequest(request);
    }

    // ==================== Batch Submit ====================

    /**
     * Submit multiple changes in a single transaction.
     * Uses current session so the server records the correct submitter (employee/manager, not customer).
     */
    public void submitMapChanges(MapChanges changes) {
        String token = LoginController.currentSessionToken;
        Request request = new Request(MessageType.SUBMIT_MAP_CHANGES, changes, token);
        sendRequest(request);
    }

    // ==================== Approval Operations ====================

    public void getPendingMapEdits() {
        Request request = new Request(MessageType.GET_PENDING_MAP_EDITS);
        sendRequest(request);
    }

    public void approveMapEdit(int requestId) {
        Request request = new Request(MessageType.APPROVE_MAP_EDIT, requestId);
        sendRequest(request);
    }

    public void rejectMapEdit(int requestId) {
        Request request = new Request(MessageType.REJECT_MAP_EDIT, requestId);
        sendRequest(request);
    }

    // ==================== Internal Methods ====================

    private void sendRequest(Request request) {
        if (client == null) {
            if (callback != null) callback.onError("CONNECTION_ERROR", "Not connected to server");
            return;
        }
        if (!client.ensureConnected()) {
            if (callback != null) callback.onError("CONNECTION_ERROR", "Could not connect to server. Is the server running?");
            return;
        }
        try {
            System.out.println("ContentManagementControl: Sending request - " + request.getType());
            lastRequestType = request.getType();
            client.sendToServer(request);
        } catch (IOException e) {
            System.out.println("ContentManagementControl: Error sending request: " + e.getMessage());
            e.printStackTrace();
            if (callback != null) {
                callback.onError("CONNECTION_ERROR", "Failed to send request to server. Try again or check the server is running.");
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void displayMessage(Object msg) {
        System.out.println("ContentManagementControl: Received message from server");

        if (!(msg instanceof Response)) {
            System.out.println("ContentManagementControl: Unknown message type");
            return;
        }

        Response response = (Response) msg;

        if (callback == null) {
            System.out.println("ContentManagementControl: No callback registered");
            return;
        }

        if (!response.isOk()) {
            callback.onError(response.getErrorCode(), response.getErrorMessage());
            return;
        }

        Object payload = response.getPayload();

        // Determine response type and call appropriate callback
        if (payload instanceof List) {
            List<?> list = (List<?>) payload;
            if (!list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof CityDTO) {
                    callback.onCitiesReceived((List<CityDTO>) payload);
                } else if (first instanceof MapSummary) {
                    callback.onMapsReceived((List<MapSummary>) payload);
                } else if (first instanceof MapEditRequestDTO) {
                    System.out.println("ContentManagementControl: Routing " + list.size()
                            + " MapEditRequestDTO items to callback");
                    callback.onPendingRequestsReceived((List<MapEditRequestDTO>) payload);
                } else if (first instanceof Poi) {
                    callback.onPoisForCityReceived((List<Poi>) payload);
                }
            } else {
                if (lastRequestType == MessageType.GET_MAPS_FOR_CITY) {
                    callback.onMapsReceived(new ArrayList<>());
                } else if (lastRequestType == MessageType.GET_PENDING_MAP_EDITS) {
                    callback.onPendingRequestsReceived(new ArrayList<>());
                } else if (lastRequestType == MessageType.GET_POIS_FOR_CITY) {
                    callback.onPoisForCityReceived(new ArrayList<>());
                } else {
                    callback.onCitiesReceived(new ArrayList<>());
                }
            }
        } else if (payload instanceof MapContent) {
            callback.onMapContentReceived((MapContent) payload);
        } else if (payload instanceof ValidationResult) {
            callback.onValidationResult((ValidationResult) payload);
        }
    }

    /**
     * Disconnect from server.
     */
    public void disconnect() {
        if (client != null)
            client.setMessageHandler(null);
    }
}
