package fr.geoking.gaston.ui

import fr.geoking.gaston.R
import fr.geoking.gaston.poi.BrandRegistry
import java.util.Locale

/**
 * Maps fuel station brand strings to display name and icon for map markers and detail.
 * Uses brand-specific drawable when available, otherwise default gas icon.
 */
object BrandHelper {

    /** Lookup key -> brand icon drawable. Unlisted brands use ic_poi_gas. */
    private val brandIcons = mapOf(
        "total" to R.drawable.ic_brand_total,
        "totalenergies" to R.drawable.ic_brand_total,
        "bp" to R.drawable.ic_brand_bp,
        "shell" to R.drawable.ic_brand_shell,
        "esso" to R.drawable.ic_brand_esso,
        "esso express" to R.drawable.ic_brand_esso,
        "eni" to R.drawable.ic_brand_eni,
        "repsol" to R.drawable.ic_brand_repsol,
        "omv" to R.drawable.ic_brand_omv,
        "avia" to R.drawable.ic_brand_avia,
        "q8" to R.drawable.ic_brand_q8,
        "agip" to R.drawable.ic_brand_agip,
        "eurogarages" to R.drawable.ic_brand_eurogarages,
        "jet" to R.drawable.ic_brand_jet,
        "elf" to R.drawable.ic_brand_elf,
        "migrol" to R.drawable.ic_brand_migrol,
        "coop" to R.drawable.ic_brand_coop,
        "migros" to R.drawable.ic_brand_migros,
        "rel" to R.drawable.ic_brand_rel,
        "rel.metz" to R.drawable.ic_brand_rel,
        "circle k" to R.drawable.ic_brand_circlek,
        "aral" to R.drawable.ic_brand_aral,
        "carrefour" to R.drawable.ic_brand_carrefour,
        "leclerc" to R.drawable.ic_brand_leclerc,
        "e.leclerc" to R.drawable.ic_brand_leclerc,
        "auchan" to R.drawable.ic_brand_auchan,
        "intermarche" to R.drawable.ic_brand_intermarche,
        "casino" to R.drawable.ic_brand_casino,
        "tesla" to R.drawable.ic_brand_tesla,
        "ionity" to R.drawable.ic_brand_ionity,
        "fastned" to R.drawable.ic_brand_fastned,
        "allego" to R.drawable.ic_brand_allego,
        "lidl" to R.drawable.ic_brand_lidl,
        "chargy" to R.drawable.ic_brand_chargy,
        "superchargy" to R.drawable.ic_brand_chargy,
        "atlante" to R.drawable.ic_brand_atlante,
        "zunder" to R.drawable.ic_brand_zunder,
        "freshmile" to R.drawable.ic_brand_freshmile,
        "superu" to R.drawable.ic_brand_superu,
        "systeme u" to R.drawable.ic_brand_superu,
        "cooperative u" to R.drawable.ic_brand_superu,
        "match" to R.drawable.ic_brand_match,
        "supermarche match" to R.drawable.ic_brand_match,
        "powerdot" to R.drawable.ic_brand_powerdot,
        "driveco" to R.drawable.ic_brand_driveco,
        "spar" to R.drawable.ic_brand_spar,
        "gulf" to R.drawable.ic_brand_gulf,
        "monoprix" to R.drawable.ic_brand_monoprix,
        "dyneff" to R.drawable.ic_brand_dyneff,
        "delmonicos" to R.drawable.ic_brand_delmonicos,
        "easycharge" to R.drawable.ic_brand_easycharge,
        "easy charge" to R.drawable.ic_brand_easycharge,
        "izivia" to R.drawable.ic_brand_izivia,
        "electra" to R.drawable.ic_brand_electra,
        "engie" to R.drawable.ic_brand_engie,
        "engie vianeo" to R.drawable.ic_brand_engie,
        "iecharge" to R.drawable.ic_brand_iecharge,
        "nw iecharge" to R.drawable.ic_brand_iecharge,
        "eborn" to R.drawable.ic_brand_eborn,
        "e-born" to R.drawable.ic_brand_eborn,
        "reveo" to R.drawable.ic_brand_reveo,
        "bump" to R.drawable.ic_brand_bump,
        "qovoltis" to R.drawable.ic_brand_qovoltis,
        "metropolis" to R.drawable.ic_brand_metropolis,
        "chargepoint" to R.drawable.ic_brand_chargepoint,
        "zeplug" to R.drawable.ic_brand_zeplug,
        "mobilize" to R.drawable.ic_brand_mobilize,
        "stationse" to R.drawable.ic_brand_stationse,
        "stations-e" to R.drawable.ic_brand_stationse,
        "waat" to R.drawable.ic_brand_waat,
        "enbw" to R.drawable.ic_brand_enbw,
        "shell recharge" to R.drawable.ic_brand_shellrecharge,
        "evbox" to R.drawable.ic_brand_evbox,
        "virta" to R.drawable.ic_brand_virta,
        "monta" to R.drawable.ic_brand_monta,
    )

