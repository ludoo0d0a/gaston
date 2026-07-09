package fr.geoking.gaston.auto

import androidx.activity.OnBackPressedCallback
import androidx.car.app.CarContext
import androidx.lifecycle.LifecycleOwner

/**
 * Intercepts the car host back action while a POI detail overlay is shown on a map screen,
 * so [Action.BACK] returns to the station list instead of popping the map screen.
 */
class AutoPoiDetailBackHandler(
    carContext: CarContext,
    lifecycleOwner: LifecycleOwner,
    private val onDismiss: () -> Unit,
) {
    private val callback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            onDismiss()
        }
    }

    init {
        carContext.onBackPressedDispatcher.addCallback(lifecycleOwner, callback)
    }

    fun setDetailVisible(visible: Boolean) {
        callback.isEnabled = visible
    }

    /** Keeps callback state aligned when detail visibility is derived in [onGetTemplate]. */
    fun syncDetailVisible(visible: Boolean) {
        if (callback.isEnabled != visible) {
            callback.isEnabled = visible
        }
    }
}
