package common;

/**
 * Enumeration of all message types for client-server communication.
 * Used in Request objects to identify the operation to perform.
 */
public enum MessageType {

    // ==================== SEARCH (Phase 1) ====================
    /** Get all cities with map counts (catalog view) */
    GET_CITIES_CATALOG,

    /** Search maps by city name (case-insensitive) */
    SEARCH_BY_CITY_NAME,

    /** Search maps by POI name (case-insensitive) */
    SEARCH_BY_POI_NAME,

    /** Search maps by both city and POI name */
    SEARCH_BY_CITY_AND_POI,

    // ==================== MAP EDITING (Phase 2) ====================
    /** Get all cities for editor */
    GET_CITIES,

    /** Get maps for a specific city */
    GET_MAPS_FOR_CITY,

    /** Get full map content for editing */
    GET_MAP_CONTENT,

    /** Submit map changes (create/update) */
    SUBMIT_MAP_CHANGES,

    // --- City Operations ---
    /** Create a new city */
    CREATE_CITY,

    /** Update existing city */
    UPDATE_CITY,

    // --- Map Operations ---
    /** Create a new map */
    CREATE_MAP,

    /** Update existing map */
    UPDATE_MAP,

    /** Delete a map */
    DELETE_MAP,

    // --- POI Operations ---
    /** Add new POI */
    ADD_POI,

    /** Update existing POI */
    UPDATE_POI,

    /** Delete POI */
    DELETE_POI,

    /** Link POI to map */
    LINK_POI_TO_MAP,

    /** Unlink POI from map */
    UNLINK_POI_FROM_MAP,

    // --- Tour Operations ---
    /** Create a new tour */
    CREATE_TOUR,

    /** Update existing tour */
    UPDATE_TOUR,

    /** Delete tour */
    DELETE_TOUR,

    /** Add stop to tour */
    ADD_TOUR_STOP,

    /** Update tour stop */
    UPDATE_TOUR_STOP,

    /** Remove stop from tour */
    REMOVE_TOUR_STOP,

    // ==================== VERSION PUBLISHING (Phase 3) ====================
    /** List all pending map versions for approval */
    LIST_PENDING_MAP_VERSIONS,

    /** Get detailed info about a specific map version */
    GET_MAP_VERSION_DETAILS,

    /** Approve a pending map version */
    APPROVE_MAP_VERSION,

    /** Reject a pending map version */
    REJECT_MAP_VERSION,

    // ==================== PURCHASE (Phase 5) ====================
    /** Get city pricing information */
    GET_CITY_PRICE,

    /** Purchase one-time viewing rights */
    PURCHASE_ONE_TIME,

    /** Purchase subscription */
    PURCHASE_SUBSCRIPTION,

    /** Check user entitlement for a city */
    GET_ENTITLEMENT,

    /** Check if user can download map */
    CAN_DOWNLOAD,

    /** Record map version download event */
    DOWNLOAD_MAP_VERSION,

    /** Record map view event */
    RECORD_VIEW_EVENT,

    /** Get user's purchase history */
    GET_MY_PURCHASES,

    // ==================== AUTHENTICATION (Phase 4) ====================
    /** Register new customer */
    REGISTER_CUSTOMER,

    /** Login user */
    LOGIN,

    /** Logout user */
    LOGOUT,

    // ==================== CUSTOMER INFO (Phase 6) ====================
    /** Get current user's profile */
    GET_MY_PROFILE,

    /** Update current user's profile */
    UPDATE_MY_PROFILE,

    /** Admin: List all customers */
    ADMIN_LIST_CUSTOMERS,

    /** Admin: Get specific customer's purchases */
    ADMIN_GET_CUSTOMER_PURCHASES,

    // ==================== NOTIFICATIONS (Phase 7) ====================
    /** Get user's notifications */
    GET_MY_NOTIFICATIONS,

    /** Mark notification as read */
    MARK_NOTIFICATION_READ,

    /** Get unread notification count */
    GET_UNREAD_COUNT,

    // ==================== PRICING (Phase 8) ====================
    /** Get all cities with current prices */
    GET_CURRENT_PRICES,

    /** Submit a pricing change request */
    SUBMIT_PRICING_REQUEST,

    /** List all pending pricing requests */
    LIST_PENDING_PRICING_REQUESTS,

    /** Approve a pending pricing request */
    APPROVE_PRICING_REQUEST,

    /** Reject a pending pricing request */
    REJECT_PRICING_REQUEST,

    // ==================== LEGACY (backward compatibility) ====================
    /** Legacy: get all cities as simple list */
    LEGACY_GET_CITIES,

    /** Legacy: get maps for city */
    LEGACY_GET_MAPS,

    /** Legacy: update city price */
    LEGACY_UPDATE_PRICE,

    // ==================== SUPPORT (Phase 9) ====================
    /** Create a new support ticket */
    CREATE_TICKET,

    /** Get current user's tickets */
    GET_MY_TICKETS,

    /** Get ticket details with messages */
    GET_TICKET_DETAILS,

    /** Close a ticket (resolved) */
    CLOSE_TICKET,

    /** Request escalation to human agent */
    ESCALATE_TICKET,

    /** Agent: list assigned tickets */
    AGENT_LIST_ASSIGNED,

    /** Agent: list pending escalations */
    AGENT_LIST_PENDING,

    /** Agent: claim an unassigned ticket */
    AGENT_CLAIM_TICKET,

    /** Agent: send reply to customer */
    AGENT_REPLY,

    /** Agent: close/resolve ticket */
    AGENT_CLOSE_TICKET,

    // ==================== REPORTS (Phase 10) ====================
    /** Get activity report stats */
    GET_ACTIVITY_REPORT,

    // ==================== MAP EDIT APPROVALS ====================
    /** Get all POIs for a city (for tours from different maps, add existing POI to map) */
    GET_POIS_FOR_CITY,

    /** List all pending map edit requests */
    GET_PENDING_MAP_EDITS,

    /** Approve a map edit request */
    APPROVE_MAP_EDIT,

    /** Reject a map edit request */
    REJECT_MAP_EDIT
}
