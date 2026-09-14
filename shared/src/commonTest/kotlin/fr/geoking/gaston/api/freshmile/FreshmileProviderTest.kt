package fr.geoking.gaston.api.freshmile

import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FreshmileProviderTest {

    private val sampleMapLocationsJson = """
    {
      "type": "FeatureCollection",
      "bbox": [2.2, 48.8, 2.4, 48.9],
      "features": [
        {
          "type": "Feature",
          "geometry": {
            "type": "Point",
            "coordinates": [2.363958, 48.853573]
          },
          "id": "FRV75PX04094",
          "properties": {
            "location_id": 199034,
            "location_count": 1,
            "best_power_category": "normal",
            "is_available": true,
            "is_open": true,
            "total_evses": 5,
            "status": 10
          }
        },
        {
          "type": "Feature",
          "geometry": {
            "type": "Point",
            "coordinates": [2.376323, 48.805832]
          },
          "id": "FRHPCNF0723800032",
          "properties": {
            "location_id": 379529,
            "location_count": 1,
            "best_power_category": "superfast",
            "is_available": false,
            "is_open": true,
            "total_evses": 10,
            "status": 40
          }
        }
      ]
    }
    """.trimIndent()

    private val sampleLocationDetailJson = """
    {
      "data": {
        "id": 199034,
        "ref": "FRV75PX04094",
        "name": "Paris | Rue Neuve Saint-Pierre 2",
        "is_available": true,
        "is_open": true,
        "coordinates": {
          "latitude": 48.853573,
          "longitude": 2.363958
        },
        "address": {
          "fullname": "2 Rue Neuve Saint-Pierre",
          "city": "Paris",
          "postal_code": "75004",
          "country": "FRA"
        },
        "evses": [
          {
            "id": 238960,
            "status": "AVAILABLE",
            "is_available": true,
            "connectors": [
              {
                "id": 267670,
                "power": 22.0,
                "standard": "IEC_62196_T2"
              },
              {
                "id": 267671,
                "power": 150.0,
                "standard": "IEC_62196_T2_COMBO"
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

    private fun createMockClient(onRequest: ((io.ktor.client.request.HttpRequestData) -> Unit)? = null): FreshmileClient {
        val mockEngine = MockEngine { request ->
            onRequest?.invoke(request)
            val path = request.url.encodedPath
            val jsonContent = if (path.contains("locations/")) sampleLocationDetailJson else sampleMapLocationsJson
            respond(
                content = jsonContent,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine)
        return FreshmileClient(httpClient)
    }

    @Test
    fun getMapLocations_requestsBboxAsSingleCommaSeparatedString() = runBlocking {
        var capturedBbox: String? = null
        var capturedZoom: String? = null
        val client = createMockClient { request ->
            if (request.url.encodedPath.contains("map-locations")) {
                capturedBbox = request.url.parameters["bbox"]
                capturedZoom = request.url.parameters["zoom"]
            }
        }

        client.getMapLocations(5.84, 48.92, 6.46, 49.49, zoom = 11)

        assertEquals("5.84,48.92,6.46,49.49", capturedBbox)
        assertEquals("11", capturedZoom)
    }

    @Test
    fun getGasStations_returnsParsedFreshmileStations() = runBlocking {
        val client = createMockClient()
        val provider = FreshmileProvider(client, radiusKm = 20)

        val viewport = MapViewport(
            zoom = 14f,
            mapWidthPx = 1000,
            mapHeightPx = 1000,
            minLat = 48.80,
            maxLat = 48.90,
            minLng = 2.20,
            maxLng = 2.40
        )

        val pois = provider.getGasStations(48.85, 2.35, viewport = viewport)

        assertEquals(2, pois.size)

        val poi1 = pois.first { it.id == "freshmile_199034" }
        assertEquals("FRV75PX04094", poi1.name)
        assertEquals("freshmile", poi1.brand)
        assertTrue(poi1.isElectric)
        assertEquals(PoiCategory.Irve, poi1.poiCategory)
        assertEquals(22.0, poi1.powerKw)
        assertEquals(5, poi1.chargePointCount)
        val irve1 = poi1.irveDetails
        assertNotNull(irve1)
        assertEquals(5, irve1.availableConnectors)
        assertEquals(5, irve1.totalConnectors)

        val poi2 = pois.first { it.id == "freshmile_379529" }
        assertEquals("FRHPCNF0723800032", poi2.name)
        assertEquals(150.0, poi2.powerKw)
        assertEquals(10, poi2.chargePointCount)
        val irve2 = poi2.irveDetails
        assertNotNull(irve2)
        assertEquals(0, irve2.availableConnectors)
        assertEquals(10, irve2.totalConnectors)
    }

    @Test
    fun getGasStations_parsesNumericBestPowerCategory() = runBlocking {
        val numericPowerJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [2.35, 48.85] },
              "id": "STATION_NUMERIC",
              "properties": {
                "location_id": 999,
                "best_power_category": "50kW",
                "is_available": true,
                "total_evses": 2
              }
            }
          ]
        }
        """.trimIndent()

        val mockEngine = MockEngine { respond(numericPowerJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = FreshmileClient(HttpClient(mockEngine))
        val provider = FreshmileProvider(client)

        val pois = provider.getGasStations(48.85, 2.35)
        assertEquals(1, pois.size)
        assertEquals(50.0, pois.first().powerKw)
    }

    @Test
    fun getLocationDetail_parsesLocationDetailsCorrectly() = runBlocking {
        val client = createMockClient()
        val detail = client.getLocationDetail(199034)

        assertNotNull(detail)
        assertEquals(199034L, detail.id)
        assertEquals("Paris | Rue Neuve Saint-Pierre 2", detail.name)
        assertEquals("2 Rue Neuve Saint-Pierre", detail.address?.fullname)
        assertEquals("Paris", detail.address?.city)
        assertEquals(1, detail.evses?.size)
        assertEquals(2, detail.evses?.firstOrNull()?.connectors?.size)
    }

    @Test
    fun mapFreshmileStandard_mapsStandardsCorrectly() {
        assertEquals("type_2", mapFreshmileStandard("IEC_62196_T2"))
        assertEquals("type_2", mapFreshmileStandard("Type 2"))
        assertEquals("combo_ccs", mapFreshmileStandard("IEC_62196_T2_COMBO"))
        assertEquals("combo_ccs", mapFreshmileStandard("CCS 2"))
        assertEquals("chademo", mapFreshmileStandard("CHAdeMO"))
        assertEquals("ef", mapFreshmileStandard("DOMESTIC_F"))
        assertEquals("tesla_s", mapFreshmileStandard("TESLA"))
    }
}
