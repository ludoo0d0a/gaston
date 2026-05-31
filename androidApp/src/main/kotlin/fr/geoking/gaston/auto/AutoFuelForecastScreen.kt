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
                    loadError = carContext.getString(R.string.location_not_available)
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
        if (!settingsManager.settings.value.hasPremiumFeatures) {
            return MessageTemplate.Builder(carContext.getString(R.string.premium_required_message))
                .setHeader(
                    Header.Builder()
                        .setTitle(carContext.getString(R.string.screen_fuel_price_outlook))
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setIcon(carContext.dashboardFuelIcon())
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.action_ok))
                        .setOnClickListener { screenManager.pop() }
                        .build()
                )
                .build()
        }

        val list = ItemList.Builder()

        if (loading) {
            list.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.forecast_loading))
                    .addText(carContext.getString(R.string.forecast_fetching))
                    .setImage(carContext.actionMapIcon())
                    .build()
            )
        } else if (loadError != null && uiState.historyPoints.isEmpty() && uiState.forecastPoints.isEmpty()) {
            list.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.forecast_unavailable))
                    .addText(loadError ?: carContext.getString(R.string.error_unknown))
                    .build()
            )
        } else {
            val fuelTitle = fuelTitle(uiState.fuelId)
            val lastHist = uiState.historyPoints.maxByOrNull { it.day }
            val histLine = if (lastHist != null) {
                carContext.getString(
                    R.string.forecast_latest_local_avg,
                    lastHist.day,
                    lastHist.priceEurPerL.formatEurL()
                )
            } else {
                carContext.getString(R.string.forecast_no_history_phone)
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
                        .setTitle(carContext.getString(R.string.forecast_next_days))
                        .addText(carContext.getString(R.string.forecast_no_rows))
                        .build()
                )
            } else {
                forecasts.forEachIndexed { index, pt ->
                    val label = carContext.getString(R.string.forecast_day_target, index + 1, pt.day)
                    list.addItem(
                        Row.Builder()
                            .setTitle(label)
                            .addText(carContext.getString(R.string.forecast_est_price, pt.priceEurPerL.formatEurL()))
                            .build()
                    )
                }
            }

            val dir = uiState.directionUp
            val score = uiState.marketScore
            if (dir != null && score != null) {
                val upText = if (dir) {
                    carContext.getString(R.string.forecast_market_up)
                } else {
                    carContext.getString(R.string.forecast_market_flat)
                }
                list.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.forecast_market_signal))
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
                        .setTitle(carContext.getString(R.string.forecast_7day_accuracy))
                        .addText(
                            carContext.getString(
                                R.string.forecast_hit_rate,
                                "${String.format(Locale.US, "%.0f", hit * 100)}%",
                                maeStr
                            )
                        )
                        .build()
                )
            }
            val last = uiState.lastScoreDirectionCorrect
            if (last != null) {
                list.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.forecast_last_scored))
                        .addText(
                            if (last) carContext.getString(R.string.forecast_direction_matched)
                            else carContext.getString(R.string.forecast_direction_not_matched)
                        )
                        .build()
                )
            }
            loadError?.let { err ->
                if (uiState.historyPoints.isNotEmpty() || uiState.forecastPoints.isNotEmpty()) {
                    list.addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.forecast_note))
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
                    .setTitle(carContext.getString(R.string.screen_fuel_price_outlook_short))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.action_refresh))
                            .setOnClickListener { refresh() }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

private fun fuelTitle(fuelId: String): String = when (fuelId) {
    "gazole" -> carContext.getString(R.string.fuel_gazole)
    "sp95" -> carContext.getString(R.string.fuel_sp95_slash)
    "sp98" -> carContext.getString(R.string.fuel_sp98)
    "gplc" -> carContext.getString(R.string.fuel_gplc)
    "e85" -> carContext.getString(R.string.fuel_e85)
    else -> fuelId
}

private fun Double.formatEurL(): String = String.format(Locale.US, "%.3f", this)
