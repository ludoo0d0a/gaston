package fr.geoking.julius.api.ocpi

import fr.geoking.julius.shared.util.DateUtils
import io.ktor.util.date.GMTDate
import kotlin.math.roundToInt

/**
 * Utility to parse and format OCPI 2.2.1 tariffs.
 * Follows the "first match per dimension" rule.
 * https://github.com/ocpi/ocpi/blob/master/mod_tariffs.asciidoc#131-tariff-object
 */
object OcpiTariffParser {

    /**
     * Returns a human-readable summary of the tariff (e.g., "0.45€/kWh + 1.00€/session").
     * For POI display, we generally look for the components that apply "now" or by default.
     */
    fun formatTariff(tariff: OcpiTariff): String {
        val components = getActiveComponents(tariff)
        if (components.isEmpty()) return "Unknown price"

        val parts = mutableListOf<String>()

        components[OcpiPriceComponentType.FLAT]?.let {
            parts.add("${formatPrice(it.price, tariff.currency)} / session")
        }

        components[OcpiPriceComponentType.ENERGY]?.let {
            parts.add("${formatPrice(it.price, tariff.currency)} / kWh")
        }

        components[OcpiPriceComponentType.TIME]?.let {
            val hourly = it.price
            parts.add("${formatPrice(hourly, tariff.currency)} / h")
        }

        components[OcpiPriceComponentType.PARKING_TIME]?.let {
            parts.add("${formatPrice(it.price, tariff.currency)} / h (parking)")
        }

        return parts.joinToString(" + ")
    }

    /**
     * Resolves the active [OcpiPriceComponent] for each dimension.
     * Implements the "first-match" logic of OCPI 2.2.1.
     */
    fun getActiveComponents(tariff: OcpiTariff): Map<OcpiPriceComponentType, OcpiPriceComponent> {
        val result = mutableMapOf<OcpiPriceComponentType, OcpiPriceComponent>()
        val now = GMTDate()

        // For each dimension, find the first matching element
        for (type in OcpiPriceComponentType.values()) {
            val matchingElement = tariff.elements.firstOrNull { element ->
                element.price_components.any { it.type == type } && matchesRestrictions(element.restrictions, now)
            }

            matchingElement?.price_components?.find { it.type == type }?.let {
                result[type] = it
            }
        }

        return result
    }

    /**
     * Checks if the restrictions of a TariffElement match the current context.
     * For POI display, we focus on time/date/day restrictions.
     */
    fun matchesRestrictions(restrictions: OcpiTariffRestrictions?, now: GMTDate): Boolean {
        if (restrictions == null) return true

        // Day of week check
        if (restrictions.day_of_week.isNotEmpty()) {
            val dayNow = now.dayOfWeek.name
            if (restrictions.day_of_week.none { it.uppercase() == dayNow }) return false
        }

        // Time of day check
        if (restrictions.start_time != null || restrictions.end_time != null) {
            val hourNow = now.hours
            val minNow = now.minutes
            val totalMinNow = hourNow * 60 + minNow

            val startMin = restrictions.start_time?.let { parseTime(it) } ?: 0
            val endMin = restrictions.end_time?.let { parseTime(it) } ?: 1440

            if (endMin < startMin) {
                // Wraps around midnight
                if (totalMinNow < startMin && totalMinNow >= endMin) return false
            } else {
                if (totalMinNow < startMin || totalMinNow >= endMin) return false
            }
        }

        // Date check
        if (restrictions.start_date != null || restrictions.end_date != null) {
            val dateStr = formatCompactDate(now)
            restrictions.start_date?.let { if (dateStr < it) return false }
            restrictions.end_date?.let { if (dateStr >= it) return false }
        }

        // Session-based restrictions
        if (restrictions.min_kwh != null || restrictions.max_kwh != null ||
            restrictions.min_duration != null || restrictions.max_duration != null ||
            restrictions.min_power != null || restrictions.max_power != null ||
            restrictions.min_current != null || restrictions.max_current != null) {
            // These are conditional prices that apply during charging.
            // For a "base" price display, we assume a fresh 0-indexed session.
            if ((restrictions.min_kwh ?: 0.0) > 0.0) return false
            if ((restrictions.min_duration ?: 0) > 0) return false
            // Note: we could also check max_kwh/max_duration etc but usually those are for tiered pricing.
        }

        return true
    }

    private fun parseTime(time: String): Int {
        val parts = time.split(":")
        if (parts.size != 2) return 0
        return parts[0].toIntOrNull()?.let { h ->
            parts[1].toIntOrNull()?.let { m ->
                h * 60 + m
            }
        } ?: 0
    }

    private fun formatCompactDate(date: GMTDate): String {
        val year = date.year
        val month = (date.month.ordinal + 1).toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private fun formatPrice(price: Double, currency: String): String {
        val symbol = when (currency.uppercase()) {
            "EUR" -> "€"
            "USD" -> "$"
            "GBP" -> "£"
            else -> currency
        }

        // Format to 2 or 3 decimal places
        val rounded = (price * 1000).roundToInt() / 1000.0
        return if (rounded % 1.0 == 0.0) {
            "${rounded.toInt()}$symbol"
        } else {
            "$rounded$symbol"
        }
    }
}
