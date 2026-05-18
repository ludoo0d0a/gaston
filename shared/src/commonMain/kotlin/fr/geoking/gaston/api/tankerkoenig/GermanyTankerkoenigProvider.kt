package fr.geoking.gaston.api.tankerkoenig

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.logging.log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [PoiProvider] for German fuel prices using the free Tankerkönig API (MTS-K).
 * API provides real-time prices for gas stations in Germany.
 * Base URL: https://creativecommons.tankerkoenig.de/json/list.php
 */
class GermanyTankerkoenigProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val radiusKm: Int = 10,
    private val limit: Int = 50
) : PoiProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val rad = radiusKm.coerceIn(1, 25)
        val url = "https://creativecommons.tankerkoenig.de/json/list.php?lat=$latitude&lng=$longitude&rad=$rad&sort=dist&type=all&apikey=$apiKey"

        val response = try {
            client.get(url)
        } catch (e: Exception) {
            log.w(e) { "[Tankerkoenig] Request failed" }
            return emptyList()
        }

        val body = response.bodyAsText()
        if (response.status.value != 200) return emptyList()

        val root = withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<TankerkoenigResponse>(body)
            } catch (e: Exception) {
                log.w(e) { "[Tankerkoenig] Parsing failed" }
                null
            }
        }

        if (root?.ok != true) return emptyList()

        return root.stations?.take(limit)?.map { it.toPoi() } ?: emptyList()
    }

    private fun TankerkoenigStation.toPoi(): Poi {
        val prices = mutableListOf<FuelPrice>()
        diesel?.let { prices.add(FuelPrice("Gazole", it)) }
        e5?.let { prices.add(FuelPrice("SP95 E5", it)) }
        e10?.let { prices.add(FuelPrice("SP95 E10", it)) }

        return Poi(
            id = "tankerkoenig:$id",
            name = name ?: "Gas Station",
            address = listOfNotNull(street, houseNumber, postCode, place).joinToString(" "),
            latitude = lat ?: 0.0,
            longitude = lng ?: 0.0,
            brand = brand,
            poiCategory = PoiCategory.Gas,
            fuelPrices = prices.ifEmpty { null },
            source = "Tankerkönig (Germany)"
        )
    }
}

@Serializable
data class TankerkoenigResponse(
    val ok: Boolean = false,
    val stations: List<TankerkoenigStation>? = null
)

@Serializable
data class TankerkoenigStation(
    val id: String? = null,
    val name: String? = null,
    val brand: String? = null,
    val street: String? = null,
    val place: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val diesel: Double? = null,
    val e5: Double? = null,
    val e10: Double? = null,
    val isOpen: Boolean? = null,
    val houseNumber: String? = null,
    val postCode: Int? = null
)
