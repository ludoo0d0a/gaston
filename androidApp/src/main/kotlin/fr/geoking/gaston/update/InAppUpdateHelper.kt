package fr.geoking.gaston.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helps check for and start in-app updates (Play Core). When an update is available,
 * [updateAvailable] emits the [AppUpdateInfo]. Once a flexible update is downloaded,
 * the app will automatically call [completeUpdate] to install and restart.
 */
class InAppUpdateHelper(
    private val context: android.content.Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private val _updateAvailable = MutableStateFlow<AppUpdateInfo?>(null)
    val updateAvailable: StateFlow<AppUpdateInfo?> = _updateAvailable.asStateFlow()

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            completeUpdate()
        }
    }

    init {
        appUpdateManager.registerListener(installStateListener)
    }

    fun unregister() {
        appUpdateManager.unregisterListener(installStateListener)
    }

    /**
     * Checks if an update is available. If so, [updateAvailable] will emit the info.
     * Prefer [AppUpdateType.FLEXIBLE] so the user can keep using the app while downloading.
     */
    fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                _updateAvailable.value = appUpdateInfo
            }
        }
    }

    /**
     * Starts the in-app update flow. Call when the user taps "Update" in the dialog.
     * [launcher] should be from [Activity.registerForActivityResult] with
     * [androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult].
     * Clears [updateAvailable] so the dialog dismisses.
     */
    fun startUpdate(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        appUpdateManager.startUpdateFlowForResult(appUpdateInfo, launcher, options)
        _updateAvailable.value = null
    }

    /**
     * Installs the downloaded update and restarts the app.
     */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    /**
     * Dismisses the "update available" dialog without starting the update.
     */
    fun dismissUpdate() {
        _updateAvailable.value = null
    }
}
