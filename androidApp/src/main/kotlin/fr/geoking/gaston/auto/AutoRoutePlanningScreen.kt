package fr.geoking.gaston.auto

import android.content.Intent
import android.net.Uri
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
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.geocoding.GeocodedPlace
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
    private var loadingMessageResId: Int = R.string.planning_route
    private var stations: List<Poi> = emptyList()
    private var currentRoutePoints: List<Pair<Double, Double>>? = null

    private var originSuggestions: List<GeocodedPlace> = emptyList()
    private var destinationSuggestions: List<GeocodedPlace> = emptyList()
    private var suggestionJob: Job? = null

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
                    title = carContext.getString(R.string.route_origin),
                    hint = carContext.getString(R.string.route_origin_placeholder),
                    query = originQuery,
                    onQueryChange = { originQuery = it.take(120) },
                    onSubmit = {
                        step = Step.DESTINATION
                        invalidate()
                    },
                    showSkipToCurrentLocation = true
                )

                Step.DESTINATION -> buildSearchTemplate(
                    title = carContext.getString(R.string.route_destination),
                    hint = carContext.getString(R.string.route_destination_placeholder),
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
                .setHeader(Header.Builder().setTitle(carContext.getString(R.string.cd_route)).setStartHeaderAction(Action.BACK).build())
                .build()
        }
    }

    private fun fetchSuggestions(query: String, isOrigin: Boolean) {
        suggestionJob?.cancel()
        if (query.isBlank()) {
            if (isOrigin) originSuggestions = emptyList() else destinationSuggestions = emptyList()
            invalidate()
            return
        }

        val settings = settingsManager.settings.value
        val historyMatches = settings.routeHistory.filter { it.label.contains(query, ignoreCase = true) }
        val favoriteMatches = settings.favoriteLocations.filter { it.label.contains(query, ignoreCase = true) }
        val local = (favoriteMatches + historyMatches).distinctBy { it.label }

        if (isOrigin) originSuggestions = local else destinationSuggestions = local
        invalidate()

        if (query.length > 2) {
            suggestionJob = lifecycleScope.launch {
                kotlinx.coroutines.delay(500)
                try {
                    val remote = geocodingClient.geocode(
                        query,
                        limit = 5,
                        biasLatitude = settings.lastKnownLat,
                        biasLongitude = settings.lastKnownLon
                    )
                    val merged = (favoriteMatches + historyMatches + remote).distinctBy { it.label }
                    if (isOrigin) originSuggestions = merged else destinationSuggestions = merged
                    invalidate()
                } catch (e: Exception) {
                    // Ignore
                }
            }
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
                    .setIcon(carContext.actionMapIcon())
                    .setTitle(carContext.getString(R.string.action_back))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                onQueryChange(searchText)
                fetchSuggestions(searchText, step == Step.ORIGIN)
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

        val suggestions = if (step == Step.ORIGIN) originSuggestions else destinationSuggestions
        if (query.isNotBlank() && suggestions.isNotEmpty()) {
            val listBuilder = ItemList.Builder()
            val settings = settingsManager.settings.value
            suggestions.take(6).forEach { suggestion ->
                val isHistory = settings.routeHistory.any { it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude }
                val isFavorite = settings.favoriteLocations.any { it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude }

                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(suggestion.label)
                        .setImage(
                            if (isHistory || isFavorite) {
                                carContext.actionHistoryIcon()
                            } else {
                                carContext.actionMapIcon()
                            }
                        )
                        .setOnClickListener {
                            if (step == Step.ORIGIN) {
                                originQuery = suggestion.label
                                originSuggestions = emptyList()
                                step = Step.DESTINATION
                            } else {
                                destinationQuery = suggestion.label
                                destinationSuggestions = emptyList()
                                step = Step.RESULTS
                                compute()
                            }
                            invalidate()
                        }
                        .build()
                )
            }
            builder.setItemList(listBuilder.build())
        } else if (showSkipToCurrentLocation) {
            builder.setItemList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.action_use_current_location))
                            .setOnClickListener {
                                originQuery = ""
                                originSuggestions = emptyList()
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
                    .setNoItemsMessage(carContext.getString(R.string.route_submit_destination))
                    .build()
            )
        }

        return builder.build()
    }

    private fun buildResultsTemplate(): Template {
        lastError?.let { err ->
            return MessageTemplate.Builder(err.take(300))
                .setHeader(Header.Builder().setTitle(carContext.getString(R.string.cd_route)).setStartHeaderAction(Action.BACK).build())
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.action_retry))
                        .setOnClickListener { compute() }
                        .build()
                )
                .build()
        }

        val list = ItemList.Builder()
            .setNoItemsMessage(
                if (loading) carContext.getString(loadingMessageResId)
                else carContext.getString(R.string.poi_no_pois_along_route)
            )

        if (!loading) {
            // Standard Android Auto list limit is 6 items for many templates.
            stations.take(6).forEach { poi ->
                list.addItem(
                    Row.Builder()
                        .setTitle(poi.name.ifBlank { poi.address.ifBlank { carContext.getString(R.string.route_poi_fallback) } })
                        .addText(poi.address.ifBlank { "${poi.latitude}, ${poi.longitude}" })
                        .setOnClickListener {
                            val uri = IntentNavigationHelper.getNavigationUri(poi)
                            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE).apply { data = uri })
                        }
                        .build()
                )
            }
        }

        val actionStripBuilder = ActionStrip.Builder()
        actionStripBuilder.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.action_edit))
                .setIcon(carContext.actionSettingsIcon())
                .setOnClickListener {
                    step = Step.ORIGIN
                    invalidate()
                }
                .build()
        )

        val canShowMap = !loading || currentRoutePoints != null
        if (canShowMap) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_show_on_map))
                    .setIcon(carContext.actionMapIcon())
                    .setOnClickListener { showOnMap() }
                    .build()
            )
        }

        if (!loading && stations.isNotEmpty()) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_start_nav))
                    .setIcon(carContext.actionMapIcon())
                    .setOnClickListener { openExternalDirections() }
                    .build()
            )
        }

        val actionStrip = actionStripBuilder.build()

        val headerBuilder = Header.Builder()
            .setTitle(if (loading) carContext.getString(loadingMessageResId) else carContext.getString(R.string.screen_route_pois))
            .setStartHeaderAction(Action.BACK)

        return ListTemplate.Builder()
            .setHeader(headerBuilder.build())
            .setLoading(loading)
            .setSingleList(list.build())
            .setActionStrip(actionStrip)
            .build()
    }

    private fun showOnMap() {
        val points = currentRoutePoints
        if (points != null) {
            val factory: BorneAvailabilityProviderFactory? = try {
                org.koin.core.context.GlobalContext.get().get()
            } catch (e: Exception) {
                null
            }

            if (factory != null) {
                screenManager.push(
                    CustomMapPoiScreen(
                        carContext = carContext,
                        poiProvider = poiProvider,
                        availabilityProviderFactory = factory,
                        settingsManager = settingsManager,
                        routePlanner = routePlanner,
                        routingClient = routingClient,
                        geocodingClient = geocodingClient,
                        title = carContext.getString(R.string.screen_route_preview),
                        itineraryPoints = points
                    )
                )
            }
            return
        }

        // If points are null, we might still be loading or failed to get them.
        // Try one last time if we have queries.
        lifecycleScope.launch {
            try {
                val originLatLon = if (originQuery.isBlank()) {
                    val loc = LocationHelper.getCurrentLocation(carContext)
                    loc?.let { it.latitude to it.longitude }
                } else {
                    geocodingClient.geocode(originQuery, limit = 1).firstOrNull()?.let { it.latitude to it.longitude }
                }

                val destLatLon = if (initialDestination?.latitude != null && initialDestination.longitude != null) {
                    initialDestination.latitude to initialDestination.longitude
                } else {
                    geocodingClient.geocode(destinationQuery, limit = 1).firstOrNull()?.let { it.latitude to it.longitude }
                }

                if (originLatLon != null && destLatLon != null) {
                    val route = routingClient.getRoute(originLatLon.first, originLatLon.second, destLatLon.first, destLatLon.second)
                    if (route != null) {
                        currentRoutePoints = route.points
                        showOnMap()
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoRoutePlanning", "Failed to show on map", e)
            }
        }
    }

    private fun openExternalDirections() {
        val origin = originQuery.takeIf { it.isNotBlank() } ?: carContext.getString(R.string.network_current_location)
        val dest = destinationQuery
        if (dest.isBlank()) return
        val url = "https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(dest)}&travelmode=driving"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.google.android.apps.maps")
        }
        try {
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            Log.e("AutoRoutePlanning", "startCarApp failed", e)
        }
    }

    private fun compute() {
        computeJob?.cancel()
        computeJob = lifecycleScope.launch {
            loading = true
            loadingMessageResId = R.string.searching_address
            lastError = null
            stations = emptyList()
            invalidate()
            try {
                val originLatLon = if (originQuery.isBlank()) {
                    val loc = LocationHelper.getCurrentLocation(carContext)
                    if (loc == null) throw Exception(carContext.getString(R.string.route_could_not_get_location))
                    loc.latitude to loc.longitude
                } else {
                    val origin = geocodingClient.geocode(originQuery, limit = 1).firstOrNull()
                        ?: throw Exception(carContext.getString(R.string.route_origin_not_found))
                    origin.latitude to origin.longitude
                }

                val destLat: Double
                val destLon: Double
                if (initialDestination?.latitude != null && initialDestination.longitude != null) {
                    destLat = initialDestination.latitude
                    destLon = initialDestination.longitude
                } else {
                    val dest = geocodingClient.geocode(destinationQuery, limit = 1).firstOrNull()
                        ?: throw Exception(carContext.getString(R.string.route_destination_not_found))
                    destLat = dest.latitude
                    destLon = dest.longitude
                }

                loadingMessageResId = R.string.planning_route
                invalidate()

                val route = routingClient.getRoute(originLatLon.first, originLatLon.second, destLat, destLon)
                if (route == null) throw Exception(carContext.getString(R.string.route_no_route_found))
                currentRoutePoints = route.points
                invalidate()

                loadingMessageResId = R.string.finding_stations
                invalidate()

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
                    is NetworkException -> e.message ?: carContext.getString(R.string.network_error)
                    else -> e.message ?: e.toString()
                }
            } finally {
                loading = false
                invalidate()
            }
        }
    }
}
