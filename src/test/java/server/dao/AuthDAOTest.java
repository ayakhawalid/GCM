package server.dao;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UserDAO authentication and registration.
 * 
 * IMPORTANT: These tests require the database to be set up with seed data.
 * Run: mysql -u root -p < dummy_db.sql
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthDAOTest {

    // Test registration username (unique per test run)
    private static final String TEST_USERNAME = "testuser_" + System.currentTimeMillis();
    private static final String TEST_EMAIL = "test_" + System.currentTimeMillis() + "@test.com";

    /**
     * Test 1: Authenticate existing user with correct password
     */
    @Test
    @Order(1)
    @DisplayName("Login with correct password succeeds")
    void authenticate_correctPassword_succeeds() {
        // Using existing test user from seed data
        UserDAO.UserInfo user = UserDAO.authenticate("customer", "1234");

        assertNotNull(user, "Should authenticate successfully");
        assertEquals("customer", user.username);
        assertEquals("CUSTOMER", user.role);
        assertTrue(user.isActive);

        System.out.println("✓ Test 1 passed: Correct password authentication works");
    }

    /**
     * Test 2: Wrong password should fail
     */
    @Test
    @Order(2)
    @DisplayName("Login with wrong password fails")
    void authenticate_wrongPassword_fails() {
        UserDAO.UserInfo user = UserDAO.authenticate("customer", "wrongpassword");

        assertNull(user, "Should not authenticate with wrong password");

        System.out.println("✓ Test 2 passed: Wrong password correctly rejected");
    }

    /**
     * Test 3: Non-existent user should fail
     */
    @Test
    @Order(3)
    @DisplayName("Login with non-existent user fails")
    void authenticate_nonExistentUser_fails() {
        UserDAO.UserInfo user = UserDAO.authenticate("nonexistentuser12345", "anypassword");

        assertNull(user, "Should not authenticate non-existent user");

        System.out.println("✓ Test 3 passed: Non-existent user correctly rejected");
    }

    /**
     * Test 4: Register new customer succeeds
     */
    @Test
    @Order(4)
    @DisplayName("Register new customer succeeds")
    void register_newCustomer_succeeds() {
        int userId = UserDAO.createCustomer(
                TEST_USERNAME,
                TEST_EMAIL,
                "testpass123",
                "0501234567",
                "tok_mock_test",
                "4242");

        assertTrue(userId > 0, "Should create new customer and return positive ID");

        // Verify we can login with new credentials
        UserDAO.UserInfo user = UserDAO.authenticate(TEST_USERNAME, "testpass123");
        assertNotNull(user, "Should be able to login with new credentials");
        assertEquals("CUSTOMER", user.role);

        System.out.println("✓ Test 4 passed: New customer registered successfully (ID: " + userId + ")");
    }

    /**
     * Test 5: Duplicate username should fail
     */
    @Test
    @Order(5)
    @DisplayName("Register duplicate username fails")
    void register_duplicateUsername_fails() {
        // Try to register with existing username
        int userId = UserDAO.createCustomer(
                "customer", // Existing username from seed data
                "unique_" + System.currentTimeMillis() + "@test.com",
                "pass1234",
                null,
                null,
                null);

        assertEquals(-1, userId, "Should fail with duplicate username");

        System.out.println("✓ Test 5 passed: Duplicate username correctly rejected");
    }

    /**
     * Test 6: Duplicate email should fail
     */
    @Test
    @Order(6)
    @DisplayName("Register duplicate email fails")
    void register_duplicateEmail_fails() {
        // Try to register with existing email
        int userId = UserDAO.createCustomer(
                "unique_" + System.currentTimeMillis(),
                "customer@gcm.com", // Existing email from seed data
                "pass1234",
                null,
                null,
                null);

        assertEquals(-1, userId, "Should fail with duplicate email");

        System.out.println("✓ Test 6 passed: Duplicate email correctly rejected");
    }

    /**
     * Test 7: Find user by username
     */
    @Test
    @Order(7)
    @DisplayName("Find user by username works")
    void findByUsername_existingUser_returnsUser() {
        UserDAO.UserInfo user = UserDAO.findByUsername("customer");

        assertNotNull(user);
        assertEquals("customer", user.username);

        System.out.println("✓ Test 7 passed: findByUsername works correctly");
    }

    /**
     * Test 8: Check username exists
     */
    @Test
    @Order(8)
    @DisplayName("usernameExists check works")
    void usernameExists_existingUsername_returnsTrue() {
        assertTrue(UserDAO.usernameExists("customer"));
        assertFalse(UserDAO.usernameExists("nonexistent12345"));

        System.out.println("✓ Test 8 passed: usernameExists works correctly");
    }
}
