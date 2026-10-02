package fr.geoking.gaston.feature.maintenance

/**
 * Pure consumption / cost stats from chronological fill events.
 * Intervals missing odometer on either endpoint are skipped.
 */
object MaintenanceStatsCalculator {

    data class FillStats(
        val sampleCount: Int,
        val totalDistanceKm: Int,
        val totalVolumeLiters: Double,
        val totalEnergyKwh: Double,
        val totalCostEur: Double,
        val litersPer100Km: Double?,
        val kwhPer100Km: Double?,
        val costPer100Km: Double?,
    ) {
        companion object {
            val EMPTY = FillStats(
                sampleCount = 0,
                totalDistanceKm = 0,
                totalVolumeLiters = 0.0,
                totalEnergyKwh = 0.0,
                totalCostEur = 0.0,
                litersPer100Km = null,
                kwhPer100Km = null,
                costPer100Km = null,
            )
        }
    }

    fun compute(events: List<MaintenanceEvent>, fromMs: Long = 0L): FillStats {
        val filtered = events
            .filter { it.dateEpochMs >= fromMs }
            .sortedBy { it.dateEpochMs }

        val fuel = filtered.filter { it.type == MaintenanceEventType.FUEL }
        val charge = filtered.filter { it.type == MaintenanceEventType.CHARGE }

        var totalDistance = 0
        var totalLiters = 0.0
        var totalKwh = 0.0
        var totalCost = 0.0
        var sampleCount = 0

        fun accumulateFills(fills: List<MaintenanceEvent>, volumeOf: (MaintenanceEvent) -> Double?) {
            var prevWithOdo: MaintenanceEvent? = null
            for (curr in fills) {
                val currKm = curr.odometerKm
                if (currKm == null) continue
                val prev = prevWithOdo
                prevWithOdo = curr
                if (prev == null) continue
                val prevKm = prev.odometerKm ?: continue
                val deltaKm = currKm - prevKm
                if (deltaKm <= 0) continue
                val volume = volumeOf(curr) ?: continue
                if (volume <= 0.0) continue
                totalDistance += deltaKm
                sampleCount += 1
                when (curr.type) {
                    MaintenanceEventType.FUEL -> totalLiters += volume
                    MaintenanceEventType.CHARGE -> totalKwh += volume
                    else -> Unit
                }
                curr.amountEur?.takeIf { it > 0 }?.let { totalCost += it }
            }
        }

        accumulateFills(fuel) { it.volumeLiters }
        accumulateFills(charge) { it.energyKwh }

        // Also sum absolute costs for expense / fills in period (even without odometer pairs)
        val absoluteCost = filtered
            .filter {
                it.type == MaintenanceEventType.FUEL ||
                    it.type == MaintenanceEventType.CHARGE ||
                    it.type == MaintenanceEventType.SERVICE ||
                    it.type == MaintenanceEventType.EXPENSE
            }
            .sumOf { it.amountEur ?: 0.0 }

        val litersPer100 = if (totalDistance > 0 && totalLiters > 0) {
            totalLiters * 100.0 / totalDistance
        } else null
        val kwhPer100 = if (totalDistance > 0 && totalKwh > 0) {
            totalKwh * 100.0 / totalDistance
        } else null
        val costPer100 = if (totalDistance > 0 && totalCost > 0) {
            totalCost * 100.0 / totalDistance
        } else null

        return FillStats(
            sampleCount = sampleCount,
            totalDistanceKm = totalDistance,
            totalVolumeLiters = totalLiters,
            totalEnergyKwh = totalKwh,
            totalCostEur = absoluteCost,
            litersPer100Km = litersPer100,
            kwhPer100Km = kwhPer100,
            costPer100Km = costPer100,
        )
    }
}
