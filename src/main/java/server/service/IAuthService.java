package server.service;

/**
 * Authentication service interface.
 * Phase 15: Maintainability - Strict separation with interfaces.
 * 
 * Designed to be mappable to REST endpoints for future web migration.
 */
public interface IAuthService {

    /**
     * Authenticate user and create session.
     * REST mapping: POST /api/auth/login
     * 
     * @param username Username
     * @param password Password (will be verified against hash)
     * @return Session token on success, null on failure
     */
    String login(String username, String password);

    /**
     * Invalidate session and logout user.
     * REST mapping: POST /api/auth/logout
     * 
     * @param sessionToken Session token to invalidate
     * @return true if logout successful
     */
    boolean logout(String sessionToken);

    /**
     * Register new customer.
     * REST mapping: POST /api/auth/register
     * 
     * @param username Username
     * @param password Password
     * @param email    Email address
     * @param phone    Phone number (optional)
     * @return User ID on success, -1 on failure
     */
    int registerCustomer(String username, String password, String email, String phone);

    /**
     * Validate session token.
     * REST mapping: GET /api/auth/validate
     * 
     * @param sessionToken Session token
     * @return User ID if valid, null if invalid
     */
    Integer validateSession(String sessionToken);

    /**
     * Check if user is already logged in.
     * 
     * @param userId User ID
     * @return true if user has active session
     */
    boolean isUserLoggedIn(int userId);
}
