package fr.geoking.gaston.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealApiTestEnvTest {

    @Test
    fun requireIntegrationEnv_throwsWithEnvKeyName() {
        val ex = assertFailsWith<MissingIntegrationTestEnvException> {
            requireIntegrationEnv("FUELPRICES_DK_KEY", "Denmark fuelprices.dk API")
        }
        assertEquals(listOf("FUELPRICES_DK_KEY"), ex.envKeys)
        assertEquals(true, ex.message!!.contains("FUELPRICES_DK_KEY"))
    }
}
