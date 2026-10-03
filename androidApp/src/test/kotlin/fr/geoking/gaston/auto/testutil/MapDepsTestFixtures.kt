package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.weather.WeatherProviderFactory
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.api.routing.OsrmRoutingClient
import fr.geoking.gaston.toll.TollCalculator
import fr.geoking.gaston.toll.TollEstimateService
import fr.geoking.gaston.toll.TollRouteComparer

fun fakeMapDeps(poiProvider: PoiProvider = FakePoiProvider()): MapDeps {
    val tollCalculator = TollCalculator { null }
    val tollEstimateService = TollEstimateService(tollCalculator, backendClient = null, useBackend = { false })
    return MapDeps(
        poiProvider = poiProvider,
        availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
        communityRepo = FakeCommunityPoiRepository(),
        favoritesRepo = FakeFavoritesRepository(),
        trafficProviderFactory = TrafficProviderFactory(emptyList()),
        weatherProviderFactory = WeatherProviderFactory(emptyList()),
        routePlanner = RoutePlanner(FakeRoutingClient()),
        routingClient = FakeRoutingClient(),
        tollCalculator = tollCalculator,
        tollEstimateService = tollEstimateService,
        tollRouteComparer = TollRouteComparer(tollEstimateService),
        osrmRoutingClient = OsrmRoutingClient(io.ktor.client.HttpClient()),
        geocodingClient = FakeGeocodingClient(),
        dangerZoneRepository = fr.geoking.gaston.aac.DangerZoneRepository(
            franceRadarsClient = fr.geoking.gaston.api.radars.FranceRadarsClient(
                client = io.ktor.client.HttpClient(),
            )
        ),
    )
}
