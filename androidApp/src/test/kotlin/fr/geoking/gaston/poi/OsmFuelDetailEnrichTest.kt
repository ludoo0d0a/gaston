package fr.geoking.gaston.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsmFuelDetailEnrichTest {

    @Test
    fun shouldPrefetchOsmFuelZone_onlyWhenRadiusReasonable() {
        assertTrue(shouldPrefetchOsmFuelZone(1))
        assertTrue(shouldPrefetchOsmFuelZone(15))
        assertFalse(shouldPrefetchOsmFuelZone(16))
        assertFalse(shouldPrefetchOsmFuelZone(0))
    }

    @Test
    fun shouldIncludeDiskPoiOnMap_excludesEnrichOnlyOsmFuel() {
        val enrich = Poi(
            id = "${OSM_FUEL_ENRICH_ID_PREFIX}123",
            name = "Gas station",
            address = "",
            latitude = 48.0,
            longitude = 2.0,
            poiCategory = PoiCategory.Gas,
            source = "OpenStreetMap",
        )
        val legacyOsm = Poi(
            id = "osm:999",
            name = "Gas station",
            address = "",
            latitude = 48.0,
            longitude = 2.0,
            poiCategory = PoiCategory.Gas,
            source = "OpenStreetMap",
        )
        val dataGouv = Poi(
            id = "57120005",
            name = "Total Paris",
            address = "",
            latitude = 48.0,
            longitude = 2.0,
            brand = "Total",
            poiCategory = PoiCategory.Gas,
            source = "DataGouvPrixCarburant",
        )
        val fuelOnly = setOf(PoiProviderType.Etalab, PoiProviderType.GasApi)
        assertFalse(shouldIncludeDiskPoiOnMap(enrich, fuelOnly, setOf(PoiCategory.Gas)))
        assertFalse(shouldIncludeDiskPoiOnMap(legacyOsm, fuelOnly, setOf(PoiCategory.Gas)))
        assertTrue(shouldIncludeDiskPoiOnMap(dataGouv, fuelOnly, setOf(PoiCategory.Gas)))
        // Prefixed enrich rows stay hidden even if Overpass is enabled for other amenities.
        assertFalse(
            shouldIncludeDiskPoiOnMap(
                enrich,
                fuelOnly + PoiProviderType.Overpass,
                setOf(PoiCategory.Gas),
            ),
        )
    }

    @Test
    fun toOsmFuelEnrichCachePoi_prefixesId() {
        val osm = Poi(
            id = "osm:42",
            name = "OSM",
            address = "",
            latitude = 1.0,
            longitude = 2.0,
            poiCategory = PoiCategory.Gas,
            source = "OpenStreetMap",
        )
        assertEquals("${OSM_FUEL_ENRICH_ID_PREFIX}42", toOsmFuelEnrichCachePoi(osm).id)
    }

    @Test
    fun matchOsmFuelForGasStation_prefersRefIdOverDistance() {
        val station = Poi(
            id = "57120005",
            refId = "57120005",
            name = "DataGouv",
            address = "",
            latitude = 49.25,
            longitude = 6.096,
            poiCategory = PoiCategory.Gas,
        )
        val farButSameRef = Poi(
            id = "osm:1",
            refId = "57120005",
            name = "OSM far",
            address = "",
            latitude = 49.26,
            longitude = 6.11,
            poiCategory = PoiCategory.Gas,
            source = "OpenStreetMap",
        )
        val nearNoRef = Poi(
            id = "osm:2",
            name = "OSM near",
            address = "",
            latitude = 49.2501,
            longitude = 6.0961,
            poiCategory = PoiCategory.Gas,
            source = "OpenStreetMap",
        )
        val match = matchOsmFuelForGasStation(station, listOf(nearNoRef, farButSameRef))
        assertEquals("osm:1", match?.id)
    }

    @Test
    fun matchOsmFuelForGasStation_nearestWithin100m() {
        val station = Poi(
            id = "a",
            name = "Station",
            address = "",
            latitude = 48.85,
            longitude = 2.35,
            poiCategory = PoiCategory.Gas,
        )
        val near = Poi(
            id = "osm:near",
            name = "Near",
            address = "",
            latitude = 48.8502,
            longitude = 2.3502,
            poiCategory = PoiCategory.Gas,
            source = "OpenStreetMap",
        )
        val far = Poi(
            id = "osm:far",
            name = "Far",
            address = "",
            latitude = 48.86,
            longitude = 2.36,
            poiCategory = PoiCategory.Gas,
            source = "OpenStreetMap",
        )
        assertEquals("osm:near", matchOsmFuelForGasStation(station, listOf(far, near))?.id)
        assertNull(matchOsmFuelForGasStation(station, listOf(far)))
    }

    @Test
    fun osmFuelEnrichFlightKey_sameAreaSharesKey() {
        assertEquals(
            osmFuelEnrichFlightKey(48.8566, 2.3522, 3),
            osmFuelEnrichFlightKey(48.8561, 2.3528, 3),
        )
    }

    @Test
    fun toOsmFuelEnrichLoadedRegion_marksOverpassGas() {
        val entity = fr.geoking.gaston.persistence.OsmFuelEnrichRegionEntity(
            id = 1,
            centerLat = 48.0,
            centerLng = 2.0,
            maxRadiusKmLoaded = 10,
            loadedAtMs = 42L,
        )
        val region = toOsmFuelEnrichLoadedRegion(entity)
        assertEquals(10, region.maxRadiusKmLoaded)
        assertEquals(setOf(PoiProviderType.Overpass), region.loadedProviders)
        assertEquals(setOf(PoiCategory.Gas), region.loadedCategories)
        assertNotNull(region.categoryLoadedAtMs[PoiCategory.Gas])
    }
}
