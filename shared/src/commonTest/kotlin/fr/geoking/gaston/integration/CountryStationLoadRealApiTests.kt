package fr.geoking.gaston.integration

import fr.geoking.gaston.poi.AutoModeCountryProbes
import fr.geoking.gaston.poi.CountryStationProbe
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.fail

/**
 * Live HTTP integration tests for auto-mode fuel providers in every supported country.
 * Excluded from default CI via *RealApiTests* filter; run on the station-load-integration workflow.
 */
class CountryStationLoadRealApiTests {

    private val client by lazy { createRealApiHttpClient() }

    @Test
    fun allAutoModeCountries_loadNearbyStations() = runBlocking {
        val results = mutableListOf<StationLoadProbeResult>()
        val failures = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        try {
            for ((index, probe) in AutoModeCountryProbes.ALL.withIndex()) {
                if (index > 0) delay(1_500)
                val outcome = loadProbe(probe)
                results += StationLoadProbeReport.fromProbe(probe, outcome)
                when (outcome) {
                    is ProbeOutcome.Success -> Unit
                    is ProbeOutcome.Skipped -> skipped += outcome.reason
                    is ProbeOutcome.Failed -> failures += outcome.message
                }
            }
        } finally {
            StationLoadProbeReport.write(results)
        }

        if (skipped.isNotEmpty()) {
            println("Skipped probes:\n${skipped.joinToString("\n")}")
        }

        if (failures.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Station load failed for ${failures.size} country probe(s):")
                    failures.forEach { appendLine("  - $it") }
                    if (skipped.isNotEmpty()) {
                        appendLine()
                        appendLine("Skipped (${skipped.size}):")
                        skipped.forEach { appendLine("  - $it") }
                    }
                },
            )
        }
    }

    private suspend fun loadProbe(probe: CountryStationProbe): ProbeOutcome {
        val provider = try {
            RealApiTestProviders.create(client, probe)
        } catch (e: MissingIntegrationTestEnvException) {
            return ProbeOutcome.Failed(
                message = "${probe.iso} (${probe.fuelProvider}): ${e.message}",
                status = StationLoadProbeStatus.MISSING_ENV_KEY,
                envKeys = e.envKeys,
            )
        }
            ?: return ProbeOutcome.Skipped("${probe.iso}: no provider factory for ${probe.fuelProvider}")

        return try {
            val pois = withTimeout(180_000) {
                loadWithRetries(probe) { attempt ->
                    if (attempt > 0) delay(3_000)
                    provider.getGasStations(probe.latitude, probe.longitude, viewport = null)
                }
            }
            if (pois.isEmpty()) {
                return if (probe.skipIfEmpty) {
                    ProbeOutcome.Skipped(
                        "${probe.iso}: ${probe.fuelProvider} returned no stations (upstream API empty or unavailable)",
                    )
                } else {
                    ProbeOutcome.Failed(
                        message = "${probe.iso} (${probe.cityLabel}, ${probe.fuelProvider}): returned 0 stations",
                    )
                }
            } else {
                val valid = pois.count { p ->
                    p.name.isNotBlank() &&
                        p.latitude in -90.0..90.0 &&
                        p.longitude in -180.0..180.0
                }
                if (valid == 0) {
                    ProbeOutcome.Failed(
                        message = "${probe.iso} (${probe.cityLabel}, ${probe.fuelProvider}): ${pois.size} results but none had valid name/coordinates",
                    )
                } else {
                    println("${probe.iso}: ${pois.size} station(s), $valid with name+coordinates")
                    ProbeOutcome.Success(total = pois.size, valid = valid)
                }
            }
        } catch (e: Exception) {
            val message = "${probe.iso} (${probe.cityLabel}, ${probe.fuelProvider}): ${e::class.simpleName}: ${e.message}"
            ProbeOutcome.Failed(
                message = message,
                status = StationLoadProbeReport.classifyFailure(e, message),
            )
        }
    }

    private suspend fun loadWithRetries(
        probe: CountryStationProbe,
        maxAttempts: Int = 3,
        load: suspend (attempt: Int) -> List<Poi>,
    ): List<Poi> {
        var last: List<Poi> = emptyList()
        repeat(maxAttempts) { attempt ->
            last = load(attempt)
            if (last.isNotEmpty()) return last
        }
        return last
    }
}
