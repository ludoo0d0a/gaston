package fr.geoking.gaston.feature.maintenance

/**
 * Evaluates whether a [ServiceInterval] is due based on current time and odometer.
 */
object MaintenanceReminderEvaluator {

    data class DueReminder(
        val interval: ServiceInterval,
        val dueByKm: Boolean,
        val dueByDate: Boolean,
        val kmRemaining: Int?,
        val daysRemaining: Int?,
    )

    fun evaluate(
        intervals: List<ServiceInterval>,
        currentOdometerKm: Int?,
        nowMs: Long = System.currentTimeMillis(),
    ): List<DueReminder> {
        return intervals.mapNotNull { interval ->
            val dueByKm = isDueByKm(interval, currentOdometerKm)
            val dueByDate = isDueByDate(interval, nowMs)
            if (!dueByKm && !dueByDate) return@mapNotNull null
            DueReminder(
                interval = interval,
                dueByKm = dueByKm,
                dueByDate = dueByDate,
                kmRemaining = kmRemaining(interval, currentOdometerKm),
                daysRemaining = daysRemaining(interval, nowMs),
            )
        }
    }

    fun isDueByKm(interval: ServiceInterval, currentOdometerKm: Int?): Boolean {
        val everyKm = interval.everyKm ?: return false
        val lastKm = interval.lastOdometerKm ?: return false
        val current = currentOdometerKm ?: return false
        return current - lastKm >= everyKm
    }

    fun isDueByDate(interval: ServiceInterval, nowMs: Long): Boolean {
        val everyMonths = interval.everyMonths ?: return false
        val lastDone = interval.lastDoneAtMs ?: return false
        val dueAt = addMonthsApprox(lastDone, everyMonths)
        return nowMs >= dueAt
    }

    fun kmRemaining(interval: ServiceInterval, currentOdometerKm: Int?): Int? {
        val everyKm = interval.everyKm ?: return null
        val lastKm = interval.lastOdometerKm ?: return null
        val current = currentOdometerKm ?: return null
        return everyKm - (current - lastKm)
    }

    fun daysRemaining(interval: ServiceInterval, nowMs: Long): Int? {
        val everyMonths = interval.everyMonths ?: return null
        val lastDone = interval.lastDoneAtMs ?: return null
        val dueAt = addMonthsApprox(lastDone, everyMonths)
        val deltaMs = dueAt - nowMs
        return (deltaMs / (24L * 60L * 60L * 1000L)).toInt()
    }

    /** Approximate calendar months as 30.44 days. */
    fun addMonthsApprox(fromMs: Long, months: Int): Long {
        val msPerMonth = (30.44 * 24 * 60 * 60 * 1000).toLong()
        return fromMs + months * msPerMonth
    }
}
