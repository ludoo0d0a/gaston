package fr.geoking.gaston.api.ecomovement

import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.PoiCategory
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

class EcoMovementOcpiProviderTest {

    @Test
    fun getGasStations_filtersByDistance_andMapsToPoi() = runBlocking {
        val engine = MockEngine { request ->
            respond(
                content = """
                {
                  "data": [
                    {
                      "id": "LOC-1",
                      "name": "Close Location",
                      "address": "1 Main St",
                      "city": "Paris",
                      "coordinates": { "latitude": "48.8566", "longitude": "2.3522" },
                      "evses": [
                        { "uid": "1", "status": "AVAILABLE", "connectors": [{ "standard": "IEC_62196_T2_COMBO", "max_electric_power": 50000 }] },
                        { "uid": "2", "status": "CHARGING", "connectors": [{ "standard": "IEC_62196_T2_COMBO", "max_electric_power": 50000 }] }
                      ]
                    },
                    {
                      "id": "LOC-2",
                      "name": "Far Location",
                      "coordinates": { "latitude": "50.0", "longitude": "5.0" }
                    }
                  ],
                  "status_code": 1000
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = EcoMovementOcpiClient(HttpClient(engine), "key")
        val provider = EcoMovementOcpiProvider(client, radiusKm = 10)

        // Paris center
        val pois = provider.getGasStations(48.8566, 2.3522, null)

        assertEquals(1, pois.size)
        val poi = pois.first()
        assertEquals("ecomovement-LOC-1", poi.id)
        assertEquals("Close Location", poi.name)
        assertEquals(PoiCategory.Irve, poi.poiCategory)
        assertEquals(50.0, poi.powerKw)
        assertTrue(poi.irveDetails?.connectorTypes?.contains("combo_ccs") == true)
        assertEquals(1, poi.irveDetails?.availableConnectors)
        assertEquals(2, poi.irveDetails?.totalConnectors)
    }

    @Test
    fun getGasStations_restrictsToMapViewportBoundary_andCapsAtLimit() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                {
                  "data": [
                    {"id":"IN","name":"Inside","coordinates":{"latitude":"48.86","longitude":"2.35"},
                     "evses":[{"uid":"1","status":"AVAILABLE","connectors":[{"standard":"IEC_62196_T2","max_electric_power":11000}]}]},
                    {"id":"OUT","name":"Outside corner","coordinates":{"latitude":"48.90","longitude":"2.40"},
                     "evses":[{"uid":"2","status":"AVAILABLE","connectors":[{"standard":"IEC_62196_T2","max_electric_power":11000}]}]}
                  ],
                  "status_code": 1000
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val provider = EcoMovementOcpiProvider(
            EcoMovementOcpiClient(HttpClient(engine), "key"),
            radiusKm = 50,
            limit = 100,
        )
        val viewport = MapViewport(
            zoom = 14f,
            mapWidthPx = 800,
            mapHeightPx = 600,
            minLat = 48.85,
            maxLat = 48.87,
            minLng = 2.34,
            maxLng = 2.36,
        )
        val pois = provider.getGasStations(48.86, 2.35, viewport)
        assertEquals(1, pois.size)
        assertEquals("ecomovement-IN", pois.first().id)
    }
}
