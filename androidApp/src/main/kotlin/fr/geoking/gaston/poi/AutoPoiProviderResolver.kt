package fr.geoking.gaston.poi

/**
 * Auto-mode provider selection.
 *
 * Goal: pick a sensible default provider set for the current country while keeping
 * the user's manual selection as a fallback when the country is unknown or unsupported.
 */
fun autoProvidersForCountry(
    countryCode: String,
    vehicleEnergy: String,
    fallbackManual: Set<PoiProviderType>
): Set<PoiProviderType> {
    val iso = countryCode.trim().uppercase()

    val wantFuel = vehicleEnergy != "electric"
    val wantElectric = vehicleEnergy != "gas"

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

    val resolved = buildSet {
        if (fuelProvider != null) add(fuelProvider)
        if (electricProvider != null) add(electricProvider)

        // Sensible cross-border fallback for fuel when we don't have a dedicated provider.
        if (wantFuel && fuelProvider == null) add(PoiProviderType.Fuelo)
    }

    // If we failed to resolve anything (shouldn't happen), fall back to manual selection.
    return resolved.ifEmpty { fallbackManual }
}

