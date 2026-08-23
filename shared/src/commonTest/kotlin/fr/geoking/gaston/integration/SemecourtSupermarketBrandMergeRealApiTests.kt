package fr.geoking.gaston.integration

import fr.geoking.gaston.api.datagouv.DataGouvProvider
import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassProvider
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.SemecourtSupermarketBrandMergeTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Live HTTP e2e for Semécourt supermarket brand enrichment.
 *
 * Fetches DataGouv gas stations + Overpass supermarkets for:
 * - a close zone (~1 km) that should only contain the Semécourt station + Auchan
 * - a 20 km diameter zone (10 km radius)
 *
 * Then runs [PoiMerger.enrichBrandsFromSupermarkets] and asserts the Semécourt station
 * receives the Auchan brand. Excluded from default CI via `*RealApiTests*` filter.
 */
class SemecourtSupermarketBrandMergeRealApiTests {

    private val client by lazy { createRealApiHttpClient() }

    @Test
    fun closeZone_stationAndAuchanOnly_enrichesBrand() = runBlocking {
        runSemecourtMergeScenario(
            label = "close zone (~1 km)",
            radiusKm = CLOSE_ZONE_RADIUS_KM,
            expectFewStations = true,
        )
    }

    @Test
    fun zone20kmDiameter_enrichesSemecourtAmongManyStations() = runBlocking {
        runSemecourtMergeScenario(
            label = "20 km diameter (10 km radius)",
            radiusKm = ZONE_20KM_DIAMETER_RADIUS_KM,
            expectFewStations = false,
        )
    }

    private suspend fun runSemecourtMergeScenario(
        label: String,
        radiusKm: Int,
        expectFewStations: Boolean,
    ) {
        val overpass = OverpassProvider(OverpassClient(client), radiusKm = radiusKm, limit = 100)

        // DataGouv caps at 100 rows (often one row per fuel), unordered — a wide query can
        // omit Semécourt. Always pin a close fetch on the official PDV geom, then widen.
        val stations = withTimeout(180_000) {
            val close = loadWithRetries {
                    DataGouvProvider(client, radiusKm = CLOSE_ZONE_RADIUS_KM, limit = 100)
                    .getGasStations(DATAGOUV_LAT, DATAGOUV_LON, viewport = null)
            }
            if (radiusKm <= CLOSE_ZONE_RADIUS_KM) {
                close
            } else {
                delay(1_000)
                val wide = loadWithRetries {
                        DataGouvProvider(client, radiusKm = radiusKm, limit = 100)
                        .getGasStations(DATAGOUV_LAT, DATAGOUV_LON, viewport = null)
                }
                (close + wide).distinctBy { it.id }
            }
        }
        delay(1_500)
        val supermarkets = withTimeout(180_000) {
            loadWithRetries {
                overpass.search(
                    PoiSearchRequest(
                        latitude = CENTER_LAT,
                        longitude = CENTER_LON,
                        categories = setOf(PoiCategory.Supermarket),
                        skipFilters = true,
                    ),
                )
            }
        }

        assertTrue(stations.isNotEmpty(), "$label: DataGouv returned no stations near Semécourt")
        assertTrue(supermarkets.isNotEmpty(), "$label: Overpass returned no supermarkets near Semécourt")

        val semecourt = stations.find { it.id == SemecourtSupermarketBrandMergeTest.SEMECOURT_STATION_ID }
            ?: stations.find { it.address.contains("SEMéCOURT", ignoreCase = true) }
            ?: stations.find { it.name.contains("SEMéCOURT", ignoreCase = true) }
        assertNotNull(
            semecourt,
            "$label: Semécourt station ${SemecourtSupermarketBrandMergeTest.SEMECOURT_STATION_ID} not in DataGouv results " +
                "(got ${stations.size}: ${stations.take(8).joinToString { it.id }})",
        )

        val auchan = findNearestAuchanSupermarket(supermarkets, CENTER_LAT, CENTER_LON)
        assertNotNull(
            auchan,
            "$label: Auchan supermarket not found in Overpass results " +
                "(got ${supermarkets.size}: ${supermarkets.take(8).joinToString { "${it.name}/${it.brand}" }})",
        )

        if (expectFewStations) {
            assertTrue(
                stations.size <= 3,
                "$label: expected a tight station set, got ${stations.size} stations",
            )
        } else {
            assertTrue(
                stations.size >= 5,
                "$label: expected many stations in 20 km diameter, got ${stations.size}",
            )
        }

        // Prefer accurate pump coords for enrich: live DataGouv geom can be >300 m from Auchan.
        val stationForMerge = semecourt.copy(
            brand = null,
            poiCategory = PoiCategory.Gas,
            latitude = SemecourtSupermarketBrandMergeTest.STATION_LAT,
            longitude = SemecourtSupermarketBrandMergeTest.STATION_LON,
        )
        val liveDistM = haversineMeters(
            semecourt.latitude, semecourt.longitude,
            auchan.latitude, auchan.longitude,
        )
        val distM = haversineMeters(
            stationForMerge.latitude, stationForMerge.longitude,
            auchan.latitude, auchan.longitude,
        )
        assertTrue(
            distM <= SemecourtSupermarketBrandMergeTest.SUPERMARKET_ENRICH_MAX_M,
            "$label: accurate pump–Auchan distance ${distM.toInt()}m exceeds enrich radius " +
                "(${SemecourtSupermarketBrandMergeTest.SUPERMARKET_ENRICH_MAX_M.toInt()}m)",
        )

        val enriched = PoiMerger.enrichBrandsFromSupermarkets(
            pois = listOf(stationForMerge) + stations.filter { it.id != stationForMerge.id },
            supermarkets = supermarkets,
        )
        val enrichedSemecourt = enriched.find { it.id == stationForMerge.id }
        assertNotNull(enrichedSemecourt)
        val brand = enrichedSemecourt.brand?.trim().orEmpty()
        assertTrue(
            brand.contains("Auchan", ignoreCase = true),
            "$label: expected Auchan brand after supermarket merge, got '$brand' " +
                "(supermarket=${auchan.name}/${auchan.brand}, dist=${distM.toInt()}m, " +
                "liveDataGouvDist=${liveDistM.toInt()}m)",
        )
        println(
            "$label: OK — Semécourt ${stationForMerge.id} → brand=$brand " +
                "(${stations.size} stations, ${supermarkets.size} supermarkets, " +
                "dist=${distM.toInt()}m, liveDataGouvDist=${liveDistM.toInt()}m)",
        )
    }

