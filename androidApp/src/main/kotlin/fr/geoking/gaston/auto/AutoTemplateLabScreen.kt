package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps

/**
 * Developer / QA hub to open different Android Auto templates side by side.
 * Split into sub-menus to stay under the 6-item limit for many head units.
 */
class AutoTemplateLabScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoTemplateLabScreen") {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("UI Templates")
                .addText("Message, Pane, Grid, Search, SignIn, Tabs...")
                .setOnClickListener {
                    screenManager.push(AutoTemplateLabBasicScreen(carContext))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map & Nav Templates")
                .addText("NavigationTemplate, RoutePreview, PlaceList...")
                .setOnClickListener {
                    screenManager.push(AutoTemplateLabMapTemplatesScreen(carContext))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("App Feature Samples")
                .addText("Native POI, Custom Map, Route Planning...")
                .setOnClickListener {
                    screenManager.push(AutoTemplateLabFeaturesScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map Settings")
                .addText("Current mode: ${settingsManager.settings.value.carMapMode.name}")
                .setOnClickListener {
                    val next = if (settingsManager.settings.value.carMapMode == fr.geoking.gaston.CarMapMode.Native) fr.geoking.gaston.CarMapMode.Custom else fr.geoking.gaston.CarMapMode.Native
                    settingsManager.setCarMapMode(next)
                    invalidate()
                }
                .build()
        )

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Template lab")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

class AutoTemplateLabBasicScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoTemplateLabBasicScreen") {
        val listBuilder = ItemList.Builder()
        listBuilder.addItem(Row.Builder().setTitle("MessageTemplate").setOnClickListener { screenManager.push(AutoMessageTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("PaneTemplate").setOnClickListener { screenManager.push(AutoPaneTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("GridTemplate").setOnClickListener { screenManager.push(AutoGridTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("LongMessageTemplate").setOnClickListener { screenManager.push(AutoLongMessageTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("SearchTemplate").setOnClickListener { screenManager.push(AutoSearchTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("SignInTemplate").setOnClickListener { screenManager.push(AutoSignInTemplateScreen(carContext)) }.build())

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("UI Templates").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

class AutoTemplateLabMapTemplatesScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoTemplateLabMapTemplatesScreen") {
        val listBuilder = ItemList.Builder()
        listBuilder.addItem(Row.Builder().setTitle("NavigationTemplate").setOnClickListener { screenManager.push(GuidanceScreen(carContext, fr.geoking.gaston.poi.Poi(id="lab", name="Sample", address = "Sample address", latitude=48.8, longitude=2.3))) }.build())
        listBuilder.addItem(Row.Builder().setTitle("RoutePreviewNavigationTemplate").setOnClickListener { screenManager.push(AutoRoutePreviewNavigationTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("PlaceListMapTemplate").setOnClickListener { screenManager.push(AutoPlaceListMapTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("PlaceListNavigationTemplate").setOnClickListener { screenManager.push(AutoPlaceListNavigationTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("TabTemplate").setOnClickListener { screenManager.push(AutoTabTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle("MapTemplate (Custom OSM)").setOnClickListener { screenManager.push(AutoMapTemplateScreen(carContext)) }.build())

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Map & Nav Templates").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

class AutoTemplateLabFeaturesScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoTemplateLabFeaturesScreen") {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(Row.Builder().setTitle("Native map POI").setOnClickListener {
            val deps = getMapDeps()
            if (deps != null) screenManager.push(NativeMapPoiScreen(carContext, deps.poiProvider, deps.availabilityProviderFactory, settingsManager, deps.communityRepo, deps.favoritesRepo))
        }.build())

        listBuilder.addItem(Row.Builder().setTitle("MapLibre (lab)").setOnClickListener { screenManager.push(AutoLibreMapLabScreen(carContext)) }.build())

        listBuilder.addItem(Row.Builder().setTitle("Custom map (pan)").setOnClickListener {
            val deps = getMapDeps()
            if (deps != null) screenManager.push(CustomMapPoiScreen(carContext, deps.poiProvider, deps.availabilityProviderFactory, settingsManager, deps.routePlanner, deps.routingClient, deps.tollCalculator, deps.trafficProviderFactory, deps.geocodingClient, deps.communityRepo, deps.favoritesRepo))
        }.build())

        listBuilder.addItem(Row.Builder().setTitle("Route planning").setOnClickListener {
            val deps = getMapDeps()
            if (deps != null) screenManager.push(AutoRoutePlanningScreen(carContext, deps.routePlanner, deps.routingClient, deps.poiProvider, deps.geocodingClient, settingsManager))
        }.build())

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("App Features").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
