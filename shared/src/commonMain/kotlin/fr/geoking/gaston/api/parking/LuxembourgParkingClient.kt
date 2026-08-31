package fr.geoking.gaston.api.parking

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for Ville de Luxembourg (VDL) Parking API (https://feed.vdl.lu/circulation/parking/feed.json).
 * Provides real-time capacity and available free spaces for car parks in Luxembourg.
 */
class LuxembourgParkingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://feed.vdl.lu/circulation/parking/feed.json"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches all real-time parking locations in Luxembourg.
     */
    suspend fun getParkings(): List<LuxembourgParkingLocation> = runCatching {
        val response = client.get(baseUrl)
        if (response.status.value != 200) return emptyList()
        val body = response.bodyAsText()
        parseParkings(body)
    }.getOrElse { emptyList() }

    fun parseParkings(jsonBody: String): List<LuxembourgParkingLocation> {
        val root = json.parseToJsonElement(jsonBody).jsonObject
        val parkingsObj = root["parking"]?.jsonObject ?: return emptyList()

        return parkingsObj.values.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val title = obj["titre"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val total = obj["total"]?.jsonPrimitive?.intOrNull
            val available = obj["actuel"]?.jsonPrimitive?.intOrNull
            val isOpen = obj["ouvert"]?.jsonPrimitive?.booleanOrNull ?: true
            val isFull = obj["complet"]?.jsonPrimitive?.booleanOrNull ?: false
            val isBreakdown = obj["panne"]?.jsonPrimitive?.booleanOrNull ?: false

            val locObj = obj["localisation"]?.jsonObject
            val lat = locObj?.get("latitude")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = locObj?.get("longitude")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null

            val quartierObj = locObj.get("quartier")?.jsonObject
            val quartier = quartierObj?.get("en")?.jsonPrimitive?.content
                ?: quartierObj?.get("fr")?.jsonPrimitive?.content

            val entreeArr = locObj["entree"]
            val address = if (entreeArr != null && entreeArr is kotlinx.serialization.json.JsonArray && entreeArr.isNotEmpty()) {
                entreeArr[0].jsonObject["adresse"]?.jsonPrimitive?.content
            } else null

            val paymentObj = obj["paiement"]?.jsonObject
            val tariffObj = paymentObj?.get("tarif")?.jsonObject
            val priceInfo = tariffObj?.get("en")?.jsonPrimitive?.content
                ?: tariffObj?.get("fr")?.jsonPrimitive?.content

            val openingObj = obj["ouverture"]?.jsonObject
            val openingHours = openingObj?.get("en")?.jsonPrimitive?.content
                ?: openingObj?.get("fr")?.jsonPrimitive?.content

            val status = when {
                isBreakdown -> "out_of_service"
                !isOpen -> "closed"
                isFull -> "full"
                else -> "open"
            }

            LuxembourgParkingLocation(
                id = id,
                title = title,
                latitude = lat,
                longitude = lon,
                totalCapacity = total,
                availableSpaces = available,
                status = status,
                address = address,
                quartier = quartier,
                openingHours = openingHours?.trim(),
                priceInfo = priceInfo?.trim()
            )
        }
    }
}

data class LuxembourgParkingLocation(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val totalCapacity: Int?,
    val availableSpaces: Int?,
    val status: String,
    val address: String?,
    val quartier: String?,
    val openingHours: String?,
    val priceInfo: String?
)
