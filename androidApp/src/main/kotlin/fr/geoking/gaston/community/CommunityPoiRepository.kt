package fr.geoking.gaston.community

import fr.geoking.gaston.poi.Poi

/**
 * Local (and later sync) storage for community POIs and hidden official POIs.
 * All access goes through this interface so Firebase can be added later.
 */
interface CommunityPoiRepository {
    suspend fun getCommunityPoisInArea(lat: Double, lng: Double, radiusKm: Double): List<Poi>
    /** Official POI ids that are overridden by a community POI in this area (to hide from base list). */
    suspend fun getCommunityLinkedOfficialIdsInArea(lat: Double, lng: Double, radiusKm: Double): Set<String>
    suspend fun getHiddenOfficialIds(): Set<String>
    suspend fun addCommunityPoi(poi: Poi, linkedOfficialId: String? = null)
    suspend fun updateCommunityPoi(id: String, poi: Poi)
    suspend fun removeCommunityPoi(id: String)
    suspend fun hideOfficialPoi(externalPoiId: String)
    suspend fun unhideOfficialPoi(externalPoiId: String)
    suspend fun getCommunityPoi(id: String): Poi?
}
