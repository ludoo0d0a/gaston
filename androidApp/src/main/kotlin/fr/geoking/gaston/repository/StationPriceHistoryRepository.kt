package fr.geoking.gaston.repository

import fr.geoking.gaston.persistence.NationalFuelPriceDao
import fr.geoking.gaston.persistence.StationPriceSampleDao
import fr.geoking.gaston.persistence.StationPriceSampleEntity
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

class StationPriceHistoryRepository(
    private val dao: StationPriceSampleDao,
    private val nationalDao: NationalFuelPriceDao? = null
) {
    data class PricePoint(
        val observedAtMs: Long,
        val price: Double,
        val fuelId: String,
        val fuelName: String,
        val outOfStock: Boolean
    )

    /**
     * Record one price sample per fuel type for this POI, if we have live price data.
     *
     * To keep the DB small, we skip inserting if the latest sample for (stationId, fuelId)
     * is recent and unchanged.
     */
    suspend fun recordFromPoi(poi: Poi, nowMs: Long = System.currentTimeMillis()) {
        val prices = poi.fuelPrices?.takeIf { it.isNotEmpty() } ?: return

        val samplesToInsert = mutableListOf<StationPriceSampleEntity>()
        for (fp in prices) {
            val fuelId = MapPoiFilter.fuelNameToId(fp.fuelName)
                ?: fp.fuelName.trim().lowercase().replace(Regex("\\s+"), "_")
            val latest = dao.latestSample(poi.id, fuelId)

            val isSamePrice = latest != null && abs(latest.price - fp.price) < 0.0005
            val isSameStock = latest != null && latest.outOfStock == fp.outOfStock
            val isRecent = latest != null && (nowMs - latest.observedAtMs) < (30L * 60L * 1000L) // 30 min

            if (latest != null && isRecent && isSamePrice && isSameStock) continue

            samplesToInsert += StationPriceSampleEntity(
                id = UUID.randomUUID().toString(),
                stationId = poi.id,
                fuelId = fuelId,
                fuelName = fp.fuelName,
                price = fp.price,
                currency = "EUR",
                outOfStock = fp.outOfStock,
                observedAtMs = nowMs
            )
        }

        if (samplesToInsert.isNotEmpty()) {
            dao.insertAll(samplesToInsert)
        }
    }

    suspend fun getLastDaysSeries(stationId: String, days: Int = 5, nowMs: Long = System.currentTimeMillis()): Map<String, List<PricePoint>> {
        val fromMs = nowMs - daysToMs(days)
        val raw = dao.samplesSince(stationId = stationId, fromMs = fromMs)
        return raw
            .groupBy { it.fuelId }
            .mapValues { (_, list) ->
                list.map {
                    PricePoint(
                        observedAtMs = it.observedAtMs,
                        price = it.price,
                        fuelId = it.fuelId,
                        fuelName = it.fuelName,
                        outOfStock = it.outOfStock
                    )
                }
            }
    }

    /**
     * Calculates a price rating (0.0 to 10.0) based on how this station's prices
     * compare to national averages over the last 30 days.
     *
     * A score of 10 means the station is consistently much cheaper than average.
     * A score of 5 means it's about average.
     * A score of 0 means it's consistently much more expensive.
     */
    suspend fun getPriceRating(stationId: String, fuelId: String, countryCode: String = "FR", nowMs: Long = System.currentTimeMillis()): Double? {
        if (nationalDao == null) return null

        val fromMs = nowMs - daysToMs(30)
        val samples = dao.samplesSince(stationId, fromMs).filter { it.fuelId == fuelId }
        if (samples.isEmpty()) return null

        val fromDay = LocalDate.now().minusDays(30).toString()
        val nationalPrices = nationalDao.getPricesSince(countryCode, fuelId, fromDay)
            .associateBy { it.day }
        if (nationalPrices.isEmpty()) return null

        var totalWeight = 0.0
        var weightedDiffSum = 0.0

        for (sample in samples) {
            val day = LocalDate.ofEpochDay(sample.observedAtMs / (24L * 60L * 60L * 1000L)).toString()
            val national = nationalPrices[day]?.avgPrice ?: nationalPrices.values.firstOrNull()?.avgPrice ?: continue

            // Difference in percentage. Negative means cheaper.
            val diffPct = (sample.price - national) / national

            // Recency weighting: more recent samples have higher weight.
            val ageDays = (nowMs - sample.observedAtMs).toDouble() / (24L * 60L * 60L * 1000L)
            val weight = 1.0 / (1.0 + ageDays / 7.0)

            weightedDiffSum += diffPct * weight
            totalWeight += weight
        }

        if (totalWeight == 0.0) return null
        val avgDiffPct = weightedDiffSum / totalWeight

        // Mapping: -5% or better -> 10.0, 0% -> 5.0, +5% or worse -> 0.0
        val rating = 5.0 - (avgDiffPct * 100.0)
        return rating.coerceIn(0.0, 10.0)
    }

    suspend fun deleteOldSamples(beforeMs: Long) {
        dao.deleteOldSamples(beforeMs)
    }

    private fun daysToMs(days: Int): Long = days.toLong() * 24L * 60L * 60L * 1000L
}

