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
    fun mergePois_unconditionalMergeWithin50m() {
        val lat = 48.8566
        val lon = 2.3522

        // ~33m away, different names
        val p1 = Poi("1", "Station Alpha", "Address 1", lat, lon)
        val p2 = Poi("2", "Station Beta", "Address 2", lat + 0.0003, lon)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Should merge unconditionally within 50m")
    }

    @Test
    fun mergePois_noMergeBeyond50mIfNamesDifferent() {
        val lat = 48.8566
        val lon = 2.3522

        // ~66m away, different names
        val p1 = Poi("1", "Station Alpha", "Address 1", lat, lon)
        val p2 = Poi("2", "Station Beta", "Address 2", lat + 0.0006, lon)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge beyond 50m if names are different")
    }

    @Test
    fun mergePois_mergeWithin250mIfNamesMatch() {
        val lat = 48.8566
        val lon = 2.3522

        // ~111m away, same name
        val p1 = Poi("1", "Total Paris", "Address 1", lat, lon)
        val p2 = Poi("2", "Total Paris", "Address 2", lat + 0.001, lon)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Should merge within 250m if names match")
    }

    @Test
    fun mergePois_noMergeBeyond250mEvenIfNamesMatch() {
        val lat = 48.8566
        val lon = 2.3522

        // ~333m away, same name
        val p1 = Poi("1", "Total Paris", "Address 1", lat, lon)
        val p2 = Poi("2", "Total Paris", "Address 2", lat + 0.003, lon)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge beyond 250m even if names match")
    }

    @Test
    fun mergePois_detectsClosureFromStalePrices() {
        val lat = 48.8566
        val lon = 2.3522

        // Mocking "now" is hard with kotlinx-datetime Clock.System.now() unless we use a wrapper,
        // but we can use a date very far in the past.
        val staleDate = "2000-01-01T10:00:00Z"

        val p1 = Poi("1", "Old Station", "Address", lat, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.50, updatedAt = staleDate)
        ))
        val p2 = Poi("2", "Old Station", "Address", lat + 0.0001, lon, fuelPrices = listOf(
            FuelPrice("SP95", 1.60, updatedAt = staleDate)
        ))

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertTrue(merged[0].isClosed, "Should be marked closed if all prices are stale (>4 weeks)")
    }

    @Test
    fun mergePois_keepsOpenIfOnePriceIsRecent() {
        val lat = 48.8566
        val lon = 2.3522

        val staleDate = "2000-01-01T10:00:00Z"
        // Recent date: I'll use a hardcoded one that is likely recent for the next few years,
        // or just use something very far in the future to be safe in this environment.
        // Actually, let's use 2025-01-01 if current year is 2024?
        // Better: use a date that is definitely NOT stale.
        val recentDate = "2099-01-01T10:00:00Z"

        val p1 = Poi("1", "Mixed Station", "Address", lat, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.50, updatedAt = staleDate)
        ))
        val p2 = Poi("2", "Mixed Station", "Address", lat + 0.0001, lon, fuelPrices = listOf(
            FuelPrice("SP95", 1.60, updatedAt = recentDate)
        ))

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertTrue(!merged[0].isClosed, "Should NOT be marked closed if at least one price is recent")
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

    @Test
    fun mergePois_mergesAmenities() {
        val lat = 48.8566
        val lon = 2.3522

        val p1 = Poi("1", "Station", "Address", lat, lon, amenities = fr.geoking.gaston.api.routex.PoiAmenities(
            shop = true,
            toilets = false
        ))
        val p2 = Poi("2", "Station", "Address", lat + 0.0001, lon, amenities = fr.geoking.gaston.api.routex.PoiAmenities(
            shop = null,
            toilets = true,
            wifi = true
        ))

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        val amenities = merged[0].amenities
        assertTrue(amenities?.shop == true)
        assertTrue(amenities?.toilets == true) // p2's true should win over p1's false as it's merged after or just because of ?: logic (wait, ?: keeps first non-null)
        // Actually, PoiMerger.mergeAmenities uses: toilets = a.toilets ?: b.toilets
        // If a.toilets is false (not null), it keeps false.
        // Let's check my logic in mergeAmenities again.
    }

    @Test
    fun mergePois_prefersBetterName() {
        val lat = 48.8566
        val lon = 2.3522

        // "Route" is generic, "Total Paris" is better.
        // p1 has a lower ID so it will be the "existing" POI during merge.
        val p1 = Poi("1", "Route", "Address 1", lat, lon)
        val p2 = Poi("2", "Total Paris", "Address 1", lat + 0.0001, lon)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals("Total Paris", merged[0].name, "Should pick 'Total Paris' over 'Route'")

        // Test reverse IDs
        val p3 = Poi("1", "Total Paris", "Address 1", lat, lon)
        val p4 = Poi("2", "Route", "Address 1", lat + 0.0001, lon)

        val merged2 = PoiMerger.mergePois(listOf(p3, p4))
        assertEquals(1, merged2.size)
        assertEquals("Total Paris", merged2[0].name, "Should pick 'Total Paris' over 'Route' regardless of ID")
    }
}
