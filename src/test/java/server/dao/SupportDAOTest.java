package server.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for SupportDAO.
 * Verifies FAQ matching and basic DAO structure.
 */
public class SupportDAOTest {

    @BeforeEach
    public void setUp() throws Exception {
        // Setup not required for static methods test or assume DB is ready
    }

    @Test
    @DisplayName("Find Matching FAQ returns entries for strong match")
    public void testFindMatchingFaq_StrongMatch() {
        // "purchase" and "map" appear in seeded FAQ
        List<SupportDAO.FaqEntry> entries = SupportDAO.findMatchingFaq("How do I purchase a map?");
        assertNotNull(entries, "Should return a list of entries");
        assertFalse(entries.isEmpty(), "Should find at least one matching FAQ");
        assertTrue(entries.get(0).answer.contains("Browse Catalog"), "Answer should contain purchase instructions");
    }

    @Test
    @DisplayName("Find Matching FAQ returns empty list for no match")
    public void testFindMatchingFaq_NoMatch() {
        List<SupportDAO.FaqEntry> entries = SupportDAO.findMatchingFaq("Something confusing unrelated xyz");
        assertNotNull(entries, "Should return a list");
        assertTrue(entries.isEmpty(), "Should not find any matching entries");
    }

    @Test
    @DisplayName("SupportDAO class exists and is loadable")
    public void testDAOStructure() {
        // Compile check
        try {
            Class.forName("server.dao.SupportDAO");
        } catch (ClassNotFoundException e) {
            fail("SupportDAO class should exist");
        }
    }
}
