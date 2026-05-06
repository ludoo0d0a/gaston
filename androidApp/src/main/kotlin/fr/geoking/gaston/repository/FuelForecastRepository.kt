package fr.geoking.gaston.repository

import fr.geoking.gaston.api.datagouv.DataGouvPrixCarburantClient
import fr.geoking.gaston.api.datagouv.DataGouvPrixCarburantStation
import fr.geoking.gaston.fuelforecast.DailyClose
import fr.geoking.gaston.fuelforecast.FuelForecastResult
import fr.geoking.gaston.fuelforecast.MarketReturnInputs
import fr.geoking.gaston.fuelforecast.RuleBasedFuelPricePredictor
import fr.geoking.gaston.fuelforecast.StooqDailyClient
import fr.geoking.gaston.fuelforecast.StooqSymbols
import fr.geoking.gaston.persistence.AppDatabase
import fr.geoking.gaston.persistence.FuelPricePredictionEntity
import fr.geoking.gaston.persistence.FuelPricePredictionScoreEntity
import fr.geoking.gaston.persistence.LocalFuelAvgDailyEntity
import fr.geoking.gaston.persistence.MarketDailyQuoteEntity
import fr.geoking.gaston.poi.MapPoiFilter
import io.ktor.client.HttpClient
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

private val FUEL_PRIORITY = listOf("gazole", "sp95", "sp98", "gplc", "e85")

/**
 * Local pump averages, Stooq-based market series, rule forecasts, and scoring against realized daily averages.
 */
