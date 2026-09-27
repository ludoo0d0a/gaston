package fr.geoking.gaston.aac

/**
 * Fallback highway detection from a reverse-geocoded thoroughfare / road name
 * when OSM map-matching is unavailable.
 */
object ThoroughfareHighwayHeuristic {
    private val keywords = listOf(
        "autoroute", "motorway", "highway", "interstate",
        "autobahn", "autostrada", "autovía", "autopista",
        "snelweg", "freeway",
    )

    private val frenchOrLetterCode = Regex("^[a-z]\\s?\\d{1,4}\\b")
    private val interstateCode = Regex("^i-\\s?\\d{1,3}\\b")

    fun isLikelyHighway(thoroughfare: String?): Boolean {
        if (thoroughfare.isNullOrBlank()) return false
        val s = thoroughfare.lowercase()
        if (keywords.any { it in s }) return true
        if (frenchOrLetterCode.containsMatchIn(s)) return true
        if (interstateCode.containsMatchIn(s)) return true
        return false
    }
}
