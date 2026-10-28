package fr.geoking.gaston.map.download

import fr.geoking.gaston.auto.map.mapsforge.DownloadProgress
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream

/**
 * HTTP file download with optional resume via `Range` / `206 Partial Content`.
 * Partial bytes are kept in [tempFile] across failures so a later call can continue.
 */
object ResumableHttpDownloader {

    suspend fun download(
        client: HttpClient,
        url: String,
        tempFile: File,
        fileName: String,
        userAgent: String? = null,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File> {
        tempFile.parentFile?.mkdirs()
        var existingBytes = if (tempFile.exists()) tempFile.length().coerceAtLeast(0L) else 0L
        if (existingBytes > 0L && tempFile.length() == 0L) {
            tempFile.delete()
            existingBytes = 0L
        }

        onProgress(DownloadProgress(fileName, existingBytes, -1))

        return try {
            client.prepareGet(url) {
                if (!userAgent.isNullOrBlank()) {
                    header(HttpHeaders.UserAgent, userAgent)
                }
                if (existingBytes > 0L) {
                    header(HttpHeaders.Range, "bytes=$existingBytes-")
                }
            }.execute { response ->
                val code = response.status.value
                when (code) {
                    200 -> {
                        // Server ignored Range (or fresh download) — rewrite from byte 0.
                        if (tempFile.exists()) tempFile.delete()
                        existingBytes = 0L
                    }
                    206 -> {
                        // Resume OK — append.
                    }
                    416 -> {
                        // Requested range not satisfiable: treat existing temp as complete if non-empty.
                        if (existingBytes > 0L) {
                            onProgress(
                                DownloadProgress(
                                    fileName = fileName,
                                    bytesDownloaded = existingBytes,
                                    totalBytes = existingBytes,
                                    isComplete = true,
                                )
                            )
                            return@execute Result.success(tempFile)
                        }
                        val err = "HTTP 416: range not satisfiable"
                        onProgress(DownloadProgress(fileName, 0, -1, error = err))
                        return@execute Result.failure(Exception(err))
                    }
                    else -> {
                        val err = "HTTP Error $code: ${response.status.description}"
                        onProgress(DownloadProgress(fileName, existingBytes, -1, error = err))
                        return@execute Result.failure(Exception(err))
                    }
                }

                val contentLength = response.contentLength() ?: -1L
                val totalBytes = when {
                    code == 206 && contentLength >= 0L -> existingBytes + contentLength
                    code == 206 -> parseContentRangeTotal(response.headers[HttpHeaders.ContentRange])
                        ?: -1L
                    contentLength >= 0L -> contentLength
                    else -> parseContentRangeTotal(response.headers[HttpHeaders.ContentRange]) ?: -1L
                }

                var bytesDownloaded = existingBytes
                val channel = response.bodyAsChannel()
                val append = code == 206 && existingBytes > 0L
                FileOutputStream(tempFile, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        onProgress(
                            DownloadProgress(
                                fileName = fileName,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                            )
                        )
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    val err = "Downloaded file is missing or empty"
                    onProgress(DownloadProgress(fileName, 0, -1, error = err))
                    return@execute Result.failure(Exception(err))
                }

                onProgress(
                    DownloadProgress(
                        fileName = fileName,
                        bytesDownloaded = tempFile.length(),
                        totalBytes = if (totalBytes > 0) totalBytes else tempFile.length(),
                        isComplete = true,
                    )
                )
                Result.success(tempFile)
            }
        } catch (e: Exception) {
            onProgress(
                DownloadProgress(
                    fileName = fileName,
                    bytesDownloaded = if (tempFile.exists()) tempFile.length() else 0L,
                    totalBytes = -1,
                    error = e.message ?: "Download failed",
                )
            )
            Result.failure(e)
        }
    }

    /** Parses `Content-Range: bytes 0-99/1234` → total size `1234`, or null if `*`. */
    internal fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val totalPart = header.substringAfter('/', missingDelimiterValue = "").trim()
        if (totalPart.isEmpty() || totalPart == "*") return null
        return totalPart.toLongOrNull()?.takeIf { it >= 0L }
    }
}
