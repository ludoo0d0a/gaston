package fr.geoking.gaston

import android.content.Context
import android.content.SharedPreferences
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.feature.settings.FirestoreSettingsSync
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.sanitizeUserPoiProviderSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class CarMapMode { Native, Custom }
enum class MapEngine { Google, MapLibre }
enum class ThemeMode { System, Light, Dark }
enum class MapTheme(val styleUrl: String) {
    Dark("https://tiles.openfreemap.org/styles/dark"),
    Modern("https://tiles.openfreemap.org/styles/bright"),
    Standard("https://tiles.openfreemap.org/styles/liberty")
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
    val evRangeKm: Int = DEFAULT_EV_RANGE_KM,
    val evConsumptionKwhPer100km: Float? = null,
    val batteryCapacityKwh: Float? = null,
    val gasTankCapacityLiters: Float? = null,
    val gasConsumptionLper100km: Float? = null,
    val openChargeMapKey: String = "",
    /** Eco-Movement OCPI Data API key (Authorization: Token ...). */
    val ecoMovementKey: String = "",
    /** Fuelprices.dk API key (Denmark). */
    val fuelpricesDkKey: String = "",
    /** NSW FuelCheck API key (Australia). */
    val nswFuelCheckKey: String = "",
    /** NSW FuelCheck API secret (Australia). */
    val nswFuelCheckSecret: String = "",
    val selectedOverpassAmenityTypes: Set<String> = emptySet(),
    val phoneMapEngine: MapEngine = MapEngine.Google,
    val mapTheme: MapTheme = MapTheme.Dark,
    val vehicleType: VehicleType = VehicleType.Car,
    val carMapMode: CarMapMode = CarMapMode.Native,
    val googleUserName: String? = null,
    val isLoggedIn: Boolean = false,
    val tollDataPath: String? = null,
    val mobiliteitLuxembourgKey: String = "",
    val routeHistory: List<GeocodedPlace> = emptyList(),
    val favoriteLocations: List<GeocodedPlace> = emptyList(),
    val isPremium: Boolean = false,
    val routeStationSearchRadiusMeters: Int = 2000,
    val filterOnlyHighwayStations: Boolean = false,
    val lastKnownLat: Double? = null,
    val lastKnownLon: Double? = null
)

