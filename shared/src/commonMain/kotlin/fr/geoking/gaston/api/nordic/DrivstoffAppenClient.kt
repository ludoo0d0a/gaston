package fr.geoking.julius.api.nordic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable
import fr.geoking.julius.shared.util.md5

@Serializable
data class DrivstoffAuthSession(
    val token: String
)

@Serializable
data class DrivstoffStation(
    val id: Int,
    val name: String,
    val location: String,
    val coordinates: DrivstoffCoordinates,
    val deleted: Int,
    val stationTypeId: Int,
    val prices: List<DrivstoffPrice>,
    val brand: DrivstoffBrand
)

@Serializable
data class DrivstoffCoordinates(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class DrivstoffPrice(
    val fuelTypeId: Int,
    val price: Double,
    val deleted: Int
)

@Serializable
data class DrivstoffBrand(
    val name: String
)

class DrivstoffAppenClient(private val client: HttpClient) {
    private val apiBase = "https://api.drivstoffappen.no/api/v1"

    suspend fun fetchStations(countryId: Int): List<DrivstoffStation> {
        val authRes = client.get("$apiBase/authorization-sessions") {
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
        }
        val session = authRes.body<DrivstoffAuthSession>()
        val token = session.token
        val shifted = token.drop(1) + token.take(1)
        val apiKey = md5(shifted)

        val response = client.get("$apiBase/stations?countryId=$countryId") {
            header("Accept", "application/json")
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
            header("X-API-KEY", apiKey)
            header("X-CLIENT-ID", "com.raskebiler.drivstoff.appen.android")
        }
        return response.body<List<DrivstoffStation>>()
    }
}
