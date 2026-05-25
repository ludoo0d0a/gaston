package fr.geoking.gaston.auto

import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import androidx.car.app.CarContext

val CarContext.carWindowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

val CarContext.isDarkMode: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
