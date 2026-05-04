package fr.geoking.gaston.api.routex

import kotlinx.serialization.Serializable

/**
 * Amenities and opening info for a POI (e.g. from Routex API or Overpass).
 * All fields are optional; null means unknown.
 */
@Serializable
data class PoiAmenities(
    val manned24h: Boolean? = null,
    val mannedAutomat24h: Boolean? = null,
    val automat: Boolean? = null,
    val motorwayIndicator: Boolean? = null,
    val restaurant: Boolean? = null,
    val shop: Boolean? = null,
    val snackbar: Boolean? = null,
    val carWash: Boolean? = null,
    val showers: Boolean? = null,
    val adBluePump: Boolean? = null,
    val r4tNetwork: Boolean? = null,
    val carVignette: Boolean? = null,
    val highspeedDiesel: Boolean? = null,
    val truckIndicator: Boolean? = null,
    val truckParking: Boolean? = null,
    val truckDiesel: Boolean? = null,
    val truckLane: Boolean? = null,
    val dieselBio: Boolean? = null,
    val hvo100: Boolean? = null,
    val lng: Boolean? = null,
    val lpg: Boolean? = null,
    val cng: Boolean? = null,
    val adBlueCanister: Boolean? = null,
    val toilets: Boolean? = null,
    val drinkingWater: Boolean? = null,
    val food: Boolean? = null,
    val wifi: Boolean? = null,
    val atm: Boolean? = null,
    val playground: Boolean? = null,
    val monOpenFuel: String? = null,
    val monCloseFuel: String? = null,
    val tueOpenFuel: String? = null,
    val tueCloseFuel: String? = null,
    val wedOpenFuel: String? = null,
    val wedCloseFuel: String? = null,
    val thuOpenFuel: String? = null,
    val thuCloseFuel: String? = null,
    val friOpenFuel: String? = null,
    val friCloseFuel: String? = null,
    val satOpenFuel: String? = null,
    val satCloseFuel: String? = null,
    val sunOpenFuel: String? = null,
    val sunCloseFuel: String? = null,
    val open24h: Boolean? = null,
    val openingHoursFuel: List<String> = emptyList()
)

/**
 * Parsed gas station / site from Routex API.
 */
@Serializable
data class RoutexSite(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val siteName: String? = null,
    val postcode: String? = null,
    val addressLocal: String? = null,
    val countryLocal: String? = null,
    val townLocal: String? = null,
    val details: PoiAmenities? = null
)
