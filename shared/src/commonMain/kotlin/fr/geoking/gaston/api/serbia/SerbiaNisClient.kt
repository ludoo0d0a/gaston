package fr.geoking.gaston.api.serbia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NISStation(
    val Pj: String,
    val Naziv: String? = null,
    val Adresa: String? = null,
    val Mesto: String? = null,
    val Brend: String? = null,
    val Latitude: Double,
    val Longitude: Double,
    val Goriva: List<NISFuel>? = null
)

@Serializable
data class NISFuel(
    val NazivRobe: String
)

data class BrandPrice(
    val brand: String,
    val price: Double
)

class SerbiaNisClient(private val client: HttpClient) {
    private val mapUrl = "https://www.nisgazprom.rs/benzinske-stanice/mapa/"
    private val cenaBaseUrl = "https://cenagoriva.rs"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchNISStations(): List<NISStation> {
        val response = client.get(mapUrl) {
            header("Accept", "text/html")
            header("User-Agent", "Mozilla/5.0 (compatible; Gaston/1.0)")
        }
        val html = response.bodyAsText()
        val match = Regex("var\\s+bs\\s*=\\s*(\\{[^;]*\\})\\s*;").find(html)
        if (match != null) {
            val bsJson = match.groupValues[1]
            // The JSON is double encoded: {"items":"[...]"}
            val bsObj = json.decodeFromString<BSContainer>(bsJson)
            return json.decodeFromString<List<NISStation>>(bsObj.items)
        }
        return emptyList()
    }

    suspend fun fetchBrandPrices(path: String): List<BrandPrice> {
        val url = "$cenaBaseUrl$path"
        val response = client.get(url) {
            header("Accept", "text/html")
            header("User-Agent", "Mozilla/5.0 (compatible; Gaston/1.0)")
        }
        val html = response.bodyAsText()
        return parseCenaGorivaPage(html)
    }

    private fun parseCenaGorivaPage(html: String): List<BrandPrice> {
        val prices = mutableListOf<BrandPrice>()
        val pairRegex = Regex("<t[hd]>\\s*<img[^>]*?alt=\"([^\"]+?)\"[^>]*?>\\s*</t[hd]>\\s*<td[^>]*?data-price=\"([^\"]+?)\"[^>]*?>", RegexOption.IGNORE_CASE)
        pairRegex.findAll(html).forEach { match ->
            val altText = match.groupValues[1].trim()
            val priceStr = match.groupValues[2].trim()
            val brand = altText.replace(Regex("\\s*(pumpa\\s+)?logo\\s*$", RegexOption.IGNORE_CASE), "").trim().lowercase()
            val price = priceStr.toDoubleOrNull() ?: 0.0
            if (brand.isNotEmpty() && price > 0) {
                prices.add(BrandPrice(brand, price))
            }
        }
        return prices
    }

    @Serializable
    private data class BSContainer(val items: String)
}
