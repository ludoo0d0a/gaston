package fr.geoking.gaston.api.switzerland

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ComparisStation(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val brand: String?,
    val prices: List<ComparisFuelPrice>,
)

data class ComparisFuelPrice(
    val fuelName: String,
    val price: Double,
)

class ComparisClient(private val client: HttpClient) {
    private val pageUrl = "https://www.comparis.ch/benzin-preise"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAllStations(): List<ComparisStation> {
        val html = client.get(pageUrl) {
            header("Accept", "text/html")
            header("User-Agent", DESKTOP_USER_AGENT)
        }.bodyAsText()
        val scriptJson = Regex(
            """<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.get(1)?.trim() ?: return emptyList()

        val root = json.parseToJsonElement(scriptJson).jsonObject
        val data = root["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("data") as? JsonArray
            ?: return emptyList()

        return data.mapNotNull { element ->
            val station = element.jsonObject
            val location = station["location"]?.jsonObject ?: return@mapNotNull null
            val lat = location["lat"]?.jsonPrimitive?.double ?: return@mapNotNull null
            val lng = location["lng"]?.jsonPrimitive?.double ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null

            val fuels = parseFuels(station["fuelCollection"]?.jsonObject)
            ComparisStation(
                id = station["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = station["displayName"]?.jsonPrimitive?.content?.trim().orEmpty(),
                address = station["formattedAddress"]?.jsonPrimitive?.content?.trim().orEmpty(),
                lat = lat,
                lng = lng,
                brand = station["brand"]?.jsonPrimitive?.content,
                prices = fuels,
            )
        }
    }

    private fun parseFuels(fuelCollection: JsonObject?): List<ComparisFuelPrice> {
        if (fuelCollection == null) return emptyList()
        return fuelCollection.entries.mapNotNull { (key, value) ->
            val price = value.jsonObject["displayPrice"]?.jsonPrimitive?.double ?: return@mapNotNull null
            ComparisFuelPrice(fuelName = key.uppercase(), price = price)
        }
    }

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"
    }
}
