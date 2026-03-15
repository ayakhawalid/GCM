package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.*;
import server.DBConnector;
import server.dao.AuditLogDAO;
import server.dao.NotificationDAO;
import server.dao.PricingDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Handler for all pricing-related operations.
 * Manages price inquiries, pricing requests, and approvals.
 */
public class PricingHandler {

    // Pricing action constants for audit log
    public static final String ACTION_PRICING_REQUESTED = "PRICING_REQUESTED";
    public static final String ACTION_PRICING_APPROVED = "PRICING_APPROVED";
    public static final String ACTION_PRICING_REJECTED = "PRICING_REJECTED";
    public static final String ENTITY_PRICING_REQUEST = "PRICING_REQUEST";

    /**
     * Check if this handler can process the given message type.
     */
    public static boolean canHandle(MessageType type) {
        return type == MessageType.GET_CURRENT_PRICES ||
                type == MessageType.SUBMIT_PRICING_REQUEST ||
                type == MessageType.LIST_PENDING_PRICING_REQUESTS ||
                type == MessageType.APPROVE_PRICING_REQUEST ||
                type == MessageType.REJECT_PRICING_REQUEST;
    }

    /**
     * Handle a pricing-related request.
     */
    public static Response handle(Request request) {
        MessageType type = request.getType();

        try {
            switch (type) {
                case GET_CURRENT_PRICES:
                    return handleGetCurrentPrices(request);

                case SUBMIT_PRICING_REQUEST:
                    return handleSubmitPricingRequest(request);

                case LIST_PENDING_PRICING_REQUESTS:
                    return handleListPendingRequests(request);

                case APPROVE_PRICING_REQUEST:
                    return handleApproveRequest(request);

                case REJECT_PRICING_REQUEST:
                    return handleRejectRequest(request);

                default:
                    return Response.error(request, Response.ERR_INTERNAL,
                            "Unknown pricing message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("PricingHandler error: " + e.getMessage());
            e.printStackTrace();
            return Response.error(request, Response.ERR_INTERNAL,
                    "Server error processing pricing request: " + e.getMessage());
        }
    }

    /**
     * Get all cities with current prices.
     * No authentication required - anyone can view prices.
     */
    private static Response handleGetCurrentPrices(Request request) {
        // Ensure table exists
        PricingDAO.ensureTableExists();

        List<CityPriceInfo> prices = PricingDAO.getAllCurrentPrices();
        System.out.println("PricingHandler: Returning " + prices.size() + " city prices");
        return Response.success(request, prices);
    }

    /**
     * Submit a new pricing request.
     * Requires ContentManager or higher role.
     */
    private static Response handleSubmitPricingRequest(Request request) {
        // Validate payload
        if (!(request.getPayload() instanceof SubmitPricingRequest)) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "SubmitPricingRequest payload required");
        }

        SubmitPricingRequest submitReq = (SubmitPricingRequest) request.getPayload();

        // Get user ID from session
        int userId = request.getUserId();
        if (userId <= 0) {
            return Response.error(request, Response.ERR_AUTHENTICATION,
                    "Authentication required");
        }

        // Server-side validation
        ValidationResult validation = validatePricingRequest(submitReq);
        if (!validation.isValid()) {
            return Response.error(request, Response.ERR_VALIDATION,
                    validation.getErrors().get(0).getMessage());
        }

        // Check for existing pending request
        if (PricingDAO.hasPendingRequest(submitReq.getCityId())) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "There is already a pending pricing request for this city");
        }

        // Create the request
        int requestId = PricingDAO.createPricingRequest(
                submitReq.getCityId(),
                submitReq.getProposedPrice(),
                submitReq.getReason(),
                userId);

        if (requestId < 0) {
            return Response.error(request, Response.ERR_INTERNAL,
                    "Failed to create pricing request");
        }

        // Log the action
        AuditLogDAO.logSimple(ACTION_PRICING_REQUESTED, userId,
                ENTITY_PRICING_REQUEST, requestId,
                String.format("{\"cityId\":%d,\"proposedPrice\":%.2f}",
                        submitReq.getCityId(), submitReq.getProposedPrice()));

        // Return the created request
        PricingRequestDTO created = PricingDAO.getRequestById(requestId);
        System.out.println("PricingHandler: Created pricing request #" + requestId);
        return Response.success(request, created);
    }

    /**
     * List all pending pricing requests.
     * For CompanyManager approval screen.
     */
    private static Response handleListPendingRequests(Request request) {
        List<PricingRequestDTO> pending = PricingDAO.listPendingRequests();
        System.out.println("PricingHandler: Found " + pending.size() + " pending requests");
        return Response.success(request, pending);
    }

    /**
     * Approve a pending pricing request.
     * Applies the new price and notifies the submitter.
     */
    private static Response handleApproveRequest(Request request) {
        if (!(request.getPayload() instanceof ApprovePricingRequest)) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "ApprovePricingRequest payload required");
        }

        ApprovePricingRequest approveReq = (ApprovePricingRequest) request.getPayload();
        int requestId = approveReq.getRequestId();

        int approverId = request.getUserId();
        if (approverId <= 0) {
            return Response.error(request, Response.ERR_AUTHENTICATION,
                    "Authentication required");
        }

        // Get request details first
        PricingRequestDTO pricingRequest = PricingDAO.getRequestById(requestId);
        if (pricingRequest == null) {
            return Response.error(request, Response.ERR_NOT_FOUND,
                    "Pricing request not found");
        }

        if (!"PENDING".equals(pricingRequest.getStatus())) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "Request is not pending. Current status: " + pricingRequest.getStatus());
        }

        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            // 1. Approve and apply price
            boolean approved = PricingDAO.approveRequest(conn, requestId, approverId);
            if (!approved) {
                conn.rollback();
                return Response.error(request, Response.ERR_INTERNAL,
                        "Failed to approve pricing request");
            }

            // 2. Log the action
            AuditLogDAO.log(conn, ACTION_PRICING_APPROVED, approverId,
                    ENTITY_PRICING_REQUEST, requestId,
                    "cityId", String.valueOf(pricingRequest.getCityId()),
                    "oldPrice", String.format("%.2f", pricingRequest.getCurrentPrice()),
                    "newPrice", String.format("%.2f", pricingRequest.getProposedPrice()));

            // 3. Notify the submitter
            String title = "Pricing Request Approved";
            String body = String.format(
                    "Your pricing request for %s has been approved!\n\n" +
                            "Price changed from ₪%.2f to ₪%.2f",
                    pricingRequest.getCityName(),
                    pricingRequest.getCurrentPrice(),
                    pricingRequest.getProposedPrice());
            NotificationDAO.createNotification(conn, pricingRequest.getCreatedBy(), title, body);

            conn.commit();

            // Return updated request
            PricingRequestDTO updated = PricingDAO.getRequestById(requestId);
            System.out.println("PricingHandler: Approved pricing request #" + requestId);
            return Response.success(request, updated);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
            System.err.println("PricingHandler approve error: " + e.getMessage());
            return Response.error(request, Response.ERR_INTERNAL,
                    "Database error: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    /* ignore */ }
            }
        }
    }

    /**
     * Reject a pending pricing request.
     * Notifies the submitter with rejection reason.
     */
    private static Response handleRejectRequest(Request request) {
        if (!(request.getPayload() instanceof ApprovePricingRequest)) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "ApprovePricingRequest payload required");
        }

        ApprovePricingRequest rejectReq = (ApprovePricingRequest) request.getPayload();
        int requestId = rejectReq.getRequestId();
        String reason = rejectReq.getReason();

        if (reason == null || reason.trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "Rejection reason is required");
        }

        int rejecterId = request.getUserId();
        if (rejecterId <= 0) {
            return Response.error(request, Response.ERR_AUTHENTICATION,
                    "Authentication required");
        }

        // Get request details
        PricingRequestDTO pricingRequest = PricingDAO.getRequestById(requestId);
        if (pricingRequest == null) {
            return Response.error(request, Response.ERR_NOT_FOUND,
                    "Pricing request not found");
        }

        if (!"PENDING".equals(pricingRequest.getStatus())) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "Request is not pending. Current status: " + pricingRequest.getStatus());
        }

        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            // 1. Reject the request
            boolean rejected = PricingDAO.rejectRequest(conn, requestId, rejecterId, reason);
            if (!rejected) {
                conn.rollback();
                return Response.error(request, Response.ERR_INTERNAL,
                        "Failed to reject pricing request");
            }

            // 2. Log the action
            AuditLogDAO.log(conn, ACTION_PRICING_REJECTED, rejecterId,
                    ENTITY_PRICING_REQUEST, requestId,
                    "cityId", String.valueOf(pricingRequest.getCityId()),
                    "reason", reason);

            // 3. Notify the submitter
            String title = "Pricing Request Rejected";
            String body = String.format(
                    "Your pricing request for %s has been rejected.\n\n" +
                            "Proposed price: ₪%.2f → ₪%.2f\n\n" +
                            "Reason: %s",
                    pricingRequest.getCityName(),
                    pricingRequest.getCurrentPrice(),
                    pricingRequest.getProposedPrice(),
                    reason);
            NotificationDAO.createNotification(conn, pricingRequest.getCreatedBy(), title, body);

            conn.commit();

            // Return updated request
            PricingRequestDTO updated = PricingDAO.getRequestById(requestId);
            System.out.println("PricingHandler: Rejected pricing request #" + requestId);
            return Response.success(request, updated);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
            System.err.println("PricingHandler reject error: " + e.getMessage());
            return Response.error(request, Response.ERR_INTERNAL,
                    "Database error: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    /* ignore */ }
            }
        }
    }

    /**
     * Validate a pricing request.
     */
    private static ValidationResult validatePricingRequest(SubmitPricingRequest req) {
        ValidationResult result = new ValidationResult();

        // Validate city ID
        if (req.getCityId() <= 0) {
            result.addGeneralError("City ID is required");
        }

        // Validate proposed price
        double price = req.getProposedPrice();
        if (price <= 0) {
            result.addGeneralError("Price must be greater than 0");
        } else if (price > 10000) {
            result.addGeneralError("Price cannot exceed ₪10,000");
        }

        // Validate reason
        String reason = req.getReason();
        if (reason == null || reason.trim().length() < 10) {
            result.addGeneralError("Reason must be at least 10 characters");
        }

        return result;
    }
}
