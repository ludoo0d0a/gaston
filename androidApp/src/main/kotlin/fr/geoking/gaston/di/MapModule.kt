package fr.geoking.gaston.di

import org.koin.core.context.GlobalContext

import fr.geoking.gaston.api.belib.BelibAvailabilityClient
import fr.geoking.gaston.api.belib.BelibAvailabilityProvider
import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.chargy.ChargyProvider
import fr.geoking.gaston.api.dkv.DkvOcpiClient
import fr.geoking.gaston.api.dkv.DkvOcpiProvider
import fr.geoking.gaston.api.ecomovement.EcoMovementOcpiClient
import fr.geoking.gaston.api.ecomovement.EcoMovementOcpiProvider
import fr.geoking.gaston.api.fastned.FastnedOcpiClient
import fr.geoking.gaston.api.fastned.FastnedOcpiProvider
import fr.geoking.gaston.api.evpricesfr.EvPricesFrClient
import fr.geoking.gaston.api.datagouv.DataGouvCampingClient
import fr.geoking.gaston.api.datagouv.DataGouvCampingProvider
import fr.geoking.gaston.api.datagouv.DataGouvElecProvider
import fr.geoking.gaston.api.datagouv.DataGouvProvider
import fr.geoking.gaston.api.datagouv.DataGouvPrixCarburantProvider
import fr.geoking.gaston.api.minetur.SpainMineturProvider
import fr.geoking.gaston.api.tankerkoenig.GermanyTankerkoenigProvider
import fr.geoking.gaston.api.econtrol.AustriaEControlProvider
import fr.geoking.gaston.api.argentina.ArgentinaEnergiaProvider
import fr.geoking.gaston.api.australia.AustraliaNswFuelCheckProvider
import fr.geoking.gaston.api.croatia.CroatiaMzoeProvider
import fr.geoking.gaston.api.denmark.FuelpricesDKProvider
import fr.geoking.gaston.api.belgium.BelgiumPetrolPricesClient
import fr.geoking.gaston.api.belgium.BelgiumOfficialProvider
import fr.geoking.gaston.api.dgeg.PortugalDgegProvider
import fr.geoking.gaston.api.finland.PolttoaineProvider
import fr.geoking.gaston.api.fuelo.FueloProvider
import fr.geoking.gaston.api.gas.GasApiClient
import fr.geoking.gaston.api.gas.GasApiProvider
import fr.geoking.gaston.api.greece.GreeceFuelGRProvider
import fr.geoking.gaston.api.ireland.IrelandPickAPumpProvider
import fr.geoking.gaston.api.it.MimitFuelProvider
import fr.geoking.gaston.api.mexico.MexicoCREProvider
import fr.geoking.gaston.api.moldova.MoldovaAnreProvider
import fr.geoking.gaston.api.netherlands.NetherlandsAnwbProvider
import fr.geoking.gaston.api.no.DrivstoffAppenProvider
import fr.geoking.gaston.api.openvan.OpenVanCampClient
import fr.geoking.gaston.api.openvan.OpenVanCampProvider
import fr.geoking.gaston.api.openchargemap.OpenChargeMapClient
import fr.geoking.gaston.api.openchargemap.OpenChargeMapProvider
import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassProvider
import fr.geoking.gaston.api.parking.ParkingProviderFactory
import fr.geoking.gaston.api.routing.OsrmRoutingClient
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.api.routex.RoutexProvider
import fr.geoking.gaston.api.romania.RomaniaPecoProvider
import fr.geoking.gaston.api.serbia.SerbiaNisProvider
import fr.geoking.gaston.api.si.GorivaSiProvider
import fr.geoking.gaston.api.uk.UkCmaFuelProvider
import fr.geoking.gaston.api.geocoding.AdresseDataGouvGeocodingClient
import fr.geoking.gaston.api.geocoding.NominatimGeocodingClient
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.transit.BelgiumTransitProvider
import fr.geoking.gaston.api.transit.FranceTransitProvider
import fr.geoking.gaston.api.transit.LuxembourgTransitProvider
import fr.geoking.gaston.api.traffic.CitaGeoJsonTrafficClient
import fr.geoking.gaston.api.traffic.CitaTrafficProvider
import fr.geoking.gaston.api.traffic.GeographicRegion
import fr.geoking.gaston.api.traffic.TomTomTrafficClient
import fr.geoking.gaston.api.traffic.TomTomTrafficProvider
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.weather.MetNorwayWeatherProvider
import fr.geoking.gaston.api.weather.OpenMeteoGeocodingClient
import fr.geoking.gaston.api.weather.OpenMeteoWeatherProvider
import fr.geoking.gaston.api.weather.WeatherProvider
import fr.geoking.gaston.api.weather.WeatherProviderFactory
import fr.geoking.gaston.api.toll.OpenTollDataParser
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.community.LocalCommunityPoiRepository
import fr.geoking.gaston.community.LocalFavoritesRepository
import fr.geoking.gaston.community.storage.CommunityPoiStorage
import fr.geoking.gaston.community.storage.FavoritePoiStorage
import fr.geoking.gaston.parking.ParkingAggregator
import fr.geoking.gaston.poi.MergedPoiProvider
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.SelectorPoiProvider
import fr.geoking.gaston.transit.TransitAggregator
import fr.geoking.gaston.transit.TransitApiSelector
import fr.geoking.gaston.transit.TransitProvider
import fr.geoking.gaston.toll.TollCalculator
import fr.geoking.gaston.ui.OpenTollDataHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Map-related dependencies (POI, routing, traffic, transit, parking, community, toll).
 * Loaded only when the user opens the map or route planning, so app startup stays light.
 */
