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
import fr.geoking.gaston.poi.Poi
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
        val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
            context = carContext,
            poi = poi,
            effectiveEnergyTypes = emptySet(), // No easy access to settings here, using default
            effectivePowerLevels = emptySet(),
            isSelected = false,
            sizePx = 72,
            availability = null,
            markerStyle = MarkerStyle.Circle
        )
        return Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithBitmap(markerBitmap)).build(),
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
        val address = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.ifBlank { "${poi.latitude}, ${poi.longitude}" }
        val source = "[Source: ${poi.source ?: "Unknown"}]"
        val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
            context = carContext,
            poi = poi,
            effectiveEnergyTypes = effectiveEnergyTypes,
            effectivePowerLevels = effectivePowerLevels,
            isSelected = false,
            sizePx = 72,
            availability = availability,
            markerStyle = MarkerStyle.Circle
        )
        val place = Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithBitmap(markerBitmap)).build(),
                        PlaceMarker.TYPE_ICON
                    )
                    .build()
            )
            .build()

        // IMAGE_TYPE_LARGE is forbidden when Metadata contains a Place (Car App Library constraint:
        // "A row must not have both a large image and a place"). Use IMAGE_TYPE_SMALL so that the
        // marker icon is still visible in the list while the Place metadata works for map pins.
        val rowBuilder = Row.Builder()
            .setTitle(title)
            .setMetadata(Metadata.Builder().setPlace(place).build())
            .setImage(CarIcon.Builder(IconCompat.createWithBitmap(markerBitmap)).build(), Row.IMAGE_TYPE_SMALL)
            .setIsBrowsable(true)
            .setOnClickListener(onClick)

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
            val interpunct = "\u00b7"
            val s = SpannableString("  $interpunct $address")
            s.setSpan(DistanceSpan.create(distance), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            rowBuilder.addText(s)
            rowBuilder.addText(source)
        } else {
            rowBuilder.addText("$address $source")
        }

        return rowBuilder.build()
    }
}
