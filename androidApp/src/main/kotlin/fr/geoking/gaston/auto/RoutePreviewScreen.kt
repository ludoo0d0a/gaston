package fr.geoking.gaston.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import android.net.Uri
import fr.geoking.gaston.R
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RouteResult
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Simple route preview screen:
 * - Fetches 1+ routes using RoutingClient / RoutePlanner
 * - Shows them in a list
 * - Starts navigation by launching the external navigation app.
 */
class RoutePreviewScreen(
    carContext: CarContext,
    private val destination: Poi,
    private val routePlanner: RoutePlanner,
    private val routingClient: RoutingClient
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var isLoading: Boolean = true
    private var routes: List<RouteResult> = emptyList()
    private var activeRoute: RouteResult? = null

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        scope.launch {
            try {
                val result = routingClient.getRoute(
                    originLat = destination.latitude,    // In a real app, origin would be current location.
                    originLon = destination.longitude,   // Here we only demonstrate wiring; API expects origin/destination.
                    destLat = destination.latitude,
                    destLon = destination.longitude
                )
                routes = result?.let { listOf(it) } ?: emptyList()
                activeRoute = routes.firstOrNull()
            } catch (e: Exception) {
                Log.e("RoutePreviewScreen", "Failed to load routes", e)
                routes = emptyList()
                activeRoute = null
            } finally {
                isLoading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            if (isLoading) {
                MessageTemplate.Builder(carContext.getString(R.string.calculating))
                    .setHeader(
                        Header.Builder()
                            .setTitle(destination.name.ifBlank { carContext.getString(R.string.destination_title) })
                            .setStartHeaderAction(Action.BACK)
                            .build()
                    )
                    .setLoading(true)
                    .build()
            } else if (routes.isEmpty()) {
                MessageTemplate.Builder(carContext.getString(R.string.no_route_found))
                    .setHeader(
                        Header.Builder()
                            .setTitle(destination.name.ifBlank { carContext.getString(R.string.destination_title) })
                            .setStartHeaderAction(Action.BACK)
                            .build()
                    )
                    .build()
            } else {
                buildPreviewTemplate()
            }
        } catch (e: Exception) {
            Log.e("RoutePreviewScreen", "Error building template", e)
            MessageTemplate.Builder(carContext.getString(R.string.route_preview_error_format, e.message ?: carContext.getString(R.string.unknown)))
                .setHeader(
                    Header.Builder()
                        .setTitle(carContext.getString(R.string.error))
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .build()
        }
    }

    private fun buildPreviewTemplate(): Template {
        val listBuilder = ItemList.Builder()

        routes.forEachIndexed { index, route ->
            val title = carContext.getString(R.string.route_index_title, index + 1)
            val distanceKm = route.distanceMeters / 1000.0
            val estimatedMinutes = (distanceKm / 80.0 * 60.0).toInt().coerceAtLeast(1)
            val summary = carContext.getString(R.string.distance_format, distanceKm)
            val etaDescription = carContext.getString(R.string.eta_format, estimatedMinutes)

            val row = Row.Builder()
                .setTitle(title)
                .addText(summary)
                .addText(etaDescription)
                .setOnClickListener {
                    activeRoute = route
                    invalidate()
                }
                .build()
            listBuilder.addItem(row)
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.start_nav))
                    .setOnClickListener {
                        if (activeRoute != null) startExternalNavigation()
                    }
                    .build()
            )
            .build()

        val headerBuilder = Header.Builder()
            .setTitle(destination.name.ifBlank { carContext.getString(R.string.destination_title) })
            .setStartHeaderAction(Action.BACK)

        actionStrip.actions.forEach {
            headerBuilder.addEndHeaderAction(it)
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(headerBuilder.build())
            .build()
    }

    private fun startExternalNavigation() {
        // To be compliant with the NAVIGATION category, we push a screen that uses NavigationTemplate
        // instead of relying solely on external intents.
        screenManager.push(GuidanceScreen(carContext, destination))
    }
}

