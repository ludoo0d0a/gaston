package fr.geoking.gaston.ui.map

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoiMarkerHelperTest {

    @Test
    fun `getPoiLabel returns price for Gas station even when no fuel filters are selected`() {
        val poi = Poi(
            id = "1",
            name = "Station 1",
            address = "Address 1",
            latitude = 0.0,
            longitude = 0.0,
            fuelPrices = listOf(FuelPrice("Gazole", 1.80), FuelPrice("SP98", 1.95))
        )
        val label = PoiMarkerHelper.getPoiLabel(poi, emptySet(), emptySet())
        assertEquals("€1.800", label)
    }

    @Test
    fun `getPoiLabel returns price for Gas station when fuel filter matches`() {
        val poi = Poi(
            id = "1",
            name = "Station 1",
            address = "Address 1",
            latitude = 0.0,
            longitude = 0.0,
            fuelPrices = listOf(FuelPrice("sp95", 1.50))
        )
        val label = PoiMarkerHelper.getPoiLabel(poi, setOf("sp95"), emptySet())
        assertEquals("€1.500", label)
    }

    @Test
    fun `getPoiLabel returns the lowest price among matching fuel filters`() {
        val poi = Poi(
            id = "1",
            name = "Station 1",
            address = "Address 1",
            latitude = 0.0,
            longitude = 0.0,
            fuelPrices = listOf(FuelPrice("Gazole", 1.80), FuelPrice("sp95", 1.70))
        )
        val label = PoiMarkerHelper.getPoiLabel(poi, setOf("gazole", "sp95"), emptySet())
        assertEquals("€1.700", label)
    }

    @Test
    fun `getPoiLabel ignores out of stock fuel prices`() {
        val poi = Poi(
            id = "1",
            name = "Station 1",
            address = "Address 1",
            latitude = 0.0,
            longitude = 0.0,
            fuelPrices = listOf(FuelPrice("Gazole", 1.60, outOfStock = true), FuelPrice("sp95", 1.70))
        )
        val label = PoiMarkerHelper.getPoiLabel(poi, setOf("gazole", "sp95"), emptySet())
        assertEquals("€1.700", label)
    }

    @Test
    fun `getPoiLabel returns null for IRVE station when no electric filters are selected`() {
        val poi = Poi(
            id = "2",
            name = "Charger 1",
            address = "Address 2",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            powerKw = 50.0
        )
        val label = PoiMarkerHelper.getPoiLabel(poi, emptySet(), emptySet())
        assertNull("Label should be null when no electric filter is selected", label)
    }

    @Test
    fun `getPoiLabel returns power for IRVE station when electric filter matches`() {
        val poi = Poi(
            id = "2",
            name = "Charger 1",
            address = "Address 2",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            powerKw = 50.0
        )
        val label = PoiMarkerHelper.getPoiLabel(poi, setOf("electric"), emptySet())
        assertEquals("50kW", label)
    }

    @Test
    fun `getPoiLabel returns power for IRVE station when power filter matches`() {
        val poi = Poi(
            id = "2",
            name = "Charger 1",
            address = "Address 2",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            powerKw = 50.0
        )
        val label = PoiMarkerHelper.getPoiLabel(poi, emptySet(), setOf(50))
        assertEquals("50kW", label)
    }

    @Test
    fun `getPoiLabel for hybrid station follows filter priorities`() {
        val hybridPoi = Poi(
            id = "3",
            name = "Hybrid 1",
            address = "Address 3",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            powerKw = 50.0,
            fuelPrices = listOf(FuelPrice("sp95", 1.50))
        )

        // No filters -> fuel price (Priority 1) - UPDATED BEHAVIOR
        assertEquals("€1.500", PoiMarkerHelper.getPoiLabel(hybridPoi, emptySet(), emptySet()))

        // Only fuel filter -> fuel price
        assertEquals("€1.500", PoiMarkerHelper.getPoiLabel(hybridPoi, setOf("sp95"), emptySet()))

        // Only electric filter -> power
        assertEquals("50kW", PoiMarkerHelper.getPoiLabel(hybridPoi, setOf("electric"), emptySet()))

        // Both filters -> fuel price (Priority 1)
        assertEquals("€1.500", PoiMarkerHelper.getPoiLabel(hybridPoi, setOf("sp95", "electric"), emptySet()))
    }

    @Test
    fun `getPoiColor returns correct color for IRVE station`() {
        val evPoi = Poi(
            id = "4",
            name = "EV 1",
            address = "Address 4",
            latitude = 0.0,
            longitude = 0.0,
            isElectric = true,
            powerKw = 150.0
        )

        val color = PoiMarkerHelper.getPoiColor(evPoi, PoiCategory.Irve, setOf("electric"), emptySet())
        val expected = fr.geoking.gaston.ui.ColorHelper.getPowerColor(150.0)
        assertEquals(expected, androidx.compose.ui.graphics.Color(color))
    }
}
