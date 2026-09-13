package fr.geoking.gaston.api.atlante

import fr.geoking.gaston.poi.AbstractPoiProvider
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderRules
import fr.geoking.gaston.poi.radiusKmFromMapViewport
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [PoiProvider] backed by Atlante's geodata endpoint.
 *
 * Supported countries include IT, FR, ES, PT where Atlante operates charging hubs.
 */
class AtlanteProvider(
    private val client: AtlanteClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 100
) : AbstractPoiProvider() {

    override val usageRules: PoiProviderRules = PoiProviderRules(
        countries = setOf("IT", "FR", "ES", "PT")
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let { radiusKmFromMapViewport(latitude, longitude, it).toDouble().coerceIn(1.0, 100.0) }
            ?: radiusKm.toDouble()

        val stations = client.getStations()

        return stations
            .mapNotNull { station ->
                val lat = station.coordinates?.latitude
                    ?: station.latitude?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val lon = station.coordinates?.longitude
                    ?: station.longitude?.toDoubleOrNull()
                    ?: return@mapNotNull null

                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > effectiveRadiusKm) return@mapNotNull null

                station to dist
            }
            .sortedBy { it.second }
            .take(limit)
            .map { (station, _) ->
                val lat = station.coordinates?.latitude ?: station.latitude!!.toDouble()
                val lon = station.coordinates?.longitude ?: station.longitude!!.toDouble()

                val evses = station.evses.orEmpty()
                val connectors = evses.flatMap { it.connectors.orEmpty() }
                val connectorTypes = connectors.mapNotNull { it.standard?.let(::mapAtlanteStandard) }.toSet()

                val maxPowerKw = connectors
                    .mapNotNull { c ->
                        c.maxElectricPower?.let { if (it > 1000.0) it / 1000.0 else it }
                            ?: powerKwFromAmpsVolts(c)
                    }
                    .maxOrNull()

                val totalConnectors = connectors.size.takeIf { it > 0 } ?: evses.size

                val address = buildString {
                    if (!station.address.isNullOrBlank()) append(station.address)
                    if (!station.province.isNullOrBlank() || !station.region.isNullOrBlank()) {
                        val regionStr = listOfNotNull(station.province, station.region)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(", ")
                        if (regionStr.isNotBlank()) {
                            if (isNotEmpty()) append(", ")
                            append(regionStr)
                        }
                    }
                    if (!station.country.isNullOrBlank()) {
                        if (isNotEmpty()) append(" (").append(station.country).append(")")
                    }
                }.ifBlank { station.name ?: "Atlante" }

                Poi(
                    id = "atlante_${station.id}",
                    name = station.name?.ifBlank { null } ?: "Atlante",
                    address = address,
                    latitude = lat,
                    longitude = lon,
                    brand = "atlante",
                    isElectric = true,
                    poiCategory = PoiCategory.Irve,
                    powerKw = maxPowerKw,
                    operator = "Atlante",
                    chargePointCount = totalConnectors.takeIf { it > 0 },
                    irveDetails = IrveDetails(
                        connectorTypes = connectorTypes,
                        totalConnectors = totalConnectors.takeIf { it > 0 }
                    ),
                    source = "Atlante"
                )
            }
    }

    override suspend fun clearCache() {
        client.clearCache()
    }

    private fun powerKwFromAmpsVolts(connector: AtlanteConnectorDto): Double? {
        val v = connector.maxVoltage ?: return null
        val a = connector.maxAmperage ?: return null
        return (v * a) / 1000.0
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

internal fun mapAtlanteStandard(standard: String): String = when (standard.uppercase().trim()) {
    "TYPE 2", "TYPE2", "IEC_62196_T2" -> "type_2"
    "CCS", "CCS 2", "CCS2", "COMBO_CCS", "IEC_62196_T2_COMBO" -> "combo_ccs"
    "CHADEMO" -> "chademo"
    "TESLA" -> "tesla_s"
    else -> standard.lowercase().replace(" ", "_")
}
