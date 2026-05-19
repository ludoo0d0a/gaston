package fr.geoking.gaston.auto

import fr.geoking.gaston.R
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
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ui.OpenTollDataHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto screen to download OpenTollData (French highway toll) JSON. Shown from Settings.
 */
class AutoTollDataScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val helper = OpenTollDataHelper(carContext.applicationContext)

    private var isDownloading: Boolean = false
    private var downloadBytes: Long = 0L
    private var downloadTotal: Long? = null
    private var downloadError: String? = null
    private var lastInvalidateTime: Long = 0L

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value

        if (isDownloading) {
            val progressText = when {
                downloadError != null -> "Error: $downloadError"
                downloadTotal != null && downloadTotal!! > 0 -> {
                    val pct = (100 * downloadBytes / downloadTotal!!).toInt()
                    "Downloading toll data… $pct%"
                }
                else -> "Downloading toll data… ${downloadBytes / (1024 * 1024)} MB"
            }
            val cancelAction = Action.Builder().setTitle(carContext.getString(R.string.action_cancel)).setOnClickListener {
                isDownloading = false
                downloadError = null
                invalidate()
            }.build()
            return MessageTemplate.Builder(progressText)
                .setHeader(
                    Header.Builder()
                        .setTitle("OpenTollData")
                        .setStartHeaderAction(Action.BACK)
                        .addEndHeaderAction(cancelAction)
                        .build()
                )
                .build()
        }

        val isDownloaded = helper.isTollDataDownloaded(settings)
        val subtitle = if (isDownloaded) "Downloaded" else "Tap to download French highway toll data"

        val listBuilder = ItemList.Builder()
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_toll_data_opentolldata))
                .addText(subtitle)
                .setOnClickListener {
                    if (isDownloaded) {
                        // Already downloaded; could show a message or do nothing
                        return@setOnClickListener
                    }
                    downloadError = null
                    isDownloading = true
                    downloadBytes = 0L
                    downloadTotal = null
                    invalidate()
                    lifecycleScope.launch {
                        val result = helper.download { bytes, total ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                downloadBytes = bytes
                                downloadTotal = total
                                val now = System.currentTimeMillis()
                                if (now - lastInvalidateTime > 500) {
                                    lastInvalidateTime = now
                                    invalidate()
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isDownloading = false
                            result.fold(
                                onSuccess = { path ->
                                    val current = settingsManager.settings.value
                                    settingsManager.saveSettings(current.copy(tollDataPath = path))
                                },
                                onFailure = { e ->
                                    downloadError = e.message ?: "Download failed"
                                }
                            )
                            invalidate()
                        }
                    }
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_download_toll_data)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