val mapModule = module {

    // Map data source: Routex (default), flux instantané prix carburants, gas-api, or quotidien DataGouv.
    single<PoiProvider>(named("routex")) {
        RoutexProvider(get(), radiusKm = 5)
    }
    single<PoiProvider>(named("datagouvprixcarburant")) {
        DataGouvPrixCarburantProvider(get(), radiusKm = 10, limit = 100)
    }
    single<PoiProvider>(named("gasapi")) {
        GasApiProvider(get(), radiusKm = 10, limit = 100)
    }
    single<PoiProvider>(named("ukcma")) {
        UkCmaFuelProvider(get(), radiusKm = 15, limit = 200)
    }
    single<PoiProvider>(named("mimit")) {
        MimitFuelProvider(get(), radiusKm = 15, limit = 200)
    }
    single<PoiProvider>(named("gorivasi")) {
        GorivaSiProvider(get(), radiusKm = 15, limit = 200)
    }
    single<PoiProvider>(named("drivstoffappen")) {
        DrivstoffAppenProvider(get(), country = "Norway", countryIso2 = "NO", radiusKm = 20, limit = 150)
    }
    single<PoiProvider>(named("drivstoffappen_se")) {
        DrivstoffAppenProvider(get(), country = "Sweden", countryIso2 = "SE", radiusKm = 20, limit = 150)
    }
    single<PoiProvider>(named("portugaldgeg")) {
        PortugalDgegProvider(get())
    }
    single<PoiProvider>(named("netherlandsanwb")) {
        NetherlandsAnwbProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("fuelpricesdk")) {
        val sm = get<fr.geoking.gaston.SettingsManager>()
        val key = sm.settings.value.fuelpricesDkKey.ifBlank { fr.geoking.gaston.BuildConfig.FUELPRICES_DK_KEY }
        FuelpricesDKProvider(get(), apiKey = key, radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("fuelo")) {
        FueloProvider(get(), radiusKm = 20, limit = 60)
    }
    single<PoiProvider>(named("nswfuelcheck")) {
        val sm = get<fr.geoking.gaston.SettingsManager>()
        val key = sm.settings.value.nswFuelCheckKey.ifBlank { fr.geoking.gaston.BuildConfig.NSW_FUELCHECK_KEY }
        val secret = sm.settings.value.nswFuelCheckSecret.ifBlank { fr.geoking.gaston.BuildConfig.NSW_FUELCHECK_SECRET }
        AustraliaNswFuelCheckProvider(get(), apiKey = key, apiSecret = secret, radiusKm = 20, limit = 60)
    }
    single<PoiProvider>(named("croatiamzoe")) {
        CroatiaMzoeProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("finlandpolttoaine")) {
        PolttoaineProvider(get(), limit = 40)
    }
    single<PoiProvider>(named("greecefuelgr")) {
        GreeceFuelGRProvider(get(), limit = 60)
    }
    single<PoiProvider>(named("irelandpickapump")) {
        IrelandPickAPumpProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("moldovaanre")) {
        MoldovaAnreProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("romaniapeco")) {
        RomaniaPecoProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("serbianis")) {
        SerbiaNisProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("mexicocre")) {
        MexicoCREProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("argentinaenergia")) {
        ArgentinaEnergiaProvider(get(), radiusKm = 20, limit = 80)
    }
    single<PoiProvider>(named("datagouv")) {
        DataGouvProvider(
            client = get(),
            radiusKm = 10,
            limit = 100,
            gasApiClient = null
        )
    }
    single<PoiProvider>(named("datagouvelec")) {
        DataGouvElecProvider(get(), radiusKm = 10, limit = 100)
    }
    single<OpenChargeMapClient> {
        OpenChargeMapClient(get(), apiKey = get<fr.geoking.gaston.SettingsManager>().settings.value.openChargeMapKey.ifBlank { null })
    }
    single<PoiProvider>(named("openchargemap")) {
        OpenChargeMapProvider(get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider>(named("chargy")) {
        ChargyProvider(get(), radiusKm = 15, limit = 100)
    }
    single { FastnedOcpiClient(get(), apiKey = fr.geoking.gaston.BuildConfig.FASTNED_UK_KEY) }
    single<PoiProvider>(named("fastned")) {
        FastnedOcpiProvider(get(), radiusKm = 10, limit = 100)
    }

    // Free/public France EV tariff baselines (HTML scraping).
    single { EvPricesFrClient(get()) }
    single {
        DkvOcpiClient(
            client = get(),
            subscriptionKey = fr.geoking.gaston.BuildConfig.DKV_SUBSCRIPTION_KEY,
            authorization = fr.geoking.gaston.BuildConfig.DKV_AUTHORIZATION.takeIf { it.isNotBlank() }
        )
    }
    single<PoiProvider>(named("dkv")) {
        DkvOcpiProvider(get(), radiusKm = 10, limit = 150)
    }
    single {
        val sm = get<fr.geoking.gaston.SettingsManager>()
        val key = sm.settings.value.ecoMovementKey.ifBlank { fr.geoking.gaston.BuildConfig.ECO_MOVEMENT_KEY }
        EcoMovementOcpiClient(get(), apiKey = key)
    }
    single<PoiProvider>(named("ecomovement")) {
        val sm = get<fr.geoking.gaston.SettingsManager>()
        val key = sm.settings.value.ecoMovementKey.ifBlank { fr.geoking.gaston.BuildConfig.ECO_MOVEMENT_KEY }
        if (key.isBlank()) {
            object : PoiProvider {
                override fun supportedCategories(): Set<fr.geoking.gaston.poi.PoiCategory> =
                    setOf(fr.geoking.gaston.poi.PoiCategory.Irve)

                override suspend fun getGasStations(
                    latitude: Double,
                    longitude: Double,
                    viewport: fr.geoking.gaston.poi.MapViewport?
                ): List<fr.geoking.gaston.poi.Poi> = emptyList()
            }
        } else {
            EcoMovementOcpiProvider(get(), radiusKm = 10, limit = 150)
        }
    }
    single { OverpassClient(get()) }
    single<PoiProvider>(named("overpass")) {
        OverpassProvider(get(), radiusKm = 5, limit = 100)
    }
    single { OpenVanCampClient(get()) }
    single<PoiProvider>(named("openvancamp")) {
        OpenVanCampProvider(openVanClient = get(), overpassClient = get(), radiusKm = 10, limit = 100)
    }
    single<PoiProvider>(named("spainminetur")) {
        SpainMineturProvider(get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider>(named("germanytankerkoenig")) {
        GermanyTankerkoenigProvider(get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider>(named("austriaecontrol")) {
        AustriaEControlProvider(get(), limit = 50)
    }
    single { BelgiumPetrolPricesClient(get()) }
    single<PoiProvider>(named("belgiumofficial")) {
        BelgiumOfficialProvider(belgiumClient = get(), overpassClient = get(), radiusKm = 10, limit = 50)
    }
    single { DataGouvCampingClient(get()) }
    single<PoiProvider>(named("datagouvcamping")) {
        DataGouvCampingProvider(get(), radiusKm = 15, limit = 50)
    }
    single<PoiProvider>(named("selector")) {
        SelectorPoiProvider(
            routex = get(named("routex")),
            dataGouvPrixCarburant = get(named("datagouvprixcarburant")),
            gasApi = get(named("gasapi")),
            dataGouv = get(named("datagouv")),
            ukCma = get(named("ukcma")),
            italyMimit = get(named("mimit")),
            sloveniaGorivaSi = get(named("gorivasi")),
            norwayDrivstoffAppen = get(named("drivstoffappen")),
            swedenDrivstoffAppen = get(named("drivstoffappen_se")),
            portugalDgeg = get(named("portugaldgeg")),
            netherlandsAnwb = get(named("netherlandsanwb")),
            denmarkFuelpricesDk = get(named("fuelpricesdk")),
            fuelo = get(named("fuelo")),
            australiaNswFuelCheck = get(named("nswfuelcheck")),
            croatiaMzoe = get(named("croatiamzoe")),
            finlandPolttoaine = get(named("finlandpolttoaine")),
            greeceFuelGr = get(named("greecefuelgr")),
            irelandPickAPump = get(named("irelandpickapump")),
            moldovaAnre = get(named("moldovaanre")),
            romaniaPeco = get(named("romaniapeco")),
            serbiaNis = get(named("serbianis")),
            mexicoCre = get(named("mexicocre")),
            argentinaEnergia = get(named("argentinaenergia")),
            dataGouvElec = get(named("datagouvelec")),
            openChargeMap = get(named("openchargemap")),
            chargy = get(named("chargy")),
            fastned = get(named("fastned")),
            dkv = get(named("dkv")),
            ecoMovement = get(named("ecomovement")),
            openVanCamp = get(named("openvancamp")),
            spainMinetur = get(named("spainminetur")),
            germanyTankerkoenig = get(named("germanytankerkoenig")),
            austriaEControl = get(named("austriaecontrol")),
            belgiumOfficial = get(named("belgiumofficial")),
            openVanCampClient = get(),
            overpass = get(named("overpass")),
            dataGouvCamping = get(named("datagouvcamping")),
            settingsManager = get()
        )
    }
    single { CommunityPoiStorage(androidContext()) }
    single { FavoritePoiStorage(androidContext()) }
    single<CommunityPoiRepository> { LocalCommunityPoiRepository(get()) }
    single<FavoritesRepository> { LocalFavoritesRepository(get()) }
    single<PoiProvider> {
        MergedPoiProvider(base = get(named("selector")), communityRepo = get())
    }

    // Borne availability (e.g. Belib Paris): factory returns provider for current location.
    single { BelibAvailabilityClient(get()) }
    single<BorneAvailabilityProvider>(named("belib")) {
        BelibAvailabilityProvider(get(), radiusKm = 10, limit = 200)
    }
    single<BorneAvailabilityProviderFactory> {
        BorneAvailabilityProviderFactory(get(named("belib")))
    }

    // Traffic: Luxembourg CITA GeoJSON first; TomTom incidents as global fallback (needs TOMTOM_KEY).
    single { CitaGeoJsonTrafficClient(get()) }
    single { CitaTrafficProvider(get()) }
    single { TomTomTrafficClient(get()) }
    single { TomTomTrafficProvider(get(), fr.geoking.gaston.BuildConfig.TOMTOM_KEY) }
    single<TrafficProviderFactory> {
        TrafficProviderFactory(
            listOf(
                GeographicRegion.Bbox(49.4, 5.7, 50.2, 6.6) to get<CitaTrafficProvider>(),
                GeographicRegion.Everywhere to get<TomTomTrafficProvider>()
            )
        )
    }

    // Geocoding for weather place names (global; no API key).
    single { OpenMeteoGeocodingClient(get()) }

    // Weather: MET Norway (Nordic), Open-Meteo Meteo-France blend (France + Corsica), Open-Meteo default elsewhere.
    single<WeatherProvider>(named("weather_met_norway")) {
        MetNorwayWeatherProvider(get())
    }
    single<WeatherProvider>(named("weather_open_meteo_fr")) {
        OpenMeteoWeatherProvider(
            get(),
            providerId = "open_meteo_meteofrance",
            models = "meteofrance_seamless"
        )
    }
    single<WeatherProvider>(named("weather_open_meteo")) {
        OpenMeteoWeatherProvider(get(), providerId = "open_meteo", models = null)
    }
    single<WeatherProviderFactory> {
        WeatherProviderFactory(
            listOf(
                GeographicRegion.Bbox(latMin = 55.0, lonMin = -10.0, latMax = 72.0, lonMax = 35.0) to get(named("weather_met_norway")),
                GeographicRegion.Bbox(latMin = 41.0, lonMin = -5.5, latMax = 51.6, lonMax = 10.0) to get(named("weather_open_meteo_fr")),
                GeographicRegion.Everywhere to get(named("weather_open_meteo"))
            )
        )
    }

    single<RoutingClient> { OsrmRoutingClient(get()) }
    single<RoutePlanner> { RoutePlanner(get()) }
    single<GeocodingClient> { NominatimGeocodingClient(get()) }

    // Transit (bus/tram): location-based provider selection (France, Luxembourg, Belgium).
    single<TransitProvider>(named("fr_ratp")) { FranceTransitProvider(get()) }
    single<TransitProvider>(named("lu_mobiliteit")) {
        val sm = get<fr.geoking.gaston.SettingsManager>()
        val key = sm.settings.value.mobiliteitLuxembourgKey.ifBlank { fr.geoking.gaston.BuildConfig.MOBILITEIT_LUXEMBOURG_KEY }
        LuxembourgTransitProvider(get(), key)
    }
    single<TransitProvider>(named("be_stib")) { BelgiumTransitProvider(get()) }
    single<List<TransitProvider>>(named("transitProviders")) {
        listOf(get(named("fr_ratp")), get(named("lu_mobiliteit")), get(named("be_stib")))
    }
    single { TransitApiSelector(get(named("transitProviders"))) }
    single { TransitAggregator(get(named("transitProviders")), get()) }

    // Parking POIs: LiveParking + ParkAPI + OSM, aggregated via factory
    single<ParkingProviderFactory> { ParkingProviderFactory(get(), get()) }
    single<ParkingAggregator> { get<ParkingProviderFactory>().createAggregator() }

    single { OpenTollDataHelper(androidContext()) }
    single<TollCalculator> {
        val settingsManager = get<fr.geoking.gaston.SettingsManager>()
        TollCalculator(dataSource = {
            val path = settingsManager.settings.value.tollDataPath ?: return@TollCalculator null
            val file = java.io.File(path)
            if (!file.exists()) return@TollCalculator null
            OpenTollDataParser.parse(file.readText())
        })
    }
}

/** All map/route dependencies resolved after [MapModuleLoader.ensureLoaded]. */
data class MapDeps(
    val poiProvider: PoiProvider,
    val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    val communityRepo: CommunityPoiRepository,
    val favoritesRepo: FavoritesRepository,
    val trafficProviderFactory: TrafficProviderFactory,
    val weatherProviderFactory: WeatherProviderFactory,
    val routePlanner: RoutePlanner,
    val routingClient: RoutingClient,
    val tollCalculator: TollCalculator,
    val geocodingClient: GeocodingClient
)

/**
 * Loads [mapModule] only when needed (e.g. when user opens the map). Call from a [org.koin.core.component.KoinComponent]
 * before resolving any map-related dependency, or use [ensureLoaded] and then resolve via Koin.
 */
object MapModuleLoader {

    @Volatile
    private var loaded = false

    private val lock = Any()

    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            android.util.Log.d("MapModuleLoader", "Loading map module (first map open)")
            org.koin.core.context.loadKoinModules(mapModule)
            loaded = true
        }
    }
}
