package fr.geoking.gaston.auto.map.mapsforge

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.AppManager
import androidx.car.app.constraints.ConstraintManager
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
    private val mapManager: MapsforgeMapManager,
    private val recommendLat: Double? = null,
    private val recommendLon: Double? = null,
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
        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

        val listBuilder = ItemList.Builder()
        val activeMap = mapManager.getActiveMapFile()
        var slotsUsed = 0

        fun canAdd(): Boolean = slotsUsed < listLimit
        fun addRow(row: Row) {
            if (!canAdd()) return
            listBuilder.addItem(row)
            slotsUsed++
        }

        val activeLabel = activeMap?.name ?: carContext.getString(R.string.network_none)
        addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.mapsforge_active_map))
                .addText(activeLabel)
                .build()
        )

        val progress = downloadProgress
        if (progress != null && !progress.isComplete && progress.error == null) {
            addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.mapsforge_downloading, progress.fileName))
                    .addText("${progress.progressPercent}% (${progress.bytesDownloaded / (1024 * 1024)} MB)")
                    .build()
            )
        } else if (progress?.error != null) {
            addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.mapsforge_download_error))
                    .addText(progress.error.take(500))
                    .build()
            )
        }

        val recommended = MapsforgePresetServers.getRecommendedPreset(recommendLat, recommendLon)
        val catalog = buildList {
            add(recommended)
            for (preset in MapsforgePresetServers.FRANCE_REGION_MAPS +
                MapsforgePresetServers.NEIGHBOR_MAPS +
                listOf(MapsforgePresetServers.FRANCE_ALL)
            ) {
                if (preset.storageKey != recommended.storageKey || preset.name != recommended.name) {
                    add(preset)
                }
            }
        }

        for (preset in catalog) {
            if (!canAdd()) break
            val isInstalled = installedMaps.any {
                it.name.equals(preset.storageKey, ignoreCase = true) ||
                    it.name.equals("${preset.storageKey}.map", ignoreCase = true) ||
                    it.name.contains(preset.storageKey, ignoreCase = true)
            }
            val isRecommended = preset.storageKey == recommended.storageKey &&
                preset.name == recommended.name
            val status = if (isInstalled) {
                carContext.getString(R.string.mapsforge_installed, preset.sizeEstimateMb)
            } else {
                carContext.getString(R.string.mapsforge_available, preset.sizeEstimateMb)
            }
            val subtitle = if (isRecommended) {
                carContext.getString(
                    R.string.mapsforge_suggest_download_region,
                    preset.name,
                    preset.sizeEstimateMb,
                )
            } else {
                "${preset.region} · $status"
            }

            addRow(
                Row.Builder()
                    .setTitle(
                        if (isRecommended) {
                            carContext.getString(R.string.mapsforge_download_region, preset.name)
                        } else {
                            preset.name
                        }
                    )
                    .addText(subtitle.take(500))
                    .setOnClickListener { startDownload(preset, progress) }
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

    private fun startDownload(preset: MapsforgeServerMap, progress: DownloadProgress?) {
        if (progress != null && !progress.isComplete && progress.error == null) {
            try {
                carContext.getCarService(AppManager::class.java)
                    .showToast(
                        carContext.getString(R.string.mapsforge_download_in_progress),
                        CarToast.LENGTH_SHORT,
                    )
            } catch (_: Exception) {
            }
            return
        }
        lifecycleScope.launch {
            try {
                carContext.getCarService(AppManager::class.java)
                    .showToast(
                        carContext.getString(R.string.mapsforge_starting_download, preset.name),
                        CarToast.LENGTH_SHORT,
                    )
            } catch (_: Exception) {
            }
            val result = mapManager.downloadMap(preset.url, "${preset.storageKey}.map")
            if (result.isSuccess) {
                try {
                    carContext.getCarService(AppManager::class.java)
                        .showToast(
                            carContext.getString(R.string.mapsforge_download_success, preset.name),
                            CarToast.LENGTH_SHORT,
                        )
                } catch (_: Exception) {
                }
            }
            invalidate()
        }
    }
}
