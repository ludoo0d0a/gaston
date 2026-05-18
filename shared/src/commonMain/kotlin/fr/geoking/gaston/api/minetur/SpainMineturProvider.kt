package fr.geoking.gaston.api.minetur

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * [PoiProvider] for Spanish fuel prices using the free Minetur REST API.
 * API provides all stations in Spain; we filter by distance in-memory.
 * Base URL: https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/
 */
class SpainMineturProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 50
) : PoiProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedStations: List<MineturStation>? = null

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = getOrFetchStations()
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .mapNotNull { it.toPoi(latitude, longitude) }
                .filter { it.distance <= radiusKm }
                .sortedBy { it.distance }
                .take(limit)
                .map { it.poi }
                .toList()
        }
    }

    private suspend fun getOrFetchStations(): List<MineturStation> = mutex.withLock {
        cachedStations?.let { return@withLock it }

        // Spain is large, and this API returns ALL stations if we call the main endpoint.
        // To avoid repeated 5MB downloads, we cache the result in-memory for the session.
        val url =
            "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/"
        var response = try {
            client.get(url)
        } catch (e: Exception) {
            return@withLock emptyList()
        }
        var attempts = 0
        while (response.status.value == 503 && attempts < 3) {
            kotlinx.coroutines.delay(5_000)
            attempts++
            response = try {
                client.get(url)
            } catch (e: Exception) {
                return@withLock emptyList()
            }
        }

        val body = response.bodyAsText()
        if (response.status.value != 200) return@withLock emptyList()

        val root = withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<MineturResponse>(body)
            } catch (e: Exception) {
                null
            }
        }

        val stations = root?.listaEESSPrecio ?: emptyList()
        cachedStations = stations
        return@withLock stations
    }

    private data class StationWithDistance(val poi: Poi, val distance: Double)

    private fun MineturStation.toPoi(centerLat: Double, centerLon: Double): StationWithDistance? {
        val lat = latitud?.replace(",", ".")?.toDoubleOrNull() ?: return null
        val lon = longitudWGS84?.replace(",", ".")?.toDoubleOrNull() ?: return null

        val dLat = (lat - centerLat) * 111.0
        val dLon = (lon - centerLon) * 111.0 * cos(centerLat * PI / 180.0)
        val dist = sqrt(dLat * dLat + dLon * dLon)

        val prices = mutableListOf<FuelPrice>()
        precioGasolina95E5?.replace(",", ".")?.toDoubleOrNull()?.let {
            prices.add(FuelPrice("SP95 E5", it))
        }
        precioGasoilA?.replace(",", ".")?.toDoubleOrNull()?.let {
            prices.add(FuelPrice("Gazole", it))
        }
        precioGasolina98E5?.replace(",", ".")?.toDoubleOrNull()?.let {
            prices.add(FuelPrice("SP98", it))
        }

        val poi = Poi(
            id = "minetur:$ideess",
            name = rotulo ?: "Gas Station",
            address = direccion ?: "",
            latitude = lat,
            longitude = lon,
            brand = rotulo,
            poiCategory = PoiCategory.Gas,
            fuelPrices = prices.ifEmpty { null },
            source = "Minetur (Spain)"
        )
        return StationWithDistance(poi, dist)
    }
}

@Serializable
data class MineturResponse(
    @SerialName("ListaEESSPrecio") val listaEESSPrecio: List<MineturStation>? = null
)

@Serializable
data class MineturStation(
    @SerialName("IDEESS") val ideess: String? = null,
    @SerialName("Rotulo") val rotulo: String? = null,
    @SerialName("Direccion") val direccion: String? = null,
    @SerialName("Latitud") val latitud: String? = null,
    @SerialName("Longitud (WGS84)") val longitudWGS84: String? = null,
    @SerialName("Precio Gasolina 95 E5") val precioGasolina95E5: String? = null,
    @SerialName("Precio Gasóleo A") val precioGasoilA: String? = null,
    @SerialName("Precio Gasolina 98 E5") val precioGasolina98E5: String? = null
)
