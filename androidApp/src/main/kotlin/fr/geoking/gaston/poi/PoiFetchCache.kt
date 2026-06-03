package fr.geoking.gaston.poi

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.categoryFromAmenityId
import fr.geoking.gaston.effectiveAllowedCategories
import fr.geoking.gaston.isOtherModeActive
import fr.geoking.gaston.shared.location.haversineKm
import java.util.Calendar

/** Fuel / EV station data (prices, availability) — refreshed daily for prices, static data kept for 7 days. */
const val POI_CACHE_TTL_ENERGY_MS = 7L * 24 * 60 * 60 * 1000L

/** OSM amenities (parking, toilets, …) — stable for days. */
const val POI_CACHE_TTL_AMENITY_MS = 7L * 24 * 60 * 60 * 1000L

/** Disk retention and geo-region bounds use the longest TTL. */
const val POI_CACHE_DISK_RETENTION_MS = POI_CACHE_TTL_AMENITY_MS

/**
 * Pure helpers for POI fetch cache keys, category unions, and region coverage.
 * Used by [SelectorPoiProvider] and unit tests.
 */
data class LoadedPoiRegion(
    val centerLat: Double,
    val centerLng: Double,
    val maxRadiusKmLoaded: Int,
    val loadedAtMs: Long,
    val loadedProviders: Set<PoiProviderType> = emptySet(),
    val loadedCategories: Set<PoiCategory> = emptySet(),
    val categoryLoadedAtMs: Map<PoiCategory, Long> = emptyMap(),
)

fun cacheTtlMsForCategory(category: PoiCategory): Long = when (category) {
    PoiCategory.Gas, PoiCategory.Irve -> POI_CACHE_TTL_ENERGY_MS
    else -> POI_CACHE_TTL_AMENITY_MS
}

fun poiPrimaryCategory(poi: Poi): PoiCategory {
    poi.poiCategory?.let { return it }
    if (poi.isElectric) return PoiCategory.Irve
    if (!poi.fuelPrices.isNullOrEmpty()) return PoiCategory.Gas
    return PoiCategory.Parking
}

fun cacheTtlMsForPoi(poi: Poi): Long = cacheTtlMsForCategory(poiPrimaryCategory(poi))

fun isPoiCacheEntryExpired(poi: Poi, seenAtMs: Long, nowMs: Long): Boolean {
    // Static data should be cached for 7 days.
    // We allow price data to be stale (e.g. from previous days) for immediate display.
    // Background refresh via categoryCacheStillFresh still triggers a new fetch daily.
    return nowMs - seenAtMs > cacheTtlMsForPoi(poi)
}

fun categoryCacheStillFresh(
    category: PoiCategory,
    region: LoadedPoiRegion,
    nowMs: Long,
): Boolean {
    if (category !in region.loadedCategories) return false
    val loadedAt = region.categoryLoadedAtMs[category] ?: region.loadedAtMs

    // Prices should be cached for the current day.
    if (category == PoiCategory.Gas || category == PoiCategory.Irve || category == PoiCategory.BatterySwap) {
        if (!isSameDay(loadedAt, nowMs)) return false
    }

    return nowMs - loadedAt <= cacheTtlMsForCategory(category)
}

