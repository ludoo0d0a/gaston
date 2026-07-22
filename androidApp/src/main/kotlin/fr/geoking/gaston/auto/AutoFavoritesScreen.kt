package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.launch

/**
 * Lists locally saved favorite stations from [fr.geoking.gaston.community.FavoritesRepository].
 *
 * Reloads on each [onStart] so toggles made on a pushed detail screen appear after BACK.
 */
class AutoFavoritesScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?,
) : Screen(carContext), DefaultLifecycleObserver {

    private var favorites: List<Poi>? = null
    private var isLoading = true

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        loadFavorites()
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()
            val repo = getMapDeps()?.favoritesRepo
            favorites = repo?.getFavorites() ?: emptyList()
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoFavoritesScreen",
        templateName = "ListTemplate",
    ) {
        val title = carContext.getString(R.string.screen_favorites)
        val header = Header.Builder()
            .setTitle(title)
            .setStartHeaderAction(Action.BACK)
            .build()

        if (isLoading && favorites == null) {
            return@safeCarTemplate ListTemplate.Builder()
                .setHeader(header)
                .setLoading(true)
                .build()
        }

        val pois = favorites.orEmpty()
        if (pois.isEmpty()) {
            return@safeCarTemplate MessageTemplate.Builder(
                carContext.getString(R.string.favorites_empty).take(500)
            )
                .setHeader(header)
                .build()
        }

        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

        val settings = settingsManager.settings.value
        val energies = settings.effectiveMapEnergyFilterIds()
        val powerLevels = settings.effectiveIrvePowerLevels()
        val distanceFrom = settings.lastKnownLat?.let { lat ->
            settings.lastKnownLon?.let { lon -> lat to lon }
        }

        val itemListBuilder = ItemList.Builder()
        pois.take(listLimit).forEach { poi ->
            itemListBuilder.addItem(
                AutoPoiUiHelper.buildPoiRow(
                    carContext = carContext,
                    poi = poi,
                    availability = null,
                    effectiveEnergyTypes = energies,
                    effectivePowerLevels = powerLevels,
                    distanceFromLatLon = distanceFrom,
                    includePlace = false,
                ) {
                    screenManager.push(
                        PoiDetailScreen(
                            carContext = carContext,
                            poi = poi,
                            settingsManager = settingsManager,
                            favoritesRepo = getMapDeps()?.favoritesRepo,
                        )
                    )
                }
            )
        }

        ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(itemListBuilder.build())
            .build()
    }
}
