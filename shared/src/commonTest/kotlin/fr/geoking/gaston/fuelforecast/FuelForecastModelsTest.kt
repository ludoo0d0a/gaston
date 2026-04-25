package fr.geoking.gaston.fuelforecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuelForecastModelsTest {

    @Test
    fun stooqCsv_parsesTail() {
        val csv = """
            Date,Open,High,Low,Close,Volume
            2026-04-01,1,1,1,70.0,0
            2026-04-02,1,1,1,71.0,0
            2026-04-03,1,1,1,72.0,0
        """.trimIndent()
        val rows = StooqDailyClient.parseDailyCsv(csv, maxRows = 2)
        assertEquals(2, rows.size)
        assertEquals("2026-04-02", rows[0].day)
        assertEquals(71.0, rows[0].close)
        assertEquals(72.0, rows[1].close)
    }

    @Test
    fun predictor_score_positiveWhenCrudeUp() {
        val p = RuleBasedFuelPricePredictor(upThreshold = 0.001)
        val brent = closes(100.0, 102.0, 104.0, 106.0) // 3d return > 0
        val ho = closes(2.5, 2.5, 2.5, 2.5)
        val fx = closes(1.10, 1.10, 1.10, 1.10)
        val r = p.predict("gazole", baselinePriceEurPerL = 1.80, brent, ho, fx)
        assertTrue(r.score > 0)
        assertTrue(r.horizonPredictions[0].predictedPriceEurPerL > 1.80)
    }

    private fun closes(vararg v: Double): List<DailyClose> =
        v.mapIndexed { i, c -> DailyClose(day = "2026-04-${(i + 1).toString().padStart(2, '0')}", close = c) }
}
