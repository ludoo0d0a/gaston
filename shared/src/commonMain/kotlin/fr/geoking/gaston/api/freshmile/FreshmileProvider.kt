package fr.geoking.gaston.api.freshmile

import fr.geoking.gaston.poi.AbstractPoiProvider
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderRules
import fr.geoking.gaston.poi.radiusKmFromMapViewport

/**
 * [PoiProvider] backed by Freshmile EV charging API.
 * Restricted to European countries where Freshmile operates charging stations.
 */
class FreshmileProvider(
    private val client: FreshmileClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 150
) : AbstractPoiProvider() {

    override val usageRules: PoiProviderRules = PoiProviderRules(
        countries = setOf(
            "FR", "DE", "IT", "ES", "PT", "BE", "NL", "LU", "CH", "AT",
            "UK", "GB", "IE", "SE", "NO", "FI", "DK", "PL", "CZ", "SK",
            "HU", "RO", "BG", "GR", "HR", "SI", "EE", "LV", "LT", "AD",
            "MC", "SM", "VA", "LI", "MT", "CY"
        )
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        if (!shouldQuery(latitude, longitude, viewport)) {
            return emptyList()
        }

        val (minLat, maxLat, minLng, maxLng, zoom) = if (viewport?.minLat != null && viewport.maxLat != null && viewport.minLng != null && viewport.maxLng != null) {
            val z = viewport.zoom.toInt().coerceIn(10, 18)
            listOf(viewport.minLat, viewport.maxLat, viewport.minLng, viewport.maxLng, z.toDouble())
        } else {
            val effectiveRadius = viewport?.let { radiusKmFromMapViewport(latitude, longitude, it) } ?: radiusKm
            val latDelta = effectiveRadius / 111.0
            val lngDelta = effectiveRadius / (111.0 * kotlin.math.cos(latitude * Math.PI / 180.0).coerceAtLeast(0.01))
            val z = viewport?.zoom?.toInt()?.coerceIn(10, 18) ?: 14
            listOf(latitude - latDelta, latitude + latDelta, longitude - lngDelta, longitude + lngDelta, z.toDouble())
        }

        val mapResponse = try {
            client.getMapLocations(
                minLng = minLng,
                minLat = minLat,
                maxLng = maxLng,
                maxLat = maxLat,
                zoom = zoom.toInt()
            )
        } catch (_: Exception) {
            return emptyList()
        }

        val features = mapResponse.features.orEmpty()

        return features
            .mapNotNull { feature ->
                val coords = feature.geometry?.coordinates ?: return@mapNotNull null
                if (coords.size < 2) return@mapNotNull null
                val lon = coords[0]
                val lat = coords[1]

                val props = feature.properties
                val locId = props?.locationId
                val poiId = if (locId != null) "freshmile_$locId" else "freshmile_${feature.id ?: "${lat}_${lon}"}"

                val totalEvses = props?.totalEvses
                val isAvailable = props?.isAvailable == true
                val availableEvses = if (isAvailable) (totalEvses ?: 1) else 0

                val estimatedPowerKw = when (props?.bestPowerCategory?.lowercase()?.trim()) {
                    "normal" -> 22.0
                    "fast" -> 50.0
                    "superfast", "ultrafast", "ultra_fast" -> 150.0
                    else -> null
                }

                Poi(
                    id = poiId,
                    name = feature.id?.ifBlank { null } ?: "Freshmile Station",
                    address = "Freshmile Charge",
                    latitude = lat,
                    longitude = lon,
                    brand = "freshmile",
                    isElectric = true,
                    poiCategory = PoiCategory.Irve,
                    powerKw = estimatedPowerKw,
                    operator = "Freshmile",
                    chargePointCount = totalEvses,
                    irveDetails = IrveDetails(
                        availableConnectors = availableEvses,
                        totalConnectors = totalEvses
                    ),
                    source = "Freshmile",
                    refId = feature.id
                )
            }
            .take(limit)
    }

    override suspend fun clearCache() {
        client.clearCache()
    }
}

internal fun mapFreshmileStandard(standard: String): String = when (standard.uppercase().trim()) {
    "IEC_62196_T2", "TYPE_2", "TYPE 2" -> "type_2"
    "IEC_62196_T2_COMBO", "CCS", "CCS 2", "CCS2", "COMBO_CCS" -> "combo_ccs"
    "CHADEMO" -> "chademo"
    "DOMESTIC_F", "EF" -> "ef"
    "TESLA" -> "tesla_s"
    else -> standard.lowercase().replace(" ", "_")
}
