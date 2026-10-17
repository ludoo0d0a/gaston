package fr.geoking.gaston.ui

import fr.geoking.gaston.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BrandHelperTest {

    @Test
    fun testTeslaMatching() {
        val info = BrandHelper.getBrandInfo("Tesla Supercharger")
        assertNotNull(info)
        assertEquals("Tesla", info.displayName)
        assertEquals(R.drawable.ic_brand_tesla, info.iconResId)
    }

    @Test
    fun testIonityMatching() {
        val info = BrandHelper.getBrandInfo("IONITY Paris")
        assertNotNull(info)
        assertEquals("Ionity", info.displayName)
    }

    @Test
    fun testLidlMatching() {
        val info = BrandHelper.getBrandInfo("Lidl Charging")
        assertNotNull(info)
        assertEquals("Lidl", info.displayName)
    }

    @Test
    fun testChargyMatching() {
        val info = BrandHelper.getBrandInfo("Chargy Ok")
        assertNotNull(info)
        assertEquals("Chargy", info.displayName)
        assertEquals(R.drawable.ic_brand_chargy, info.iconResId)
        assertEquals(R.drawable.ic_brand_chargy_rounded, info.roundedIconResId)
    }

    @Test
    fun testDyneffMatching() {
        val info = BrandHelper.getBrandInfo("Dyneff Montpellier")
        assertNotNull(info)
        assertEquals("Dyneff", info.displayName)
        assertEquals(R.drawable.ic_brand_dyneff, info.iconResId)
        assertEquals(R.drawable.ic_brand_dyneff_rounded, info.roundedIconResId)
    }

    @Test
    fun testUnknownBrandReturnsNull() {
        val info = BrandHelper.getBrandInfo("Some Unknown Brand")
        assertNull(info)
    }

    @Test
    fun testGasBrandsCategorization() {
        val gasBrands = BrandHelper.getGasBrands()
        val ids = gasBrands.map { it.first }
        assert(ids.contains("total"))
        assert(ids.contains("shell"))
        assert(ids.contains("dyneff"))
        assert(!ids.contains("tesla"))
        assert(!ids.contains("ionity"))
    }

    @Test
    fun testElectricBrandsCategorization() {
        val electricBrands = BrandHelper.getElectricBrands()
        val names = electricBrands.map { it.second }
        assert(names.contains("Tesla"))
        assert(names.contains("Ionity"))
        assert(names.contains("Total"))
        assert(names.contains("Delmonicos"))
        assert(names.contains("Easy Charge"))
        assert(names.contains("Electra"))
        assert(names.contains("ENGIE Vianeo"))
        assert(!names.contains("BP"))
    }

    @Test
    fun testNewElectricBrandsMatching() {
        val delmonicos = BrandHelper.getBrandInfo("Delmonicos Station")
        assertNotNull(delmonicos)
        assertEquals("Delmonicos", delmonicos.displayName)
        assertEquals(R.drawable.ic_brand_delmonicos, delmonicos.iconResId)

        val easycharge = BrandHelper.getBrandInfo("Easy Charge Express")
        assertNotNull(easycharge)
        assertEquals("Easy Charge", easycharge.displayName)
        assertEquals(R.drawable.ic_brand_easycharge, easycharge.iconResId)

        val electra = BrandHelper.getBrandInfo("Electra Fast Charging")
        assertNotNull(electra)
        assertEquals("Electra", electra.displayName)

        val engie = BrandHelper.getBrandInfo("ENGIE Vianeo Hub")
        assertNotNull(engie)
        assertEquals("ENGIE Vianeo", engie.displayName)
    }

    @Test
    fun testNewManufacturerBrandsMatching() {
        val manufacturers = listOf(
            "e-Totem" to R.drawable.ic_brand_etotem,
            "DBT-CEV" to R.drawable.ic_brand_dbt,
            "Lafon" to R.drawable.ic_brand_lafon,
            "ANYOS" to R.drawable.ic_brand_anyos,
            "Wattpark" to R.drawable.ic_brand_wattpark,
            "Ze-Watt" to R.drawable.ic_brand_zewatt,
            "Sermes" to R.drawable.ic_brand_sermes,
            "Saintronic" to R.drawable.ic_brand_saintronic,
            "Schneider Electric" to R.drawable.ic_brand_schneider,
            "Legrand" to R.drawable.ic_brand_legrand,
            "Hager" to R.drawable.ic_brand_hager,
            "Mersen" to R.drawable.ic_brand_mersen,
            "Valeo" to R.drawable.ic_brand_valeo,
            "G2mobility" to R.drawable.ic_brand_g2mobility,
            "Ecoload" to R.drawable.ic_brand_ecoload,
            "Cahors" to R.drawable.ic_brand_cahors,
            "ABB E-mobility" to R.drawable.ic_brand_abb,
            "Alpitronic" to R.drawable.ic_brand_alpitronic,
            "Alfen" to R.drawable.ic_brand_alfen,
            "Wallbox" to R.drawable.ic_brand_wallbox,
            "Circontrol" to R.drawable.ic_brand_circontrol,
            "Kempower" to R.drawable.ic_brand_kempower,
            "Tritium" to R.drawable.ic_brand_tritium,
            "Siemens" to R.drawable.ic_brand_siemens,
            "Mennekes" to R.drawable.ic_brand_mennekes,
            "Eaton" to R.drawable.ic_brand_eaton,
            "StarCharge" to R.drawable.ic_brand_starcharge,
            "Huawei Digital Power" to R.drawable.ic_brand_huawei,
            "Nayax" to R.drawable.ic_brand_nayax,
            "Daze" to R.drawable.ic_brand_daze,
            "Easee" to R.drawable.ic_brand_easee,
            "Zaptec" to R.drawable.ic_brand_zaptec,
        )

        for ((name, expectedIconRes) in manufacturers) {
            val info = BrandHelper.getBrandInfo(name)
            assertNotNull(info, "BrandInfo for $name should not be null")
            assertEquals(name, info.displayName)
            assertEquals(expectedIconRes, info.iconResId)
        }
    }

    @Test
    fun testBeneluxElectricBrandsInBrandHelper() {
        val electricBrands = BrandHelper.getElectricBrands()
        val names = electricBrands.map { it.second }

        val expectedBenelux = listOf(
            "Enovos", "Superchargy", "Sudstroum", "Electris", "Creos",
            "Luminus", "Eneco", "Blue Corner", "DATS 24", "Vandebron",
            "LeasePlan", "Greenflux", "Sparki", "EDI", "Powerpass",
            "CityPower", "Strohm", "Rebel Mobility", "Optimile", "OpCharge",
            "Vattenfall", "Orange Charging", "Equans", "Essent"
        )

        for (name in expectedBenelux) {
            assert(names.contains(name)) { "BrandHelper electric brands list should contain $name" }
        }
    }
}
