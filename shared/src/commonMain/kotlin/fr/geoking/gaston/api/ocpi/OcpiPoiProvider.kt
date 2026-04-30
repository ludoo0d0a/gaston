package fr.geoking.julius.api.ocpi

import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.IrveDetails

/**
 * [PoiProvider] implementation for OCPI-compliant CPOs (Ionity, Fastned, etc.).
 * Uses [OcpiClient] to fetch locations and maps them to [Poi].
 */
class OcpiPoiProvider(
    private val client: OcpiClient,
    private val providerName: String,
    private val radiusKm: Int = 10
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val locations = client.getLocations(latitude, longitude, radiusKm)

        // Try to fetch all tariffs once to avoid too many individual calls
        val allTariffs = try {
            client.getTariffs().associateBy { it.id }
        } catch (e: Exception) {
            emptyMap()
        }

        return locations.map { loc ->
            val lat = loc.coordinates.latitude.toDoubleOrNull() ?: 0.0
            val lon = loc.coordinates.longitude.toDoubleOrNull() ?: 0.0

            val allConnectors = loc.evses.flatMap { it.connectors }
            val connectorTypes = allConnectors.mapNotNull { mapConnectorStandard(it.standard) }.toSet()

            val maxPower = allConnectors.mapNotNull { it.max_electric_power }.maxOfOrNull { it }?.toDouble()?.let { it / 1000.0 }
                ?: allConnectors.map { (it.max_voltage * it.max_amperage).toDouble() / 1000.0 }.maxOfOrNull { it }

            val available = loc.evses.count { it.status == OcpiStatus.AVAILABLE }
            val total = loc.evses.size

            // Resolve tarification from the first available tariff_id in connectors
            val tariffId = allConnectors.firstOrNull { it.tariff_ids.isNotEmpty() }?.tariff_ids?.firstOrNull()
            val tariffDescription = tariffId?.let { id ->
                allTariffs[id]?.let { OcpiTariffParser.formatTariff(it) }
            }

            val metadata = mutableMapOf<String, String>()
            metadata["ID"] = loc.id
            loc.operator?.website?.let { metadata["Website"] = it }
            metadata["City"] = loc.city
            loc.postal_code?.let { metadata["Postcode"] = it }

            Poi(
                id = loc.id,
                name = loc.name ?: providerName,
                address = loc.address,
                latitude = lat,
                longitude = lon,
                brand = providerName,
                isElectric = true,
                powerKw = maxPower,
                operator = loc.operator?.name ?: providerName,
                chargePointCount = total,
                irveDetails = IrveDetails(
                    connectorTypes = connectorTypes,
                    availableConnectors = available,
                    totalConnectors = total,
                    tarification = tariffDescription
                ),
                source = providerName,
                metadata = metadata
            )
        }
    }

    private fun mapConnectorStandard(standard: String): String? = when (standard) {
        "IEC_62196_T2" -> "type_2"
        "IEC_62196_T2_COMBO" -> "combo_ccs"
        "CHADEMO" -> "chademo"
        "DOMESTIC_F" -> "ef"
        "TESLA_S" -> "type_2"
        "TESLA_R" -> "combo_ccs"
        else -> "autre"
    }
}
