package fr.geoking.gaston.api.overpass

import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.platform.getSystemLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OverpassTranslationTest {

    @Test
    fun testGetSystemLanguage() {
        val lang = getSystemLanguage()
        assertNotNull(lang)
        assertTrue(lang.isNotEmpty())
    }

    @Test
    fun testOverpassTranslatorTranslateDirect() {
        // Test Category/Display name translation to French
        assertEquals("Toilettes", OverpassTranslator.translate("Toilets", "fr"))
        assertEquals("Eau potable", OverpassTranslator.translate("Drinking water", "fr"))
        assertEquals("Aire de caravanes", OverpassTranslator.translate("Caravan site", "fr"))
        assertEquals("Aire de caravanes", OverpassTranslator.translate("Aire camping-car", "fr"))
        assertEquals("Aire de repos", OverpassTranslator.translate("Rest area", "fr"))
        assertEquals("Station-service", OverpassTranslator.translate("Gas station", "fr"))
        assertEquals("Station de recharge", OverpassTranslator.translate("Charging station", "fr"))
        assertEquals("Échange de batterie", OverpassTranslator.translate("Battery swap", "fr"))

        // Test fallback to English
        assertEquals("Toilets", OverpassTranslator.translate("Toilets", "en"))
        assertEquals("Drinking water", OverpassTranslator.translate("Drinking water", "en"))

        // Test non-translatable values fallback
        assertEquals("Unknown Random Value", OverpassTranslator.translate("Unknown Random Value", "fr"))
    }

    @Test
    fun testCuisineMultiValueTranslation() {
        // Test single cuisine
        assertEquals("Pizza", OverpassTranslator.translate("pizza", "fr"))
        assertEquals("Italien", OverpassTranslator.translate("italian", "fr"))

        // Test semi-colon separated cuisines
        assertEquals("Pizza, Italien", OverpassTranslator.translate("pizza;italian", "fr"))
        assertEquals("Pizza, Italian", OverpassTranslator.translate("pizza;italian", "en"))
        assertEquals("Burgers, Kebab, Chinois", OverpassTranslator.translate("burger; kebab; chinese", "fr"))
    }

    @Test
    fun testOverpassElementLanguageSelection() {
        // Element with default and French translated name
        val elementWithTranslation = OverpassElement(
            id = 12345L,
            lat = 48.8,
            lon = 2.3,
            tags = mapOf(
                "name" to "Welcome Toilets",
                "name:fr" to "Toilettes de Bienvenue",
                "brand" to "TotalEnergies",
                "brand:fr" to "Total Énergie",
                "cuisine" to "burger;pizza",
                "cuisine:fr" to "burger;pizza",
                "operator" to "Tesla",
                "operator:fr" to "Tesla France"
            )
        )

        // French active
        assertEquals("Toilettes de Bienvenue", elementWithTranslation.name("fr"))
        assertEquals("Total Énergie", elementWithTranslation.brand("fr"))
        assertEquals("Tesla France", elementWithTranslation.operator("fr"))

        // English active (or anything else without explicit translation, falls back to default)
        assertEquals("Welcome Toilets", elementWithTranslation.name("en"))
        assertEquals("TotalEnergies", elementWithTranslation.brand("en"))
        assertEquals("Tesla", elementWithTranslation.operator("en"))
    }

    @Test
    fun testOverpassElementFallbackTranslation() {
        // Element without name:fr but name is a generic term that our translator knows
        val genericToiletElement = OverpassElement(
            id = 54321L,
            lat = 48.8,
            lon = 2.3,
            tags = mapOf(
                "name" to "toilets",
                "cuisine" to "pizza;italian"
            )
        )

        // API has no name:fr, so apiName is "toilets". Local translator translates to "Toilettes"
        val apiName = genericToiletElement.name("fr")
        assertEquals("toilets", apiName)

        val translatedName = OverpassTranslator.translate(apiName, "fr")
        assertEquals("Toilettes", translatedName)

        // API has no cuisine:fr, but cuisine is "pizza;italian". Local translator translates to "Pizza, Italien"
        val apiCuisine = genericToiletElement.cuisine("fr")
        val translatedCuisine = OverpassTranslator.translate(apiCuisine, "fr")
        assertEquals("Pizza, Italien", translatedCuisine)
    }

    @Test
    fun testNewAmenitiesTranslationAndMapping() {
        // Test Post Box
        assertEquals("Boîte aux lettres", OverpassTranslator.translate("Post box", "fr"))
        assertEquals("Boîte aux lettres", OverpassTranslator.translate("post_box", "fr"))
        assertEquals("Post box", OverpassTranslator.translate("Post box", "en"))
        assertEquals("Post box", OverpassTranslator.translate("post_box", "en"))

        // Test Water Body
        assertEquals("Lac / Étang", OverpassTranslator.translate("Water body", "fr"))
        assertEquals("Lac / Étang", OverpassTranslator.translate("water", "fr"))
        assertEquals("Water body", OverpassTranslator.translate("Water body", "en"))
        assertEquals("Water body", OverpassTranslator.translate("water", "en"))

        // Test Cafe
        assertEquals("Café", OverpassTranslator.translate("Cafe", "fr"))
        assertEquals("Café", OverpassTranslator.translate("cafe", "fr"))
        assertEquals("Cafe", OverpassTranslator.translate("Cafe", "en"))
        assertEquals("Cafe", OverpassTranslator.translate("cafe", "en"))

        // Test Supermarket
        assertEquals("Supermarché", OverpassTranslator.translate("Supermarket", "fr"))
        assertEquals("Supermarché", OverpassTranslator.translate("supermarket", "fr"))
        assertEquals("Supérette", OverpassTranslator.translate("convenience", "fr"))
        assertEquals("Supermarket", OverpassTranslator.translate("Supermarket", "en"))
        assertEquals("Supermarket", OverpassTranslator.translate("supermarket", "en"))
        assertEquals("Convenience store", OverpassTranslator.translate("convenience", "en"))
    }
}
