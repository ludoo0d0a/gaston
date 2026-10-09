package fr.geoking.gaston.shared.network

import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.shared.logging.NetworkLog
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Thread-safe global tracker for HTTP 429 rate-limited hosts and endpoints.
 *
 * When an API server returns 429 Too Many Requests, [recordRateLimit] sets a cooldown
 * duration (60s default, [Retry-After] header, or parsed from response/error text such as
 * `cooldown 59 s remaining`) during which subsequent HTTP requests to that host are
 * short-circuited before network I/O.
 */
object RateLimitTracker {
    private val lock = Any()
    private val rateLimitedHosts = mutableMapOf<String, Long>()

    /** App / debug-bar format produced by [RateLimitPlugin] and API clients. */
    private val cooldownRemainingRegex =
        Regex("""cooldown\s+(\d+)\s*s\s+remaining""", RegexOption.IGNORE_CASE)

    /** Overpass `/api/status` and some HTML 429 bodies: `… in 12 seconds`. */
    private val inSecondsRegex =
        Regex("""in\s+(\d+)\s+seconds?""", RegexOption.IGNORE_CASE)

    /**
     * Records an HTTP 429 rate limit for [hostOrUrl].
     *
     * @param hostOrUrl Hostname or URL returned 429.
     * @param retryAfterHeader Value of Retry-After HTTP header if present.
     * @param defaultCooldownMs Cooldown duration in ms if no other source is valid.
     * @param responseBody Optional response or error text; parsed for `cooldown N s remaining`
     *   (and Overpass-style `in N seconds`) when [retryAfterHeader] is absent.
     */
    fun recordRateLimit(
        hostOrUrl: String,
        retryAfterHeader: String? = null,
        defaultCooldownMs: Long = 60_000L,
        responseBody: String? = null,
    ) {
        val host = extractHost(hostOrUrl)
        if (host.isBlank()) return
        val fromTextSec = parseCooldownSecondsFromText(responseBody)
        val cooldownMs = parseRetryAfterMs(retryAfterHeader)
            ?: fromTextSec?.times(1000L)
            ?: defaultCooldownMs
        val untilMs = System.currentTimeMillis() + cooldownMs
        synchronized(lock) {
            val current = rateLimitedHosts[host] ?: 0L
            if (untilMs > current) {
                rateLimitedHosts[host] = untilMs
            }
        }
    }

    /**
     * Returns true if [hostOrUrl] is currently under active rate-limit cooldown.
     */
    fun isRateLimited(hostOrUrl: String): Boolean {
        val host = extractHost(hostOrUrl)
        if (host.isBlank()) return false
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val cooldownEnd = rateLimitedHosts[host] ?: return false
            if (now >= cooldownEnd) {
                rateLimitedHosts.remove(host)
                return false
            }
            return true
        }
    }

    /**
     * Returns remaining cooldown in milliseconds for [hostOrUrl], or 0 if not rate limited.
     */
    fun getRemainingCooldownMs(hostOrUrl: String): Long {
        val host = extractHost(hostOrUrl)
        if (host.isBlank()) return 0L
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val cooldownEnd = rateLimitedHosts[host] ?: return 0L
            val remaining = cooldownEnd - now
            if (remaining <= 0) {
                rateLimitedHosts.remove(host)
                return 0L
            }
            return remaining
        }
    }

    /**
     * Resets rate-limit records for all hosts.
     */
    fun reset() {
        synchronized(lock) {
            rateLimitedHosts.clear()
        }
    }

    /**
     * Extracts hostname from a full URL or host string.
     */
    internal fun extractHost(hostOrUrl: String): String {
        val clean = hostOrUrl.trim().lowercase()
        if (clean.isBlank()) return ""
        val withoutScheme = if (clean.startsWith("http://")) {
            clean.substring(7)
        } else if (clean.startsWith("https://")) {
            clean.substring(8)
        } else {
            clean
        }
        return withoutScheme.substringBefore("/").substringBefore(":")
    }

    /**
     * Parses Retry-After header into milliseconds (supports integer seconds).
     */
    internal fun parseRetryAfterMs(headerValue: String?): Long? {
        if (headerValue.isNullOrBlank()) return null
        val seconds = headerValue.trim().toLongOrNull()
        if (seconds != null && seconds > 0) {
            return seconds * 1000L
        }
        return null
    }

    /**
     * Extracts cooldown duration in seconds from error/response text.
     *
     * Prefers `cooldown 59 s remaining` (app + RateLimitPlugin wording), then
     * Overpass-style `in N seconds`.
     */
    fun parseCooldownSecondsFromText(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        cooldownRemainingRegex.find(text)?.groupValues?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }
        inSecondsRegex.find(text)?.groupValues?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }
        return null
    }

    /** Builds the canonical short-circuit message used across clients and the plugin. */
    fun cooldownMessage(hostOrUrl: String, remainingSec: Long = remainingCooldownSec(hostOrUrl)): String {
        val host = extractHost(hostOrUrl).ifBlank { hostOrUrl }
        return "Rate limit active for $host (cooldown $remainingSec s remaining)"
    }

    fun remainingCooldownSec(hostOrUrl: String): Long =
        (getRemainingCooldownMs(hostOrUrl) / 1000).coerceAtLeast(1)
}

/**
 * Ktor Client Plugin that enforces 429 rate-limiting rules globally.
 *
 * 1. Pre-flight check: short-circuits requests to rate-limited hosts with [NetworkException].
 * 2. Response check: records 429 status codes and Retry-After header values in [RateLimitTracker].
 */
val RateLimitPlugin = createClientPlugin("RateLimitPlugin") {
    on(Send) { request ->
        val host = request.url.host
        if (RateLimitTracker.isRateLimited(host)) {
            val message = RateLimitTracker.cooldownMessage(host)
            // Short-circuit never hits ResponseObserver — mirror it into the debug bar.
            DebugLogStore.addLog(
                NetworkLog(
                    id = "ratelimit-${host}-${System.currentTimeMillis()}",
                    url = request.url.toString(),
                    host = host,
                    method = request.method.value,
                    requestHeaders = emptyMap(),
                    requestBody = null,
                    responseHeaders = null,
                    responseBody = message,
                    statusCode = 429,
                    durationMs = 0L,
                    timestamp = System.currentTimeMillis(),
                )
            )
            throw NetworkException(429, message)
        }
        val call = proceed(request)
        if (call.response.status == HttpStatusCode.TooManyRequests) {
            val retryAfter = call.response.headers[HttpHeaders.RetryAfter]
            RateLimitTracker.recordRateLimit(host, retryAfter)
        }
        call
    }
}
