package server.dao;

import common.dto.CityPriceInfo;
import common.dto.EntitlementInfo;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PurchaseDAO.
 * Requires seeded database.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PurchaseDAOTest {

    // Using seeded user 'customer' (id=1)
    private static final int USER_ID = 1;
    // Using cities: Haifa (1), Tel Aviv (2), Jerusalem (3)

    @Test
    @Order(1)
    @DisplayName("Get City Price returns valid info")
    void getCityPrice_returnsInfo() {
        CityPriceInfo info = PurchaseDAO.getCityPrice(1); // Haifa
        assertNotNull(info);
        assertEquals(1, info.getCityId());
        assertTrue(info.getOneTimePrice() > 0);
        assertNotNull(info.getSubscriptionPrices());
        assertEquals(3, info.getSubscriptionPrices().size()); // 1, 3, 6 months (dynamic pricing)
    }

    @Test
    @Order(2)
    @DisplayName("Purchase One-Time succeeds")
    void purchaseOneTime_succeeds() {
        // Purchase Jerusalem (3) - customer doesn't have it yet in seed (seed has
        // expired sub)
        boolean result = PurchaseDAO.purchaseOneTime(USER_ID, 3);
        assertTrue(result, "Purchase should succeed");

        // Check entitlement
        EntitlementInfo entitlement = PurchaseDAO.getEntitlement(USER_ID, 3);
        assertEquals(EntitlementInfo.EntitlementType.ONE_TIME, entitlement.getType());
        assertTrue(entitlement.isCanDownload());
    }

    @Test
    @Order(3)
    @DisplayName("Subscription grants view and download")
    void purchaseSubscription_grantsAccess() {
        // Subscribe to New York (4)
        boolean result = PurchaseDAO.purchaseSubscription(USER_ID, 4, 1);
        assertTrue(result, "Subscription should succeed");

        EntitlementInfo entitlement = PurchaseDAO.getEntitlement(USER_ID, 4);
        assertEquals(EntitlementInfo.EntitlementType.SUBSCRIPTION, entitlement.getType());
        assertTrue(entitlement.isCanView());
        assertTrue(entitlement.isCanDownload());
        assertNotNull(entitlement.getExpiryDate());
    }

    @Test
    @Order(4)
    @DisplayName("Entitlement checks active subscription")
    void getEntitlement_checksActiveSub() {
        // Customer has ACTIVE sub to Tel Aviv (2) from seed
        EntitlementInfo entitlement = PurchaseDAO.getEntitlement(USER_ID, 2);
        assertEquals(EntitlementInfo.EntitlementType.SUBSCRIPTION, entitlement.getType());
        assertTrue(entitlement.isCanView());
    }

    @Test
    @Order(5)
    @DisplayName("Entitlement handles expired subscription")
    void getEntitlement_handlesExpired() {
        // Customer has EXPIRED sub to Jerusalem (3) from seed
        // BUT we purchased one-time in Test 2!
        // So checking entitlement should return ONE_TIME now.
        EntitlementInfo entitlement = PurchaseDAO.getEntitlement(USER_ID, 3);

        // Should be ONE_TIME because sub expired but one-time is forever
        assertEquals(EntitlementInfo.EntitlementType.ONE_TIME, entitlement.getType());
        assertFalse(entitlement.isCanView()); // One-time cannot view
        assertTrue(entitlement.isCanDownload());
    }

    @Test
    @Order(6)
    @DisplayName("Record events works without error")
    void recordEvents_succeeds() {
        assertDoesNotThrow(() -> PurchaseDAO.recordDownload(USER_ID, 1));
        assertDoesNotThrow(() -> PurchaseDAO.recordView(USER_ID, 1, 1));
    }
}
