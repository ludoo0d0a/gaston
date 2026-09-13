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
    fun powerMatchesAnyLevel_requiresMinimumSelectedPower() {
        // Selection 100+ (level 100): should NOT match 20 kW or 50 kW, SHOULD match 100 kW, 150 kW, 300 kW
        val levels100 = setOf(100)
        assertTrue(!MapPoiFilter.powerMatchesAnyLevel(20.0, levels100), "100+ selection should exclude 20kW")
        assertTrue(!MapPoiFilter.powerMatchesAnyLevel(50.0, levels100), "100+ selection should exclude 50kW")
        assertTrue(MapPoiFilter.powerMatchesAnyLevel(100.0, levels100), "100+ selection should include 100kW")
        assertTrue(MapPoiFilter.powerMatchesAnyLevel(150.0, levels100), "100+ selection should include 150kW")

        // Selection 50+ (level 50): should NOT match 20 kW, SHOULD match 50 kW and above
        val levels50 = setOf(50)
        assertTrue(!MapPoiFilter.powerMatchesAnyLevel(20.0, levels50), "50+ selection should exclude 20kW")
        assertTrue(MapPoiFilter.powerMatchesAnyLevel(50.0, levels50), "50+ selection should include 50kW")
        assertTrue(MapPoiFilter.powerMatchesAnyLevel(120.0, levels50), "50+ selection should include 120kW")

        // Empty selection or setOf(0): matches any power
        assertTrue(MapPoiFilter.powerMatchesAnyLevel(20.0, emptySet()), "Empty levels should match any power")
        assertTrue(MapPoiFilter.powerMatchesAnyLevel(20.0, setOf(0)), "0kW selection should match any power")
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

    @Test
    fun testNewOsmTagCategoriesParsing() {
        // Test Post Box
        assertEquals(PoiCategory.PostBox, PoiCategory.fromOsmTags(mapOf("amenity" to "post_box")))

        // Test Water Body
        assertEquals(PoiCategory.WaterBody, PoiCategory.fromOsmTags(mapOf("natural" to "water")))

        // Test Cafe
        assertEquals(PoiCategory.Cafe, PoiCategory.fromOsmTags(mapOf("amenity" to "cafe")))

        // Test Supermarket
        assertEquals(PoiCategory.Supermarket, PoiCategory.fromOsmTags(mapOf("shop" to "supermarket")))
        assertEquals(PoiCategory.Supermarket, PoiCategory.fromOsmTags(mapOf("shop" to "convenience")))
    }

    @Test
    fun filterCheapest_withLocation_keepsThreeCheapest() {
        val originLat = 48.8566
        val originLon = 2.3522
        val pois = listOf(
            pricedPoi("expensive-near", originLat + 0.01, originLon, 2.00),
            pricedPoi("cheap-far", originLat + 0.20, originLon, 1.40),
            pricedPoi("mid-1", originLat + 0.05, originLon, 1.50),
            pricedPoi("mid-2", originLat + 0.06, originLon, 1.55),
            pricedPoi("cheap-2", originLat + 0.08, originLon, 1.45),
            pricedPoi("unpriced", originLat, originLon, price = null),
        )

        val result = MapPoiFilter.filterCheapest(
            pois = pois,
            selectedFuelIds = setOf("gazole"),
            isLuxembourg = false,
            fromLat = originLat,
            fromLon = originLon,
            limit = MapPoiFilter.CAR_CHEAPEST_COUNT,
        )

        assertEquals(listOf("cheap-far", "cheap-2", "mid-1"), result.map { it.id })
    }

    @Test
    fun filterCheapest_withLocation_breaksPriceTiesByDistance() {
        val originLat = 48.8566
        val originLon = 2.3522
        val pois = listOf(
            pricedPoi("same-far", originLat + 0.20, originLon, 1.50),
            pricedPoi("same-closest", originLat + 0.01, originLon, 1.50),
            pricedPoi("same-mid", originLat + 0.05, originLon, 1.50),
            pricedPoi("same-farther", originLat + 0.10, originLon, 1.50),
        )

        val result = MapPoiFilter.filterCheapest(
            pois = pois,
            selectedFuelIds = setOf("gazole"),
            isLuxembourg = true,
            fromLat = originLat,
            fromLon = originLon,
            limit = MapPoiFilter.CAR_CHEAPEST_COUNT,
        )

        assertEquals(listOf("same-closest", "same-mid", "same-farther"), result.map { it.id })
    }

    @Test
    fun filterCheapest_withoutLocation_keepsTiesAtCutoff() {
        val pois = listOf(
            pricedPoi("p1", 0.0, 0.0, 1.10),
            pricedPoi("p2", 0.0, 0.0, 1.20),
            pricedPoi("p3", 0.0, 0.0, 1.30),
            pricedPoi("p4", 0.0, 0.0, 1.40),
            pricedPoi("p5", 0.0, 0.0, 1.50),
            pricedPoi("p6-tie", 0.0, 0.0, 1.50),
            pricedPoi("p7", 0.0, 0.0, 1.60),
        )

        val result = MapPoiFilter.filterCheapest(
            pois = pois,
            selectedFuelIds = setOf("gazole"),
            isLuxembourg = false,
        )

        assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "p6-tie"), result.map { it.id })
    }

    private fun pricedPoi(id: String, lat: Double, lon: Double, price: Double?): Poi = Poi(
        id = id,
        name = id,
        address = "Address",
        latitude = lat,
        longitude = lon,
        isElectric = false,
        fuelPrices = price?.let { listOf(FuelPrice("Gazole", it)) },
    )
}
