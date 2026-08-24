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
}
