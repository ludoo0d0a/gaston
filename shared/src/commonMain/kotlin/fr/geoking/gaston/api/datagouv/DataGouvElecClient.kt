package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.*

/** Safely get results as a list; API may return results as array or single object. */
private fun resultsAsList(obj: JsonObject): List<JsonElement> {
    val results = obj["results"] ?: return emptyList()
    return when (results) {
        is JsonArray -> results
        is JsonObject -> listOf(results)
        else -> emptyList()
    }
}

/**
 * Client for the French open data IRVE (Infrastructures de Recharge pour Véhicules Électriques),
 * consolidated base from [data.gouv.fr](https://www.data.gouv.fr/datasets/base-nationale-des-irve-infrastructures-de-recharge-pour-vehicules-electriques),
 * served via ODRÉ (Open Data Réseaux Énergies) Opendatasoft API.
 *
 * Source: [Bornes IRVE - ODRÉ](https://odre.opendatasoft.com/explore/dataset/bornes-irve/).
 * No API key required. Licence: Licence Ouverte 2.0.
 */
class DataGouvElecClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://odre.opendatasoft.com/api/explore/v2.1/catalog/datasets/bornes-irve"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches EV charging stations (bornes IRVE) within [radiusKm] of (latitude, longitude).
     * Uses a bounding-box query then filters by distance; returns up to [limit] nearest stations.
     */
    suspend fun getStations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 10,
        limit: Int = 100
    ): List<DataGouvElecStation> {
        val deltaLat = radiusKm / 111.0
        val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
        val latLo = latitude - deltaLat
        val latHi = latitude + deltaLat
        val lngLo = longitude - deltaLng
        val lngHi = longitude + deltaLng
        val where = "consolidated_latitude > $latLo and consolidated_latitude < $latHi and consolidated_longitude > $lngLo and consolidated_longitude < $lngHi"
        val encodedWhere = where.encodeURLParameter()
        val requestLimit = (limit * 2).coerceAtLeast(50).coerceAtMost(100) // fetch more PDC rows to aggregate into stations
        val url = "$baseUrl/records?where=$encodedWhere&limit=$requestLimit"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "DataGouvElec API error: $body")
        }
        val raw = parseRecords(body)
        val aggregated = aggregateByStation(raw)
        return aggregated
            .mapNotNull { station ->
                val dist = haversineKm(latitude, longitude, station.latitude, station.longitude)
                if (dist <= radiusKm) station to dist else null
            }
            .sortedBy { it.second }
            .take(limit.coerceAtMost(50))
            .map { it.first }
    }

    private fun parseBool(value: Any?): Boolean? {
        when (value) {
            is Boolean -> return value
            is String -> return value.trim().lowercase() == "true"
            else -> return null
        }
    }

    private fun parseConnectorTypes(record: JsonObject): Set<String> {
        val set = mutableSetOf<String>()
        if (parseBool(record["prise_type_2"]?.jsonPrimitive?.content) == true) set.add("type_2")
        if (parseBool(record["prise_type_combo_ccs"]?.jsonPrimitive?.content) == true) set.add("combo_ccs")
        if (parseBool(record["prise_type_chademo"]?.jsonPrimitive?.content) == true) set.add("chademo")
        if (parseBool(record["prise_type_ef"]?.jsonPrimitive?.content) == true) set.add("ef")
        if (parseBool(record["prise_type_autre"]?.jsonPrimitive?.content) == true) set.add("autre")
        return set
    }

    /** Aggregate PDC records by station (id_station_itinerance); merge connector types, take first non-null for text/booleans. */
    private fun aggregateByStation(records: List<DataGouvElecStationRaw>): List<DataGouvElecStation> {
        val byStation = records.groupBy { it.stationId }
        return byStation.map { (_, group) ->
            val first = group.first()
            val connectors = group.map { it.connectorTypes }.flatten().toSet()
            val tarification = group.mapNotNull { it.tarification }.firstOrNull()?.takeIf { it.isNotBlank() }
            val openingHours = group.mapNotNull { it.openingHours }.firstOrNull()?.takeIf { it.isNotBlank() }
            val gratuit = group.mapNotNull { it.gratuit }.firstOrNull()
            val reservation = group.mapNotNull { it.reservation }.firstOrNull()
            val paymentActe = group.mapNotNull { it.paymentActe }.firstOrNull()
            val paymentCb = group.mapNotNull { it.paymentCb }.firstOrNull()
            val paymentAutre = group.mapNotNull { it.paymentAutre }.firstOrNull()
            val conditionAcces = group.mapNotNull { it.conditionAcces }.firstOrNull()?.takeIf { it.isNotBlank() }
            val nbrePdc = group.fold(0) { acc, rec -> maxOf(acc, rec.nbrePdc ?: 0) }.takeIf { n -> n > 0 }
            val puissanceKw = group.mapNotNull { it.puissanceKw }.maxOrNull()
            DataGouvElecStation(
                id = first.id,
                name = first.name,
                address = first.address,
                latitude = first.latitude,
                longitude = first.longitude,
                brand = first.brand,
                puissanceKw = puissanceKw,
                operator = first.operator,
                isOnHighway = first.isOnHighway,
                nbrePdc = nbrePdc,
                irveDetails = IrveDetails(
                    connectorTypes = connectors,
                    tarification = tarification,
                    gratuit = gratuit,
                    openingHours = openingHours,
                    reservation = reservation,
                    paymentActe = paymentActe,
                    paymentCb = paymentCb,
                    paymentAutre = paymentAutre,
                    conditionAcces = conditionAcces
                )
            )
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun parseRecords(body: String): List<DataGouvElecStationRaw> {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject
        val results = resultsAsList(obj)
        val stations = mutableListOf<DataGouvElecStationRaw>()
        for (item in results) {
            val record = item as? JsonObject ?: continue
            parseStation(record)?.let { stations.add(it) }
        }
        return stations
    }

    internal fun parseStation(record: JsonObject): DataGouvElecStationRaw? {
        val stationId = record["id_station_itinerance"]?.jsonPrimitive?.content ?: return null
        val pdcId = record["id_pdc_itinerance"]?.jsonPrimitive?.content ?: stationId
        val id = stationId
        val lat = record["consolidated_latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: (record["coordonneesxy"]?.jsonObject)?.let { coord ->
                coord["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
            }
        val lng = record["consolidated_longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: (record["coordonneesxy"]?.jsonObject)?.let { coord ->
                coord["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            }
        if (lat == null || lng == null) return null
        val brand = record["nom_enseigne"]?.jsonPrimitive?.content?.trim()
        val name = record["nom_station"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank {
            brand ?: "Station"
        }
        val address = record["adresse_station"]?.jsonPrimitive?.content?.trim().orEmpty()
        val power = record["puissance_nominale"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val operator = record["nom_operateur"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        val implantation = record["implantation_station"]?.jsonPrimitive?.content?.trim().orEmpty()
        val isOnHighway = implantation.contains("autoroute", ignoreCase = true) ||
            (address + " " + (record["nom_station"]?.jsonPrimitive?.content?.trim().orEmpty())).contains("autoroute", ignoreCase = true)
        val nbrePdc = record["nbre_pdc"]?.jsonPrimitive?.content?.toIntOrNull()
        val connectorTypes = parseConnectorTypes(record)
        val tarification = record["tarification"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        val openingHours = record["horaires"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        val gratuit = parseBool(record["gratuit"]?.jsonPrimitive?.content)
        val reservation = parseBool(record["reservation"]?.jsonPrimitive?.content)
        val paymentActe = parseBool(record["paiement_acte"]?.jsonPrimitive?.content)
        val paymentCb = parseBool(record["paiement_cb"]?.jsonPrimitive?.content)
        val paymentAutre = parseBool(record["paiement_autre"]?.jsonPrimitive?.content)
        val conditionAcces = record["condition_acces"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        return DataGouvElecStationRaw(
            stationId = stationId,
            id = id,
            name = name,
            address = address,
            latitude = lat,
            longitude = lng,
            brand = brand,
            puissanceKw = power,
            operator = operator,
            isOnHighway = isOnHighway,
            nbrePdc = nbrePdc,
            connectorTypes = connectorTypes,
            tarification = tarification,
            openingHours = openingHours,
            gratuit = gratuit,
            reservation = reservation,
            paymentActe = paymentActe,
            paymentCb = paymentCb,
            paymentAutre = paymentAutre,
            conditionAcces = conditionAcces
        )
    }
}

/** Raw PDC record before aggregation by station. */
internal data class DataGouvElecStationRaw(
    val stationId: String,
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String?,
    val puissanceKw: Double?,
    val operator: String?,
    val isOnHighway: Boolean,
    val nbrePdc: Int?,
    val connectorTypes: Set<String>,
    val tarification: String?,
    val openingHours: String?,
    val gratuit: Boolean?,
    val reservation: Boolean?,
    val paymentActe: Boolean?,
    val paymentCb: Boolean?,
    val paymentAutre: Boolean?,
    val conditionAcces: String?
)

/** EV charging station from ODRÉ IRVE (data.gouv.fr base nationale IRVE). */
data class DataGouvElecStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val puissanceKw: Double? = null,
    val operator: String? = null,
    val isOnHighway: Boolean = false,
    /** Number of points de charge (charging points) at the station. */
    val nbrePdc: Int? = null,
    /** Connector types, tarification, horaires, payment, etc. */
    val irveDetails: IrveDetails? = null
)
