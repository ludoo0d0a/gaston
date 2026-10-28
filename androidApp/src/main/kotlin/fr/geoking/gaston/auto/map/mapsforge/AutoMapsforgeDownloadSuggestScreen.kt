package fr.geoking.gaston.auto.map.mapsforge

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.auto.safeCarTemplate
import kotlinx.coroutines.launch

class AutoMapsforgeDownloadSuggestScreen(
    carContext: CarContext,
    private val mapManager: MapsforgeMapManager,
    private val preset: MapsforgeServerMap,
    private val onDownloaded: () -> Unit = {}
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoMapsforgeDownloadSuggestScreen",
        templateName = "MessageTemplate"
    ) {
        val title = carContext.getString(
            R.string.mapsforge_suggest_download_region,
            preset.name,
            preset.sizeEstimateMb
        )
        val warningMessage = carContext.getString(
            R.string.map_download_warning_message,
            preset.name,
            preset.sizeEstimateMb
        )

        MessageTemplate.Builder(warningMessage.take(500))
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_download))
                    .setOnClickListener {
                        lifecycleScope.launch {
                            try {
                                carContext.getCarService(AppManager::class.java)
                                    .showToast(
                                        carContext.getString(R.string.mapsforge_starting_download, preset.name),
                                        CarToast.LENGTH_SHORT
                                    )
                            } catch (_: Exception) {
                            }
                            val res = mapManager.downloadMap(preset.url, "${preset.storageKey}.map")
                            if (res.isSuccess) {
                                try {
                                    carContext.getCarService(AppManager::class.java)
                                        .showToast(
                                            carContext.getString(R.string.mapsforge_download_success, preset.name),
                                            CarToast.LENGTH_SHORT
                                        )
                                } catch (_: Exception) {
                                }
                                onDownloaded()
                                screenManager.pop()
                            }
                        }
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_cancel))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
