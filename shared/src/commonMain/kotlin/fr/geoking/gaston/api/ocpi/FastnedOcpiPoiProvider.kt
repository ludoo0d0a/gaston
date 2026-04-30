package fr.geoking.julius.api.ocpi

import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.IrveDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/**
 * Specialized [PoiProvider] for Fastned using OCPI.
 * Fetches both locations and tariffs to enrich the POI data with pricing information.
 */
class FastnedOcpiPoiProvider(
    private val client: OcpiClient,
    private val radiusKm: Int = 10
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> = supervisorScope {
        val locationsDeferred = async { client.getLocations(latitude, longitude, radiusKm) }
        val tariffsDeferred = async { client.getTariffs() }

        val locations = try { locationsDeferred.await() } catch (e: Exception) { emptyList() }
        val tariffs = try { tariffsDeferred.await() } catch (e: Exception) { emptyList() }

        val tariffMap = tariffs.associateBy { it.id }

        locations.map { loc ->
            val lat = loc.coordinates.latitude.toDoubleOrNull() ?: 0.0
            val lon = loc.coordinates.longitude.toDoubleOrNull() ?: 0.0

            val allConnectors = loc.evses.flatMap { it.connectors }
            val connectorTypes = allConnectors.mapNotNull { mapConnectorStandard(it.standard) }.toSet()

            val maxPower = allConnectors.mapNotNull { it.max_electric_power }.maxOfOrNull { it }?.toDouble()?.let { it / 1000.0 }
                ?: allConnectors.map { (it.max_voltage * it.max_amperage).toDouble() / 1000.0 }.maxOfOrNull { it }

            val available = loc.evses.count { it.status == OcpiStatus.AVAILABLE }
            val total = loc.evses.size

            val metadata = mutableMapOf<String, String>()
            metadata["ID"] = loc.id
            loc.operator?.website?.let { metadata["Website"] = it }
            metadata["City"] = loc.city
            loc.postal_code?.let { metadata["Postcode"] = it }

            // Try to find a representative tariff from the connectors
            val tarification = allConnectors.flatMap { it.tariff_ids }
                .mapNotNull { tariffMap[it] }
                .firstOrNull()
                ?.let { OcpiTariffParser.formatTariff(it) }

            Poi(
                id = loc.id,
                name = loc.name ?: "Fastned",
                address = loc.address,
                latitude = lat,
                longitude = lon,
                brand = "Fastned",
                isElectric = true,
                powerKw = maxPower,
                operator = loc.operator?.name ?: "Fastned",
                chargePointCount = total,
                irveDetails = IrveDetails(
                    connectorTypes = connectorTypes,
                    availableConnectors = available,
                    totalConnectors = total,
                    tarification = tarification
                ),
                source = "Fastned",
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
