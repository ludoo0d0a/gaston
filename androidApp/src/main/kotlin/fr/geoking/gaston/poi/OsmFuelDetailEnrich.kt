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
