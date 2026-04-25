package fr.geoking.gaston.community.db

import kotlinx.serialization.Serializable

/** Saved/favorite POI. poiId is the Poi.id (official or community). Snapshot fields for display when POI is no longer in provider results. */
@Serializable
data class FavoritePoiEntity(
    val poiId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isElectric: Boolean = false,
    val brand: String? = null,
    val createdAt: Long
)
