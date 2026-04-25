package fr.geoking.gaston.api.parking

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.parking.ParkingAggregator
import fr.geoking.gaston.parking.ParkingApiSelector
import fr.geoking.gaston.parking.ParkingProvider
import io.ktor.client.HttpClient

/**
 * Builds parking API clients and providers, and the [ParkingAggregator].
 * Use this to get a single entry point that aggregates LiveParking, ParkAPI, and OSM.
 */
class ParkingProviderFactory(
    private val httpClient: HttpClient,
    private val overpassClient: OverpassClient,
    private val parkApiCityConfigs: List<ParkApiCityConfig> = defaultParkApiCities()
) {
    /**
     * Creates the aggregator and selector. Call [ParkingAggregator.getParkingNearby] with
     * a [ParkingQueryContext] and radius to get merged results from all covering providers.
     */
    fun createAggregator(): ParkingAggregator {
        val providers = createProviders()
        val selector = ParkingApiSelector(providers)
        return ParkingAggregator(providers, selector)
    }

    /**
     * Creates all parking providers (LiveParking, ParkAPI, OSM). Exposed for tests or custom wiring.
     */
    fun createProviders(): List<ParkingProvider> {
        val liveParkingClient = LiveParkingClient(httpClient)
        val liveParkingProvider = LiveParkingProvider(liveParkingClient)
        val parkApiClient = ParkApiClient(httpClient)
        val parkApiProvider = ParkApiProvider(parkApiClient, parkApiCityConfigs)
        val osmProvider = OsmParkingProvider(overpassClient)
        return listOf(liveParkingProvider, parkApiProvider, osmProvider)
    }

    companion object {
        /** Default ParkAPI cities (active_support) with approximate bboxes. */
        fun defaultParkApiCities(): List<ParkApiCityConfig> = listOf(
            ParkApiCityConfig("Zuerich", 47.30, 47.45, 8.45, 8.65),
            ParkApiCityConfig("Dresden", 50.95, 51.15, 13.65, 13.85),
            ParkApiCityConfig("Hamburg", 53.45, 53.65, 9.85, 10.25),
            ParkApiCityConfig("Basel", 47.50, 47.62, 7.55, 7.65),
            ParkApiCityConfig("Freiburg", 47.95, 48.05, 7.80, 7.92),
            ParkApiCityConfig("Heidelberg", 49.35, 49.45, 8.65, 8.78),
            ParkApiCityConfig("Karlsruhe", 48.95, 49.05, 8.35, 8.48),
            ParkApiCityConfig("Nuernberg", 49.40, 49.52, 11.02, 11.15),
            ParkApiCityConfig("Ulm", 48.35, 48.42, 9.95, 10.05),
            ParkApiCityConfig("Wiesbaden", 50.02, 50.12, 8.18, 8.28),
            ParkApiCityConfig("Ingolstadt", 48.72, 48.80, 11.38, 11.45),
            ParkApiCityConfig("Kaiserslautern", 49.42, 49.47, 7.73, 7.80),
            ParkApiCityConfig("Aarhus", 56.12, 56.18, 10.15, 10.22)
        )
    }
}
