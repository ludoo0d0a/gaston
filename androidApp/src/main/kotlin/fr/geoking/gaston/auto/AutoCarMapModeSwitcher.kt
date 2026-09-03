package fr.geoking.gaston.auto

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarToast
import androidx.car.app.Screen
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.weather.WeatherProviderFactory
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.di.MapModuleLoader
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.toll.TollCalculator
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Cycles [CarMapMode] and rebuilds the AA map screen so the new backend is actually shown. */
object AutoCarMapModeSwitcher : KoinComponent {

    fun cycle(
        screen: Screen,
        settingsManager: SettingsManager,
        title: String,
        replaceMapNow: Boolean,
    ) {
        val next = settingsManager.settings.value.carMapMode.next()
        settingsManager.setCarMapMode(next)
        try {
            screen.carContext.getCarService(AppManager::class.java)
                .showToast(next.displayLabel(screen.carContext), CarToast.LENGTH_SHORT)
        } catch (_: Exception) {
        }
        if (replaceMapNow) {
            replaceMap(screen, settingsManager, title)
        }
    }

    /** When returning to a map whose backend no longer matches settings (e.g. cycled in settings). */
    fun replaceIfStale(
        screen: Screen,
        expectedMode: CarMapMode,
        settingsManager: SettingsManager,
        title: String,
    ): Boolean {
        if (settingsManager.settings.value.carMapMode == expectedMode) return false
        return replaceMap(screen, settingsManager, title)
    }

    private fun replaceMap(screen: Screen, settingsManager: SettingsManager, title: String): Boolean {
        val mapDeps = resolveMapDeps() ?: return false
        val replacement = AutoMapScreenFactory.createMapPoiScreen(
            carContext = screen.carContext,
            mapDeps = mapDeps,
            settingsManager = settingsManager,
            title = title,
        )
        val manager = screen.screenManager
        manager.pop()
        manager.push(replacement)
        return true
    }

    private fun resolveMapDeps(): MapDeps? = try {
        MapModuleLoader.ensureLoaded()
        MapDeps(
            poiProvider = get<PoiProvider>(),
            availabilityProviderFactory = get<BorneAvailabilityProviderFactory>(),
            communityRepo = get<CommunityPoiRepository>(),
            favoritesRepo = get<FavoritesRepository>(),
            trafficProviderFactory = get<TrafficProviderFactory>(),
            weatherProviderFactory = get<WeatherProviderFactory>(),
            routePlanner = get<RoutePlanner>(),
            routingClient = get<RoutingClient>(),
            tollCalculator = get<TollCalculator>(),
            geocodingClient = get<GeocodingClient>(),
        )
    } catch (e: Exception) {
        Log.e("AutoCarMapModeSwitcher", "Failed to load map dependencies", e)
        null
    }
}
