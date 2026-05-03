package fr.geoking.gaston.api.routex

import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.shared.logging.log
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/** Maximum number of POIs to return per getResults call. */
const val ROUTEX_MAX_POIS = 20

/** Minimum radius (km) sent to the API so edge stations (e.g. Terville) are returned and not dropped by tight viewport. */
const val ROUTEX_MIN_REQUEST_RADIUS_KM = 3

/**
 * Computes the search radius in km that covers the visible map area from center, zoom and size.
 * Uses Web Mercator: at zoom z the world is 256*2^z pixels wide; latitude scale varies with cos(lat).
 * Returns half the diagonal of the visible rectangle in km, so the API scope matches the view.
 */
fun radiusKmFromMapViewport(
    centerLat: Double,
    centerLng: Double,
    zoom: Float,
    mapWidthPx: Int,
    mapHeightPx: Int
): Int {
    val z = zoom.toDouble().coerceIn(0.0, 24.0)
    val scale = 256.0 * 2.0.pow(z)
    val latRad = centerLat * PI / 180.0
    val cosLat = cos(latRad).coerceIn(0.01, 1.0)
    // Visible span in degrees (Web Mercator)
    val halfLngDeg = (mapWidthPx / 2.0) * 360.0 / scale
    val halfLatDeg = (mapHeightPx / 2.0) * 360.0 * cosLat / scale
    // Convert to km at center latitude (1° lat ≈ 111 km, 1° lng ≈ 111*cos(lat) km)
    val halfLngKm = halfLngDeg * 111.0 * cosLat
    val halfLatKm = halfLatDeg * 111.0
    val radiusKm = sqrt(halfLngKm * halfLngKm + halfLatKm * halfLatKm)
    return radiusKm.toInt().coerceAtLeast(1)
}

/**
 * Client for the Routex (Wigeogis) SiteFinder API.
 * Fetches gas station results from https://app.wigeogis.com/kunden/routex-sitefinder/backend/getResults
 * Results are restricted to the requested map bounds and limited to [ROUTEX_MAX_POIS].
 */
class RoutexClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://app.wigeogis.com/kunden/routex-sitefinder/backend"
) {

    private data class BoundsBox(val minLng: Double, val minLat: Double, val maxLng: Double, val maxLat: Double) {
        fun contains(lat: Double, lng: Double): Boolean =
            lat in minLat..maxLat && lng in minLng..maxLng
    }

    /**
     * Request body for getResults (siteFinder section).
     * Coordinates in locations.loc are [latitude, longitude].
     */
    @Serializable
    data class RoutexRequest(
        val zoom: Int = 12,
        val bounds: String,
        val title: String = "",
        val section: String = "siteFinder",
        val type: String = "",
        val routeDistance: Int = 5,
        val routeDistanceMin: Int = 1,
        val routeDistanceMax: Int = 5,
        val routeDistanceStep: Int = 1,
        val routeDistanceUnit: String = "km",
        val routeDistanceOverflow: Boolean = false,
        val avoidanceDistanceThreshold: Int = 500,
        val routeWkt: String? = null,
        val siteFilter: String = "radius",
        val radius: Int = 5,
        val radiusMin: Int = 1,
        val radiusMax: Int = 50,
        val radiusStep: Int = 1,
        val radiusUnit: String = "km",
        val width: Double = 2.6,
        val widthMin: Int = 2,
        val widthMax: Double = 2.6,
        val widthStep: Double = 0.1,
        val widthUnit: String = "m",
        val height: Int = 4,
        val heightMin: Int = 2,
        val heightMax: Int = 4,
        val heightStep: Double = 0.1,
        val heightUnit: String = "m",
        val length: Double = 18.75,
        val lengthMin: Int = 6,
        val lengthMax: Double = 18.75,
        val lengthStep: Double = 0.25,
        val lengthUnit: String = "m",
        val avoidMotorways: Boolean = false,
        val avoidTolls: Boolean = false,
        val avoidFerries: Boolean = false,
        val truckRestrictions: Boolean = false,
        val routeFilterChange: Boolean = false,
        val routingMaxWaypoints: Int = 8,
        val locations: RoutexLocations,
        val filters: List<Nothing> = emptyList(),
        val lng: String = "en"
    )

    @Serializable
    data class RoutexLocations(
        val loc: RoutexLoc,
        val routingLocations: List<RoutexRoutingLoc> = defaultRoutingLocations
    )

    @Serializable
    data class RoutexLoc(
        val id: String = "loc",
        val coordinates: List<Double>,
        val address: String = "Your current location",
        val radius: Int = 5
    )

    @Serializable
    data class RoutexRoutingLoc(
        val id: String,
        val coordinates: List<Double>? = null,
        val address: String = "",
        val visible: Boolean = true
    )

    companion object {
        private val defaultRoutingLocations = (0..7).map { i ->
            RoutexRoutingLoc(
                id = "wp$i",
                coordinates = null,
                address = "",
                visible = i < 2
            )
        }

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses API response into a list of sites. Visible for testing.
         * Handles: root array [...], or object with results[], sites[], or GeoJSON features[].
         * Supports item format: id, x (lng), y (lat), brand_id.
         */
        internal fun parseResults(body: String): List<RoutexSite> {
            val element = json.parseToJsonElement(body)

            val array = when (element) {
                is JsonObject -> {
                    element["results"]?.jsonArray
                        ?: element["sites"]?.jsonArray
                        ?: element["data"]?.jsonObject?.get("results")?.jsonArray
                        ?: element["data"]?.jsonObject?.get("sites")?.jsonArray
                        ?: element["features"]?.jsonArray
                }
                is kotlinx.serialization.json.JsonArray -> element
                else -> null
            }

            if (array == null) {
                val keys = (element as? JsonObject)?.keys?.joinToString(",") ?: "not-an-object"
                log.w { "[RoutexClient] parseResults: no results/sites/features array; root keys=$keys bodyPreview=${body.take(400)}" }
                return emptyList()
            }

            val results = array.mapNotNull { item ->
                when (item) {
                    is JsonObject -> parseSiteFromObject(item)
                    else -> null
                }
            }
            if (results.isEmpty() && array.isNotEmpty()) {
                val first = array.firstOrNull()
                val firstKeys = (first as? JsonObject)?.keys?.joinToString(",") ?: "not-json-object"
                log.w { "[RoutexClient] parseResults: array has ${array.size} items but parsed 0; first item keys=$firstKeys" }
            }
            return results
        }

        private fun parseBoolOrNull(e: JsonElement?): Boolean? {
            when (e) {
                is JsonPrimitive -> {
                    val c = e.content
                    if (c == "true") return true
                    if (c == "false") return false
                    c.toIntOrNull()?.let { if (it != 0) true else false }?.let { return it }
                }
                else -> {}
            }
            return null
        }

        private fun parseStringList(e: JsonElement?): List<String> {
            val arr = e?.jsonArray ?: return emptyList()
            return arr.mapNotNull { (it as? JsonPrimitive)?.content }
        }

        private fun parseDetails(props: JsonObject): PoiAmenities {
            fun get(key: String): JsonElement? = props[key]
            return PoiAmenities(
                manned24h = parseBoolOrNull(get("manned_24h")),
                mannedAutomat24h = parseBoolOrNull(get("manned_automat_24h")),
                automat = parseBoolOrNull(get("automat")),
                motorwayIndicator = parseBoolOrNull(get("motorway_indicator")),
                restaurant = parseBoolOrNull(get("restaurant")),
                shop = parseBoolOrNull(get("shop")),
                snackbar = parseBoolOrNull(get("snackbar")),
                carWash = parseBoolOrNull(get("car_wash")),
                showers = parseBoolOrNull(get("showers")),
                adBluePump = parseBoolOrNull(get("ad_blue_pump")),
                r4tNetwork = parseBoolOrNull(get("r4t_network")),
                carVignette = parseBoolOrNull(get("car_vignette")),
                highspeedDiesel = parseBoolOrNull(get("highspeed_diesel")),
                truckIndicator = parseBoolOrNull(get("truck_indicator")),
                truckParking = parseBoolOrNull(get("truck_parking")),
                truckDiesel = parseBoolOrNull(get("truck_diesel")),
                truckLane = parseBoolOrNull(get("truck_lane")),
                dieselBio = parseBoolOrNull(get("diesel_bio")),
                hvo100 = parseBoolOrNull(get("hvo100")),
                lng = parseBoolOrNull(get("lng")),
                lpg = parseBoolOrNull(get("lpg")),
                cng = parseBoolOrNull(get("cng")),
                adBlueCanister = parseBoolOrNull(get("ad_blue_canister")),
                monOpenFuel = (get("mon_open_fuel") as? JsonPrimitive)?.content,
                monCloseFuel = (get("mon_close_fuel") as? JsonPrimitive)?.content,
                tueOpenFuel = (get("tue_open_fuel") as? JsonPrimitive)?.content,
                tueCloseFuel = (get("tue_close_fuel") as? JsonPrimitive)?.content,
                wedOpenFuel = (get("wed_open_fuel") as? JsonPrimitive)?.content,
                wedCloseFuel = (get("wed_close_fuel") as? JsonPrimitive)?.content,
                thuOpenFuel = (get("thu_open_fuel") as? JsonPrimitive)?.content,
                thuCloseFuel = (get("thu_close_fuel") as? JsonPrimitive)?.content,
                friOpenFuel = (get("fri_open_fuel") as? JsonPrimitive)?.content,
                friCloseFuel = (get("fri_close_fuel") as? JsonPrimitive)?.content,
                satOpenFuel = (get("sat_open_fuel") as? JsonPrimitive)?.content,
                satCloseFuel = (get("sat_close_fuel") as? JsonPrimitive)?.content,
                sunOpenFuel = (get("sun_open_fuel") as? JsonPrimitive)?.content,
                sunCloseFuel = (get("sun_close_fuel") as? JsonPrimitive)?.content,
                open24h = parseBoolOrNull(get("open_24h")),
                openingHoursFuel = parseStringList(get("opening_hours_fuel"))
            )
        }

        private fun parseSiteFromObject(obj: JsonObject): RoutexSite? {
            val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
            fun parseDouble(e: JsonElement): Double? =
                (e as? JsonPrimitive)?.content?.toDoubleOrNull()

            val (lat, lng) = when {
                coords != null && coords.size >= 2 -> {
                    val a = parseDouble(coords[0]) ?: return null
                    val b = parseDouble(coords[1]) ?: return null
                    Pair(b, a)
                }
                else -> {
                    val latVal = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        ?: obj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val lngVal = obj["lng"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        ?: obj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    if (latVal != null && lngVal != null) {
                        Pair(latVal, lngVal)
                    } else {
                        val xVal = obj["x"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        val yVal = obj["y"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        if (xVal != null && yVal != null) {
                            Pair(yVal, xVal)
                        } else {
                            val coordList = obj["coordinates"]?.jsonArray
                            if (coordList != null && coordList.size >= 2) {
                                val first = parseDouble(coordList[0]) ?: return null
                                val second = parseDouble(coordList[1]) ?: return null
                                // GeoJSON and many APIs use [longitude, latitude]
                                Pair(second, first)
                            } else return null
                        }
                    }
                }
            }

            val props = obj["properties"]?.jsonObject ?: obj
            val siteName = props["site_name"]?.jsonPrimitive?.content
                ?: obj["site_name"]?.jsonPrimitive?.content
            val name = props["name"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
                ?: props["title"]?.jsonPrimitive?.content
                ?: siteName
                ?: ""
            val address = props["address"]?.jsonPrimitive?.content
                ?: obj["address"]?.jsonPrimitive?.content
                ?: ""
            val id = obj["id"]?.jsonPrimitive?.content
                ?: props["id"]?.jsonPrimitive?.content
                ?: "${lat}_${lng}"
            val brand = props["brand"]?.jsonPrimitive?.content
                ?: obj["brand"]?.jsonPrimitive?.content
                ?: obj["brand_id"]?.jsonPrimitive?.content
                ?: props["brand_id"]?.jsonPrimitive?.content
            val postcode = props["postcode"]?.jsonPrimitive?.content
                ?: obj["postcode"]?.jsonPrimitive?.content
            val addressLocal = props["address_local"]?.jsonPrimitive?.content
                ?: obj["address_local"]?.jsonPrimitive?.content
            val countryLocal = props["country_local"]?.jsonPrimitive?.content
                ?: obj["country_local"]?.jsonPrimitive?.content
            val townLocal = props["town_local"]?.jsonPrimitive?.content
                ?: obj["town_local"]?.jsonPrimitive?.content
            val details = parseDetails(props)

            return RoutexSite(
                id = id,
                name = name.ifBlank { "Gas station" },
                address = address,
                latitude = lat,
                longitude = lng,
                brand = brand,
                siteName = siteName,
                postcode = postcode,
                addressLocal = addressLocal,
                countryLocal = countryLocal,
                townLocal = townLocal,
                details = details
            )
        }

        /**
         * Filters sites to those inside the bounding box, sorts by distance to box center (so closest
         * stations are kept when capping), and limits to maxPois. Visible for testing.
         */
        internal fun filterInBoundsAndLimit(
            sites: List<RoutexSite>,
            minLng: Double,
            minLat: Double,
            maxLng: Double,
            maxLat: Double,
            maxPois: Int = ROUTEX_MAX_POIS
        ): List<RoutexSite> {
            val centerLat = (minLat + maxLat) / 2.0
            val centerLng = (minLng + maxLng) / 2.0
            fun distSq(s: RoutexSite): Double {
                val dlat = s.latitude - centerLat
                val dlng = s.longitude - centerLng
                return dlat * dlat + dlng * dlng
            }
            return sites
                .filter { it.latitude in minLat..maxLat && it.longitude in minLng..maxLng }
                .sortedBy { distSq(it) }
                .take(maxPois)
        }
    }

    /**
     * Build bounding box from center (lat, lng) and radius in km.
     */
    private fun boundsBoxFromCenter(lat: Double, lng: Double, radiusKm: Int): BoundsBox {
        // Approximate: 1° lat ≈ 111 km, 1° lng ≈ 111 * cos(lat) km
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(lat * PI / 180).coerceIn(0.01, 1.0))
        return BoundsBox(
            minLng = lng - lngDelta,
            minLat = lat - latDelta,
            maxLng = lng + lngDelta,
            maxLat = lat + latDelta
        )
    }

    /**
     * Build bounds string from center (lat, lng) and radius in km.
     * Format: "minLng, minLat, maxLng, maxLat"
     */
    fun boundsFromCenter(lat: Double, lng: Double, radiusKm: Int): String {
        val box = boundsBoxFromCenter(lat, lng, radiusKm)
        return "${box.minLng}, ${box.minLat}, ${box.maxLng}, ${box.maxLat}"
    }

    /**
     * Call getResults and return raw JSON string (for debugging) or parsed gas station list.
     * Response structure is not fully documented; we parse flexibly from either
     * a "results"/"sites" array or GeoJSON-style "features".
     */
    suspend fun getResults(latitude: Double, longitude: Double, radiusKm: Int = 5): List<RoutexSite> {
        val requestRadiusKm = maxOf(radiusKm, ROUTEX_MIN_REQUEST_RADIUS_KM)
        val requestBox = boundsBoxFromCenter(latitude, longitude, requestRadiusKm)
        // Filter using the same box we requested so we don't drop stations the API returned
        // (e.g. edge stations like Terville that fall just outside a tight viewport radius).
        val filterBox = requestBox

        val request = RoutexRequest(
            bounds = "${requestBox.minLng}, ${requestBox.minLat}, ${requestBox.maxLng}, ${requestBox.maxLat}",
            locations = RoutexLocations(
                loc = RoutexLoc(
                    coordinates = listOf(latitude, longitude),
                    radius = requestRadiusKm
                )
            ),
            radius = requestRadiusKm
        )

        val response = client.post("$baseUrl/getResults") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val body = response.bodyAsText()
        log.d { "[RoutexClient] getResults lat=$latitude lon=$longitude requestRadius=$requestRadiusKm status=${response.status.value} bodyLength=${body.length}" }
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Routex API error: $body")
        }

        val all = parseResults(body)
        val results = filterInBoundsAndLimit(
            all,
            filterBox.minLng,
            filterBox.minLat,
            filterBox.maxLng,
            filterBox.maxLat,
            ROUTEX_MAX_POIS
        )
        log.d { "[RoutexClient] parseResults -> ${all.size} sites, ${results.size} in bounds (max $ROUTEX_MAX_POIS)" }
        return results
    }
}
