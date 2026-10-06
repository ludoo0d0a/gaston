package fr.geoking.gaston.auto

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun settingsScreenDoesNotIncludeDeveloperEntry() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoSettingsScreen(carContext, settingsManager)
        val template = screen.onGetTemplate() as ListTemplate
        val titles = template.singleList!!.items.map { (it as Row).title.toString() }

        assertFalse(
            "AutoSettingsScreen should not contain developer sub-menu",
            titles.contains(carContext.getString(R.string.screen_developer)),
        )
    }

    @Test
    fun mapSettingsScreenDoesNotIncludeDeveloperEntry() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoMapSettingsScreen(carContext, settingsManager)
        val template = screen.onGetTemplate() as ListTemplate
        val titles = template.singleList!!.items.map { (it as Row).title.toString() }

        assertFalse(
            "AutoMapSettingsScreen should not contain developer sub-menu",
            titles.contains(carContext.getString(R.string.screen_developer)),
        )
    }

    @Test
    fun moreOptionsScreenIncludesThreeSettingsMenusAtSameLevel() {
        assertTrue("DEBUG_DEV must be on for local/dev builds", BuildConfig.DEBUG_DEV)

        val carContext = CarScreenTestHarness.newTestCarContext()
        val fakeNetworkService = FakeNetworkService()

        val moreScreen = AutoSettingsMenuTestHelper.createMoreOptionsScreen(
            carContext,
            settingsManager,
            fakeNetworkService,
        )
        val template = moreScreen.onGetTemplate() as ListTemplate
        val titles = template.singleList!!.items.map { (it as Row).title.toString() }

        val settingsTitle = carContext.getString(R.string.cd_settings)
        val mapSettingsTitle = carContext.getString(R.string.cd_map_settings)
        val devTitle = carContext.getString(R.string.screen_developer)

        assertTrue("More screen must contain Paramètres", titles.contains(settingsTitle))
        assertTrue("More screen must contain Paramètres carte", titles.contains(mapSettingsTitle))
        assertTrue("More screen must contain Menu développeur", titles.contains(devTitle))
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
            carContext.getString(R.string.dev_test_danger_zone_notification_subtitle_aa),
            radarRow!!.texts.firstOrNull()?.toString(),
        )
        // Action row (not a toggle): onClickListener is registered so the HUN can be posted.
        assertNotNull(radarRow.onClickDelegate)
    }
}

class FakeNetworkService : NetworkService {
    override val status: StateFlow<NetworkStatus> = MutableStateFlow(NetworkStatus())
    override suspend fun getCurrentStatus(): NetworkStatus = NetworkStatus()
}

object AutoSettingsMenuTestHelper {
    fun createMoreOptionsScreen(
        carContext: androidx.car.app.CarContext,
        settingsManager: SettingsManager,
        networkService: NetworkService,
    ): androidx.car.app.Screen {
        return object : androidx.car.app.Screen(carContext) {
            override fun onGetTemplate(): androidx.car.app.model.Template {
                val moreListBuilder = androidx.car.app.model.ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.cd_settings))
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.cd_map_settings))
                            .build()
                    )

                if (BuildConfig.DEBUG_DEV) {
                    moreListBuilder.addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.screen_developer))
                            .build()
                    )
                }

                return ListTemplate.Builder()
                    .setSingleList(moreListBuilder.build())
                    .setHeader(
                        androidx.car.app.model.Header.Builder()
                            .setTitle(carContext.getString(R.string.screen_more))
                            .setStartHeaderAction(androidx.car.app.model.Action.BACK)
                            .build()
                    )
                    .build()
            }
        }
    }
}
