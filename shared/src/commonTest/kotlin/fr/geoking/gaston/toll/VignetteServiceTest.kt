package fr.geoking.gaston.toll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VignetteServiceTest {

    @Test
    fun requiresVignette_knownVignetteCountries() {
        listOf("AT", "BG", "CH", "CZ", "HU", "MD", "RO", "SI", "SK").forEach { code ->
            assertTrue(VignetteService.requiresVignette(code), "Expected vignette for $code")
        }
    }

    @Test
    fun requiresVignette_caseInsensitiveAndTrimmed() {
        assertTrue(VignetteService.requiresVignette(" at "))
        assertTrue(VignetteService.requiresVignette("ch"))
    }

    @Test
    fun requiresVignette_nonVignetteCountries() {
        listOf("FR", "DE", "BE", "IT", "ES", "NL", "PL", "HR").forEach { code ->
            assertFalse(VignetteService.requiresVignette(code), "Expected no vignette for $code")
        }
    }

    @Test
    fun requiresVignette_nullOrBlank_returnsFalse() {
        assertFalse(VignetteService.requiresVignette(null))
        assertFalse(VignetteService.requiresVignette(""))
        assertFalse(VignetteService.requiresVignette("   "))
    }

    @Test
    fun infoFor_includesOfficialOnlineShopUrls() {
        val expected = mapOf(
            "AT" to "https://shop.asfinag.at/",
            "BG" to "https://www.bgtoll.bg/en/e-vignette",
            "CH" to "https://www.e-vignette.ch/",
            "CZ" to "https://edalnice.gov.cz/en",
            "HU" to "https://ematrica.nemzetiutdij.hu/",
            "MD" to "https://evinieta.gov.md/",
            "RO" to "https://www.erovinieta.ro/",
            "SI" to "https://evinjeta.dars.si/en/",
            "SK" to "https://eznamka.sk/en",
        )
        expected.forEach { (code, url) ->
            val info = assertNotNull(VignetteService.infoFor(code), "Missing info for $code")
            assertEquals(url, info.onlineShopUrl)
            assertTrue(info.onlineShopUrl.startsWith("https://"), "Shop URL must be https for $code")
        }
    }

    @Test
    fun infoFor_offlineInfo_whenAvailable() {
        val at = assertNotNull(VignetteService.infoFor("AT"))
        assertEquals("https://www.asfinag.at/en/toll/vignette/digital-vignette/", at.offlineInfoUrl)

        val ch = assertNotNull(VignetteService.infoFor("CH"))
        assertEquals("https://www.bazg.admin.ch/en/faq-vignette-and-e-vignette-purchase", ch.offlineInfoUrl)

        // Digital-only official shops: no separate offline page
        assertNull(VignetteService.infoFor("CZ")?.offlineInfoUrl)
        assertNull(VignetteService.infoFor("HU")?.offlineInfoUrl)
        assertNull(VignetteService.infoFor("SI")?.offlineInfoUrl)
    }

    @Test
    fun infoFor_unknownCountry_returnsNull() {
        assertNull(VignetteService.infoFor("FR"))
        assertNull(VignetteService.infoFor(null))
    }
}
