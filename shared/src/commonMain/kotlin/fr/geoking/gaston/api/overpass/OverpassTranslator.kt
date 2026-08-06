package fr.geoking.gaston.api.overpass

/**
 * Handles translations of categories, generic names, and cuisine values
 * for supported languages (English and French). Fallbacks to the default
 * value from the API if no translation is available.
 */
object OverpassTranslator {
    private val translations = mapOf(
        "fr" to mapOf(
            // Categories / Display Names
            "Toilets" to "Toilettes",
            "Drinking water" to "Eau potable",
            "Camping" to "Camping",
            "Caravan site" to "Aire de caravanes",
            "Aire camping-car" to "Aire de caravanes",
            "Picnic site" to "Aire de pique-nique",
            "Picnic area" to "Aire de pique-nique",
            "Truck stop" to "Aire de poids lourds",
            "Rest area" to "Aire de repos",
            "Restaurant" to "Restaurant",
            "Fast food" to "Restauration rapide",
            "Radar" to "Radar",
            "Parking" to "Parking",
            "Viewpoint" to "Point de vue",
            "Gas station" to "Station-service",
            "Charging station" to "Station de recharge",
            "Battery swap" to "Échange de batterie",
            "Post box" to "Boîte aux lettres",
            "Water body" to "Lac / Étang",
            "Cafe" to "Café",
            "Supermarket" to "Supermarché",

            // Common generic OSM names
            "toilets" to "Toilettes",
            "drinking_water" to "Eau potable",
            "picnic_table" to "Table de pique-nique",
            "picnic_site" to "Aire de pique-nique",
            "water_point" to "Point d'eau",
            "water_tap" to "Robinet d'eau",
            "rest_area" to "Aire de repos",
            "post_box" to "Boîte aux lettres",
            "water" to "Lac / Étang",
            "cafe" to "Café",
            "supermarket" to "Supermarché",
            "convenience" to "Supérette",

            // Cuisine values
            "burger" to "Burgers",
            "pizza" to "Pizza",
            "italian" to "Italien",
            "french" to "Français",
            "chinese" to "Chinois",
            "kebab" to "Kebab",
            "sandwich" to "Sandwichs",
            "asian" to "Asiatique",
            "regional" to "Régional",
            "traditional" to "Traditionnel",
            "fast_food" to "Restauration rapide",
            "mexican" to "Mexicain",
            "sushi" to "Sushi",
            "indian" to "Indien",
            "crepe" to "Crêperie"
        ),
        "en" to mapOf(
            "Toilets" to "Toilets",
            "Drinking water" to "Drinking water",
            "Camping" to "Camping",
            "Caravan site" to "Caravan site",
            "Aire camping-car" to "Caravan site",
            "Picnic site" to "Picnic area",
            "Picnic area" to "Picnic area",
            "Truck stop" to "Truck stop",
            "Rest area" to "Rest area",
            "Restaurant" to "Restaurant",
            "Fast food" to "Fast food",
            "Radar" to "Radar",
            "Parking" to "Parking",
            "Viewpoint" to "Viewpoint",
            "Gas station" to "Gas station",
            "Charging station" to "Charging station",
            "Battery swap" to "Battery swap",
            "Post box" to "Post box",
            "Water body" to "Water body",
            "Cafe" to "Cafe",
            "Supermarket" to "Supermarket",

            "toilets" to "Toilets",
            "drinking_water" to "Drinking water",
            "picnic_table" to "Picnic table",
            "picnic_site" to "Picnic area",
            "water_point" to "Water point",
            "water_tap" to "Water tap",
            "rest_area" to "Rest area",
            "post_box" to "Post box",
            "water" to "Water body",
            "cafe" to "Cafe",
            "supermarket" to "Supermarket",
            "convenience" to "Convenience store",

            "burger" to "Burger",
            "pizza" to "Pizza",
            "italian" to "Italian",
            "french" to "French",
            "chinese" to "Chinese",
            "kebab" to "Kebab",
            "sandwich" to "Sandwich",
            "asian" to "Asian",
            "regional" to "Regional",
            "traditional" to "Traditional",
            "fast_food" to "Fast food",
            "mexican" to "Mexican",
            "sushi" to "Sushi",
            "indian" to "Indian",
            "crepe" to "Creperie"
        )
    )

    /**
     * Translates the given string [value] to the supported [lang] (en or fr).
     * If not found, falls back to the original value.
     */
    fun translate(value: String?, lang: String): String? {
        if (value.isNullOrBlank()) return null
        val cleanLang = lang.lowercase().trim()
        val langMap = translations[cleanLang] ?: translations["en"]!!

        // Exact case-insensitive match on the map
        val translated = langMap[value] ?: langMap[value.lowercase().trim()]
        if (translated != null) return translated

        // For composite keys like "cuisine=pizza;italian", translate elements individually
        if (value.contains(";")) {
            val parts = value.split(";").map { it.trim() }
            val translatedParts = parts.map { translate(it, lang) ?: it }
            return translatedParts.joinToString(", ")
        }

        return value
    }
}
