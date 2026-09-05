package fr.geoking.gaston.api.evpricesfr

import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvPricesFrEnricherTest {

    private val allego = EvPriceBaseline(
        provider = EvPriceProvider.Allego,
        country = "FR",
        currency = "EUR",
        sourceUrl = "https://example.test",
        priceModel = "kwh_fixed_by_power_category",
        values = mapOf(
            "regular_eur_per_kwh" to 0.60,
            "fast_eur_per_kwh" to 0.63,
            "ultra_fast_eur_per_kwh" to 0.73,
        ),
    )

    private val electra = EvPriceBaseline(
        provider = EvPriceProvider.Electra,
        country = "FR",
        currency = "EUR",
        sourceUrl = "https://example.test",
        priceModel = "kwh_range_dynamic",
        values = mapOf(
            "dynamic_min_eur_per_kwh" to 0.39,
            "dynamic_max_eur_per_kwh" to 0.61,
        ),
    )

    @Test
    fun hasUsefulTarification_detectsStructuredAndGeneric() {
        assertTrue(EvPricesFrEnricher.hasUsefulTarification("0,40€ / kWh"))
        assertTrue(EvPricesFrEnricher.hasUsefulTarification("Gratuit"))
        assertFalse(EvPricesFrEnricher.hasUsefulTarification("Payant"))
        assertFalse(EvPricesFrEnricher.hasUsefulTarification(null))
        assertFalse(EvPricesFrEnricher.hasUsefulTarification("  "))
    }

    @Test
    fun matchProvider_fromOperatorBrandName() {
        assertEquals(EvPriceProvider.Ionity, EvPricesFrEnricher.matchProvider("IONITY GmbH", null, null))
        assertEquals(EvPriceProvider.Fastned, EvPricesFrEnricher.matchProvider(null, "Fastned", null))
        assertEquals(EvPriceProvider.Electra, EvPricesFrEnricher.matchProvider(null, null, "Electra Paris Est"))
        assertEquals(EvPriceProvider.TotalEnergies, EvPricesFrEnricher.matchProvider("TotalEnergies Charging Services", null, null))
        assertNull(EvPricesFrEnricher.matchProvider("Izivia", "EDF", "Station municipale"))
    }

    @Test
    fun formatBaseline_picksAllegoTierByPower() {
        val ultra = EvPricesFrEnricher.formatBaseline(allego, powerKw = 300.0)
        assertEquals("≈ 0,73 €/kWh (Allego, tarif publié)", ultra)
        val ac = EvPricesFrEnricher.formatBaseline(allego, powerKw = 22.0)
        assertEquals("≈ 0,60 €/kWh (Allego, tarif publié)", ac)
    }

    @Test
    fun enrich_fillsMissingTarificationInFrance() {
        val poi = Poi(
            id = "1",
            name = "Allego A6",
            address = "Aire",
            latitude = 48.85,
            longitude = 2.35,
            brand = "Allego",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 175.0,
            operator = "Allego",
            irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs")),
            source = "DataGouv",
        )
        val enriched = EvPricesFrEnricher.enrich(listOf(poi), listOf(allego, electra)).single()
        assertEquals("≈ 0,73 €/kWh (Allego, tarif publié)", enriched.irveDetails?.tarification)
        assertTrue(enriched.source!!.contains("EV tarifs FR"))
    }

    @Test
    fun enrich_keepsDataGouvPriceAndSetsGratuitLabel() {
        val withPrice = Poi(
            id = "2",
            name = "Local",
            address = "x",
            latitude = 48.85,
            longitude = 2.35,
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            operator = "Allego",
            irveDetails = IrveDetails(tarification = "0,35 €/kWh"),
        )
        assertEquals(
            "0,35 €/kWh",
            EvPricesFrEnricher.enrich(listOf(withPrice), listOf(allego)).single().irveDetails?.tarification
        )

        val free = Poi(
            id = "3",
            name = "Mairie",
            address = "x",
            latitude = 48.85,
            longitude = 2.35,
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            irveDetails = IrveDetails(gratuit = true),
        )
        assertEquals(
            "Gratuit",
            EvPricesFrEnricher.enrich(listOf(free), listOf(allego)).single().irveDetails?.tarification
        )
    }

    @Test
    fun enrich_skipsOutsideFrance() {
        val poi = Poi(
            id = "4",
            name = "Allego BE",
            address = "x",
            latitude = 50.85,
            longitude = 4.35,
            brand = "Allego",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            operator = "Allego",
            irveDetails = IrveDetails(),
        )
        val enriched = EvPricesFrEnricher.enrich(listOf(poi), listOf(allego)).single()
        assertNull(enriched.irveDetails?.tarification)
    }
}
