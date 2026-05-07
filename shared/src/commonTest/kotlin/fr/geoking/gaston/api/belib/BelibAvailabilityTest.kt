package fr.geoking.gaston.api.belib

import io.ktor.client.HttpClient
import fr.geoking.gaston.poi.Poi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BelibAvailabilityTest {

    private val client = BelibAvailabilityClient(HttpClient())
    private val provider = BelibAvailabilityProvider(client)

    @Test
    fun parseRecords_parsesBelibRecord() {
        val body = """
            {
                "results": [
                    {
                        "id_pdc": "FR*V75*EPX19*17*6",
                        "statut_pdc": "Disponible",
                        "coordonneesxy": {
                            "lon": 2.370891,
                            "lat": 48.882175
                        },
                        "adresse_station": "4 Avenue Secrétan 75019 Paris"
                    }
                ]
            }
        """.trimIndent()

        val records = client.parseRecords(body)
        assertEquals(1, records.size)
        val record = records[0]
        assertEquals("FR*V75*EPX19*17*6", record.idPdc)
        assertEquals("Disponible", record.statutPdc)
        assertEquals(48.882175, record.latitude)
        assertEquals(2.370891, record.longitude)
        assertEquals("4 Avenue Secrétan 75019 Paris", record.adresseStation)
    }

    @Test
    fun mapStatut_mapsCorrectStatuses() {
        assertEquals(AvailabilityStatus.Available, provider.mapStatut("Disponible"))
        assertEquals(AvailabilityStatus.Occupied, provider.mapStatut("Occupé (en charge)"))
        assertEquals(AvailabilityStatus.Occupied, provider.mapStatut("occupe"))
        assertEquals(AvailabilityStatus.Maintenance, provider.mapStatut("En maintenance"))
        assertEquals(AvailabilityStatus.Reserved, provider.mapStatut("Réservé"))
        assertEquals(AvailabilityStatus.NotImplemented, provider.mapStatut("Pas implémenté"))
        assertEquals(AvailabilityStatus.ComingIntoService, provider.mapStatut("En cours de mise en service"))
        assertEquals(AvailabilityStatus.PlannedIntoService, provider.mapStatut("Mise en service planifiée"))
        assertEquals(AvailabilityStatus.Removed, provider.mapStatut("Supprimé"))
        assertEquals(AvailabilityStatus.Unknown, provider.mapStatut("Inconnu"))
        assertEquals(AvailabilityStatus.Unknown, provider.mapStatut("Something else"))
    }

    @Test
    fun stationIdFromPdcId_derivesCorrectId() {
        assertEquals("FR*V75*E9004*01", provider.stationIdFromPdcId("FR*V75*E9004*01*1"))
        assertEquals("FR*V75*E9004*01", provider.stationIdFromPdcId("FR*V75*E9004*01*2"))
        assertEquals(null, provider.stationIdFromPdcId("SHORT-ID"))
    }

    @Test
    fun matchAvailabilityToPois_matchesByDistance() {
        val availabilities = listOf(
            PdcAvailability(
                id = "PDC1",
                status = AvailabilityStatus.Available,
                latitude = 48.85,
                longitude = 2.35,
                stationId = "ST1"
            ),
            PdcAvailability(
                id = "PDC2",
                status = AvailabilityStatus.Occupied,
                latitude = 48.85,
                longitude = 2.35,
                stationId = "ST1"
            )
        )
        val pois = listOf(
            Poi(
                id = "POI1",
                name = "Belib Station",
                address = "Address",
                latitude = 48.85001, // Very close to 48.85
                longitude = 2.35001,
                isElectric = true
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
    fun matchAvailabilityToPois_filtersByMaxDistance() {
        val availabilities = listOf(
            PdcAvailability(
                id = "PDC1",
                status = AvailabilityStatus.Available,
                latitude = 48.85,
                longitude = 2.35,
                stationId = "ST1"
            )
        )
        val pois = listOf(
            Poi(
                id = "POI_FAR",
                name = "Far Station",
                address = "Address",
                latitude = 48.90, // Far
                longitude = 2.40,
                isElectric = true
            )
        )

        val result = matchAvailabilityToPois(availabilities, pois, maxDistanceMeters = 100.0)
        assertEquals(0, result.size)
    }
}
