package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.weather.WeatherProviderFactory
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.toll.TollCalculator

fun fakeMapDeps(poiProvider: PoiProvider = FakePoiProvider()): MapDeps = MapDeps(
    poiProvider = poiProvider,
    availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
    communityRepo = FakeCommunityPoiRepository(),
    favoritesRepo = FakeFavoritesRepository(),
    trafficProviderFactory = TrafficProviderFactory(emptyList()),
    weatherProviderFactory = WeatherProviderFactory(emptyList()),
    routePlanner = RoutePlanner(FakeRoutingClient()),
    routingClient = FakeRoutingClient(),
    tollCalculator = TollCalculator { null },
    geocodingClient = FakeGeocodingClient(),
)
