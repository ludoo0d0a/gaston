package fr.geoking.gaston.entitlements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementsTest {

    @Test
    fun testFreePlanLimitsAndFeatures() {
        val entitlements = DefaultEntitlements(Plan.FREE)

        assertEquals(Plan.FREE, entitlements.plan.value)
        assertFalse(entitlements.can(Feature.FavoritesUnlimited))
        assertEquals(3, entitlements.limit(Feature.FavoritesUnlimited))

        assertFalse(entitlements.can(Feature.RouteSearchUnlimited))
        assertEquals(1, entitlements.limit(Feature.RouteSearchUnlimited))

        assertFalse(entitlements.can(Feature.AutoStationList))
        assertEquals(5, entitlements.limit(Feature.AutoStationList))

        assertFalse(entitlements.can(Feature.MultiVehicle))
        assertEquals(1, entitlements.limit(Feature.MultiVehicle))

        assertFalse(entitlements.can(Feature.NoAds))
        assertFalse(entitlements.can(Feature.AutoFullMap))
        assertFalse(entitlements.can(Feature.PriceAlerts))
        assertFalse(entitlements.can(Feature.MaintenanceExport))
    }

    @Test
    fun testPremiumPlanUnlocksAllFeatures() {
        val entitlements = DefaultEntitlements(Plan.FREE)
        entitlements.setPlan(Plan.PREMIUM)

        assertEquals(Plan.PREMIUM, entitlements.plan.value)
        assertTrue(entitlements.can(Feature.FavoritesUnlimited))
        assertEquals(Int.MAX_VALUE, entitlements.limit(Feature.FavoritesUnlimited))

        assertTrue(entitlements.can(Feature.RouteSearchUnlimited))
        assertEquals(Int.MAX_VALUE, entitlements.limit(Feature.RouteSearchUnlimited))

        assertTrue(entitlements.can(Feature.AutoStationList))
        assertEquals(Int.MAX_VALUE, entitlements.limit(Feature.AutoStationList))

        assertTrue(entitlements.can(Feature.MultiVehicle))
        assertEquals(Int.MAX_VALUE, entitlements.limit(Feature.MultiVehicle))

        assertTrue(entitlements.can(Feature.NoAds))
        assertTrue(entitlements.can(Feature.AutoFullMap))
        assertTrue(entitlements.can(Feature.PriceAlerts))
        assertTrue(entitlements.can(Feature.MaintenanceExport))
    }

    @Test
    fun testFakeEntitlements() {
        val fake = FakeEntitlements(Plan.FREE)
        assertEquals(3, fake.limit(Feature.FavoritesUnlimited))

        fake.setPlan(Plan.PREMIUM)
        assertEquals(Int.MAX_VALUE, fake.limit(Feature.FavoritesUnlimited))
        assertTrue(fake.can(Feature.NoAds))
    }
}
