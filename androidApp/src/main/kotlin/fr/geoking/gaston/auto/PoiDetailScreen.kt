package fr.geoking.gaston.auto

import android.content.Intent
import fr.geoking.gaston.SettingsManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.api.belib.StationAvailabilitySummary

/**
 * Android Auto screen showing simplified POI details and a "Navigate" action that starts navigation
 * (e.g. handing off to the Android Auto driving app).
 *
 * Uses [MessageTemplate] — a terminal template for station detail.
 * The simplified view ensures compliance with host rules, specifically the limit of one label action.
 */
class PoiDetailScreen(
    carContext: CarContext,
    private val poi: Poi,
    private val settingsManager: SettingsManager,
    @Suppress("UNUSED_PARAMETER") availabilitySummary: StationAvailabilitySummary? = null,
    @Suppress("UNUSED_PARAMETER") effectiveEnergyTypes: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") effectivePowerLevels: Set<Int> = emptySet(),
    @Suppress("UNUSED_PARAMETER") rating: Int? = null,
    @Suppress("UNUSED_PARAMETER") poiList: List<Poi> = emptyList(),
    @Suppress("UNUSED_PARAMETER") initialPoiIndex: Int = -1,
    @Suppress("UNUSED_PARAMETER") availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap(),
    @Suppress("UNUSED_PARAMETER") onPoiSelected: ((Poi) -> Unit)? = null
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "PoiDetailScreen",
        templateName = "MessageTemplate",
    ) {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val navigateIntent = Intent(CarContext.ACTION_NAVIGATE).apply {
            data = IntentNavigationHelper.getNavigationUri(poi)
        }

        val body = poi.address.ifBlank { carContext.getString(R.string.poi_no_extra_details) }

        MessageTemplate.Builder(body)
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.navigate))
                    .setOnClickListener { carContext.startCarApp(navigateIntent) }
                    .build()
            )
            .build()
    }

}
