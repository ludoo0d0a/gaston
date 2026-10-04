package fr.geoking.gaston.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.feature.notification.NotificationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoDeveloperScreenTest {

    private lateinit var settingsManager: SettingsManager
    private lateinit var notificationHelper: NotificationHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsManager = SettingsManager(context)
        notificationHelper = NotificationHelper(context)
        startKoin {
            modules(
                module {
                    single { settingsManager }
                    single { notificationHelper }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun settingsScreenIncludesDeveloperEntryWhenDebugDev() {
        assertTrue("DEBUG_DEV must be on for local/dev builds", BuildConfig.DEBUG_DEV)

        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoSettingsScreen(carContext, settingsManager)
        val template = screen.onGetTemplate() as ListTemplate
        val titles = template.singleList!!.items.map { (it as Row).title.toString() }

        assertTrue(
            titles.contains(carContext.getString(R.string.screen_developer)),
        )
    }

    @Test
    fun mapSettingsScreenIncludesDeveloperEntryWhenDebugDev() {
        assertTrue("DEBUG_DEV must be on for local/dev builds", BuildConfig.DEBUG_DEV)

        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoMapSettingsScreen(carContext, settingsManager)
        val template = screen.onGetTemplate() as ListTemplate
        val titles = template.singleList!!.items.map { (it as Row).title.toString() }

        assertTrue(
            titles.contains(carContext.getString(R.string.screen_developer)),
        )
    }

    @Test
    fun developerScreenExposesRadarNotificationTest() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoDeveloperScreen(carContext, settingsManager)
        val template = screen.onGetTemplate() as ListTemplate
        val items = template.singleList!!.items.map { it as Row }

        val radarRow = items.find {
            it.title.toString() == carContext.getString(R.string.dev_test_danger_zone_notification)
        }
        assertNotNull("Radar notification test row missing", radarRow)
        assertEquals(
            carContext.getString(R.string.dev_test_danger_zone_notification_subtitle),
            radarRow!!.texts.firstOrNull()?.toString(),
        )
        // Action row (not a toggle): onClickListener is registered so the HUN can be posted.
        assertNotNull(radarRow.onClickDelegate)
    }
}
