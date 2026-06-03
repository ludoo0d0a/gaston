package fr.geoking.gaston.auto

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.R
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.shared.datetime.DateTimeUtils

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

    private fun buildPoiIcon(
        carContext: CarContext,
        poi: Poi,
        effectiveEnergyTypes: Set<String> = emptySet(),
        effectivePowerLevels: Set<Int> = emptySet(),
        sizePx: Int = 128
    ): CarIcon {
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val category = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
        val categoryColor = PoiMarkerHelper.getPoiColor(poi, category, effectiveEnergyTypes, effectivePowerLevels)

        val bitmap = PoiMarkerHelper.getPoiHeadBitmap(
            context = carContext,
            poi = poi,
            brandInfo = brandInfo,
            sizePx = sizePx,
            categoryColor = categoryColor
        )

        return if (bitmap != null) {
            CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
        } else {
            val iconResId = PoiMarkerHelper.headDrawableResId(poi, brandInfo)
            CarIcon.Builder(IconCompat.createWithResource(carContext, iconResId)).build()
        }
    }

    fun buildPlace(carContext: CarContext, poi: Poi): Place {
        val carIcon = buildPoiIcon(carContext, poi)
        return Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(carIcon, PlaceMarker.TYPE_ICON)
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
        includePlace: Boolean = false,
        onClick: () -> Unit
    ): Row {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val carIcon = buildPoiIcon(carContext, poi, effectiveEnergyTypes, effectivePowerLevels)

        val rowBuilder = Row.Builder()
            .setTitle(title)
            .setBrowsable(true)
            .setOnClickListener(onClick)

        if (includePlace) {
            // If the template renders a map (like PlaceListMapTemplate), we MUST provide a Place
            // with a marker in Metadata for the host to display the pin.
            // Constraint: Rows can't have both a marker (in Metadata) and an image.
            val place = Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
                .setMarker(
                    PlaceMarker.Builder()
                        .setIcon(carIcon, PlaceMarker.TYPE_ICON)
                        .build()
                )
                .build()
            rowBuilder.setMetadata(Metadata.Builder().setPlace(place).build())
        } else {
            // For screens where we render the map ourselves (CustomMapPoiScreen, MapLibrePoiScreen)
            // or simple list/search screens, we use a Row image for the brand icon.
            rowBuilder.setImage(carIcon, Row.IMAGE_TYPE_SMALL)
        }

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
            // Fuel name removed as requested
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

        val secondaryText = secondaryDetails.joinToString(" $interpunct ")
        if (secondaryText.isNotBlank()) {
            rowBuilder.addText(secondaryText)
        }

        return rowBuilder.build()
    }

    fun buildPoiDetailRows(
        carContext: CarContext,
        poi: Poi,
        availability: StationAvailabilitySummary?,
        effectiveEnergyTypes: Set<String> = emptySet(),
        effectivePowerLevels: Set<Int> = emptySet(),
        onHeaderClick: (() -> Unit)? = null,
        maxRows: Int = 6
    ): List<Row> {
        val rows = mutableListOf<Row>()

        // 1. Brand / Name Row
        val brandIcon = buildPoiIcon(carContext, poi, effectiveEnergyTypes, effectivePowerLevels)
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)

        val headerRow = Row.Builder()
            .setTitle(title)
            .setImage(brandIcon, Row.IMAGE_TYPE_SMALL)

        brandInfo?.let {
            if (title != it.displayName) {
                headerRow.addText(it.displayName)
            }
        }

        if (onHeaderClick != null) {
            headerRow.setOnClickListener(onHeaderClick)
            headerRow.setBrowsable(true)
        }

        rows.add(headerRow.build())

        // 2. Prepare non-fuel sections to know how much space is left for fuel
        val irveRows = mutableListOf<Row>()
        if (poi.isElectric) {
            val electricInfo = mutableListOf<String>()
            poi.operator?.takeIf { it.isNotBlank() }?.let { electricInfo.add(it) }
            poi.powerKw?.let { electricInfo.add(carContext.getString(R.string.power_kw_format, it.toInt())) }

            if (electricInfo.isNotEmpty()) {
                irveRows.add(Row.Builder().setTitle(electricInfo.joinToString(" • ")).build())
            }

            val connectorInfo = mutableListOf<String>()
            poi.irveDetails?.let { d ->
                if (d.connectorTypes.isNotEmpty()) {
                    connectorInfo.add(d.connectorTypes.sorted().joinToString(", ") { BrandHelper.connectorTypeLabel(it) })
                }
            }
            availability?.let { s ->
                connectorInfo.add(carContext.getString(R.string.poi_availability, s.availableCount, s.totalCount))
            }

            if (connectorInfo.isNotEmpty()) {
                irveRows.add(Row.Builder().setTitle(connectorInfo.joinToString(" • ")).build())
            }
        }

        val amenityRows = mutableListOf<Row>()
        poi.amenities?.let { a ->
            val ams = mutableListOf<String>()
            if (a.open24h == true) ams.add(carContext.getString(R.string.amenity_24h))
            if (a.shop == true) ams.add(carContext.getString(R.string.amenity_shop))
            if (a.restaurant == true) ams.add(carContext.getString(R.string.amenity_restaurant))
            if (a.toilets == true) ams.add(carContext.getString(R.string.amenity_toilets))
            if (a.carWash == true) ams.add(carContext.getString(R.string.amenity_car_wash))

            if (ams.isNotEmpty()) {
                amenityRows.add(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.poi_section_services))
                        .addText(ams.joinToString(" • "))
                        .build()
                )
            }
        }

        // 3. Fuels (limited by remaining space)
        val fuelIdsFilter = effectiveEnergyTypes - "electric"
        val fuelPrices = (poi.fuelPrices ?: emptyList())
            .sortedWith(compareByDescending<fr.geoking.gaston.poi.FuelPrice> { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsFilter }
                .thenBy { it.price })

        val fixedRowCount = 1 + irveRows.size + amenityRows.size
        val maxFuelRows = (maxRows - fixedRowCount).coerceAtLeast(0)

        fuelPrices.take(maxFuelRows).forEach { fp ->
            val fuelId = MapPoiFilter.fuelNameToId(fp.fuelName)
            val fuelColor = AutoCarIcons.fuelCarColor(fuelId)

            val priceStr = if (fp.outOfStock) "—" else "€%.3f".format(fp.price)
            val updated = fp.updatedAt?.let {
                " (${DateTimeUtils.formatRelativeTime(it)})"
            } ?: ""

            val titleSpannable = SpannableString(fp.fuelName)
            titleSpannable.setSpan(ForegroundCarColorSpan.create(fuelColor), 0, titleSpannable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

            val textSpannable = SpannableString("$priceStr$updated")
            // Note: Colors in secondary text are sometimes ignored by hosts but valid in library.
            textSpannable.setSpan(ForegroundCarColorSpan.create(fuelColor), 0, textSpannable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

            rows.add(
                Row.Builder()
                    .setTitle(titleSpannable)
                    .addText(textSpannable)
                    .build()
            )
        }

        // 4. Add IRVE and Amenities
        rows.addAll(irveRows)
        rows.addAll(amenityRows)

        return rows.take(maxRows)
    }
}
