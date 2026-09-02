package fr.geoking.gaston.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoiMergerTest {

    @Test
    fun mergeInto_upsertsElectricAlongsideFuelOnSameStation() {
        val cache = mutableMapOf<String, Poi>()
        val fuel = Poi(
            id = "station-1",
            name = "Total",
            address = "Rue A",
            latitude = 48.85,
            longitude = 2.35,
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.75)),
        )
        val electric = Poi(
            id = "station-1",
            name = "Total",
            address = "Rue A",
            latitude = 48.85,
            longitude = 2.35,
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 150.0,
            operator = "TotalEnergies",
        )
        PoiMerger.mergeInto(cache, listOf(fuel))
        PoiMerger.mergeInto(cache, listOf(electric))
        val merged = cache["station-1"]!!
        assertEquals(listOf("Gazole"), merged.fuelPrices?.map { it.fuelName })
        assertTrue(merged.isElectric)
        assertEquals(150.0, merged.powerKw)
        assertEquals("TotalEnergies", merged.operator)
    }

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
    fun mergePois_mergeWithin300mIfSameBrand() {
        val lat = 48.8566
        val lon = 2.3522

        // ~277m away (0.0025 * 111,000)
        val p1 = Poi("1", "Total Station", "Address 1", lat, lon, brand = "Total")
        val p2 = Poi("2", "Another Station", "Address 2", lat + 0.0025, lon, brand = "Total")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Should merge within 300m if brands match")
    }

    @Test
    fun mergePois_noMergeBeyond300mEvenIfSameBrand() {
        val lat = 48.8566
        val lon = 2.3522

        // ~388m away (0.0035 * 111,000)
        val p1 = Poi("1", "Total Station", "Address 1", lat, lon, brand = "Total")
        val p2 = Poi("2", "Another Station", "Address 2", lat + 0.0035, lon, brand = "Total")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge beyond 300m even if brands match")
    }

    @Test
    fun mergePois_noMergeBeyond300mEvenIfNamesMatch() {
        val lat = 48.8566
        val lon = 2.3522

        // ~388m away, same name
        val p1 = Poi("1", "Total Paris", "Address 1", lat, lon)
        val p2 = Poi("2", "Total Paris", "Address 2", lat + 0.0035, lon)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge beyond 300m even if names match")
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

    @Test
    fun mergePois_prefersSpecificNameOverStationCity() {
        val lat = 48.8566
        val lon = 2.3522

        val p1 = Poi("1", "Station Paris", "Address 1", lat, lon)
        val p2 = Poi("2", "Total Paris", "Address 1", lat + 0.0001, lon)

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals("Total Paris", merged[0].name)
    }

    @Test
    fun hasNoBrand_identifiesGenericAndEmptyBrands() {
        val gasNoBrand1 = Poi("1", "Station", "Address", 48.85, 2.35, brand = null, poiCategory = PoiCategory.Gas)
        val gasNoBrand2 = Poi("2", "Station", "Address", 48.85, 2.35, brand = "Sans Enseigne", poiCategory = PoiCategory.Gas)
        val gasNoBrand3 = Poi("3", "Station", "Address", 48.85, 2.35, brand = "Independant (GMS)", poiCategory = PoiCategory.Gas)
        val gasWithBrand = Poi("4", "Station", "Address", 48.85, 2.35, brand = "Total", poiCategory = PoiCategory.Gas)
        val toiletNoBrand = Poi("5", "Toilets", "Address", 48.85, 2.35, brand = null, poiCategory = PoiCategory.Toilet)

        assertTrue(PoiMerger.hasNoBrand(gasNoBrand1))
        assertTrue(PoiMerger.hasNoBrand(gasNoBrand2))
        assertTrue(PoiMerger.hasNoBrand(gasNoBrand3))
        assertTrue(!PoiMerger.hasNoBrand(gasWithBrand))
        assertTrue(!PoiMerger.hasNoBrand(toiletNoBrand)) // Only gas stations should match
    }

    @Test
    fun enrichBrandsFromSupermarkets_enrichesNearStations() {
        val lat = 48.85
        val lon = 2.35

        // ~100m away (0.0009 lat)
        val stationNear = Poi("gas-near", "Gazole Station", "Address", lat, lon, brand = null, poiCategory = PoiCategory.Gas)
        val stationGeneric = Poi("gas-generic", "Station Paris", "Address", lat, lon, brand = null, poiCategory = PoiCategory.Gas)
        // ~2220m away (0.02 lat) — beyond SUPERMARKET_BRAND_ENRICH_METERS (300m)
        val stationFar = Poi("gas-far", "Gazole Station 2", "Address", lat + 0.02, lon, brand = "sans enseigne", poiCategory = PoiCategory.Gas)
        // Already branded
        val stationBranded = Poi("gas-branded", "Gazole Station 3", "Address", lat, lon, brand = "Esso", poiCategory = PoiCategory.Gas)

        val supermarket1 = Poi("market-1", "Auchan Supermarché", "Address", lat + 0.0009, lon, brand = "Auchan", poiCategory = PoiCategory.Supermarket)
        val supermarket2 = Poi("market-2", "E.Leclerc", "Address", lat + 0.005, lon, brand = null, poiCategory = PoiCategory.Supermarket)

        val enriched = PoiMerger.enrichBrandsFromSupermarkets(
            pois = listOf(stationNear, stationGeneric, stationFar, stationBranded),
            supermarkets = listOf(supermarket1, supermarket2)
        )

        assertEquals(4, enriched.size)

        val enrichedNear = enriched.find { it.id == "gas-near" }!!
        assertEquals("Auchan", enrichedNear.brand, "Should enrich brand from near supermarket")
        assertEquals("Gazole Station", enrichedNear.name, "Should not overwrite a specific name")

        val enrichedGeneric = enriched.find { it.id == "gas-generic" }!!
        assertEquals("Auchan", enrichedGeneric.brand)
        assertEquals("Auchan", enrichedGeneric.name, "Generic Station Paris title should become Auchan")

        val enrichedFar = enriched.find { it.id == "gas-far" }!!
        assertEquals("sans enseigne", enrichedFar.brand, "Should NOT enrich brand from far supermarket (>300m)")

        val enrichedBranded = enriched.find { it.id == "gas-branded" }!!
        assertEquals("Esso", enrichedBranded.brand, "Should NOT overwrite already branded station")
    }

    @Test
    fun enrichBrandsFromSupermarkets_replacesGenericSiteName() {
        val lat = 48.85
        val lon = 2.35
        val station = Poi(
            id = "gas-site",
            name = "Station",
            address = "Address",
            latitude = lat,
            longitude = lon,
            brand = null,
            poiCategory = PoiCategory.Gas,
            siteName = "Station Lyon",
        )
        val supermarket = Poi(
            id = "market",
            name = "Carrefour",
            address = "Address",
            latitude = lat + 0.0009,
            longitude = lon,
            brand = "Carrefour",
            poiCategory = PoiCategory.Supermarket,
        )

        val enriched = PoiMerger.enrichBrandsFromSupermarkets(listOf(station), listOf(supermarket)).single()
        assertEquals("Carrefour", enriched.brand)
        assertEquals("Carrefour", enriched.name)
        assertEquals("Carrefour", enriched.siteName)
    }

    @Test
    fun mergePois_complementaryGas_pricesAndBrandWithin300m() {
        val lat = 48.8566
        val lon = 2.3522
        // ~200 m apart
        val osm = Poi(
            id = "osm-total",
            name = "Total",
            address = "Rue A",
            latitude = lat,
            longitude = lon,
            brand = "Total",
            poiCategory = PoiCategory.Gas,
            source = "Overpass",
        )
        val dataGouv = Poi(
            id = "dg-prices",
            name = "Station Maizières",
            address = "Rue A",
            latitude = lat + 0.0018,
            longitude = lon,
            brand = null,
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.65, updatedAt = "2099-01-01T10:00:00Z")),
            source = "DataGouv",
        )

        val merged = PoiMerger.mergePois(listOf(osm, dataGouv))
        assertEquals(1, merged.size)
        assertEquals("Total", merged[0].name)
        assertEquals("Total", merged[0].brand)
        assertEquals(lat, merged[0].latitude)
        assertEquals(lon, merged[0].longitude)
        assertEquals(listOf("Gazole"), merged[0].fuelPrices?.map { it.fuelName })
    }

    @Test
    fun mergePois_complementaryGas_doesNotMergeWhenBothHavePrices() {
        val lat = 48.8566
        val lon = 2.3522
        val a = Poi(
            id = "1",
            name = "Total Nord",
            address = "",
            latitude = lat,
            longitude = lon,
            brand = "Total",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.60)),
        )
        val b = Poi(
            id = "2",
            name = "Total Sud",
            address = "",
            latitude = lat + 0.0018,
            longitude = lon,
            brand = "Total",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.62)),
        )
        // Same brand within 300m → still merges via brand rule; use different brands without name match
        val c = Poi(
            id = "3",
            name = "Station Alpha",
            address = "",
            latitude = lat,
            longitude = lon,
            brand = null,
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.60)),
        )
        val d = Poi(
            id = "4",
            name = "Station Beta",
            address = "",
            latitude = lat + 0.0018,
            longitude = lon,
            brand = null,
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("SP95", 1.70)),
        )
        assertEquals(2, PoiMerger.mergePois(listOf(c, d)).size)
        // Brand match still merges Total pair (expected)
        assertEquals(1, PoiMerger.mergePois(listOf(a, b)).size)
    }

    @Test
    fun mergePois_prefersBrandedCoordsOverPricedGeneric() {
        val lat = 48.8566
        val lon = 2.3522
        val branded = Poi(
            id = "z-branded",
            name = "Total",
            address = "",
            latitude = lat,
            longitude = lon,
            brand = "Total",
            poiCategory = PoiCategory.Gas,
        )
        val priced = Poi(
            id = "a-priced",
            name = "Station",
            address = "",
            latitude = lat + 0.0002,
            longitude = lon,
            brand = null,
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.55)),
        )
        // Sort by id: a-priced is existing, z-branded is incoming → coords should still move to Total
        val merged = PoiMerger.mergePois(listOf(priced, branded))
        assertEquals(1, merged.size)
        assertEquals(lat, merged[0].latitude)
        assertEquals(lon, merged[0].longitude)
        assertEquals("Total", merged[0].name)
        assertEquals(listOf("Gazole"), merged[0].fuelPrices?.map { it.fuelName })
    }

    @Test
    fun mergePois_doesNotBrandMergeDistinctChargyHubsWithin300m() {
        // Real P+R Bouillon cluster: hub + two nearby Chargy sites (same brand, different names).
        val hub = Poi(
            id = "chargy-49.599813-6.108361",
            name = "VdL - Luxembourg - P+R Bouillon",
            address = "Rue de Bouillon 61",
            latitude = 49.599813,
            longitude = 6.108361,
            brand = "Chargy",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            chargePointCount = 68,
            irveDetails = IrveDetails(availableConnectors = 58, totalConnectors = 68),
        )
        val aerien = Poi(
            id = "chargy-49.599956-6.106203",
            name = "Chargy Ok - Parking aérien BOUILLON",
            address = "Rue de Bouillon 61",
            latitude = 49.599956,
            longitude = 6.106203,
            brand = "Chargy",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            chargePointCount = 2,
            irveDetails = IrveDetails(availableConnectors = 2, totalConnectors = 2),
        )
        val conservatoire = Poi(
            id = "chargy-49.602135-6.106474",
            name = "Chargy Ok - Parking souterrain CONSERVATOIRE",
            address = "Nearby",
            latitude = 49.602135,
            longitude = 6.106474,
            brand = "Chargy",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            chargePointCount = 3,
            irveDetails = IrveDetails(availableConnectors = 3, totalConnectors = 3),
        )

        val merged = PoiMerger.mergePois(listOf(hub, aerien, conservatoire))
        assertEquals(3, merged.size, "Distinct Chargy EV sites within 300m must stay separate")
        val hubMerged = merged.single { it.id == hub.id }
        assertEquals(58, hubMerged.irveDetails?.availableConnectors)
        assertEquals(68, hubMerged.irveDetails?.totalConnectors)
    }

    @Test
    fun mergePois_gasSameBrandWithin300mStillMerges() {
        val lat = 48.8566
        val lon = 2.3522
        val p1 = Poi("1", "Total Station", "Address 1", lat, lon, brand = "Total")
        val p2 = Poi("2", "Another Station", "Address 2", lat + 0.0025, lon, brand = "Total")
        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Gas same-brand within 300m should still merge")
    }

    @Test
    fun mergePois_preferLargerIrveTotalsWhenMergingWithin50m() {
        val lat = 49.599813
        val lon = 6.108361
        val hub = Poi(
            id = "hub",
            name = "VdL - P+R Bouillon",
            address = "A",
            latitude = lat,
            longitude = lon,
            brand = "Chargy",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            irveDetails = IrveDetails(availableConnectors = 58, totalConnectors = 68),
        )
        // ~33m away — unconditional merge despite different name / smaller totals
        val small = Poi(
            id = "small",
            name = "Nearby dual CP",
            address = "B",
            latitude = lat + 0.0003,
            longitude = lon,
            brand = "Chargy",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            irveDetails = IrveDetails(availableConnectors = 2, totalConnectors = 2),
        )

        val merged = PoiMerger.mergePois(listOf(hub, small))
        assertEquals(1, merged.size)
        assertEquals(58, merged[0].irveDetails?.availableConnectors)
        assertEquals(68, merged[0].irveDetails?.totalConnectors)
    }
}
