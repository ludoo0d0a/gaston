package fr.geoking.gaston.api.econtrol

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.AbstractPoiProvider
import fr.geoking.gaston.poi.PoiProviderRules
import fr.geoking.gaston.shared.logging.log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [PoiProvider] for Austrian fuel prices using the free E-Control API.
 * Base URL: https://api.e-control.at/sprit/1.0/
 */
class AustriaEControlProvider(
    private val client: HttpClient,
    private val limit: Int = 50
) : AbstractPoiProvider() {

    override val usageRules = PoiProviderRules(countries = setOf("AT"))

    private val json = Json { ignoreUnknownKeys = true }

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        if (!shouldQuery(latitude, longitude, viewport)) return emptyList()
        // Fetch Diesel as representative for location search
        val url = "https://api.e-control.at/sprit/1.0/search/gas-stations/by-address?latitude=$latitude&longitude=$longitude&fuelType=DIE&includeClosed=true"

        val response = try {
            client.get(url)
        } catch (e: Exception) {
            log.w(e) { "[E-Control] Request failed" }
            return emptyList()
        }

        val body = response.bodyAsText()
        if (response.status.value != 200) return emptyList()

        val stations = withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<List<EControlStation>>(body)
            } catch (e: Exception) {
                log.w(e) { "[E-Control] Parsing failed" }
                emptyList()
            }
        }

        return stations.take(limit).map { it.toPoi() }
    }

    private fun EControlStation.toPoi(): Poi {
        val prices = mutableListOf<FuelPrice>()
        prices.addAll(this.prices?.mapNotNull { p ->
            val name = when (p.fuelType) {
                "DIE" -> "Gazole"
                "SUP" -> "SP95 E5"
                "GAS" -> "GPLc"
                else -> p.label ?: "Fuel"
            }
            p.amount?.let { FuelPrice(name, it) }
        } ?: emptyList())

        return Poi(
            id = "econtrol:$id",
            name = name ?: "Gas Station",
            isClosed = open == false,
            address = listOfNotNull(location?.address, location?.postalCode, location?.city).joinToString(" "),
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            brand = name,
            poiCategory = PoiCategory.Gas,
            fuelPrices = prices.ifEmpty { null },
            source = "E-Control (Austria)"
        )
    }
}

@Serializable
data class EControlStation(
    val id: Int? = null,
    val name: String? = null,
    val open: Boolean? = null,
    val location: EControlLocation? = null,
    val prices: List<EControlPrice>? = null
)

@Serializable
data class EControlLocation(
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class EControlPrice(
    val fuelType: String? = null,
    val amount: Double? = null,
    val label: String? = null
)
