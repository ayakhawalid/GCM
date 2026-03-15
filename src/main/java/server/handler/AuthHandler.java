package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.LoginRequest;
import common.dto.LoginResponse;
import common.dto.RegisterRequest;
import server.SessionManager;
import server.dao.UserDAO;

/**
 * Handles authentication messages: REGISTER_CUSTOMER, LOGIN, LOGOUT.
 */
public class AuthHandler {

    /**
     * Check if this handler can process the given message type.
     */
    public static boolean canHandle(MessageType type) {
        return type == MessageType.REGISTER_CUSTOMER ||
                type == MessageType.LOGIN ||
                type == MessageType.LOGOUT;
    }

    /**
     * Handle the authentication request.
     */
    public static Response handle(Request request) {
        switch (request.getType()) {
            case REGISTER_CUSTOMER:
                return handleRegister(request);
            case LOGIN:
                return handleLogin(request);
            case LOGOUT:
                return handleLogout(request);
            default:
                return Response.error(request, Response.ERR_INTERNAL, "Unknown auth message type");
        }
    }

    /**
     * Handle customer registration.
     * Expected payload: RegisterRequest
     */
    private static Response handleRegister(Request request) {
        System.out.println("═══ REGISTER_CUSTOMER ═══");

        if (!(request.getPayload() instanceof RegisterRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid registration data");
        }

        RegisterRequest reg = (RegisterRequest) request.getPayload();

        // Validate required fields
        if (reg.getUsername() == null || reg.getUsername().trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Username is required");
        }
        if (reg.getEmail() == null || reg.getEmail().trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Email is required");
        }
        if (reg.getPassword() == null || reg.getPassword().length() < 4) {
            return Response.error(request, Response.ERR_VALIDATION, "Password must be at least 4 characters");
        }

        // Check for duplicate username
        if (UserDAO.usernameExists(reg.getUsername())) {
            return Response.error(request, Response.ERR_VALIDATION, "Username already exists");
        }

        // Check for duplicate email
        if (UserDAO.emailExists(reg.getEmail())) {
            return Response.error(request, Response.ERR_VALIDATION, "Email already exists");
        }

        // Create customer
        int userId = UserDAO.createCustomer(
                reg.getUsername(),
                reg.getEmail(),
                reg.getPassword(),
                reg.getPhone(),
                reg.getPaymentToken() != null ? reg.getPaymentToken() : "tok_mock_" + System.currentTimeMillis(),
                reg.getCardLast4() != null ? reg.getCardLast4() : "0000");

        if (userId < 0) {
            return Response.error(request, Response.ERR_DATABASE, "Failed to create customer account");
        }

        System.out.println("✓ Customer registered successfully: " + reg.getUsername());
        return Response.success(request, "Registration successful! Please login.");
    }

    /**
     * Handle user login.
     * Expected payload: LoginRequest
     */
    private static Response handleLogin(Request request) {
        System.out.println("═══ LOGIN ═══");

        if (!(request.getPayload() instanceof LoginRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid login data");
        }

        LoginRequest login = (LoginRequest) request.getPayload();

        // Authenticate user
        UserDAO.UserInfo user = UserDAO.authenticate(login.getUsername(), login.getPassword());

        if (user == null) {
            System.out.println("✗ Login failed for: " + login.getUsername());
            return Response.error(request, Response.ERR_UNAUTHORIZED, "Invalid username or password");
        }

        // Single session per user: if already logged in, invalidate old session so re-login works (e.g. after logout)
        SessionManager sessions = SessionManager.getInstance();
        if (sessions.isUserLoggedIn(user.id)) {
            System.out.println("⚠ User already had session - invalidating old session for re-login: " + login.getUsername());
            sessions.invalidateUserSession(user.id);
        }

        String token = sessions.createSession(user.id, user.username, user.role);

        if (token == null) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to create session");
        }

        // Update last login time
        UserDAO.updateLastLogin(user.id);

        // Build response
        LoginResponse response = new LoginResponse(
                token,
                user.id,
                user.username,
                user.role,
                false // isSubscribed - will be implemented in purchase phase
        );

        System.out.println("✓ Login successful: " + user.username + " (role: " + user.role + ")");
        return Response.success(request, response);
    }

    /**
     * Handle user logout.
     * Token can be in payload (String) or in request session token.
     */
    private static Response handleLogout(Request request) {
        System.out.println("═══ LOGOUT ═══");

        String token = null;
        if (request.getPayload() instanceof String) {
            token = (String) request.getPayload();
        }
        if ((token == null || token.isEmpty()) && request.getSessionToken() != null) {
            token = request.getSessionToken();
        }

        if (token == null || token.isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Session token required");
        }

        SessionManager sessions = SessionManager.getInstance();
        boolean invalidated = sessions.invalidateSession(token);

        if (invalidated) {
            System.out.println("✓ Logout successful");
            return Response.success(request, "Logged out successfully");
        } else {
            return Response.error(request, Response.ERR_NOT_FOUND, "Session not found");
        }
    }
}
