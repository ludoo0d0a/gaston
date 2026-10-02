package fr.geoking.gaston

import android.content.Context
import coil3.SingletonImageLoader
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.tools.debugbar.model.CacheStatRow
import fr.geoking.tools.debugbar.model.CacheStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

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

    /** Best-effort cache snapshot for the debug-bar Cache tab. */
    suspend fun collectStats(context: Context): CacheStats = withContext(Dispatchers.IO) {
        val byType = mutableListOf<CacheStatRow>()

        try {
            val imageLoader = SingletonImageLoader.get(context)
            val memory = imageLoader.memoryCache
            if (memory != null) {
                byType += CacheStatRow(
                    label = "Images (memory)",
                    sizeBytes = memory.size.toLong(),
                    itemCount = memory.keys.size,
                )
            }
            val disk = imageLoader.diskCache
            if (disk != null) {
                byType += CacheStatRow(
                    label = "Images (disk)",
                    sizeBytes = disk.size,
                    itemCount = diskCount(disk.directory.toFile()),
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error reading Coil cache stats", e)
        }

        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                val (bytes, count) = dirStats(cacheDir)
                byType += CacheStatRow(
                    label = "App cacheDir",
                    sizeBytes = bytes,
                    itemCount = count,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error reading cacheDir stats", e)
        }

        CacheStats(
            totalSizeBytes = byType.sumOf { it.sizeBytes },
            totalItemCount = byType.sumOf { it.itemCount },
            byType = byType,
            byHost = emptyList(),
        )
    }

    private fun diskCount(dir: File): Int {
        if (!dir.exists()) return 0
        return dir.walkTopDown().count { it.isFile }
    }

    private fun dirStats(dir: File): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                bytes += file.length()
                count++
            }
        }
        return bytes to count
    }
}
