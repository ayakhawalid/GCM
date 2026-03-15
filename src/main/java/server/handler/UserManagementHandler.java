package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.StaffUserDTO;
import server.SessionManager;
import server.dao.UserDAO;

import java.util.List;
import java.util.Map;

/**
 * Handles user management operations.
 * Restricted to COMPANY_MANAGER role only.
 */
public class UserManagementHandler {

    private static final String ROLE_COMPANY_MANAGER = "COMPANY_MANAGER";

    public static boolean canHandle(MessageType type) {
        switch (type) {
            case ADMIN_LIST_STAFF:
            case ADMIN_UPDATE_USER_ROLE:
            case ADMIN_REVOKE_ROLE:
            case ADMIN_CREATE_STAFF_USER:
                return true;
            default:
                return false;
        }
    }

    public static Response handle(Request request) {
        switch (request.getType()) {
            case ADMIN_LIST_STAFF:
                return handleListStaff(request);
            case ADMIN_UPDATE_USER_ROLE:
                return handleUpdateRole(request);
            case ADMIN_REVOKE_ROLE:
                return handleRevokeRole(request);
            case ADMIN_CREATE_STAFF_USER:
                return handleCreateStaffUser(request);
            default:
                return Response.error(request, Response.ERR_INTERNAL, "Unknown user management type");
        }
    }

    private static Response handleListStaff(Request request) {
        SessionManager.SessionInfo session = validateCompanyManager(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED,
                    "Company Manager access required");
        }

        List<StaffUserDTO> staff = UserDAO.listStaffUsers();
        return Response.success(request, staff);
    }

    @SuppressWarnings("unchecked")
    private static Response handleUpdateRole(Request request) {
        SessionManager.SessionInfo session = validateCompanyManager(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED,
                    "Company Manager access required");
        }

        if (!(request.getPayload() instanceof Map)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid payload");
        }

        Map<String, Object> payload = (Map<String, Object>) request.getPayload();
        int targetUserId;
        String newRole;
        try {
            targetUserId = Integer.parseInt(payload.get("userId").toString());
            newRole = payload.get("role").toString();
        } catch (Exception e) {
            return Response.error(request, Response.ERR_VALIDATION, "userId and role required");
        }

        if (targetUserId == session.userId) {
            return Response.error(request, Response.ERR_VALIDATION, "Cannot change your own role");
        }

        boolean success = UserDAO.updateUserRole(targetUserId, newRole);
        if (!success) {
            return Response.error(request, Response.ERR_DATABASE, "Failed to update role");
        }

        // Force logout the affected user so they get fresh permissions
        SessionManager.getInstance().invalidateUserSession(targetUserId);

        List<StaffUserDTO> staff = UserDAO.listStaffUsers();
        return Response.success(request, staff);
    }

    private static Response handleRevokeRole(Request request) {
        SessionManager.SessionInfo session = validateCompanyManager(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED,
                    "Company Manager access required");
        }

        int targetUserId;
        try {
            targetUserId = Integer.parseInt(request.getPayload().toString());
        } catch (Exception e) {
            return Response.error(request, Response.ERR_VALIDATION, "User ID required");
        }

        if (targetUserId == session.userId) {
            return Response.error(request, Response.ERR_VALIDATION, "Cannot revoke your own role");
        }

        boolean success = UserDAO.revokeRole(targetUserId);
        if (!success) {
            return Response.error(request, Response.ERR_DATABASE,
                    "Failed to revoke role (user may already be a customer)");
        }

        SessionManager.getInstance().invalidateUserSession(targetUserId);

        List<StaffUserDTO> staff = UserDAO.listStaffUsers();
        return Response.success(request, staff);
    }

    @SuppressWarnings("unchecked")
    private static Response handleCreateStaffUser(Request request) {
        SessionManager.SessionInfo session = validateCompanyManager(request);
        if (session == null) {
            return Response.error(request, Response.ERR_UNAUTHORIZED,
                    "Company Manager access required");
        }

        if (!(request.getPayload() instanceof Map)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid payload");
        }

        Map<String, Object> payload = (Map<String, Object>) request.getPayload();
        String username = (String) payload.get("username");
        String email = (String) payload.get("email");
        String password = (String) payload.get("password");
        String role = (String) payload.get("role");

        if (username == null || username.trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Username is required");
        }
        if (email == null || !email.contains("@")) {
            return Response.error(request, Response.ERR_VALIDATION, "Valid email is required");
        }
        if (password == null || password.length() < 4) {
            return Response.error(request, Response.ERR_VALIDATION,
                    "Password must be at least 4 characters");
        }
        if (role == null) {
            return Response.error(request, Response.ERR_VALIDATION, "Role is required");
        }

        if (UserDAO.usernameExists(username.trim())) {
            return Response.error(request, Response.ERR_VALIDATION, "Username already exists");
        }
        if (UserDAO.emailExists(email.trim())) {
            return Response.error(request, Response.ERR_VALIDATION, "Email already exists");
        }

        int newId = UserDAO.createStaffUser(username.trim(), email.trim(), password, role);
        if (newId < 0) {
            return Response.error(request, Response.ERR_DATABASE, "Failed to create user");
        }

        List<StaffUserDTO> staff = UserDAO.listStaffUsers();
        return Response.success(request, staff);
    }

    private static SessionManager.SessionInfo validateCompanyManager(Request request) {
        String token = request.getSessionToken();
        if (token == null || token.isEmpty()) return null;

        SessionManager.SessionInfo session = SessionManager.getInstance().validateSession(token);
        if (session == null) return null;

        if (!ROLE_COMPANY_MANAGER.equals(session.role)) return null;

        return session;
    }
}