open class SettingsManager(
    context: Context,
    private val firestoreSync: FirestoreSettingsSync? = null
) {
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
        val mobiliteitLuxembourgKey =
            prefs.getString("mobiliteit_luxembourg_key", "")?.takeIf { it.isNotEmpty() }
                ?: BuildConfig.MOBILITEIT_LUXEMBOURG_KEY

        val openChargeMapKey =
            prefs.getString("openchargemap_key", "")?.takeIf { it.isNotBlank() }
                ?: BuildConfig.OPENCHARGEMAP_KEY

        val ecoMovementKey =
            prefs.getString("eco_movement_key", "")?.takeIf { it.isNotBlank() }
                ?: BuildConfig.ECO_MOVEMENT_KEY

        val fuelpricesDkKey =
            prefs.getString("fuelprices_dk_key", "")?.takeIf { it.isNotBlank() }
                ?: BuildConfig.FUELPRICES_DK_KEY

        val nswFuelCheckKey =
            prefs.getString("nsw_fuelcheck_key", "")?.takeIf { it.isNotBlank() }
                ?: BuildConfig.NSW_FUELCHECK_KEY

        val nswFuelCheckSecret =
            prefs.getString("nsw_fuelcheck_secret", "")?.takeIf { it.isNotBlank() }
                ?: BuildConfig.NSW_FUELCHECK_SECRET

        val routeHistoryJson = prefs.getString("route_history", null)
        val routeHistory = try {
            if (routeHistoryJson.isNullOrBlank()) emptyList() else Json.decodeFromString<List<GeocodedPlace>>(routeHistoryJson)
        } catch (_: Exception) {
            emptyList()
        }

        val favoriteLocationsJson = prefs.getString("favorite_locations", null)
        val favoriteLocations = try {
            if (favoriteLocationsJson.isNullOrBlank()) emptyList() else Json.decodeFromString<List<GeocodedPlace>>(favoriteLocationsJson)
        } catch (_: Exception) {
            emptyList()
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
            MapTheme.valueOf(prefs.getString("map_theme", MapTheme.Dark.name) ?: MapTheme.Dark.name)
        } catch (_: Exception) { MapTheme.Dark }

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
            evRangeKm = prefs.getInt("ev_range_km", DEFAULT_EV_RANGE_KM),
            evConsumptionKwhPer100km = prefs.getString("ev_consumption_kwh_per_100km", null)?.toFloatOrNull(),
            batteryCapacityKwh = prefs.getString("battery_capacity_kwh", null)?.toFloatOrNull(),
            gasTankCapacityLiters = prefs.getString("gas_tank_capacity_liters", null)?.toFloatOrNull(),
            gasConsumptionLper100km = prefs.getString("gas_consumption_l_per_100km", null)?.toFloatOrNull(),
            openChargeMapKey = openChargeMapKey,
            ecoMovementKey = ecoMovementKey,
            fuelpricesDkKey = fuelpricesDkKey,
            nswFuelCheckKey = nswFuelCheckKey,
            nswFuelCheckSecret = nswFuelCheckSecret,
            selectedOverpassAmenityTypes = prefs.getStringSet("overpass_amenity_types", null)?.toSet()
                ?: emptySet(),
            phoneMapEngine = phoneMapEngine,
            mapTheme = mapTheme,
            vehicleType = vehicleType,
            carMapMode = carMapMode,
            googleUserName = prefs.getString("google_user_name", null),
            isLoggedIn = prefs.getBoolean("is_logged_in", false),
            tollDataPath = prefs.getString("toll_data_path", null),
            mobiliteitLuxembourgKey = mobiliteitLuxembourgKey,
            routeHistory = routeHistory,
            favoriteLocations = favoriteLocations,
            isPremium = prefs.getBoolean("is_premium", false),
            routeStationSearchRadiusMeters = prefs.getInt("route_station_radius_m", 2000),
            filterOnlyHighwayStations = prefs.getBoolean("filter_only_highway", false),
            lastKnownLat = prefs.getString("last_known_lat", null)?.toDoubleOrNull(),
            lastKnownLon = prefs.getString("last_known_lon", null)?.toDoubleOrNull()
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
        _settings.value = settings
        prefs.edit()
            .putString("ui_theme_mode", settings.uiThemeMode.name)
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
            .putInt("ev_range_km", settings.evRangeKm)
            .putString("ev_consumption_kwh_per_100km", settings.evConsumptionKwhPer100km?.toString())
            .putString("battery_capacity_kwh", settings.batteryCapacityKwh?.toString())
            .putString("gas_tank_capacity_liters", settings.gasTankCapacityLiters?.toString())
            .putString("gas_consumption_l_per_100km", settings.gasConsumptionLper100km?.toString())
            .putString("openchargemap_key", settings.openChargeMapKey)
            .putString("eco_movement_key", settings.ecoMovementKey)
            .putString("fuelprices_dk_key", settings.fuelpricesDkKey)
            .putString("nsw_fuelcheck_key", settings.nswFuelCheckKey)
            .putString("nsw_fuelcheck_secret", settings.nswFuelCheckSecret)
            .putStringSet("overpass_amenity_types", settings.selectedOverpassAmenityTypes)
            .putString("phone_map_engine", settings.phoneMapEngine.name)
            .putString("map_theme", settings.mapTheme.name)
            .putString("vehicle_type", settings.vehicleType.name)
            .putString("car_map_mode", settings.carMapMode.name)
            .putString("google_user_name", settings.googleUserName)
            .putBoolean("is_logged_in", settings.isLoggedIn)
            .putString("toll_data_path", settings.tollDataPath)
            .putString("mobiliteit_luxembourg_key", settings.mobiliteitLuxembourgKey)
            .putString("route_history", Json.encodeToString(settings.routeHistory))
            .putString("favorite_locations", Json.encodeToString(settings.favoriteLocations))
            .putBoolean("is_premium", settings.isPremium)
            .putInt("route_station_radius_m", settings.routeStationSearchRadiusMeters)
            .putBoolean("filter_only_highway", settings.filterOnlyHighwayStations)
            .putString("last_known_lat", settings.lastKnownLat?.toString())
            .putString("last_known_lon", settings.lastKnownLon?.toString())
            .apply()

        if (upload) {
            scope.launch { firestoreSync?.uploadSettings(settings) }
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

    open fun setSelectedMapEnergyTypes(types: Set<String>) {
        val filtered = types.filter { it != "electric" }.toSet()
        val currentMode = _settings.value.mapEnergyMode
        val nextMode = if (currentMode == EnergyFilterMode.Electric) EnergyFilterMode.Fuel else currentMode

        // If we were in "Parking only" mode (set by dashboard shortcut), reset to Auto providers and clear amenities.
        val currentAmenities = _settings.value.selectedOverpassAmenityTypes
        val nextAmenities = if (currentAmenities == setOf("parking")) emptySet() else currentAmenities
        val nextProviderMode = if (currentAmenities == setOf("parking")) PoiProviderSelectionMode.Auto else _settings.value.poiProviderSelectionMode

        saveSettings(_settings.value.copy(
            selectedMapEnergyTypes = filtered,
            mapEnergyMode = nextMode,
            useVehicleFilter = false,
            selectedOverpassAmenityTypes = nextAmenities,
            poiProviderSelectionMode = nextProviderMode
        ))
    }

    // Backwards-compatible name used by various UI screens
    open fun setMapEnergyTypes(types: Set<String>) = setSelectedMapEnergyTypes(types)

    open fun setEnergyFilterMode(mode: EnergyFilterMode) {
        // If we were in "Parking only" mode (set by dashboard shortcut), clear amenities.
        val currentAmenities = _settings.value.selectedOverpassAmenityTypes
        val nextAmenities = if (currentAmenities == setOf("parking")) emptySet() else currentAmenities

        saveSettings(_settings.value.copy(
            useVehicleFilter = false,
            poiProviderSelectionMode = PoiProviderSelectionMode.Auto,
            mapEnergyMode = mode,
            selectedOverpassAmenityTypes = nextAmenities
        ))
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

        // If we were in "Parking only" mode (set by dashboard shortcut), reset to Auto providers and clear amenities.
        val currentAmenities = _settings.value.selectedOverpassAmenityTypes
        val nextAmenities = if (currentAmenities == setOf("parking")) emptySet() else currentAmenities
        val nextProviderMode = if (currentAmenities == setOf("parking")) PoiProviderSelectionMode.Auto else _settings.value.poiProviderSelectionMode

        saveSettings(_settings.value.copy(
            mapPowerLevels = levels,
            mapEnergyMode = nextMode,
            useVehicleFilter = false,
            selectedOverpassAmenityTypes = nextAmenities,
            poiProviderSelectionMode = nextProviderMode
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

    open fun setEvConsumptionKwhPer100km(value: Float?) {
        saveSettings(_settings.value.copy(evConsumptionKwhPer100km = value))
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
        saveSettings(_settings.value.copy(gasConsumptionLper100km = value))
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

    open fun setOverpassAmenityTypes(types: Set<String>) {
        saveSettings(_settings.value.copy(selectedOverpassAmenityTypes = types))
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

