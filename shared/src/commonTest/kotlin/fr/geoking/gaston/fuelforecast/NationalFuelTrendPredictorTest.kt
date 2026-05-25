package fr.geoking.gaston.fuelforecast

import fr.geoking.gaston.api.datagouv.NationalFuelDailyAverage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NationalFuelTrendPredictorTest {

    private val predictor = NationalFuelTrendPredictor(minHistoryDays = 3, projectionDays = 3)

    @Test
    fun project_risingTrend_increasesPrices() {
        val history = listOf(
            NationalFuelDailyAverage("2026-05-20", 2.00),
            NationalFuelDailyAverage("2026-05-21", 2.02),
            NationalFuelDailyAverage("2026-05-22", 2.04)
        )
        val out = predictor.project(history, anchorDay = "2026-05-22")
        assertEquals(3, out.size)
        assertEquals("2026-05-23", out[0].day)
        assertTrue(out[0].priceEurPerL > 2.04)
        assertTrue(out[2].priceEurPerL > out[0].priceEurPerL)
    }

    @Test
    fun project_insufficientHistory_returnsEmpty() {
        val history = listOf(
            NationalFuelDailyAverage("2026-05-20", 2.00),
            NationalFuelDailyAverage("2026-05-21", 2.02)
        )
        assertTrue(predictor.project(history, anchorDay = "2026-05-21").isEmpty())
    }
}
