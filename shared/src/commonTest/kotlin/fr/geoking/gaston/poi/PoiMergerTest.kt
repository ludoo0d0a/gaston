package fr.geoking.gaston.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoiMergerTest {

    @Test
    fun mergePois_mergesClosePois() {
        // Paris coordinates
        val lat = 48.8566
        val lon = 2.3522

        // 0.0001 lat/lon is roughly 11 meters
        val p1 = Poi("1", "Station A", "Address 1", lat, lon, brand = "Generic")
        val p2 = Poi("2", "Station A", "Address 1", lat + 0.0002, lon + 0.0002, brand = "Generic") // ~30m away

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Should merge two close POIs with similar names")
    }

    @Test
    fun mergePois_respects100mThreshold() {
        val lat = 48.8566
        val lon = 2.3522

        // 0.001 degrees latitude is ~111m
        val p1 = Poi("1", "Station A", "Address 1", lat, lon, brand = "Generic")
        val p2 = Poi("2", "Station A", "Address 1", lat + 0.001, lon, brand = "Generic") // ~111m away

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge POIs more than 100m away")
    }

    @Test
    fun mergePois_prioritizesBrandWithIcon() {
        val lat = 48.8566
        val lon = 2.3522

        // "Total" has an icon, "GenericBrand" does not
        val p1 = Poi("1", "Total Paris", "Address 1", lat, lon, brand = "GenericBrand")
        val p2 = Poi("2", "Total Paris", "Address 1", lat + 0.0001, lon, brand = "Total")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals("Total", merged[0].brand, "Should pick the brand with an icon")

        // Reverse order
        val merged2 = PoiMerger.mergePois(listOf(p2, p1))
        assertEquals(1, merged2.size)
        assertEquals("Total", merged2[0].brand, "Should pick the brand with an icon regardless of order")
    }

    @Test
    fun mergePois_picksLatestPrice() {
        val lat = 48.8566
        val lon = 2.3522

        val p1 = Poi("1", "Station", "Address", lat, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.50, updatedAt = "2023-10-01T10:00:00Z")
        ))
        val p2 = Poi("2", "Station", "Address", lat + 0.0001, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.60, updatedAt = "2023-10-01T12:00:00Z")
        ))

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals(1.60, merged[0].fuelPrices?.first()?.price, "Should pick the latest price")

        // Reverse order
        val merged2 = PoiMerger.mergePois(listOf(p2, p1))
        assertEquals(1, merged2.size)
        assertEquals(1.60, merged2[0].fuelPrices?.first()?.price, "Should pick the latest price regardless of order")
    }

    @Test
    fun mergePois_picksPriceWithTimestampOverNull() {
        val lat = 48.8566
        val lon = 2.3522

        val p1 = Poi("1", "Station", "Address", lat, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.50, updatedAt = null)
        ))
        val p2 = Poi("2", "Station", "Address", lat + 0.0001, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.60, updatedAt = "2023-10-01T12:00:00Z")
        ))

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals(1.60, merged[0].fuelPrices?.first()?.price, "Should pick price with timestamp over null")
    }

    @Test
    fun mergePois_detectsBrandFromName() {
        val lat = 48.8566
        val lon = 2.3522

        // p1 has brand but generic name, p2 has NO brand but name contains "Total"
        // Names must be similar enough to trigger a merge.
        val p1 = Poi("1", "Total Paris Sud", "Address 1", lat, lon, brand = "Independant")
        val p2 = Poi("2", "Total Paris", "Address 1", lat + 0.0001, lon, brand = null)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals("Total", merged[0].brand, "Should detect Total from name and prioritize it over Independant")
    }

    @Test
    fun mergePois_detectsBrandFromBrandFieldFuzzy() {
        val lat = 48.8566
        val lon = 2.3522

        // p1 has a messy brand string that contains "Esso"
        val p1 = Poi("1", "Station", "Address 1", lat, lon, brand = "Esso Express - Relais")
        val p2 = Poi("2", "Station", "Address 1", lat + 0.0001, lon, brand = "Generic")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals("Esso", merged[0].brand, "Should normalize Esso Express to Esso")
    }
}
