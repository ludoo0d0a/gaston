package fr.geoking.gaston.api.qualicharge

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.Poi
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QualiChargeDynamiqueTest {

    private val client = QualiChargeDynamiqueClient(HttpClient())

    @Test
    fun parseDynamicCsv_parsesRows() {
        val csv = """
            id_pdc_itinerance,etat_pdc,occupation_pdc,horodatage
            FREVCE9008842,en_service,libre,2026-08-21 15:34:58+00:00
            FRSWSE6150801,en_service,occupe,2026-08-21 21:09:48+00:00
            FRS84EVIOWO2,hors_service,inconnu,2026-07-07 10:27:38+00:00
        """.trimIndent()

        val rows = client.parseDynamicCsv(csv)
        assertEquals(3, rows.size)
        assertEquals("FREVCE9008842", rows[0].idPdcItinerance)
        assertEquals("en_service", rows[0].etatPdc)
        assertEquals("libre", rows[0].occupationPdc)
        assertEquals("occupe", rows[1].occupationPdc)
        assertEquals("hors_service", rows[2].etatPdc)
    }

    @Test
    fun parseStaticCsv_parsesCoordonneesAndStation() {
        val csv = """
            id_station_itinerance,id_pdc_itinerance,coordonneesXY
            FRDREP270644195,FRDREE6474122,"[-1.93764016, 47.37397756]"
            FREVZPD673346,FREVZEICIA2,"[4.842365, 46.363661]"
        """.trimIndent()

        val byPdc = client.parseStaticCsv(csv)
        assertEquals(2, byPdc.size)
        val first = byPdc["FRDREE6474122"]
        assertNotNull(first)
        assertEquals("FRDREP270644195", first.stationId)
        assertEquals(47.37397756, first.latitude)
        assertEquals(-1.93764016, first.longitude)
        val second = byPdc["FREVZEICIA2"]
        assertNotNull(second)
        assertEquals(46.363661, second.latitude)
        assertEquals(4.842365, second.longitude)
    }

    @Test
    fun parseCoordonneesXy_handlesFormats() {
        assertEquals(-1.9 to 47.3, QualiChargeDynamiqueClient.parseCoordonneesXy("[-1.9, 47.3]"))
        assertEquals(4.8 to 46.3, QualiChargeDynamiqueClient.parseCoordonneesXy("\"[4.8, 46.3]\""))
        assertEquals(null, QualiChargeDynamiqueClient.parseCoordonneesXy(null))
        assertEquals(null, QualiChargeDynamiqueClient.parseCoordonneesXy("bad"))
    }

    @Test
    fun mapStatus_mapsEtatAndOccupation() {
        assertEquals(AvailabilityStatus.Available, client.mapStatus("en_service", "libre"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("en_service", "occupe"))
        assertEquals(AvailabilityStatus.Reserved, client.mapStatus("en_service", "reserve"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("hors_service", "libre"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("inconnu", "inconnu"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("en_service", "inconnu"))
    }

    @Test
    fun matchAvailabilityToPois_matchesByPdcId() {
        val availabilities = listOf(
            PdcAvailability(
                id = "PDC_A",
                status = AvailabilityStatus.Available,
                latitude = 45.0,
                longitude = 1.0,
                stationId = "ST_OTHER"
            ),
            PdcAvailability(
                id = "PDC_B",
                status = AvailabilityStatus.Occupied,
                latitude = 45.0,
                longitude = 1.0,
                stationId = "ST_OTHER"
            )
        )
        val pois = listOf(
            Poi(
                id = "POI1",
                name = "Station",
                address = "Addr",
                latitude = 48.0,
                longitude = 2.0,
                isElectric = true,
                irveDetails = IrveDetails(pdcIds = setOf("PDC_A", "PDC_B"))
            )
        )
        val result = matchAvailabilityToPois(availabilities, pois)
        assertEquals(1, result.size)
        val summary = result["POI1"]
        assertNotNull(summary)
        assertEquals(1, summary.availableCount)
        assertEquals(2, summary.totalCount)
    }

    @Test
    fun matchAvailabilityToPois_matchesByStationId() {
        val availabilities = listOf(
            PdcAvailability(
                id = "PDC1",
                status = AvailabilityStatus.Available,
                latitude = 0.0,
                longitude = 0.0,
                stationId = "FRSTATION1"
            )
        )
        val pois = listOf(
            Poi(
                id = "FRSTATION1",
                name = "IRVE",
                address = "Addr",
                latitude = 48.0,
                longitude = 2.0,
                isElectric = true
            )
        )
        val result = matchAvailabilityToPois(availabilities, pois)
        assertTrue(result.containsKey("FRSTATION1"))
        assertEquals(1, result["FRSTATION1"]!!.availableCount)
    }

    @Test
    fun factory_returnsQualiChargeInFrance_andMergesBelibInParis() = runBlocking {
        val belib = object : fr.geoking.gaston.api.belib.BorneAvailabilityProvider {
            override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int) =
                listOf(
                    PdcAvailability(
                        id = "BELIB1",
                        status = AvailabilityStatus.Available,
                        latitude = 48.85,
                        longitude = 2.35
                    )
                )
        }
        val quali = object : fr.geoking.gaston.api.belib.BorneAvailabilityProvider {
            override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int) =
                listOf(
                    PdcAvailability(
                        id = "QUALI1",
                        status = AvailabilityStatus.Occupied,
                        latitude = 48.85,
                        longitude = 2.35
                    )
                )
        }
        val factory = fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory(
            belibProvider = belib,
            qualiChargeProvider = quali,
        )
        // Lyon → QualiCharge only
        assertEquals(quali, factory.getProvider(45.75, 4.85))
        // Outside France (London) → none
        assertEquals(null, factory.getProvider(51.51, -0.13))

        // Paris → merged (QualiCharge primary + Belib secondary)
        val paris = factory.getProvider(48.85, 2.35)
        assertNotNull(paris)
        assertTrue(paris !== quali)
        assertTrue(paris !== belib)
        val merged = paris.getAvailability(48.85, 2.35, 10)
        assertEquals(2, merged.size)
        assertEquals(setOf("QUALI1", "BELIB1"), merged.map { it.id }.toSet())
    }

    @Test
    fun mergedProvider_keepsPrimaryOnIdCollision() = runBlocking {
        val primary = object : fr.geoking.gaston.api.belib.BorneAvailabilityProvider {
            override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int) =
                listOf(
                    PdcAvailability(
                        id = "SAME",
                        status = AvailabilityStatus.Available,
                        latitude = 48.85,
                        longitude = 2.35
                    )
                )
        }
        val secondary = object : fr.geoking.gaston.api.belib.BorneAvailabilityProvider {
            override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int) =
                listOf(
                    PdcAvailability(
                        id = "SAME",
                        status = AvailabilityStatus.Occupied,
                        latitude = 48.85,
                        longitude = 2.35
                    ),
                    PdcAvailability(
                        id = "EXTRA",
                        status = AvailabilityStatus.Reserved,
                        latitude = 48.86,
                        longitude = 2.36
                    )
                )
        }
        val merged = fr.geoking.gaston.api.belib.MergedBorneAvailabilityProvider(primary, secondary)
        val result = merged.getAvailability(48.85, 2.35, 10)
        assertEquals(2, result.size)
        assertEquals(AvailabilityStatus.Available, result.first { it.id == "SAME" }.status)
        assertTrue(result.any { it.id == "EXTRA" })
    }
}
