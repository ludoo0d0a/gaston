package fr.geoking.gaston.poi

/**
 * Registry of fuel and electric charging brands, used for normalization and icon lookup.
 * This logic is shared to allow the [PoiMerger] to prioritize brands that have icons in the app.
 */
object BrandRegistry {

    /** Lookup key (lowercase, normalized) -> company display name. */
    val BRAND_NAMES = mapOf(
        "total" to "Total",
        "totalenergies" to "Total",
        "bp" to "BP",
        "shell" to "Shell",
        "esso" to "Esso",
        "esso express" to "Esso",
        "eni" to "Eni",
        "repsol" to "Repsol",
        "omv" to "OMV",
        "avia" to "AVIA",
        "q8" to "Q8",
        "agip" to "Agip",
        "carrefour" to "Carrefour",
        "leclerc" to "Leclerc",
        "e.leclerc" to "Leclerc",
        "auchan" to "Auchan",
        "intermarche" to "Intermarché",
        "casino" to "Casino",
        "superu" to "Super U",
        "indigo" to "Indigo",
        "rel" to "REL",
        "rel.metz" to "REL",
        "circle k" to "Circle K",
        "eurogarages" to "Euro Garages",
        "aral" to "Aral",
        "jet" to "Jet",
        "elf" to "Elf",
        "migrol" to "Migrol",
        "coop" to "Coop",
        "migros" to "Migros",
        "tesla" to "Tesla",
        "ionity" to "Ionity",
        "fastned" to "Fastned",
        "allego" to "Allego",
        "lidl" to "Lidl",
        "chargy" to "Chargy",
        "atlante" to "Atlante",
        "zunder" to "Zunder",
        "freshmile" to "Freshmile",
        "systeme u" to "Système U",
        "cooperative u" to "Coopérative U",
        "match" to "Match",
        "supermarche match" to "Supermarché Match",
        "powerdot" to "Powerdot",
        "driveco" to "Driveco",
        "spar" to "SPAR",
        "gulf" to "Gulf",
        "monoprix" to "Monoprix",
    )

    /** brand_id (lowercase) -> is gas station brand. */
    val GAS_BRANDS = setOf(
        "total", "totalenergies", "bp", "shell", "esso", "esso express", "eni", "repsol", "omv", "avia",
        "q8", "agip", "carrefour", "leclerc", "auchan", "intermarche", "casino", "rel", "rel.metz",
        "circle k", "eurogarages", "aral", "jet", "elf", "migrol", "coop", "migros",
        "superu", "systeme u", "match", "supermarche match",
        "spar", "gulf", "monoprix"
    )

    /** brand_id (lowercase) -> is electric charging brand. */
    val ELECTRIC_BRANDS = setOf(
        "tesla", "ionity", "fastned", "allego", "lidl", "chargy", "atlante", "zunder", "total", "totalenergies",
        "freshmile", "superu", "systeme u", "cooperative u", "match", "supermarche match",
        "powerdot", "driveco", "carrefour", "leclerc", "auchan"
    )

    /** Set of brand keys that have a dedicated icon in the application. */
    val BRANDS_WITH_ICONS = setOf(
        "total", "totalenergies", "bp", "shell", "esso", "esso express", "eni", "repsol", "omv", "avia",
        "q8", "agip", "eurogarages", "jet", "elf", "migrol", "coop", "migros", "rel", "rel.metz",
        "circle k", "aral", "carrefour", "leclerc", "e.leclerc", "auchan", "intermarche", "casino",
        "tesla", "ionity", "fastned", "allego", "lidl", "chargy", "atlante", "zunder", "freshmile",
        "superu", "systeme u", "cooperative u", "match", "supermarche match", "powerdot", "driveco"
        , "spar", "gulf", "monoprix"
    )

    /** Returns true if the brand has a dedicated icon. */
    fun hasIcon(brandId: String?): Boolean {
        if (brandId.isNullOrBlank()) return false
        val normalized = normalizeLookupKey(brandId)
        if (BRANDS_WITH_ICONS.contains(normalized)) return true

        // Also check if any key in BRANDS_WITH_ICONS is contained in the normalized brandId (fuzzy match)
        return BRANDS_WITH_ICONS.any { normalized.contains(it) }
    }

    /**
     * Attempts to find a known brand from the provided brand and name.
     * Checks the brand field first, then looks for keywords in the name.
     */
    fun findBrand(name: String?, brand: String?): String? {
        val normalizedBrand = brand?.let { normalizeLookupKey(it) }

        // 1. Check if the brand field itself is a known brand (highest priority)
        if (!normalizedBrand.isNullOrBlank()) {
            val key = BRAND_NAMES.keys.sortedByDescending { it.length }
                .find { normalizedBrand == it || normalizedBrand.contains(it) }
            if (key != null) return BRAND_NAMES[key]
        }

        // 2. Search for brand keywords in the name
        if (!name.isNullOrBlank()) {
            val normalizedName = normalizeLookupKey(name)
            val key = BRAND_NAMES.keys.sortedByDescending { it.length }.find { normalizedName.contains(it) }
            if (key != null) return BRAND_NAMES[key]
        }

        // 3. Fallback to the original brand name if it's not generic
        val generic = setOf("station", "independant", "independant (gms)", "sans enseigne", "autoroute", "route")
        if (normalizedBrand != null && normalizedBrand !in generic) {
            return brand
        }

        return null
    }

    /**
     * Strip accents, lowercase, and map common API / commercial variants to a single lookup key.
     */
    fun normalizeLookupKey(raw: String): String {
        // Simple manual diacritics folding for common French/Spanish/German/etc. letters
        // since java.text.Normalizer is not available in KMP commonMain.
        var out = raw.trim().lowercase()
        out = out
            .replace('à', 'a').replace('á', 'a').replace('â', 'a').replace('ä', 'a').replace('ã', 'a').replace('å', 'a')
            .replace('ç', 'c')
            .replace('è', 'e').replace('é', 'e').replace('ê', 'e').replace('ë', 'e')
            .replace('ì', 'i').replace('í', 'i').replace('î', 'i').replace('ï', 'i')
            .replace('ñ', 'n')
            .replace('ò', 'o').replace('ó', 'o').replace('ô', 'o').replace('ö', 'o').replace('õ', 'o')
            .replace('ù', 'u').replace('ú', 'u').replace('û', 'u').replace('ü', 'u')
            .replace('ý', 'y').replace('ÿ', 'y')
            .replace('œ', 'o').replace('æ', 'a')

        val base = out.replace(Regex("\\s+"), " ")

        return when {
            base.contains("systeme u") || base.contains("super u") || base.contains("hyper u") ||
                base.contains("u express") || base.contains("station u") -> "superu"
            base.contains("intermarche") -> "intermarche"
            base.contains("casino") -> "casino"
            base.contains("indigo") -> "indigo"
            base.contains("total") && base.contains("access") -> "totalenergies"
            base.contains("esso") && base.contains("express") -> "esso express"
            else -> base.replace(". ", ".").trim()
        }
    }
}
