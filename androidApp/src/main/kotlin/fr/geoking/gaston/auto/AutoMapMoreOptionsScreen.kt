package fr.geoking.gaston.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager

class AutoMapMoreOptionsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val lat: Double,
    private val lon: Double,
    private val onRecenter: () -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Recenter")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener {
                        onRecenter()
                        screenManager.pop()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Open in External Map")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener {
                        val intent = Intent(CarContext.ACTION_NAVIGATE).apply {
                            data = Uri.parse("geo:$lat,$lon?q=${Uri.encode("%.4f, %.4f".format(java.util.Locale.US, lat, lon))}")
                        }
                        carContext.startCarApp(intent)
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Settings")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                    .setOnClickListener {
                        screenManager.push(AutoMapSettingsScreen(carContext, settingsManager))
                    }
                    .build()
            )

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("More Options")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
            .build()
    }
}
