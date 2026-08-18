package fr.geoking.gaston.poi

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Semécourt (Moselle): gas station at Voie Romaine next to Auchan supermarket.
 *
 * End-to-end checks of [PoiMerger.enrichBrandsFromSupermarkets] on real geography:
 * - a tight zone that only contains the station + supermarket
 * - a 20 km diameter zone with many other stations
 *
 * Coordinates: pump ≈ (49.19477, 6.14645); OSM Auchan supermarket node 5467200318.
 * DataGouv station id `57280001` often ships with coarse geom (~49.199, 6.15).
 */
class SemecourtSupermarketBrandMergeTest {

    @Test
    fun closeZone_onlyStationAndSupermarket_enrichesAuchanBrand() {
        val station = semecourtStation(brand = null)
        val supermarket = auchanSupermarket()

        val distM = haversineMeters(
            station.latitude, station.longitude,
            supermarket.latitude, supermarket.longitude,
        )
        assertTrue(
            distM <= SUPERMARKET_ENRICH_MAX_M,
            "Semécourt pump and Auchan supermarket must be within enrich radius " +
                "(got ${distM.toInt()}m, max ${SUPERMARKET_ENRICH_MAX_M.toInt()}m)",
        )

        val enriched = PoiMerger.enrichBrandsFromSupermarkets(
            pois = listOf(station),
            supermarkets = listOf(supermarket),
        )

        assertEquals(1, enriched.size)
        assertEquals("Auchan", enriched.single().brand)
        assertEquals("Auchan", enriched.single().name)
        assertEquals(SEMECOURT_STATION_ID, enriched.single().id)
    }

    @Test
    fun zone20kmDiameter_enrichesSemecourtAmongManyStations() {
        val supermarket = auchanSupermarket()
        val stations = buildList {
            add(semecourtStation(brand = "sans enseigne"))
            // Decoys within a 20 km diameter (10 km radius) around the commercial center.
            addAll(stationsOnRingKm(radiusKm = 2.0, count = 4, brand = null))
            addAll(stationsOnRingKm(radiusKm = 5.0, count = 6, brand = "Independant"))
            addAll(stationsOnRingKm(radiusKm = 9.5, count = 8, brand = null))
            // Already branded — must not be overwritten even if somehow near a supermarket.
            add(
                Poi(
                    id = "branded-esso",
                    name = "Esso Semécourt Nord",
                    address = "",
                    latitude = STATION_LAT + 0.02,
                    longitude = STATION_LON,
                    brand = "Esso",
                    poiCategory = PoiCategory.Gas,
                ),
            )
        }

        stations.forEach { poi ->
            val dKm = haversineMeters(CENTER_LAT, CENTER_LON, poi.latitude, poi.longitude) / 1000.0
            assertTrue(
                dKm <= ZONE_20KM_DIAMETER_RADIUS_KM + 0.05,
                "Fixture station ${poi.id} is ${"%.2f".format(dKm)} km from center " +
                    "(expected ≤ ${ZONE_20KM_DIAMETER_RADIUS_KM} km)",
            )
        }

        val enriched = PoiMerger.enrichBrandsFromSupermarkets(stations, listOf(supermarket))
        val byId = enriched.associateBy { it.id }

        val semecourt = byId[SEMECOURT_STATION_ID]
        assertNotNull(semecourt)
        assertEquals("Auchan", semecourt.brand, "Semécourt station should get Auchan from nearby supermarket")
        assertEquals("Auchan", semecourt.name, "Generic Station SEMéCOURT title should become Auchan")

        assertEquals("Esso", byId.getValue("branded-esso").brand)
        assertEquals("Esso Semécourt Nord", byId.getValue("branded-esso").name)

        // Stations far from Auchan keep their original brand (null / Independant).
        stations.filter { it.id != SEMECOURT_STATION_ID }.forEach { original ->
            assertEquals(
                original.brand,
                byId.getValue(original.id).brand,
                "Station ${original.id} should not inherit Auchan (too far from supermarket)",
            )
        }
    }

    private fun semecourtStation(brand: String?): Poi = Poi(
        id = SEMECOURT_STATION_ID,
        name = "Station SEMéCOURT",
        address = "VOIE ROMAINE, 57280, SEMéCOURT",
        latitude = STATION_LAT,
        longitude = STATION_LON,
        brand = brand,
        poiCategory = PoiCategory.Gas,
        source = "DataGouv",
    )

    private fun auchanSupermarket(): Poi = Poi(
        id = "osm:$AUCHAN_OSM_NODE_ID",
        name = "Auchan",
        address = "Parc commercial de Auchan Semécourt",
        latitude = AUCHAN_LAT,
        longitude = AUCHAN_LON,
        brand = "Auchan",
        poiCategory = PoiCategory.Supermarket,
        source = "Overpass",
    )

    /** Places [count] stations evenly on a circle of [radiusKm] around the Semécourt center. */
    private fun stationsOnRingKm(radiusKm: Double, count: Int, brand: String?): List<Poi> {
        val latDeg = radiusKm / 111.0
        val lonDeg = radiusKm / (111.0 * cos(CENTER_LAT * PI / 180.0))
        return (0 until count).map { i ->
            val angle = 2.0 * PI * i / count
            Poi(
                id = "decoy-r${radiusKm.toInt()}-$i",
                name = "Station decoy $i",
                address = "",
                latitude = CENTER_LAT + latDeg * sin(angle),
                longitude = CENTER_LON + lonDeg * cos(angle),
                brand = brand,
                poiCategory = PoiCategory.Gas,
            )
        }
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
        /** Official DataGouv / prix-carburants id for Auchan Semécourt. */
        const val SEMECOURT_STATION_ID = "57280001"

        /** Accurate pump location (Voie Romaine), ~18 m from Auchan supermarket. */
        const val STATION_LAT = 49.1947746
        const val STATION_LON = 6.1464533

        /** OSM node 5467200318 — shop=supermarket brand=Auchan. */
        const val AUCHAN_OSM_NODE_ID = 5467200318L
        const val AUCHAN_LAT = 49.1946182
        const val AUCHAN_LON = 6.1465251

        const val CENTER_LAT = STATION_LAT
        const val CENTER_LON = STATION_LON

        /** Must stay in sync with [PoiMerger.SUPERMARKET_BRAND_ENRICH_METERS]. */
        const val SUPERMARKET_ENRICH_MAX_M = PoiMerger.SUPERMARKET_BRAND_ENRICH_METERS

        /** 20 km diameter ⇒ 10 km search radius. */
        const val ZONE_20KM_DIAMETER_RADIUS_KM = 10.0
    }
}
