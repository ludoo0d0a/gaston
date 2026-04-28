package fr.geoking.gaston.api.evpricesfr

data class EvPriceBaseline(
    val provider: EvPriceProvider,
    val country: String,
    val currency: String,
    val sourceUrl: String,
    /**
     * ISO-8601 timestamp (UTC recommended) describing when the source data was last updated.
     * For GitHub-hosted files, this should be the last commit date for the file.
     */
    val lastUpdateDate: String? = null,
    /**
     * One of:
     * - kwh_fixed_by_offer
     * - kwh_fixed_by_power_category
     * - kwh_range_dynamic
     * - kwh_fixed_by_power_threshold
     * - kwh_from_minimum
     * - community_dataset_subset
     */
    val priceModel: String,
    /** Provider-specific numeric values, normalized to EUR/kWh when applicable. */
    val values: Map<String, Double>,
    val notes: String? = null
)

enum class EvPriceProvider {
    Fastned,
    Allego,
    Electra,
    TotalEnergies,
    Ionity,
    Tesla,
}

