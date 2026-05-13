package fr.geoking.gaston.auto

import android.content.res.Configuration
import androidx.car.app.CarContext

val CarContext.isDarkMode: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
