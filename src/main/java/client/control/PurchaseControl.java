package client.control;

import client.GCMClient;
import client.LoginController;
import common.MessageType;
import common.Request;
import common.dto.CityPriceInfo;
import common.dto.EntitlementInfo;
import common.dto.PurchaseRequest;
import common.dto.PurchaseResponse;

import java.io.IOException;

/**
 * Client-side controller for purchase operations.
 */
public class PurchaseControl {
    private GCMClient client;

    public PurchaseControl(GCMClient client) {
        this.client = client;
    }

    public interface PurchaseCallback {
        void onSuccess(PurchaseResponse response);

        void onError(String message);
    }

    public interface PriceCallback {
        void onPriceReceived(CityPriceInfo info);

        void onError(String message);
    }

    public interface EntitlementCallback {
        void onEntitlementReceived(EntitlementInfo info);

        void onError(String message);
    }

    public interface GenericCallback {
        void onSuccess(String message);

        void onError(String message);
    }

    /**
     * Get pricing info for a city.
     */
    public void getCityPrice(int cityId, PriceCallback callback) {
        // Since GCMClient uses async messages, we'd typically register a listener or
        // use a request ID.
        // For this project, existing pattern seems to be sending message and handling
        // response in Screen.
        // However, we can send a Request object now.

        Request request = new Request(MessageType.GET_CITY_PRICE, cityId);
        try {
            client.sendToServer(request);
            // The response will come back to the Client's handleMessageFromServer.
            // The Screen needs to register as a listener or handle the response type.
            // For now, we'll just send the request.
            // PROPER IMPLEMENTATION requires the Client to route the response back to the
            // callback.
            // Given the existing architecture shown in DashboardScreen (implements
            // MessageHandler),
            // the Screen receives ALL messages.
            // So this Control class might just be a helper to format requests.
        } catch (IOException e) {
            callback.onError("Network error: " + e.getMessage());
        }
    }

    /**
     * Send purchase request (includes session token for authentication).
     */
    public void purchaseOneTime(int cityId) {
        Request request = new Request(MessageType.PURCHASE_ONE_TIME, new PurchaseRequest(cityId), LoginController.currentSessionToken);
        send(request);
    }

    public void purchaseSubscription(int cityId, int months) {
        Request request = new Request(MessageType.PURCHASE_SUBSCRIPTION, new PurchaseRequest(cityId, months), LoginController.currentSessionToken);
        send(request);
    }

    public void getEntitlement(int cityId) {
        Request request = new Request(MessageType.GET_ENTITLEMENT, cityId, LoginController.currentSessionToken);
        send(request);
    }

    public void checkCanDownload(int cityId) {
        Request request = new Request(MessageType.CAN_DOWNLOAD, cityId, LoginController.currentSessionToken);
        send(request);
    }

    public void downloadMapVersion(int cityId) {
        Request request = new Request(MessageType.DOWNLOAD_MAP_VERSION, cityId, LoginController.currentSessionToken);
        send(request);
    }

    private void send(Request request) {
        try {
            client.sendToServer(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
