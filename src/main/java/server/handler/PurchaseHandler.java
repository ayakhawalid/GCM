package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CityPriceInfo;
import common.dto.EntitlementInfo;
import common.dto.PurchaseRequest;
import common.dto.PurchaseResponse;
import server.dao.PurchaseDAO;
import server.SessionManager;

import java.time.LocalDate;

/**
 * Handles purchase-related messages.
 */
public class PurchaseHandler {

    public static boolean canHandle(MessageType type) {
        switch (type) {
            case GET_CITY_PRICE:
            case PURCHASE_ONE_TIME:
            case PURCHASE_SUBSCRIPTION:
            case GET_ENTITLEMENT:
            case CAN_DOWNLOAD:
            case DOWNLOAD_MAP_VERSION:
            case RECORD_VIEW_EVENT:
            case GET_MY_PURCHASES:
                return true;
            default:
                return false;
        }
    }

    public static Response handle(Request request) {
        switch (request.getType()) {
            case GET_CITY_PRICE:
                return handleGetCityPrice(request);
            case PURCHASE_ONE_TIME:
                return handlePurchaseOneTime(request);
            case PURCHASE_SUBSCRIPTION:
                return handlePurchaseSubscription(request);
            case GET_ENTITLEMENT:
                return handleGetEntitlement(request);
            case CAN_DOWNLOAD:
                return handleCanDownload(request);
            case DOWNLOAD_MAP_VERSION:
                return handleDownloadMapVersion(request);
            case RECORD_VIEW_EVENT:
                return handleRecordViewEvent(request);
            case GET_MY_PURCHASES:
                return handleGetMyPurchases(request);
            default:
                return Response.error(request, Response.ERR_INTERNAL, "Unknown purchase message type");
        }
    }

    private static Response handleGetMyPurchases(Request request) {
        // Needs authentication
        Integer userId = getAuthenticatedUserId(request);
        if (userId == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        java.util.List<EntitlementInfo> purchases = PurchaseDAO.getUserPurchases(userId);
        return Response.success(request, purchases);
    }

    private static Response handleGetCityPrice(Request request) {
        Object payload = request.getPayload();
        if (!(payload instanceof Integer) && !(payload instanceof String)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid city ID");
        }

        int cityId;
        try {
            cityId = Integer.parseInt(payload.toString());
        } catch (NumberFormatException e) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid city ID format");
        }

        CityPriceInfo info = PurchaseDAO.getCityPrice(cityId);
        if (info == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "City not found");
        }

