package fr.geoking.gaston.api.atlante

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for Atlante EV charging stations dataset endpoint (`https://map.atlante.energy/geodata.json`).
 */
class AtlanteClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://map.atlante.energy/geodata.json",
    private val cacheMaxAgeMs: Long = 30 * 60_000L
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val mutex = Mutex()
    private var cachedStations: List<AtlanteStationDto> = emptyList()
    private var cacheTimestampMs: Long = 0L

    suspend fun getStations(): List<AtlanteStationDto> = mutex.withLock {
        val now = currentTimeMs()
        if (cachedStations.isNotEmpty() && (now - cacheTimestampMs) < cacheMaxAgeMs) {
            return cachedStations
        }

        val responseText = httpClient.get(baseUrl).bodyAsText()
        val stations = json.decodeFromString<List<AtlanteStationDto>>(responseText)

        cachedStations = stations
        cacheTimestampMs = now
        stations
    }

    suspend fun clearCache() = mutex.withLock {
        cachedStations = emptyList()
        cacheTimestampMs = 0L
    }
}

@Serializable
data class AtlanteStationDto(
    val id: String,
    val name: String? = null,
    @SerialName("site_status") val siteStatus: String? = null,
    @SerialName("commissioning_date_forecast") val commissioningDateForecast: String? = null,
    val address: String? = null,
    val province: String? = null,
    val region: String? = null,
    val country: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val coordinates: AtlanteCoordinatesDto? = null,
    val evses: List<AtlanteEvseDto>? = null
)

@Serializable
data class AtlanteCoordinatesDto(
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class AtlanteEvseDto(
    val connectors: List<AtlanteConnectorDto>? = null
)

@Serializable
data class AtlanteConnectorDto(
    val standard: String? = null,
    val format: String? = null,
    @SerialName("power_type") val powerType: String? = null,
    @SerialName("max_electric_power") val maxElectricPower: Double? = null,
    @SerialName("max_voltage") val maxVoltage: Double? = null,
    @SerialName("max_amperage") val maxAmperage: Double? = null
)

private fun currentTimeMs(): Long = System.currentTimeMillis()
