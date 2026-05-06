package fr.geoking.gaston.shared.rate

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * In-memory daily request limiter, keyed by userId.
 *
 * Note: This is process-local (resets on app restart). Good enough for client-side quota protection.
 */
class DailyUserRateLimiter(
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    private val mutex = Mutex()
    private val counts = mutableMapOf<String, Int>()

    suspend fun tryAcquire(userId: String, dailyLimit: Int): Boolean = mutex.withLock {
        if (dailyLimit <= 0) return@withLock false

        val dayKey = currentDayKey()
        val key = "${userId.trim()}@$dayKey"
        val current = counts[key] ?: 0
        if (current >= dailyLimit) return@withLock false
        counts[key] = current + 1
        true
    }

    private fun currentDayKey(): String {
        val dt = Clock.System.now().toLocalDateTime(timeZone)
        // YYYY-MM-DD
        return "${dt.year.toString().padStart(4, '0')}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
    }
}

