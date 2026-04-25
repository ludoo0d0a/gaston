package fr.geoking.gaston

import android.content.Context
import coil3.SingletonImageLoader
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.poi.PoiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

object CacheManager {
    suspend fun clearAllCaches(context: Context) {
        // 1. POI Markers (Memory-only, fast)
        PoiMarkerHelper.clearCache()

        // POI Provider Cache (Memory-only, fast)
        try {
            val poiProvider = GlobalContext.get().getOrNull<PoiProvider>()
            poiProvider?.clearCache()
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error clearing PoiProvider cache", e)
        }

        // 2. Network Debug Logs (Memory-only, fast)
        try {
            DebugLogStore.clearLogs()
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error clearing DebugLogStore", e)
        }

        // 3. Disk-intensive operations
        withContext(Dispatchers.IO) {
            // Coil Image Cache
            try {
                val imageLoader = SingletonImageLoader.get(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Error clearing Coil cache", e)
            }

            // Cache Directory (temp files, voice recordings, etc.)
            try {
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    val files = cacheDir.listFiles()
                    if (files != null) {
                        for (file in files) {
                            file.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Error clearing cache directory", e)
            }
        }
    }
}
