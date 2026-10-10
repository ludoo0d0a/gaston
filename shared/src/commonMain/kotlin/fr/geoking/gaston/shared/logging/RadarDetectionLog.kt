package fr.geoking.gaston.shared.logging

import co.touchlab.kermit.Logger

/**
 * Developer-facing radar / danger-zone detection logs.
 *
 * Written to [DebugLogStore] with [HOST] so the debug-bar Network tab can filter the chip `radar`.
 * Also emitted on Kermit tag `radar` for logcat filtering.
 */
object RadarDetectionLog {
    const val HOST = "radar"

    private val radarLog: Logger by lazy { Logger.withTag(HOST) }

    enum class Event {
        /** Vehicle entered the extended danger zone (alert fired). */
        ENTRY,
        /** Vehicle within near-radar distance of zone center. */
        NEAR,
        /** Vehicle left the extended zone after an alert. */
        EXIT,
        /** Developer test alert. */
        TEST,
    }

    fun log(
        event: Event,
        zoneId: String,
        kind: String,
        roadClass: String,
        radiusMeters: Double,
        distanceMeters: Double,
        vmaKmH: Int?,
        speedKmH: Double,
        isAhead: Boolean,
        isOverspeed: Boolean,
        source: String? = null,
        enabled: Boolean,
    ) {
        if (!enabled) return

        val vma = vmaKmH?.toString() ?: "-"
        val distM = distanceMeters.toInt()
        val radiusM = radiusMeters.toInt()
        val speed = speedKmH.toInt()
        val now = System.currentTimeMillis()

        val summary =
            "radar $event zone=$zoneId kind=$kind road=$roadClass " +
                "radius=${radiusM}m dist=${distM}m vma=$vma speed=$speed " +
                "ahead=$isAhead overspeed=$isOverspeed" +
                (source?.let { " source=$it" } ?: "")

        radarLog.i { summary }

        val query = buildString {
            append("vma=").append(vma)
            append("&dist=").append(distM).append("m")
            append("&radius=").append(radiusM).append("m")
            append("&road=").append(roadClass)
            append("&kind=").append(kind)
            append("&speed=").append(speed)
            append("&ahead=").append(isAhead)
            append("&overspeed=").append(isOverspeed)
            if (!source.isNullOrBlank()) {
                append("&source=").append(source)
            }
        }

        DebugLogStore.addLog(
            NetworkLog(
                id = "radar-${event.name.lowercase()}-$zoneId-$now",
                url = "radar://${event.name.lowercase()}/$zoneId?$query",
                host = HOST,
                method = event.name,
                requestHeaders = emptyMap(),
                requestBody = null,
                responseHeaders = null,
                responseBody = summary,
                statusCode = if (isOverspeed) 400 else 200,
                durationMs = 0L,
                timestamp = now,
            ),
        )
    }
}
