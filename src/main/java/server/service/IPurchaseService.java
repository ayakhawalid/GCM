package server.service;

import common.dto.*;
import java.util.List;

/**
 * Purchase and subscription service interface.
 * Phase 15: Maintainability - Strict separation with interfaces.
 * 
 * Designed to be mappable to REST endpoints for future web migration.
 */
public interface IPurchaseService {

    /**
     * Get city price.
     * REST mapping: GET /api/cities/{cityId}/price
     */
    double getCityPrice(int cityId);

    /**
     * Process one-time purchase.
     * REST mapping: POST /api/purchases/one-time
     * 
     * @return Purchase ID, or -1 on failure
     */
    int purchaseOneTime(int customerId, int cityId);

    /**
     * Process subscription purchase.
     * REST mapping: POST /api/purchases/subscription
     * 
     * @return Purchase ID, or -1 on failure
     */
    int purchaseSubscription(int customerId, int cityId, int durationMonths);

    /**
     * Check if customer has access to a city.
     * REST mapping: GET /api/customers/{customerId}/access/{cityId}
     */
    boolean hasAccess(int customerId, int cityId);

    /**
     * Record a download event.
     * REST mapping: POST /api/downloads
     */
    void recordDownload(int customerId, int cityId, int mapVersionId);

    /**
     * Record a view event.
     * REST mapping: POST /api/views
     */
    void recordView(int customerId, int cityId, int mapId);

    /**
     * Get customer's purchases.
     * REST mapping: GET /api/customers/{customerId}/purchases
     */
    List<CustomerPurchaseDTO> getCustomerPurchases(int customerId);

    /**
     * Get expiring subscriptions (for scheduler).
     */
    List<ExpiringSubscriptionDTO> getExpiringSubscriptions(int daysAhead);

    /**
     * Renew subscription.
     * REST mapping: POST /api/subscriptions/{subscriptionId}/renew
     */
    boolean renewSubscription(int subscriptionId);
}
