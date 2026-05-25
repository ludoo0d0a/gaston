package fr.geoking.gaston.fuelforecast

import fr.geoking.gaston.api.datagouv.NationalFuelDailyAverage
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Short-horizon projection from national average history (linear trend on recent days).
 */
class NationalFuelTrendPredictor(
    private val minHistoryDays: Int = 3,
    private val projectionDays: Int = 5
) {

    fun project(
        history: List<NationalFuelDailyAverage>,
        anchorDay: String
    ): List<NationalFuelDailyAverage> {
        val sorted = history.sortedBy { it.day }
        if (sorted.size < minHistoryDays) return emptyList()

        val n = sorted.size
        val xs = (0 until n).map { it.toDouble() }
        val ys = sorted.map { it.priceEurPerL }
        val sumX = xs.sum()
        val sumY = ys.sum()
        val sumXy = xs.zip(ys).sumOf { (x, y) -> x * y }
        val sumX2 = xs.sumOf { it * it }
        val denom = n * sumX2 - sumX * sumX
        val slope = if (denom == 0.0) 0.0 else (n * sumXy - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n

        val anchor = runCatching { LocalDate.parse(anchorDay) }.getOrNull() ?: return emptyList()
        return (1..projectionDays).map { horizon ->
            val targetDay = anchor.plus(DatePeriod(days = horizon))
            val x = (n - 1) + horizon.toDouble()
            val price = (intercept + slope * x).coerceAtLeast(0.05)
            NationalFuelDailyAverage(day = targetDay.toString(), priceEurPerL = price)
        }
    }
}
