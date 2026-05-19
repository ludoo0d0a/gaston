package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.model.DateTimeWithZone
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.poi.Poi
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Screen showing active navigation guidance using NavigationTemplate.
 */
class GuidanceScreen(
    carContext: CarContext,
    private val destination: Poi
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "GuidanceScreen") {
        // In a real app, these values would come from a navigation engine.
        // For now, we provide placeholders to satisfy Play Store requirements for the NAVIGATION category.

        val maneuver = Maneuver.Builder(Maneuver.TYPE_DEPART)
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
            .build()

        val nextStep = Step.Builder()
            .setManeuver(maneuver)
            .setRoad(destination.name.ifBlank { "Destination" })
            .build()

        val routingInfo = RoutingInfo.Builder()
            .setCurrentStep(nextStep, Distance.create(500.0, Distance.UNIT_METERS))
            .setNextStep(nextStep)
            .build()

        val arrivalTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
        val travelEstimate = TravelEstimate.Builder(
            Distance.create(10.5, Distance.UNIT_KILOMETERS),
            DateTimeWithZone.create(arrivalTime, TimeZone.getDefault())
        ).setRemainingTimeSeconds(TimeUnit.MINUTES.toSeconds(10))
         .build()

        NavigationTemplate.Builder()
            .setNavigationInfo(routingInfo)
            .setDestinationTravelEstimate(travelEstimate)
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.action_exit))
                            .setOnClickListener { screenManager.pop() }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
