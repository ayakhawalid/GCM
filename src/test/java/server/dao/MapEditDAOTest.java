package server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import common.Poi;
import common.dto.MapChanges;
import common.dto.MapSummary;
import common.dto.TourDTO;
import common.dto.TourStopDTO;
import common.dto.ValidationResult;
import server.DBConnector;

/**
 * Tests for Map Editing DAOs.
 * 
 * IMPORTANT: These tests require the database to be set up with seed data.
 * Run: mysql -u root -p < dummy_db.sql
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MapEditDAOTest {

    private static int testCityId;
    private static int testMapId;
    private static int testPoiId;
    private static int testTourId;

    /**
     * Test 1: Create city + first map
     */
    @Test
    @Order(1)
    @DisplayName("Create city with first map - success")
    void createCityWithFirstMap_success() throws SQLException {
        // Create city
        testCityId = CityDAO.createCity("Test City " + System.currentTimeMillis(),
                "Test description", 25.0);
        assertTrue(testCityId > 0, "City should be created with valid ID");

        // Create first map
        testMapId = MapDAO.createMap(testCityId, "Test Map", "First map for test city");
        assertTrue(testMapId > 0, "Map should be created with valid ID");

        // Verify city has map
        List<MapSummary> maps = MapDAO.getMapsForCity(testCityId, 0);
        assertEquals(1, maps.size(), "City should have exactly one map");
        assertEquals("Test Map", maps.get(0).getName());

        System.out.println("✓ Test 1 passed: Created city " + testCityId + " with map " + testMapId);
    }

    /**
     * Test 2: Add POI then link to tour stop
     */
    @Test
    @Order(2)
    @DisplayName("Add POI and link to tour - success")
    void addPoiAndLinkToTour_success() throws SQLException {
        // Create POI
        Poi poi = new Poi(0, testCityId, "Test POI", "123,456", "Museum", "A test POI", true);
        testPoiId = PoiDAO.createPoi(poi);
        assertTrue(testPoiId > 0, "POI should be created with valid ID");

        // Link POI to map
        try (Connection conn = DBConnector.getConnection()) {
            boolean linked = PoiDAO.linkPoiToMap(conn, testMapId, testPoiId, 1);
            assertTrue(linked, "POI should be linked to map");
        }

        // Verify POI is on map
        List<Poi> pois = PoiDAO.getPoisForMap(testMapId);
        assertTrue(pois.stream().anyMatch(p -> p.getId() == testPoiId),
                "POI should appear in map's POI list");

        // Create tour
        TourDTO tour = new TourDTO(0, testCityId, "Test Tour", "A test tour");
        testTourId = TourDAO.createTour(tour);
        assertTrue(testTourId > 0, "Tour should be created with valid ID");

        // Add POI as tour stop
        TourStopDTO stop = new TourStopDTO();
        stop.setTourId(testTourId);
        stop.setPoiId(testPoiId);
        stop.setStopOrder(1);
        stop.setNotes("First stop");

        try (Connection conn = DBConnector.getConnection()) {
            int stopId = TourDAO.addTourStop(conn, stop);
            assertTrue(stopId > 0, "Tour stop should be created");
        }

        // Verify tour has stop
        TourDTO loadedTour = TourDAO.getTourById(testTourId);
        assertNotNull(loadedTour, "Tour should be loaded");
        assertEquals(1, loadedTour.getStops().size(), "Tour should have one stop");

        System.out.println("✓ Test 2 passed: Created POI " + testPoiId +
                " and linked to tour " + testTourId);
    }

    /**
     * Test 3: Delete POI that is in a tour (must block)
     */
    @Test
    @Order(3)
    @DisplayName("Delete POI in tour - should be blocked")
    void deletePoiInTour_blocked() {
        // Check if POI is used in tour
        boolean isUsed = PoiDAO.isPoiUsedInTour(testPoiId);
        assertTrue(isUsed, "POI should be detected as used in tour");

        // Try to delete - should throw exception
        try (Connection conn = DBConnector.getConnection()) {
            assertThrows(SQLException.class, () -> {
                PoiDAO.deletePoi(conn, testPoiId);
            }, "Deleting POI in tour should throw SQLException");
        } catch (SQLException e) {
            fail("Should not fail opening connection");
        }

        // Verify POI still exists
        Poi poi = PoiDAO.getPoiById(testPoiId);
        assertNotNull(poi, "POI should still exist after failed delete");

        System.out.println("✓ Test 3 passed: POI delete was correctly blocked");
    }

    /**
     * Test 4: Invalid submission returns validation error
     */
    @Test
    @Order(4)
    @DisplayName("Submit invalid changes - returns validation errors")
    void submitInvalidChanges_returnsErrors() {
        // Create changes with invalid data
        MapChanges changes = new MapChanges();
        changes.setMapId(testMapId);

        // Add POI with empty name (invalid)
        Poi invalidPoi = new Poi(0, testCityId, "", "", "", "", false);
        changes.getAddedPois().add(invalidPoi);

        // Add tour with empty name (invalid)
        TourDTO invalidTour = new TourDTO(0, testCityId, "", "");
        changes.getAddedTours().add(invalidTour);

        // Try to delete POI that's in tour (should be caught)
        changes.getDeletedPoiIds().add(testPoiId);

        // Validate (this would normally be done in handler)
        ValidationResult result = validateChanges(changes);

        assertFalse(result.isValid(), "Validation should fail");
        assertTrue(result.getErrors().size() >= 1, "Should have at least one error");

        System.out.println("✓ Test 4 passed: Invalid changes returned " +
                result.getErrors().size() + " validation errors");
    }

    /**
     * Diagnostic: Draft map must stay draft after getMapsForCity (ensureTourMapsForCity must not reuse or approve it).
     * Creates a draft map (approved=0, created_by=2), then a tour with same name so ensureTourMapsForCity runs;
     * verifies the list still returns the user's draft with isDraft()=true.
     */
    @Test
    @Order(5)
    @DisplayName("Draft map stays draft when tour has same name")
    void draftMapStaysDraftWhenTourHasSameName() throws SQLException {
        int cityId = CityDAO.createCity("DraftTestCity " + System.currentTimeMillis(), "desc", 10.0);
        assertTrue(cityId > 0, "Need city for test");

        int draftMapId;
        try (Connection conn = DBConnector.getConnection()) {
            assertNotNull(conn, "Need DB connection");
            draftMapId = MapDAO.createMap(conn, cityId, "SameNameMap", "draft map", 2, false);
        }
        assertTrue(draftMapId > 0, "Draft map should be created");

        // Diagnose: does the maps table have approved/created_by? (Required for draft vs published.)
        boolean schemaHasApprovalColumns = false;
        try (Connection c = DBConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT approved, created_by FROM maps WHERE id = ?")) {
            ps.setInt(1, draftMapId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Map row should exist");
            schemaHasApprovalColumns = true;
            int approved = rs.getInt("approved");
            int createdBy = rs.getInt("created_by");
            System.out.println("MapEditDAOTest: map " + draftMapId + " in DB: approved=" + approved + ", created_by=" + createdBy);
            if (approved != 0)
                System.out.println("MapEditDAOTest: BUG – draft map was stored with approved=1 (expected 0)");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unknown column 'approved'")) {
                System.out.println("MapEditDAOTest: DIAGNOSIS – maps table has no 'approved' column. Run migration_city_map_approval.sql (or use dummy_db.sql) for draft/publish to work. Skipping draft assertions.");
                MapDAO.deleteMap(draftMapId);
                return;
            }
            throw e;
        }

        List<MapSummary> maps1 = MapDAO.getMapsForCity(cityId, 2);
        MapSummary draftInList = maps1.stream().filter(m -> m.getId() == draftMapId).findFirst().orElse(null);
        assertNotNull(draftInList, "Draft map should appear for creator (user 2)");
        assertTrue(draftInList.isDraft(), "Draft map must be returned with isDraft=true");

        TourDTO tour = new TourDTO(0, cityId, "SameNameMap", "tour");
        int tourId = TourDAO.createTour(tour);
        assertTrue(tourId > 0, "Tour should be created");
        tour.setId(tourId);

        List<MapSummary> maps2 = MapDAO.getMapsForCity(cityId, 2);
        MapSummary forSameName = maps2.stream().filter(m -> "SameNameMap".equals(m.getName() != null ? m.getName().trim() : null)).findFirst().orElse(null);
        assertNotNull(forSameName, "One map with name SameNameMap should appear (deduped)");
        assertTrue(forSameName.isDraft(), "Draft must still be shown (prefer user draft over tour map); got isDraft=" + forSameName.isDraft());
        assertEquals(draftMapId, forSameName.getId(), "The visible map must be the user's draft, not the tour map");

        try (Connection conn = DBConnector.getConnection()) {
            TourDAO.deleteTour(conn, tourId);
        }
        MapDAO.deleteMap(draftMapId);
        CityDAO.deleteCity(cityId);
        System.out.println("✓ Draft-stays-draft diagnostic passed");
    }

    /**
     * Cleanup: Delete test data
     */
    @Test
    @Order(6)
    @DisplayName("Cleanup test data")
    void cleanup() throws SQLException {
        // First delete tour (which frees the POI)
        try (Connection conn = DBConnector.getConnection()) {
            TourDAO.deleteTour(conn, testTourId);
        }

        // Now we can delete POI
        try (Connection conn = DBConnector.getConnection()) {
            PoiDAO.deletePoi(conn, testPoiId);
        }

        // Delete map then city (tests create their own city; remove it so it doesn't appear in app)
        MapDAO.deleteMap(testMapId);
        CityDAO.deleteCity(testCityId);

        System.out.println("✓ Cleanup complete");
    }

    /**
     * Helper: Validate changes (simulates validation logic from handler)
     */
    private ValidationResult validateChanges(MapChanges changes) {
        ValidationResult result = new ValidationResult();

        for (int i = 0; i < changes.getAddedPois().size(); i++) {
            Poi poi = changes.getAddedPois().get(i);
            if (poi.getName() == null || poi.getName().trim().isEmpty()) {
                result.addError("addedPoi[" + i + "].name", "POI name is required");
            }
        }

        for (int i = 0; i < changes.getAddedTours().size(); i++) {
            TourDTO tour = changes.getAddedTours().get(i);
            if (tour.getName() == null || tour.getName().trim().isEmpty()) {
                result.addError("addedTour[" + i + "].name", "Tour name is required");
            }
        }

        for (int poiId : changes.getDeletedPoiIds()) {
            if (PoiDAO.isPoiUsedInTour(poiId)) {
                result.addError("deletedPoi[" + poiId + "]", "Cannot delete POI - used in tour");
            }
        }

        return result;
    }
}
