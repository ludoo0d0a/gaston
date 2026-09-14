package fr.geoking.gaston.api.gireve

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.poi.AbstractPoiProvider
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderRules

/**
 * [PoiProvider] implementation for Gireve IRVE (France),
 * fetching static and dynamic EV charging station data via [GireveClient].
 */
class GireveProvider(
    private val client: GireveClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 300
) : AbstractPoiProvider() {

    override val usageRules: PoiProviderRules = PoiProviderRules(countries = setOf("FR"))

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val records = client.getAvailability(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit
        )

        if (records.isEmpty()) return emptyList()

        val byStation = records.groupBy { it.stationId ?: "${it.latitude}_${it.longitude}" }

        return byStation.map { (stationKey, pdcs) ->
            val first = pdcs.first()
            val total = pdcs.size
            val available = pdcs.count {
                client.mapStatus(it.etatPdc, it.occupationPdc) == AvailabilityStatus.Available
            }
            val pdcIds = pdcs.map { it.idPdcItinerance }.toSet()

            val name = first.stationName
                ?: first.enseigneName
                ?: first.operatorName
                ?: first.stationId
                ?: "Station IRVE Gireve"

            val brandName = first.operatorName ?: first.enseigneName ?: first.amenageurName
            val maxPowerKw = pdcs.mapNotNull { it.puissanceNominale }.maxOrNull()

            Poi(
                id = "gireve_$stationKey",
                name = name,
                brand = brandName,
                address = first.address ?: "France",
                latitude = first.latitude,
                longitude = first.longitude,
                isElectric = true,
                poiCategory = PoiCategory.Irve,
                powerKw = maxPowerKw,
                chargePointCount = total,
                irveDetails = IrveDetails(
                    availableConnectors = available,
                    totalConnectors = total,
                    pdcIds = pdcIds
                ),
                source = "Gireve"
            )
        }
    }
}
