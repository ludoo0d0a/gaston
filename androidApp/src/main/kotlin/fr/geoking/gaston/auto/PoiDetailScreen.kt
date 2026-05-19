package fr.geoking.gaston.auto

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.ui.BrandHelper

/**
 * Android Auto screen showing full POI details and a "Go to this station" action
 * that starts navigation (e.g. to Android Auto driving app).
 */
class PoiDetailScreen(
    carContext: CarContext,
    private val poi: Poi,
    private val availabilitySummary: StationAvailabilitySummary? = null,
    private val rating: Int? = null
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
        val body = buildDetailMessage(poi)
        val navigateIntent = Intent(CarContext.ACTION_NAVIGATE).apply {
            data = IntentNavigationHelper.getNavigationUri(poi)
        }
        val navigateAction = Action.Builder()
            .setTitle(carContext.getString(R.string.screen_navigate_to))
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_poi_gas)
                ).build()
            )
            .setOnClickListener {
                carContext.startCarApp(navigateIntent)
            }
            .build()
        return MessageTemplate.Builder(body)
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(navigateAction)
                    .build()
            )
            .build()
    }

    private fun buildDetailMessage(poi: Poi): CharSequence {
        val sb = StringBuilder()
        val spans = mutableListOf<Pair<IntRange, StyleSpan>>()

        fun appendHeader(title: String) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            val start = sb.length
            sb.append(title.uppercase())
            spans.add(start until sb.length to StyleSpan(Typeface.BOLD))
            sb.append("\n")
        }

        fun appendLine(text: String) {
            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
            sb.append(text)
        }

        // 1. Brand & Basic Info
        poi.brand?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        if (poi.isElectric) {
            poi.operator?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            if (poi.isOnHighway) appendLine(carContext.getString(R.string.highway))
            poi.chargePointCount?.let { n ->
                appendLine(
                    if (n == 1) {
                        carContext.getString(R.string.poi_charge_point_one)
                    } else {
                        carContext.getString(R.string.poi_charge_points, n)
                    }
                )
            }
            availabilitySummary?.let { s ->
                appendLine(carContext.getString(R.string.poi_availability, s.availableCount, s.totalCount))
            }
        }

        // 2. Address
        appendHeader(carContext.getString(R.string.poi_section_address))
        val addressLines = mutableListOf<String>()
        poi.addressLocal?.takeIf { it.isNotBlank() }?.let { addressLines.add(it) }
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { addressLines.add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { addressLines.add(it) }
        if (addressLines.isEmpty() && poi.address.isNotBlank()) addressLines.add(poi.address)
        addressLines.forEach { appendLine(it) }

        // 3. Price Rating
        if (!poi.isElectric && poi.priceRating != null) {
            appendHeader(carContext.getString(R.string.poi_section_price_rating))
            val r = poi.priceRating!!
            val label = when {
                r >= 8.5 -> carContext.getString(R.string.poi_rating_very_cheap)
                r >= 7.0 -> carContext.getString(R.string.poi_rating_cheap)
                r >= 4.0 -> carContext.getString(R.string.poi_rating_average)
                r >= 2.0 -> carContext.getString(R.string.poi_rating_expensive)
                else -> carContext.getString(R.string.poi_rating_very_expensive)
            }
            appendLine(carContext.getString(R.string.poi_rating_format_label, r, label))
        }

        // 4. Prices
        poi.fuelPrices?.takeIf { it.isNotEmpty() }?.let { prices ->
            appendHeader(carContext.getString(R.string.poi_section_prices))
            prices.forEach { fp ->
                val priceStr = if (fp.outOfStock) "—" else "€%.3f".format(fp.price)
                val updated = fp.updatedAt?.let {
                    " (${fr.geoking.gaston.shared.datetime.DateTimeUtils.formatRelativeTime(it)})"
                } ?: ""
                appendLine("${fp.fuelName}: $priceStr$updated")
            }
        }

        // 5. IRVE Details
        if (poi.isElectric) {
            poi.irveDetails?.let { d ->
                appendHeader(carContext.getString(R.string.poi_section_charging_details))
                poi.powerKw?.let { appendLine(carContext.getString(R.string.power_kw_format, it.toInt())) }
                if (d.connectorTypes.isNotEmpty()) {
                    val labels = d.connectorTypes.sorted().map { BrandHelper.connectorTypeLabel(it) }.joinToString(", ")
                    appendLine(carContext.getString(R.string.poi_connectors_prefix, labels))
                }
                if (d.gratuit == true) appendLine(carContext.getString(R.string.poi_free))
                d.tarification?.takeIf { it.isNotBlank() }?.let {
                    appendLine(carContext.getString(R.string.poi_pricing_prefix, it))
                }
                d.openingHours?.takeIf { it.isNotBlank() }?.let {
                    appendLine(carContext.getString(R.string.poi_hours_prefix, it))
                }
                if (d.reservation == true) appendLine(carContext.getString(R.string.poi_reservation_possible))
                listOfNotNull(
                    if (d.paymentActe == true) carContext.getString(R.string.poi_payment_on_site) else null,
                    if (d.paymentCb == true) carContext.getString(R.string.poi_payment_card) else null,
                    if (d.paymentAutre == true) carContext.getString(R.string.poi_payment_other) else null
                ).joinToString(", ").takeIf { it.isNotBlank() }?.let {
                    appendLine(carContext.getString(R.string.poi_payment_prefix, it))
                }
                d.conditionAcces?.takeIf { it.isNotBlank() }?.let {
                    appendLine(carContext.getString(R.string.poi_access_prefix, it))
                }
            }
        }

        // 6. Amenities
        poi.amenities?.let { a ->
            val ams = mutableListOf<String>()
            if (a.open24h == true) ams.add(carContext.getString(R.string.amenity_24h))
            if (a.shop == true) ams.add(carContext.getString(R.string.amenity_shop))
            if (a.restaurant == true) ams.add(carContext.getString(R.string.amenity_restaurant))
            if (a.toilets == true) ams.add(carContext.getString(R.string.amenity_toilets))
            if (a.carWash == true) ams.add(carContext.getString(R.string.amenity_car_wash))
            if (a.showers == true) ams.add(carContext.getString(R.string.amenity_showers))
            if (a.atm == true) ams.add(carContext.getString(R.string.poi_amenity_atm))
            if (a.wifi == true) ams.add(carContext.getString(R.string.poi_amenity_wifi))

            if (ams.isNotEmpty()) {
                appendHeader(carContext.getString(R.string.poi_section_services))
                appendLine(ams.joinToString(" · "))
            }
        }

        // 7. Restaurant
        poi.restaurantDetails?.let { d ->
            appendHeader(carContext.getString(R.string.poi_section_restaurant))
            if (d.isFastFood) appendLine(carContext.getString(R.string.poi_fast_food))
            d.brand?.takeIf { it.isNotBlank() }?.let {
                appendLine(carContext.getString(R.string.poi_brand_prefix, it))
            }
            d.cuisine?.takeIf { it.isNotBlank() }?.let {
                appendLine(carContext.getString(R.string.poi_cuisine_prefix, it))
            }
            d.openingHours?.takeIf { it.isNotBlank() }?.let {
                appendLine(carContext.getString(R.string.poi_hours_prefix, it))
            }
        }

        // 8. Sources
        val sourceUpdates = poi.sourceUpdates
        if (sourceUpdates != null && sourceUpdates.isNotEmpty()) {
            appendHeader(carContext.getString(R.string.screen_sources))
            sourceUpdates.forEach { (src, time) ->
                appendLine("• $src: ${fr.geoking.gaston.shared.datetime.DateTimeUtils.formatRelativeTime(time)}")
            }
        } else {
            poi.source?.takeIf { it.isNotBlank() }?.let {
                appendHeader(carContext.getString(R.string.screen_sources))
                appendLine(it)
            }
        }

        if (sb.isBlank()) return carContext.getString(R.string.poi_no_extra_details)

        val ss = SpannableString(sb)
        spans.forEach { (range, span) ->
            ss.setSpan(span, range.first, range.last, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return ss
    }
}