class FuelForecastRepository(
    private val http: HttpClient,
    private val db: AppDatabase,
    private val predictor: RuleBasedFuelPricePredictor = RuleBasedFuelPricePredictor()
) {
    private val stooq = StooqDailyClient(http)
    private val prix = DataGouvPrixCarburantClient(http)
    private val marketDao = db.marketDailyQuoteDao()
    private val localAvgDao = db.localFuelAvgDailyDao()
    private val predictionDao = db.fuelPricePredictionDao()
    private val scoreDao = db.fuelPricePredictionScoreDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val MARKET_CACHE_MS = 4L * 60L * 60L * 1000L
        private const val CHEAPEST_STATIONS = 20
        private const val SEARCH_RADIUS_KM = 15
        private const val STATION_LIMIT = 100
        private const val DIRECTION_EPSILON_EUR = 0.005
    }

    fun locationKey(latitude: Double, longitude: Double): String =
        "${formatCoord(latitude)}_${formatCoord(longitude)}"

    private fun formatCoord(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    suspend fun refreshAndBuildUiState(
        latitude: Double,
        longitude: Double,
        fuelIds: Set<String>
    ): FuelForecastUiState {
        val results = refreshAndBuildMultiUiState(latitude, longitude, fuelIds)
        val primaryFuel = FUEL_PRIORITY.firstOrNull { it in results.keys } ?: fuelIds.firstOrNull { it != "electric" } ?: "gazole"
        return results[primaryFuel] ?: FuelForecastUiState(fuelId = primaryFuel, locationKey = locationKey(latitude, longitude))
    }

    suspend fun refreshAndBuildMultiUiState(
        latitude: Double,
        longitude: Double,
        fuelIds: Set<String>
    ): Map<String, FuelForecastUiState> {
        val locKey = locationKey(latitude, longitude)
        val today = todayParis()
        val now = System.currentTimeMillis()

        val stations = try {
            prix.getStations(latitude, longitude, SEARCH_RADIUS_KM, STATION_LIMIT)
        } catch (_: Exception) {
            emptyList()
        }

        val effectiveFuels = fuelIds.filter { it != "electric" }.toSet()
        if (effectiveFuels.isEmpty()) {
            return mapOf(
                "gazole" to FuelForecastUiState(
                    fuelId = "gazole",
                    locationKey = locKey,
                    errorMessage = "Select a fuel type (e.g. Gazole or SP95)."
                )
            )
        }

        for (fuel in effectiveFuels) {
            val prices = stationPricesForFuel(stations, fuel).sorted()
            if (prices.isEmpty()) continue
            val n = min(CHEAPEST_STATIONS, prices.size)
            val avg = prices.take(n).average()
            localAvgDao.upsert(
                LocalFuelAvgDailyEntity(
                    day = today,
                    fuelId = fuel,
                    locationKey = locKey,
                    avgPrice = avg,
                    stationCount = n,
                    updatedAtMs = now
                )
            )
        }

        refreshMarketCacheIfStale(now)

        val fromDay = LocalDate.parse(today).minusDays(14).toString()
        var brent = loadClosesFromDb(StooqSymbols.BRENT_UK, fromDay)
        var ho = loadClosesFromDb(StooqSymbols.HEATING_OIL, fromDay)
        var fx = loadClosesFromDb(StooqSymbols.EURUSD, fromDay)
        if (brent.size < 4) {
            brent = try {
                stooq.fetchDailyCloses(StooqSymbols.BRENT_UK, 40)
            } catch (_: Exception) {
                brent
            }
        }
        if (ho.size < 4) ho = brent
        if (fx.size < 2) {
            fx = try {
                stooq.fetchDailyCloses(StooqSymbols.EURUSD, 40)
            } catch (_: Exception) {
                fx
            }
        }

        scorePendingPredictions(today)

        val resultMap = mutableMapOf<String, FuelForecastUiState>()
        val allHistories = mutableMapOf<String, List<DailyPricePoint>>()

        for (fuel in effectiveFuels) {
            val baseline = localAvgDao.getDay(locKey, fuel, today)?.avgPrice
            if (baseline != null && brent.size >= 4) {
                val forecast = predictor.predict(fuel, baseline, brent, ho, fx)
                persistPredictions(today, now, locKey, fuel, baseline, forecast)
            }

            val history = localAvgDao.series(locKey, fuel, fromDay).map {
                DailyPricePoint(day = it.day, priceEurPerL = it.avgPrice, isForecast = false)
            }
            allHistories[fuel] = history

            val predictions = predictionDao.forCreationDay(today, fuel, locKey)
            val forecastPoints = predictions.map { p ->
                DailyPricePoint(day = p.targetDay, priceEurPerL = p.predictedPrice, isForecast = true)
            }

            val score = predictions.firstOrNull()?.marketScore
            val up = predictions.firstOrNull()?.predictedUp

            val accFrom = LocalDate.parse(today).minusDays(7).toString()
            val accRow = scoreDao.accuracySince(fuel, locKey, accFrom)
            val lastScore = scoreDao.latestScoreForLocation(fuel, locKey)

            val error = if (stationPricesForFuel(stations, fuel).isEmpty()) {
                "No pump prices for $fuel nearby. Try again later."
            } else {
                null
            }

            resultMap[fuel] = FuelForecastUiState(
                fuelId = fuel,
                locationKey = locKey,
                historyPoints = history,
                forecastPoints = forecastPoints,
                marketScore = score,
                directionUp = up,
                accuracyHitRate7d = accRow?.hitRate,
                accuracyMae7d = accRow?.mae,
                lastScoreDirectionCorrect = lastScore?.directionCorrect,
                errorMessage = error
            )
        }

        if (allHistories.isNotEmpty()) {
            resultMap["unified"] = FuelForecastUiState(
                fuelId = "unified",
                locationKey = locKey,
                allFuelsHistory = allHistories,
                brentHistory = brent
            )
        }

        return resultMap
    }

    private suspend fun persistPredictions(
        createdDay: String,
        createdAtMs: Long,
        locationKey: String,
        fuelId: String,
        baseline: Double,
        forecast: FuelForecastResult
    ) {
        val inputsJson = json.encodeToString(MarketReturnInputs.serializer(), forecast.inputs)
        for (hp in forecast.horizonPredictions) {
            val h = hp.horizonDays
            if (predictionDao.existsForHorizon(createdDay, fuelId, locationKey, h)) continue
            val target = LocalDate.parse(createdDay).plusDays(h.toLong()).toString()
            predictionDao.insert(
                FuelPricePredictionEntity(
                    id = UUID.randomUUID().toString(),
                    createdAtMs = createdAtMs,
                    createdDay = createdDay,
                    targetDay = target,
                    horizonDays = h,
                    fuelId = fuelId,
                    locationKey = locationKey,
                    predictedUp = hp.predictedUp,
                    predictedPrice = hp.predictedPriceEurPerL,
                    baselinePrice = baseline,
                    marketScore = forecast.score,
                    inputsJson = inputsJson
                )
            )
        }
    }

    private suspend fun scorePendingPredictions(todayParis: String) {
        val pending = predictionDao.pendingScoring(todayParis)
        val now = System.currentTimeMillis()
        for (p in pending) {
            val actualRow = localAvgDao.getDay(p.locationKey, p.fuelId, p.targetDay) ?: continue
            val actual = actualRow.avgPrice
            val baseline = p.baselinePrice
            val actualUp = actual > baseline + DIRECTION_EPSILON_EUR
            val correct = actualUp == p.predictedUp
            val err = abs(p.predictedPrice - actual)
            scoreDao.insert(
                FuelPricePredictionScoreEntity(
                    id = UUID.randomUUID().toString(),
                    predictionId = p.id,
                    scoredAtMs = now,
                    actualPrice = actual,
                    baselinePrice = baseline,
                    directionCorrect = correct,
                    absError = err
                )
            )
        }
    }

    private suspend fun refreshMarketCacheIfStale(nowMs: Long) {
        val sym = StooqSymbols.BRENT_UK
        val last = marketDao.latestFetchMs(sym) ?: 0L
        if (nowMs - last < MARKET_CACHE_MS) return

        coroutineScope {
            val b = async { runCatching { stooq.fetchDailyCloses(StooqSymbols.BRENT_UK, 40) }.getOrElse { emptyList() } }
            val h = async { runCatching { stooq.fetchDailyCloses(StooqSymbols.HEATING_OIL, 40) }.getOrElse { emptyList() } }
            val e = async { runCatching { stooq.fetchDailyCloses(StooqSymbols.EURUSD, 40) }.getOrElse { emptyList() } }
            val rows = mutableListOf<MarketDailyQuoteEntity>()
            for (d in b.await()) rows += quoteEntity(StooqSymbols.BRENT_UK, d, nowMs)
            for (d in h.await()) rows += quoteEntity(StooqSymbols.HEATING_OIL, d, nowMs)
            for (d in e.await()) rows += quoteEntity(StooqSymbols.EURUSD, d, nowMs)
            if (rows.isNotEmpty()) marketDao.upsertAll(rows)
        }
    }

    private fun quoteEntity(symbol: String, bar: DailyClose, fetchedAt: Long) =
        MarketDailyQuoteEntity(symbol = symbol, day = bar.day, close = bar.close, fetchedAtMs = fetchedAt)

    private suspend fun loadClosesFromDb(symbol: String, fromDay: String): List<DailyClose> =
        marketDao.since(symbol, fromDay).map { DailyClose(day = it.day, close = it.close) }

    private fun stationPricesForFuel(
        stations: List<DataGouvPrixCarburantStation>,
        fuelId: String
    ): List<Double> {
        val out = ArrayList<Double>()
        for (s in stations) {
            for (f in s.fuels) {
                val id = MapPoiFilter.fuelNameToId(f.name) ?: continue
                if (id == fuelId) out += f.priceEur
            }
        }
        return out
    }

    private fun todayParis(): String = LocalDate.now(PARIS).toString()
}

data class DailyPricePoint(
    val day: String,
    val priceEurPerL: Double,
    val isForecast: Boolean
)

data class FuelForecastUiState(
    val fuelId: String,
    val locationKey: String,
    val historyPoints: List<DailyPricePoint> = emptyList(),
    val forecastPoints: List<DailyPricePoint> = emptyList(),
    val marketScore: Double? = null,
    val directionUp: Boolean? = null,
    val accuracyHitRate7d: Double? = null,
    val accuracyMae7d: Double? = null,
    val lastScoreDirectionCorrect: Boolean? = null,
    val errorMessage: String? = null,
    val allFuelsHistory: Map<String, List<DailyPricePoint>> = emptyMap(),
    val brentHistory: List<DailyClose> = emptyList()
)
