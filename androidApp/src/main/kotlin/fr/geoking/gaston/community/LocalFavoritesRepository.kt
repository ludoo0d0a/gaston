package fr.geoking.gaston.community

import fr.geoking.gaston.community.db.FavoritePoiEntity
import fr.geoking.gaston.community.storage.FavoritePoiStorage
import fr.geoking.gaston.poi.Poi

class LocalFavoritesRepository(
    private val storage: FavoritePoiStorage
) : FavoritesRepository {

    override suspend fun getFavorites(): List<Poi> =
        storage.getFavorites().map { it.toPoi() }

    override suspend fun isFavorite(poiId: String): Boolean =
        storage.getFavorites().any { it.poiId == poiId }

    override suspend fun addFavorite(poi: Poi) {
        val list = storage.getFavorites().toMutableList()
        list.removeAll { it.poiId == poi.id }
        list.add(
            FavoritePoiEntity(
                poiId = poi.id,
                name = poi.name,
                address = poi.address,
                latitude = poi.latitude,
                longitude = poi.longitude,
                isElectric = poi.isElectric,
                brand = poi.brand,
                createdAt = System.currentTimeMillis()
            )
        )
        storage.saveFavorites(list)
    }

    override suspend fun removeFavorite(poiId: String) {
        val list = storage.getFavorites().filter { it.poiId != poiId }
        storage.saveFavorites(list)
    }

    override suspend fun toggleFavorite(poi: Poi): Boolean {
        return if (isFavorite(poi.id)) {
            removeFavorite(poi.id)
            false
        } else {
            addFavorite(poi)
            true
        }
    }
}

private fun FavoritePoiEntity.toPoi(): Poi = Poi(
    id = poiId,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    brand = brand,
    isElectric = isElectric
)
