package fr.geoking.gaston.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealApiTestEnvTest {

    @Test
    fun requireIntegrationEnv_throwsWithEnvKeyName() {
        // Use a synthetic key that CI secrets never set, so this unit test stays deterministic.
        val missingKey = "__GASTON_MISSING_INTEGRATION_ENV_FOR_UNIT_TEST__"
        val ex = assertFailsWith<MissingIntegrationTestEnvException> {
            requireIntegrationEnv(missingKey, "unit-test missing-env probe")
        }
        assertEquals(listOf(missingKey), ex.envKeys)
        assertEquals(true, ex.message!!.contains(missingKey))
    }
}
