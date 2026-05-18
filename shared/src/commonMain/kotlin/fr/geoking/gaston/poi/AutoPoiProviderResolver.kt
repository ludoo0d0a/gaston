package fr.geoking.gaston.poi

/**
 * Auto-mode provider selection for multiple countries.
 *
 * Goal: pick a sensible default provider set for all detected countries (e.g. cross-border)
 * while keeping the user's manual selection as a fallback when no countries are supported.
 */
fun autoProvidersForCountries(
    countryCodes: List<String>,
    wantFuel: Boolean,
    wantElectric: Boolean,
    fallbackManual: Set<PoiProviderType>
): Set<PoiProviderType> {
    if (countryCodes.isEmpty()) return fallbackManual

    val resolved = mutableSetOf<PoiProviderType>()

    countryCodes.forEach { countryCode ->
        val iso = countryCode.trim().uppercase()

        val fuelProvider = if (wantFuel) {
            when (iso) {
                "FR" -> PoiProviderType.DataGouv
                "GB", "UK" -> PoiProviderType.UkCma
                "IT" -> PoiProviderType.ItalyMimit
                "SI" -> PoiProviderType.SloveniaGorivaSi
                "NO" -> PoiProviderType.NorwayDrivstoffAppen
                "SE" -> PoiProviderType.SwedenDrivstoffAppen
                "PT" -> PoiProviderType.PortugalDgeg
                "NL" -> PoiProviderType.NetherlandsAnwb
                "DK" -> PoiProviderType.DenmarkFuelpricesDk
                "HR" -> PoiProviderType.CroatiaMzoe
                "FI" -> PoiProviderType.FinlandPolttoaine
                "GR" -> PoiProviderType.GreeceFuelGr
                "IE" -> PoiProviderType.IrelandPickAPump
                "MD" -> PoiProviderType.MoldovaAnre
                "RO" -> PoiProviderType.RomaniaPeco
                "RS" -> PoiProviderType.SerbiaNis
                "MX" -> PoiProviderType.MexicoCre
                "AR" -> PoiProviderType.ArgentinaEnergia
                "ES" -> PoiProviderType.SpainMinetur
                "DE" -> PoiProviderType.GermanyTankerkoenig
                "AT" -> PoiProviderType.AustriaEControl
                "BE" -> PoiProviderType.BelgiumOfficial
                "LU" -> PoiProviderType.OpenVanCamp
                else -> null
            }
        } else null

        val electricProvider = if (wantElectric) {
            when (iso) {
                "FR" -> PoiProviderType.DataGouvElec
                "LU" -> PoiProviderType.Chargy
                else -> PoiProviderType.OpenChargeMap
            }
        } else null

        if (fuelProvider != null) resolved.add(fuelProvider)
        if (electricProvider != null) resolved.add(electricProvider)

        // Sensible cross-border fallback for fuel when we don't have a dedicated provider.
        if (wantFuel && fuelProvider == null) resolved.add(PoiProviderType.Fuelo)
    }

    // Always include Overpass as a secondary source for station locations.
    resolved.add(PoiProviderType.Overpass)

    // If we failed to resolve anything (shouldn't happen), fall back to manual selection.
    return resolved.ifEmpty { fallbackManual }
}
