package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.radar.DangerZoneAlertCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android Auto developer options (visible when [fr.geoking.gaston.BuildConfig.DEBUG_DEV]).
 * Danger-zone test: beep + TTS + HUN via [DangerZoneAlertCoordinator].
 */
class AutoDeveloperScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
) : Screen(carContext), KoinComponent {

    private val notificationHelper: NotificationHelper by inject()
    private val dangerZoneCoordinator: DangerZoneAlertCoordinator by inject()

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoDeveloperScreen", "ListTemplate") {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.dev_premium_mode))
                .addText(carContext.getString(R.string.dev_premium_mode_subtitle))
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.setDevSimulatePremium(checked)
                        invalidate()
                    }.setChecked(settings.devSimulatePremium).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.dev_raw_detail))
                .addText(carContext.getString(R.string.dev_raw_detail_subtitle))
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.saveSettings(settings.copy(devRawDetail = checked))
                        invalidate()
                    }.setChecked(settings.devRawDetail).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.dev_test_danger_zone_notification))
                .addText(carContext.getString(R.string.dev_test_danger_zone_notification_subtitle_aa))
                .setOnClickListener {
                    if (!notificationHelper.canPostNotifications()) {
                        carContext.getCarService(androidx.car.app.AppManager::class.java)
                            .showToast(
                                carContext.getString(R.string.dev_test_radar_notification_permission),
                                CarToast.LENGTH_LONG,
                            )
                        return@setOnClickListener
                    }
                    // Same process-wide path as live alerts: beep + TTS + HUN + HUD.
                    dangerZoneCoordinator.triggerTestAlert()
                    carContext.getCarService(androidx.car.app.AppManager::class.java)
                        .showToast(
                            carContext.getString(R.string.dev_test_radar_notification_sent),
                            CarToast.LENGTH_SHORT,
                        )
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.filter_debug_logging))
                .addText(carContext.getString(R.string.filter_capture_network_logs))
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.saveSettings(settings.copy(debugLoggingEnabled = checked))
                        invalidate()
                    }.setChecked(settings.debugLoggingEnabled).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.settings_debug_disable_cache))
                .addText(carContext.getString(R.string.dev_disable_cache_subtitle))
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.setDisableCache(checked)
                        invalidate()
                    }.setChecked(settings.disableCache).build()
                )
                .build()
        )

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.screen_developer))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
