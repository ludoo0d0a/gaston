package fr.geoking.gaston.community.storage

import android.content.Context
import fr.geoking.gaston.community.db.FavoritePoiEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based storage for favorite POIs (no Room dependency).
 */
class FavoritePoiStorage(context: Context) {
    private val dir = File(context.filesDir, "community").also { it.mkdirs() }
    private val file = File(dir, "favorite_poi.json")
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    suspend fun getFavorites(): List<FavoritePoiEntity> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching {
                json.decodeFromString<List<FavoritePoiEntity>>(file.readText())
            }.getOrElse { emptyList() }
        }
    }

    suspend fun saveFavorites(list: List<FavoritePoiEntity>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            file.writeText(json.encodeToString(list))
        }
    }
}
