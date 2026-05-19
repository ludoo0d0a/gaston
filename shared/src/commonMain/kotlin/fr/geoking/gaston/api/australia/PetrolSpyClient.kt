package fr.geoking.gaston.api.australia

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.cos
import kotlin.math.PI

@Serializable
data class PetrolSpyResponse(
    val message: PetrolSpyMessage,
)

@Serializable
data class PetrolSpyMessage(
    val list: List<PetrolSpyStation> = emptyList(),
)

@Serializable
data class PetrolSpyStation(
    val id: Int,
    val name: String,
    val address: String? = null,
    val brand: String? = null,
    val postCode: String? = null,
    val country: String? = null,
    val location: PetrolSpyLocation,
    val prices: Map<String, PetrolSpyPrice>? = null,
)

@Serializable
data class PetrolSpyLocation(
    val x: Double,
    val y: Double,
)

@Serializable
data class PetrolSpyPrice(
    val amount: Double,
)

class PetrolSpyClient(private val client: HttpClient) {
    private val baseUrl = "https://petrolspy.com.au/webservice-1/station/box"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchStationsInBox(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double,
    ): List<PetrolSpyStation> {
        val url = "$baseUrl?neLat=$latMax&neLng=$lonMax&swLat=$latMin&swLng=$lonMin"
        val body = client.get(url) {
            header("Accept", "application/json")
            header("User-Agent", DESKTOP_USER_AGENT)
            header("Referer", "https://petrolspy.com.au/")
            header("x-ps-fp", "06999ae0c2fa02880528b0a549374286")
            header("X-Requested-With", "XMLHttpRequest")
        }.bodyAsText()
        return json.decodeFromString<PetrolSpyResponse>(body).message.list
    }

    fun boundingBox(lat: Double, lon: Double, radiusKm: Double): BoundingBox {
        val cappedKm = radiusKm.coerceAtMost(15.0)
        val latDelta = cappedKm / 111.0
        val lonDelta = cappedKm / (111.0 * cos(lat * PI / 180.0).coerceAtLeast(0.01))
        return BoundingBox(
            latMin = lat - latDelta,
            lonMin = lon - lonDelta,
            latMax = lat + latDelta,
            lonMax = lon + lonDelta,
        )
    }

    data class BoundingBox(
        val latMin: Double,
        val lonMin: Double,
        val latMax: Double,
        val lonMax: Double,
    )

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"
    }
}
