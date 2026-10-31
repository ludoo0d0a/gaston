package fr.geoking.gaston.auto.map.mapsforge

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.auto.safeCarTemplate
import kotlinx.coroutines.launch

/**
 * Terminal AA prompt asking the driver to download the offline region covering the current position.
 */
class AutoMapsforgeMissingRegionScreen(
    carContext: CarContext,
    private val mapManager: MapsforgeMapManager,
    private val preset: MapsforgeServerMap,
    private val onDownloaded: (() -> Unit)? = null,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoMapsforgeMissingRegionScreen",
        templateName = "MessageTemplate",
    ) {
        val body = carContext.getString(
            R.string.mapsforge_suggest_download_region,
            preset.name,
            preset.sizeEstimateMb,
        ).take(500)

        MessageTemplate.Builder(body)
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.mapsforge_no_map_for_location))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_download))
                    .setFlags(Action.FLAG_PRIMARY)
                    .setOnClickListener(
                        ParkedOnlyOnClickListener.create {
                            lifecycleScope.launch {
                                try {
                                    carContext.getCarService(AppManager::class.java).showToast(
                                        carContext.getString(
                                            R.string.mapsforge_starting_download,
                                            preset.name,
                                        ),
                                        CarToast.LENGTH_SHORT,
                                    )
                                } catch (_: Exception) {
                                }
                                val result = mapManager.downloadMap(
                                    preset.url,
                                    "${preset.storageKey}.map",
                                )
                                if (result.isSuccess) {
                                    onDownloaded?.invoke()
                                    try {
                                        carContext.getCarService(AppManager::class.java).showToast(
                                            carContext.getString(
                                                R.string.mapsforge_download_success,
                                                preset.name,
                                            ),
                                            CarToast.LENGTH_SHORT,
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                                screenManager.pop()
                            }
                        }
                    )
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_maybe_later))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
