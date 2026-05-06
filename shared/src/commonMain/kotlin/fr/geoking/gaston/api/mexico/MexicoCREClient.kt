package fr.geoking.gaston.api.mexico

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readLine

data class MexicoPlace(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double
)

data class MexicoPrice(
    val placeId: String,
    val type: String,
    val price: Double
)

class MexicoCREClient(private val client: HttpClient) {
    private val placesUrl = "https://publicacionexterna.azurewebsites.net/publicaciones/places"
    private val pricesUrl = "https://publicacionexterna.azurewebsites.net/publicaciones/prices"

    suspend fun fetchPlaces(): List<MexicoPlace> {
        val response = client.get(placesUrl) {
            header("User-Agent", "Mozilla/5.0 (compatible; Gaston/1.0)")
        }
        val channel = response.bodyAsChannel()
        val places = mutableListOf<MexicoPlace>()
        val startTag = "<place "
        val endTag = "</place>"

        val idRegex = Regex("place_id=\"(\\d+)\"")
        val nameRegex = Regex("<name>([\\s\\S]*?)</name>")
        val xRegex = Regex("<x>([\\s\\S]*?)</x>")
        val yRegex = Regex("<y>([\\s\\S]*?)</y>")

        val currentBlock = StringBuilder()
        while (true) {
            val line = channel.readLine() ?: break
            currentBlock.append(line).append("\n")

            while (true) {
                val startIdx = currentBlock.indexOf(startTag)
                if (startIdx == -1) {
                    currentBlock.setLength(0)
                    break
                }
                val endIdx = currentBlock.indexOf(endTag, startIdx)
                if (endIdx == -1) {
                    if (startIdx > 0) {
                        currentBlock.delete(0, startIdx)
                    }
                    break
                }

                val block = currentBlock.substring(startIdx, endIdx + endTag.length)
                currentBlock.delete(0, endIdx + endTag.length)

                val idMatch = idRegex.find(block) ?: continue
                val id = idMatch.groupValues[1]
                val name = nameRegex.find(block)?.groupValues?.get(1)?.trim() ?: ""
                val lon = xRegex.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val lat = yRegex.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                if (lat != 0.0 && lon != 0.0) {
                    places.add(MexicoPlace(id, name, lat, lon))
                }
            }
        }
        return places
    }

    suspend fun fetchPrices(): List<MexicoPrice> {
        val response = client.get(pricesUrl) {
            header("User-Agent", "Mozilla/5.0 (compatible; Gaston/1.0)")
        }
        val channel = response.bodyAsChannel()
        val prices = mutableListOf<MexicoPrice>()
        val startTag = "<place "
        val endTag = "</place>"

        val idRegex = Regex("place_id=\"(\\d+)\"")
        val gpRegex = Regex("<gas_price\\s+type=\"(\\w+)\">([^<]+)</gas_price>")

        val currentBlock = StringBuilder()
        while (true) {
            val line = channel.readLine() ?: break
            currentBlock.append(line).append("\n")

            while (true) {
                val startIdx = currentBlock.indexOf(startTag)
                if (startIdx == -1) {
                    currentBlock.setLength(0)
                    break
                }
                val endIdx = currentBlock.indexOf(endTag, startIdx)
                if (endIdx == -1) {
                    if (startIdx > 0) {
                        currentBlock.delete(0, startIdx)
                    }
                    break
                }

                val block = currentBlock.substring(startIdx, endIdx + endTag.length)
                currentBlock.delete(0, endIdx + endTag.length)

                val idMatch = idRegex.find(block) ?: continue
                val id = idMatch.groupValues[1]
                gpRegex.findAll(block).forEach { gpMatch ->
                    val type = gpMatch.groupValues[1]
                    val price = gpMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        prices.add(MexicoPrice(id, type, price))
                    }
                }
            }
        }
        return prices
    }
}