    private fun findNearestAuchanSupermarket(
        supermarkets: List<Poi>,
        lat: Double,
        lon: Double,
    ): Poi? {
        return supermarkets
            .filter { poi ->
                listOfNotNull(poi.brand, poi.name).any { it.contains("Auchan", ignoreCase = true) }
            }
            .minByOrNull { haversineMeters(lat, lon, it.latitude, it.longitude) }
    }

    private suspend fun loadWithRetries(
        maxAttempts: Int = 3,
        load: suspend (attempt: Int) -> List<Poi>,
    ): List<Poi> {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                if (attempt > 0) delay(3_000)
                val result = load(attempt)
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (lastError != null) {
            fail("Live fetch failed after $maxAttempts attempts: ${lastError!!.message}")
        }
        return emptyList()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = lat1 * PI / 180.0
        val p2 = lat2 * PI / 180.0
        val dp = (lat2 - lat1) * PI / 180.0
        val dl = (lon2 - lon1) * PI / 180.0
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(a))
    }

    companion object {
        private val CENTER_LAT = SemecourtSupermarketBrandMergeTest.CENTER_LAT
        private val CENTER_LON = SemecourtSupermarketBrandMergeTest.CENTER_LON

        /** Coarse DataGouv geom for PDV 57280001 (used as gas query center). */
        private const val DATAGOUV_LAT = 49.199
        private const val DATAGOUV_LON = 6.15

        /** Tight zone: station + commercial park only. */
        private const val CLOSE_ZONE_RADIUS_KM = 1

        /** 20 km diameter. */
        private val ZONE_20KM_DIAMETER_RADIUS_KM =
            SemecourtSupermarketBrandMergeTest.ZONE_20KM_DIAMETER_RADIUS_KM.toInt()
    }
}
