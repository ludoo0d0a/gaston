package fr.geoking.gaston.api.traffic

/**
 * Minimal parser for CITA DATEX II MeasuredDataPublication XML.
 * Extracts measurement site records: road, direction, location, speed.
 * Legacy DATEX II helper; [CitaTrafficProvider] uses GeoJSON. Kept for optional DATEX tooling.
 */
object CitaDatexParser {

    private val siteRefId = Regex("""measurementSiteReference[^>]*id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val timeDefault = Regex("""<measurementTimeDefault>([^<]+)</measurementTimeDefault>""", RegexOption.IGNORE_CASE)
    private val roadNumber = Regex("""<roadNumber>([^<]+)</roadNumber>""", RegexOption.IGNORE_CASE)
    private val directionBound = Regex("""<directionBoundOnLinearSection>([^<]+)</directionBoundOnLinearSection>""", RegexOption.IGNORE_CASE)
    private val latitude = Regex("""<latitude>([^<]+)</latitude>""", RegexOption.IGNORE_CASE)
    private val longitude = Regex("""<longitude>([^<]+)</longitude>""", RegexOption.IGNORE_CASE)
    private val speed = Regex("""<speed>([^<]+)</speed>""", RegexOption.IGNORE_CASE)

    /**
     * Parses CITA DATEX II XML and returns a list of raw site records (one per measurement site).
     * Splits by measurementSiteReference so each block is one site; extracts first occurrence of each field.
     */
    fun parseToRawSites(xml: String): List<CitaRawSite> {
        val sites = mutableListOf<CitaRawSite>()
        val blocks = xml.split(Regex("""<measurementSiteReference\s""", RegexOption.IGNORE_CASE)).drop(1)
        for (block in blocks) {
            val id = siteRefId.find(block)?.groupValues?.get(1) ?: continue
            val timeStr = timeDefault.find(block)?.groupValues?.get(1)
            val road = roadNumber.find(block)?.groupValues?.get(1)?.trim() ?: continue
            val dir = directionBound.find(block)?.groupValues?.get(1)?.trim()
            val lat = latitude.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val lon = longitude.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val speedKmh = speed.find(block)?.groupValues?.get(1)?.toDoubleOrNull()
            sites.add(
                CitaRawSite(
                    siteId = id,
                    roadNumber = road,
                    direction = dir,
                    latitude = lat,
                    longitude = lon,
                    speedKmh = speedKmh,
                    measurementTime = timeStr
                )
            )
        }
        return sites
    }

    /** Raw record from CITA DATEX II (one per measurement site). */
    data class CitaRawSite(
        val siteId: String,
        val roadNumber: String,
        val direction: String?,
        val latitude: Double,
        val longitude: Double,
        val speedKmh: Double?,
        val measurementTime: String?
    )

    /**
     * Parse ISO-8601-ish time to epoch millis. Returns null on failure.
     * Treats as local time (CITA uses +01:00); no TZ conversion.
     */
    fun parseMeasurementTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            val normalized = s.replace("+01:00", "").replace("Z", "").trim()
            val tIdx = normalized.indexOf('T')
            if (tIdx < 0) return null
            val dateParts = normalized.substring(0, tIdx).split("-")
            val timeParts = normalized.substring(tIdx + 1).split(":", ".")
            if (dateParts.size < 3 || timeParts.size < 3) return null
            val y = dateParts[0].toInt()
            val m = dateParts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: return null
            val d = dateParts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31) ?: return null
            val h = timeParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return null
            val min = timeParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: return null
            val sec = timeParts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: return null
            // Epoch days since 1970-01-01 (simplified)
            val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
            val leap = if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 1 else 0
            val dayOfYear = monthDays.getOrElse(m - 1) { 0 } + d + if (m > 2) leap else 0
            val yearDays = (y - 1970) * 365 + (y - 1968) / 4 - (y - 1900) / 100 + (y - 1600) / 400
            val totalDays = yearDays + dayOfYear - 1
            (totalDays * 86400L + h * 3600L + min * 60L + sec) * 1000L
        } catch (_: Exception) {
            null
        }
    }
}