    /** Lookup key -> rounded brand icon drawable. Unlisted brands use ic_poi_gas_rounded. */
    private val roundedBrandIcons = mapOf(
        "total" to R.drawable.ic_brand_total_rounded,
        "totalenergies" to R.drawable.ic_brand_total_rounded,
        "bp" to R.drawable.ic_brand_bp_rounded,
        "shell" to R.drawable.ic_brand_shell_rounded,
        "esso" to R.drawable.ic_brand_esso_rounded,
        "esso express" to R.drawable.ic_brand_esso_rounded,
        "eni" to R.drawable.ic_brand_eni_rounded,
        "repsol" to R.drawable.ic_brand_repsol_rounded,
        "omv" to R.drawable.ic_brand_omv_rounded,
        "avia" to R.drawable.ic_brand_avia_rounded,
        "q8" to R.drawable.ic_brand_q8_rounded,
        "agip" to R.drawable.ic_brand_agip_rounded,
        "eurogarages" to R.drawable.ic_brand_eurogarages_rounded,
        "jet" to R.drawable.ic_brand_jet_rounded,
        "elf" to R.drawable.ic_brand_elf_rounded,
        "migrol" to R.drawable.ic_brand_migrol_rounded,
        "coop" to R.drawable.ic_brand_coop_rounded,
        "migros" to R.drawable.ic_brand_migros_rounded,
        "rel" to R.drawable.ic_brand_rel_rounded,
        "rel.metz" to R.drawable.ic_brand_rel_rounded,
        "circle k" to R.drawable.ic_brand_circlek_rounded,
        "aral" to R.drawable.ic_brand_aral_rounded,
        "carrefour" to R.drawable.ic_brand_carrefour_rounded,
        "leclerc" to R.drawable.ic_brand_leclerc_rounded,
        "e.leclerc" to R.drawable.ic_brand_leclerc_rounded,
        "auchan" to R.drawable.ic_brand_auchan_rounded,
        "intermarche" to R.drawable.ic_brand_intermarche_rounded,
        "casino" to R.drawable.ic_brand_casino_rounded,
        "tesla" to R.drawable.ic_brand_tesla_rounded,
        "ionity" to R.drawable.ic_brand_ionity_rounded,
        "fastned" to R.drawable.ic_brand_fastned_rounded,
        "allego" to R.drawable.ic_brand_allego_rounded,
        "lidl" to R.drawable.ic_brand_lidl_rounded,
        "chargy" to R.drawable.ic_brand_chargy_rounded,
        "superchargy" to R.drawable.ic_brand_chargy_rounded,
        "atlante" to R.drawable.ic_brand_atlante_rounded,
        "zunder" to R.drawable.ic_brand_zunder_rounded,
        "freshmile" to R.drawable.ic_brand_freshmile_rounded,
        "superu" to R.drawable.ic_brand_superu_rounded,
        "systeme u" to R.drawable.ic_brand_superu_rounded,
        "cooperative u" to R.drawable.ic_brand_superu_rounded,
        "match" to R.drawable.ic_brand_match_rounded,
        "supermarche match" to R.drawable.ic_brand_match_rounded,
        "powerdot" to R.drawable.ic_brand_powerdot_rounded,
        "driveco" to R.drawable.ic_brand_driveco_rounded,
        "spar" to R.drawable.ic_brand_spar_rounded,
        "gulf" to R.drawable.ic_brand_gulf_rounded,
        "monoprix" to R.drawable.ic_brand_monoprix_rounded,
        "dyneff" to R.drawable.ic_brand_dyneff_rounded,
        "delmonicos" to R.drawable.ic_brand_delmonicos_rounded,
        "easycharge" to R.drawable.ic_brand_easycharge_rounded,
        "easy charge" to R.drawable.ic_brand_easycharge_rounded,
        "izivia" to R.drawable.ic_brand_izivia_rounded,
        "electra" to R.drawable.ic_brand_electra_rounded,
        "engie" to R.drawable.ic_brand_engie_rounded,
        "engie vianeo" to R.drawable.ic_brand_engie_rounded,
        "iecharge" to R.drawable.ic_brand_iecharge_rounded,
        "nw iecharge" to R.drawable.ic_brand_iecharge_rounded,
        "eborn" to R.drawable.ic_brand_eborn_rounded,
        "e-born" to R.drawable.ic_brand_eborn_rounded,
        "reveo" to R.drawable.ic_brand_reveo_rounded,
        "bump" to R.drawable.ic_brand_bump_rounded,
        "qovoltis" to R.drawable.ic_brand_qovoltis_rounded,
        "metropolis" to R.drawable.ic_brand_metropolis_rounded,
        "chargepoint" to R.drawable.ic_brand_chargepoint_rounded,
        "zeplug" to R.drawable.ic_brand_zeplug_rounded,
        "mobilize" to R.drawable.ic_brand_mobilize_rounded,
        "stationse" to R.drawable.ic_brand_stationse_rounded,
        "stations-e" to R.drawable.ic_brand_stationse_rounded,
        "waat" to R.drawable.ic_brand_waat_rounded,
        "enbw" to R.drawable.ic_brand_enbw_rounded,
        "shell recharge" to R.drawable.ic_brand_shellrecharge_rounded,
        "evbox" to R.drawable.ic_brand_evbox_rounded,
        "virta" to R.drawable.ic_brand_virta_rounded,
        "monta" to R.drawable.ic_brand_monta_rounded,
    )

