package fr.geoking.gaston.api.traffic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficAlertCopyTest {

    @Test
    fun frAlertExamples() {
        assertEquals("accident dans 500m", TrafficAlertCopy.frAlert(TrafficSeverity.Accident, 500.0))
        assertEquals("voie fermée dans 300m", TrafficAlertCopy.frAlert(TrafficSeverity.Closure, 300.0))
        assertEquals("bouchon dans 2km", TrafficAlertCopy.frAlert(TrafficSeverity.Congestion, 2000.0))
        assertEquals("travaux dans 1km", TrafficAlertCopy.frAlert(TrafficSeverity.Roadworks, 1000.0))
        assertEquals("accident dans 200m", TrafficAlertCopy.frAlert(TrafficSeverity.Accident, 200.0))
    }

    @Test
    fun nonAlertableSeverities() {
        assertNull(TrafficAlertCopy.frAlert(TrafficSeverity.Normal, 500.0))
        assertNull(TrafficAlertCopy.frAlert(TrafficSeverity.Unknown, 500.0))
        assertFalse(TrafficAlertCopy.isAlertable(TrafficSeverity.Normal))
        assertTrue(TrafficAlertCopy.isAlertable(TrafficSeverity.Accident))
    }
}
