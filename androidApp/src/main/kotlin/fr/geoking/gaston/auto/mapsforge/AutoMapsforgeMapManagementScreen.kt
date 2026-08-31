package fr.geoking.gaston.auto.mapsforge

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.AppManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.auto.safeCarTemplate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class AutoMapsforgeMapManagementScreen(
    carContext: CarContext,
    private val mapManager: MapsforgeMapManager
) : Screen(carContext) {

    private var installedMaps: List<File> = mapManager.installedMaps.value
    private var downloadProgress: DownloadProgress? = mapManager.downloadProgress.value

    init {
        lifecycleScope.launch {
            mapManager.installedMaps.collectLatest {
                installedMaps = it
                invalidate()
            }
        }
        lifecycleScope.launch {
            mapManager.downloadProgress.collectLatest {
                downloadProgress = it
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoMapsforgeMapManagementScreen",
        templateName = "ListTemplate"
    ) {
        val listBuilder = ItemList.Builder()
        val activeMap = mapManager.getActiveMapFile()

        val activeLabel = activeMap?.name ?: carContext.getString(R.string.network_none)
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.mapsforge_active_map))
                .addText(activeLabel)
                .build()
        )

        val progress = downloadProgress
        if (progress != null && !progress.isComplete && progress.error == null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.mapsforge_downloading, progress.fileName))
                    .addText("${progress.progressPercent}% (${progress.bytesDownloaded / (1024 * 1024)} MB)")
                    .build()
            )
        } else if (progress?.error != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.mapsforge_download_error))
                    .addText(progress.error)
                    .build()
            )
        }

        MapsforgePresetServers.PRESET_MAPS.forEach { preset ->
            val isInstalled = installedMaps.any { it.name == preset.name || it.name == "${preset.name}.map" || it.name.contains(preset.name, ignoreCase = true) }
            val subtitle = if (isInstalled) {
                carContext.getString(R.string.mapsforge_installed, preset.sizeEstimateMb)
            } else {
                carContext.getString(R.string.mapsforge_available, preset.sizeEstimateMb)
            }

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(preset.name)
                    .addText("${preset.region} · $subtitle")
                    .setOnClickListener {
                        if (progress != null && !progress.isComplete && progress.error == null) {
                            try {
                                carContext.getCarService(AppManager::class.java)
                                    .showToast(carContext.getString(R.string.mapsforge_download_in_progress), CarToast.LENGTH_SHORT)
                            } catch (_: Exception) {}
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            try {
                                carContext.getCarService(AppManager::class.java)
                                    .showToast(carContext.getString(R.string.mapsforge_starting_download, preset.name), CarToast.LENGTH_SHORT)
                            } catch (_: Exception) {}
                            val result = mapManager.downloadMap(preset.url, "${preset.name}.map")
                            if (result.isSuccess) {
                                try {
                                    carContext.getCarService(AppManager::class.java)
                                        .showToast(carContext.getString(R.string.mapsforge_download_success, preset.name), CarToast.LENGTH_SHORT)
                                } catch (_: Exception) {}
                            }
                            invalidate()
                        }
                    }
                    .build()
            )
        }

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.mapsforge_offline_maps))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
