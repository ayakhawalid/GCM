package server;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionManager concurrent login prevention.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        // Get fresh instance for each test
        // Note: In real tests, we'd want to reset the singleton
        sessionManager = SessionManager.getInstance();
    }

    /**
     * Test 1: Create session generates valid token
     */
    @Test
    @Order(1)
    @DisplayName("createSession generates valid token")
    void createSession_generatesValidToken() {
        // Use unique user ID to avoid conflicts with other tests
        int userId = 9999;

        // First, make sure user doesn't have existing session
        sessionManager.invalidateUserSession(userId);

        String token = sessionManager.createSession(userId, "testuser", "CUSTOMER");

        assertNotNull(token, "Should generate a token");
        assertFalse(token.isEmpty(), "Token should not be empty");

        // Validate the session
        SessionManager.SessionInfo info = sessionManager.validateSession(token);
        assertNotNull(info);
        assertEquals(userId, info.userId);
        assertEquals("testuser", info.username);

        // Cleanup
        sessionManager.invalidateSession(token);

        System.out.println("✓ Test 1 passed: Session creation works");
    }

    /**
     * Test 2: User should be marked as logged in after session creation
     */
    @Test
    @Order(2)
    @DisplayName("isUserLoggedIn returns true for active session")
    void isUserLoggedIn_withActiveSession_returnsTrue() {
        int userId = 9998;
        sessionManager.invalidateUserSession(userId);

        assertFalse(sessionManager.isUserLoggedIn(userId), "Should not be logged in initially");

        String token = sessionManager.createSession(userId, "testuser2", "CUSTOMER");

        assertTrue(sessionManager.isUserLoggedIn(userId), "Should be logged in after session created");

        // Cleanup
        sessionManager.invalidateSession(token);

        System.out.println("✓ Test 2 passed: isUserLoggedIn works correctly");
    }

    /**
     * Test 3: Second login should be rejected for same user
     */
    @Test
    @Order(3)
    @DisplayName("Concurrent login is rejected")
    void createSession_whenAlreadyLoggedIn_rejectsSecondLogin() {
        int userId = 9997;
        sessionManager.invalidateUserSession(userId);

        // First login
        String token1 = sessionManager.createSession(userId, "testuser3", "CUSTOMER");
        assertNotNull(token1, "First login should succeed");

        // Second login attempt
        String token2 = sessionManager.createSession(userId, "testuser3", "CUSTOMER");
        assertNull(token2, "Second login should be rejected");

        // Cleanup
        sessionManager.invalidateSession(token1);

        System.out.println("✓ Test 3 passed: Concurrent login correctly rejected");
    }

    /**
     * Test 4: Invalidate session removes it
     */
    @Test
    @Order(4)
    @DisplayName("invalidateSession removes session")
    void invalidateSession_removesSession() {
        int userId = 9996;
        sessionManager.invalidateUserSession(userId);

        String token = sessionManager.createSession(userId, "testuser4", "CUSTOMER");
        assertTrue(sessionManager.isUserLoggedIn(userId));

        boolean invalidated = sessionManager.invalidateSession(token);
        assertTrue(invalidated, "Should return true for successful invalidation");

        assertFalse(sessionManager.isUserLoggedIn(userId), "User should not be logged in after logout");
        assertNull(sessionManager.validateSession(token), "Token should be invalid after logout");

        System.out.println("✓ Test 4 passed: Session invalidation works");
    }

    /**
     * Test 5: After logout, user can login again
     */
    @Test
    @Order(5)
    @DisplayName("User can login again after logout")
    void createSession_afterLogout_succeeds() {
        int userId = 9995;
        sessionManager.invalidateUserSession(userId);

        // First login
        String token1 = sessionManager.createSession(userId, "testuser5", "CUSTOMER");
        assertNotNull(token1);

        // Logout
        sessionManager.invalidateSession(token1);

        // Second login should now succeed
        String token2 = sessionManager.createSession(userId, "testuser5", "CUSTOMER");
        assertNotNull(token2, "Should be able to login after logout");

        // Cleanup
        sessionManager.invalidateSession(token2);

        System.out.println("✓ Test 5 passed: Re-login after logout works");
    }
}
