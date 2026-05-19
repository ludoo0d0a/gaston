package fr.geoking.gaston.auto

import android.content.Intent
import android.net.Uri
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
            .setTitle(carContext.getString(R.string.navigate_to))
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

    private fun buildDetailMessage(poi: Poi): String {
        val lines = mutableListOf<String>()
        poi.source?.takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.source_label, it)) }
        poi.brand?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        if (poi.isElectric) {
            poi.operator?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
            if (poi.isOnHighway) lines.add(carContext.getString(R.string.highway))
            poi.chargePointCount?.let { n ->
                lines.add(carContext.resources.getQuantityString(R.plurals.points_count, n, n))
            }
            availabilitySummary?.let { s ->
                lines.add(carContext.resources.getQuantityString(R.plurals.available_count, s.availableCount, s.availableCount, s.totalCount))
            }
        }
        poi.addressLocal?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { lines.add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        if (lines.isEmpty() && poi.address.isNotBlank()) lines.add(poi.address)
        poi.fuelPrices?.let { prices ->
            if (prices.isNotEmpty()) {
                lines.add("")
                prices.forEach { fp ->
                    val priceStr = if (fp.outOfStock) "—" else "€%.3f".format(fp.price)
                    val updated = fp.updatedAt?.let {
                        " (${fr.geoking.gaston.shared.datetime.DateTimeUtils.formatRelativeTime(it)})"
                    } ?: ""
                    lines.add("${fp.fuelName}: $priceStr$updated")
                }
            }
        }
        rating?.let { r -> lines.add(carContext.getString(R.string.rating_format, r.toDouble())) }
        poi.irveDetails?.let { d ->
            if (d.connectorTypes.isNotEmpty()) {
                val connectorLabels = d.connectorTypes.sorted().map { connectorLabel(it) }.joinToString(", ")
                lines.add(carContext.getString(R.string.connectors_label_detail, connectorLabels))
            }
            if (d.gratuit == true) lines.add(carContext.getString(R.string.free))
            d.tarification?.takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.tariffs_label, it)) }
            d.openingHours?.takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.opening_hours_label, it)) }
            if (d.reservation == true) lines.add(carContext.getString(R.string.reservation_possible))
            listOfNotNull(
                if (d.paymentActe == true) carContext.getString(R.string.payment_on_demand) else null,
                if (d.paymentCb == true) carContext.getString(R.string.payment_card) else null,
                if (d.paymentAutre == true) carContext.getString(R.string.payment_other) else null
            ).joinToString(", ").takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.payment_label, it)) }
            d.conditionAcces?.takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.access_label, it)) }
        }
        poi.restaurantDetails?.let { d ->
            if (d.isFastFood) lines.add(carContext.getString(R.string.fast_food))
            d.brand?.takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.brand_label, it)) }
            d.cuisine?.takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.cuisine_label, it)) }
            d.openingHours?.takeIf { it.isNotBlank() }?.let { lines.add(carContext.getString(R.string.opening_hours_label, it)) }
        }
        return lines.joinToString("\n").ifBlank { carContext.getString(R.string.no_extra_details) }
    }

    private fun connectorLabel(id: String): String = when (id) {
        "type_2" -> "Type 2"
        "combo_ccs" -> "CCS"
        "chademo" -> "CHAdeMO"
        "ef" -> "E/F"
        "autre" -> carContext.getString(R.string.other_label)
        else -> id
    }
}
