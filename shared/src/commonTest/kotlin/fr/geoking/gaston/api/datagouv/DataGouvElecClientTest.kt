package fr.geoking.gaston.api.datagouv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import io.ktor.client.HttpClient

class DataGouvElecClientTest {

    private val client = DataGouvElecClient(HttpClient())

    @Test
    fun parseStation_usesBrandWhenNameMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement("""
            {
                "id_station_itinerance": "FR*ABC*P123",
                "nom_enseigne": "Tesla",
                "consolidated_latitude": 48.0,
                "consolidated_longitude": 2.0
            }
        """).jsonObject

        val station = client.parseStation(record)
        assertNotNull(station)
        assertEquals("Tesla", station.name)
    }

    @Test
    fun parseStation_usesGenericFallbackWhenNameAndBrandMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement("""
            {
                "id_station_itinerance": "FR*XYZ*P456",
                "consolidated_latitude": 45.0,
                "consolidated_longitude": 4.0
            }
        """).jsonObject

        val station = client.parseStation(record)
        assertNotNull(station)
        assertEquals("Station", station.name)
    }
}