    data class BrandInfo(
        val displayName: String,
        val iconResId: Int,
        val roundedIconResId: Int
    )

    fun getBrandInfo(brandId: String?): BrandInfo? {
        if (brandId.isNullOrBlank()) return null

        // 1. Try resolving through common registry first to catch keywords and aliases
        val detectedBrand = BrandRegistry.findBrand(null, brandId)
        if (detectedBrand != null) {
            val lookupKey = BrandRegistry.BRAND_NAMES.entries.find { it.value == detectedBrand }?.key
                ?: BrandRegistry.normalizeLookupKey(detectedBrand)

            if (brandIcons.containsKey(lookupKey)) {
                return BrandInfo(
                    displayName = detectedBrand,
                    iconResId = brandIcons[lookupKey] ?: R.drawable.ic_poi_gas,
                    roundedIconResId = roundedBrandIcons[lookupKey] ?: R.drawable.ic_poi_gas_rounded
                )
            }
        }

        // 2. Fallback to existing fuzzy logic if findBrand didn't map to a known icon key
        val normalized = BrandRegistry.normalizeLookupKey(brandId)
        val fuzzyEntry = BrandRegistry.BRAND_NAMES.entries.find { normalized.contains(it.key) }
        if (fuzzyEntry != null) {
            val key = fuzzyEntry.key
            return BrandInfo(
                displayName = fuzzyEntry.value,
                iconResId = brandIcons[key] ?: R.drawable.ic_poi_gas,
                roundedIconResId = roundedBrandIcons[key] ?: R.drawable.ic_poi_gas_rounded
            )
        }

        // 3. No match: return null to let UI handle fallback icon (plug vs pump)
        return null
    }

    /** Returns list of brands for fuel (gas). */
    fun getGasBrands(): List<Pair<String, String>> {
        return BrandRegistry.BRAND_NAMES.filterKeys { it in BrandRegistry.GAS_BRANDS }
            .entries.map { it.key to it.value }
            .distinctBy { it.second }
            .sortedBy { it.second }
    }

    /** Returns list of brands for electric charging. */
    fun getElectricBrands(): List<Pair<String, String>> {
        return BrandRegistry.BRAND_NAMES.filterKeys { it in BrandRegistry.ELECTRIC_BRANDS }
            .entries.map { it.key to it.value }
            .distinctBy { it.second }
            .sortedBy { it.second }
    }

    /**
     * Display label + [brandIcons] drawable for each [getElectricBrands] entry (fallback: generic plug).
     */
    fun getElectricBrandIconEntries(): List<Pair<String, Int>> {
        return getElectricBrands().map { (key, label) ->
            label to (brandIcons[key] ?: R.drawable.ic_poi_electric)
        }
    }

    /**
     * Display label + [brandIcons] drawable for each [getGasBrands] entry (fallback: generic pump).
     */
    fun getGasBrandIconEntries(): List<Pair<String, Int>> {
        return getGasBrands().map { (key, label) ->
            label to (brandIcons[key] ?: R.drawable.ic_poi_gas)
        }
    }

    /**
     * One row per distinct rounded marker-head drawable (circle + logo), for design previews.
     * [Pair.first] is display name; [Pair.second] is `R.drawable.ic_brand_*_rounded` (or shared fallback).
     */
    fun distinctRoundedBrandHeads(): List<Pair<String, Int>> {
        return brandIcons.entries
            .mapNotNull { (key, _) ->
                val label = BrandRegistry.BRAND_NAMES[key] ?: return@mapNotNull null
                val rounded = roundedBrandIcons[key] ?: return@mapNotNull null
                label to rounded
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase(Locale.getDefault()) }
    }

    /** Returns a human-readable label for IRVE connector type IDs. */
    fun connectorTypeLabel(id: String): String = when (id) {
        "type_2" -> "Type 2"
        "combo_ccs" -> "CCS"
        "chademo" -> "CHAdeMO"
        "ef" -> "E/F"
        "autre" -> "Autre"
        else -> id
    }
}
