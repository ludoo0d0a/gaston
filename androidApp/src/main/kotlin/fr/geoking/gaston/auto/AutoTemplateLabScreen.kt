package fr.geoking.gaston.auto

import fr.geoking.gaston.R
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
                .setTitle(carContext.getString(R.string.template_ui_templates))
                .addText(carContext.getString(R.string.template_lab_subtitle_ui))
                .setOnClickListener {
                    screenManager.push(AutoTemplateLabBasicScreen(carContext))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.template_map_nav))
                .addText(carContext.getString(R.string.template_lab_subtitle_map))
                .setOnClickListener {
                    screenManager.push(AutoTemplateLabMapTemplatesScreen(carContext))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.template_app_features))
                .addText(carContext.getString(R.string.template_lab_subtitle_features))
                .setOnClickListener {
                    screenManager.push(AutoTemplateLabFeaturesScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_map_settings))
                .addText("Current mode: ${settingsManager.settings.value.carMapMode.name}")
                .setOnClickListener {
                    settingsManager.setCarMapMode(settingsManager.settings.value.carMapMode.next())
                    invalidate()
                }
                .build()
        )

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.screen_template_lab))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

class AutoTemplateLabBasicScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoTemplateLabBasicScreen") {
        val listBuilder = ItemList.Builder()
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_message_sample)).setOnClickListener { screenManager.push(AutoMessageTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_pane)).setOnClickListener { screenManager.push(AutoPaneTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_grid)).setOnClickListener { screenManager.push(AutoGridTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_long_message)).setOnClickListener { screenManager.push(AutoLongMessageTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_search)).setOnClickListener { screenManager.push(AutoSearchTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_sign_in)).setOnClickListener { screenManager.push(AutoSignInTemplateScreen(carContext)) }.build())

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.template_ui_templates)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

class AutoTemplateLabMapTemplatesScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoTemplateLabMapTemplatesScreen") {
        val listBuilder = ItemList.Builder()
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_navigation)).setOnClickListener { screenManager.push(GuidanceScreen(carContext, fr.geoking.gaston.poi.Poi(id="lab", name="Sample", address = "Sample address", latitude=48.8, longitude=2.3))) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_route_preview_nav)).setOnClickListener { screenManager.push(AutoRoutePreviewNavigationTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_place_list_map_title)).setOnClickListener { screenManager.push(AutoPlaceListMapTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_place_list_nav)).setOnClickListener { screenManager.push(AutoPlaceListNavigationTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.template_tab)).setOnClickListener { screenManager.push(AutoTabTemplateScreen(carContext)) }.build())
        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.map_template_custom_osm)).setOnClickListener { screenManager.push(AutoMapTemplateScreen(carContext)) }.build())

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.template_map_nav)).setStartHeaderAction(Action.BACK).build())
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

        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.map_native_poi)).setOnClickListener {
            val deps = getMapDeps()
            if (deps != null) screenManager.push(NativeMapPoiScreen(carContext, deps.poiProvider, deps.availabilityProviderFactory, settingsManager, deps.communityRepo, deps.favoritesRepo))
        }.build())

        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.map_libre_lab)).setOnClickListener { screenManager.push(AutoLibreMapLabScreen(carContext)) }.build())

        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.map_custom_pan)).setOnClickListener {
            val deps = getMapDeps()
            if (deps != null) screenManager.push(CustomMapPoiScreen(carContext, deps.poiProvider, deps.availabilityProviderFactory, settingsManager, deps.routePlanner, deps.routingClient, deps.tollCalculator, deps.trafficProviderFactory, deps.geocodingClient, deps.communityRepo, deps.favoritesRepo))
        }.build())

        listBuilder.addItem(Row.Builder().setTitle("MapLibre POI map").setOnClickListener {
            val deps = getMapDeps()
            if (deps != null) {
                screenManager.push(
                    MapLibrePoiScreen(
                        carContext,
                        deps.poiProvider,
                        deps.availabilityProviderFactory,
                        settingsManager,
                        deps.routePlanner,
                        deps.routingClient,
                        deps.tollCalculator,
                        deps.trafficProviderFactory,
                        deps.geocodingClient,
                        deps.communityRepo,
                        deps.favoritesRepo,
                    ),
                )
            }
        }.build())

        listBuilder.addItem(Row.Builder().setTitle(carContext.getString(R.string.screen_route_planning)).setOnClickListener {
            val deps = getMapDeps()
            if (deps != null) screenManager.push(AutoRoutePlanningScreen(carContext, deps.routePlanner, deps.routingClient, deps.poiProvider, deps.geocodingClient, settingsManager))
        }.build())

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.template_app_features_header)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
