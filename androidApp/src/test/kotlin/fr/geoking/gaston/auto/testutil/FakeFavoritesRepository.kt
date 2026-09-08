package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.Poi

class FakeFavoritesRepository : FavoritesRepository {
    override suspend fun getFavorites(): List<Poi> = emptyList()

    override suspend fun isFavorite(poiId: String): Boolean = false

    override suspend fun addFavorite(poi: Poi) {}

    override suspend fun removeFavorite(poiId: String) {}

    override suspend fun toggleFavorite(poi: Poi): Boolean = false
}
