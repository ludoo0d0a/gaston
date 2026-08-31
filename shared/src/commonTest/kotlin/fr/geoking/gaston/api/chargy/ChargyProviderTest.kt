package fr.geoking.gaston.api.chargy

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargyProviderTest {

    private fun buildProvider(kmlResponse: String, apiKey: String = "test-key"): ChargyProvider {
        val engine = MockEngine {
            respond(
                content = kmlResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/vnd.google-earth.kml+xml")
            )
        }
        return ChargyProvider(client = HttpClient(engine), apiKey = apiKey, radiusKm = 20, limit = 50)
    }

    @Test
    fun getGasStations_returnsEmptyWhenApiKeyIsBlank() = runBlocking {
        val provider = buildProvider(SAMPLE_KML, apiKey = "")
        // Luxembourg coordinates
        val pois = provider.getGasStations(latitude = 49.6116, longitude = 6.1319)
        assertTrue(pois.isEmpty())
    }

    @Test
    fun getGasStations_returnsLuxembourgStationsWithinRadius() = runBlocking {
        val provider = buildProvider(SAMPLE_KML, apiKey = "test-key")
        // Luxembourg City coordinates
        val pois = provider.getGasStations(latitude = 49.6019, longitude = 6.0689)

        assertEquals(1, pois.size)
        val poi = pois.first()
        assertTrue(poi.id.startsWith("chargy-"))
        assertTrue(poi.name.contains("ACL- Automobile Club du Luxembourg"))
        assertEquals("Chargy", poi.brand)
        assertEquals("Chargy", poi.operator)
        assertEquals("Chargy", poi.source)
        assertEquals(true, poi.isElectric)
        assertEquals(22.0, poi.powerKw)
        assertEquals(2, poi.chargePointCount)
        assertEquals(2, poi.irveDetails?.availableConnectors)
        assertEquals(2, poi.irveDetails?.totalConnectors)
    }

    @Test
    fun getGasStations_returnsEmptyForCoordinatesOutsideLuxembourg() = runBlocking {
        val provider = buildProvider(SAMPLE_KML, apiKey = "test-key")
        // Paris coordinates (outside Luxembourg)
        val pois = provider.getGasStations(latitude = 48.8566, longitude = 2.3522)
        assertTrue(pois.isEmpty())
    }

    companion object {
        private val SAMPLE_KML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
            <Document>
              <Placemark>
                <name>Chargy Ok - ACL- Automobile Club du Luxembourg</name>
                <address>route de Longwy 72, 8080 Bertrange Luxembourg</address>
                <description>&lt;span&gt;&lt;b&gt;2&lt;/b&gt; connectors with 22kW and Type 2 connector&lt;span&gt;&lt;br/&gt;&lt;span&gt;&lt;b&gt;2&lt;/b&gt; available connectors&lt;span&gt;&lt;br/&gt;&lt;span&gt;&lt;b&gt;0&lt;/b&gt; occupied connectors&lt;span&gt;&lt;br/&gt;</description>
                <styleUrl>#AVAILABLE</styleUrl>
                <ExtendedData>
                  <Data name="CPnum"><value>2</value></Data>
                  <Data name="chargingdevice">
                    <value>{"id":440224,"name":"CP5922","numberOfConnectors":2,"connectors":[{"id":491990,"name":"CP5922 - 1","maxchspeed":22.0,"connector":1,"description":"AVAILABLE"},{"id":491991,"name":"CP5922 - 2","maxchspeed":22.0,"connector":2,"description":"AVAILABLE"}]}</value>
                  </Data>
                </ExtendedData>
                <Point>
                  <coordinates>6.0689301,49.6019439</coordinates>
                </Point>
              </Placemark>
            </Document>
            </kml>
        """.trimIndent()
    }
}
