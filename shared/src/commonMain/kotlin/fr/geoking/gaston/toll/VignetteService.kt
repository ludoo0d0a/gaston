package fr.geoking.gaston.toll

/**
 * Official purchase channels for a country that requires a motorway vignette.
 *
 * Prefer government / motorway-operator shops (not third-party resellers).
 *
 * @param countryCode ISO 3166-1 alpha-2
 * @param onlineShopUrl Official web shop (e-vignette / digital purchase)
 * @param offlineInfoUrl Optional official page for sticker / physical sales points
 */
data class VignetteRequirement(
    val countryCode: String,
    val onlineShopUrl: String,
    val offlineInfoUrl: String? = null,
)

/**
 * Static lookup of European countries that require a time-based motorway vignette
 * (sticker or e-vignette linked to the plate) for light vehicles.
 *
 * Source: European vignette systems as of 2026 (AT, BG, CH, CZ, HU, MD, RO, SI, SK).
 * Countries with distance-based tolls or free motorways return null / false.
 */
object VignetteService {

    private val byCountryCode: Map<String, VignetteRequirement> = listOf(
        VignetteRequirement(
            countryCode = "AT",
            onlineShopUrl = "https://shop.asfinag.at/",
            offlineInfoUrl = "https://www.asfinag.at/en/toll/vignette/digital-vignette/",
        ),
        VignetteRequirement(
            countryCode = "BG",
            onlineShopUrl = "https://www.bgtoll.bg/en/e-vignette",
        ),
        VignetteRequirement(
            countryCode = "CH",
            onlineShopUrl = "https://www.e-vignette.ch/",
            offlineInfoUrl = "https://www.bazg.admin.ch/en/faq-vignette-and-e-vignette-purchase",
        ),
        VignetteRequirement(
            countryCode = "CZ",
            onlineShopUrl = "https://edalnice.gov.cz/en",
        ),
        VignetteRequirement(
            countryCode = "HU",
            onlineShopUrl = "https://ematrica.nemzetiutdij.hu/",
        ),
        VignetteRequirement(
            countryCode = "MD",
            onlineShopUrl = "https://evinieta.gov.md/",
        ),
        VignetteRequirement(
            countryCode = "RO",
            // Official CNIR / gov digitalisation channel for electronic rovinieta
            onlineShopUrl = "https://www.erovinieta.ro/",
        ),
        VignetteRequirement(
            countryCode = "SI",
            onlineShopUrl = "https://evinjeta.dars.si/en/",
        ),
        VignetteRequirement(
            countryCode = "SK",
            onlineShopUrl = "https://eznamka.sk/en",
        ),
    ).associateBy { it.countryCode }

    /** Returns vignette requirement + official shop links, or null if none. */
    fun infoFor(countryCode: String?): VignetteRequirement? {
        val cc = countryCode?.uppercase()?.trim() ?: return null
        return byCountryCode[cc]
    }

    /** Returns true when [countryCode] (ISO 3166-1 alpha-2) requires a motorway vignette. */
    fun requiresVignette(countryCode: String?): Boolean = infoFor(countryCode) != null
}
