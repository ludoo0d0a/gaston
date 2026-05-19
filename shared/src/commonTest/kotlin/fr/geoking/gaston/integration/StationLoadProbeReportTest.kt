package fr.geoking.gaston.integration

import fr.geoking.gaston.shared.network.NetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationLoadProbeReportTest {

    @Test
    fun classifyFailure_missingEnvKey() {
        val status = StationLoadProbeReport.classifyFailure(
            exception = MissingIntegrationTestEnvException(
                envKeys = listOf("FUELPRICES_DK_KEY"),
                message = "missing",
            ),
            message = "missing FUELPRICES_DK_KEY",
        )
        assertEquals(StationLoadProbeStatus.MISSING_ENV_KEY, status)
    }

    @Test
    fun classifyFailure_http401() {
        val status = StationLoadProbeReport.classifyFailure(
            exception = NetworkException(401, "Unauthorized"),
            message = "DK: NetworkException: Unauthorized",
        )
        assertEquals(StationLoadProbeStatus.AUTH_ERROR, status)
    }

    @Test
    fun classifyFailure_genericError() {
        val status = StationLoadProbeReport.classifyFailure(
            exception = RuntimeException("timeout"),
            message = "FR: timeout",
        )
        assertEquals(StationLoadProbeStatus.FAILED, status)
    }

    @Test
    fun write_emitsValidJson() {
        val file = kotlin.io.path.createTempFile(suffix = ".json").toFile()
        try {
            StationLoadProbeReport.write(
                listOf(
                    StationLoadProbeResult(
                        iso = "FR",
                        cityLabel = "Paris",
                        fuelProvider = "DataGouv",
                        status = StationLoadProbeStatus.OK,
                        stationCount = 10,
                        validCount = 10,
                    ),
                    StationLoadProbeResult(
                        iso = "DK",
                        cityLabel = "Copenhagen",
                        fuelProvider = "DenmarkFuelpricesDk",
                        status = StationLoadProbeStatus.MISSING_ENV_KEY,
                        message = "missing key",
                        envKeys = listOf("FUELPRICES_DK_KEY"),
                    ),
                ),
                target = file,
            )
            val json = file.readText()
            assertTrue(json.contains("\"iso\": \"FR\""))
            assertTrue(json.contains("\"status\": \"missing_env_key\""))
            assertTrue(json.contains("FUELPRICES_DK_KEY"))
        } finally {
            file.delete()
        }
    }
}
