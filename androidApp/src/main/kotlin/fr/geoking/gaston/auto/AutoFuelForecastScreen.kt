package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.repository.FuelForecastUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Simple Android Auto list for local fuel outlook (no Canvas).
 * Uses the same [FuelForecastRepository] pipeline as the phone dashboard.
 */
class AutoFuelForecastScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val fuelForecastRepository: FuelForecastRepository
) : Screen(carContext) {

    private var uiState: FuelForecastUiState = FuelForecastUiState(fuelId = "gazole", locationKey = "")
    private var loading = true
    private var loadError: String? = null

    init {
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            loading = true
            loadError = null
            invalidate()
            try {
                val loc = withContext(Dispatchers.IO) { LocationHelper.getCurrentLocation(carContext) }
                if (loc == null) {
                    loadError = "Location unavailable"
                    uiState = FuelForecastUiState(
                        fuelId = "gazole",
                        locationKey = "",
                        errorMessage = loadError
                    )
                } else {
                    val fuelIds = settingsManager.settings.value.effectiveMapEnergyFilterIds()
                    uiState = fuelForecastRepository.refreshAndBuildUiState(
                        loc.latitude,
                        loc.longitude,
                        fuelIds
                    )
                    if (uiState.errorMessage != null) {
                        loadError = uiState.errorMessage
                    }
                }
            } catch (e: Exception) {
                loadError = e.message ?: e.toString()
                uiState = FuelForecastUiState(
                    fuelId = uiState.fuelId,
                    locationKey = uiState.locationKey,
                    errorMessage = loadError
                )
            } finally {
                loading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (!settingsManager.settings.value.isPremium) {
            return MessageTemplate.Builder("Gaston Premium Required")
                .setHeader(
                    Header.Builder()
                        .setTitle("Fuel Price Outlook")
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setIcon(carContext.dashboardFuelIcon())
                .addAction(
                    Action.Builder()
                        .setTitle("OK")
                        .setOnClickListener { screenManager.pop() }
                        .build()
                )
                .build()
        }

        val list = ItemList.Builder()

        if (loading) {
            list.addItem(
                Row.Builder()
                    .setTitle("Loading…")
                    .addText("Fetching local prices and market data")
                    .setImage(carContext.actionMapIcon())
                    .build()
            )
        } else if (loadError != null && uiState.historyPoints.isEmpty() && uiState.forecastPoints.isEmpty()) {
            list.addItem(
                Row.Builder()
                    .setTitle("Forecast unavailable")
                    .addText(loadError ?: "Unknown error")
                    .build()
            )
        } else {
            val fuelTitle = fuelTitle(uiState.fuelId)
            val lastHist = uiState.historyPoints.maxByOrNull { it.day }
            val histLine = if (lastHist != null) {
                "Latest local avg (${lastHist.day}): ${lastHist.priceEurPerL.formatEurL()} €/L"
            } else {
                "No history yet — open the app on the phone on more days to build a series."
            }
            list.addItem(
                Row.Builder()
                    .setTitle(fuelTitle)
                    .addText(histLine)
                    .setImage(carContext.dashboardFuelIcon())
                    .build()
            )

            val forecasts = uiState.forecastPoints.sortedBy { it.day }
            if (forecasts.isEmpty()) {
                list.addItem(
                    Row.Builder()
                        .setTitle("Next days")
                        .addText("No forecast rows yet (needs market data). Pull to refresh from header.")
                        .build()
                )
            } else {
                forecasts.forEachIndexed { index, pt ->
                    val label = when (index) {
                        0 -> "D+1 (target ${pt.day})"
                        1 -> "D+2 (target ${pt.day})"
                        else -> "D+3 (target ${pt.day})"
                    }
                    list.addItem(
                        Row.Builder()
                            .setTitle(label)
                            .addText("Est. ${pt.priceEurPerL.formatEurL()} €/L")
                            .build()
                    )
                }
            }

            val dir = uiState.directionUp
            val score = uiState.marketScore
            if (dir != null && score != null) {
                val upText = if (dir) "Upward pressure on pump prices" else "No strong upward signal"
                list.addItem(
                    Row.Builder()
                        .setTitle("Market signal")
                        .addText("$upText (score ${String.format(Locale.US, "%+.4f", score)})")
                        .build()
                )
            }

            val hit = uiState.accuracyHitRate7d
            val mae = uiState.accuracyMae7d
            if (hit != null && !hit.isNaN()) {
                val maeStr = if (mae != null && !mae.isNaN()) mae.formatEurL() else "—"
                list.addItem(
                    Row.Builder()
                        .setTitle("7-day accuracy")
                        .addText("Hit rate: ${String.format(Locale.US, "%.0f", hit * 100)}% · MAE: $maeStr €/L")
                        .build()
                )
            }
            val last = uiState.lastScoreDirectionCorrect
            if (last != null) {
                list.addItem(
                    Row.Builder()
                        .setTitle("Last scored prediction")
                        .addText(if (last) "Direction matched outcome" else "Direction did not match")
                        .build()
                )
            }
            loadError?.let { err ->
                if (uiState.historyPoints.isNotEmpty() || uiState.forecastPoints.isNotEmpty()) {
                    list.addItem(
                        Row.Builder()
                            .setTitle("Note")
                            .addText(err)
                            .build()
                    )
                }
            }
        }

        return ListTemplate.Builder()
            .setSingleList(list.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Fuel price outlook")
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setTitle("Refresh")
                            .setOnClickListener { refresh() }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

private fun fuelTitle(fuelId: String): String = when (fuelId) {
    "gazole" -> "Gazole"
    "sp95" -> "SP95 / E10"
    "sp98" -> "SP98"
    "gplc" -> "GPLc"
    "e85" -> "E85"
    else -> fuelId
}

private fun Double.formatEurL(): String = String.format(Locale.US, "%.3f", this)
