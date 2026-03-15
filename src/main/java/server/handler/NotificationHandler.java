package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.NotificationDTO;
import server.SessionManager;
import server.dao.NotificationDAO;

import java.util.List;

/**
 * Handles notification messages (Phase 7).
 * Manages user notifications for subscription expiry and other system events.
 */
public class NotificationHandler {

    /**
     * Check if this handler can process the given message type.
     */
    public static boolean canHandle(MessageType type) {
        switch (type) {
            case GET_MY_NOTIFICATIONS:
            case MARK_NOTIFICATION_READ:
            case GET_UNREAD_COUNT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Handle the notification request.
     */
    public static Response handle(Request request) {
        switch (request.getType()) {
            case GET_MY_NOTIFICATIONS:
                return handleGetMyNotifications(request);
            case MARK_NOTIFICATION_READ:
                return handleMarkNotificationRead(request);
            case GET_UNREAD_COUNT:
                return handleGetUnreadCount(request);
            default:
                return Response.error(request, Response.ERR_INTERNAL, "Unknown notification message type");
        }
    }

    /**
     * GET_MY_NOTIFICATIONS - Get all notifications for current user.
     */
    private static Response handleGetMyNotifications(Request request) {
        System.out.println("═══ GET_MY_NOTIFICATIONS ═══");

        // Validate authentication
        SessionManager.SessionInfo session = validateSession(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        List<NotificationDTO> notifications = NotificationDAO.getNotificationsForUser(session.userId);
        System.out.println("✓ Retrieved " + notifications.size() + " notifications for: " + session.username);
        return Response.success(request, notifications);
    }

    /**
     * MARK_NOTIFICATION_READ - Mark a notification as read.
     * Expected payload: notificationId (Integer)
     */
    private static Response handleMarkNotificationRead(Request request) {
        System.out.println("═══ MARK_NOTIFICATION_READ ═══");

        // Validate authentication
        SessionManager.SessionInfo session = validateSession(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        // Parse notification ID
        int notificationId;
        try {
            notificationId = Integer.parseInt(request.getPayload().toString());
        } catch (Exception e) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid notification ID");
        }

        boolean success = NotificationDAO.markAsRead(notificationId);
        if (success) {
            System.out.println("✓ Marked notification " + notificationId + " as read");
            return Response.success(request, "Notification marked as read");
        } else {
            return Response.error(request, Response.ERR_NOT_FOUND, "Notification not found");
        }
    }

    /**
     * GET_UNREAD_COUNT - Get count of unread notifications.
     */
    private static Response handleGetUnreadCount(Request request) {
        System.out.println("═══ GET_UNREAD_COUNT ═══");

        // Validate authentication
        SessionManager.SessionInfo session = validateSession(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Login required");
        }

        int count = NotificationDAO.getUnreadCount(session.userId);
        System.out.println("✓ Unread count for " + session.username + ": " + count);
        return Response.success(request, count);
    }

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
}
