package fr.geoking.gaston.api.fastned

import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [PoiProvider] backed by the Fastned UK Open Data API (OCPI 2.2.1).
 *
 * Because the dataset is UK-only and small (~100 stations), locations are fetched in full and
 * cached in-memory for [cacheMaxAgeMs]. Tariffs are fetched once per cache period and rendered
 * as a human-readable [IrveDetails.tarification] string.
 */
class FastnedOcpiProvider(
    private val client: FastnedOcpiClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100,
    private val cacheMaxAgeMs: Long = 30 * 60_000L
) : PoiProvider {

    /** UK bounding box (with margin). */
    private val ukBbox = object {
        val latMin = 49.5
        val latMax = 61.0
        val lonMin = -9.0
        val lonMax = 2.5
    }

    private var cachedLocations: List<FastnedOcpiLocation> = emptyList()
    private var cachedTariffText: String? = null
    private var cacheTimestampMs: Long = 0L

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        // Skip API calls when outside the UK.
        if (latitude < ukBbox.latMin || latitude > ukBbox.latMax ||
            longitude < ukBbox.lonMin || longitude > ukBbox.lonMax
        ) {
            return emptyList()
        }

        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

        ensureCachePopulated()

        val tarification = cachedTariffText

        return cachedLocations
            .mapNotNull { loc ->
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = loc.coordinates.longitude?.toDoubleOrNull() ?: return@mapNotNull null
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > effectiveRadiusKm) return@mapNotNull null
                loc to dist
            }
            .sortedBy { it.second }
            .take(limit)
            .map { (loc, _) ->
                val lat = loc.coordinates!!.latitude!!.toDouble()
                val lon = loc.coordinates.longitude!!.toDouble()
                val evses = loc.evses.orEmpty()
                val connectors = evses.flatMap { it.connectors.orEmpty() }
                val connectorTypes = connectors.mapNotNull { it.standard?.let(::mapOcpiStandard) }.toSet()
                val maxPowerKw = connectors
                    .mapNotNull { it.maxElectricPower }
                    .maxOrNull()
                    ?.let { it / 1000.0 }
                    ?: connectors.mapNotNull { powerKwFromAmpsVolts(it) }.maxOrNull()
                val address = buildString {
                    if (!loc.address.isNullOrBlank()) append(loc.address)
                    if (!loc.city.isNullOrBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(loc.city)
                    }
                    if (!loc.postalCode.isNullOrBlank()) {
                        if (isNotEmpty()) append(" ")
                        append(loc.postalCode)
                    }
                }.ifBlank { loc.city ?: "UK" }

                Poi(
                    id = "fastned-${loc.id}",
                    name = loc.name?.ifBlank { null } ?: "Fastned",
                    address = address,
                    latitude = lat,
                    longitude = lon,
                    brand = "fastned",
                    isElectric = true,
                    poiCategory = PoiCategory.Irve,
                    powerKw = maxPowerKw,
                    operator = loc.operator?.name ?: "Fastned",
                    isOnHighway = false,
                    chargePointCount = evses.size.takeIf { it > 0 },
                    fuelPrices = null,
                    irveDetails = IrveDetails(
                        connectorTypes = connectorTypes,
                        tarification = tarification
                    ),
                    source = "Fastned"
                )
            }
    }

    override suspend fun clearCache() {
        cachedLocations = emptyList()
        cachedTariffText = null
        cacheTimestampMs = 0L
    }

    private suspend fun ensureCachePopulated() {
        val now = currentTimeMs()
        if (cachedLocations.isNotEmpty() && now - cacheTimestampMs < cacheMaxAgeMs) return

        cachedLocations = fetchAllLocations()
        cachedTariffText = runCatching { buildTariffText(fetchAllTariffs()) }.getOrNull()
        cacheTimestampMs = now
    }

    private suspend fun fetchAllLocations(): List<FastnedOcpiLocation> {
        val pageSize = 200
        val result = mutableListOf<FastnedOcpiLocation>()
        var offset = 0
        while (true) {
            val page = client.listLocations(limit = pageSize, offset = offset)
            result.addAll(page)
            if (page.size < pageSize) break
            offset += pageSize
        }
        return result
    }

    private suspend fun fetchAllTariffs(): List<FastnedOcpiTariff> {
        val pageSize = 100
        val result = mutableListOf<FastnedOcpiTariff>()
        var offset = 0
        while (true) {
            val page = client.listTariffs(limit = pageSize, offset = offset)
            result.addAll(page)
            if (page.size < pageSize) break
            offset += pageSize
        }
        return result
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildTariffText(tariffs: List<FastnedOcpiTariff>): String? {
        if (tariffs.isEmpty()) return null
        val parts = tariffs.flatMap { tariff ->
            val currency = tariff.currency ?: ""
            val symbol = currencySymbol(currency)
            tariff.elements.orEmpty().flatMap { element ->
                element.priceComponents.orEmpty().mapNotNull { pc ->
                    val price = pc.price ?: return@mapNotNull null
                    when (pc.type) {
                        "ENERGY" -> "$symbol${"%.2f".format(price)}/kWh"
                        "FLAT" -> "$symbol${"%.2f".format(price)} (flat)"
                        "TIME" -> "$symbol${"%.2f".format(price)}/min"
                        "PARKING_TIME" -> "$symbol${"%.2f".format(price)}/min parking"
                        else -> null
                    }
                }
            }
        }.distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" + ")
    }

    private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
        "GBP" -> "£"
        "EUR" -> "€"
        "USD" -> "$"
        "CHF" -> "CHF "
        else -> "$currency "
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** kW from voltage × amperage when [maxElectricPower] is absent. */
    private fun powerKwFromAmpsVolts(connector: FastnedOcpiConnector): Double? {
        val v = connector.maxVoltage ?: return null
        val a = connector.maxAmperage ?: return null
        return (v * a) / 1000.0
    }
}

/**
 * Maps an OCPI 2.2.1 connector standard string to the internal connector type ID used in
 * [IrveDetails.connectorTypes] and displayed via BrandHelper.connectorTypeLabel().
 */
internal fun mapOcpiStandard(standard: String): String = when (standard.uppercase()) {
    "IEC_62196_T2" -> "type_2"
    "IEC_62196_T2_COMBO" -> "combo_ccs"
    "CHADEMO" -> "chademo"
    "DOMESTIC_E", "DOMESTIC_F" -> "ef"
    "IEC_62196_T1" -> "type_1"
    "IEC_62196_T1_COMBO" -> "combo_ccs"
    "TESLA_S" -> "tesla_s"
    "TESLA_R" -> "tesla_r"
    else -> standard.lowercase()
}

/** Multiplatform clock millis (expect/actual pattern replaced by inline delegation). */
private fun currentTimeMs(): Long = System.currentTimeMillis()
