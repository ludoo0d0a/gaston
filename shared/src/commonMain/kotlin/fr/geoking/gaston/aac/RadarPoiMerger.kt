package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm

/**
 * Deduplicates nearby radar markers so the map shows one fused amenity pin per control.
 */
object RadarPoiMerger {
    const val DEDUP_RADIUS_METERS = RadarOsmEnricher.DEFAULT_MATCH_RADIUS_METERS

    fun dedupeRadarAmenities(pois: List<Poi>): List<Poi> {
        val radars = pois.filter { it.poiCategory == PoiCategory.Radar }
        if (radars.size <= 1) return pois

        val primary = radars.filter {
            it.source == "LufopOpenSpeedCam" ||
                it.source == "FranceRadars" ||
                it.source == "LuxembourgRadars"
        }
        val secondary = radars.filter {
            it.source != "LufopOpenSpeedCam" &&
                it.source != "FranceRadars" &&
                it.source != "LuxembourgRadars"
        }
        if (primary.isEmpty() || secondary.isEmpty()) return pois

        val dropSecondaryIds = mutableSetOf<String>()
        for (s in secondary) {
            val nearPrimary = primary.any { p ->
                haversineKm(s.latitude, s.longitude, p.latitude, p.longitude) * 1000.0 <= DEDUP_RADIUS_METERS
            }
            if (nearPrimary) dropSecondaryIds.add(s.id)
        }
        if (dropSecondaryIds.isEmpty()) return pois
        return pois.filterNot { it.id in dropSecondaryIds }
    }
}
