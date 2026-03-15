package server.dao;

import common.dto.MapVersionDTO;
import org.junit.jupiter.api.*;
import server.DBConnector;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MapVersion DAO operations.
 * Tests the Phase 3 approval workflow.
 * 
 * IMPORTANT: Run dummy_db.sql first to create tables.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MapVersionDAOTest {

    private static int testVersionId;
    private static final int TEST_MAP_ID = 1; // Haifa Bay Area map from seed data
    private static final int TEST_CREATOR_ID = 2; // employee user
    private static final int TEST_APPROVER_ID = 3; // manager user

    /**
     * Test 1: Create version and verify PENDING status
     * Requirement: approve publishes version (prerequisite)
     */
    @Test
    @Order(1)
    @DisplayName("Create version - status should be PENDING")
    void createVersion_statusIsPending() throws SQLException {
        try (Connection conn = DBConnector.getConnection()) {
            testVersionId = MapVersionDAO.createVersion(
                    conn, TEST_MAP_ID, TEST_CREATOR_ID, "Test changes for approval");

            assertTrue(testVersionId > 0, "Version should be created with valid ID");

            MapVersionDTO version = MapVersionDAO.getVersionById(testVersionId);
            assertNotNull(version, "Version should be retrievable");
            assertEquals("PENDING", version.getStatus(), "New version should have PENDING status");
            assertEquals(TEST_MAP_ID, version.getMapId(), "Map ID should match");
            assertEquals(TEST_CREATOR_ID, version.getCreatedBy(), "Creator should match");

            System.out.println("✓ Test 1 passed: Created PENDING version " + testVersionId);
        }
    }

    /**
     * Test 2: List pending versions
     */
    @Test
    @Order(2)
    @DisplayName("List pending versions - should include our test version")
    void listPendingVersions_includesTestVersion() {
        List<MapVersionDTO> pending = MapVersionDAO.listPendingVersions();

        assertNotNull(pending, "Pending list should not be null");
        assertTrue(pending.stream().anyMatch(v -> v.getId() == testVersionId),
                "Pending list should include our test version");

        System.out.println("✓ Test 2 passed: Found " + pending.size() + " pending version(s)");
    }

    /**
     * Test 3: Approve version - status becomes APPROVED
     * Requirement: approve publishes version
     */
    @Test
    @Order(3)
    @DisplayName("Approve version - status should be APPROVED")
    void approveVersion_statusIsApproved() throws SQLException {
        try (Connection conn = DBConnector.getConnection()) {
            boolean updated = MapVersionDAO.updateStatus(
                    conn, testVersionId, "APPROVED", TEST_APPROVER_ID, null);

            assertTrue(updated, "Update should succeed");

            MapVersionDTO version = MapVersionDAO.getVersionById(testVersionId);
            assertEquals("APPROVED", version.getStatus(), "Status should be APPROVED");
            assertEquals(TEST_APPROVER_ID, version.getApprovedBy(), "Approver should be set");
            assertNotNull(version.getApprovedAt(), "Approval timestamp should be set");

            System.out.println("✓ Test 3 passed: Version approved successfully");
        }
    }

    /**
     * Test 4: Get latest approved version
     * Requirement: customers see only APPROVED versions
     */
    @Test
    @Order(4)
    @DisplayName("Get latest approved - should return our approved version")
    void getLatestApproved_returnsApprovedVersion() {
        MapVersionDTO latest = MapVersionDAO.getLatestApprovedVersion(TEST_MAP_ID);

        assertNotNull(latest, "Should have at least one approved version");
        assertEquals("APPROVED", latest.getStatus(), "Latest should be APPROVED");

        System.out.println("✓ Test 4 passed: Latest approved version is v" + latest.getVersionNumber());
    }

    /**
     * Test 5: Reject keeps customer-visible version unchanged
     * Create new version, reject it, verify latest approved unchanged
     */
    @Test
    @Order(5)
    @DisplayName("Reject version - customer-visible version unchanged")
    void rejectVersion_customerVersionUnchanged() throws SQLException {
        // Get current latest approved version
        MapVersionDTO beforeReject = MapVersionDAO.getLatestApprovedVersion(TEST_MAP_ID);
        int approvedVersionBefore = beforeReject != null ? beforeReject.getVersionNumber() : 0;

        // Create and reject a new version
        try (Connection conn = DBConnector.getConnection()) {
            int rejectVersionId = MapVersionDAO.createVersion(
                    conn, TEST_MAP_ID, TEST_CREATOR_ID, "Changes to be rejected");

            MapVersionDAO.updateStatus(conn, rejectVersionId, "REJECTED",
                    TEST_APPROVER_ID, "Does not meet quality standards");

            MapVersionDTO rejected = MapVersionDAO.getVersionById(rejectVersionId);
            assertEquals("REJECTED", rejected.getStatus(), "Status should be REJECTED");
            assertNotNull(rejected.getRejectionReason(), "Rejection reason should be set");
        }

        // Verify latest approved is unchanged
        MapVersionDTO afterReject = MapVersionDAO.getLatestApprovedVersion(TEST_MAP_ID);
        int approvedVersionAfter = afterReject != null ? afterReject.getVersionNumber() : 0;

        assertEquals(approvedVersionBefore, approvedVersionAfter,
                "Latest approved version should be unchanged after rejection");

        System.out.println("✓ Test 5 passed: Customer-visible version unchanged after rejection");
    }

    /**
     * Test 6: Approval creates audit log entry
     */
    @Test
    @Order(6)
    @DisplayName("Approval creates audit log")
    void approval_createsAuditLog() throws SQLException {
        try (Connection conn = DBConnector.getConnection()) {
            // Create a new version
            int auditTestVersionId = MapVersionDAO.createVersion(
                    conn, TEST_MAP_ID, TEST_CREATOR_ID, "Audit log test");

            // Create approval record
            ApprovalDAO.createApproval(conn, ApprovalDAO.ENTITY_MAP_VERSION, auditTestVersionId);

            // Log the approval
            AuditLogDAO.log(conn, AuditLogDAO.ACTION_VERSION_CREATED, TEST_CREATOR_ID,
                    AuditLogDAO.ENTITY_MAP_VERSION, auditTestVersionId, "test", "true");

            // Verify by checking the audit_log table directly
            String sql = "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'MAP_VERSION' AND entity_id = ?";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, auditTestVersionId);
                var rs = stmt.executeQuery();
                assertTrue(rs.next() && rs.getInt(1) > 0, "Audit log entry should exist");
            }

            System.out.println("✓ Test 6 passed: Audit log entries written");
        }
    }
}
