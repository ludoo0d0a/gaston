package fr.geoking.gaston.repository

import fr.geoking.gaston.persistence.StationPriceSampleDao
import fr.geoking.gaston.persistence.StationPriceSampleEntity
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import java.util.UUID
import kotlin.math.abs

class StationPriceHistoryRepository(
    private val dao: StationPriceSampleDao
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

    private fun daysToMs(days: Int): Long = days.toLong() * 24L * 60L * 60L * 1000L
}

