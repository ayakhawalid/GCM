package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CustomerListItemDTO;
import common.dto.CustomerProfileDTO;
import common.dto.CustomerPurchaseDTO;
import server.SessionManager;
import server.dao.PurchaseDAO;
import server.dao.UserDAO;

import java.util.List;
import java.util.Map;

/**
 * Handles customer information messages (Phase 6).
 * Implements RBAC to ensure customers can only access their own data.
 */
public class CustomerHandler {

    // Roles allowed to access admin functions
    private static final String ROLE_MANAGER = "CONTENT_MANAGER";
    private static final String ROLE_COMPANY_MANAGER = "COMPANY_MANAGER";

    /**
     * Check if this handler can process the given message type.
     */
    public static boolean canHandle(MessageType type) {
        switch (type) {
            case GET_MY_PROFILE:
            case UPDATE_MY_PROFILE:
            case ADMIN_LIST_CUSTOMERS:
            case ADMIN_GET_CUSTOMER_PURCHASES:
                return true;
            default:
                return false;
        }
    }

    /**
     * Handle the customer request.
     */
    public static Response handle(Request request) {
        switch (request.getType()) {
            case GET_MY_PROFILE:
                return handleGetMyProfile(request);
            case UPDATE_MY_PROFILE:
                return handleUpdateMyProfile(request);
            case ADMIN_LIST_CUSTOMERS:
                return handleAdminListCustomers(request);
            case ADMIN_GET_CUSTOMER_PURCHASES:
                return handleAdminGetCustomerPurchases(request);
            default:
                return Response.error(request, Response.ERR_INTERNAL, "Unknown customer message type");
        }
    }

    // ==================== Profile Operations ====================

    /**
     * GET_MY_PROFILE - Get current user's profile.
     * Anyone authenticated can access their own profile.
     */
    private static Response handleGetMyProfile(Request request) {
        System.out.println("═══ GET_MY_PROFILE ═══");

        // Validate authentication
        SessionManager.SessionInfo session = validateSession(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        CustomerProfileDTO profile = UserDAO.getProfile(session.userId);
        if (profile == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Profile not found");
        }

        if (isManager(session.role)) {
            maskCardDetails(profile);
        }

        System.out.println("✓ Profile retrieved for: " + session.username);
        return Response.success(request, profile);
    }

    /**
     * UPDATE_MY_PROFILE - Update current user's profile.
     * Expected payload: Map with "email" and/or "phone".
     */
    @SuppressWarnings("unchecked")
    private static Response handleUpdateMyProfile(Request request) {
        System.out.println("═══ UPDATE_MY_PROFILE ═══");

        // Validate authentication
        SessionManager.SessionInfo session = validateSession(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        // Parse payload
        if (!(request.getPayload() instanceof Map)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid update data");
        }

        Map<String, String> updates = (Map<String, String>) request.getPayload();
        String email = updates.get("email");
        String phone = updates.get("phone");
        String cardNumber = updates.get("card");
        String cardExpiry = updates.get("cardExpiry");

        // Validate email if provided
        if (email != null && !email.isEmpty()) {
            if (!email.contains("@")) {
                return Response.error(request, Response.ERR_VALIDATION, "Invalid email format");
            }
            // Check if email already exists for another user
            UserDAO.UserInfo existingUser = UserDAO.findByEmail(email);
            if (existingUser != null && existingUser.id != session.userId) {
                return Response.error(request, Response.ERR_VALIDATION, "Email already in use");
            }
        }

        boolean success = UserDAO.updateProfile(session.userId, email, phone, cardNumber, cardExpiry);
        if (success) {
            System.out.println("✓ Profile updated for: " + session.username);
            // Return updated profile
            CustomerProfileDTO profile = UserDAO.getProfile(session.userId);
            return Response.success(request, profile);
        } else {
            return Response.error(request, Response.ERR_DATABASE, "Failed to update profile");
        }
    }

    // ==================== Admin Operations ====================

    /**
     * ADMIN_LIST_CUSTOMERS - List all customers.
     * Only CONTENT_MANAGER or COMPANY_MANAGER can access.
     */
    private static Response handleAdminListCustomers(Request request) {
        System.out.println("═══ ADMIN_LIST_CUSTOMERS ═══");

        // Validate authentication and role
        SessionManager.SessionInfo session = validateSession(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        if (!isManager(session.role)) {
            System.out.println("✗ Access denied for role: " + session.role);
            return Response.error(request, Response.ERR_UNAUTHORIZED,
                    "Manager access required");
        }

        boolean lastMonthOnly = false;
        if (request.getPayload() != null && request.getPayload() instanceof Boolean) {
            lastMonthOnly = (Boolean) request.getPayload();
        }

        List<CustomerListItemDTO> customers = UserDAO.listAllCustomers(lastMonthOnly);
        System.out.println("✓ Listed " + customers.size() + " customers");
        return Response.success(request, customers);
    }

    /**
     * ADMIN_GET_CUSTOMER_PURCHASES - Get a specific customer's purchases.
     * Only CONTENT_MANAGER or COMPANY_MANAGER can access.
     * Expected payload: userId (Integer) or Map with "userId" and "lastMonthOnly"
     */
    @SuppressWarnings("unchecked")
    private static Response handleAdminGetCustomerPurchases(Request request) {
        System.out.println("═══ ADMIN_GET_CUSTOMER_PURCHASES ═══");

        // Validate authentication and role
        SessionManager.SessionInfo session = validateSession(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        if (!isManager(session.role)) {
            System.out.println("✗ Access denied for role: " + session.role);
            return Response.error(request, Response.ERR_UNAUTHORIZED,
                    "Manager access required");
        }

        // Parse target user ID and lastMonthOnly
        int targetUserId = -1;
        boolean lastMonthOnly = false;

        Object payload = request.getPayload();
        try {
            if (payload instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) payload;
                targetUserId = Integer.parseInt(map.get("userId").toString());
                if (map.containsKey("lastMonthOnly")) {
                    lastMonthOnly = (Boolean) map.get("lastMonthOnly");
                }
            } else {
                targetUserId = Integer.parseInt(payload.toString());
            }
        } catch (Exception e) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid user ID");
        }

        List<CustomerPurchaseDTO> purchases = PurchaseDAO.getPurchasesDetailed(targetUserId, lastMonthOnly);
        System.out.println("✓ Retrieved " + purchases.size() + " purchases for user " + targetUserId);
        return Response.success(request, purchases);
    }

    // ==================== Helper Methods ====================

    /**
     * Validate session token from request.
     */
    private static SessionManager.SessionInfo validateSession(Request request) {
        String token = request.getSessionToken();
        if (token == null || token.isEmpty()) {
            return null;
        }
        return SessionManager.getInstance().validateSession(token);
    }

    /**
     * Check if role is a manager role.
     */
    private static boolean isManager(String role) {
        return ROLE_MANAGER.equals(role) || ROLE_COMPANY_MANAGER.equals(role);
    }

    /**
     * Masks card details for manager view.
     */
    private static void maskCardDetails(CustomerProfileDTO profile) {
        if (profile.getCardLast4() != null) {
            profile.setCardLast4("****");
        }
        if (profile.getCardExpiry() != null) {
            profile.setCardExpiry("**/**");
        }
    }
}
