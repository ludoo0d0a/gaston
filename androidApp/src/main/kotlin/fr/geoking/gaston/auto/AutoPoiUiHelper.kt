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
import fr.geoking.gaston.poi.FuelPrice
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

    private fun formatDistanceText(meters: Double): String =
        if (meters >= 1000.0) "%.1f km".format(meters / 1000.0)
        else "%.0f m".format(meters)

    private fun sortedFuelPrices(poi: Poi, fuelIdsFilter: Set<String>): List<FuelPrice> =
        (poi.fuelPrices ?: emptyList()).sortedWith(
            compareByDescending<FuelPrice> { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsFilter }
                .thenBy { it.price }
        )

    private fun formatFuelPriceText(fp: FuelPrice): String {
        val priceStr = if (fp.outOfStock) "—" else "€%.3f".format(fp.price)
        val updated = fp.updatedAt?.let { " (${DateTimeUtils.formatRelativeTime(it)})" } ?: ""
        return "$priceStr$updated"
    }

    /**
     * One fuel price row for map detail lists. Uses a tinted pump icon when [includePlace] is false
     * (Custom/MapLibre map); on PlaceListMap rows cannot carry an image, so the fuel name is tinted
     * via [ForegroundCarColorSpan] instead (same palette as phone [fr.geoking.gaston.ui.ColorHelper]).
     */
    private fun buildFuelPriceRow(
        carContext: CarContext,
        fp: FuelPrice,
        includePlace: Boolean,
        metadata: Metadata?,
        distanceFromLatLon: Pair<Double, Double>?,
        poi: Poi,
    ): Row {
        val fuelId = MapPoiFilter.fuelNameToId(fp.fuelName)
        val fuelColor = AutoCarIcons.fuelCarColor(fuelId)
        val rowBuilder = Row.Builder()

        if (includePlace) {
            val titleSpannable = SpannableString(fp.fuelName)
            titleSpannable.setSpan(
                ForegroundCarColorSpan.create(fuelColor),
                0,
                titleSpannable.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
            rowBuilder.setTitle(titleSpannable)
        } else {
            rowBuilder.setImage(
                carContext.carIcon(R.drawable.ic_poi_gas, fuelColor),
                Row.IMAGE_TYPE_SMALL,
            )
            rowBuilder.setTitle(fp.fuelName)
        }
        rowBuilder.addText(formatFuelPriceText(fp))

        if (includePlace) {
            metadata?.let { rowBuilder.setMetadata(it) }
            applyDistanceSpan(rowBuilder, distanceFromLatLon, poi)
        }
        return rowBuilder.build()
    }

    private fun applyDistanceSpan(
        rowBuilder: Row.Builder,
        distanceFromLatLon: Pair<Double, Double>?,
        poi: Poi,
        label: String? = null
    ) {
        if (distanceFromLatLon == null) return
        val (lat, lon) = distanceFromLatLon
        val meters = distanceMeters(lat, lon, poi.latitude, poi.longitude)
        val distance = if (meters >= 1000.0) {
            Distance.create(meters / 1000.0, Distance.UNIT_KILOMETERS)
        } else {
            Distance.create(meters, Distance.UNIT_METERS)
        }
        val interpunct = "\u00b7"
        val text = if (label != null) "  $interpunct $label" else " "
        val s = SpannableString(text)
        s.setSpan(DistanceSpan.create(distance), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        rowBuilder.addText(s)
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

        // PlaceList* templates require DistanceSpan on non-browsable rows; some hosts are strict even
        // when rows are browsable. Including a DistanceSpan makes the row universally valid.
        if (distanceFromLatLon != null) {
            applyDistanceSpan(rowBuilder, distanceFromLatLon, poi, label)
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

        val secondaryText = secondaryDetails.joinToString(" \u00b7 ")
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
        distanceFromLatLon: Pair<Double, Double>? = null,
        onHeaderClick: (() -> Unit)? = null,
        maxRows: Int = 6,
        includePlace: Boolean = false
    ): List<Row> {
        val rows = mutableListOf<Row>()
        val metadata = if (includePlace) {
            Metadata.Builder().setPlace(buildPlace(carContext, poi)).build()
        } else null

        // 1. Brand / Name Row
        val brandIcon = buildPoiIcon(carContext, poi, effectiveEnergyTypes, effectivePowerLevels)
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)

        val headerRow = Row.Builder()
            .setTitle(title)

        if (includePlace) {
            headerRow.setMetadata(metadata!!)
        } else {
            headerRow.setImage(brandIcon, Row.IMAGE_TYPE_SMALL)
        }

        brandInfo?.let {
            if (title != it.displayName) {
                headerRow.addText(it.displayName)
            }
        }

        if (distanceFromLatLon != null) {
            applyDistanceSpan(headerRow, distanceFromLatLon, poi)
        }

        if (onHeaderClick != null) {
            headerRow.setOnClickListener(onHeaderClick)
            headerRow.setBrowsable(true)
        }

        rows.add(headerRow.build())

        val fuelIdsFilter = effectiveEnergyTypes - "electric"
        val fuelPrices = sortedFuelPrices(poi, fuelIdsFilter)

        // Gas stations: show fuel prices first (map detail is row-capped; full list is in PoiDetailScreen).
        if (fuelPrices.isNotEmpty()) {
            val maxFuelRows = (maxRows - rows.size).coerceAtLeast(0)
            fuelPrices.take(maxFuelRows).forEach { fp ->
                rows.add(
                    buildFuelPriceRow(
                        carContext = carContext,
                        fp = fp,
                        includePlace = includePlace,
                        metadata = metadata,
                        distanceFromLatLon = distanceFromLatLon,
                        poi = poi,
                    )
                )
            }
            return rows.take(maxRows)
        }

        // 1b. Address Row (non-fuel POIs)
        if (poi.address.isNotBlank()) {
            val addressRow = Row.Builder()
                .setTitle(carContext.getString(R.string.poi_section_address))
                .addText(poi.address)

            if (includePlace) {
                addressRow.setMetadata(metadata!!)
                applyDistanceSpan(addressRow, distanceFromLatLon, poi)
            }
            rows.add(addressRow.build())
        }

        // 2. Prepare non-fuel sections to know how much space is left for fuel
        val irveRows = mutableListOf<Row>()
        if (poi.isElectric) {
            val electricInfo = mutableListOf<String>()
            poi.operator?.takeIf { it.isNotBlank() }?.let { electricInfo.add(it) }
            poi.powerKw?.let { electricInfo.add(carContext.getString(R.string.power_kw_format, it.toInt())) }

            if (electricInfo.isNotEmpty()) {
                val row = Row.Builder().setTitle(electricInfo.joinToString(" • "))
                if (includePlace) {
                    row.setMetadata(metadata!!)
                    applyDistanceSpan(row, distanceFromLatLon, poi)
                }
                irveRows.add(row.build())
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
                val row = Row.Builder().setTitle(connectorInfo.joinToString(" • "))
                if (includePlace) {
                    row.setMetadata(metadata!!)
                    applyDistanceSpan(row, distanceFromLatLon, poi)
                }
                irveRows.add(row.build())
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
            if (a.wifi == true) ams.add(carContext.getString(R.string.poi_amenity_wifi))
            if (a.atm == true) ams.add(carContext.getString(R.string.poi_amenity_atm))
            if (a.showers == true) ams.add(carContext.getString(R.string.amenity_showers))
            if (a.drinkingWater == true) ams.add(carContext.getString(R.string.amenity_drinking_water))

            if (ams.isNotEmpty()) {
                val row = Row.Builder()
                    .setTitle(carContext.getString(R.string.poi_section_services))
                    .addText(ams.joinToString(" • "))

                if (includePlace) {
                    row.setMetadata(metadata!!)
                    applyDistanceSpan(row, distanceFromLatLon, poi)
                }
                amenityRows.add(row.build())
            }
        }

        // 3. Add IRVE and Amenities
        rows.addAll(irveRows)
        rows.addAll(amenityRows)

        return rows.take(maxRows)
    }

    /**
     * Builds the full station detail as a plain-text body for [androidx.car.app.model.LongMessageTemplate].
     *
     * `LongMessageTemplate` is the host-approved *terminal* template for "station detail" (see
     * docs/android-auto.md): it scrolls, so it shows every fuel price without the ~6-row
     * `ListTemplate` cap, and it is one of the template types allowed as the last step of a task.
     * Body is plain `String` (no spans — strict hosts reject styled text) and capped at 5000 chars.
     */
    fun buildPoiDetailText(
        carContext: CarContext,
        poi: Poi,
        availability: StationAvailabilitySummary?,
        effectiveEnergyTypes: Set<String> = emptySet(),
        effectivePowerLevels: Set<Int> = emptySet(),
        distanceFromLatLon: Pair<Double, Double>? = null
    ): String {
        val sb = StringBuilder()

        // Headline: brand · distance · best-price/power label
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val headlineParts = mutableListOf<String>()
        brandInfo?.displayName?.takeIf { it.isNotBlank() && it != title }?.let { headlineParts.add(it) }
        distanceFromLatLon?.let { (lat, lon) ->
            headlineParts.add(formatDistanceText(distanceMeters(lat, lon, poi.latitude, poi.longitude)))
        }
        PoiMarkerHelper.getPoiLabel(poi, effectiveEnergyTypes, effectivePowerLevels)
            ?.takeIf { it.isNotBlank() }?.let { headlineParts.add(it) }
        if (headlineParts.isNotEmpty()) {
            sb.appendLine(headlineParts.joinToString("  ·  "))
            sb.appendLine()
        }

        // Address
        if (poi.address.isNotBlank()) {
            sb.appendLine(carContext.getString(R.string.poi_section_address))
            sb.appendLine(poi.address)
            sb.appendLine()
        }

        // Fuel prices (filtered fuels first, then by price; all prices listed — no row cap)
        val fuelIdsFilter = effectiveEnergyTypes - "electric"
        val fuelPrices = sortedFuelPrices(poi, fuelIdsFilter)
        if (fuelPrices.isNotEmpty()) {
            sb.appendLine(carContext.getString(R.string.poi_section_prices))
            val nameWidth = fuelPrices.maxOf { it.fuelName.length }.coerceIn(4, 14)
            fuelPrices.forEach { fp ->
                val priceStr = formatFuelPriceText(fp)
                sb.appendLine("${fp.fuelName.padEnd(nameWidth)}  $priceStr")
            }
            sb.appendLine()
        }

        // Charging details (electric)
        if (poi.isElectric) {
            val charging = mutableListOf<String>()
            poi.operator?.takeIf { it.isNotBlank() }?.let { charging.add(it) }
            poi.powerKw?.let { charging.add(carContext.getString(R.string.power_kw_format, it.toInt())) }
            poi.chargePointCount?.let { n ->
                charging.add(
                    if (n == 1) carContext.getString(R.string.poi_charge_point_one)
                    else carContext.getString(R.string.poi_charge_points, n)
                )
            }
            poi.irveDetails?.let { d ->
                if (d.connectorTypes.isNotEmpty()) {
                    charging.add(d.connectorTypes.sorted().joinToString(", ") { BrandHelper.connectorTypeLabel(it) })
                }
            }
            availability?.let { s ->
                charging.add(carContext.getString(R.string.poi_availability, s.availableCount, s.totalCount))
            }
            if (charging.isNotEmpty()) {
                sb.appendLine(carContext.getString(R.string.poi_section_charging_details))
                charging.forEach { sb.appendLine(it) }
                sb.appendLine()
            }
        }

        // Services / amenities
        poi.amenities?.let { a ->
            val ams = mutableListOf<String>()
            if (a.open24h == true) ams.add(carContext.getString(R.string.amenity_24h))
            if (a.shop == true) ams.add(carContext.getString(R.string.amenity_shop))
            if (a.restaurant == true) ams.add(carContext.getString(R.string.amenity_restaurant))
            if (a.toilets == true) ams.add(carContext.getString(R.string.amenity_toilets))
            if (a.carWash == true) ams.add(carContext.getString(R.string.amenity_car_wash))
            if (a.wifi == true) ams.add(carContext.getString(R.string.poi_amenity_wifi))
            if (a.atm == true) ams.add(carContext.getString(R.string.poi_amenity_atm))
            if (a.showers == true) ams.add(carContext.getString(R.string.amenity_showers))
            if (a.drinkingWater == true) ams.add(carContext.getString(R.string.amenity_drinking_water))
            if (ams.isNotEmpty()) {
                sb.appendLine(carContext.getString(R.string.poi_section_services))
                sb.appendLine(ams.joinToString(" • "))
                sb.appendLine()
            }
        }

        val text = sb.toString().trim()
        return text.ifBlank { carContext.getString(R.string.poi_no_extra_details) }.take(5000)
    }
}
