package fr.geoking.gaston.api.routing

import fr.geoking.gaston.shared.platform.getEnv
import fr.geoking.gaston.shared.rate.DailyUserRateLimiter

/**
 * Wraps [TomTomRoutingClient] with a per-user daily limit.
 *
 * Default: 5 requests / user / day
 * Override with env var: TOMTOM_DAILY_REQUESTS_PER_USER
 */
class RateLimitedTomTomRoutingClient(
    private val delegate: TomTomRoutingClient,
    private val limiter: DailyUserRateLimiter = DailyUserRateLimiter()
) {
    suspend fun calculateRouteJson(
        userId: String,
        apiKey: String,
        routePlanningLocations: String,
        travelMode: String = "car"
    ): String? {
        val limit = perUserDailyLimit()
        val allowed = limiter.tryAcquire(userId = userId, dailyLimit = limit)
        if (!allowed) return null
        return delegate.calculateRouteJson(
            apiKey = apiKey,
            routePlanningLocations = routePlanningLocations,
            travelMode = travelMode
        )
    }

    private fun perUserDailyLimit(): Int {
        val raw = getEnv(ENV_VAR_PER_USER_DAILY_LIMIT)?.trim()
        return raw?.toIntOrNull()?.coerceIn(0, 10_000) ?: DEFAULT_PER_USER_DAILY_LIMIT
    }

    private companion object {
        private const val ENV_VAR_PER_USER_DAILY_LIMIT = "TOMTOM_DAILY_REQUESTS_PER_USER"
        private const val DEFAULT_PER_USER_DAILY_LIMIT = 5
    }
}

