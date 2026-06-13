package fr.geoking.gaston.auto

import android.content.Intent
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.api.belib.StationAvailabilitySummary

/**
 * Android Auto screen showing full POI details and a "Navigate" action that starts navigation
 * (e.g. handing off to the Android Auto driving app).
 *
 * Uses [LongMessageTemplate] — the host-approved *terminal* template for station detail
 * (docs/android-auto.md). A `ListTemplate` is NOT a valid last step of a task, which is why the
 * detail screen was rejected by the host when reached from the map. `LongMessageTemplate` also
 * scrolls, so every fuel price is shown without the ~6-row list cap. [safeCarTemplate] still wraps
 * the build to surface validation errors instead of crashing the session.
 */
class PoiDetailScreen(
    carContext: CarContext,
    private var poi: Poi,
    private val settingsManager: SettingsManager,
    private var availabilitySummary: StationAvailabilitySummary? = null,
    private val effectiveEnergyTypes: Set<String> = emptySet(),
    private val effectivePowerLevels: Set<Int> = emptySet(),
    private val rating: Int? = null,
    private val poiList: List<Poi> = emptyList(),
    private val initialPoiIndex: Int = -1,
    private val availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap(),
    private val onPoiSelected: ((Poi) -> Unit)? = null
) : Screen(carContext) {

    private var currentIndex = initialPoiIndex

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "PoiDetailScreen",
        templateName = "LongMessageTemplate",
    ) {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val navigateIntent = Intent(CarContext.ACTION_NAVIGATE).apply {
            data = IntentNavigationHelper.getNavigationUri(poi)
        }

        val currentSettings = settingsManager.settings.value
        val resolvedEnergyTypes = if (effectiveEnergyTypes.isNotEmpty()) effectiveEnergyTypes else currentSettings.effectiveMapEnergyFilterIds()
        val resolvedPowerLevels = if (effectivePowerLevels.isNotEmpty()) effectivePowerLevels else currentSettings.effectiveIrvePowerLevels()

        val body = AutoPoiUiHelper.buildPoiDetailText(
            carContext = carContext,
            poi = poi,
            availability = availabilitySummary,
            effectiveEnergyTypes = resolvedEnergyTypes,
            effectivePowerLevels = resolvedPowerLevels,
            distanceFromLatLon = currentSettings.lastKnownLat?.let { lat ->
                currentSettings.lastKnownLon?.let { lon ->
                    lat to lon
                }
            }
        )

        val builder = LongMessageTemplate.Builder(body)
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.navigate))
                    .setOnClickListener { carContext.startCarApp(navigateIntent) }
                    .build()
            )

        // LongMessageTemplate caps actions at 2 and Navigate is primary, so we expose a single
        // browse action: step to the next station while one exists, otherwise step back. The
        // station list itself remains the place to browse freely; Back returns to it.
        when {
            poiList.isNotEmpty() && currentIndex in 0 until poiList.size - 1 -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.action_next))
                        .setOnClickListener { moveTo(currentIndex + 1) }
                        .build()
                )
            }
            poiList.isNotEmpty() && currentIndex > 0 -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.action_previous))
                        .setOnClickListener { moveTo(currentIndex - 1) }
                        .build()
                )
            }
        }

        builder.build()
    }

    private fun moveTo(index: Int) {
        if (index !in poiList.indices) return
        currentIndex = index
        poi = poiList[index]
        availabilitySummary = availabilityByPoiId[poi.id]
        onPoiSelected?.invoke(poi)
        invalidate()
    }
}
