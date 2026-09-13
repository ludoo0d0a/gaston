package fr.geoking.gaston.api.gireve

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GireveTest {

    private val sampleStaticCsv = """
        nom_amenageur,siren_amenageur,contact_amenageur,nom_operateur,contact_operateur,telephone_operateur,nom_enseigne,id_station_itinerance,id_station_local,nom_station,implantation_station,adresse_station,code_insee_commune,coordonneesXY,nbre_pdc,id_pdc_itinerance,id_pdc_local,puissance_nominale,prise_type_ef,prise_type_2,prise_type_combo_ccs,prise_type_chademo,prise_type_autre,gratuit,paiement_acte,paiement_cb,paiement_autre,tarification,condition_acces,reservation,horaires,accessibilite_pmr,restriction_gabarit,station_deux_roues,raccordement,num_pdl,date_mise_en_service,observations,date_maj,cable_t2_attache
        Freshmile,123456789,,Freshmile,,,Freshmile,FRFREP123456,,Metz Centre,Parking,Place de la Comedie Metz 57000,57463,"[6.175, 49.120]",2,FRFREE0001,,22,False,True,False,False,False,False,False,False,True,,Accès libre,False,24/7,Accessibilité inconnue,,False,,,,2026-05-15,False
        Freshmile,123456789,,Freshmile,,,Freshmile,FRFREP123456,,Metz Centre,Parking,Place de la Comedie Metz 57000,57463,"[6.175, 49.120]",2,FRFREE0002,,22,False,True,False,False,False,False,False,False,True,,Accès libre,False,24/7,Accessibilité inconnue,,False,,,,2026-05-15,False
        Paris Op,987654321,,Paris Op,,,Paris Enseigne,FRPARP99999,,Paris Station,Voirie,Rue de Rivoli Paris 75001,75101,"[2.350, 48.855]",1,FRPARD9999,,50,False,True,True,False,False,False,False,False,True,,Accès libre,False,24/7,Accessibilité inconnue,,False,,,,2026-05-15,False
    """.trimIndent()

    private val sampleDynamicCsv = """
        id_pdc_itinerance,etat_pdc,occupation_pdc,horodatage,etat_prise_type_2,etat_prise_type_combo_ccs,etat_prise_type_chademo,etat_prise_type_ef
        FRFREE0001,en_service,libre,2026-09-13 14:20:26,,fonctionnel,,
        FRFREE0002,en_service,occupe,2026-09-13 14:20:26,,fonctionnel,,
        FRPARD9999,en_service,libre,2026-09-13 14:20:26,,fonctionnel,,
    """.trimIndent()

    private fun createMockClient(): HttpClient {
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("gireve-irve-dynamique") -> {
                    respond(
                        content = sampleDynamicCsv,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/csv; charset=utf-8")
                    )
                }
                url.contains("gireve-irve-statique") -> {
                    respond(
                        content = sampleStaticCsv,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/csv; charset=utf-8")
                    )
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        return HttpClient(mockEngine)
    }

    @Test
    fun parseCoordonneesXy_validAndInvalid() {
        assertEquals(6.175 to 49.120, GireveClient.parseCoordonneesXy("[6.175, 49.120]"))
        assertEquals(2.35 to 48.855, GireveClient.parseCoordonneesXy("\"[2.35, 48.855]\""))
        assertEquals(null, GireveClient.parseCoordonneesXy(null))
        assertEquals(null, GireveClient.parseCoordonneesXy("invalid"))
    }

    @Test
    fun statusMapping() {
        val client = GireveClient(HttpClient(MockEngine { respond("") }))
        assertEquals(AvailabilityStatus.Available, client.mapStatus("en_service", "libre"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("en_service", "occupe"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("en_service", "occupé"))
        assertEquals(AvailabilityStatus.Reserved, client.mapStatus("en_service", "reserve"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("hors_service", "libre"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("inconnu", "inconnu"))
    }

    @Test
    fun getAvailability_returnsRecordsNearLocation() = runBlocking {
        val client = GireveClient(createMockClient())
        // Query near Metz (49.12, 6.175)
        val records = client.getAvailability(49.12, 6.175, radiusKm = 10)
        assertEquals(2, records.size)
        assertTrue(records.all { it.idPdcItinerance.startsWith("FRFREE") })
        assertEquals("Metz Centre", records.first().stationName)
    }

    @Test
    fun gireveProvider_returnsGroupedPoisWithAvailability() = runBlocking {
        val client = GireveClient(createMockClient())
        val provider = GireveProvider(client)

        val pois = provider.getGasStations(49.12, 6.175)
        assertEquals(1, pois.size)

        val poi = pois.first()
        assertEquals("Metz Centre", poi.name)
        assertEquals("Freshmile", poi.brand)
        assertTrue(poi.isElectric)
        assertEquals(PoiCategory.Irve, poi.poiCategory)
        assertEquals(2, poi.chargePointCount)

        assertNotNull(poi.irveDetails)
        assertEquals(1, poi.irveDetails?.availableConnectors)
        assertEquals(2, poi.irveDetails?.totalConnectors)
        assertEquals(setOf("FRFREE0001", "FRFREE0002"), poi.irveDetails?.pdcIds)
        assertEquals("Gireve", poi.source)
    }

    @Test
    fun gireveAvailabilityProvider_returnsPdcList() = runBlocking {
        val client = GireveClient(createMockClient())
        val availProvider = GireveAvailabilityProvider(client)

        val availabilities = availProvider.getAvailability(49.12, 6.175, radiusKm = 10)
        assertEquals(2, availabilities.size)

        val first = availabilities.first { it.id == "FRFREE0001" }
        assertEquals(AvailabilityStatus.Available, first.status)

        val second = availabilities.first { it.id == "FRFREE0002" }
        assertEquals(AvailabilityStatus.Occupied, second.status)
    }

    @Test
    fun borneAvailabilityProviderFactory_selectsGireveInFrance() {
        val mockClient = GireveClient(createMockClient())
        val gireveAvail = GireveAvailabilityProvider(mockClient)
        val belibAvail = GireveAvailabilityProvider(mockClient)

        val factory = BorneAvailabilityProviderFactory(
            belibProvider = belibAvail,
            gireveProvider = gireveAvail
        )

        // Metz (France, outside Paris)
        val providerInMetz = factory.getProvider(49.12, 6.175)
        assertNotNull(providerInMetz)

        // Paris (France, inside Paris)
        val providerInParis = factory.getProvider(48.85, 2.35)
        assertNotNull(providerInParis)
    }
}
