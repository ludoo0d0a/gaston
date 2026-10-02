package fr.geoking.gaston.shared.location

/**
 * Emitted when the device detects a change from one valid country to another.
 *
 * @param countryCode ISO 3166-1 alpha-2 code of the destination country
 * @param countryName Display name when available, otherwise the country code
 */
data class BorderCrossingEvent(
    val countryCode: String,
    val countryName: String,
)
