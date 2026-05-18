package fr.geoking.gaston.auto

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.MarkerStyle

/**
 * Shared logic for mapping POIs to car UI components (rows, markers, icons).
 */
object AutoPoiUiHelper {

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Haversine distance (meters). Good enough for on-screen "distance" spans.
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    fun buildPlace(carContext: CarContext, poi: Poi): Place {
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val iconResId = PoiMarkerHelper.headDrawableResId(poi, brandInfo)
        return Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithResource(carContext, iconResId)).build(),
                        PlaceMarker.TYPE_ICON
                    )
                    .build()
            )
            .build()
    }

    fun buildPoiRow(
        carContext: CarContext,
        poi: Poi,
        availability: StationAvailabilitySummary?,
        effectiveEnergyTypes: Set<String> = emptySet(),
        effectivePowerLevels: Set<Int> = emptySet(),
        distanceFromLatLon: Pair<Double, Double>? = null,
        onClick: () -> Unit
    ): Row {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val iconResId = PoiMarkerHelper.headDrawableResId(poi, brandInfo)
        val carIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, iconResId)).build()

        val place = Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(carIcon, PlaceMarker.TYPE_ICON)
                    .build()
            )
            .build()

        // IMAGE_TYPE_LARGE is forbidden when Metadata contains a Place (Car App Library constraint:
        // "A row must not have both a large image and a place"). Use IMAGE_TYPE_SMALL so that the
        // marker icon is still visible in the list while the Place metadata works for map pins.
        val rowBuilder = Row.Builder()
            .setTitle(title)
            .setMetadata(Metadata.Builder().setPlace(place).build())
            .setImage(carIcon, Row.IMAGE_TYPE_SMALL)
            .setBrowsable(true)
            .setOnClickListener(onClick)

        val label = PoiMarkerHelper.getPoiLabel(poi, effectiveEnergyTypes, effectivePowerLevels)
        val interpunct = "\u00b7"

        // PlaceList* templates require DistanceSpan on non-browsable rows; some hosts are strict even
        // when rows are browsable. Including a DistanceSpan makes the row universally valid.
        if (distanceFromLatLon != null) {
            val (lat, lon) = distanceFromLatLon
            val meters = distanceMeters(lat, lon, poi.latitude, poi.longitude)
            val distance = if (meters >= 1000.0) {
                Distance.create(meters / 1000.0, Distance.UNIT_KILOMETERS)
            } else {
                Distance.create(meters, Distance.UNIT_METERS)
            }
            val text = if (label != null) "  $interpunct $label" else " "
            val s = SpannableString(text)
            s.setSpan(DistanceSpan.create(distance), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            rowBuilder.addText(s)
        } else if (label != null) {
            rowBuilder.addText(label)
        }

        // Second line: additional details for parity with phone dashboard
        val secondaryDetails = mutableListOf<String>()
        val fuelIds = effectiveEnergyTypes - "electric"
        val hasFuelFilter = fuelIds.isNotEmpty()

        // If the label is a fuel price, try to find the corresponding fuel name
        val prices = poi.fuelPrices.orEmpty()
        val matchingPrices = if (hasFuelFilter) {
            prices.filter { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
        } else {
            prices.filter { !it.outOfStock }
        }
        val bestPriceItem = matchingPrices.minByOrNull { it.price }

        val shownFuelPrice = label?.startsWith("€") == true
        if (shownFuelPrice && bestPriceItem != null) {
            secondaryDetails.add(bestPriceItem.fuelName)
        } else if (poi.isElectric) {
            poi.operator?.takeIf { it.isNotBlank() }?.let { secondaryDetails.add(it) }
        }

        if (poi.isElectric) {
            poi.chargePointCount?.let { n ->
                secondaryDetails.add(if (n == 1) "1 point" else "$n points")
            }
            availability?.let { s ->
                secondaryDetails.add("${s.availableCount}/${s.totalCount} dispo")
            }
        }

        val addressLocal = poi.addressLocal
        if (secondaryDetails.isEmpty() && !addressLocal.isNullOrBlank()) {
            secondaryDetails.add(addressLocal)
        }

        val secondaryText = secondaryDetails.joinToString(" $interpunct ")
        if (secondaryText.isNotBlank()) {
            rowBuilder.addText(secondaryText)
        }

        return rowBuilder.build()
    }
}
