package fr.geoking.gaston.road

import fr.geoking.gaston.aac.RoadNetworkClass

/**
 * Vector road geometries for map overlays and radar blue corridors.
 * Implementations may hit Overpass, Room, or both.
 */
interface RoadGeometrySource {
    suspend fun fetchRoadsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 1.0,
    ): List<RoadPolyline>

    /**
     * Roads usable for a radar of [roadClass] within [radiusMeters] of the point
     * (corridor snap / walk).
     */
    suspend fun fetchRoadsAround(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        roadClass: RoadNetworkClass,
    ): List<RoadPolyline>

    suspend fun clearCache()
}
