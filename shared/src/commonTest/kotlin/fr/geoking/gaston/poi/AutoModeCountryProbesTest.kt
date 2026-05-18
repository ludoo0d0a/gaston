package fr.geoking.gaston.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoModeCountryProbesTest {

    @Test
    fun probes_coverEveryAutoModeFuelCountry() {
        val probeIsos = AutoModeCountryProbes.ALL.map { it.iso }.toSet()
        val autoIsos = setOf(
            "FR", "GB", "IT", "SI", "NO", "SE", "PT", "NL", "DK", "HR", "FI", "GR", "IE",
            "MD", "RO", "RS", "MX", "AR", "ES", "DE", "AT", "BE", "LU",
        )
        assertEquals(autoIsos, probeIsos)
    }

    @Test
    fun eachProbe_matchesAutoProviderResolver() {
        for (probe in AutoModeCountryProbes.ALL) {
            val resolved = autoProvidersForCountries(
                countryCodes = listOf(probe.iso),
                wantFuel = true,
                wantElectric = false,
                fallbackManual = emptySet(),
            )
            assertTrue(
                probe.fuelProvider in resolved,
                "${probe.iso} (${probe.cityLabel}): expected ${probe.fuelProvider} in $resolved",
            )
        }
    }
}
