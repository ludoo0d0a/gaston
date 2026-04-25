package fr.geoking.gaston.community

import fr.geoking.gaston.poi.Poi

/**
 * Local (and later sync) storage for favorite/saved stations.
 */
interface FavoritesRepository {
    suspend fun getFavorites(): List<Poi>
    suspend fun isFavorite(poiId: String): Boolean
    suspend fun addFavorite(poi: Poi)
    suspend fun removeFavorite(poiId: String)
    suspend fun toggleFavorite(poi: Poi): Boolean
}
