package fr.geoking.gaston.auto

import android.content.Intent
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.api.belib.StationAvailabilitySummary

/**
 * Android Auto screen showing full POI details and a "Go to this station" action
 * that starts navigation (e.g. to Android Auto driving app).
 *
 * Uses [ListTemplate] rows (not [androidx.car.app.model.MessageTemplate]) so details stay
 * within host row limits; [safeCarTemplate] avoids session crashes on validation errors.
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
        val navigateIntent = Intent(CarContext.ACTION_NAVIGATE).apply {
            data = IntentNavigationHelper.getNavigationUri(poi)
        }

        val navigateAction = Action.Builder()
            .setTitle(carContext.getString(R.string.screen_navigate_to))
            .setIcon(carContext.actionNavigateToIcon())
            .setOnClickListener {
                carContext.startCarApp(navigateIntent)
            }
            .build()

        val closeAction = Action.Builder()
            .setTitle(carContext.getString(R.string.action_close))
            .setIcon(carContext.actionCloseIcon())
            .setOnClickListener {
                screenManager.pop()
            }
            .build()

        val actionStripBuilder = ActionStrip.Builder()
        var actionCount = 0

        if (poiList.isNotEmpty() && currentIndex > 0) {
            actionCount++
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_previous))
                    .setIcon(carContext.actionPreviousIcon())
                    .setOnClickListener {
                        currentIndex--
                        poi = poiList[currentIndex]
                        availabilitySummary = availabilityByPoiId[poi.id]
                        onPoiSelected?.invoke(poi)
                        invalidate()
                    }
                    .build()
            )
        }

        if (poiList.isNotEmpty() && currentIndex < poiList.size - 1 && currentIndex != -1) {
            actionCount++
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_next))
                    .setIcon(carContext.actionNextIcon())
                    .setOnClickListener {
                        currentIndex++
                        poi = poiList[currentIndex]
                        availabilitySummary = availabilityByPoiId[poi.id]
                        onPoiSelected?.invoke(poi)
                        invalidate()
                    }
                    .build()
            )
        }

        val currentSettings = settingsManager.settings.value
        val resolvedEnergyTypes = if (effectiveEnergyTypes.isNotEmpty()) effectiveEnergyTypes else currentSettings.effectiveMapEnergyFilterIds()
        val resolvedPowerLevels = if (effectivePowerLevels.isNotEmpty()) effectivePowerLevels else currentSettings.effectiveIrvePowerLevels()

        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

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
            maxRows = listLimit
        )

        val itemListBuilder = ItemList.Builder()
        detailRows.forEach { itemListBuilder.addItem(it) }

        val builder = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(navigateAction)
                    .addEndHeaderAction(closeAction)
                    .build()
            )
            .setSingleList(itemListBuilder.build())

        if (actionCount > 0) {
            builder.setActionStrip(actionStripBuilder.build())
        }

        builder.build()
    }
}
