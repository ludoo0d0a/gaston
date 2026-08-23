package fr.geoking.gaston.api.dgeg

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.radiusKmFromMapViewport
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.shared.logging.log
import kotlin.math.PI
import kotlin.math.cos
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

/**
 * [PoiProvider] for Portugal using the DGEG (Direção-Geral de Energia e Geologia) JSON API.
 * Handles station-specific real-time prices for mainland Portugal.
 */
class PortugalDgegProvider(
    private val httpClient: HttpClient
) : PoiProvider {

    private var cache: List<Poi>? = null
    private var lastFetch: Long = 0
    private val cacheDurationMs = 3600_000 // 1 hour

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val allStations = getAllStations()

        if (viewport != null) {
            // Precise filtering based on viewport
            // the SelectorPoiProvider already applies its own distance filter based on viewport.
            // But we still want to return a sensible subset here for performance.
            val radiusKm = radiusKmFromMapViewport(latitude, longitude, viewport).toDouble()
            val latDeg = radiusKm / 111.0
            val lonDeg = radiusKm / (111.0 * cos(latitude * PI / 180.0).coerceAtLeast(0.01))

            return allStations.filter {
                it.latitude in (latitude - latDeg)..(latitude + latDeg) &&
                it.longitude in (longitude - lonDeg)..(longitude + lonDeg)
            }
        }

        // Filtering by proximity (simple bounding box for performance)
        val radiusDeg = 0.5 // ~55km (increased from 0.2 to avoid missing POIs)
        return allStations.filter {
            it.latitude in (latitude - radiusDeg)..(latitude + radiusDeg) &&
            it.longitude in (longitude - radiusDeg)..(longitude + radiusDeg)
        }
    }

    private suspend fun getAllStations(): List<Poi> {
        val now = currentTimeMillis()
        if (cache != null && now - lastFetch < cacheDurationMs) {
            return cache!!
        }

        return try {
            val fuelIds = listOf(2101, 2105, 3201, 3205, 3400, 3405, 1120).joinToString(",")
            val url = "https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos"
            val response: DgegEnvelope = httpClient.get(url) {
                header(HttpHeaders.UserAgent, "Gaston/1.0")
                parameter("idsTiposComb", fuelIds)
                parameter("qtdPorPagina", 15000)
                parameter("pagina", 1)
            }.body()

            // Keep implementation lightweight: network logs are captured elsewhere.

            val pois = response.resultado
                .groupBy { it.Id }
                .mapNotNull { (id, stations) ->
                    val first = stations.first()
                    var lat = first.Latitude
                    var lon = first.Longitude

                    if (lat == 0.0 || lon == 0.0) {
                        val fallback = first.Location?.split(Regex("[,\\s]+"))
                        if (fallback?.size == 2) {
                            lat = fallback[0].toDoubleOrNull() ?: 0.0
                            lon = fallback[1].toDoubleOrNull() ?: 0.0
                        }
                    }

                    if (lat == 0.0 || lon == 0.0) return@mapNotNull null

                    val prices = stations.mapNotNull { it.toFuelPrice() }

                    Poi(
                        id = "dgeg:$id",
                        name = first.Nome,
                        address = listOfNotNull(first.Morada, first.Localidade).joinToString(", "),
                        latitude = lat,
                        longitude = lon,
                        brand = first.Marca,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "DGEG (Portugal)"
                    )
                }
            cache = pois
            lastFetch = now
            pois
        } catch (e: Exception) {
            log.e(e) { "[PortugalDgegProvider] Failed to fetch stations" }
            cache ?: emptyList()
        }
    }

    override suspend fun clearCache() {
        cache = null
        lastFetch = 0
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()

    @Serializable
    private data class DgegEnvelope(
        val status: Boolean,
        val resultado: List<DgegStation>
    )

    @Serializable
    private data class DgegStation(
        val Id: Int,
        val Nome: String,
        val Preco: String? = null,
        val Combustivel: String? = null,
        val Combustive1: String? = null, // Fallback fuel type
        val Marca: String? = null,
        val Morada: String? = null,
        val Localidade: String? = null,
        val Latitude: Double,
        val Longitude: Double,
        val Location: String? = null // Fallback location "lat,lon"
    ) {
        fun toFuelPrice(): FuelPrice? {
            val priceString = Preco?.trim()?.split(" ")?.firstOrNull() ?: return null
            val priceValue = priceString.replace(",", ".").toDoubleOrNull() ?: return null

            val type = Combustivel ?: Combustive1
            val fuelType = when {
                type?.contains("Gasóleo simples", ignoreCase = true) == true -> "Gazole"
                type?.contains("Gasóleo especial", ignoreCase = true) == true -> "Gazole Premium"
                type?.contains("Gasóleo", ignoreCase = true) == true -> "Gazole"
                type?.contains("Gasolina simples 95", ignoreCase = true) == true -> "SP95"
                type?.contains("Gasolina especial 95", ignoreCase = true) == true -> "SP95"
                type?.contains("Gasolina 95", ignoreCase = true) == true -> "SP95"
                type?.contains("Gasolina 98", ignoreCase = true) == true -> "SP98"
                type?.contains("Gasolina especial 98", ignoreCase = true) == true -> "SP98"
                type?.contains("GPL Auto", ignoreCase = true) == true -> "GPLc"
                type?.contains("GPL", ignoreCase = true) == true -> "GPLc"
                else -> return null
            }

            return FuelPrice(fuelType, priceValue)
        }
    }
}
