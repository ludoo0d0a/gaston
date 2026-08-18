package fr.geoking.gaston.poi

import fr.geoking.gaston.shared.platform.getSystemLanguage

/** Longest first so "gas station" wins over "station". */
private val GENERIC_STATION_PREFIXES = listOf("gas station", "station service", "station")

private data class GenericStationMatch(val city: String?)

/**
 * Localized generic fuel-station title: "Station" / "Gas station",
 * or the same with [city] appended ("Station Paris").
 */
fun genericStationName(city: String? = null, lang: String = getSystemLanguage()): String {
    val label = if (lang.lowercase().startsWith("fr")) "Station" else "Gas station"
    val trimmedCity = city?.trim().orEmpty()
    return if (trimmedCity.isEmpty()) label else "$label $trimmedCity"
}

/**
 * True when [name] is a generic station label in any supported translation,
 * with or without a city suffix ("Station", "Gas station", "Station-service",
 * "Station Paris"). Blank names are generic. Names that contain a known brand
 * ("Station U", "Station Total") are not.
 */
fun isGenericStationName(name: String?): Boolean = matchGenericStationName(name) != null

/** City suffix of a generic station title, or null if none / not generic. */
fun genericStationCity(name: String?): String? = matchGenericStationName(name)?.city

private fun matchGenericStationName(name: String?): GenericStationMatch? {
    if (name.isNullOrBlank()) return GenericStationMatch(city = null)
    if (BrandRegistry.findBrand(name, null) != null) return null

    val normalized = normalizeStationLabel(name)
    val prefix = GENERIC_STATION_PREFIXES.firstOrNull { candidate ->
        normalized == candidate || normalized.startsWith("$candidate ")
    } ?: return null

    val city = extractCitySuffix(name, prefix)
    if (city != null && BrandRegistry.findBrand(city, null) != null) return null
    return GenericStationMatch(city)
}

private fun normalizeStationLabel(raw: String): String {
    return raw.trim().lowercase()
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
}

private fun extractCitySuffix(original: String, prefix: String): String? {
    val prefixPattern = prefix.split(' ').joinToString("[\\s\\-]+") { Regex.escape(it) }
    val match = Regex(
        "^$prefixPattern(?:[\\s\\-]+(.+))?$",
        RegexOption.IGNORE_CASE,
    ).find(original.trim()) ?: return null
    return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}
