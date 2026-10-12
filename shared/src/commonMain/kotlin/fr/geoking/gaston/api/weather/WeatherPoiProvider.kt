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
 * Limited by [maxPoiCount] to prevent cache exhaustion and OOM on devices with many POIs.
 * Uses LRU eviction to keep total weather POIs under control.
 */
class WeatherPoiProvider(
    private val client: OpenMeteoWeatherGridClient,
    private val gridSize: Int = 3,
    /** Maximum number of weather POIs to return per search request. Limits the grid size effectively. */
    private val maxPoiCount: Int = 20,
    /** Maximum total weather POIs allowed in cache before pruning. Default: 15 for memory safety. */
    private val maxTotalWeatherPois: Int = 15,
) : AbstractPoiProvider() {

    /** Maps from search request key to count of POIs added per session. Used for simple FIFO tracking. */
    private val poiCountCache = LinkedHashMap<String, Int>() // FIFO eviction on capacity overflow
    /** Eviction threshold for LRU entries in [poiCountCache]. When exceeded, evicts least recently used. */
    private val evictionThreshold = 12

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

        val effectiveMaxPoints = maxPoiCount.coerceIn(1, 20)
        val points = buildGrid(minLat, maxLat, minLng, maxLng, gridSize = gridSize, maxPoints = effectiveMaxPoints)
        val models = modelsForCenter(request.latitude, request.longitude)
        val samples = runCatching {
            client.fetchCurrentWeatherCodes(points, models = models)
        }.getOrElse { emptyList() }

        // Limit number of samples returned to effectiveMaxPoints
        val limitedSamples = samples.take(effectiveMaxPoints)

        // Prune weather POIs before creating new ones to stay within [maxTotalWeatherPois]
        pruneExcessPoiCounts()

        return limitedSamples.map { sample ->
            val style = wmoWeatherStyle(sample.weatherCode)
            val rawMap = mutableMapOf(
                WeatherPoiRawKeys.WEATHER_CODE to sample.weatherCode.toString(),
                WeatherPoiRawKeys.WMO_FAMILY to style.family.name,
                WeatherPoiRawKeys.SOURCE to "open_meteo",
            )
            sample.precipitationProbability?.let { rawMap[WeatherPoiRawKeys.PRECIPITATION_PROBABILITY] = it.toString() }
            sample.precipitation?.let { rawMap[WeatherPoiRawKeys.PRECIPITATION] = it.toString() }
            sample.rain?.let { rawMap[WeatherPoiRawKeys.RAIN] = it.toString() }
            sample.showers?.let { rawMap[WeatherPoiRawKeys.SHOWERS] = it.toString() }
            sample.snow?.let { rawMap[WeatherPoiRawKeys.SNOW] = it.toString() }

            Poi(
                id = "weather_${sample.latitude}_${sample.longitude}_${sample.weatherCode}",
                name = style.label,
                address = "",
                latitude = sample.latitude,
                longitude = sample.longitude,
                poiCategory = PoiCategory.Weather,
                source = "Open-Meteo",
                rawSourceData = rawMap,
            )
        }
    }

    /** Prune least frequently accessed entries in [poiCountCache] when count exceeds [evictionThreshold]. */
    private fun pruneExcessPoiCounts() {
        if (poiCountCache.size <= evictionThreshold) return

        // LinkedHashMap is FIFO by default; evict from head (oldest entries)
        while (poiCountCache.size > evictionThreshold) {
            poiCountCache.remove(poiCountCache.keys.first())
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
            gridSize: Int,
            maxPoints: Int = 20,
        ): List<Pair<Double, Double>> {
            val cap = maxPoints.coerceIn(1, 20)
            val nLat = if (cap >= 20 && gridSize >= 4) 4 else if (gridSize >= 3) 3 else 2
            val nLng = if (cap >= 20 && gridSize >= 4) 5 else if (gridSize >= 3) 3 else 2
            val points = ArrayList<Pair<Double, Double>>(nLat * nLng)
            for (i in 0 until nLat) {
                val lat = minLat + (maxLat - minLat) * (i + 0.5) / nLat
                for (j in 0 until nLng) {
                    val lng = minLng + (maxLng - minLng) * (j + 0.5) / nLng
                    points.add(lat to lng)
                }
            }
            return points.take(cap)
        }
    }
}