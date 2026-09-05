package fr.geoking.gaston.api.evpricesfr

import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.isChargingStation

/**
 * Attaches free France EV tariff baselines to IRVE POIs that lack a useful [IrveDetails.tarification].
 *
 * Baselines are operator-level published rates (not per-station). DataGouv free-text / [IrveDetails.gratuit]
 * always win when present.
 */
object EvPricesFrEnricher {

    private val structuredPriceHint = Regex(
        """\d+[.,]\d+\s*€|\d+[.,]\d+\s*/\s*kWh|€\s*\d+[.,]\d+""",
        RegexOption.IGNORE_CASE
    )

    private val genericTariffLabels = setOf(
        "payant",
        "paying",
        "paid",
        "variable",
        "inconnu",
        "unknown",
        "n/a",
        "na",
        "-",
    )

    /**
     * Enrich charging POIs in mainland France when [baselines] can supply an operator estimate.
     */
    fun enrich(pois: List<Poi>, baselines: List<EvPriceBaseline>): List<Poi> {
        if (pois.isEmpty() || baselines.isEmpty()) return pois
        val byProvider = baselines.associateBy { it.provider }
        return pois.map { poi -> enrichOne(poi, byProvider) }
    }

    internal fun enrichOne(poi: Poi, byProvider: Map<EvPriceProvider, EvPriceBaseline>): Poi {
        if (!poi.isChargingStation) return poi
        if (ParkingRegion.containing(poi.latitude, poi.longitude) != ParkingRegion.France) return poi

        val details = poi.irveDetails ?: IrveDetails()
        if (details.gratuit == true) {
            val label = details.tarification?.takeIf { it.isNotBlank() } ?: "Gratuit"
            return if (details.tarification == label) poi
            else poi.copy(irveDetails = details.copy(tarification = label))
        }
        if (hasUsefulTarification(details.tarification)) return poi

        val provider = matchProvider(poi.operator, poi.brand, poi.name) ?: return poi
        val baseline = byProvider[provider] ?: return poi
        val text = formatBaseline(baseline, poi.powerKw) ?: return poi

        return poi.copy(
            irveDetails = details.copy(tarification = text),
            source = when (val s = poi.source) {
                null -> "EV tarifs FR ($provider)"
                else -> "$s + EV tarifs FR ($provider)"
            }
        )
    }

    fun hasUsefulTarification(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return false
        if (t.equals("Gratuit", ignoreCase = true) || t.equals("Free", ignoreCase = true)) return true
        if (genericTariffLabels.contains(t.lowercase())) return false
        return structuredPriceHint.containsMatchIn(t)
    }

    fun matchProvider(operator: String?, brand: String?, name: String?): EvPriceProvider? {
        val haystack = listOfNotNull(operator, brand, name)
            .joinToString(" ")
            .lowercase()
            .replace('\u00A0', ' ')
        if (haystack.isBlank()) return null
        return when {
            "ionity" in haystack -> EvPriceProvider.Ionity
            "fastned" in haystack -> EvPriceProvider.Fastned
            "allego" in haystack -> EvPriceProvider.Allego
            "electra" in haystack || "go-electra" in haystack || "go electra" in haystack ->
                EvPriceProvider.Electra
            "totalenergies" in haystack || "total energies" in haystack ||
                "charge+" in haystack || "charge plus" in haystack ||
                (haystack.contains("total") && (haystack.contains("charge") || haystack.contains("born"))) ->
                EvPriceProvider.TotalEnergies
            "tesla" in haystack || "supercharger" in haystack -> EvPriceProvider.Tesla
            else -> null
        }
    }

    fun formatBaseline(baseline: EvPriceBaseline, powerKw: Double?): String? {
        val v = baseline.values
        val label = when (baseline.provider) {
            EvPriceProvider.Fastned -> "Fastned"
            EvPriceProvider.Allego -> "Allego"
            EvPriceProvider.Electra -> "Electra"
            EvPriceProvider.TotalEnergies -> "TotalEnergies"
            EvPriceProvider.Ionity -> "IONITY"
            EvPriceProvider.Tesla -> "Tesla"
        }
        return when (baseline.priceModel) {
            "kwh_fixed_by_offer" -> {
                val price = v["app_eur_per_kwh"] ?: v["standard_eur_per_kwh"] ?: return null
                "≈ ${formatEurPerKwh(price)} ($label, tarif publié)"
            }
            "kwh_fixed_by_power_category" -> {
                val price = pickByPower(
                    powerKw = powerKw,
                    regular = v["regular_eur_per_kwh"],
                    fast = v["fast_eur_per_kwh"],
                    ultra = v["ultra_fast_eur_per_kwh"],
                ) ?: return null
                "≈ ${formatEurPerKwh(price)} ($label, tarif publié)"
            }
            "kwh_range_dynamic" -> {
                val lo = v["dynamic_min_eur_per_kwh"] ?: return null
                val hi = v["dynamic_max_eur_per_kwh"] ?: return null
                "≈ ${formatEur(lo)}–${formatEur(hi)} €/kWh ($label, dynamique)"
            }
            "kwh_fixed_by_power_threshold" -> {
                val price = when {
                    powerKw != null && powerKw > 50.0 -> v["gt_50kw_eur_per_kwh"]
                    else -> v["lte_50kw_eur_per_kwh"] ?: v["gt_50kw_eur_per_kwh"]
                } ?: return null
                "≈ ${formatEurPerKwh(price)} ($label, tarif publié)"
            }
            "kwh_from_minimum" -> {
                val from = v["from_eur_per_kwh"] ?: return null
                "à partir de ${formatEurPerKwh(from)} ($label)"
            }
            else -> null
        }
    }

    private fun pickByPower(
        powerKw: Double?,
        regular: Double?,
        fast: Double?,
        ultra: Double?,
    ): Double? {
        if (powerKw == null) return ultra ?: fast ?: regular
        return when {
            powerKw >= 150.0 -> ultra ?: fast ?: regular
            powerKw >= 50.0 -> fast ?: ultra ?: regular
            else -> regular ?: fast ?: ultra
        }
    }

    private fun formatEurPerKwh(value: Double): String = "${formatEur(value)} €/kWh"

    private fun formatEur(value: Double): String {
        val cents = kotlin.math.round(value * 100.0).toInt()
        val whole = cents / 100
        val frac = kotlin.math.abs(cents % 100)
        return "$whole,${frac.toString().padStart(2, '0')}"
    }
}
