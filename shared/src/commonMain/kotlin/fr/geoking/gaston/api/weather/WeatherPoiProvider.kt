package fr.geoking.gaston.api.weather

import fr.geoking.gaston.poi.AbstractPoiProvider
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderRules
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.calculateBoundsFromMapViewport

/**
 * Precipitation grid over the map viewport via Open-Meteo bounding_box (one POI per model cell,
 * then thinned to [maxPoiCount]).
 * Limited by [maxPoiCount] to prevent cache exhaustion and OOM on devices with many POIs.
 * Uses LRU eviction to keep total weather POIs under control.
 */
class WeatherPoiProvider(
    private val client: OpenMeteoWeatherGridClient,
    /** Maximum number of weather POIs to return per search request. */
    private val maxPoiCount: Int = 20,
    /** Maximum total weather POIs allowed in cache before pruning. Default: 15 for memory safety. */
    @Suppress("unused")
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
        if (minLat >= maxLat || minLng >= maxLng) return emptyList()

        val effectiveMaxPoints = maxPoiCount.coerceIn(1, OpenMeteoWeatherGridClient.MAX_POINTS)
        val models = modelsForCenter(request.latitude, request.longitude)
        val samples = runCatching {
            client.fetchCurrentPrecipitation(
                latMin = minLat,
                lonMin = minLng,
                latMax = maxLat,
                lonMax = maxLng,
                models = models,
            )
        }.getOrElse { emptyList() }

        // Keep only cells with active precipitation (dry / clear grid points stay off the map).
        val activePrecipSamples = samples.filter { it.precipitation > 0.0 }

        val limitedSamples = thinEvenly(activePrecipSamples, effectiveMaxPoints)

        // Prune weather POIs before creating new ones to stay within [maxTotalWeatherPois]
        pruneExcessPoiCounts()

        return limitedSamples.map { sample ->
            val style = precipitationStyle(sample.precipitation)
            Poi(
                id = "weather_${sample.latitude}_${sample.longitude}_${sample.precipitation}",
                name = style.label,
                address = "",
                latitude = sample.latitude,
                longitude = sample.longitude,
                poiCategory = PoiCategory.Weather,
                source = "Open-Meteo",
                rawSourceData = mapOf(
                    WeatherPoiRawKeys.SOURCE to "open_meteo",
                    WeatherPoiRawKeys.PRECIPITATION to sample.precipitation.toString(),
                ),
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
        /**
         * Bounding-box Open-Meteo requires a concrete model (not `*_seamless` / `best_match`).
         * France → ARPEGE world (~0.25°); elsewhere → ECMWF IFS 0.25°.
         */
        private fun modelsForCenter(lat: Double, lon: Double): String =
            if (lat in 41.0..51.6 && lon in -5.5..10.0) {
                "meteofrance_arpege_world"
            } else {
                OpenMeteoWeatherGridClient.DEFAULT_MODEL
            }

        private fun MapViewport.withResolvedBounds(centerLat: Double, centerLng: Double): MapViewport {
            if (minLat != null && maxLat != null && minLng != null && maxLng != null) return this
            if (mapWidthPx <= 0 || mapHeightPx <= 0) return this
            return calculateBoundsFromMapViewport(centerLat, centerLng, zoom, mapWidthPx, mapHeightPx)
        }

        /** Keep up to [max] samples spread across the full model grid. */
        internal fun thinEvenly(samples: List<WeatherGridSample>, max: Int): List<WeatherGridSample> {
            if (samples.size <= max) return samples
            if (max <= 1) return listOf(samples[samples.size / 2])
            val last = samples.lastIndex
            return (0 until max).map { i ->
                val index = ((i.toDouble() / (max - 1)) * last).toInt()
                samples[index]
            }
        }
    }
}
