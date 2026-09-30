package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm

/**
 * Prefers data.gouv FranceRadars markers over nearby OSM speed_camera duplicates
 * so the map shows one fused amenity pin per control.
 */
object RadarPoiMerger {
    const val DEDUP_RADIUS_METERS = RadarOsmEnricher.DEFAULT_MATCH_RADIUS_METERS

    fun dedupeRadarAmenities(pois: List<Poi>): List<Poi> {
        val radars = pois.filter { it.poiCategory == PoiCategory.Radar }
        if (radars.size <= 1) return pois

        val france = radars.filter { it.source == "FranceRadars" }
        val osm = radars.filter { it.source != "FranceRadars" }
        if (france.isEmpty() || osm.isEmpty()) return pois

        val dropOsmIds = mutableSetOf<String>()
        for (o in osm) {
            val nearFr = france.any { f ->
                haversineKm(o.latitude, o.longitude, f.latitude, f.longitude) * 1000.0 <= DEDUP_RADIUS_METERS
            }
            if (nearFr) dropOsmIds.add(o.id)
        }
        if (dropOsmIds.isEmpty()) return pois
        return pois.filterNot { it.id in dropOsmIds }
    }
}
