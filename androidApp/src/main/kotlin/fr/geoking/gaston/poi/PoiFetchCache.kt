package fr.geoking.gaston.poi

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.categoryFromAmenityId
import fr.geoking.gaston.effectiveAllowedCategories
import fr.geoking.gaston.isOtherModeActive
import fr.geoking.gaston.shared.location.haversineKm

/** Fuel / EV station data (prices, availability) — refresh often. */
const val POI_CACHE_TTL_ENERGY_MS = 12L * 60 * 60 * 1000L

/** OSM amenities (parking, toilets, …) — stable for days. */
const val POI_CACHE_TTL_AMENITY_MS = 3L * 24 * 60 * 60 * 1000L

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

fun isPoiCacheEntryExpired(poi: Poi, seenAtMs: Long, nowMs: Long): Boolean =
    nowMs - seenAtMs > cacheTtlMsForPoi(poi)

fun categoryCacheStillFresh(
    category: PoiCategory,
    region: LoadedPoiRegion,
    nowMs: Long,
): Boolean {
    if (category !in region.loadedCategories) return false
    val loadedAt = region.categoryLoadedAtMs[category] ?: region.loadedAtMs
    return nowMs - loadedAt <= cacheTtlMsForCategory(category)
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

fun resolveCategoriesToFetch(settings: AppSettings): Set<PoiCategory> {
    val amenityIds = settings.selectedOverpassAmenityTypes + settings.cacheWarmAmenityTypes
    val categories = amenityIds.mapNotNull { categoryFromAmenityId(it) }.toMutableSet()

    if (settings.isOtherModeActive()) {
        categories += settings.effectiveAllowedCategories()
        return categories
    }

    categories += PoiCategory.Gas
    categories += PoiCategory.Irve
    categories += settings.effectiveAllowedCategories()
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
    val needsAmenityFetch = missingCategories.any { it != PoiCategory.Gas && it != PoiCategory.Irve }
    if (needsAmenityFetch && PoiProviderType.Overpass in allProviders) {
        result += PoiProviderType.Overpass
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
