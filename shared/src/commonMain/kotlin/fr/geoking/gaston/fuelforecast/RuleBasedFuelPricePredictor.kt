package fr.geoking.gaston.fuelforecast

import kotlin.math.ln
import kotlin.math.pow
import kotlinx.serialization.Serializable

/**
 * Transparent rule-based 1–3 day signal from crude/refined momentum and EUR/USD.
 *
 * Score uses multi-day log returns (dimensionless). Positive score ⇒ upward pressure on EUR pump prices.
 */
@Serializable
data class MarketReturnInputs(
    val returnBrent3d: Double?,
    val returnHeatingOil3d: Double?,
    val returnEurusd1d: Double?
)

data class HorizonPrediction(
    val horizonDays: Int,
    val predictedUp: Boolean,
    val predictedPriceEurPerL: Double
)

data class FuelForecastResult(
    val score: Double,
    val inputs: MarketReturnInputs,
    val horizonPredictions: List<HorizonPrediction>
)

class RuleBasedFuelPricePredictor(
    /** If score > threshold ⇒ "UP" direction. */
    private val upThreshold: Double = 0.002,
    /** EUR/L per unit of [score] at horizon 1 (score is log-return scale). */
    private val passThroughGazole: Double = 0.45,
    private val passThroughGasoline: Double = 0.38,
    /** Decay factor per additional day of horizon. */
    private val horizonDecay: Double = 0.85
) {

    fun computeScore(inputs: MarketReturnInputs): Double {
        val rB = inputs.returnBrent3d ?: 0.0
        val rHo = inputs.returnHeatingOil3d ?: rB
        val rFx = inputs.returnEurusd1d ?: 0.0
        return 0.5 * rB + 0.4 * rHo - 0.2 * rFx
    }

    fun buildInputs(
        brent: List<DailyClose>,
        heatingOil: List<DailyClose>,
        eurusd: List<DailyClose>
    ): MarketReturnInputs {
        fun logReturn(series: List<DailyClose>, lagDays: Int): Double? {
            if (series.size <= lagDays) return null
            val a = series.last().close
            val b = series[series.size - 1 - lagDays].close
            if (a <= 0 || b <= 0) return null
            return ln(a / b)
        }
        return MarketReturnInputs(
            returnBrent3d = logReturn(brent, 3),
            returnHeatingOil3d = logReturn(heatingOil, 3),
            returnEurusd1d = logReturn(eurusd, 1)
        )
    }

    /** @param baselinePriceE local average EUR/L today. */
    fun predict(
        fuelId: String,
        baselinePriceEurPerL: Double,
        brent: List<DailyClose>,
        heatingOil: List<DailyClose>,
        eurusd: List<DailyClose>
    ): FuelForecastResult {
        val inputs = buildInputs(brent, heatingOil, eurusd)
        val score = computeScore(inputs)
        val k = passThrough(fuelId)
        val horizons = (1..3).map { h ->
            val decay = horizonDecay.pow(h.toDouble())
            val predictedPrice = baselinePriceEurPerL + k * score * decay
            HorizonPrediction(
                horizonDays = h,
                predictedUp = score > upThreshold,
                predictedPriceEurPerL = predictedPrice.coerceAtLeast(0.05)
            )
        }
        return FuelForecastResult(score = score, inputs = inputs, horizonPredictions = horizons)
    }

    private fun passThrough(fuelId: String): Double = when (fuelId) {
        "gazole" -> passThroughGazole
        else -> passThroughGasoline
    }
}
