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

        val installedLabels = mapManager.installedRegionLabels()
        val activeLabel = when {
            installedLabels.isNotEmpty() -> installedLabels.joinToString(", ")
            activeMap != null -> activeMap.name
            else -> carContext.getString(R.string.network_none)
        }
        addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.mapsforge_active_map))
                .addText(activeLabel.take(500))
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
        val missingRecommended = mapManager.getMissingRecommendedPreset(recommendLat, recommendLon)

        // Order: installed presets first, then missing recommended, then remaining catalog.
        val catalog = buildList {
            val seenKeys = mutableSetOf<String>()
            fun addUnique(preset: MapsforgeServerMap) {
                val key = "${preset.storageKey}|${preset.name}"
                if (seenKeys.add(key)) add(preset)
            }
            for (preset in MapsforgePresetServers.PRESET_MAPS) {
                if (mapManager.isPresetInstalled(preset)) addUnique(preset)
            }
            if (missingRecommended != null) addUnique(missingRecommended)
            addUnique(recommended)
            for (preset in MapsforgePresetServers.PRESET_MAPS) {
                addUnique(preset)
            }
        }

        for (preset in catalog) {
            if (!canAdd()) break
            val isInstalled = mapManager.isPresetInstalled(preset)
            val isRecommended = preset.storageKey == recommended.storageKey &&
                preset.name == recommended.name
            val status = if (isInstalled) {
                carContext.getString(R.string.mapsforge_installed, preset.sizeEstimateMb)
            } else {
                carContext.getString(R.string.mapsforge_available, preset.sizeEstimateMb)
            }
            val subtitle = when {
                isRecommended && !isInstalled -> carContext.getString(
                    R.string.mapsforge_suggest_download_region,
                    preset.name,
                    preset.sizeEstimateMb,
                )
                isInstalled -> status
                else -> "${preset.region} · $status"
            }

            addRow(
                Row.Builder()
                    .setTitle(
                        if (isRecommended && !isInstalled) {
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
