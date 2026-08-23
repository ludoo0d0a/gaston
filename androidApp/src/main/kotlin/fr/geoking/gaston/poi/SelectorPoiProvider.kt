package fr.geoking.gaston.poi

import android.util.Log
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.isOtherModeActive
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
import fr.geoking.gaston.shared.logging.ProviderTraceEntry
import fr.geoking.gaston.shared.logging.ProviderTracePhase
import fr.geoking.gaston.shared.logging.ProviderTraceStore
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.awaitAll

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
    private val australiaFuelWatch: PoiProvider,
    private val australiaPetrolSpy: PoiProvider,
    private val switzerlandComparis: PoiProvider,
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
    private val usaEia: PoiProvider,
    private val openVanCampClient: OpenVanCampClient,
    private val overpass: PoiProvider,
    private val dataGouvCamping: PoiProvider?,
    private val poiCacheDao: PoiCacheDao,
    private val settingsManager: SettingsManager,
    private val historyRepo: StationPriceHistoryRepository? = null
) : PoiProvider, CoroutineScope {

    override val coroutineContext = SupervisorJob() + Dispatchers.IO

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val hybridProvider by lazy { HybridPoiProvider(dataGouv, dataGouvElec) }

    private val cachedPois = mutableMapOf<String, Poi>()
    private val poiSeenAtMs = mutableMapOf<String, Long>()
    private val loadedRegions = mutableListOf<LoadedPoiRegion>()
    /** Overpass supermarchés fetched only for brand enrich — never shown unless amenity is on. */
    private val supermarketEnrichCache = mutableMapOf<String, Poi>()
    private val supermarketEnrichRegions = mutableListOf<LoadedPoiRegion>()
    private var lastCacheKey: String? = null
    private var lastCleanupAtMs: Long = 0
    private val cacheLock = Any()
    private val flowMutex = Mutex()

    private fun getCountryCodes(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        hasViewport: Boolean
    ): List<String> {
        return if (hasViewport) {
            val latDelta = radiusKm / 111.0
            val lonDelta = radiusKm / (111.0 * cos(latitude * PI / 180.0))
            val latMin = latitude - latDelta
            val latMax = latitude + latDelta
            val lonMin = longitude - lonDelta
            val lonMax = longitude + lonDelta

            val allIn = ParkingRegion.allInViewport(latMin, latMax, lonMin, lonMax)

            // If any region fully contains this viewport, pick the most specific one.
            val containing = allIn.firstOrNull { r ->
                latMin >= r.latMin && latMax <= r.latMax &&
                        lonMin >= r.lonMin && lonMax <= r.lonMax
            }
            if (containing != null) return listOf(containing.countryCode)

            allIn.map { it.countryCode }
        } else {
            listOfNotNull(ParkingRegion.containing(latitude, longitude)?.countryCode)
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
        PoiProviderType.AustraliaFuelWatch -> australiaFuelWatch
        PoiProviderType.AustraliaPetrolSpy -> australiaPetrolSpy
        PoiProviderType.SwitzerlandComparis -> switzerlandComparis
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
        PoiProviderType.UsaEia -> usaEia
        PoiProviderType.Overpass -> overpass
        PoiProviderType.Hybrid -> hybridProvider
    }

    private fun runCacheEviction(nowMs: Long) {
        val regionCutoff = nowMs - POI_CACHE_DISK_RETENTION_MS
        loadedRegions.removeAll { it.loadedAtMs < regionCutoff }
        if (poiSeenAtMs.isNotEmpty()) {
            val expiredPoiIds = poiSeenAtMs
                .mapNotNull { (id, seenAt) ->
                    val poi = cachedPois[id] ?: return@mapNotNull id
                    if (isPoiCacheEntryExpired(poi, seenAt, nowMs)) id else null
                }
                .toSet()
            if (expiredPoiIds.isNotEmpty()) {
                poiSeenAtMs.keys.removeAll(expiredPoiIds)
                expiredPoiIds.forEach { cachedPois.remove(it) }
            }
        }
    }

    private fun readCoverageAndCache(
        request: PoiSearchRequest,
        requiredRadiusKm: Int,
        providers: Set<PoiProviderType>,
        categoriesToFetch: Set<PoiCategory>,
        nowMs: Long,
    ): Pair<PoiCoverageResult, PoiSearchResult?> {
        runCacheEviction(nowMs)
        val coverage = computePoiCoverage(
            regions = loadedRegions,
            centerLat = request.latitude,
            centerLng = request.longitude,
            requiredRadiusKm = requiredRadiusKm,
            providers = providers,
            categoriesToFetch = categoriesToFetch,
            nowMs = nowMs,
        )
        if (coverage.fullyCovered) {
            return coverage to PoiSearchResult(
                pois = applyPostFilters(cachedPois.values.toList(), request, providers),
            )
        }
        return coverage to null
    }

    private fun recordLoadedRegion(
        centerLat: Double,
        centerLng: Double,
        requiredRadiusKm: Int,
        loadedAtMs: Long,
        fetchedProviders: Set<PoiProviderType>,
        fetchedCategories: Set<PoiCategory>,
        maxRegions: Int,
    ) {
        val covering = findCoveringRegion(
            loadedRegions,
            centerLat,
            centerLng,
            requiredRadiusKm,
        )
        val updated = mergeLoadedRegion(
            existing = covering,
            centerLat = centerLat,
            centerLng = centerLng,
            requiredRadiusKm = requiredRadiusKm,
            loadedAtMs = loadedAtMs,
            fetchedProviders = fetchedProviders,
            fetchedCategories = fetchedCategories,
        )
        if (covering != null) {
            val idx = loadedRegions.indexOf(covering)
            if (idx >= 0) loadedRegions[idx] = updated
        } else {
            loadedRegions.add(updated)
        }
        while (loadedRegions.size > maxRegions) {
            val farthest = loadedRegions.maxBy { r ->
                haversineKm(r.centerLat, r.centerLng, centerLat, centerLng)
            }
            loadedRegions.remove(farthest)
        }
    }

    private fun trimPoiCache(centerLat: Double, centerLng: Double, maxPoisInCache: Int) {
        if (cachedPois.size <= maxPoisInCache) return
        val toRemove = cachedPois.values
            .asSequence()
            .map { p -> p.id to approxDistanceKm(centerLat, centerLng, p.latitude, p.longitude) }
            .sortedByDescending { it.second }
            .take(cachedPois.size - maxPoisInCache)
            .map { it.first }
            .toList()
        toRemove.forEach {
            cachedPois.remove(it)
            poiSeenAtMs.remove(it)
        }
    }

    private suspend fun fetchPoisFromProviders(
        request: PoiSearchRequest,
        providerCategories: Map<PoiProviderType, Set<PoiCategory>>,
        allProviders: Set<PoiProviderType>,
    ): Pair<List<Poi>, List<PoiProviderError>> {
        if (providerCategories.isEmpty()) return emptyList<Poi>() to emptyList()

        val results = supervisorScope {
            providerCategories.mapNotNull { (providerType, categories) ->
                if (categories.isEmpty()) return@mapNotNull null
                async {
                    val effectiveRequest = request.copy(categories = categories, skipFilters = true)
                    val activeProvider = getProvider(providerType)
                    val fetchStartMs = System.currentTimeMillis()
                    traceProvider(
                        phase = ProviderTracePhase.FetchStart,
                        message = "search ${categories.joinToString { it.name }}",
                        provider = providerType.name,
                        categories = categories.map { it.name },
                    )
                    val searchResult = try {
                        activeProvider.searchResult(effectiveRequest)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        PoiSearchResult(errors = listOf(PoiProviderError(providerType.name, e.message ?: "Unknown error")))
                    }
                    traceProvider(
                        phase = ProviderTracePhase.FetchEnd,
                        message = "search done",
                        provider = providerType.name,
                        poiCount = searchResult.pois.size,
                        durationMs = System.currentTimeMillis() - fetchStartMs,
                        errors = searchResult.errors.map { "${it.providerName}: ${it.message}" },
                    )

                    val extraPois = if (
                        providerType == PoiProviderType.Overpass &&
                        PoiCategory.CaravanSite in categories &&
                        dataGouvCamping != null &&
                        ParkingRegion.containing(request.latitude, request.longitude)?.countryCode == "FR"
                    ) {
                        try {
                            dataGouvCamping.search(effectiveRequest)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            emptyList<Poi>()
                        }
                    } else {
                        emptyList()
                    }
                    searchResult.pois + extraPois to searchResult.errors
                }
            }.awaitAll()
        }

        val allPois = results.flatMap { it.first }
        val errors = results.flatMap { it.second }

        val merged = PoiMerger.mergePois(allPois)
        val enrichedWithSupermarkets = enrichBrandsFromSupermarkets(
            pois = merged,
            latitude = request.latitude,
            longitude = request.longitude,
            viewport = request.viewport
        )
        val enriched = enrichNationalReferencePrices(
            pois = enrichedWithSupermarkets,
            providers = allProviders,
            centerLat = request.latitude,
            centerLon = request.longitude,
        )
        val rated = enrichPriceRatings(enriched)
        return rated to errors
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
                    poiCacheDao.deleteOldPois(nowCleanup - POI_CACHE_DISK_RETENTION_MS)
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
            traceProvider(
                phase = ProviderTracePhase.Skipped,
                message = "searchFlow: no effective providers",
                countries = isoCountries,
            )
            send(PoiSearchResult())
            return@channelFlow
        }

        val categoriesToFetch = resolveCategoriesToFetch(settings, request.categories)
        traceProviderResolved(
            source = "searchFlow",
            providers = providers,
            countries = isoCountries,
            categories = categoriesToFetch,
            selectionMode = settings.poiProviderSelectionMode.name,
            radiusKm = requiredRadiusKm,
        )

        val nowMs = System.currentTimeMillis()
        val diskMinUpdatedAtMs = nowMs - POI_CACHE_DISK_RETENTION_MS
        val maxRegions = 12 // Increased slightly for persistence
        val maxPoisInCache = 1200

        var isFromMemory = false
        val coverageAndCache = synchronized(cacheLock) {
            lastCacheKey = invalidateRegionCoverageOnProviderSetChange(
                providers = providers,
                lastKey = lastCacheKey,
                loadedRegions = loadedRegions,
            )
            if (settings.disableCache) {
                PoiCoverageResult(coveringRegion = null, missingProviders = providers, missingCategories = categoriesToFetch) to null
            } else {
                readCoverageAndCache(request, requiredRadiusKm, providers, categoriesToFetch, nowMs)
            }
        }
        val coverage = coverageAndCache.first
        var currentAlreadyCoveredResult = coverageAndCache.second
        if (currentAlreadyCoveredResult != null) {
            isFromMemory = true
        }
        if (currentAlreadyCoveredResult == null && !settings.disableCache) {
            // Try persistent cache
            val latDelta = requiredRadiusKm / 111.0
            val lonDelta = requiredRadiusKm / (111.0 * cos(request.latitude * PI / 180.0))
            val dbPois = try {
                poiCacheDao.getPoisInRegion(
                    latMin = request.latitude - latDelta,
                    latMax = request.latitude + latDelta,
                    lonMin = request.longitude - lonDelta,
                    lonMax = request.longitude + lonDelta,
                    minUpdatedAtMs = diskMinUpdatedAtMs,
                ).mapNotNull { entity ->
                    try {
                        val poi = json.decodeFromString<Poi>(entity.poiJson)
                        val seenAt = entity.updatedAtMs
                        if (isPoiCacheEntryExpired(poi, seenAt, nowMs)) null else poi to seenAt
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("SelectorPoiProvider", "Failed to query DB cache", e)
                emptyList()
            }

            val dbPoisForDisplay = dbPois.filter { (poi, _) ->
                poi.poiCategory != PoiCategory.Supermarket || PoiCategory.Supermarket in categoriesToFetch
            }
            if (dbPoisForDisplay.isNotEmpty()) {
                val dbResultPois = synchronized(cacheLock) {
                    PoiMerger.mergeInto(cachedPois, dbPoisForDisplay.map { it.first })
                    dbPoisForDisplay.forEach { (poi, seenAt) -> poiSeenAtMs[poi.id] = seenAt }
                    cachedPois.values.toList()
                }
                traceProvider(
                    phase = ProviderTracePhase.CacheDisk,
                    message = "searchFlow: disk cache primed",
                    effectiveProviders = providers.map { it.name }.sorted(),
                    poiCount = dbResultPois.size,
                )
                currentAlreadyCoveredResult = PoiSearchResult(pois = applyPostFilters(dbResultPois, request, providers))
            }
        }

        if (currentAlreadyCoveredResult != null) {
            send(currentAlreadyCoveredResult)
            if (isFromMemory) {
                traceProvider(
                    phase = ProviderTracePhase.CacheMemory,
                    message = "searchFlow: region covered (memory)",
                    effectiveProviders = providers.map { it.name }.sorted(),
                    poiCount = currentAlreadyCoveredResult.pois.size,
                )
                return@channelFlow
            }
        }

        if (settings.isOtherModeActive() && categoriesToFetch.isEmpty()) {
            send(PoiSearchResult())
            return@channelFlow
        }

        val providersToFetch = if (coverage.geoCovered) {
            providersForIncrementalFetch(providers, coverage.missingProviders, coverage.missingCategories)
        } else {
            providers
        }

        val providerCategories = providersToFetch.associateWith { providerType ->
            if (coverage.geoCovered) {
                if (providerType in coverage.missingProviders) {
                    categoriesToFetch.intersect(getProvider(providerType).supportedCategories())
                } else {
                    coverage.missingCategories.intersect(getProvider(providerType).supportedCategories())
                }
            } else {
                categoriesToFetch.intersect(getProvider(providerType).supportedCategories())
            }
        }.filterValues { it.isNotEmpty() }

        traceProviderFetchPlanned(
            source = "searchFlow",
            effectiveProviders = providers,
            providerCategories = providerCategories,
            geoCovered = coverage.geoCovered,
        )

        if (providerCategories.isEmpty() && coverage.geoCovered) {
            traceProvider(
                phase = ProviderTracePhase.Skipped,
                message = "searchFlow: geo covered, nothing to fetch",
                effectiveProviders = providers.map { it.name }.sorted(),
                poiCount = cachedPois.size,
            )
            send(
                PoiSearchResult(pois = applyPostFilters(cachedPois.values.toList(), request, providers)),
            )
            return@channelFlow
        }

        if (coverage.geoCovered && cachedPois.isNotEmpty()) {
            send(
                PoiSearchResult(pois = applyPostFilters(cachedPois.values.toList(), request, providers)),
            )
        }

        val accumulated = mutableListOf<Poi>()
        val errors = mutableListOf<PoiProviderError>()
        var finalEnriched = listOf<Poi>()

        supervisorScope {
            providerCategories.forEach { (providerType, categoriesForProvider) ->
                launch {
                    val (rated, providerErrors) = fetchPoisFromProviders(
                        request = request,
                        providerCategories = mapOf(providerType to categoriesForProvider),
                        allProviders = providers,
                    )
                    flowMutex.withLock {
                        accumulated.addAll(rated)
                        errors.addAll(providerErrors)
                        val merged = PoiMerger.mergePois(accumulated)
                        finalEnriched = merged

                        val resultToEmit = synchronized(cacheLock) {
                            PoiMerger.mergeInto(cachedPois, rated)
                            rated.forEach { poiSeenAtMs[it.id] = nowMs }
                            PoiSearchResult(
                                pois = applyPostFilters(cachedPois.values.toList(), request, providers),
                                errors = errors.toList(),
                            )
                        }
                        send(resultToEmit)
                    }
                }
            }
        }

        val mergedNow = System.currentTimeMillis()
        synchronized(cacheLock) {
            finalEnriched.forEach { poiSeenAtMs[it.id] = mergedNow }
            cachedPois.values.forEach { p ->
                if (poiSeenAtMs[p.id] == null) poiSeenAtMs[p.id] = mergedNow
            }
            recordLoadedRegion(
                centerLat = request.latitude,
                centerLng = request.longitude,
                requiredRadiusKm = requiredRadiusKm,
                loadedAtMs = mergedNow,
                fetchedProviders = providerCategories.keys,
                fetchedCategories = providerCategories.values.flatten().toSet(),
                maxRegions = maxRegions,
            )
            trimPoiCache(request.latitude, request.longitude, maxPoisInCache)
        }

        traceProvider(
            phase = ProviderTracePhase.Complete,
            message = "searchFlow done",
            effectiveProviders = providers.map { it.name }.sorted(),
            fetchedProviders = providerCategories.keys.map { it.name }.sorted(),
            poiCount = synchronized(cacheLock) { cachedPois.size },
            errors = errors.map { "${it.providerName}: ${it.message}" },
        )

        // Persist on the provider scope (not channelFlow children) so collectors clear promptly.
        val entitiesToPersist = synchronized(cacheLock) { cachedPois.values.toList() }
        this@SelectorPoiProvider.launch {
            try {
                val entities = entitiesToPersist.map { p ->
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

        if (providers.isEmpty()) {
            traceProvider(
                phase = ProviderTracePhase.Skipped,
                message = "searchResult: no effective providers",
                countries = isoCountries,
            )
            return PoiSearchResult()
        }

        val categoriesToFetch = resolveCategoriesToFetch(settings, request.categories)
        traceProviderResolved(
            source = "searchResult",
            providers = providers,
            countries = isoCountries,
            categories = categoriesToFetch,
            selectionMode = settings.poiProviderSelectionMode.name,
            radiusKm = requiredRadiusKm,
        )

        val nowMs = System.currentTimeMillis()
        val diskMinUpdatedAtMs = nowMs - POI_CACHE_DISK_RETENTION_MS
        val maxRegions = 12
        val maxPoisInCache = 1200

        val coverageAndCache = synchronized(cacheLock) {
            lastCacheKey = invalidateRegionCoverageOnProviderSetChange(
                providers = providers,
                lastKey = lastCacheKey,
                loadedRegions = loadedRegions,
            )
            if (settings.disableCache) {
                PoiCoverageResult(coveringRegion = null, missingProviders = providers, missingCategories = categoriesToFetch) to null
            } else {
                readCoverageAndCache(request, requiredRadiusKm, providers, categoriesToFetch, nowMs)
            }
        }
        val coverage = coverageAndCache.first
        coverageAndCache.second?.let {
            traceProvider(
                phase = ProviderTracePhase.CacheMemory,
                message = "searchResult: region covered (memory)",
                effectiveProviders = providers.map { it.name }.sorted(),
                poiCount = it.pois.size,
            )
            return it
        }

        // Try persistent cache
        if (settings.disableCache) {
            val providersToFetch = providers
            val providerCategories = providersToFetch.associateWith { providerType ->
                categoriesToFetch.intersect(getProvider(providerType).supportedCategories())
            }.filterValues { it.isNotEmpty() }

            val (rated, errors) = fetchPoisFromProviders(
                request = request,
                providerCategories = providerCategories,
                allProviders = providers,
            )
            return PoiSearchResult(pois = applyPostFilters(rated, request, providers), errors = errors)
        }

        val latDelta = requiredRadiusKm / 111.0
        val lonDelta = requiredRadiusKm / (111.0 * cos(request.latitude * PI / 180.0))
        val dbPois = try {
            poiCacheDao.getPoisInRegion(
                latMin = request.latitude - latDelta,
                latMax = request.latitude + latDelta,
                lonMin = request.longitude - lonDelta,
                lonMax = request.longitude + lonDelta,
                minUpdatedAtMs = diskMinUpdatedAtMs,
            ).mapNotNull { entity ->
                try {
                    val poi = json.decodeFromString<Poi>(entity.poiJson)
                    val seenAt = entity.updatedAtMs
                    if (isPoiCacheEntryExpired(poi, seenAt, nowMs)) null else poi to seenAt
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to query DB cache", e)
            emptyList()
        }

        if (dbPois.isNotEmpty()) {
            val dbPoisForDisplay = dbPois.filter { (poi, _) ->
                poi.poiCategory != PoiCategory.Supermarket || PoiCategory.Supermarket in categoriesToFetch
            }
            if (dbPoisForDisplay.isNotEmpty()) {
                synchronized(cacheLock) {
                    PoiMerger.mergeInto(cachedPois, dbPoisForDisplay.map { it.first })
                    dbPoisForDisplay.forEach { (poi, seenAt) -> poiSeenAtMs[poi.id] = seenAt }
                }
            }
        }

        if (settings.isOtherModeActive() && categoriesToFetch.isEmpty()) {
            return PoiSearchResult()
        }

        val providersToFetch = if (coverage.geoCovered) {
            providersForIncrementalFetch(providers, coverage.missingProviders, coverage.missingCategories)
        } else {
            providers
        }

        val providerCategories = providersToFetch.associateWith { providerType ->
            if (coverage.geoCovered) {
                if (providerType in coverage.missingProviders) {
                    categoriesToFetch.intersect(getProvider(providerType).supportedCategories())
                } else {
                    coverage.missingCategories.intersect(getProvider(providerType).supportedCategories())
                }
            } else {
                categoriesToFetch.intersect(getProvider(providerType).supportedCategories())
            }
        }.filterValues { it.isNotEmpty() }

        traceProviderFetchPlanned(
            source = "searchResult",
            effectiveProviders = providers,
            providerCategories = providerCategories,
            geoCovered = coverage.geoCovered,
        )

        if (providerCategories.isEmpty() && coverage.geoCovered) {
            traceProvider(
                phase = ProviderTracePhase.Skipped,
                message = "searchResult: geo covered, nothing to fetch",
                effectiveProviders = providers.map { it.name }.sorted(),
                poiCount = cachedPois.size,
            )
            return PoiSearchResult(pois = applyPostFilters(cachedPois.values.toList(), request, providers))
        }

        val (rated, errors) = fetchPoisFromProviders(
            request = request,
            providerCategories = providerCategories,
            allProviders = providers,
        )

        val mergedNow = System.currentTimeMillis()
        synchronized(cacheLock) {
            PoiMerger.mergeInto(cachedPois, rated)
            rated.forEach { poiSeenAtMs[it.id] = mergedNow }
            cachedPois.values.forEach { p ->
                if (poiSeenAtMs[p.id] == null) poiSeenAtMs[p.id] = mergedNow
            }
            recordLoadedRegion(
                centerLat = request.latitude,
                centerLng = request.longitude,
                requiredRadiusKm = requiredRadiusKm,
                loadedAtMs = mergedNow,
                fetchedProviders = providerCategories.keys,
                fetchedCategories = providerCategories.values.flatten().toSet(),
                maxRegions = maxRegions,
            )
            trimPoiCache(request.latitude, request.longitude, maxPoisInCache)
        }

        // Persist to DB
        try {
            val entitiesToPersist = synchronized(cacheLock) { cachedPois.values.toList() }
            val entities = entitiesToPersist.map { p ->
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
        traceProvider(
            phase = ProviderTracePhase.Complete,
            message = "searchResult done",
            effectiveProviders = providers.map { it.name }.sorted(),
            fetchedProviders = providerCategories.keys.map { it.name }.sorted(),
            poiCount = result.size,
            errors = errors.map { "${it.providerName}: ${it.message}" },
        )
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

    override suspend fun clearCache() {
        synchronized(cacheLock) {
            loadedRegions.clear()
            cachedPois.clear()
            poiSeenAtMs.clear()
            supermarketEnrichCache.clear()
            supermarketEnrichRegions.clear()
            lastCacheKey = null
        }

        try {
            poiCacheDao.clearCache()
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to clear DB cache", e)
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
        australiaFuelWatch.clearCache()
        australiaPetrolSpy.clearCache()
        switzerlandComparis.clearCache()
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
        usaEia.clearCache()
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

        if (providers.isEmpty()) {
            traceProvider(
                phase = ProviderTracePhase.Skipped,
                message = "getGasStations: no effective providers",
                countries = isoCountries,
            )
            return emptyList()
        }

        traceProviderResolved(
            source = "getGasStations",
            providers = providers,
            countries = isoCountries,
            categories = setOf(PoiCategory.Gas),
            selectionMode = settings.poiProviderSelectionMode.name,
            radiusKm = radiusKm,
        )

        val request = PoiSearchRequest(
            latitude = latitude,
            longitude = longitude,
            viewport = viewport,
            categories = setOf(PoiCategory.Gas),
            skipFilters = true,
        )
        val providerCategories = providers.associateWith { providerType ->
            setOf(PoiCategory.Gas).intersect(getProvider(providerType).supportedCategories())
        }.filterValues { it.isNotEmpty() }
        val (fetchedPois, _) = fetchPoisFromProviders(
            request = request,
            providerCategories = providerCategories,
            allProviders = providers,
        )

        var result = PoiMerger.mergePois(fetchedPois)
        result = enrichBrandsFromSupermarkets(
            pois = result,
            latitude = latitude,
            longitude = longitude,
            viewport = viewport
        )
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

    private fun isProviderTraceEnabled(): Boolean =
        settingsManager.settings.value.debugLoggingEnabled

    private fun traceProvider(
        phase: ProviderTracePhase,
        message: String,
        effectiveProviders: List<String> = emptyList(),
        fetchedProviders: List<String> = emptyList(),
        countries: List<String> = emptyList(),
        categories: List<String> = emptyList(),
        provider: String? = null,
        poiCount: Int? = null,
        durationMs: Long? = null,
        errors: List<String> = emptyList(),
    ) {
        if (!isProviderTraceEnabled()) return
        ProviderTraceStore.add(
            ProviderTraceEntry(
                id = System.nanoTime().toString(),
                timestamp = System.currentTimeMillis(),
                phase = phase,
                message = message,
                effectiveProviders = effectiveProviders,
                fetchedProviders = fetchedProviders,
                countries = countries,
                categories = categories,
                provider = provider,
                poiCount = poiCount,
                durationMs = durationMs,
                errors = errors,
            ),
        )
    }

    private fun traceProviderResolved(
        source: String,
        providers: Set<PoiProviderType>,
        countries: List<String>,
        categories: Set<PoiCategory>,
        selectionMode: String,
        radiusKm: Int,
    ) {
        traceProvider(
            phase = ProviderTracePhase.Resolved,
            message = "$source ($selectionMode, r=${radiusKm}km)",
            effectiveProviders = providers.map { it.name }.sorted(),
            countries = countries.distinct().sorted(),
            categories = categories.map { it.name }.sorted(),
        )
    }

    private fun traceProviderFetchPlanned(
        source: String,
        effectiveProviders: Set<PoiProviderType>,
        providerCategories: Map<PoiProviderType, Set<PoiCategory>>,
        geoCovered: Boolean,
    ) {
        val plan = providerCategories.entries.joinToString { (p, cats) ->
            "${p.name}:${cats.joinToString { it.name }}"
        }
        traceProvider(
            phase = ProviderTracePhase.FetchPlanned,
            message = "$source geoCovered=$geoCovered → $plan",
            effectiveProviders = effectiveProviders.map { it.name }.sorted(),
            fetchedProviders = providerCategories.keys.map { it.name }.sorted(),
            categories = providerCategories.values.flatten().map { it.name }.distinct().sorted(),
        )
    }

    private suspend fun enrichBrandsFromSupermarkets(
        pois: List<Poi>,
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val gasStations = pois.filter { it.poiCategory == PoiCategory.Gas }
        if (gasStations.isEmpty()) return pois

        val hasAnyNoBrand = gasStations.any { PoiMerger.hasNoBrand(it) }
        if (!hasAnyNoBrand) return pois

        val radiusKm = viewport?.let { v ->
            radiusKmFromMapViewport(latitude, longitude, v.zoom, v.mapWidthPx, v.mapHeightPx).coerceIn(1, 50)
        } ?: 10

        val nowMs = System.currentTimeMillis()
        val latDelta = radiusKm / 111.0
        val lonDelta = radiusKm / (111.0 * cos(latitude * PI / 180.0))
        val latMin = latitude - latDelta
        val latMax = latitude + latDelta
        val lonMin = longitude - lonDelta
        val lonMax = longitude + lonDelta

        val coverage = synchronized(cacheLock) {
            computePoiCoverage(
                regions = supermarketEnrichRegions,
                centerLat = latitude,
                centerLng = longitude,
                requiredRadiusKm = radiusKm,
                providers = setOf(PoiProviderType.Overpass),
                categoriesToFetch = setOf(PoiCategory.Supermarket),
                nowMs = nowMs
            )
        }

        var supermarkets = synchronized(cacheLock) {
            supermarketEnrichCache.values.filter {
                it.latitude in latMin..latMax && it.longitude in lonMin..lonMax
            }
        }

        if (!coverage.fullyCovered || supermarkets.isEmpty()) {
            val fetchedSupermarkets = try {
                overpass.search(
                    PoiSearchRequest(
                        latitude = latitude,
                        longitude = longitude,
                        viewport = viewport,
                        categories = setOf(PoiCategory.Supermarket),
                        skipFilters = true
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("SelectorPoiProvider", "Failed to fetch supermarkets from Overpass", e)
                emptyList()
            }.filter { it.poiCategory == PoiCategory.Supermarket }

            if (fetchedSupermarkets.isNotEmpty()) {
                synchronized(cacheLock) {
                    fetchedSupermarkets.forEach { supermarketEnrichCache[it.id] = it }
                    val covering = findCoveringRegion(
                        supermarketEnrichRegions,
                        latitude,
                        longitude,
                        radiusKm,
                    )
                    val updated = mergeLoadedRegion(
                        existing = covering,
                        centerLat = latitude,
                        centerLng = longitude,
                        requiredRadiusKm = radiusKm,
                        loadedAtMs = nowMs,
                        fetchedProviders = setOf(PoiProviderType.Overpass),
                        fetchedCategories = setOf(PoiCategory.Supermarket),
                    )
                    if (covering != null) {
                        val idx = supermarketEnrichRegions.indexOf(covering)
                        if (idx >= 0) supermarketEnrichRegions[idx] = updated
                    } else {
                        supermarketEnrichRegions.add(updated)
                    }
                    while (supermarketEnrichRegions.size > 12) {
                        val farthest = supermarketEnrichRegions.maxBy { r ->
                            haversineKm(r.centerLat, r.centerLng, latitude, longitude)
                        }
                        supermarketEnrichRegions.remove(farthest)
                    }
                }
                supermarkets = (supermarkets + fetchedSupermarkets).distinctBy { it.id }
            }
        }

        return PoiMerger.enrichBrandsFromSupermarkets(pois, supermarkets)
    }
}
