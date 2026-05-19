package fr.geoking.gaston.poi

/**
 * Representative map positions and auto-mode fuel providers for integration tests.
 * Keep in sync with [autoProvidersForCountries] (fuel branch).
 */
data class CountryStationProbe(
    val iso: String,
    val cityLabel: String,
    val latitude: Double,
    val longitude: Double,
    val fuelProvider: PoiProviderType,
    /**
     * When true, an empty result is reported as skipped (not a failure). Use for providers
     * that are mapped in auto mode but currently return no data from the upstream API.
     */
    val skipIfEmpty: Boolean = false,
)

object AutoModeCountryProbes {
    val ALL: List<CountryStationProbe> = listOf(
        CountryStationProbe("FR", "Paris", 48.8566, 2.3522, PoiProviderType.DataGouv),
        CountryStationProbe("GB", "London", 51.5074, -0.1278, PoiProviderType.UkCma),
        CountryStationProbe("IT", "Rome", 41.9028, 12.4964, PoiProviderType.ItalyMimit),
        CountryStationProbe("SI", "Ljubljana", 46.0569, 14.5058, PoiProviderType.SloveniaGorivaSi),
        CountryStationProbe("NO", "Oslo", 59.9139, 10.7522, PoiProviderType.NorwayDrivstoffAppen),
        CountryStationProbe(
            iso = "SE",
            cityLabel = "Stockholm",
            latitude = 59.3293,
            longitude = 18.0686,
            fuelProvider = PoiProviderType.SwedenDrivstoffAppen,
            skipIfEmpty = true,
        ),
        CountryStationProbe("PT", "Lisbon", 38.7223, -9.1393, PoiProviderType.PortugalDgeg),
        CountryStationProbe("NL", "Amsterdam", 52.3676, 4.9041, PoiProviderType.NetherlandsAnwb),
        CountryStationProbe("DK", "Copenhagen", 55.6761, 12.5683, PoiProviderType.DenmarkFuelpricesDk),
        CountryStationProbe("HR", "Zagreb", 45.8150, 15.9819, PoiProviderType.CroatiaMzoe),
        CountryStationProbe("FI", "Helsinki", 60.1699, 24.9384, PoiProviderType.FinlandPolttoaine),
        CountryStationProbe("GR", "Athens", 37.9838, 23.7275, PoiProviderType.GreeceFuelGr),
        CountryStationProbe(
            iso = "IE",
            cityLabel = "Dublin",
            latitude = 53.3498,
            longitude = -6.2603,
            fuelProvider = PoiProviderType.IrelandPickAPump,
            skipIfEmpty = true,
        ),
        CountryStationProbe("MD", "Chișinău", 47.0105, 28.8638, PoiProviderType.MoldovaAnre),
        CountryStationProbe("RO", "Bucharest", 44.4268, 26.1025, PoiProviderType.RomaniaPeco),
        CountryStationProbe(
            iso = "RS",
            cityLabel = "Belgrade",
            latitude = 44.7866,
            longitude = 20.4489,
            fuelProvider = PoiProviderType.SerbiaNis,
            skipIfEmpty = true,
        ),
        CountryStationProbe("MX", "Mexico City", 19.4326, -99.1332, PoiProviderType.MexicoCre),
        CountryStationProbe(
            iso = "AR",
            cityLabel = "Buenos Aires",
            latitude = -34.6037,
            longitude = -58.3816,
            fuelProvider = PoiProviderType.ArgentinaEnergia,
            skipIfEmpty = true,
        ),
        CountryStationProbe(
            iso = "ES",
            cityLabel = "Madrid",
            latitude = 40.4168,
            longitude = -3.7038,
            fuelProvider = PoiProviderType.SpainMinetur,
            skipIfEmpty = true,
        ),
        CountryStationProbe("DE", "Berlin", 52.5200, 13.4050, PoiProviderType.GermanyTankerkoenig),
        CountryStationProbe("AT", "Vienna", 48.2082, 16.3738, PoiProviderType.AustriaEControl),
        CountryStationProbe("BE", "Brussels", 50.8503, 4.3517, PoiProviderType.BelgiumOfficial),
        CountryStationProbe("LU", "Luxembourg", 49.6116, 6.1319, PoiProviderType.OpenVanCamp),
    )
}
