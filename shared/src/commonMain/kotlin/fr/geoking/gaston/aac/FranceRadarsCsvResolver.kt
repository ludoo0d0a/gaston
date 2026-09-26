package fr.geoking.gaston.aac

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves the latest CSV resource URL for the France fixed radars dataset on data.gouv.fr.
 * Falls back to [fallbackUrl] when the API is unreachable.
 */
class FranceRadarsCsvResolver(
    private val client: HttpClient,
    private val datasetApiUrl: String = DATASET_API_URL,
    private val fallbackUrl: String,
) {
    companion object {
        const val DATASET_API_URL =
            "https://www.data.gouv.fr/api/1/datasets/liste-des-radars-fixes-en-france/"
        const val DATASET_SLUG = "liste-des-radars-fixes-en-france"
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class ResolvedCsv(
        val url: String,
        val resourceId: String?,
        val title: String?,
        val lastModified: String?,
    )

    suspend fun resolveLatestCsvUrl(): ResolvedCsv {
        return try {
            val body = client.get(datasetApiUrl).bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val resources = root["resources"]?.jsonArray.orEmpty()
            val csvResources = resources.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val format = obj["format"]?.jsonPrimitive?.content?.lowercase()
                val mime = obj["mime"]?.jsonPrimitive?.content?.lowercase()
                val isCsv = format == "csv" || mime?.contains("csv") == true
                if (!isCsv) return@mapNotNull null
                val url = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.content
                val title = obj["title"]?.jsonPrimitive?.content
                val lastModified = obj["last_modified"]?.jsonPrimitive?.content
                    ?: obj["created_at"]?.jsonPrimitive?.content
                ResolvedCsv(url = url, resourceId = id, title = title, lastModified = lastModified)
            }
            csvResources.maxByOrNull { it.lastModified.orEmpty() }
                ?: ResolvedCsv(url = fallbackUrl, resourceId = null, title = null, lastModified = null)
        } catch (_: Exception) {
            ResolvedCsv(url = fallbackUrl, resourceId = null, title = null, lastModified = null)
        }
    }
}
