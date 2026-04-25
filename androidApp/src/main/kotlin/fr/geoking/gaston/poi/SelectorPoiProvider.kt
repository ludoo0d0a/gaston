package fr.geoking.gaston.poi

import android.util.Log
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.api.openvan.OpenVanCampClient
import fr.geoking.gaston.api.openvan.OpenVanCampProvider
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delegates to the currently selected [PoiProvider] (Routex, DataGouv fuel, …). Etalab / GasApi stay wired for tests;
 * user selection is sanitized to DataGouv in [SettingsManager].
 * based on [SettingsManager.settings].selectedPoiProvider.
 */
class SelectorPoiProvider(
    private val routex: PoiProvider,
    private val dataGouvPrixCarburant: PoiProvider,
    private val gasApi: PoiProvider,
    private val dataGouv: PoiProvider,
    private val dataGouvElec: PoiProvider,
    private val openChargeMap: PoiProvider,
    private val chargy: PoiProvider,
    private val openVanCamp: PoiProvider,
    private val spainMinetur: PoiProvider,
    private val germanyTankerkoenig: PoiProvider,
    private val austriaEControl: PoiProvider,
    private val belgiumOfficial: PoiProvider,
    private val openVanCampClient: OpenVanCampClient,
    private val overpass: PoiProvider,
    private val dataGouvCamping: PoiProvider?,
    private val settingsManager: SettingsManager
) : PoiProvider {

    private data class LoadedPoiRegion(
        val centerLat: Double,
        val centerLng: Double,
        val maxRadiusKmLoaded: Int,
        val loadedAtMs: Long
    )

    private val hybridProvider by lazy { HybridPoiProvider(dataGouv, dataGouvElec) }

    private var cachedPois = listOf<Poi>()
    private val poiSeenAtMs = mutableMapOf<String, Long>()
    private val loadedRegions = mutableListOf<LoadedPoiRegion>()
    private var lastCacheKey: String? = null
    private val cacheLock = Any()
    private val flowMutex = Mutex()

    private fun getProvider(type: PoiProviderType): PoiProvider = when (type) {
        PoiProviderType.Routex -> routex
        PoiProviderType.Etalab -> dataGouvPrixCarburant
        PoiProviderType.GasApi -> gasApi
        PoiProviderType.DataGouv -> dataGouv
        PoiProviderType.DataGouvElec -> dataGouvElec
        PoiProviderType.OpenChargeMap -> openChargeMap
        PoiProviderType.Chargy -> chargy
        PoiProviderType.OpenVanCamp -> openVanCamp
        PoiProviderType.SpainMinetur -> spainMinetur
        PoiProviderType.GermanyTankerkoenig -> germanyTankerkoenig
        PoiProviderType.AustriaEControl -> austriaEControl
        PoiProviderType.BelgiumOfficial -> belgiumOfficial
        PoiProviderType.Overpass -> overpass
        PoiProviderType.Hybrid -> hybridProvider
    }

    private class HybridPoiProvider(
        private val gasProvider: PoiProvider,
        private val elecProvider: PoiProvider
    ) : PoiProvider {
        override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas, PoiCategory.Irve)
        override suspend fun search(request: PoiSearchRequest): List<Poi> {
            val gasResult = gasProvider.search(request.copy(categories = setOf(PoiCategory.Gas), skipFilters = request.skipFilters))
            val elecResult = elecProvider.search(request.copy(categories = setOf(PoiCategory.Irve), skipFilters = request.skipFilters))
            return gasResult + elecResult
        }
        override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
            val gasResult = gasProvider.getGasStations(latitude, longitude, viewport)
            val elecResult = elecProvider.getGasStations(latitude, longitude, viewport)
            return gasResult + elecResult
        }
    }

    override fun searchFlow(request: PoiSearchRequest): Flow<PoiSearchResult> = channelFlow {
        val settings = settingsManager.settings.value
        val providers = try {
            settings.effectiveProviders()
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to resolve providers from settings", e)
            settings.selectedPoiProviders
        }

        if (providers.isEmpty()) {
            send(PoiSearchResult())
            return@channelFlow
        }

        val poiFetchKey = buildString {
            append(providers.sortedBy { it.name }.joinToString(",") { it.name })
            append("|vehicleFilter=").append(settings.useVehicleFilter)
            append("|fuelCard=").append(settings.fuelCard)
            append("|vehicleType=").append(settings.vehicleType)
            append("|vehicleEnergy=").append(settings.vehicleEnergy)
            append("|overpassAmenities=").append(settings.selectedOverpassAmenityTypes.sorted().joinToString(","))
        }

        val nowMs = System.currentTimeMillis()
        val ttlMs = 5L * 60L * 1000L // 5 minutes TTL as requested
        val expiresBeforeMs = nowMs - ttlMs
        val maxRegions = 8
        val maxPoisInCache = 1200

        val alreadyCoveredResult = synchronized(cacheLock) {
            if (lastCacheKey != poiFetchKey) {
                loadedRegions.clear()
                cachedPois = emptyList()
                poiSeenAtMs.clear()
                lastCacheKey = poiFetchKey
            }

            // TTL eviction
            loadedRegions.removeAll { it.loadedAtMs < expiresBeforeMs }
            if (poiSeenAtMs.isNotEmpty()) {
                val expiredPoiIds = poiSeenAtMs
                    .filter { (_, seenAt) -> seenAt < expiresBeforeMs }
                    .keys
                    .toSet()
                if (expiredPoiIds.isNotEmpty()) {
                    poiSeenAtMs.keys.removeAll(expiredPoiIds)
                    cachedPois = cachedPois.filterNot { it.id in expiredPoiIds }
                }
            }

            val requiredRadiusKm = request.viewport?.let { v ->
                radiusKmFromMapViewport(
                    request.latitude,
                    request.longitude,
                    v.zoom,
                    v.mapWidthPx,
                    v.mapHeightPx
                ).coerceIn(1, 50)
            } ?: 10 // Default radius for dashboard

            val viewportCovered = loadedRegions.any { region ->
                region.maxRadiusKmLoaded >= requiredRadiusKm &&
                        haversineKm(
                            request.latitude,
                            request.longitude,
                            region.centerLat,
                            region.centerLng
                        ) <= (region.maxRadiusKmLoaded - requiredRadiusKm).toDouble() + 0.5
            }

            if (viewportCovered) {
                PoiSearchResult(pois = applyPostFilters(cachedPois, request, providers))
            } else null
        }

        if (alreadyCoveredResult != null) {
            send(alreadyCoveredResult)
            return@channelFlow
        }

        val allPois = mutableListOf<Poi>()
        val errors = mutableListOf<PoiProviderError>()
        var finalEnriched = listOf<Poi>()

        val vehicleType = settings.vehicleType
        val categories = try {
            providers.map { providerType ->
                when (providerType) {
                    PoiProviderType.Overpass -> {
                        val fromSettings = settings.selectedOverpassAmenityTypes.mapNotNull { id ->
                            when (id) {
                                "toilets" -> PoiCategory.Toilet
                                "drinking_water" -> PoiCategory.DrinkingWater
                                "camp_site" -> PoiCategory.Camping
                                "caravan_site" -> PoiCategory.CaravanSite
                                "picnic_site" -> PoiCategory.PicnicSite
                                "truck_stop" -> PoiCategory.TruckStop
                                "rest_area" -> PoiCategory.RestArea
                                "restaurant" -> PoiCategory.Restaurant
                                "fast_food" -> PoiCategory.FastFood
                                "speed_camera" -> PoiCategory.Radar
                                else -> null
                            }
                        }.toSet()
                        val defaultOverpass = fromSettings.ifEmpty {
                            setOf(PoiCategory.Toilet, PoiCategory.DrinkingWater)
                        }
                        when (vehicleType) {
                            VehicleType.Truck -> defaultOverpass + setOf(PoiCategory.TruckStop, PoiCategory.RestArea, PoiCategory.Gas)
                            VehicleType.Motorhome -> defaultOverpass + setOf(PoiCategory.CaravanSite, PoiCategory.Camping, PoiCategory.PicnicSite)
                            else -> defaultOverpass
                        }
                    }

                    else -> {
                        when (vehicleType) {
                            VehicleType.Truck -> setOf(PoiCategory.Gas, PoiCategory.TruckStop, PoiCategory.RestArea)
                            else -> setOf(PoiCategory.Gas, PoiCategory.Irve)
                        }
                    }
                }
            }.flatten().toSet()
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to map categories", e)
            setOf(PoiCategory.Gas, PoiCategory.Irve)
        }

        val effectiveRequest = request.copy(categories = categories, skipFilters = true)

        coroutineScope {
            providers.forEach { providerType ->
                launch {
                    val activeProvider = getProvider(providerType)
                    val searchResult = try {
                        activeProvider.searchResult(effectiveRequest)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        PoiSearchResult(errors = listOf(PoiProviderError(providerType.name, e.message ?: "Unknown error")))
                    }

                    flowMutex.withLock {
                        allPois.addAll(searchResult.pois)
                        errors.addAll(searchResult.errors)

                        if (providerType == PoiProviderType.Overpass && PoiCategory.CaravanSite in categories && dataGouvCamping != null) {
                            try {
                                val extra = dataGouvCamping.search(effectiveRequest)
                                allPois.addAll(extra)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                errors.add(PoiProviderError("DataGouv Camping", e.message ?: "Unknown error"))
                            }
                        }

                        val merged = PoiMerger.mergePois(allPois)
                        val enriched = enrichNationalReferencePrices(
                            pois = merged,
                            providers = providers,
                            centerLat = request.latitude,
                            centerLon = request.longitude
                        )
                        finalEnriched = enriched
                        PoiSearchResult(pois = applyPostFilters(enriched, request, providers), errors = errors.toList())
                    }.let { result ->
                        send(result)
                    }
                }
            }
        }

        // After all providers finish, update the cache.
        val requiredRadiusKm = request.viewport?.let { v ->
            radiusKmFromMapViewport(
                request.latitude,
                request.longitude,
                v.zoom,
                v.mapWidthPx,
                v.mapHeightPx
            ).coerceIn(1, 50)
        } ?: 10

        synchronized(cacheLock) {
            cachedPois = PoiMerger.mergeInto(cachedPois, finalEnriched)
            val mergedNow = System.currentTimeMillis()
            finalEnriched.forEach { poiSeenAtMs[it.id] = mergedNow }
            cachedPois.forEach { p ->
                if (poiSeenAtMs[p.id] == null) poiSeenAtMs[p.id] = mergedNow
            }

            loadedRegions.add(
                LoadedPoiRegion(
                    centerLat = request.latitude,
                    centerLng = request.longitude,
                    maxRadiusKmLoaded = requiredRadiusKm,
                    loadedAtMs = mergedNow
                )
            )

            while (loadedRegions.size > maxRegions) {
                val farthest = loadedRegions.maxBy { r ->
                    haversineKm(r.centerLat, r.centerLng, request.latitude, request.longitude)
                }
                loadedRegions.remove(farthest)
            }

            if (cachedPois.size > maxPoisInCache) {
                cachedPois = cachedPois
                    .asSequence()
                    .map { p -> p to approxDistanceKm(request.latitude, request.longitude, p.latitude, p.longitude) }
                    .sortedBy { it.second }
                    .take(maxPoisInCache)
                    .map { it.first }
                    .toList()
                val keepIds = cachedPois.map { it.id }.toSet()
                poiSeenAtMs.keys.retainAll(keepIds)
            }
        }
    }

    override suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
        val settings = settingsManager.settings.value
        val providers = try {
            settings.effectiveProviders()
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to resolve providers from settings", e)
            settings.selectedPoiProviders
        }

        if (providers.isEmpty()) return PoiSearchResult()

        val poiFetchKey = buildString {
            append(providers.sortedBy { it.name }.joinToString(",") { it.name })
            append("|vehicleFilter=").append(settings.useVehicleFilter)
            append("|fuelCard=").append(settings.fuelCard)
            append("|vehicleType=").append(settings.vehicleType)
            append("|vehicleEnergy=").append(settings.vehicleEnergy)
            append("|overpassAmenities=").append(settings.selectedOverpassAmenityTypes.sorted().joinToString(","))
        }

        val nowMs = System.currentTimeMillis()
        val ttlMs = 5L * 60L * 1000L // 5 minutes TTL as requested
        val expiresBeforeMs = nowMs - ttlMs
        val maxRegions = 8
        val maxPoisInCache = 1200

        synchronized(cacheLock) {
            if (lastCacheKey != poiFetchKey) {
                loadedRegions.clear()
                cachedPois = emptyList()
                poiSeenAtMs.clear()
                lastCacheKey = poiFetchKey
            }

            // TTL eviction
            loadedRegions.removeAll { it.loadedAtMs < expiresBeforeMs }
            if (poiSeenAtMs.isNotEmpty()) {
                val expiredPoiIds = poiSeenAtMs
                    .filter { (_, seenAt) -> seenAt < expiresBeforeMs }
                    .keys
                    .toSet()
                if (expiredPoiIds.isNotEmpty()) {
                    poiSeenAtMs.keys.removeAll(expiredPoiIds)
                    cachedPois = cachedPois.filterNot { it.id in expiredPoiIds }
                }
            }

            val requiredRadiusKm = request.viewport?.let { v ->
                radiusKmFromMapViewport(
                    request.latitude,
                    request.longitude,
                    v.zoom,
                    v.mapWidthPx,
                    v.mapHeightPx
                ).coerceIn(1, 50)
            } ?: 10 // Default radius for dashboard

            val viewportCovered = loadedRegions.any { region ->
                region.maxRadiusKmLoaded >= requiredRadiusKm &&
                        haversineKm(
                            request.latitude,
                            request.longitude,
                            region.centerLat,
                            region.centerLng
                        ) <= (region.maxRadiusKmLoaded - requiredRadiusKm).toDouble() + 0.5
            }

            if (viewportCovered) {
                return PoiSearchResult(pois = applyPostFilters(cachedPois, request, providers))
            }
        }

        val allPois = mutableListOf<Poi>()
        val errors = mutableListOf<PoiProviderError>()

        val vehicleType = settings.vehicleType
        val categories = try {
            providers.map { providerType ->
                when (providerType) {
                    PoiProviderType.Overpass -> {
                        val fromSettings = settings.selectedOverpassAmenityTypes.mapNotNull { id ->
                            when (id) {
                                "toilets" -> PoiCategory.Toilet
                                "drinking_water" -> PoiCategory.DrinkingWater
                                "camp_site" -> PoiCategory.Camping
                                "caravan_site" -> PoiCategory.CaravanSite
                                "picnic_site" -> PoiCategory.PicnicSite
                                "truck_stop" -> PoiCategory.TruckStop
                                "rest_area" -> PoiCategory.RestArea
                                "restaurant" -> PoiCategory.Restaurant
                                "fast_food" -> PoiCategory.FastFood
                                "speed_camera" -> PoiCategory.Radar
                                else -> null
                            }
                        }.toSet()
                        val defaultOverpass = fromSettings.ifEmpty {
                            setOf(PoiCategory.Toilet, PoiCategory.DrinkingWater)
                        }
                        when (vehicleType) {
                            VehicleType.Truck -> defaultOverpass + setOf(PoiCategory.TruckStop, PoiCategory.RestArea, PoiCategory.Gas)
                            VehicleType.Motorhome -> defaultOverpass + setOf(PoiCategory.CaravanSite, PoiCategory.Camping, PoiCategory.PicnicSite)
                            else -> defaultOverpass
                        }
                    }

                    else -> {
                        when (vehicleType) {
                            VehicleType.Truck -> setOf(PoiCategory.Gas, PoiCategory.TruckStop, PoiCategory.RestArea)
                            else -> setOf(PoiCategory.Gas, PoiCategory.Irve)
                        }
                    }
                }
            }.flatten().toSet()
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to map categories", e)
            setOf(PoiCategory.Gas, PoiCategory.Irve)
        }

        val effectiveRequest = request.copy(categories = categories, skipFilters = true)

        providers.forEach { providerType ->
            val activeProvider = getProvider(providerType)
            val searchResult = try {
                activeProvider.searchResult(effectiveRequest)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                PoiSearchResult(errors = listOf(PoiProviderError(providerType.name, e.message ?: "Unknown error")))
            }
            allPois.addAll(searchResult.pois)
            errors.addAll(searchResult.errors)

            if (providerType == PoiProviderType.Overpass && PoiCategory.CaravanSite in categories && dataGouvCamping != null) {
                try {
                    val extra = dataGouvCamping.search(effectiveRequest)
                    allPois.addAll(extra)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errors.add(PoiProviderError("DataGouv Camping", e.message ?: "Unknown error"))
                }
            }
        }

        val merged = PoiMerger.mergePois(allPois)
        val enriched = enrichNationalReferencePrices(
            pois = merged,
            providers = providers,
            centerLat = request.latitude,
            centerLon = request.longitude
        )

        val requiredRadiusKm = request.viewport?.let { v ->
            radiusKmFromMapViewport(
                request.latitude,
                request.longitude,
                v.zoom,
                v.mapWidthPx,
                v.mapHeightPx
            ).coerceIn(1, 50)
        } ?: 10

        synchronized(cacheLock) {
            cachedPois = PoiMerger.mergeInto(cachedPois, enriched)
            val mergedNow = System.currentTimeMillis()
            enriched.forEach { poiSeenAtMs[it.id] = mergedNow }
            cachedPois.forEach { p ->
                if (poiSeenAtMs[p.id] == null) poiSeenAtMs[p.id] = mergedNow
            }

            loadedRegions.add(
                LoadedPoiRegion(
                    centerLat = request.latitude,
                    centerLng = request.longitude,
                    maxRadiusKmLoaded = requiredRadiusKm,
                    loadedAtMs = mergedNow
                )
            )

            // Keep the region cache bounded.
            while (loadedRegions.size > maxRegions) {
                val farthest = loadedRegions.maxBy { r ->
                    haversineKm(r.centerLat, r.centerLng, request.latitude, request.longitude)
                }
                loadedRegions.remove(farthest)
            }

            // Keep the POI cache bounded: keep closest POIs to current center.
            if (cachedPois.size > maxPoisInCache) {
                cachedPois = cachedPois
                    .asSequence()
                    .map { p -> p to approxDistanceKm(request.latitude, request.longitude, p.latitude, p.longitude) }
                    .sortedBy { it.second }
                    .take(maxPoisInCache)
                    .map { it.first }
                    .toList()
                val keepIds = cachedPois.map { it.id }.toSet()
                poiSeenAtMs.keys.retainAll(keepIds)
            }
        }

        val result = applyPostFilters(cachedPois, request, providers)
        Log.d("SelectorPoiProvider", "search providers=$providers categories=$categories skipFilters=${request.skipFilters} -> ${result.size} pois")
        return PoiSearchResult(pois = result, errors = errors)
    }

    private fun applyPostFilters(pois: List<Poi>, request: PoiSearchRequest, providers: Set<PoiProviderType>): List<Poi> {
        val centerLat = request.latitude
        val centerLng = request.longitude
        val displayRadiusKm = request.viewport?.let { v ->
            radiusKmFromMapViewport(
                centerLat,
                centerLng,
                v.zoom,
                v.mapWidthPx,
                v.mapHeightPx
            ).coerceIn(1, 50)
        } ?: 10

        val raw = pois.filter { poi ->
            approxDistanceKm(centerLat, centerLng, poi.latitude, poi.longitude) <= displayRadiusKm * 1.05
        }

        return if (!request.skipFilters) {
            StationMapFilters.apply(
                settings = settingsManager.settings.value,
                pois = raw,
                providers = providers,
                skipWhenOnlyOverpass = true
            )
        } else {
            raw
        }
    }

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        return searchResult(request).pois
    }

    override fun clearCache() {
        synchronized(cacheLock) {
            loadedRegions.clear()
            cachedPois = emptyList()
            poiSeenAtMs.clear()
        }
        routex.clearCache()
        dataGouvPrixCarburant.clearCache()
        gasApi.clearCache()
        dataGouv.clearCache()
        dataGouvElec.clearCache()
        openChargeMap.clearCache()
        chargy.clearCache()
        openVanCamp.clearCache()
        spainMinetur.clearCache()
        germanyTankerkoenig.clearCache()
        austriaEControl.clearCache()
        belgiumOfficial.clearCache()
        overpass.clearCache()
    }

    // POI deduplication/merge is centralized in `PoiMerger` so the map cache and selectors
    // use the same “close enough + similar enough” matching rules.

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val settings = settingsManager.settings.value
        val providers = try {
            settings.effectiveProviders()
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to resolve providers from settings", e)
            settings.selectedPoiProviders
        }

        if (providers.isEmpty()) return emptyList()

        val allPois = mutableListOf<Poi>()

        providers.forEach { providerType ->
            val activeProvider = getProvider(providerType)
            allPois.addAll(activeProvider.getGasStations(latitude, longitude, viewport))
        }

        var result = PoiMerger.mergePois(allPois)
        result = enrichNationalReferencePrices(
            pois = result,
            providers = providers,
            centerLat = latitude,
            centerLon = longitude
        )
        result = StationMapFilters.apply(
            settings = settings,
            pois = result,
            providers = providers,
            skipWhenOnlyOverpass = false,
        )
        Log.d("SelectorPoiProvider", "selected=$providers lat=$latitude lon=$longitude -> ${result.size} pois (energy+power+operator+connector filter)")
        return result
    }

    /**
     * When OpenVan.camp is enabled, attach reference fuel prices (weekly averages) to gas POIs
     * in countries known for having reference prices (e.g. Luxembourg, Portugal, Italy, etc.).
     */
    private suspend fun enrichNationalReferencePrices(
        pois: List<Poi>,
        providers: Set<PoiProviderType>,
        centerLat: Double,
        centerLon: Double
    ): List<Poi> {
        if (PoiProviderType.OpenVanCamp !in providers) return pois

        // Determine target country from search center
        val region = ParkingRegion.containing(centerLat, centerLon) ?: return pois
        val iso = region.countryCode

        if (!FuelPriceRegistry.hasReferencePrice(iso)) return pois

        val prices = try {
            openVanCampClient.getReferenceFuelPrices(iso)?.takeIf { it.isNotEmpty() } ?: return pois
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return pois
        }

        return pois.map { p ->
            if (p.isElectric) return@map p
            val cat = p.poiCategory ?: PoiCategory.Gas
            if (cat != PoiCategory.Gas) return@map p
            if (!region.contains(p.latitude, p.longitude)) return@map p
            if (!p.fuelPrices.isNullOrEmpty()) return@map p

            p.copy(
                fuelPrices = prices,
                source = when (val s = p.source) {
                    null -> "OpenVan.camp ($iso official price)"
                    else -> "$s + OpenVan.camp ($iso official price)"
                }
            )
        }
    }
}
