package fr.geoking.gaston.poi

import fr.geoking.gaston.persistence.OsmFuelEnrichRegionEntity
import fr.geoking.gaston.shared.location.approxDistanceKm

/** Max radius for a single Overpass fuel-zone prefetch (map viewport or detail). */
const val OSM_FUEL_ENRICH_MAX_ZONE_KM = 15

/** Default zone radius when enriching one station with no covering cache. */
const val OSM_FUEL_ENRICH_DEFAULT_ZONE_KM = 3

/** Match OSM fuel node to a DataGouv/GasAPI station. */
const val OSM_FUEL_ENRICH_MATCH_KM = 0.1

/**
 * Room/map id prefix for Overpass fuel rows stored only for detail enrich.
 * Must never appear as map pins (keeps DataGouv/GasAPI names + brand icons).
 */
const val OSM_FUEL_ENRICH_ID_PREFIX = "osm-fuel-enrich:"

/** True for enrich-cache-only OSM fuel rows (current prefix or legacy `osm:` contamination). */
fun isOsmFuelEnrichCachePoi(poi: Poi): Boolean {
    if (poi.id.startsWith(OSM_FUEL_ENRICH_ID_PREFIX)) return true
    // Legacy rows written before the dedicated prefix — treat OSM Gas as enrich-only
    // unless the caller explicitly includes Overpass as a map provider.
    return poi.poiCategory == PoiCategory.Gas &&
        poi.id.startsWith("osm:") &&
        (poi.source?.contains("OpenStreetMap", ignoreCase = true) == true)
}

/**
 * Disk hydrate filter for the map/list cache. Enrich-only OSM fuel must not pollute pins.
 */
fun shouldIncludeDiskPoiOnMap(
    poi: Poi,
    providers: Set<PoiProviderType>,
    categoriesToFetch: Set<PoiCategory>,
): Boolean {
    if (poi.poiCategory == PoiCategory.Supermarket && PoiCategory.Supermarket !in categoriesToFetch) {
        return false
    }
    if (poi.id.startsWith(OSM_FUEL_ENRICH_ID_PREFIX)) return false
    if (isOsmFuelEnrichCachePoi(poi) && PoiProviderType.Overpass !in providers) return false
    return true
}

fun toOsmFuelEnrichCachePoi(osmFuel: Poi): Poi {
    val rawId = osmFuel.id.removePrefix("osm:").removePrefix(OSM_FUEL_ENRICH_ID_PREFIX)
    return osmFuel.copy(id = "$OSM_FUEL_ENRICH_ID_PREFIX$rawId")
}

/**
 * Pick the best Overpass fuel POI for [station] from a zone result list.
 * Prefer matching [Poi.refId], else nearest within [OSM_FUEL_ENRICH_MATCH_KM].
 */
fun matchOsmFuelForGasStation(station: Poi, osmFuelPois: List<Poi>): Poi? {
    if (osmFuelPois.isEmpty()) return null
    val byRef = station.refId?.takeIf { it.isNotBlank() }?.let { ref ->
        osmFuelPois.firstOrNull { it.refId == ref }
    }
    if (byRef != null) return byRef
    return osmFuelPois
        .map { candidate ->
            candidate to approxDistanceKm(
                station.latitude,
                station.longitude,
                candidate.latitude,
                candidate.longitude,
            )
        }
        .filter { (_, km) -> km <= OSM_FUEL_ENRICH_MATCH_KM }
        .minByOrNull { it.second }
        ?.first
}

fun osmFuelEnrichFlightKey(latitude: Double, longitude: Double, radiusKm: Int): String {
    val lat = (latitude * 100.0).toInt()
    val lon = (longitude * 100.0).toInt()
    return "$lat,$lon,$radiusKm"
}

fun toOsmFuelEnrichLoadedRegion(entity: OsmFuelEnrichRegionEntity): LoadedPoiRegion =
    LoadedPoiRegion(
        centerLat = entity.centerLat,
        centerLng = entity.centerLng,
        maxRadiusKmLoaded = entity.maxRadiusKmLoaded,
        loadedAtMs = entity.loadedAtMs,
        loadedProviders = setOf(PoiProviderType.Overpass),
        loadedCategories = setOf(PoiCategory.Gas),
        categoryLoadedAtMs = mapOf(PoiCategory.Gas to entity.loadedAtMs),
    )

/** True when a map/list radius is small enough for one Overpass fuel-zone prefetch. */
fun shouldPrefetchOsmFuelZone(requiredRadiusKm: Int): Boolean =
    requiredRadiusKm in 1..OSM_FUEL_ENRICH_MAX_ZONE_KM
