package fr.geoking.gaston.community.db

import kotlinx.serialization.Serializable

/** User chose to hide an official POI from the map (by its provider id). */
@Serializable
data class HiddenPoiEntity(
    val id: Long = 0,
    val externalPoiId: String,
    val createdAt: Long
)
