package fr.geoking.gaston.integration

import fr.geoking.gaston.poi.AutoModeCountryProbes
import fr.geoking.gaston.poi.CountryStationProbe
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.shared.platform.getEnv
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Live HTTP integration tests for auto-mode fuel providers in every supported country.
 * Excluded from default CI via *RealApiTests* filter; run on the station-load-integration workflow.
 */
class CountryStationLoadRealApiTests {

    private val client by lazy { createRealApiHttpClient() }

    @Test
    fun allAutoModeCountries_loadNearbyStations() = runBlocking {
        val failures = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (probe in AutoModeCountryProbes.ALL) {
            when (val outcome = loadProbe(probe)) {
                is ProbeOutcome.Success -> Unit
                is ProbeOutcome.Skipped -> skipped += outcome.reason
                is ProbeOutcome.Failed -> failures += outcome.message
            }
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
        if (probe.fuelProvider == PoiProviderType.DenmarkFuelpricesDk &&
            getEnv("FUELPRICES_DK_KEY").isNullOrBlank()
        ) {
            return ProbeOutcome.Skipped(
                "${probe.iso}: Denmark (FUELPRICES_DK_KEY not set)",
            )
        }

        val provider = RealApiTestProviders.create(client, probe)
            ?: return ProbeOutcome.Skipped("${probe.iso}: no provider factory for ${probe.fuelProvider}")

        return try {
            val pois = withTimeout(180_000) {
                loadWithRetries(probe) { attempt ->
                    if (attempt > 0) delay(3_000)
                    provider.getGasStations(probe.latitude, probe.longitude, viewport = null)
                }
            }
            if (pois.isEmpty()) {
                ProbeOutcome.Failed(
                    "${probe.iso} (${probe.cityLabel}, ${probe.fuelProvider}): returned 0 stations",
                )
            } else {
                val valid = pois.count { p ->
                    p.name.isNotBlank() &&
                        p.latitude in -90.0..90.0 &&
                        p.longitude in -180.0..180.0
                }
                if (valid == 0) {
                    ProbeOutcome.Failed(
                        "${probe.iso} (${probe.cityLabel}, ${probe.fuelProvider}): ${pois.size} results but none had valid name/coordinates",
                    )
                } else {
                    println("${probe.iso}: ${pois.size} station(s), $valid with name+coordinates")
                    ProbeOutcome.Success
                }
            }
        } catch (e: Exception) {
            ProbeOutcome.Failed(
                "${probe.iso} (${probe.cityLabel}, ${probe.fuelProvider}): ${e::class.simpleName}: ${e.message}",
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

    private sealed interface ProbeOutcome {
        data object Success : ProbeOutcome
        data class Skipped(val reason: String) : ProbeOutcome
        data class Failed(val message: String) : ProbeOutcome
    }
}
