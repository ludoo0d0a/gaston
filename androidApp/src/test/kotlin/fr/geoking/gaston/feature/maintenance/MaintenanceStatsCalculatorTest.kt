package fr.geoking.gaston.feature.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceStatsCalculatorTest {

    @Test
    fun fuelFillsWithOdometer_computeLitersPer100() {
        val events = listOf(
            event(MaintenanceEventType.FUEL, day(1), odometerKm = 10_000, volumeLiters = 40.0, amountEur = 60.0),
            event(MaintenanceEventType.FUEL, day(10), odometerKm = 10_500, volumeLiters = 35.0, amountEur = 52.5),
        )
        val stats = MaintenanceStatsCalculator.compute(events)
        assertEquals(1, stats.sampleCount)
        assertEquals(500, stats.totalDistanceKm)
        assertEquals(35.0, stats.totalVolumeLiters, 0.01)
        assertEquals(7.0, stats.litersPer100Km!!, 0.01)
        assertEquals(10.5, stats.costPer100Km!!, 0.01)
    }

    @Test
    fun missingOdometer_skipsInterval() {
        val events = listOf(
            event(MaintenanceEventType.FUEL, day(1), odometerKm = 10_000, volumeLiters = 40.0),
            event(MaintenanceEventType.FUEL, day(10), odometerKm = null, volumeLiters = 35.0),
            event(MaintenanceEventType.FUEL, day(20), odometerKm = 11_000, volumeLiters = 50.0),
        )
        val stats = MaintenanceStatsCalculator.compute(events)
        // Only interval 1→3 is valid (1000 km, 50 L)
        assertEquals(1, stats.sampleCount)
        assertEquals(1000, stats.totalDistanceKm)
        assertEquals(5.0, stats.litersPer100Km!!, 0.01)
    }

    @Test
    fun chargeFills_computeKwhPer100() {
        val events = listOf(
            event(MaintenanceEventType.CHARGE, day(1), odometerKm = 1000, energyKwh = 40.0),
            event(MaintenanceEventType.CHARGE, day(5), odometerKm = 1200, energyKwh = 30.0),
        )
        val stats = MaintenanceStatsCalculator.compute(events)
        assertEquals(1, stats.sampleCount)
        assertEquals(15.0, stats.kwhPer100Km!!, 0.01)
        assertNull(stats.litersPer100Km)
    }

    private fun day(n: Int): Long = n * 86_400_000L

    private fun event(
        type: MaintenanceEventType,
        dateEpochMs: Long,
        odometerKm: Int? = null,
        volumeLiters: Double? = null,
        energyKwh: Double? = null,
        amountEur: Double? = null,
    ) = MaintenanceEvent(
        id = "e-$dateEpochMs",
        vehicleId = "v1",
        type = type,
        dateEpochMs = dateEpochMs,
        odometerKm = odometerKm,
        volumeLiters = volumeLiters,
        energyKwh = energyKwh,
        amountEur = amountEur,
    )
}

class MaintenanceReminderEvaluatorTest {

    @Test
    fun dueByKm_whenThresholdReached() {
        val interval = ServiceInterval(
            id = "i1",
            vehicleId = "v1",
            serviceKind = ServiceKind.OIL_CHANGE,
            everyKm = 15_000,
            lastOdometerKm = 10_000,
            lastDoneAtMs = 0L,
        )
        assertTrue(MaintenanceReminderEvaluator.isDueByKm(interval, 25_000))
        assertFalse(MaintenanceReminderEvaluator.isDueByKm(interval, 20_000))
    }

    @Test
    fun dueByDate_whenMonthsElapsed() {
        val lastDone = 0L
        val interval = ServiceInterval(
            id = "i1",
            vehicleId = "v1",
            serviceKind = ServiceKind.INSPECTION,
            everyMonths = 24,
            lastDoneAtMs = lastDone,
        )
        val after24Months = MaintenanceReminderEvaluator.addMonthsApprox(lastDone, 24)
        assertTrue(MaintenanceReminderEvaluator.isDueByDate(interval, after24Months))
        assertFalse(MaintenanceReminderEvaluator.isDueByDate(interval, after24Months - 1))
    }

    @Test
    fun evaluate_returnsOnlyDue() {
        val due = ServiceInterval(
            id = "d",
            vehicleId = "v1",
            serviceKind = ServiceKind.OIL_CHANGE,
            everyKm = 10_000,
            lastOdometerKm = 0,
            lastDoneAtMs = System.currentTimeMillis(),
            everyMonths = 12,
        )
        val ok = ServiceInterval(
            id = "ok",
            vehicleId = "v1",
            serviceKind = ServiceKind.TIRES,
            everyKm = 40_000,
            lastOdometerKm = 0,
            lastDoneAtMs = System.currentTimeMillis(),
            everyMonths = 48,
        )
        val result = MaintenanceReminderEvaluator.evaluate(
            intervals = listOf(due, ok),
            currentOdometerKm = 12_000,
        )
        assertEquals(1, result.size)
        assertEquals(ServiceKind.OIL_CHANGE, result[0].interval.serviceKind)
        assertTrue(result[0].dueByKm)
    }
}

class MaintenanceCsvExporterTest {

    @Test
    fun export_includesHeaderAndEscapesCommas() {
        val csv = MaintenanceCsvExporter.export(
            listOf(
                MaintenanceEvent(
                    id = "1",
                    vehicleId = "v",
                    type = MaintenanceEventType.SERVICE,
                    dateEpochMs = 100,
                    title = "Oil, filter",
                    notes = "ok",
                    serviceKind = ServiceKind.OIL_CHANGE,
                )
            )
        )
        assertTrue(csv.startsWith("id,vehicleId,"))
        assertTrue(csv.contains("\"Oil, filter\""))
    }
}
