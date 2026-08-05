package fr.geoking.gaston

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.feature.settings.FirestoreSettingsSync
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.sanitizeUserPoiProviderSelection
import fr.geoking.gaston.shared.network.NetworkSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class CarMapMode {
    Native,
    Custom,
    MapLibre;

    fun next(): CarMapMode = entries[(ordinal + 1) % entries.size]
}
enum class MapEngine { Google, MapLibre }
enum class ThemeMode { System, Light, Dark }
enum class MapTheme(val styleUrl: String, val rasterUrl: String, val isDark: Boolean) {
    Dark("https://tiles.openfreemap.org/styles/dark", "https://a.basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png", true),
    Voyager("https://tiles.openfreemap.org/styles/bright", "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png", false),
    Standard("https://tiles.openfreemap.org/styles/liberty", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", false),
    Positron("https://tiles.openfreemap.org/styles/positron", "https://a.basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png", false),
    Fiord("https://tiles.openfreemap.org/styles/fiord", "https://a.basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png", true),
    OsmFr("https://tiles.openfreemap.org/styles/bright", "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png", false),
    Hot("https://tiles.openfreemap.org/styles/bright", "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png", false)
}

/** Energy/fuel types for map POI filter (multi-select). */
val DEFAULT_MAP_ENERGY_TYPES = emptySet<String>()

/** Type d'enseigne: "all", "major", "gms", "independant". */
const val DEFAULT_MAP_ENSEIGNE_TYPE = "all"

/** Power buckets for IRVE filter (kW). Empty = all. */
val DEFAULT_MAP_POWER_LEVELS = emptySet<Int>()

/** IRVE operator filter. Empty = all. */
val DEFAULT_MAP_IRVE_OPERATORS = emptySet<String>()

/** Brand filter. Empty = all. */
val DEFAULT_MAP_BRANDS = emptySet<String>()

/** Default EV range in km for route planning. */
const val DEFAULT_EV_RANGE_KM = 300

val PRELOADED_FAVORITES = listOf(
    GeocodedPlace("Paris", 48.8534, 2.3488),
    GeocodedPlace("London", 51.5085, -0.1257),
    GeocodedPlace("Rome", 41.8947, 12.4811),
    GeocodedPlace("Berlin", 52.5244, 13.4105),
    GeocodedPlace("Madrid", 40.4165, -3.7026),
    GeocodedPlace("Vienna", 48.2064, 16.3707),
    GeocodedPlace("Brussels", 50.8467, 4.3499),
    GeocodedPlace("Amsterdam", 52.3740, 4.8897),
    GeocodedPlace("Bern", 46.9481, 7.4474),
    GeocodedPlace("Luxembourg City", 49.6117, 6.1300),
    GeocodedPlace("Lisbon", 38.7169, -9.1399),
    GeocodedPlace("Oslo", 59.9127, 10.7461),
    GeocodedPlace("Stockholm", 59.3326, 18.0649),
    GeocodedPlace("Copenhagen", 55.6759, 12.5655),
    GeocodedPlace("Helsinki", 60.1692, 24.9402),
    GeocodedPlace("Dublin", 53.3331, -6.2489),
    GeocodedPlace("Athens", 37.9534, 23.7490),
    GeocodedPlace("Ljubljana", 46.0511, 14.5051),
    GeocodedPlace("Zagreb", 45.8144, 15.9780),
    GeocodedPlace("Bucharest", 44.4328, 26.1043),
    GeocodedPlace("Belgrade", 44.8176, 20.4633),
    GeocodedPlace("Chișinău", 47.0056, 28.8575),
    GeocodedPlace("Canberra", -35.2835, 149.1281),
    GeocodedPlace("Mexico City", 19.4273, -99.1419),
    GeocodedPlace("Buenos Aires", -34.6051, -58.4004),
    GeocodedPlace("Washington, D.C.", 38.8951, -77.0364),
    GeocodedPlace("New York", 40.7128, -74.0060),
    GeocodedPlace("Los Angeles", 34.0522, -118.2437),
    GeocodedPlace("Chicago", 41.8781, -87.6298),
    GeocodedPlace("Houston", 29.7604, -95.3698)
)

enum class FuelCard { None, Routex }

enum class PoiProviderSelectionMode { Manual, Auto }

data class AppSettings(
    /** App UI theme preference (affects phone surfaces and map styling). */
    val uiThemeMode: ThemeMode = ThemeMode.System,
    val vehicleBrand: String = "",
    val vehicleModel: String = "",
    val vehicleEnergy: String = "gas", // gas, electric, hybrid
    val vehicleGasTypes: Set<String> = DEFAULT_MAP_ENERGY_TYPES,
    val vehiclePowerLevels: Set<Int> = DEFAULT_MAP_POWER_LEVELS,
    val fuelCard: FuelCard = FuelCard.None,
    val useVehicleFilter: Boolean = false,
    /** When [Auto], provider set is derived from current country (GPS / network). */
    val poiProviderSelectionMode: PoiProviderSelectionMode = PoiProviderSelectionMode.Manual,
    val selectedPoiProviders: Set<PoiProviderType> = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
    val mapEnergyMode: EnergyFilterMode = EnergyFilterMode.Fuel,
    val selectedMapEnergyTypes: Set<String> = DEFAULT_MAP_ENERGY_TYPES,
    val mapEnseigneType: String = DEFAULT_MAP_ENSEIGNE_TYPE,
    val mapBrands: Set<String> = DEFAULT_MAP_BRANDS,
    val selectedMapServices: Set<String> = emptySet(),
    val mapPowerLevels: Set<Int> = DEFAULT_MAP_POWER_LEVELS,
    val mapIrveOperators: Set<String> = DEFAULT_MAP_IRVE_OPERATORS,
    val selectedMapConnectorTypes: Set<String> = emptySet(),
    val mapTrafficEnabled: Boolean = false,
    val debugLoggingEnabled: Boolean = false,
    val disableCache: Boolean = false,
    val mapTileDebugEnabled: Boolean = false,
    val evRangeKm: Int = DEFAULT_EV_RANGE_KM,
    val evConsumptionKwhPer100km: Float? = null,
    val batteryCapacityKwh: Float? = null,
    val gasTankCapacityLiters: Float? = null,
    val gasConsumptionLper100km: Float? = null,
    val selectedOverpassAmenityTypes: Set<String> = emptySet(),
    /** Amenities to keep fetching in the background after the user leaves Other mode (cache warming). */
    val cacheWarmAmenityTypes: Set<String> = emptySet(),
    val phoneMapEngine: MapEngine = MapEngine.Google,
    val mapTheme: MapTheme = MapTheme.Voyager,
    val mapThemeMode: ThemeMode = ThemeMode.System,
    val vehicleType: VehicleType = VehicleType.Car,
    val carMapMode: CarMapMode = CarMapMode.Native,
    val googleUserName: String? = null,
    val isLoggedIn: Boolean = false,
    val tollDataPath: String? = null,
    val routeHistory: List<GeocodedPlace> = emptyList(),
    val favoriteLocations: List<GeocodedPlace> = emptyList(),
    val isPremium: Boolean = false,
    /** Dev/test override: unlock premium features without a subscription. */
    val devSimulatePremium: Boolean = false,
    val routeStationSearchRadiusMeters: Int = 2000,
    val filterOnlyHighwayStations: Boolean = false,
    val lastKnownLat: Double? = null,
    val lastKnownLon: Double? = null,
    val lastAcceptedDisclaimerVersion: Int = 0,
    val lastCountryCode: String? = null,
    val lastCountryName: String? = null,
    val lastOperatorName: String? = null,
    val lastIsConnected: Boolean = false,
    val lastIsRoaming: Boolean = false,
) {
    val hasPremiumFeatures: Boolean get() = isPremium || devSimulatePremium
}

open class SettingsManager(
    context: Context,
    private val firestoreSync: FirestoreSettingsSync? = null
) : NetworkSettings {
    // Keep legacy name so existing installs keep settings.
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_ai_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(loadSettings())
    open val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun triggerPullAndMerge() {
        scope.launch {
            val remoteMerged = firestoreSync?.downloadAndMerge(_settings.value)
            if (remoteMerged != null && remoteMerged != _settings.value) {
                saveSettingsInternal(remoteMerged, upload = false)
            }
        }
    }

    private fun loadSettings(): AppSettings {
        val routeHistoryJson = prefs.getString("route_history", null)
        val routeHistory = try {
            if (routeHistoryJson.isNullOrBlank()) emptyList() else Json.decodeFromString<List<GeocodedPlace>>(routeHistoryJson)
        } catch (_: Exception) {
            emptyList()
        }

        val favoriteLocations = run {
            val storedJson = prefs.getString("favorite_locations", null)
            val stored = try {
                if (storedJson.isNullOrBlank()) emptyList() else Json.decodeFromString<List<GeocodedPlace>>(storedJson)
            } catch (_: Exception) {
                emptyList()
            }

            if (!prefs.getBoolean("favorites_preloaded_v1", false)) {
                val preloaded = PRELOADED_FAVORITES
                val merged = (stored + preloaded).distinctBy { "${it.latitude},${it.longitude}" }
                prefs.edit()
                    .putBoolean("favorites_preloaded_v1", true)
                    .putString("favorite_locations", Json.encodeToString(merged))
                    .apply()
                merged
            } else {
                stored
            }
        }

        val selectedProviders = run {
            val stored = prefs.getStringSet("poi_providers", null)?.mapNotNull {
                try { PoiProviderType.valueOf(it) } catch (_: Exception) { null }
            }?.toSet()
            val base = (stored ?: setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass))
                .sanitizeUserPoiProviderSelection()
            // Older builds merged Overpass at runtime without persisting it; keep that once in prefs.
            if (!prefs.getBoolean("poi_providers_overpass_migrated_v1", false)) {
                val merged = base + PoiProviderType.Overpass
                prefs.edit()
                    .putBoolean("poi_providers_overpass_migrated_v1", true)
                    .putStringSet("poi_providers", merged.map { it.name }.toSet())
                    .apply()
                merged
            } else {
                base
            }
        }

        fun readIntSet(key: String, fallback: Set<Int>): Set<Int> =
            prefs.getStringSet(key, null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: fallback

        val phoneMapEngine = try {
            MapEngine.valueOf(prefs.getString("phone_map_engine", MapEngine.Google.name) ?: MapEngine.Google.name)
        } catch (_: Exception) { MapEngine.Google }

        val mapTheme = try {
            MapTheme.valueOf(prefs.getString("map_theme", MapTheme.Voyager.name) ?: MapTheme.Voyager.name)
        } catch (_: Exception) { MapTheme.Voyager }

        val carMapMode = try {
            CarMapMode.valueOf(prefs.getString("car_map_mode", CarMapMode.Native.name) ?: CarMapMode.Native.name)
        } catch (_: Exception) { CarMapMode.Native }

        val vehicleType = try {
            VehicleType.valueOf(prefs.getString("vehicle_type", VehicleType.Car.name) ?: VehicleType.Car.name)
        } catch (_: Exception) { VehicleType.Car }

        val fuelCard = try {
            FuelCard.valueOf(prefs.getString("fuel_card", FuelCard.None.name) ?: FuelCard.None.name)
        } catch (_: Exception) { FuelCard.None }

        val poiProviderSelectionMode = try {
            PoiProviderSelectionMode.valueOf(
                prefs.getString("poi_provider_selection_mode", PoiProviderSelectionMode.Manual.name)
                    ?: PoiProviderSelectionMode.Manual.name
            )
        } catch (_: Exception) { PoiProviderSelectionMode.Manual }

        val uiThemeMode = try {
            ThemeMode.valueOf(prefs.getString("ui_theme_mode", ThemeMode.System.name) ?: ThemeMode.System.name)
        } catch (_: Exception) { ThemeMode.System }

        val mapThemeMode = try {
            ThemeMode.valueOf(prefs.getString("map_theme_mode", ThemeMode.System.name) ?: ThemeMode.System.name)
        } catch (_: Exception) { ThemeMode.System }

        val selectedMapEnergyTypes = prefs.getStringSet("map_energy_types", null)?.toSet() ?: DEFAULT_MAP_ENERGY_TYPES

        val mapEnergyMode = try {
            val stored = prefs.getString("map_energy_mode", null)
            if (stored != null) {
                EnergyFilterMode.valueOf(stored)
            } else {
                // Infer from legacy selectedMapEnergyTypes
                val hasElectric = selectedMapEnergyTypes.contains("electric")
                val hasFuel = selectedMapEnergyTypes.any { it != "electric" }
                when {
                    hasElectric && hasFuel -> EnergyFilterMode.Hybrid
                    hasElectric -> EnergyFilterMode.Electric
                    else -> EnergyFilterMode.Fuel
                }
            }
        } catch (_: Exception) { EnergyFilterMode.Fuel }

        return AppSettings(
            uiThemeMode = uiThemeMode,
            vehicleBrand = prefs.getString("vehicle_brand", "") ?: "",
            vehicleModel = prefs.getString("vehicle_model", "") ?: "",
            vehicleEnergy = prefs.getString("vehicle_energy", "gas") ?: "gas",
            vehicleGasTypes = prefs.getStringSet("vehicle_gas_types", null)?.toSet() ?: DEFAULT_MAP_ENERGY_TYPES,
            vehiclePowerLevels = readIntSet("vehicle_power_levels", DEFAULT_MAP_POWER_LEVELS),
            fuelCard = fuelCard,
            useVehicleFilter = prefs.getBoolean("use_vehicle_filter", false),
            poiProviderSelectionMode = poiProviderSelectionMode,
            selectedPoiProviders = selectedProviders,
            mapEnergyMode = mapEnergyMode,
            selectedMapEnergyTypes = selectedMapEnergyTypes,
            mapEnseigneType = prefs.getString("map_enseigne_type", DEFAULT_MAP_ENSEIGNE_TYPE) ?: DEFAULT_MAP_ENSEIGNE_TYPE,
            mapBrands = prefs.getStringSet("map_brands", null)?.toSet() ?: DEFAULT_MAP_BRANDS,
            selectedMapServices = prefs.getStringSet("map_services", null)?.toSet() ?: emptySet(),
            mapPowerLevels = readIntSet("map_power_levels", DEFAULT_MAP_POWER_LEVELS),
            mapIrveOperators = prefs.getStringSet("map_irve_operators", null)?.toSet() ?: DEFAULT_MAP_IRVE_OPERATORS,
            selectedMapConnectorTypes = prefs.getStringSet("map_connector_types", null)?.toSet() ?: emptySet(),
            mapTrafficEnabled = prefs.getBoolean("map_traffic_enabled", false),
            debugLoggingEnabled = prefs.getBoolean("debug_logging_enabled", false),
            disableCache = prefs.getBoolean("disable_cache", false),
            mapTileDebugEnabled = prefs.getBoolean("map_tile_debug_enabled", false),
            evRangeKm = prefs.getInt("ev_range_km", DEFAULT_EV_RANGE_KM),
            evConsumptionKwhPer100km = sanitizeConsumption(prefs.getString("ev_consumption_kwh_per_100km", null)?.toFloatOrNull()),
            batteryCapacityKwh = prefs.getString("battery_capacity_kwh", null)?.toFloatOrNull(),
            gasTankCapacityLiters = prefs.getString("gas_tank_capacity_liters", null)?.toFloatOrNull(),
            gasConsumptionLper100km = sanitizeConsumption(prefs.getString("gas_consumption_l_per_100km", null)?.toFloatOrNull()),
            selectedOverpassAmenityTypes = prefs.getStringSet("overpass_amenity_types", null)?.toSet()
                ?: emptySet(),
            cacheWarmAmenityTypes = prefs.getStringSet("cache_warm_amenity_types", null)?.toSet()
                ?: emptySet(),
            phoneMapEngine = phoneMapEngine,
            mapTheme = mapTheme,
            mapThemeMode = mapThemeMode,
            vehicleType = vehicleType,
            carMapMode = carMapMode,
            googleUserName = prefs.getString("google_user_name", null),
            isLoggedIn = prefs.getBoolean("is_logged_in", false),
            tollDataPath = prefs.getString("toll_data_path", null),
            routeHistory = routeHistory,
            favoriteLocations = favoriteLocations,
            isPremium = prefs.getBoolean("is_premium", false),
            devSimulatePremium = prefs.getBoolean("dev_simulate_premium", false),
            routeStationSearchRadiusMeters = prefs.getInt("route_station_radius_m", 2000),
            filterOnlyHighwayStations = prefs.getBoolean("filter_only_highway", false),
            lastKnownLat = prefs.getString("last_known_lat", null)?.toDoubleOrNull(),
            lastKnownLon = prefs.getString("last_known_lon", null)?.toDoubleOrNull(),
            lastAcceptedDisclaimerVersion = prefs.getInt("last_accepted_disclaimer_version", 0),
            lastCountryCode = prefs.getString("last_country_code", null),
            lastCountryName = prefs.getString("last_country_name", null),
            lastOperatorName = prefs.getString("last_operator_name", null),
            lastIsConnected = prefs.getBoolean("last_is_connected", false),
            lastIsRoaming = prefs.getBoolean("last_is_roaming", false),
        )
    }

    open fun saveSettings(settings: AppSettings) {
        saveSettingsInternal(settings, upload = true)
    }

    open fun saveSettingsWithThemeCheck(settings: AppSettings) {
        // Kept for compatibility with existing UI calls.
        saveSettings(settings)
    }

    private fun saveSettingsInternal(settings: AppSettings, upload: Boolean) {
        val sanitized = settings.copy(
            evConsumptionKwhPer100km = sanitizeConsumption(settings.evConsumptionKwhPer100km),
            gasConsumptionLper100km = sanitizeConsumption(settings.gasConsumptionLper100km)
        )
        _settings.value = sanitized
        prefs.edit()
            .putString("ui_theme_mode", sanitized.uiThemeMode.name)
            .putString("map_theme_mode", sanitized.mapThemeMode.name)
            .putString("vehicle_brand", settings.vehicleBrand)
            .putString("vehicle_model", settings.vehicleModel)
            .putString("vehicle_energy", settings.vehicleEnergy)
            .putStringSet("vehicle_gas_types", settings.vehicleGasTypes)
            .putStringSet("vehicle_power_levels", settings.vehiclePowerLevels.map { it.toString() }.toSet())
            .putString("fuel_card", settings.fuelCard.name)
            .putBoolean("use_vehicle_filter", settings.useVehicleFilter)
            .putString("poi_provider_selection_mode", settings.poiProviderSelectionMode.name)
            .putStringSet("poi_providers", settings.selectedPoiProviders.map { it.name }.toSet())
            .putString("map_energy_mode", settings.mapEnergyMode.name)
            .putStringSet("map_energy_types", settings.selectedMapEnergyTypes)
            .putString("map_enseigne_type", settings.mapEnseigneType)
            .putStringSet("map_brands", settings.mapBrands)
            .putStringSet("map_services", settings.selectedMapServices)
            .putStringSet("map_power_levels", settings.mapPowerLevels.map { it.toString() }.toSet())
            .putStringSet("map_irve_operators", settings.mapIrveOperators)
            .putStringSet("map_connector_types", settings.selectedMapConnectorTypes)
            .putBoolean("map_traffic_enabled", settings.mapTrafficEnabled)
            .putBoolean("debug_logging_enabled", settings.debugLoggingEnabled)
            .putBoolean("disable_cache", settings.disableCache)
            .putBoolean("map_tile_debug_enabled", settings.mapTileDebugEnabled)
            .putInt("ev_range_km", sanitized.evRangeKm)
            .putString("ev_consumption_kwh_per_100km", sanitized.evConsumptionKwhPer100km?.toString())
            .putString("battery_capacity_kwh", sanitized.batteryCapacityKwh?.toString())
            .putString("gas_tank_capacity_liters", sanitized.gasTankCapacityLiters?.toString())
            .putString("gas_consumption_l_per_100km", sanitized.gasConsumptionLper100km?.toString())
            .putStringSet("overpass_amenity_types", settings.selectedOverpassAmenityTypes)
            .putStringSet("cache_warm_amenity_types", settings.cacheWarmAmenityTypes)
            .putString("phone_map_engine", settings.phoneMapEngine.name)
            .putString("map_theme", settings.mapTheme.name)
            .putString("vehicle_type", settings.vehicleType.name)
            .putString("car_map_mode", settings.carMapMode.name)
            .putString("google_user_name", sanitized.googleUserName)
            .putBoolean("is_logged_in", sanitized.isLoggedIn)
            .putString("toll_data_path", sanitized.tollDataPath)
            .putString("route_history", Json.encodeToString(sanitized.routeHistory))
            .putString("favorite_locations", Json.encodeToString(sanitized.favoriteLocations))
            .putBoolean("is_premium", sanitized.isPremium)
            .putBoolean("dev_simulate_premium", sanitized.devSimulatePremium)
            .putInt("route_station_radius_m", sanitized.routeStationSearchRadiusMeters)
            .putBoolean("filter_only_highway", sanitized.filterOnlyHighwayStations)
            .putString("last_known_lat", sanitized.lastKnownLat?.toString())
            .putString("last_known_lon", sanitized.lastKnownLon?.toString())
            .putInt("last_accepted_disclaimer_version", sanitized.lastAcceptedDisclaimerVersion)
            .putString("last_country_code", sanitized.lastCountryCode)
            .putString("last_country_name", sanitized.lastCountryName)
            .putString("last_operator_name", sanitized.lastOperatorName)
            .putBoolean("last_is_connected", sanitized.lastIsConnected)
            .putBoolean("last_is_roaming", sanitized.lastIsRoaming)
            .apply()

        if (upload) {
            scope.launch { firestoreSync?.uploadSettings(sanitized) }
        }
    }

    // Convenience setters used across phone + Android Auto screens
    open fun setPoiProviderSelectionMode(mode: PoiProviderSelectionMode) {
        saveSettings(_settings.value.copy(poiProviderSelectionMode = mode))
    }

    open fun setPoiProviderTypes(types: Set<PoiProviderType>) {
        saveSettings(_settings.value.copy(selectedPoiProviders = types.sanitizeUserPoiProviderSelection()))
    }

    open fun setUseVehicleFilter(enabled: Boolean) {
        saveSettings(_settings.value.copy(useVehicleFilter = enabled))
    }

    open fun setPhoneMapEngine(engine: MapEngine) {
        saveSettings(_settings.value.copy(phoneMapEngine = engine))
    }

    open fun setCarMapMode(mode: CarMapMode) {
        saveSettings(_settings.value.copy(carMapMode = mode))
    }

    open fun setMapTheme(theme: MapTheme) {
        saveSettings(_settings.value.copy(mapTheme = theme))
    }

    open fun setMapTrafficEnabled(enabled: Boolean) {
        saveSettings(_settings.value.copy(mapTrafficEnabled = enabled))
    }

    open fun setDisableCache(disabled: Boolean) {
        saveSettings(_settings.value.copy(disableCache = disabled))
    }

    open fun setMapTileDebugEnabled(enabled: Boolean) {
        saveSettings(_settings.value.copy(mapTileDebugEnabled = enabled))
    }

    open fun setSelectedMapEnergyTypes(types: Set<String>) {
        val filtered = types.filter { it != "electric" }.toSet()
        val currentMode = _settings.value.mapEnergyMode
        val nextMode = if (currentMode == EnergyFilterMode.Electric) EnergyFilterMode.Fuel else currentMode
        saveSettings(_settings.value.copy(
            selectedMapEnergyTypes = filtered,
            mapEnergyMode = nextMode,
            useVehicleFilter = false
        ))
    }

    // Backwards-compatible name used by various UI screens
    open fun setMapEnergyTypes(types: Set<String>) = setSelectedMapEnergyTypes(types)

    open fun setEnergyFilterMode(mode: EnergyFilterMode) {
        val current = _settings.value
        val leavingOther = current.poiProviderSelectionMode == PoiProviderSelectionMode.Manual &&
            current.selectedPoiProviders == setOf(PoiProviderType.Overpass)
        saveSettings(
            current.copy(
                useVehicleFilter = false,
                poiProviderSelectionMode = if (leavingOther) {
                    PoiProviderSelectionMode.Auto
                } else {
                    current.poiProviderSelectionMode
                },
                mapEnergyMode = mode,
                selectedOverpassAmenityTypes = if (leavingOther) emptySet() else current.selectedOverpassAmenityTypes,
                cacheWarmAmenityTypes = if (leavingOther) {
                    current.cacheWarmAmenityTypes + current.selectedOverpassAmenityTypes
                } else {
                    current.cacheWarmAmenityTypes
                },
            )
        )
    }

    open fun setMyVehicleMode() {
        val current = _settings.value
        val leavingOther = current.poiProviderSelectionMode == PoiProviderSelectionMode.Manual &&
            current.selectedPoiProviders == setOf(PoiProviderType.Overpass)
        saveSettings(
            current.copy(
                useVehicleFilter = true,
                poiProviderSelectionMode = if (leavingOther) {
                    PoiProviderSelectionMode.Auto
                } else {
                    current.poiProviderSelectionMode
                },
                selectedOverpassAmenityTypes = if (leavingOther) emptySet() else current.selectedOverpassAmenityTypes,
                cacheWarmAmenityTypes = if (leavingOther) {
                    current.cacheWarmAmenityTypes + current.selectedOverpassAmenityTypes
                } else {
                    current.cacheWarmAmenityTypes
                },
            )
        )
    }

    open fun setOtherMode(amenityType: String = "parking") {
        val current = _settings.value
        saveSettings(
            current.copy(
                useVehicleFilter = false,
                poiProviderSelectionMode = PoiProviderSelectionMode.Manual,
                selectedPoiProviders = setOf(PoiProviderType.Overpass),
                selectedOverpassAmenityTypes = setOf(amenityType),
                cacheWarmAmenityTypes = current.cacheWarmAmenityTypes + amenityType,
            )
        )
    }

    open fun setMapEnseigneType(type: String) {
        saveSettings(_settings.value.copy(mapEnseigneType = type))
    }

    open fun setMapBrands(brands: Set<String>) {
        saveSettings(_settings.value.copy(mapBrands = brands))
    }

    open fun setSelectedMapServices(services: Set<String>) {
        saveSettings(_settings.value.copy(selectedMapServices = services))
    }

    // Backwards-compatible name used by Android Auto screens
    open fun setMapServices(services: Set<String>) = setSelectedMapServices(services)

    open fun setMapPowerLevels(levels: Set<Int>) {
        val currentMode = _settings.value.mapEnergyMode
        val nextMode = if (currentMode == EnergyFilterMode.Fuel) EnergyFilterMode.Electric else currentMode
        saveSettings(_settings.value.copy(
            mapPowerLevels = levels,
            mapEnergyMode = nextMode,
            useVehicleFilter = false
        ))
    }

    open fun setMapIrveOperators(ops: Set<String>) {
        saveSettings(_settings.value.copy(mapIrveOperators = ops))
    }

    open fun setSelectedMapConnectorTypes(types: Set<String>) {
        saveSettings(_settings.value.copy(selectedMapConnectorTypes = types))
    }

    // Backwards-compatible name used by Android Auto / phone filter screens
    open fun setMapConnectorTypes(types: Set<String>) = setSelectedMapConnectorTypes(types)

    open fun setFuelCard(card: FuelCard) {
        saveSettings(_settings.value.copy(fuelCard = card))
    }

    open fun setEvRangeKm(km: Int) {
        saveSettings(_settings.value.copy(evRangeKm = km))
    }

    private fun sanitizeConsumption(value: Float?): Float? {
        if (value == null) return null
        val rounded = (value * 10).roundToInt() / 10f
        return rounded.coerceIn(1.0f, 99.0f)
    }

    open fun setEvConsumptionKwhPer100km(value: Float?) {
        saveSettings(_settings.value.copy(evConsumptionKwhPer100km = sanitizeConsumption(value)))
    }

    open fun setBatteryCapacityKwh(value: Float?) {
        saveSettings(_settings.value.copy(batteryCapacityKwh = value))
    }

    open fun setVehicleBrand(value: String) {
        saveSettings(_settings.value.copy(vehicleBrand = value))
    }

    open fun setVehicleModel(value: String) {
        saveSettings(_settings.value.copy(vehicleModel = value))
    }

    open fun setGasTankCapacityLiters(value: Float?) {
        saveSettings(_settings.value.copy(gasTankCapacityLiters = value))
    }

    open fun setGasConsumptionLper100km(value: Float?) {
        saveSettings(_settings.value.copy(gasConsumptionLper100km = sanitizeConsumption(value)))
    }

    open fun setRouteStationSearchRadiusMeters(value: Int) {
        saveSettings(_settings.value.copy(routeStationSearchRadiusMeters = value))
    }

    open fun setFilterOnlyHighwayStations(enabled: Boolean) {
        saveSettings(_settings.value.copy(filterOnlyHighwayStations = enabled))
    }

    open fun saveLastKnownLocation(lat: Double, lon: Double) {
        if (_settings.value.lastKnownLat != lat || _settings.value.lastKnownLon != lon) {
            saveSettings(_settings.value.copy(lastKnownLat = lat, lastKnownLon = lon))
        }
    }

    open fun setVehicleType(type: VehicleType) {
        saveSettings(_settings.value.copy(vehicleType = type))
    }

    open fun setPremium(premium: Boolean) {
        if (_settings.value.isPremium != premium) {
            saveSettings(_settings.value.copy(isPremium = premium))
        }
    }

    open fun setDevSimulatePremium(enabled: Boolean) {
        if (_settings.value.devSimulatePremium != enabled) {
            saveSettings(_settings.value.copy(devSimulatePremium = enabled))
        }
    }

    open fun setOverpassAmenityTypes(types: Set<String>) {
        val current = _settings.value
        saveSettings(
            current.copy(
                selectedOverpassAmenityTypes = types,
                cacheWarmAmenityTypes = current.cacheWarmAmenityTypes + types,
            )
        )
    }

    open fun togglePoiProviderType(type: PoiProviderType) {
        val current = _settings.value.selectedPoiProviders
        val next = if (type in current) current - type else current + type
        setPoiProviderTypes(next)
    }

    open fun addRouteHistory(place: GeocodedPlace) {
        val current = _settings.value.routeHistory
        val deduped = (listOf(place) + current.filterNot { it == place }).distinct()
        saveSettings(_settings.value.copy(routeHistory = deduped.take(10)))
    }

    open fun toggleFavoriteLocation(place: GeocodedPlace) {
        val current = _settings.value.favoriteLocations
        val exists = current.any { it.latitude == place.latitude && it.longitude == place.longitude }
        val next = if (exists) {
            current.filterNot { it.latitude == place.latitude && it.longitude == place.longitude }
        } else {
            current + place
        }
        saveSettings(_settings.value.copy(favoriteLocations = next))
    }

    /**
     * Local-only user rating for a POI. Not synced to Firestore (personal preference).
     * Stored as a JSON map in shared preferences.
     */
    open fun getPoiRating(poiId: String): Int {
        val raw = prefs.getString("poi_ratings", null) ?: return 0
        val map = try {
            Json.decodeFromString<Map<String, Int>>(raw)
        } catch (_: Exception) {
            return 0
        }
        return map[poiId] ?: 0
    }

    override var lastCountryCode: String?
        get() = _settings.value.lastCountryCode
        set(value) {
            if (_settings.value.lastCountryCode != value) {
                saveSettings(_settings.value.copy(lastCountryCode = value))
            }
        }

    override var lastCountryName: String?
        get() = _settings.value.lastCountryName
        set(value) {
            if (_settings.value.lastCountryName != value) {
                saveSettings(_settings.value.copy(lastCountryName = value))
            }
        }

    override var lastOperatorName: String?
        get() = _settings.value.lastOperatorName
        set(value) {
            if (_settings.value.lastOperatorName != value) {
                saveSettings(_settings.value.copy(lastOperatorName = value))
            }
        }

    override var lastIsConnected: Boolean
        get() = _settings.value.lastIsConnected
        set(value) {
            if (_settings.value.lastIsConnected != value) {
                saveSettings(_settings.value.copy(lastIsConnected = value))
            }
        }

    override var lastIsRoaming: Boolean
        get() = _settings.value.lastIsRoaming
        set(value) {
            if (_settings.value.lastIsRoaming != value) {
                saveSettings(_settings.value.copy(lastIsRoaming = value))
            }
        }

    open fun setPoiRating(poiId: String, rating: Int) {
        val raw = prefs.getString("poi_ratings", null)
        val map = try {
            if (raw.isNullOrBlank()) emptyMap() else Json.decodeFromString<Map<String, Int>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }.toMutableMap()

        if (rating <= 0) map.remove(poiId) else map[poiId] = rating.coerceIn(1, 5)

        // Keep the prefs small: cap to 200 entries (arbitrary, user-level).
        val capped = map.entries.take(200).associate { it.key to it.value }
        prefs.edit().putString("poi_ratings", Json.encodeToString(capped)).apply()
    }
}

