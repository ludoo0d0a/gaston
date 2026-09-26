package fr.geoking.gaston.aac

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Non-regression: FR alert strings must not contain « radar + distance » patterns.
 */
class AacLabelRegressionTest {
    private val banned = Regex(
        """radar.{0,30}(\d+\s*m|\d+\s*km|à\s+\d+)""",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun dangerZoneAlertCopyHasNoRadarDistanceLeak() {
        listOf(null, 30, 50, 70, 90, 110, 130).forEach { vma ->
            val long = DangerZoneAlertCopy.frZoneEntry(vma)
            val short = DangerZoneAlertCopy.frZoneEntryShort(vma)
            assertFalse(banned.containsMatchIn(long), long)
            assertFalse(banned.containsMatchIn(short), short)
            assertFalse(long.contains("Attention, radar", ignoreCase = true))
        }
    }
}
