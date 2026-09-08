package fr.geoking.gaston.auto.testutil

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestAppManager
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider

/** Shared helpers for driving real AA `Screen`/`Session` objects in JVM/Robolectric tests. */
object CarScreenTestHarness {

    fun newTestCarContext(): TestCarContext =
        TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    /** A Surface Robolectric's ShadowSurface can lock/unlock without a real GPU/EGL context. */
    fun newFakeSurfaceContainer(width: Int = 800, height: Int = 480, dpi: Int = 160): SurfaceContainer =
        SurfaceContainer(Surface(SurfaceTexture(0)), width, height, dpi)

    /** Drives a Screen through CREATED -> STARTED -> RESUMED, registering its SurfaceCallback/lifecycle observers. */
    fun createAndStart(screen: Screen): ScreenController =
        ScreenController(screen).apply { moveToState(Lifecycle.State.RESUMED) }

    /** The SurfaceCallback the screen registered via AppManager.setSurfaceCallback, if any. */
    fun surfaceCallbackOf(carContext: TestCarContext): SurfaceCallback? =
        (carContext.getCarService(AppManager::class.java) as TestAppManager).surfaceCallback

    fun screenManagerOf(carContext: TestCarContext): TestScreenManager =
        carContext.getCarService(ScreenManager::class.java) as TestScreenManager
}
