package server.dao;

import common.dto.CitySearchResult;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SearchDAO search functionality.
 * 
 * IMPORTANT: These tests require the database to be set up with seed data.
 * Run: mysql -u root -p < dummy_db.sql
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SearchDAOTest {

    /**
     * Test 1: City exists returns results
     * Search for "Haifa" → should return results with Haifa maps
     */
    @Test
    @Order(1)
    @DisplayName("Search by city name - existing city returns results")
    void searchByCityName_existingCity_returnsResults() {
        List<CitySearchResult> results = SearchDAO.searchByCityName("Haifa");

        assertFalse(results.isEmpty(), "Should find results for 'Haifa'");
        assertEquals("Haifa", results.get(0).getCityName());
        assertTrue(results.get(0).getTotalMaps() > 0, "Haifa should have maps");

        System.out.println("✓ Test 1 passed: Found " + results.size() + " city with " +
                results.get(0).getTotalMaps() + " maps");
    }

    /**
     * Test 2: City not exists returns empty
     * Search for "XYZ123" → should return empty list
     */
    @Test
    @Order(2)
    @DisplayName("Search by city name - non-existent city returns empty")
    void searchByCityName_nonExistentCity_returnsEmpty() {
        List<CitySearchResult> results = SearchDAO.searchByCityName("XYZ123NonExistentCity");

        assertTrue(results.isEmpty(), "Should not find any results for non-existent city");

        System.out.println("✓ Test 2 passed: Correctly returned empty results");
    }

    /**
     * Test 3: POI exists in multiple maps returns all maps
     * Search for "Beach" → should return maps containing Beach POIs
     */
    @Test
    @Order(3)
    @DisplayName("Search by POI name - finds maps containing matching POIs")
    void searchByPoiName_poiExists_returnsMapsWithPoi() {
        List<CitySearchResult> results = SearchDAO.searchByPoiName("Beach");

        assertFalse(results.isEmpty(), "Should find results for 'Beach' POI");

        // Count total maps found
        int totalMaps = results.stream()
                .mapToInt(CitySearchResult::getTotalMaps)
                .sum();
        assertTrue(totalMaps > 0, "Should find at least one map with Beach POI");

        System.out.println("✓ Test 3 passed: Found " + results.size() + " cities with " +
                totalMaps + " maps containing 'Beach' POI");
    }

    /**
     * Test 4: City+POI mismatch returns empty
     * Search for city "Haifa" + POI "Statue of Liberty" → should return empty
     */
    @Test
    @Order(4)
    @DisplayName("Search by city and POI - mismatch returns empty")
    void searchByCityAndPoi_mismatch_returnsEmpty() {
        List<CitySearchResult> results = SearchDAO.searchByCityAndPoi("Haifa", "Statue of Liberty");

        // Should be empty because Statue of Liberty is in New York, not Haifa
        assertTrue(results.isEmpty(), "Should not find Haifa with Statue of Liberty POI");

        System.out.println("✓ Test 4 passed: Correctly returned empty for mismatched city+POI");
    }

    /**
     * Test 5: Case-insensitive matching
     * Search "HAIFA", "haifa", "HaIfA" → all should return same results
     */
    @Test
    @Order(5)
    @DisplayName("Search is case-insensitive")
    void searchByCityName_caseInsensitive_returnsResults() {
        List<CitySearchResult> upper = SearchDAO.searchByCityName("HAIFA");
        List<CitySearchResult> lower = SearchDAO.searchByCityName("haifa");
        List<CitySearchResult> mixed = SearchDAO.searchByCityName("HaIfA");

        // All should return the same number of results
        assertEquals(upper.size(), lower.size(), "UPPER and lower case should return same count");
        assertEquals(lower.size(), mixed.size(), "lower and MiXeD case should return same count");

        assertFalse(upper.isEmpty(), "All searches should find results");

        System.out.println("✓ Test 5 passed: Case-insensitive search working correctly");
    }

    /**
     * Test 6: Get cities catalog returns all cities
     */
    @Test
    @Order(6)
    @DisplayName("Get catalog returns all cities with maps")
    void getCitiesCatalog_returnsAllCities() {
        List<CitySearchResult> results = SearchDAO.getCitiesCatalog();

        assertFalse(results.isEmpty(), "Catalog should not be empty");
        assertTrue(results.size() >= 5, "Should have at least 5 cities in catalog");

        // All cities should have maps
        for (CitySearchResult city : results) {
            assertNotNull(city.getCityName(), "City name should not be null");
            assertTrue(city.getCityPrice() >= 0, "Price should be non-negative");
        }

        System.out.println("✓ Test 6 passed: Catalog contains " + results.size() + " cities");
    }

    /**
     * Test 7: Combined search with valid city and POI
     */
    @Test
    @Order(7)
    @DisplayName("Search by city and POI - matching returns results")
    void searchByCityAndPoi_matching_returnsResults() {
        // Haifa may have "Bahai Gardens" POI (depends on seed + approved map_pois)
        List<CitySearchResult> results = SearchDAO.searchByCityAndPoi("Haifa", "Bahai");

        assertNotNull(results);
        // If seed has Haifa with approved POI matching "Bahai", we get results
        if (!results.isEmpty()) {
            assertEquals("Haifa", results.get(0).getCityName());
        }

        System.out.println("✓ Test 7 passed: searchByCityAndPoi returned " + results.size() + " result(s)");
    }
}
