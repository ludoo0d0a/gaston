package fr.geoking.gaston.auto

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.shared.network.NetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Android Auto route planning flow using SearchTemplate so the user can type OR dictate addresses.
 * This screen collects origin/destination text, geocodes them, and shows POIs along route.
 */
class AutoRoutePlanningScreen(
    carContext: CarContext,
    private val routePlanner: RoutePlanner,
    private val routingClient: RoutingClient,
    private val poiProvider: PoiProvider,
    private val geocodingClient: GeocodingClient,
    private val settingsManager: SettingsManager,
    private val initialOriginQuery: String? = null,
    private val initialDestinationQuery: String? = null,
    private val initialDestination: NavDestination? = null
) : Screen(carContext) {

    private enum class Step { ORIGIN, DESTINATION, RESULTS }

    private var step: Step = Step.ORIGIN
    private var originQuery: String = initialOriginQuery.orEmpty()
    private var destinationQuery: String = initialDestinationQuery.orEmpty()

    private var lastError: String? = null
    private var loading: Boolean = false
    private var stations: List<Poi> = emptyList()

    private var computeJob: Job? = null

    init {
        if (!initialDestinationQuery.isNullOrBlank()) {
            step = Step.RESULTS
            compute()
        } else if (initialOriginQuery.isNullOrBlank()) {
            // Default to current location (origin step can be skipped)
            step = Step.DESTINATION
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            when (step) {
                Step.ORIGIN -> buildSearchTemplate(
                    title = "Origin",
                    hint = "Type or say the origin city/address",
                    query = originQuery,
                    onQueryChange = { originQuery = it.take(120) },
                    onSubmit = {
                        step = Step.DESTINATION
                        invalidate()
                    },
                    showSkipToCurrentLocation = true
                )

                Step.DESTINATION -> buildSearchTemplate(
                    title = "Destination",
                    hint = "Type or say the destination city/address",
                    query = destinationQuery,
                    onQueryChange = { destinationQuery = it.take(120) },
                    onSubmit = {
                        step = Step.RESULTS
                        compute()
                        invalidate()
                    },
                    showSkipToCurrentLocation = false
                )

                Step.RESULTS -> buildResultsTemplate()
            }
        } catch (e: Exception) {
            Log.e("AutoRoutePlanning", "onGetTemplate failed", e)
            MessageTemplate.Builder((e.message ?: e.toString()).take(300))
                .setHeader(Header.Builder().setTitle("Route").setStartHeaderAction(Action.BACK).build())
                .build()
        }
    }

    private fun buildSearchTemplate(
        title: String,
        hint: String,
        query: String,
        onQueryChange: (String) -> Unit,
        onSubmit: () -> Unit,
        showSkipToCurrentLocation: Boolean
    ): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setTitle("Back")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                onQueryChange(searchText)
            }

            override fun onSearchSubmitted(searchText: String) {
                onQueryChange(searchText)
                onSubmit()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(hint)
            .setShowKeyboardByDefault(true)
            .setInitialSearchText(query)
            .setActionStrip(actionStrip)

        if (showSkipToCurrentLocation) {
            builder.setItemList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("Use current location")
                            .setOnClickListener {
                                originQuery = ""
                                step = Step.DESTINATION
                                invalidate()
                            }
                            .build()
                    )
                    .build()
            )
        } else {
            builder.setItemList(
                ItemList.Builder()
                    .setNoItemsMessage("Submit destination to plan route")
                    .build()
            )
        }

        return builder.build()
    }

    private fun buildResultsTemplate(): Template {
        if (loading) {
            return MessageTemplate.Builder("Planning route…")
                .setLoading(true)
                .setHeader(Header.Builder().setTitle("Route").setStartHeaderAction(Action.BACK).build())
                .build()
        }

        lastError?.let { err ->
            return MessageTemplate.Builder(err.take(300))
                .setHeader(Header.Builder().setTitle("Route").setStartHeaderAction(Action.BACK).build())
                .addAction(
                    Action.Builder()
                        .setTitle("Retry")
                        .setOnClickListener { compute() }
                        .build()
                )
                .build()
        }

        val list = ItemList.Builder()
            .setNoItemsMessage("No POIs found along route")

        // Standard Android Auto list limit is 6 items for many templates.
        stations.take(6).forEach { poi ->
            list.addItem(
                Row.Builder()
                    .setTitle(poi.name.ifBlank { poi.address.ifBlank { "POI" } })
                    .addText(poi.address.ifBlank { "${poi.latitude}, ${poi.longitude}" })
                    .setOnClickListener {
                        val uri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(poi.name.ifBlank { poi.address })}")
                        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE).apply { data = uri })
                    }
                    .build()
            )
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Edit")
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                    .setOnClickListener {
                        step = Step.ORIGIN
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Start nav")
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener { openExternalDirections() }
                    .build()
            )
            .build()

        val headerBuilder = Header.Builder()
            .setTitle("Route POIs")
            .setStartHeaderAction(Action.BACK)

        actionStrip.actions.forEach {
            headerBuilder.addEndHeaderAction(it)
        }

        return ListTemplate.Builder()
            .setHeader(headerBuilder.build())
            .setSingleList(list.build())
            .build()
    }

    private fun openExternalDirections() {
        val origin = originQuery.takeIf { it.isNotBlank() } ?: "Current location"
        val dest = destinationQuery
        if (dest.isBlank()) return
        val url = "https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(dest)}&travelmode=driving"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.google.android.apps.maps")
        }
        carContext.startActivity(intent)
    }

    private fun compute() {
        computeJob?.cancel()
        computeJob = lifecycleScope.launch {
            loading = true
            lastError = null
            stations = emptyList()
            invalidate()
            try {
                val originLatLon = if (originQuery.isBlank()) {
                    val loc = LocationHelper.getCurrentLocation(carContext)
                    if (loc == null) throw Exception("Could not determine current location")
                    loc.latitude to loc.longitude
                } else {
                    val origin = geocodingClient.geocode(originQuery, limit = 1).firstOrNull()
                        ?: throw Exception("Origin not found")
                    origin.latitude to origin.longitude
                }

                val destLat: Double
                val destLon: Double
                if (initialDestination?.latitude != null && initialDestination.longitude != null) {
                    destLat = initialDestination.latitude
                    destLon = initialDestination.longitude
                } else {
                    val dest = geocodingClient.geocode(destinationQuery, limit = 1).firstOrNull()
                        ?: throw Exception("Destination not found")
                    destLat = dest.latitude
                    destLon = dest.longitude
                }

                // Warm up: ensure route endpoint is reachable (and fail early with nicer message)
                val route = routingClient.getRoute(originLatLon.first, originLatLon.second, destLat, destLon)
                if (route == null) throw Exception("No route found")

                val result = routePlanner.getStationsAlongRoute(
                    originLat = originLatLon.first,
                    originLon = originLatLon.second,
                    destLat = destLat,
                    destLon = destLon,
                    poiProvider = poiProvider
                )
                result.fold(
                    onSuccess = { stations = it },
                    onFailure = { throw it }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = when (e) {
                    is NetworkException -> e.message ?: "Network error"
                    else -> e.message ?: e.toString()
                }
            } finally {
                loading = false
                invalidate()
            }
        }
    }
}
