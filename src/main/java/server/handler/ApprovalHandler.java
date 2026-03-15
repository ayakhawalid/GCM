package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.*;
import server.DBConnector;
import server.dao.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Handler for all version approval operations.
 * Manages listing pending versions, approving, and rejecting.
 */
public class ApprovalHandler {

    /**
     * Handle an approval-related request.
     */
    public static Response handle(Request request) {
        MessageType type = request.getType();

        try {
            switch (type) {
                case LIST_PENDING_MAP_VERSIONS:
                    return handleListPendingVersions(request);

                case GET_MAP_VERSION_DETAILS:
                    return handleGetVersionDetails(request);

                case APPROVE_MAP_VERSION:
                    return handleApproveVersion(request);

                case REJECT_MAP_VERSION:
                    return handleRejectVersion(request);

                default:
                    return Response.error(request, Response.ERR_INTERNAL,
                            "Unknown approval message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("ApprovalHandler error: " + e.getMessage());
            e.printStackTrace();
            return Response.error(request, Response.ERR_INTERNAL,
                    "Server error processing approval request: " + e.getMessage());
        }
    }

    /**
     * List all pending map versions.
     * Returns List<MapVersionDTO> sorted by creation date (newest first).
     */
    private static Response handleListPendingVersions(Request request) {
        List<MapVersionDTO> versions = MapVersionDAO.listPendingVersions();
        System.out.println("ApprovalHandler: Found " + versions.size() + " pending versions");
        return Response.success(request, versions);
    }

    /**
     * Get detailed info about a specific version.
     * Payload: Integer (versionId)
     */
    private static Response handleGetVersionDetails(Request request) {
        if (!(request.getPayload() instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Version ID required");
        }

        int versionId = (Integer) request.getPayload();
        MapVersionDTO version = MapVersionDAO.getVersionById(versionId);

        if (version == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Version not found");
        }

        return Response.success(request, version);
    }

    /**
     * Approve a pending map version.
     * Payload: ApprovalRequest with versionId
     * 
     * Actions:
     * 1. Update MapVersion status to APPROVED
     * 2. Update Approval record to APPROVED
     * 3. Write AuditLog entry
     * 4. Notify customers who purchased the city
     */
    private static Response handleApproveVersion(Request request) {
        if (!(request.getPayload() instanceof ApprovalRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "ApprovalRequest required");
        }

        ApprovalRequest approvalReq = (ApprovalRequest) request.getPayload();
        int versionId = approvalReq.getVersionId();

        // Get version details first
        MapVersionDTO version = MapVersionDAO.getVersionById(versionId);
        if (version == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Version not found");
        }

        if (!"PENDING".equals(version.getStatus())) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "Version is not pending approval. Current status: " + version.getStatus());
        }

        // Get approver ID from session (default to 3 for testing if not available)
        int approverId = request.getUserId() > 0 ? request.getUserId() : 3;

        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            // 1. Update MapVersion status
            boolean updated = MapVersionDAO.updateStatus(conn, versionId, "APPROVED", approverId, null);
            if (!updated) {
                conn.rollback();
                return Response.error(request, Response.ERR_INTERNAL, "Failed to update version status");
            }

            // 2. Update Approval record
            ApprovalDAO.updateApproval(conn, ApprovalDAO.ENTITY_MAP_VERSION, versionId,
                    ApprovalDAO.STATUS_APPROVED, approverId, null);

            // 3. Write AuditLog
            AuditLogDAO.log(conn, AuditLogDAO.ACTION_VERSION_APPROVED, approverId,
                    AuditLogDAO.ENTITY_MAP_VERSION, versionId,
                    "mapId", String.valueOf(version.getMapId()),
                    "mapName", version.getMapName());

            // 4. Notify customers who purchased the city
            int notificationCount = NotificationDAO.notifyCustomersAboutMapUpdate(
                    conn, version.getCityId(), version.getMapName());
            System.out.println("ApprovalHandler: Created " + notificationCount + " customer notifications");

            conn.commit();

            // Return updated version
            MapVersionDTO updatedVersion = MapVersionDAO.getVersionById(versionId);
            return Response.success(request, updatedVersion);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
            System.err.println("ApprovalHandler approve error: " + e.getMessage());
            e.printStackTrace();
            return Response.error(request, Response.ERR_INTERNAL, "Database error: " + e.getMessage());
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
     * Reject a pending map version.
     * Payload: ApprovalRequest with versionId and reason
     * 
     * Actions:
     * 1. Update MapVersion status to REJECTED with reason
     * 2. Update Approval record to REJECTED
     * 3. Write AuditLog entry
     * 4. Notify the ContentEditor who submitted
     */
    private static Response handleRejectVersion(Request request) {
        if (!(request.getPayload() instanceof ApprovalRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "ApprovalRequest required");
        }

        ApprovalRequest approvalReq = (ApprovalRequest) request.getPayload();
        int versionId = approvalReq.getVersionId();
        String reason = approvalReq.getReason();

        if (reason == null || reason.trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Rejection reason is required");
        }

        // Get version details first
        MapVersionDTO version = MapVersionDAO.getVersionById(versionId);
        if (version == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Version not found");
        }

        if (!"PENDING".equals(version.getStatus())) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "Version is not pending approval. Current status: " + version.getStatus());
        }

        // Get rejector ID from session
        int rejectorId = request.getUserId() > 0 ? request.getUserId() : 3;

        Connection conn = null;
        try {
            conn = DBConnector.getConnection();
            conn.setAutoCommit(false);

            // 1. Update MapVersion status
            boolean updated = MapVersionDAO.updateStatus(conn, versionId, "REJECTED", rejectorId, reason);
            if (!updated) {
                conn.rollback();
                return Response.error(request, Response.ERR_INTERNAL, "Failed to update version status");
            }

            // 2. Update Approval record
            ApprovalDAO.updateApproval(conn, ApprovalDAO.ENTITY_MAP_VERSION, versionId,
                    ApprovalDAO.STATUS_REJECTED, rejectorId, reason);

            // 3. Write AuditLog
            AuditLogDAO.log(conn, AuditLogDAO.ACTION_VERSION_REJECTED, rejectorId,
                    AuditLogDAO.ENTITY_MAP_VERSION, versionId,
                    "mapId", String.valueOf(version.getMapId()),
                    "reason", reason);

            // 4. Notify the ContentEditor who submitted
            String title = "Map Version Rejected: " + version.getMapName();
            String body = "Your submitted changes for '" + version.getMapName() +
                    "' have been rejected.\n\nReason: " + reason +
                    "\n\nPlease revise and resubmit.";
            NotificationDAO.createNotification(conn, version.getCreatedBy(), title, body);

            conn.commit();

            // Return updated version
            MapVersionDTO updatedVersion = MapVersionDAO.getVersionById(versionId);
            return Response.success(request, updatedVersion);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
            System.err.println("ApprovalHandler reject error: " + e.getMessage());
            e.printStackTrace();
            return Response.error(request, Response.ERR_INTERNAL, "Database error: " + e.getMessage());
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
     * Check if a message type is handled by this handler.
     */
    public static boolean canHandle(MessageType type) {
        return type == MessageType.LIST_PENDING_MAP_VERSIONS ||
                type == MessageType.GET_MAP_VERSION_DETAILS ||
                type == MessageType.APPROVE_MAP_VERSION ||
                type == MessageType.REJECT_MAP_VERSION;
    }
}
