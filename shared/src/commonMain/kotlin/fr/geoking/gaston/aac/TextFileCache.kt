package fr.geoking.gaston.aac

/**
 * Optional persistent cache for large text payloads (CSV).
 * Implemented on Android; null = memory-only.
 */
interface TextFileCache {
    fun read(key: String): CachedText?
    fun write(key: String, value: String, version: String?)
    fun clear(key: String)
}

data class CachedText(
    val body: String,
    val storedAtEpochMs: Long,
    val version: String?,
)
