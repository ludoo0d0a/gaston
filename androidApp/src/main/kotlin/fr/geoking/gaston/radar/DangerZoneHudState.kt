package fr.geoking.gaston.radar

/**
 * HUD banner state for AAC danger-zone alerts.
 */
data class DangerZoneHudState(
    val active: Boolean = false,
    val speedLimitKmH: Int? = null,
)
