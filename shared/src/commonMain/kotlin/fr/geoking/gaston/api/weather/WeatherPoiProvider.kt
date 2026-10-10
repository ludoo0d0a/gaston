package fr.geoking.gaston.api.weather

import fr.geoking.gaston.poi.AbstractPoiProvider
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderRules
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.calculateBoundsFromMapViewport

/**
 * Builds a sparse weather-code grid over the map viewport via Open-Meteo (one POI per cell).
 */
class WeatherPoiProvider(
    private val client: OpenMeteoWeatherGridClient,
    private val gridSize: Int = 5,
) : AbstractPoiProvider() {

    override val usageRules: PoiProviderRules = PoiProviderRules()

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Weather)

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        if (PoiCategory.Weather !in request.categories && request.categories.isNotEmpty()) {
            return emptyList()
        }
        val viewport = request.viewport?.withResolvedBounds(request.latitude, request.longitude)
            ?: return emptyList()
        val minLat = viewport.minLat ?: return emptyList()
        val maxLat = viewport.maxLat ?: return emptyList()
        val minLng = viewport.minLng ?: return emptyList()
        val maxLng = viewport.maxLng ?: return emptyList()

        val n = gridSize.coerceIn(2, 6)
        val points = buildGrid(minLat, maxLat, minLng, maxLng, n)
        val models = modelsForCenter(request.latitude, request.longitude)
        val samples = runCatching {
            client.fetchCurrentWeatherCodes(points, models = models)
        }.getOrElse { emptyList() }

        return samples.map { sample ->
            val style = wmoWeatherStyle(sample.weatherCode)
            Poi(
                id = "weather_${sample.latitude}_${sample.longitude}_${sample.weatherCode}",
                name = style.label,
                address = "",
                latitude = sample.latitude,
                longitude = sample.longitude,
                poiCategory = PoiCategory.Weather,
                source = "Open-Meteo",
                rawSourceData = mapOf(
                    WeatherPoiRawKeys.WEATHER_CODE to sample.weatherCode.toString(),
                    WeatherPoiRawKeys.WMO_FAMILY to style.family.name,
                    WeatherPoiRawKeys.SOURCE to "open_meteo",
                ),
            )
        }
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?,
    ): List<Poi> {
        return search(
            PoiSearchRequest(
                latitude = latitude,
                longitude = longitude,
                viewport = viewport,
                categories = setOf(PoiCategory.Weather),
            )
        )
    }

    companion object {
        /** France bbox — same region as [OpenMeteoWeatherProvider] meteofrance_seamless. */
        private fun modelsForCenter(lat: Double, lon: Double): String? =
            if (lat in 41.0..51.6 && lon in -5.5..10.0) "meteofrance_seamless" else null

        private fun MapViewport.withResolvedBounds(centerLat: Double, centerLng: Double): MapViewport {
            if (minLat != null && maxLat != null && minLng != null && maxLng != null) return this
            if (mapWidthPx <= 0 || mapHeightPx <= 0) return this
            return calculateBoundsFromMapViewport(centerLat, centerLng, zoom, mapWidthPx, mapHeightPx)
        }

        private fun buildGrid(
            minLat: Double,
            maxLat: Double,
            minLng: Double,
            maxLng: Double,
            n: Int,
        ): List<Pair<Double, Double>> {
            if (n <= 1) return listOf((minLat + maxLat) / 2.0 to (minLng + maxLng) / 2.0)
            val points = ArrayList<Pair<Double, Double>>(n * n)
            for (i in 0 until n) {
                val lat = minLat + (maxLat - minLat) * (i + 0.5) / n
                for (j in 0 until n) {
                    val lng = minLng + (maxLng - minLng) * (j + 0.5) / n
                    points.add(lat to lng)
                }
            }
            return points
        }
    }
}
