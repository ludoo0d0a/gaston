package fr.geoking.tools.inappupdate

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class CheckFeedback {
    data object None : CheckFeedback()
    data object UpToDate : CheckFeedback()
    data class Error(val message: String) : CheckFeedback()
}

enum class InstallStatus {
    UNKNOWN, PENDING, DOWNLOADING, DOWNLOADED, FAILED, CANCELED, Idle
}

data class UpdateNotificationSpec(
    val channelId: String = "",
    val channelName: String = "",
    val notificationId: Int = 0,
    val titleRes: Int = 0,
    val contentRes: Int = 0,
    val smallIconRes: Int = 0,
    val smallIcon: Int = 0,
    val title: String = "",
    val message: String = "",
    val launchActivityClass: Class<*>? = null,
)

class InAppUpdateHelper(
    context: Context,
    notificationSpec: UpdateNotificationSpec? = null,
    onUpdateAvailableExtra: (() -> Unit)? = null,
) {
    val installStatus: StateFlow<Any> = MutableStateFlow(InstallStatus.Idle)
    val checkFeedback: StateFlow<CheckFeedback> = MutableStateFlow(CheckFeedback.None)
    val updateAvailable: StateFlow<AppUpdateInfo?> = MutableStateFlow(null)
    val autoStartUpdate: StateFlow<Boolean> = MutableStateFlow(false)
    val updateInfo: StateFlow<AppUpdateInfo?> = MutableStateFlow(null)

    fun checkForUpdate(manual: Boolean = false) {}
    fun consumeLaunchIntent(intent: Any?) {}
    fun unregister() {}
    fun startUpdate(info: Any? = null, launcher: Any? = null) {}
    fun autoStartUpdate() {}
    fun maybeAutoStartUpdate(info: Any? = null) {}
    fun dismissUpdate(info: Any? = null) {}
    fun resetCheckFeedback() {}
}
