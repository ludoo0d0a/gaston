package fr.geoking.gaston.poi

import android.util.Log
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveAllowedCategories
import fr.geoking.gaston.isOtherModeActive
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.api.openvan.OpenVanCampClient
import fr.geoking.gaston.api.openvan.OpenVanCampProvider
import fr.geoking.gaston.persistence.PoiCacheDao
import fr.geoking.gaston.persistence.PoiCacheEntity
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.repository.StationPriceHistoryRepository
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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
    private val ukCma: PoiProvider,
    private val italyMimit: PoiProvider,
    private val sloveniaGorivaSi: PoiProvider,
    private val norwayDrivstoffAppen: PoiProvider,
    private val swedenDrivstoffAppen: PoiProvider,
    private val portugalDgeg: PoiProvider,
    private val netherlandsAnwb: PoiProvider,
    private val denmarkFuelpricesDk: PoiProvider,
    private val fuelo: PoiProvider,
    private val australiaNswFuelCheck: PoiProvider,
    private val croatiaMzoe: PoiProvider,
    private val finlandPolttoaine: PoiProvider,
    private val greeceFuelGr: PoiProvider,
    private val irelandPickAPump: PoiProvider,
    private val moldovaAnre: PoiProvider,
    private val romaniaPeco: PoiProvider,
    private val serbiaNis: PoiProvider,
    private val mexicoCre: PoiProvider,
    private val argentinaEnergia: PoiProvider,
    private val dataGouvElec: PoiProvider,
    private val openChargeMap: PoiProvider,
    private val chargy: PoiProvider,
    private val fastned: PoiProvider,
    private val dkv: PoiProvider,
    private val ecoMovement: PoiProvider,
    private val openVanCamp: PoiProvider,
    private val spainMinetur: PoiProvider,
    private val germanyTankerkoenig: PoiProvider,
    private val austriaEControl: PoiProvider,
    private val belgiumOfficial: PoiProvider,
    private val openVanCampClient: OpenVanCampClient,
    private val overpass: PoiProvider,
    private val dataGouvCamping: PoiProvider?,
    private val poiCacheDao: PoiCacheDao,
    private val settingsManager: SettingsManager,
    private val historyRepo: StationPriceHistoryRepository? = null
) : PoiProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private data class LoadedPoiRegion(
        val centerLat: Double,
        val centerLng: Double,
        val maxRadiusKmLoaded: Int,
        val loadedAtMs: Long
    )

    private val hybridProvider by lazy { HybridPoiProvider(dataGouv, dataGouvElec) }

    private val cachedPois = mutableMapOf<String, Poi>()
    private val poiSeenAtMs = mutableMapOf<String, Long>()
    private val loadedRegions = mutableListOf<LoadedPoiRegion>()
    private var lastCacheKey: String? = null
    private var lastCleanupAtMs: Long = 0
    private val cacheLock = Any()
    private val flowMutex = Mutex()

    private fun getCategoriesToFetch(settings: AppSettings): Set<PoiCategory> {
        return if (settings.isOtherModeActive()) {
            settings.effectiveAllowedCategories()
        } else {
            // Always fetch both Gas and Irve to pre-populate cache for mode toggling
            settings.effectiveAllowedCategories() + setOf(PoiCategory.Gas, PoiCategory.Irve)
        }
    }

    private fun getCountryCodes(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        hasViewport: Boolean
    ): List<String> {
        return if (hasViewport) {
            val latDelta = radiusKm / 111.0
            val lonDelta = radiusKm / (111.0 * cos(latitude * PI / 180.0))
            ParkingRegion.allInViewport(
                latMin = latitude - latDelta,
                latMax = latitude + latDelta,
                lonMin = longitude - lonDelta,
                lonMax = longitude + lonDelta
            ).map { it.countryCode }
        } else {
            ParkingRegion.allContaining(latitude, longitude).map { it.countryCode }
        }
    }

    private fun getProvider(type: PoiProviderType): PoiProvider = when (type) {
        PoiProviderType.Routex -> routex
        PoiProviderType.Etalab -> dataGouvPrixCarburant
        PoiProviderType.GasApi -> gasApi
        PoiProviderType.DataGouv -> dataGouv
        PoiProviderType.UkCma -> ukCma
        PoiProviderType.ItalyMimit -> italyMimit
        PoiProviderType.SloveniaGorivaSi -> sloveniaGorivaSi
        PoiProviderType.NorwayDrivstoffAppen -> norwayDrivstoffAppen
        PoiProviderType.SwedenDrivstoffAppen -> swedenDrivstoffAppen
        PoiProviderType.PortugalDgeg -> portugalDgeg
        PoiProviderType.NetherlandsAnwb -> netherlandsAnwb
        PoiProviderType.DenmarkFuelpricesDk -> denmarkFuelpricesDk
        PoiProviderType.Fuelo -> fuelo
        PoiProviderType.AustraliaNswFuelCheck -> australiaNswFuelCheck
        PoiProviderType.CroatiaMzoe -> croatiaMzoe
        PoiProviderType.FinlandPolttoaine -> finlandPolttoaine
        PoiProviderType.GreeceFuelGr -> greeceFuelGr
        PoiProviderType.IrelandPickAPump -> irelandPickAPump
        PoiProviderType.MoldovaAnre -> moldovaAnre
        PoiProviderType.RomaniaPeco -> romaniaPeco
        PoiProviderType.SerbiaNis -> serbiaNis
        PoiProviderType.MexicoCre -> mexicoCre
        PoiProviderType.ArgentinaEnergia -> argentinaEnergia
        PoiProviderType.DataGouvElec -> dataGouvElec
        PoiProviderType.OpenChargeMap -> openChargeMap
        PoiProviderType.Chargy -> chargy
        PoiProviderType.Fastned -> fastned
        PoiProviderType.Dkv -> dkv
        PoiProviderType.EcoMovement -> ecoMovement
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

        // Periodic background cleanup
        val nowCleanup = System.currentTimeMillis()
        if (nowCleanup - lastCleanupAtMs > 60L * 60L * 1000L) { // Once per hour
            lastCleanupAtMs = nowCleanup
            launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    poiCacheDao.deleteOldPois(nowCleanup - (12L * 60L * 60L * 1000L))
                    historyRepo?.deleteOldSamples(nowCleanup - (30L * 24L * 60L * 60L * 1000L))
                } catch (e: Exception) {
                    Log.e("SelectorPoiProvider", "Failed to cleanup old POIs", e)
                }
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
        } ?: 10

        val isoCountries = getCountryCodes(
            latitude = request.latitude,
            longitude = request.longitude,
            radiusKm = requiredRadiusKm,
            hasViewport = request.viewport != null
        )

        val providers = try {
            settings.effectiveProviders(countryCodes = isoCountries)
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to resolve providers from settings", e)
            settings.selectedPoiProviders
        }

        if (providers.isEmpty()) {
            send(PoiSearchResult())
            return@channelFlow
        }

        val categoriesToFetch = getCategoriesToFetch(settings)

        val poiFetchKey = buildString {
            append(providers.sortedBy { it.name }.joinToString(",") { it.name })
            append("|categories=").append(categoriesToFetch.map { it.name }.sorted().joinToString(","))
        }

        val nowMs = System.currentTimeMillis()
        val ttlMs = 12L * 60L * 60L * 1000L // 12 hours TTL as requested
        val expiresBeforeMs = nowMs - ttlMs
        val maxRegions = 12 // Increased slightly for persistence
        val maxPoisInCache = 1200

        var isFromMemory = false
        val alreadyCoveredResult = synchronized(cacheLock) {
            if (lastCacheKey != poiFetchKey) {
                loadedRegions.clear()
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
                    expiredPoiIds.forEach { cachedPois.remove(it) }
                }
            }

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
                isFromMemory = true
                PoiSearchResult(pois = applyPostFilters(cachedPois.values.toList(), request, providers))
            } else null
        }

        var currentAlreadyCoveredResult = alreadyCoveredResult
        if (currentAlreadyCoveredResult == null) {
            // Try persistent cache
            val latDelta = requiredRadiusKm / 111.0
            val lonDelta = requiredRadiusKm / (111.0 * cos(request.latitude * PI / 180.0))
            val dbPois = try {
                poiCacheDao.getPoisInRegion(
                    latMin = request.latitude - latDelta,
                    latMax = request.latitude + latDelta,
                    lonMin = request.longitude - lonDelta,
                    lonMax = request.longitude + lonDelta,
                    minUpdatedAtMs = expiresBeforeMs
                ).mapNotNull {
                    try {
                        json.decodeFromString<Poi>(it.poiJson)
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("SelectorPoiProvider", "Failed to query DB cache", e)
                emptyList()
            }

            if (dbPois.isNotEmpty()) {
                val dbResultPois = synchronized(cacheLock) {
                    PoiMerger.mergeInto(cachedPois, dbPois)
                    dbPois.forEach { poiSeenAtMs[it.id] = nowMs }
                    cachedPois.values.toList()
                }
                currentAlreadyCoveredResult = PoiSearchResult(pois = applyPostFilters(dbResultPois, request, providers))
            }
        }

        if (currentAlreadyCoveredResult != null) {
            send(currentAlreadyCoveredResult)
            if (isFromMemory) {
                return@channelFlow
            }
        }

        val allPois = mutableListOf<Poi>()
        val errors = mutableListOf<PoiProviderError>()
        var finalEnriched = listOf<Poi>()

        // In "Other" mode, if no amenities are selected, we don't display anything.
        if (settings.isOtherModeActive() && categoriesToFetch.isEmpty()) {
            send(PoiSearchResult())
            return@channelFlow
        }

        val effectiveRequest = request.copy(categories = categoriesToFetch, skipFilters = true)

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

                        if (providerType == PoiProviderType.Overpass && PoiCategory.CaravanSite in categoriesToFetch && dataGouvCamping != null) {
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
                        val rated = enrichPriceRatings(enriched)
                        finalEnriched = rated

                        val resultToEmit = synchronized(cacheLock) {
                            PoiMerger.mergeInto(cachedPois, enriched)
                            enriched.forEach { poiSeenAtMs[it.id] = nowMs }
                            PoiSearchResult(pois = applyPostFilters(cachedPois.values.toList(), request, providers), errors = errors.toList())
                        }
                        send(resultToEmit)
                    }
                }
            }
        }

        // After all providers finish, update the cache.
        val mergedNow = System.currentTimeMillis()
        synchronized(cacheLock) {
            // Already merged in loop, just refresh timestamps for the final set
            finalEnriched.forEach { poiSeenAtMs[it.id] = mergedNow }
            cachedPois.values.forEach { p ->
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
                val toRemove = cachedPois.values
                    .asSequence()
                    .map { p -> p.id to approxDistanceKm(request.latitude, request.longitude, p.latitude, p.longitude) }
                    .sortedByDescending { it.second }
                    .take(cachedPois.size - maxPoisInCache)
                    .map { it.first }
                    .toList()

                toRemove.forEach {
                    cachedPois.remove(it)
                    poiSeenAtMs.remove(it)
                }
            }
        }

        // Persist to DB
        try {
            val entities = finalEnriched.map { p ->
                PoiCacheEntity(
                    id = p.id,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    name = p.name,
                    address = p.address,
                    poiJson = json.encodeToString(p),
                    updatedAtMs = mergedNow
                )
            }
            poiCacheDao.insertPois(entities)
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to persist POIs", e)
        }
    }

    override suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
        val settings = settingsManager.settings.value

        val requiredRadiusKm = request.viewport?.let { v ->
            radiusKmFromMapViewport(
                request.latitude,
                request.longitude,
                v.zoom,
                v.mapWidthPx,
                v.mapHeightPx
            ).coerceIn(1, 50)
        } ?: 10

        val isoCountries = getCountryCodes(
            latitude = request.latitude,
            longitude = request.longitude,
            radiusKm = requiredRadiusKm,
            hasViewport = request.viewport != null
        )

        val providers = try {
            settings.effectiveProviders(countryCodes = isoCountries)
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to resolve providers from settings", e)
            settings.selectedPoiProviders
        }

        if (providers.isEmpty()) return PoiSearchResult()

        val categoriesToFetch = getCategoriesToFetch(settings)

        val poiFetchKey = buildString {
            append(providers.sortedBy { it.name }.joinToString(",") { it.name })
            append("|categories=").append(categoriesToFetch.map { it.name }.sorted().joinToString(","))
        }

        val nowMs = System.currentTimeMillis()
        val ttlMs = 12L * 60L * 60L * 1000L // 12 hours TTL as requested
        val expiresBeforeMs = nowMs - ttlMs
        val maxRegions = 12
        val maxPoisInCache = 1200

        val cachedResult = synchronized(cacheLock) {
            if (lastCacheKey != poiFetchKey) {
                loadedRegions.clear()
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
                    expiredPoiIds.forEach { cachedPois.remove(it) }
                }
            }

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
                PoiSearchResult(pois = applyPostFilters(cachedPois.values.toList(), request, providers))
            } else null
        }

        if (cachedResult != null) return cachedResult

        // Try persistent cache
        val latDelta = requiredRadiusKm / 111.0
        val lonDelta = requiredRadiusKm / (111.0 * cos(request.latitude * PI / 180.0))
        val dbPois = try {
            poiCacheDao.getPoisInRegion(
                latMin = request.latitude - latDelta,
                latMax = request.latitude + latDelta,
                lonMin = request.longitude - lonDelta,
                lonMax = request.longitude + lonDelta,
                minUpdatedAtMs = expiresBeforeMs
            ).mapNotNull {
                try {
                    json.decodeFromString<Poi>(it.poiJson)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to query DB cache", e)
            emptyList()
        }

        if (dbPois.isNotEmpty()) {
            synchronized(cacheLock) {
                PoiMerger.mergeInto(cachedPois, dbPois)
                dbPois.forEach { poiSeenAtMs[it.id] = nowMs }
            }
        }

        val allPois = mutableListOf<Poi>()
        val errors = mutableListOf<PoiProviderError>()

        // In "Other" mode, if no amenities are selected, we don't display anything.
        if (settings.isOtherModeActive() && categoriesToFetch.isEmpty()) {
            return PoiSearchResult()
        }

        val effectiveRequest = request.copy(categories = categoriesToFetch, skipFilters = true)

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

            if (providerType == PoiProviderType.Overpass && PoiCategory.CaravanSite in categoriesToFetch && dataGouvCamping != null) {
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
        val rated = enrichPriceRatings(enriched)

        val mergedNow = System.currentTimeMillis()
        synchronized(cacheLock) {
            PoiMerger.mergeInto(cachedPois, rated)
            rated.forEach { poiSeenAtMs[it.id] = mergedNow }
            cachedPois.values.forEach { p ->
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
                val toRemove = cachedPois.values
                    .asSequence()
                    .map { p -> p.id to approxDistanceKm(request.latitude, request.longitude, p.latitude, p.longitude) }
                    .sortedByDescending { it.second }
                    .take(cachedPois.size - maxPoisInCache)
                    .map { it.first }
                    .toList()

                toRemove.forEach {
                    cachedPois.remove(it)
                    poiSeenAtMs.remove(it)
                }
            }
        }

        // Persist to DB
        try {
            val entities = enriched.map { p ->
                PoiCacheEntity(
                    id = p.id,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    name = p.name,
                    address = p.address,
                    poiJson = json.encodeToString(p),
                    updatedAtMs = mergedNow
                )
            }
            poiCacheDao.insertPois(entities)
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to persist POIs", e)
        }

        val finalPois = synchronized(cacheLock) { cachedPois.values.toList() }
        val result = applyPostFilters(finalPois, request, providers)
        Log.d("SelectorPoiProvider", "search providers=$providers categories=$categoriesToFetch skipFilters=${request.skipFilters} -> ${result.size} pois")
        return PoiSearchResult(pois = result, errors = errors)
    }

    private fun applyPostFilters(pois: List<Poi>, request: PoiSearchRequest, providers: Set<PoiProviderType>): List<Poi> {
        return if (!request.skipFilters) {
            StationMapFilters.apply(
                settings = settingsManager.settings.value,
                pois = pois,
                providers = providers,
                skipWhenOnlyOverpass = true
            )
        } else {
            pois
        }
    }

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        return searchResult(request).pois
    }

    override fun clearCache() {
        synchronized(cacheLock) {
            loadedRegions.clear()
            cachedPois.clear()
            poiSeenAtMs.clear()
        }
        routex.clearCache()
        dataGouvPrixCarburant.clearCache()
        gasApi.clearCache()
        dataGouv.clearCache()
        dataGouvElec.clearCache()
        openChargeMap.clearCache()
        chargy.clearCache()
        fastned.clearCache()
        dkv.clearCache()
        ecoMovement.clearCache()
        openVanCamp.clearCache()
        portugalDgeg.clearCache()
        netherlandsAnwb.clearCache()
        denmarkFuelpricesDk.clearCache()
        fuelo.clearCache()
        australiaNswFuelCheck.clearCache()
        croatiaMzoe.clearCache()
        finlandPolttoaine.clearCache()
        greeceFuelGr.clearCache()
        irelandPickAPump.clearCache()
        moldovaAnre.clearCache()
        romaniaPeco.clearCache()
        serbiaNis.clearCache()
        mexicoCre.clearCache()
        argentinaEnergia.clearCache()
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

        val radiusKm = viewport?.let {
            radiusKmFromMapViewport(
                latitude,
                longitude,
                it.zoom,
                it.mapWidthPx,
                it.mapHeightPx
            ).coerceIn(1, 50)
        } ?: 10

        val isoCountries = getCountryCodes(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            hasViewport = viewport != null
        )

        val providers = try {
            settings.effectiveProviders(countryCodes = isoCountries)
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

    private suspend fun enrichPriceRatings(pois: List<Poi>): List<Poi> {
        val repo = historyRepo ?: return pois
        val now = System.currentTimeMillis()
        return pois.map { poi ->
            if (poi.poiCategory != PoiCategory.Gas) return@map poi

            // Record current prices for history
            repo.recordFromPoi(poi, now)

            // Attach rating if possible (based on primary fuel)
            val primaryFuel = poi.fuelPrices?.firstOrNull { !it.outOfStock }?.let { MapPoiFilter.fuelNameToId(it.fuelName) }
            val rating = if (primaryFuel != null) {
                repo.getPriceRating(poi.id, primaryFuel, nowMs = now)
            } else null

            if (rating != null) poi.copy(priceRating = rating) else poi
        }
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

        // Determine target countries from search center
        val regions = ParkingRegion.allContaining(centerLat, centerLon)
        if (regions.isEmpty()) return pois

        var enrichedPois = pois
        regions.forEach { region ->
            val iso = region.countryCode
            if (!FuelPriceRegistry.hasReferencePrice(iso)) return@forEach

            val prices = try {
                openVanCampClient.getReferenceFuelPrices(iso)?.takeIf { it.isNotEmpty() } ?: return@forEach
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return@forEach
            }

            enrichedPois = enrichedPois.map { p ->
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
        return enrichedPois
    }
}
