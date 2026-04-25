package fr.geoking.gaston.fuelforecast

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Daily OHLCV from Stooq CSV (`/q/d/l/?s=sym&i=d`). No API key.
 * Symbols are lowercase on Stooq (e.g. [StooqSymbols.BRENT_UK], [StooqSymbols.HEATING_OIL], [StooqSymbols.EURUSD]).
 */
class StooqDailyClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://stooq.com/q/d/l/"
) {
    /**
     * @param maxRows keep the last [maxRows] data rows (most recent), after skipping the header.
     */
    suspend fun fetchDailyCloses(symbol: String, maxRows: Int = 30): List<DailyClose> {
        val sym = symbol.trim().lowercase()
        val url = "$baseUrl?s=$sym&i=d"
        val response = http.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Stooq error for $sym: $body")
        }
        return parseDailyCsv(body, maxRows)
    }

    companion object {
        internal fun parseDailyCsv(csvText: String, maxRows: Int): List<DailyClose> {
            val lines = csvText.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (lines.size < 2) return emptyList()
            val dataLines = lines.drop(1)
                .filter { !it.startsWith("Date,") }
            val tail = dataLines.takeLast(maxRows.coerceAtLeast(1))
            val out = ArrayList<DailyClose>(tail.size)
            for (line in tail) {
                val parts = line.split(',')
                if (parts.size < 5) continue
                val date = parts[0].trim()
                val close = parts[4].trim().toDoubleOrNull() ?: continue
                if (date.isNotEmpty() && close > 0) {
                    out += DailyClose(day = date, close = close)
                }
            }
            return out
        }
    }
}

data class DailyClose(val day: String, val close: Double)

object StooqSymbols {
    /** Brent (USD/bbl) on Stooq. */
    const val BRENT_UK = "brent.uk"

    /** NYMEX heating oil — diesel/pass-through proxy (USD/gal). */
    const val HEATING_OIL = "ho.f"

    /** EUR/USD spot. */
    const val EURUSD = "eurusd"
}
