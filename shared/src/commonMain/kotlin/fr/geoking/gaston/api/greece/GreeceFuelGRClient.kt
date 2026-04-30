package fr.geoking.gaston.api.greece

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

data class FuelGRStation(
    val id: String,
    val lat: Double,
    val lng: Double,
    val brand: String,
    val address: String?,
    val county: String?,
    val price: Double
)

class GreeceFuelGRClient(private val client: HttpClient) {
    private val baseUrl = "https://deixto.gr/fuel/get_data_v4.php"
    private val dev = "android.4.0-b2da2cf97330ca3b"
    private val dsig = "google/coral/coral:14/UQ1A.240205.004/1709778835:userdebug/release-keys"
    private val apksig = "UPJ2YQunu9eGXu8a/WOiVNAZlYA="

    suspend fun fetchNearbyStations(lat: Double, lon: Double, fuelType: String): List<FuelGRStation> {
        val url = "$baseUrl?dev=$dev&lat=$lat&long=$lon&f=$fuelType&b=0&d=30&p=0&dSig=$dsig&iLoc=unknown&apkSig=$apksig"
        val response = client.get(url) {
            header("Accept", "application/xml")
            header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 13)")
        }
        val xml = response.bodyAsText()
        return parseXML(xml)
    }

    private fun parseXML(xml: String): List<FuelGRStation> {
        val stations = mutableListOf<FuelGRStation>()
        val gsRegex = Regex("<gs\\s+id=\"(\\d+)\"([^>]*)>([\\s\\S]*?)</gs>")

        gsRegex.findAll(xml).forEach { match ->
            val id = match.groupValues[1]
            val attrs = match.groupValues[2]
            val body = match.groupValues[3]

            val lat = getTagValue(body, "lt")?.toDoubleOrNull() ?: return@forEach
            val lng = getTagValue(body, "lg")?.toDoubleOrNull() ?: return@forEach

            val priceMatch = Regex("<ft[^>]+pr=\"([^\"]+)\"").find(body)
            val price = priceMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: return@forEach
            if (price <= 0) return@forEach

            val cntMatch = Regex("cnt=\"([^\"]*)\"").find(attrs)
            val county = cntMatch?.groupValues?.get(1)

            val brand = getTagValue(body, "br") ?: ""

            stations.add(FuelGRStation(
                id = id,
                lat = lat,
                lng = lng,
                brand = brand,
                address = getTagValue(body, "ad"),
                county = county,
                price = price
            ))
        }
        return stations
    }

    private fun getTagValue(body: String, tag: String): String? {
        val regex = Regex("<$tag[^>]*>(?:<!\\[CDATA\\[([^\\]]*?)\\]\\]>|([^<]*))</$tag>")
        val match = regex.find(body)
        return match?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: match?.groupValues?.get(2)?.trim()
    }
}
