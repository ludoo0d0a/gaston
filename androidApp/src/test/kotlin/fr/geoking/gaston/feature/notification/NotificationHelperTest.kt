package fr.geoking.gaston.feature.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationHelperTest {

    @Test
    fun testShowUpdateAvailableNotificationDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = NotificationHelper(context)
        helper.showUpdateAvailableNotification()
        assertNotNull(helper)
    }
}
