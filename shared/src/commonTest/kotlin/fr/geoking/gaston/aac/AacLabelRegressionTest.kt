package fr.geoking.gaston.aac

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Non-regression: FR alert strings format validation.
 */
class AacLabelRegressionTest {

    @Test
    fun dangerZoneAlertCopyFormatsAsExpected() {
        listOf(null, 30, 50, 70, 90, 110, 130).forEach { vma ->
            val longWithDist = DangerZoneAlertCopy.frZoneEntry(vma, 1000.0)
            assertTrue(longWithDist.startsWith("Gaston, radar"))
            assertTrue(longWithDist.contains("dans 1km"))

            val short = DangerZoneAlertCopy.frZoneEntryShort(vma)
            assertTrue(short.startsWith("Zone de danger"))
        }
    }
}
