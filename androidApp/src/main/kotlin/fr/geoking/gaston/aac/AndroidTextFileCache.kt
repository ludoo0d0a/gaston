package fr.geoking.gaston.aac

import android.content.Context
import java.io.File

/**
 * Android disk cache for AAC CSV payloads (filesDir/aac/).
 */
class AndroidTextFileCache(
    context: Context,
) : TextFileCache {
    private val dir = File(context.filesDir, "aac").also { it.mkdirs() }

    override fun read(key: String): CachedText? {
        val bodyFile = File(dir, "$key.txt")
        val metaFile = File(dir, "$key.meta")
        if (!bodyFile.exists() || !metaFile.exists()) return null
        return try {
            val metaParts = metaFile.readText().lines()
            val storedAt = metaParts.getOrNull(0)?.toLongOrNull() ?: return null
            val version = metaParts.getOrNull(1)?.takeIf { it.isNotBlank() }
            CachedText(body = bodyFile.readText(), storedAtEpochMs = storedAt, version = version)
        } catch (_: Exception) {
            null
        }
    }

    override fun write(key: String, value: String, version: String?) {
        try {
            File(dir, "$key.txt").writeText(value)
            File(dir, "$key.meta").writeText("${System.currentTimeMillis()}\n${version.orEmpty()}")
        } catch (_: Exception) {
            // Best-effort cache
        }
    }

    override fun clear(key: String) {
        File(dir, "$key.txt").delete()
        File(dir, "$key.meta").delete()
    }
}
