package fr.geoking.gaston.api.weather

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Latest RainViewer radar tile URL template (public Weather Maps API, no key).
 * Template uses `{z}/{x}/{y}` placeholders for MapLibre / XYZ clients.
 */
data class RainViewerRadarFrame(
    /** Full tile URL template, e.g. `https://…/v2/radar/…/256/{z}/{x}/{y}/2/1_1.png` */
    val tileUrlTemplate: String,
    val timestampUnix: Long,
)

/**
 * Fetches [https://api.rainviewer.com/public/weather-maps.json](https://api.rainviewer.com/public/weather-maps.json).
 */
class RainViewerMapsClient(
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latestRadarFrame(tileSize: Int = 256): RainViewerRadarFrame? {
        val response = client.get(MAPS_JSON)
        val body = response.bodyAsText()
        if (response.status.value != 200) return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val host = root["host"]?.jsonPrimitive?.content ?: return null
        val past = root["radar"]?.jsonObject?.get("past")?.jsonArray ?: return null
        val last = past.lastOrNull()?.jsonObject ?: return null
        val path = last["path"]?.jsonPrimitive?.content ?: return null
        val time = last["time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        // color=2, smooth=1, snow=1 — precipitation + snow coloring as-is from RainViewer
        val template = "$host$path/$tileSize/{z}/{x}/{y}/2/1_1.png"
        return RainViewerRadarFrame(tileUrlTemplate = template, timestampUnix = time)
    }

    companion object {
        const val MAPS_JSON = "https://api.rainviewer.com/public/weather-maps.json"
    }
}
