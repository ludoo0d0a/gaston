package fr.geoking.gaston.community.storage

import android.content.Context
import fr.geoking.gaston.community.db.CommunityPoiEntity
import fr.geoking.gaston.community.db.HiddenPoiEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based storage for community POIs and hidden POIs (no Room dependency).
 * Can be replaced with Room when KSP is compatible with the project's Kotlin/AGP setup.
 */
class CommunityPoiStorage(context: Context) {
    private val dir = File(context.filesDir, "community").also { it.mkdirs() }
    private val communityFile = File(dir, "community_poi.json")
    private val hiddenFile = File(dir, "hidden_poi.json")
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    suspend fun getCommunityPois(): List<CommunityPoiEntity> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!communityFile.exists()) return@withContext emptyList()
            runCatching {
                json.decodeFromString<List<CommunityPoiEntity>>(communityFile.readText())
            }.getOrElse { emptyList() }
        }
    }

    suspend fun saveCommunityPois(list: List<CommunityPoiEntity>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            communityFile.writeText(json.encodeToString(list))
        }
    }

    suspend fun getHiddenPois(): List<HiddenPoiEntity> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!hiddenFile.exists()) return@withContext emptyList()
            runCatching {
                json.decodeFromString<List<HiddenPoiEntity>>(hiddenFile.readText())
            }.getOrElse { emptyList() }
        }
    }

    suspend fun saveHiddenPois(list: List<HiddenPoiEntity>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            hiddenFile.writeText(json.encodeToString(list))
        }
    }
}
