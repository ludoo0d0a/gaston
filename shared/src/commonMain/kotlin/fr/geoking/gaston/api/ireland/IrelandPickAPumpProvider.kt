package fr.geoking.gaston.api.ireland

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IrelandPickAPumpProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val papClient = IrelandPickAPumpClient(client)

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = try {
            papClient.fetchNearbyStations(latitude, longitude, radiusKm)
        } catch (e: Exception) {
            emptyList()
        }

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { s -> s.country == "ROI" }
                .map { s ->
                    val prices = mutableListOf<FuelPrice>()
                    s.prices?.let { p ->
                        p.petrol?.takeIf { it > 0 }?.let { prices.add(FuelPrice("SP95", it / 100.0)) }
                        p.diesel?.takeIf { it > 0 }?.let { prices.add(FuelPrice("Gazole", it / 100.0)) }
                        p.petrolplus?.takeIf { it > 0 }?.let { prices.add(FuelPrice("SP98", it / 100.0)) }
                        p.dieselplus?.takeIf { it > 0 }?.let { prices.add(FuelPrice("Gazole Premium", it / 100.0)) }
                        p.hvo?.takeIf { it > 0 }?.let { prices.add(FuelPrice("HVO", it / 100.0)) }
                    }

                    Poi(
                        id = "pap:${s.id}",
                        name = s.stationName.trim(),
                        address = s.address ?: "",
                        latitude = s.coords.lat,
                        longitude = s.coords.lng,
                        brand = s.brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "Pick A Pump (Ireland)"
                    )
                }
                .take(limit)
                .toList()
        }
    }
}
