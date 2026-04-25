package fr.geoking.gaston.parking

/**
 * Per-source parking API. Implementations fetch from LiveParking, ParkAPI, OSM, etc.
 * [ParkingApiSelector] chooses which providers to call based on user's country/region.
 */
interface ParkingProvider {
    val id: String

    /** True if this provider can return data for the given point (e.g. within coverage bbox). */
    fun covers(lat: Double, lon: Double): Boolean

    /**
     * Countries/regions this provider serves (live or city data). Empty = no region restriction.
     * Used by the selector to prefer country-specific APIs; [isFallbackOnly] providers are used only when no other serves the region.
     */
    fun servedRegions(): Set<ParkingRegion> = emptySet()

    /**
     * When true, this provider is only used when no [servedRegions] provider covers the user's region (e.g. OSM as fallback).
     */
    fun isFallbackOnly(): Boolean = false

    /**
     * Parking POIs near (lat, lon) within [radiusMeters].
     * Return empty list on error or unsupported; callers merge from multiple providers.
     */
    suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi>
}