        return Response.success(request, info);
    }

    private static Response handlePurchaseOneTime(Request request) {
        // Needs authentication
        Integer userId = getAuthenticatedUserId(request);
        if (userId == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required for purchase");
        }

        if (!(request.getPayload() instanceof PurchaseRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid purchase request");
        }

        PurchaseRequest purchase = (PurchaseRequest) request.getPayload();
        if (purchase.getPurchaseType() != PurchaseRequest.PurchaseType.ONE_TIME) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid purchase type");
        }

        boolean success = PurchaseDAO.purchaseOneTime(userId, purchase.getCityId());

        if (success) {
            server.dao.DailyStatsDAO.increment(purchase.getCityId(), server.dao.DailyStatsDAO.Metric.PURCHASE_ONE_TIME);
            return Response.success(request, new PurchaseResponse(true, "Purchase successful!",
                    EntitlementInfo.EntitlementType.ONE_TIME, null));
        } else {
            return Response.error(request, Response.ERR_DATABASE, "Purchase failed");
        }
    }

    private static Response handlePurchaseSubscription(Request request) {
        // Needs authentication
        Integer userId = getAuthenticatedUserId(request);
        if (userId == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required for purchase");
        }

        if (!(request.getPayload() instanceof PurchaseRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid purchase request");
        }

        PurchaseRequest purchase = (PurchaseRequest) request.getPayload();
        if (purchase.getPurchaseType() != PurchaseRequest.PurchaseType.SUBSCRIPTION) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid purchase type");
        }

        boolean isRenewal = PurchaseDAO.hasPreviousSubscription(userId, purchase.getCityId());
        boolean success = PurchaseDAO.purchaseSubscription(userId, purchase.getCityId(), purchase.getMonths());

        if (success) {
            // Log as RENEWAL if user had previous subscription, otherwise SUBSCRIPTION
            server.dao.DailyStatsDAO.Metric metric = isRenewal ? server.dao.DailyStatsDAO.Metric.RENEWAL
                    : server.dao.DailyStatsDAO.Metric.PURCHASE_SUBSCRIPTION;

            server.dao.DailyStatsDAO.increment(purchase.getCityId(), metric);

            LocalDate expiry = LocalDate.now().plusMonths(purchase.getMonths());
            return Response.success(request, new PurchaseResponse(true, "Subscription successful!",
                    EntitlementInfo.EntitlementType.SUBSCRIPTION, expiry));
        } else {
            return Response.error(request, Response.ERR_DATABASE, "Subscription failed");
        }
    }

    private static Response handleGetEntitlement(Request request) {
        // Needs authentication
        Integer userId = getAuthenticatedUserId(request);
        if (userId == null) {
            // Guest has no entitlement
            return Response.success(request,
                    new EntitlementInfo(0, EntitlementInfo.EntitlementType.NONE, null, false, false));
        }

        int cityId;
        try {
            cityId = Integer.parseInt(request.getPayload().toString());
        } catch (Exception e) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid city ID");
        }

        EntitlementInfo entitlement = PurchaseDAO.getEntitlement(userId, cityId);
        return Response.success(request, entitlement);
    }

    private static Response handleCanDownload(Request request) {
        // Needs authentication
        Integer userId = getAuthenticatedUserId(request);
        if (userId == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        int cityId;
        try {
            cityId = Integer.parseInt(request.getPayload().toString());
        } catch (Exception e) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid city ID");
        }

        EntitlementInfo entitlement = PurchaseDAO.getEntitlement(userId, cityId);

        if (entitlement.isCanDownload()) {
            return Response.success(request, true);
        } else {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Purchase required to download");
        }
    }

    private static Response handleDownloadMapVersion(Request request) {
        // Needs authentication
        Integer userId = getAuthenticatedUserId(request);
        if (userId == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        int cityId;
        try {
            cityId = Integer.parseInt(request.getPayload().toString());
        } catch (Exception e) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid city ID");
        }

        EntitlementInfo entitlement = PurchaseDAO.getEntitlement(userId, cityId);

        if (entitlement.isCanDownload()) {
            // Only record in download_events for one-time (so subscription downloads don't use one-time slots)
            if (entitlement.getType() == EntitlementInfo.EntitlementType.ONE_TIME) {
                PurchaseDAO.recordDownload(userId, cityId);
            }
            server.dao.DailyStatsDAO.increment(cityId, server.dao.DailyStatsDAO.Metric.DOWNLOAD);
            return Response.success(request, "Download authorized and recorded");
        }
        if (entitlement.getType() == EntitlementInfo.EntitlementType.ONE_TIME) {
            return Response.error(request, Response.ERR_FORBIDDEN,
                    "One-time download limit reached for this city. You can purchase again for another download.");
        }
        return Response.error(request, Response.ERR_UNAUTHORIZED, "Purchase required to download");
    }

    private static Response handleRecordViewEvent(Request request) {
        // Needs authentication
        Integer userId = getAuthenticatedUserId(request);
        if (userId == null) {
            // Views might be allowed for guests? Requirements say "Subscription: unlimited
            // view".
            // Guest view sounds restricted. But if guest CAN view (e.g. preview), we might
            // not record it or user 0.
            // Implication is views are for subscribers.
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        // Payload expected: "cityId,mapId"
        String payload = (String) request.getPayload();
        String[] parts = payload.split(",");
        if (parts.length != 2) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid format");
        }

        try {
            int cityId = Integer.parseInt(parts[0]);
            int mapId = Integer.parseInt(parts[1]);
            PurchaseDAO.recordView(userId, cityId, mapId);
            server.dao.DailyStatsDAO.increment(cityId, server.dao.DailyStatsDAO.Metric.VIEW);
            return Response.success(request, "View recorded");
        } catch (NumberFormatException e) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid IDs");
        }
    }

    private static Integer getAuthenticatedUserId(Request request) {
        String token = request.getSessionToken();
        if (token == null || token.isEmpty())
            return null;
        SessionManager.SessionInfo info = SessionManager.getInstance().validateSession(token);
        return info != null ? info.userId : null;
    }
}
