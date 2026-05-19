package fr.geoking.gaston.api.australia

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetrolSpyProvider(
    client: HttpClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 80,
) : PoiProvider {

    private val petrolSpyClient = PetrolSpyClient(client)

    private val fuelNameMap = mapOf(
        "U91" to "SP91",
        "E10" to "E10",
        "P95" to "SP95",
        "P98" to "SP98",
        "DSL" to "Gazole",
        "PDL" to "Gazole Premium",
        "LPG" to "GPL",
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?,
    ): List<Poi> {
        if (latitude < -44.0 || latitude > -10.0 || longitude < 112.0 || longitude > 154.0) {
            return emptyList()
        }
        val bbox = petrolSpyClient.boundingBox(latitude, longitude, radiusKm.toDouble())
        val stations = try {
            petrolSpyClient.fetchStationsInBox(bbox.latMin, bbox.lonMin, bbox.latMax, bbox.lonMax)
        } catch (_: Exception) {
            emptyList()
        }

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { s ->
                    s.country == null || s.country.equals("AU", ignoreCase = true)
                }
                .filter { s ->
                    haversineKm(latitude, longitude, s.location.y, s.location.x) <= radiusKm
                }
                .map { s ->
                    val prices = s.prices?.mapNotNull { (key, value) ->
                        val name = fuelNameMap[key] ?: key
                        val amount = value.amount
                        if (amount <= 0) null else FuelPrice(name, amount / 100.0)
                    }.orEmpty()
                    Poi(
                        id = "petrolspy:${s.id}",
                        name = s.name.trim(),
                        address = s.address?.trim() ?: "",
                        latitude = s.location.y,
                        longitude = s.location.x,
                        brand = s.brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "PetrolSpy (Australia)",
                    )
                }
                .take(limit)
                .toList()
        }
    }
}
