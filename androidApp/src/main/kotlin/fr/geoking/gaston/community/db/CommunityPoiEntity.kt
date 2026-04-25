package fr.geoking.gaston.community.db

import kotlinx.serialization.Serializable

/** User-added or user-updated POI stored locally. id uses prefix "community_" for recognition. */
@Serializable
data class CommunityPoiEntity(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val isElectric: Boolean = false,
    val powerKw: Double? = null,
    val operator: String? = null,
    val chargePointCount: Int? = null,
    /** When non-null, this community POI overrides the official POI with this id. */
    val linkedOfficialId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
