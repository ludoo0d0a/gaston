package fr.geoking.gaston.di

import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.aac.DangerZoneRepository
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.routing.OsrmRoutingClient
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.weather.WeatherProviderFactory
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.toll.TollCalculator
import fr.geoking.gaston.toll.TollEstimateService
import fr.geoking.gaston.toll.TollRouteComparer
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

/**
 * Ensures [appModule] + [mapModule] can resolve every dependency [MapDeps] / MainActivity need.
 * A missing or mistyped Koin binding surfaces here as NoBeanDefFoundException instead of a
 * forever CircularProgress on the phone map / empty dashboard POI widgets.
 */
@RunWith(RobolectricTestRunner::class)
class MapModuleResolutionTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        startKoin {
            androidContext(context)
            modules(appModule)
        }
        MapModuleLoader.ensureLoaded()
    }

    @After
    fun tearDown() {
        stopKoin()
        MapModuleLoader.resetForTests()
    }

    @Test
    fun mapDepsResolveAfterEnsureLoaded() {
        val koin = GlobalContext.get()
        val failures = mutableListOf<String>()
        fun causeChain(e: Throwable): String {
            val parts = mutableListOf<String>()
            var cur: Throwable? = e
            var depth = 0
            while (cur != null && depth < 8) {
                parts += "${cur.javaClass.simpleName}: ${cur.message}"
                cur = cur.cause
                depth++
            }
            return parts.joinToString(" <- ")
        }
        fun <T : Any> check(name: String, block: () -> T) {
            try {
                assertNotNull(name, block())
            } catch (e: Throwable) {
                failures += "$name: ${causeChain(e)}"
            }
        }

        check("PoiProvider") { koin.get<PoiProvider>() }
        check("BorneAvailabilityProviderFactory") { koin.get<BorneAvailabilityProviderFactory>() }
        check("CommunityPoiRepository") { koin.get<CommunityPoiRepository>() }
        check("FavoritesRepository") { koin.get<FavoritesRepository>() }
        check("TrafficProviderFactory") { koin.get<TrafficProviderFactory>() }
        check("WeatherProviderFactory") { koin.get<WeatherProviderFactory>() }
        check("RoutePlanner") { koin.get<RoutePlanner>() }
        check("RoutingClient") { koin.get<RoutingClient>() }
        check("TollCalculator") { koin.get<TollCalculator>() }
        check("TollEstimateService") { koin.get<TollEstimateService>() }
        check("TollRouteComparer") { koin.get<TollRouteComparer>() }
        check("OsrmRoutingClient") { koin.get<OsrmRoutingClient>() }
        check("GeocodingClient") { koin.get<GeocodingClient>() }
        check("DangerZoneRepository") { koin.get<DangerZoneRepository>() }

        if (failures.isNotEmpty()) {
            throw AssertionError("MapDeps Koin resolution failed:\n" + failures.joinToString("\n"))
        }
    }
}
