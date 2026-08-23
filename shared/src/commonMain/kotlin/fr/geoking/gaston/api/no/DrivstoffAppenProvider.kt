package fr.geoking.gaston.api.no

import fr.geoking.gaston.poi.radiusKmFromMapViewport
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Norway DrivstoffAppen public API (backend.drivstoffapp.no).
 *
 * Uses: GET /stations/fuel/nearby?lat=..&lng=..&radius=..&limit=..
 * OpenAPI: https://backend.drivstoffapp.no/openapi.json
 */
class DrivstoffAppenProvider(
    private val client: HttpClient,
    /** Country name used by backend `countries=` filter (e.g. "Norway", "Sweden"). */
    private val country: String = "Norway",
    /** ISO-2 suffix for display in addresses (e.g. "NO", "SE"). */
    private val countryIso2: String = "NO",
    private val radiusKm: Int = 10,
    private val limit: Int = 100,
) : PoiProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let { radiusKmFromMapViewport(latitude, longitude, it).coerceIn(1, 100) }
            ?: radiusKm

        val effectiveLimit = limit.coerceAtMost(100)
        val url =
            "https://backend.drivstoffapp.no/stations/fuel/nearby?lat=$latitude&lng=$longitude&radius=$effectiveRadiusKm&limit=$effectiveLimit&sort_by=distance&countries=$country"
        val body = try {
            client.get(url).bodyAsText()
        } catch (_: Exception) {
            return emptyList()
        }
        if (body.isBlank() || body.trim() == "[]") return emptyList()
        val stations = try {
            json.decodeFromString<List<FuelStation>>(body)
        } catch (e: Exception) {
            log.w(e) { "DrivstoffAppen JSON decode failed (body ${body.length} chars)" }
            return emptyList()
        }

        return withContext(Dispatchers.Default) {
            stations.mapNotNull { it.toPoiOrNull() }
        }
    }

    private fun FuelStation.toPoiOrNull(): Poi? {
        val loc = location ?: return null
        val lat = loc.lat ?: loc.latitude ?: return null
        val lon = loc.lng ?: loc.longitude ?: return null
        val updatedAt = prices?.lastUpdated

        val fuelPrices = buildList {
            prices?.gasoline95?.let { add(FuelPrice("SP95", it, updatedAt = updatedAt)) }
            prices?.gasoline98?.let { add(FuelPrice("SP98", it, updatedAt = updatedAt)) }
            prices?.diesel?.let { add(FuelPrice("Diesel", it, updatedAt = updatedAt)) }
            prices?.fd?.let { add(FuelPrice("Farget Diesel (FD)", it, updatedAt = updatedAt)) }
            prices?.hvo100?.let { add(FuelPrice("HVO 100", it, updatedAt = updatedAt)) }
        }.ifEmpty { null }

        return Poi(
            id = "drivstoffappen:$id",
            name = name,
            address = buildString {
                if (!street.isNullOrBlank()) append(street)
                if (!city.isNullOrBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(city)
                }
                if (!zip.isNullOrBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(zip)
                }
                append(" ").append(countryIso2)
            },
            latitude = lat,
            longitude = lon,
            brand = brand,
            poiCategory = PoiCategory.Gas,
            fuelPrices = fuelPrices,
            source = "DrivstoffAppen / bensinpriser.nu ($country)"
        )
    }
}

@Serializable
private data class FuelStation(
    val id: String,
    val name: String,
    val brand: String? = null,
    val street: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val location: StationLocation? = null,
    val prices: FuelPrices? = null,
)

@Serializable
private data class StationLocation(
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lng") val lng: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
private data class FuelPrices(
    @SerialName("gasoline_95_price") val gasoline95: Double? = null,
    @SerialName("gasoline_98_price") val gasoline98: Double? = null,
    @SerialName("diesel_price") val diesel: Double? = null,
    @SerialName("fd_price") val fd: Double? = null,
    @SerialName("hvo100_price") val hvo100: Double? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
)

