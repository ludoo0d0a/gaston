package fr.geoking.gaston.auto

import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.R
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
        templateName = "ListTemplate",
    ) {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }

        val currentSettings = settingsManager.settings.value
        val resolvedEnergyTypes = if (effectiveEnergyTypes.isNotEmpty()) effectiveEnergyTypes else currentSettings.effectiveMapEnergyFilterIds()
        val resolvedPowerLevels = if (effectivePowerLevels.isNotEmpty()) effectivePowerLevels else currentSettings.effectiveIrvePowerLevels()

        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) { 6 }

        val detailRows = AutoPoiUiHelper.buildPoiDetailRows(
            carContext = carContext,
            poi = poi,
            availability = availabilitySummary,
            effectiveEnergyTypes = resolvedEnergyTypes,
            effectivePowerLevels = resolvedPowerLevels,
            distanceFromLatLon = currentSettings.lastKnownLat?.let { lat ->
                currentSettings.lastKnownLon?.let { lon ->
                    lat to lon
                }
            },
            maxRows = listLimit - 1 // Leave room for "More details"
        )

        val itemListBuilder = ItemList.Builder()
        detailRows.forEach { itemListBuilder.addItem(it) }

        // Structured list is better for the car, but we keep the full text view as a fallback
        itemListBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_more_options))
                .addText(carContext.getString(R.string.about_view_disclaimer))
                .setOnClickListener(ParkedOnlyOnClickListener.create { showFullDetailText() })
                .setBrowsable(true)
                .build()
        )

        val actionStripBuilder = ActionStrip.Builder()
            // Navigate must work while driving — do not wrap in ParkedOnlyOnClickListener.
            .addAction(carContext.navigateToStationAction(poi))

        // Previous / Next actions in ActionStrip (max 2 for ListTemplate)
        if (poiList.isNotEmpty() && currentIndex > 0) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setIcon(carContext.actionPreviousIcon())
                    .setOnClickListener(ParkedOnlyOnClickListener.create { moveTo(currentIndex - 1) })
                    .build()
            )
        } else if (poiList.isNotEmpty() && currentIndex < poiList.size - 1) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setIcon(carContext.actionNextIcon())
                    .setOnClickListener(ParkedOnlyOnClickListener.create { moveTo(currentIndex + 1) })
                    .build()
            )
        }

        ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(itemListBuilder.build())
            .setActionStrip(actionStripBuilder.build())
            .build()
    }

    private fun showFullDetailText() {
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

        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }

        screenManager.push(
            object : Screen(carContext) {
                override fun onGetTemplate(): Template = LongMessageTemplate.Builder(body)
                    .setTitle(title)
                    .setHeaderAction(Action.BACK)
                    .build()
            }
        )
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
