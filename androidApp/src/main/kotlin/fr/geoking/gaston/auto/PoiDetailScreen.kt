package fr.geoking.gaston.auto

import android.content.Intent
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.ui.BrandHelper

/**
 * Android Auto screen showing full POI details and a "Go to this station" action
 * that starts navigation (e.g. to Android Auto driving app).
 */
class PoiDetailScreen(
    carContext: CarContext,
    private val poi: Poi,
    private val settingsManager: SettingsManager,
    private val availabilitySummary: StationAvailabilitySummary? = null,
    private val rating: Int? = null
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
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

        val currentSettings = settingsManager.settings.value
        val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()
        val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()

        val detailRows = AutoPoiUiHelper.buildPoiDetailRows(
            carContext = carContext,
            poi = poi,
            availability = availabilitySummary,
            effectiveEnergyTypes = effectiveEnergies,
            effectivePowerLevels = effectivePowerLevels
        )

        val itemListBuilder = ItemList.Builder()
        detailRows.forEach { itemListBuilder.addItem(it) }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(navigateAction)
                    .build()
            )
            .setSingleList(itemListBuilder.build())
            .build()
    }
}
