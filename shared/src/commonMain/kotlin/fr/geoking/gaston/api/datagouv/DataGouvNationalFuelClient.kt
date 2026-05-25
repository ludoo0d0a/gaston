package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * National daily average pump prices from data.gouv.fr / data.economie.gouv.fr
 * [prix-carburants-quotidien](https://data.economie.gouv.fr/explore/dataset/prix-carburants-quotidien).
 *
 * Aggregates all stations per fuel and calendar day ([prix_maj]).
 */
class DataGouvNationalFuelClient(
    private val client: HttpClient,
    private val baseUrl: String = QUOTIDIEN_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches national average EUR/L per fuel for each day in [[fromDay], [toDay]] (inclusive).
     * Keys are fuel ids ([MapPoiFilter.fuelNameToId]); values are sorted by day ascending.
     */
    suspend fun fetchNationalDailyHistory(
        fromDay: String,
        toDay: String
    ): Map<String, List<NationalFuelDailyAverage>> {
        val fromIso = "${fromDay}T00:00:00"
        val toExclusive = dayAfter(toDay)
        val where = "prix_maj >= '$fromIso' AND prix_maj < '${toExclusive}T00:00:00'"
        val select = "prix_nom,year(prix_maj),month(prix_maj),day(prix_maj),avg(prix_valeur)"
        val groupBy = "prix_nom,year(prix_maj),month(prix_maj),day(prix_maj)"
        val orderBy = "year(prix_maj) DESC,month(prix_maj) DESC,day(prix_maj) DESC"
        val limit = HISTORY_LIMIT
        val url = "$baseUrl/records?" +
            "select=${select.encodeURLParameter()}&" +
            "where=${where.encodeURLParameter()}&" +
            "group_by=${groupBy.encodeURLParameter()}&" +
            "order_by=${orderBy.encodeURLParameter()}&" +
            "limit=$limit"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "National fuel history API error: ${body.take(300)}")
        }
        return parseAggregatedHistory(body)
    }

    internal fun parseAggregatedHistory(body: String): Map<String, List<NationalFuelDailyAverage>> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyMap()
        val byFuel = mutableMapOf<String, MutableList<NationalFuelDailyAverage>>()
        for (item in results) {
            val row = item.jsonObject
            val fuelName = row["prix_nom"]?.jsonPrimitive?.contentOrNull ?: continue
            val fuelId = MapPoiFilter.fuelNameToId(fuelName) ?: continue
            val year = row["year(prix_maj)"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
            val month = row["month(prix_maj)"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
            val day = row["day(prix_maj)"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
            val avg = row["avg(prix_valeur)"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            val dayStr = isoDay(year, month, day)
            byFuel.getOrPut(fuelId) { mutableListOf() }.add(
                NationalFuelDailyAverage(day = dayStr, priceEurPerL = avg)
            )
        }
        return byFuel.mapValues { (_, list) -> list.sortedBy { it.day } }
    }

    private fun isoDay(year: Int, month: Int, day: Int): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    private fun dayAfter(isoDay: String): String {
        val parts = isoDay.split("-")
        if (parts.size != 3) return isoDay
        val y = parts[0].toIntOrNull() ?: return isoDay
        val m = parts[1].toIntOrNull() ?: return isoDay
        val d = parts[2].toIntOrNull() ?: return isoDay
        val daysInMonth = when (m) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
            else -> 30
        }
        if (d < daysInMonth) return isoDay(y, m, d + 1)
        if (m < 12) return isoDay(y, m + 1, 1)
        return isoDay(y + 1, 1, 1)
    }

    companion object {
        const val QUOTIDIEN_BASE_URL =
            "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien"
        /** Max rows: ~6 fuels × 60 days. */
        private const val HISTORY_LIMIT = 400
    }
}

@Serializable
data class NationalFuelDailyAverage(
    val day: String,
    val priceEurPerL: Double
)
