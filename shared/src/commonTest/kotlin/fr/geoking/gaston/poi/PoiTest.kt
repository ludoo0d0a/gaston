package fr.geoking.gaston.poi

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoiTest {

    @Test
    fun sanitizeUserPoiProviderSelection_keepsOnlySelectableProviders() {
        // When all providers are enabled (POI_DATA_SOURCES_DISABLED_FOR_USER_SELECTION is empty)
        val inSet = setOf(PoiProviderType.Etalab, PoiProviderType.GasApi, PoiProviderType.Routex)
        assertEquals(
            setOf(PoiProviderType.Etalab, PoiProviderType.GasApi, PoiProviderType.Routex),
            inSet.sanitizeUserPoiProviderSelection()
        )
    }

    @Test
    fun testMockPoiProvider() = runBlocking {
        val provider = MockPoiProvider()
        val pois = provider.getGasStations(48.8566, 2.3522)

        assertEquals(5, pois.size, "Should return 5 mock gas stations")

        val brands = pois.map { it.brand }.toSet()
        assertTrue(brands.contains("BP"), "Should contain BP")
        assertTrue(brands.contains("Aral"), "Should contain Aral")
        assertTrue(brands.contains("Eni"), "Should contain Eni")
        assertTrue(brands.contains("Circle K"), "Should contain Circle K")
        assertTrue(brands.contains("OMV"), "Should contain OMV")

        val names = pois.map { it.name }
        assertTrue(names.any { it.contains("BP") }, "One name should contain BP")
    }

    @Test
    fun matchesEnergyFilter_correctlyFilters() {
        val gasPoi = Poi(
            "1", "Gas", "Address", 0.0, 0.0, isElectric = false,
            fuelPrices = listOf(FuelPrice("Gazole", 1.5))
        )
        val elecPoi = Poi("2", "Elec", "Address", 0.0, 0.0, isElectric = true)

        assertTrue(MapPoiFilter.matchesEnergyFilter(elecPoi, setOf("electric")), "Should show elec if electric filter active")
        assertTrue(!MapPoiFilter.matchesEnergyFilter(gasPoi, setOf("electric")), "Should NOT show gas if ONLY electric filter active")
        assertTrue(MapPoiFilter.matchesEnergyFilter(gasPoi, setOf("gazole")), "Should show gas if gazole filter active")
        assertTrue(!MapPoiFilter.matchesEnergyFilter(elecPoi, setOf("gazole")), "Should NOT show elec if ONLY gazole filter active")
        assertTrue(MapPoiFilter.matchesEnergyFilter(gasPoi, emptySet()), "Should show gas with empty filters")
        assertTrue(MapPoiFilter.matchesEnergyFilter(elecPoi, emptySet()), "Should show elec with empty filters")
    }

    @Test
    fun matchesEnergyFilter_keepsGasStationWithoutPrices() {
        val gasPoiNoPrices = Poi(
            "1", "No Price Gas", "Address", 0.0, 0.0,
            isElectric = false,
            fuelPrices = null
        )

        // EnergyFilterMode.Fuel should keep the station even without prices
        assertTrue(
            MapPoiFilter.matchesEnergyFilter(gasPoiNoPrices, EnergyFilterMode.Fuel, setOf("gazole")),
            "Should keep gas station without prices when fuel mode is active"
        )

        // Hybrid mode should also keep it
        assertTrue(
            MapPoiFilter.matchesEnergyFilter(gasPoiNoPrices, EnergyFilterMode.Hybrid, setOf("gazole")),
            "Should keep gas station without prices when hybrid mode is active"
        )
    }

    @Test
    fun matchesEnergyFilter_hidesGasStationWithOnlyMismatchedFuel() {
        val gasPoiSp98Only = Poi(
            "1", "SP98 Gas", "Address", 0.0, 0.0,
            isElectric = false,
            fuelPrices = listOf(FuelPrice("SP98", 1.90))
        )

        // If we only want Gazole, hide the SP98 station
        assertTrue(
            !MapPoiFilter.matchesEnergyFilter(gasPoiSp98Only, EnergyFilterMode.Fuel, setOf("gazole")),
            "Should hide gas station with only mismatched fuel prices"
        )

        // If we want SP98, show it
        assertTrue(
            MapPoiFilter.matchesEnergyFilter(gasPoiSp98Only, EnergyFilterMode.Fuel, setOf("sp98")),
            "Should show gas station with matching fuel prices"
        )
    }
}
