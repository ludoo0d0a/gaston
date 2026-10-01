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
import fr.geoking.gaston.feature.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class CheckFeedback {
    data object None : CheckFeedback()
    data object UpToDate : CheckFeedback()
    data class Error(val message: String) : CheckFeedback()
}

/**
 * Helps check for and start in-app updates (Play Core). When an update is available,
 * [updateAvailable] emits the [AppUpdateInfo]. Once a flexible update is downloaded,
 * the app will automatically call [completeUpdate] to install and restart.
 * Manual checks (Settings) can re-prompt and emit [checkFeedback] when up to date or on error.
 */
class InAppUpdateHelper(
    private val context: android.content.Context,
    private val notificationHelper: NotificationHelper? = null
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private var isUpdateDismissed = false

    private val _updateAvailable = MutableStateFlow<AppUpdateInfo?>(null)
    val updateAvailable: StateFlow<AppUpdateInfo?> = _updateAvailable.asStateFlow()

    private val _installStatus = MutableStateFlow(InstallStatus.UNKNOWN)
    val installStatus: StateFlow<Int> = _installStatus.asStateFlow()

    private val _checkFeedback = MutableStateFlow<CheckFeedback>(CheckFeedback.None)
    val checkFeedback: StateFlow<CheckFeedback> = _checkFeedback.asStateFlow()

    private val installStateListener = InstallStateUpdatedListener { state ->
        _installStatus.value = state.installStatus()
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
     *
     * @param manual When true (Settings), ignores session dismiss, allows re-prompt,
     * and emits [checkFeedback] when already up to date or on error.
     */
    fun checkForUpdate(manual: Boolean = false) {
        if (!manual) {
            if (isUpdateDismissed) return
            // Avoid re-emitting while the "update available" dialog is already showing.
            if (_updateAvailable.value != null) return
        } else {
            _checkFeedback.value = CheckFeedback.None
            isUpdateDismissed = false
        }

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                _installStatus.value = appUpdateInfo.installStatus()

                // If an update is already in progress, don't suggest it again.
                val inProgress = appUpdateInfo.installStatus() == InstallStatus.PENDING ||
                    appUpdateInfo.installStatus() == InstallStatus.DOWNLOADING ||
                    appUpdateInfo.installStatus() == InstallStatus.INSTALLING
                if (inProgress) return@addOnSuccessListener

                // If the update was downloaded while we were not running (or listener wasn't registered yet),
                // complete it immediately so the app restarts into the updated version.
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    completeUpdate()
                    return@addOnSuccessListener
                }

                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    _updateAvailable.value = appUpdateInfo
                    notificationHelper?.showUpdateAvailableNotification()
                    return@addOnSuccessListener
                }

                // If the user already accepted an update flow earlier, Play requires we resume it.
                // But we don't set _updateAvailable again to avoid redundant popups.
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    // Already in progress, installStatus will track it.
                    return@addOnSuccessListener
                }

                if (manual) {
                    _checkFeedback.value = CheckFeedback.UpToDate
                }
            }
            .addOnFailureListener { error ->
                if (manual) {
                    _checkFeedback.value = CheckFeedback.Error(
                        error.message ?: "Unknown error",
                    )
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
        // Once the user clicks "Update", we consider it handled for this session
        // so the popup doesn't reappear if they return to the app while it's starting.
        isUpdateDismissed = true
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
        isUpdateDismissed = true
        _updateAvailable.value = null
    }

    fun resetCheckFeedback() {
        _checkFeedback.value = CheckFeedback.None
    }
}
