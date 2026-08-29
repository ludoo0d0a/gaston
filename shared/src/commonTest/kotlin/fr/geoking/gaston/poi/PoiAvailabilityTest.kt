package fr.geoking.gaston.poi

import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoiAvailabilityTest {

    @Test
    fun isChargingStation_trueForIrveCategoryWithoutExplicitElectricFlag() {
        val poi = Poi(
            id = "osm:1",
            name = "Charger",
            address = "Street",
            latitude = 0.0,
            longitude = 0.0,
            poiCategory = PoiCategory.Irve,
        )
        assertTrue(poi.isChargingStation)
    }

    @Test
    fun embeddedAvailabilitySummary_readsIrveDetails() {
        val poi = Poi(
            id = "chargy-1",
            name = "Chargy",
            address = "Street",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            irveDetails = IrveDetails(
                availableConnectors = 2,
                totalConnectors = 4,
            ),
        )
        val summary = poi.embeddedAvailabilitySummary()
        assertNotNull(summary)
        assertEquals(2, summary.availableCount)
        assertEquals(4, summary.totalCount)
    }

    @Test
    fun resolveAvailabilitySummary_prefersLiveFeedOverEmbedded() {
        val poi = Poi(
            id = "station-1",
            name = "Station",
            address = "Street",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            irveDetails = IrveDetails(
                availableConnectors = 1,
                totalConnectors = 2,
            ),
        )
        val live = StationAvailabilitySummary(availableCount = 3, totalCount = 6)
        val resolved = poi.resolveAvailabilitySummary(live)
        assertEquals(live, resolved)
    }

    @Test
    fun resolveAvailabilitySummary_fallsBackToEmbeddedWhenLiveMissing() {
        val poi = Poi(
            id = "station-1",
            name = "Station",
            address = "Street",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            irveDetails = IrveDetails(
                availableConnectors = 1,
                totalConnectors = 2,
            ),
        )
        val resolved = poi.resolveAvailabilitySummary(null)
        assertNotNull(resolved)
        assertEquals(1, resolved.availableCount)
        assertEquals(2, resolved.totalCount)
    }

    @Test
    fun embeddedAvailabilitySummary_nullWhenIncomplete() {
        val poi = Poi(
            id = "station-1",
            name = "Station",
            address = "Street",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            irveDetails = IrveDetails(totalConnectors = 2),
        )
        assertNull(poi.embeddedAvailabilitySummary())
    }

    @Test
    fun isChargingStation_falseForGasStation() {
        val poi = Poi(
            id = "gas-1",
            name = "Gas",
            address = "Street",
            latitude = 0.0,
            longitude = 0.0,
            poiCategory = PoiCategory.Gas,
        )
        assertFalse(poi.isChargingStation)
    }
}
