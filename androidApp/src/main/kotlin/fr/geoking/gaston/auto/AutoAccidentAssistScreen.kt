package fr.geoking.gaston.auto

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.R

/**
 * Terminal accident assist on Android Auto: location + dial emergency.
 * Full checklist / paper constat stays on the phone (driver-safe).
 */
class AutoAccidentAssistScreen(
    carContext: CarContext,
    private val universalNumber: String,
    private val latitude: Double?,
    private val longitude: Double?,
    private val locationAddress: String?,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoAccidentAssistScreen",
        templateName = "MessageTemplate",
    ) {
        val locationLine = when {
            latitude != null && longitude != null -> {
                val coords = carContext.getString(
                    R.string.emergency_coords,
                    String.format("%.6f", latitude),
                    String.format("%.6f", longitude),
                )
                listOfNotNull(coords, locationAddress).joinToString("\n")
            }
            else -> carContext.getString(R.string.accident_auto_location_pending)
        }
        val body = carContext.getString(R.string.accident_auto_body, locationLine).take(500)

        MessageTemplate.Builder(body)
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.accident_had_accident))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setIcon(carContext.dashboardEmergencyIcon())
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.emergency_call, universalNumber))
                    .setBackgroundColor(CarColor.RED)
                    .setFlags(Action.FLAG_PRIMARY)
                    .setOnClickListener { dial(universalNumber) }
                    .build()
            )
            .build()
    }

    private fun dial(number: String) {
        val sanitized = number.replace(" ", "")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            carContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AutoAccidentAssist", "Dialing failed", e)
        }
    }
}