fun isSameDay(t1: Long, t2: Long): Boolean {
    val dayMillis = 24 * 60 * 60 * 1000L
    if (Math.abs(t1 - t2) > dayMillis) return false

    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

data class PoiCoverageResult(
    val coveringRegion: LoadedPoiRegion?,
    val missingProviders: Set<PoiProviderType>,
    val missingCategories: Set<PoiCategory>,
) {
    val geoCovered: Boolean get() = coveringRegion != null
    val fullyCovered: Boolean get() =
        geoCovered && missingProviders.isEmpty() && missingCategories.isEmpty()
}

fun buildPoiFetchKey(providers: Set<PoiProviderType>): String =
    providers.sortedBy { it.name }.joinToString(",") { it.name }

/**
 * Returns true when [providers] changed vs [lastKey] and [loadedRegions] was cleared so the next
 * search refetches for the new provider set instead of treating stale region metadata as covered.
 */
fun invalidateRegionCoverageOnProviderSetChange(
    providers: Set<PoiProviderType>,
    lastKey: String?,
    loadedRegions: MutableList<LoadedPoiRegion>,
): String {
    val key = buildPoiFetchKey(providers)
    if (lastKey != key) {
        loadedRegions.clear()
    }
    return key
}

fun resolveCategoriesToFetch(settings: AppSettings, extraCategories: Set<PoiCategory> = emptySet()): Set<PoiCategory> {
    val categories = settings.cacheWarmAmenityTypes
        .mapNotNull { categoryFromAmenityId(it) }
        .toMutableSet()

    // Always fetch both energy types for caching purposes if not in Other mode
    if (!settings.isOtherModeActive()) {
        categories.add(PoiCategory.Gas)
        categories.add(PoiCategory.Irve)
        categories.add(PoiCategory.BatterySwap)
    }

    // amenities follow selection + vehicle.
    categories += settings.effectiveAllowedCategories()
    categories += extraCategories
    return categories
}

fun findCoveringRegion(
    regions: List<LoadedPoiRegion>,
    centerLat: Double,
    centerLng: Double,
    requiredRadiusKm: Int,
): LoadedPoiRegion? = regions.firstOrNull { region ->
    region.maxRadiusKmLoaded >= requiredRadiusKm &&
        haversineKm(centerLat, centerLng, region.centerLat, region.centerLng) <=
        (region.maxRadiusKmLoaded - requiredRadiusKm).toDouble() + 0.5
}

fun computePoiCoverage(
    regions: List<LoadedPoiRegion>,
    centerLat: Double,
    centerLng: Double,
    requiredRadiusKm: Int,
    providers: Set<PoiProviderType>,
    categoriesToFetch: Set<PoiCategory>,
    nowMs: Long,
): PoiCoverageResult {
    val covering = findCoveringRegion(regions, centerLat, centerLng, requiredRadiusKm)
        ?: return PoiCoverageResult(
            coveringRegion = null,
            missingProviders = providers,
            missingCategories = categoriesToFetch,
        )

    val missingProviders = providers - covering.loadedProviders
    val missingCategories = categoriesToFetch.filter { category ->
        !categoryCacheStillFresh(category, covering, nowMs)
    }.toSet()
    return PoiCoverageResult(
        coveringRegion = covering,
        missingProviders = missingProviders,
        missingCategories = missingCategories,
    )
}

/** Providers to call when geo is covered but categories/providers are incomplete. */
fun providersForIncrementalFetch(
    allProviders: Set<PoiProviderType>,
    missingProviders: Set<PoiProviderType>,
    missingCategories: Set<PoiCategory>,
): Set<PoiProviderType> {
    val result = missingProviders.toMutableSet()
    if (PoiCategory.Gas in missingCategories) {
        result += allProviders.filter { it.providesFuel }
    }
    if (PoiCategory.Irve in missingCategories || PoiCategory.BatterySwap in missingCategories) {
        result += allProviders.filter { it.providesElectric || it.providesSwap }
    }
    val needsAmenityFetch = missingCategories.any {
        it !in setOf(PoiCategory.Gas, PoiCategory.Irve, PoiCategory.BatterySwap)
    }
    if (needsAmenityFetch && PoiProviderType.Overpass in allProviders) {
        result += PoiProviderType.Overpass
    }
    // If Overpass is an explicit provider for Gas or Irve, and they are missing, include it.
    if (PoiProviderType.Overpass in allProviders) {
        if (PoiCategory.Gas in missingCategories || PoiCategory.Irve in missingCategories) {
            result += PoiProviderType.Overpass
        }
    }
    return result
}

fun mergeLoadedRegion(
    existing: LoadedPoiRegion?,
    centerLat: Double,
    centerLng: Double,
    requiredRadiusKm: Int,
    loadedAtMs: Long,
    fetchedProviders: Set<PoiProviderType>,
    fetchedCategories: Set<PoiCategory>,
): LoadedPoiRegion {
    val categoryTimes = existing?.categoryLoadedAtMs.orEmpty().toMutableMap()
    existing?.loadedCategories?.forEach { cat ->
        if (cat !in categoryTimes) {
            categoryTimes[cat] = existing.loadedAtMs
        }
    }
    fetchedCategories.forEach { categoryTimes[it] = loadedAtMs }

    if (existing == null) {
        return LoadedPoiRegion(
            centerLat = centerLat,
            centerLng = centerLng,
            maxRadiusKmLoaded = requiredRadiusKm,
            loadedAtMs = loadedAtMs,
            loadedProviders = fetchedProviders,
            loadedCategories = fetchedCategories,
            categoryLoadedAtMs = categoryTimes,
        )
    }
    return existing.copy(
        maxRadiusKmLoaded = maxOf(existing.maxRadiusKmLoaded, requiredRadiusKm),
        loadedAtMs = loadedAtMs,
        loadedProviders = existing.loadedProviders + fetchedProviders,
        loadedCategories = existing.loadedCategories + fetchedCategories,
        categoryLoadedAtMs = categoryTimes,
    )
}
