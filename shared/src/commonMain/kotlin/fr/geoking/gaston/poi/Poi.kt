package fr.geoking.gaston.poi

import fr.geoking.gaston.api.routex.PoiAmenities
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.parking.ParkingRegion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * Category of POI for unified search. Extensible: add new values and wire providers as needed.
 * Used by [PoiSearchRequest] and [Poi.poiCategory].
 */
@Serializable
enum class EnergyFilterMode { Fuel, Electric, Hybrid }

@Serializable
enum class PoiCategory {
    /** Fuel / gas stations (Routex, Etalab, GasApi, DataGouv). */
    Gas,
    /** EV charging / IRVE (DataGouvElec, OpenChargeMap). */
    Irve,
    /** Public toilets (e.g. Overpass amenity=toilets). */
    Toilet,
    /** Drinking water / fountains (e.g. Overpass amenity=drinking_water). */
    DrinkingWater,
    /** Camp sites (OSM tourism=camp_site). */
    Camping,
    /** Caravan / motorhome aires (OSM tourism=caravan_site; data.gouv.fr aires). */
    CaravanSite,
    /** Picnic areas (OSM tourism=picnic_site). */
    PicnicSite,
    /** Truck stops (OSM amenity=truck_stop). */
    TruckStop,
    /** Rest areas (OSM highway=rest_area). */
    RestArea,
    /** Restaurants (OSM amenity=restaurant). */
    Restaurant,
    /** Fast food (OSM amenity=fast_food). */
    FastFood,
    /** Radars / speed cameras (OSM highway=speed_camera). */
    Radar,
    /** Parking lots (OSM amenity=parking). */
    Parking,
    /** Points of interest with a view (OSM tourism=viewpoint). */
    Viewpoint,
    /** Battery swap station (OSM charging_station:battery_swapping=yes). */
    BatterySwap,
    /** Mailboxes (OSM amenity=post_box). */
    PostBox,
    /** Water body / lake / pond (OSM natural=water). */
    WaterBody,
    /** Cafe (OSM amenity=cafe). */
    Cafe,
    /** Supermarket / convenience store (OSM shop=supermarket / shop=convenience). */
    Supermarket;
    companion object {
        /** OSM amenity tag value for this category, when applicable. */
        fun fromOsmAmenity(amenity: String): PoiCategory? = when (amenity) {
            "fuel" -> Gas
            "charging_station" -> Irve
            "toilets" -> Toilet
            "drinking_water" -> DrinkingWater
            "truck_stop" -> TruckStop
            "restaurant" -> Restaurant
            "fast_food" -> FastFood
            "parking" -> Parking
            "post_box" -> PostBox
            "cafe" -> Cafe
            else -> null
        }
        /** OSM tourism tag value for this category. */
        fun fromOsmTourism(tourism: String): PoiCategory? = when (tourism) {
            "camp_site" -> Camping
            "caravan_site" -> CaravanSite
            "picnic_site" -> PicnicSite
            "viewpoint" -> Viewpoint
            else -> null
        }
        /** OSM highway tag value for this category (e.g. rest_area). */
        fun fromOsmHighway(highway: String): PoiCategory? = when (highway) {
            "rest_area" -> RestArea
            "speed_camera" -> Radar
            else -> null
        }
        /** Resolve category from OSM tags (amenity, tourism, highway). */
        fun fromOsmTags(tags: Map<String, String>): PoiCategory? {
            if (tags["charging_station:battery_swapping"] == "yes" || tags["battery_swap"] == "yes") {
                return BatterySwap
            }
            // Brand-based battery swap detection
            val brand = tags["brand"]?.lowercase() ?: ""
            if (brand.contains("nio") || brand.contains("gogoro") || brand.contains("zeway") || brand.contains("ample")) {
                return BatterySwap
            }

            tags["amenity"]?.let { fromOsmAmenity(it) }?.let { return it }
            tags["tourism"]?.let { fromOsmTourism(it) }?.let { return it }
            tags["highway"]?.let { fromOsmHighway(it) }?.let { return it }
            tags["natural"]?.let { if (it == "water") return WaterBody }
            tags["shop"]?.let { if (it == "supermarket" || it == "convenience") return Supermarket }
            return null
        }
    }
}

/**
 * POI data source. [providesFuel] / [providesElectric] classify providers for UI (e.g. filter mode), not OSM extras.
 */
@Serializable
enum class PoiProviderType(
    val providesFuel: Boolean = false,
    val providesElectric: Boolean = false,
    val providesSwap: Boolean = false,
) {
    Routex(providesFuel = true),
    Etalab(providesFuel = true),
    GasApi(providesFuel = true),
    DataGouv(providesFuel = true),
    /** UK interim fuel price open data scheme (CMA / Fuel Finder retailer feeds). */
    UkCma(providesFuel = true),
    /** Italy MIMIT open data (pipe-delimited CSV exports). */
    ItalyMimit(providesFuel = true),
    /** Slovenia goriva.si public REST API. */
    SloveniaGorivaSi(providesFuel = true),
    /** Norway DrivstoffAppen public API (real-time). */
    NorwayDrivstoffAppen(providesFuel = true),
    /** Sweden DrivstoffAppen / bensinpriser.nu community API (real-time). */
    SwedenDrivstoffAppen(providesFuel = true),
    /** Portugal official fuel prices (DGEG). */
    PortugalDgeg(providesFuel = true),
    /** Netherlands (and nearby) fuel prices via ANWB POI API. */
    NetherlandsAnwb(providesFuel = true),
    /** Denmark fuel prices via Fuelprices.dk (API key required). */
    DenmarkFuelpricesDk(providesFuel = true),
    /** Multi-country fuel station scraper via Fuelo.net. */
    Fuelo(providesFuel = true),
    /** Australia NSW FuelCheck API (API key + secret required). */
    AustraliaNswFuelCheck(providesFuel = true),
    /** Croatia MZOE dataset (mzoe-gor.hr). */
    CroatiaMzoe(providesFuel = true),
    /** Finland polttoaine.net prices (HTML scraping). */
    FinlandPolttoaine(providesFuel = true),
    /** Greece fuelgr.gr prices (nearby query). */
    GreeceFuelGr(providesFuel = true),
    /** Ireland Pick A Pump API. */
    IrelandPickAPump(providesFuel = true),
    /** Moldova ANRE public API (ecarburanti.anre.md). */
    MoldovaAnre(providesFuel = true),
    /** Romania Peco Online station prices. */
    RomaniaPeco(providesFuel = true),
    /** Serbia NIS stations + cenagoriva.rs prices. */
    SerbiaNis(providesFuel = true),
    /** Mexico CRE places + prices open data. */
    MexicoCre(providesFuel = true),
    /** Argentina Secretaría de Energía open data. */
    ArgentinaEnergia(providesFuel = true),
    /** Switzerland fuel prices via Comparis.ch (__NEXT_DATA__). */
    SwitzerlandComparis(providesFuel = true),
    /** Western Australia FuelWatch open API. */
    AustraliaFuelWatch(providesFuel = true),
    /** Australia-wide fuel prices via PetrolSpy. */
    AustraliaPetrolSpy(providesFuel = true),
    DataGouvElec(providesElectric = true),
    OpenChargeMap(providesElectric = true),
    Chargy(providesElectric = true),
    /** Fastned UK Open Data API (OCPI 2.2.1). */
    Fastned(providesElectric = true),
    /** DKV Mobility API portal (OCPI via Azure APIM). */
    Dkv(providesElectric = true),
    /** Eco-Movement OCPI Data API (CPO 2.2.1). */
    EcoMovement(providesElectric = true),
    /** Luxembourg OSM fuel + OpenVan.camp weekly reference prices (CC BY 4.0). */
    OpenVanCamp(providesFuel = true),
    /** Spanish government fuel prices (Minetur). */
    SpainMinetur(providesFuel = true),
    /** German fuel prices via Tankerkönig (MTS-K). */
    GermanyTankerkoenig(providesFuel = true),
    /** Austrian fuel prices via E-Control. */
    AustriaEControl(providesFuel = true),
    /** Belgian official maximum fuel prices. */
    BelgiumOfficial(providesFuel = true),
    /** US state-level weekly retail fuel prices (EIA petroleum/pri/gnd) + OSM stations. */
    UsaEia(providesFuel = true),
    Overpass(providesFuel = true, providesElectric = true, providesSwap = true),
    Hybrid(providesFuel = true, providesElectric = true),
}

private val POI_DATA_SOURCES_DISABLED_FOR_USER_SELECTION: Set<PoiProviderType> = emptySet()

/** True if this source is shown in map / Auto POI data source pickers. */
fun PoiProviderType.isUserSelectablePoiDataSource(): Boolean =
    this !in POI_DATA_SOURCES_DISABLED_FOR_USER_SELECTION

/**
 * Ensures user selection is valid.
 */
fun Set<PoiProviderType>.sanitizeUserPoiProviderSelection(): Set<PoiProviderType> =
    this.filter { it.isUserSelectablePoiDataSource() }.toSet()

/** True if any selected provider can supply fuel POIs (for filter / mode chips). */
fun Iterable<PoiProviderType>.anyProvidesFuel(): Boolean = any { it.providesFuel }

/** True if any selected provider can supply electric / IRVE POIs. */
fun Iterable<PoiProviderType>.anyProvidesElectric(): Boolean = any { it.providesElectric }

/** True if any selected provider can supply battery swap POIs. */
fun Iterable<PoiProviderType>.anyProvidesSwap(): Boolean = any { it.providesSwap }

/**
 * IRVE-only details: connector types, tarification (free text), opening hours, payment, etc.
 * Used when [Poi.isElectric] and data comes from data.gouv.fr IRVE.
 */
@Serializable
data class IrveDetails(
    /** Connector type ids: "type_2", "combo_ccs", "chademo", "ef", "autre". */
    val connectorTypes: Set<String> = emptySet(),
    /** Free-text tarification; display as-is. */
    val tarification: String? = null,
    val gratuit: Boolean? = null,
    val openingHours: String? = null,
    val reservation: Boolean? = null,
    val paymentActe: Boolean? = null,
    val paymentCb: Boolean? = null,
    val paymentAutre: Boolean? = null,
    /** "Accès libre" / "Accès réservé". */
    val conditionAcces: String? = null,
    /** Real-time availability: number of free connectors. */
    val availableConnectors: Int? = null,
    /** Real-time availability: total number of connectors. */
    val totalConnectors: Int? = null
)

/**
 * Restaurant/fast food details from OSM (Overpass): opening hours, cuisine, brand.
 * Used when [Poi.poiCategory] is Restaurant or FastFood and data comes from Overpass.
 */
@Serializable
data class RestaurantDetails(
    val openingHours: String? = null,
    val cuisine: String? = null,
    val brand: String? = null,
    val isFastFood: Boolean = false
)

/**
 * Fuel type and price at a gas station (e.g. from data.gouv.fr / gas-api.ovh).
 */
@Serializable
data class FuelPrice(
    val fuelName: String,
    val price: Double,
    val updatedAt: String? = null,
    val outOfStock: Boolean = false
)

@Serializable
data class Poi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    /** True for IRVE / EV charging stations (e.g. data.gouv.fr IRVE). */
    val isElectric: Boolean = false,
    /** Unified category (toilet, drinking water, gas, irve). Inferred from [isElectric] when null. */
    val poiCategory: PoiCategory? = null,
    /** Nominal power in kW (IRVE only). Used for min-power filter. */
    val powerKw: Double? = null,
    /** Operator name (IRVE only). Used for operator filter. */
    val operator: String? = null,
    /** True when station is on highway/autoroute (IRVE only). */
    val isOnHighway: Boolean = false,
    /** Number of charging points / points de charge (IRVE only). */
    val chargePointCount: Int? = null,
    /** When provided by the provider (e.g. DataGouv), lists fuel types and prices. */
    val fuelPrices: List<FuelPrice>? = null,
    /** Site name (e.g. Routex site_name) for title. */
    val siteName: String? = null,
    /** Additional categories for this POI (e.g. when a gas station is also a parking). */
    val extraCategories: Set<PoiCategory> = emptySet(),
    val isClosed: Boolean = false,
    /** Price consistency rating (0.0 to 10.0). */
    val priceRating: Double? = null,
    val postcode: String? = null,
    val addressLocal: String? = null,
    val countryLocal: String? = null,
    val townLocal: String? = null,
    /** Amenities and opening hours for fullscreen details (from Routex, Overpass, etc.). */
    val amenities: PoiAmenities? = null,
    /** IRVE-only: connector types, tarification, horaires, payment, etc. */
    val irveDetails: IrveDetails? = null,
    /** Restaurant/fast food only: opening hours, cuisine, brand (e.g. from Overpass). */
    val restaurantDetails: RestaurantDetails? = null,
    /** The source of the POI data (e.g. "Routex", "DataGouv", "Chargy"). */
    val source: String? = null,
    /** Latest price update timestamp per source. */
    val sourceUpdates: Map<String, String>? = null
)

/**
 * Optional map viewport to scope the POI search to the visible area (e.g. for Routex API).
 * When provided, radius is derived from zoom and map size instead of a fixed default.
 */
data class MapViewport(
    val zoom: Float,
    val mapWidthPx: Int,
    val mapHeightPx: Int,
    val minLat: Double? = null,
    val maxLat: Double? = null,
    val minLng: Double? = null,
    val maxLng: Double? = null
) {
    /** True if this viewport contains the given coordinates. */
    fun contains(lat: Double, lng: Double): Boolean {
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) return true
        return lat in minLat..maxLat && lng in minLng..maxLng
    }
}

/**
 * Computes the search radius in km that covers the visible map area from center, zoom and size.
 * Uses Web Mercator: at zoom z the world is 256*2^z pixels wide; latitude scale varies with cos(lat).
 * Returns half the diagonal of the visible rectangle in km, so the API scope matches the view.
 * We use ceil to ensure the radius fully covers the viewport corners.
 */
fun radiusKmFromMapViewport(
    centerLat: Double,
    centerLng: Double,
    zoom: Float,
    mapWidthPx: Int,
    mapHeightPx: Int
): Int {
    val z = zoom.toDouble().coerceIn(0.0, 24.0)
    val scale = 256.0 * 2.0.pow(z)
    val latRad = centerLat * PI / 180.0
    val cosLat = cos(latRad).coerceIn(0.01, 1.0)
    // Visible span in degrees (Web Mercator)
    val halfLngDeg = (mapWidthPx / 2.0) * 360.0 / scale
    val halfLatDeg = (mapHeightPx / 2.0) * 360.0 * cosLat / scale
    // Convert to km at center latitude (1° lat ≈ 111 km, 1° lng ≈ 111*cos(lat) km)
    val halfLngKm = halfLngDeg * 111.0 * cosLat
    val halfLatKm = halfLatDeg * 111.0
    val radiusKm = sqrt(halfLngKm * halfLngKm + halfLatKm * halfLatKm)
    return ceil(radiusKm).toInt().coerceAtLeast(1)
}

/**
 * Calculates the bounding box for a given map viewport.
 */
fun calculateBoundsFromMapViewport(
    centerLat: Double,
    centerLng: Double,
    zoom: Float,
    mapWidthPx: Int,
    mapHeightPx: Int
): MapViewport {
    val z = zoom.toDouble().coerceIn(0.0, 24.0)
    val scale = 256.0 * 2.0.pow(z)
    val latRad = centerLat * PI / 180.0
    val cosLat = cos(latRad).coerceIn(0.01, 1.0)

    val halfLngDeg = (mapWidthPx / 2.0) * 360.0 / scale
    val halfLatDeg = (mapHeightPx / 2.0) * 360.0 * cosLat / scale

    return MapViewport(
        zoom = zoom,
        mapWidthPx = mapWidthPx,
        mapHeightPx = mapHeightPx,
        minLat = centerLat - halfLatDeg,
        maxLat = centerLat + halfLatDeg,
        minLng = centerLng - halfLngDeg,
        maxLng = centerLng + halfLngDeg
    )
}

/**
 * Unified POI search request. Used by [PoiProvider.search] for gas, IRVE, toilets, water, etc.
 * Empty [categories] means "all categories supported by the provider" (provider-specific default).
 */
data class PoiSearchRequest(
    val latitude: Double,
    val longitude: Double,
    val viewport: MapViewport? = null,
    /** Requested POI categories. Empty = provider default (e.g. Gas+Irve for fuel providers). */
    val categories: Set<PoiCategory> = emptySet(),
    /** When true, the provider should skip in-memory filtering (e.g. brands) and return raw results. */
    val skipFilters: Boolean = false
)

/**
 * Result of a POI search, containing the list of POIs and any errors encountered during the search.
 */
data class PoiSearchResult(
    val pois: List<Poi> = emptyList(),
    val errors: List<PoiProviderError> = emptyList()
)

/**
 * Error information from a specific POI provider.
 */
data class PoiProviderError(
    val providerName: String,
    val message: String,
    val httpCode: Int? = null,
    val isCritical: Boolean = false
)

/**
 * Maps API fuel names (data.gouv / prix instantané / gas-api.ovh) to filter ids used in map settings.
 * Aligned with [prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/) fuel list.
 */
object MapPoiFilter {
    /** Normalize API fuel name to a filter id (gazole, sp98, sp95, sp95_e10, gplc, e85). Returns null if unknown. */
    fun fuelNameToId(fuelName: String): String? {
        val n = fuelName.trim().lowercase()
        return when {
            n.contains("gazole") || n == "gasoil" || n == "diesel" -> "gazole"
            n.contains("sp98") || n == "sp 98" -> "sp98"
            n.contains("e10") || n.contains("sp95-e10") || n == "sp95 e10" || n.contains("sp95") || n == "sp 95" -> "sp95"
            n.contains("gpl") || n == "gplc" || n == "lpg" -> "gplc"
            n.contains("e85") || n == "superéthanol" -> "e85"
            else -> null
        }
    }

    /**
     * Returns true if [poi] should be shown given [mode] and [selectedFuelIds].
     */
    fun matchesEnergyFilter(
        poi: Poi,
        mode: EnergyFilterMode,
        selectedFuelIds: Set<String>
    ): Boolean {
        val wantElectric = mode == EnergyFilterMode.Electric || mode == EnergyFilterMode.Hybrid
        val wantFuel = mode == EnergyFilterMode.Fuel || mode == EnergyFilterMode.Hybrid

        return if (poi.isElectric) {
            wantElectric
        } else {
            if (!wantFuel) return false

            // If we have specific fuel filters, hide stations that have ONLY non-matching fuels.
            // However, don't hide stations that don't have any price information (to avoid empty maps).
            if (selectedFuelIds.isNotEmpty()) {
                val prices = poi.fuelPrices
                if (!prices.isNullOrEmpty()) {
                    val stationFuelIds = prices.mapNotNull { fuelNameToId(it.fuelName) }.toSet()
                    if (stationFuelIds.intersect(selectedFuelIds).isEmpty()) {
                        return false
                    }
                }
            }
            true
        }
    }

    /**
     * Returns true if [poi] should be shown given [selectedEnergyIds] (legacy).
     */
    fun matchesEnergyFilter(poi: Poi, selectedEnergyIds: Set<String>): Boolean {
        if (selectedEnergyIds.isEmpty()) return true

        val wantElectric = "electric" in selectedEnergyIds
        val wantFuel = selectedEnergyIds.any { it != "electric" }

        return if (poi.isElectric) {
            wantElectric
        } else {
            if (!wantFuel) return false
            val fuelFilters = selectedEnergyIds.filter { it != "electric" }.toSet()
            if (fuelFilters.isNotEmpty()) {
                val prices = poi.fuelPrices
                if (!prices.isNullOrEmpty()) {
                    val stationFuelIds = prices.mapNotNull { fuelNameToId(it.fuelName) }.toSet()
                    if (stationFuelIds.intersect(fuelFilters).isEmpty()) {
                        return false
                    }
                }
            }
            true
        }
    }

    /** Returns true if [powerKw] falls into any of the selected [levels] buckets. */
    fun powerMatchesAnyLevel(powerKw: Double, levels: Set<Int>): Boolean =
        levels.any { level ->
            when (level) {
                0 -> true
                20 -> powerKw in 20.0..49.9
                50 -> powerKw in 50.0..99.9
                100 -> powerKw in 100.0..199.9
                200 -> powerKw in 200.0..299.9
                300 -> powerKw >= 300.0
                else -> powerKw >= level
            }
        }

    /**
     * Filters [pois] to only include the cheapest stations based on [selectedFuelIds].
     * Includes ties (e.g. if the 5th and 6th cheapest have the same price, both are included).
     * In Luxembourg, all stations with prices are returned if [isLuxembourg] is true.
     */
    fun filterCheapest(
        pois: List<Poi>,
        selectedFuelIds: Set<String>,
        isLuxembourg: Boolean
    ): List<Poi> {
        val pricedPois = pois.mapNotNull { poi ->
            val minPrice = poi.fuelPrices?.filter { !it.outOfStock && fuelNameToId(it.fuelName) in selectedFuelIds }
                ?.minOfOrNull { it.price }
            if (minPrice != null) Pair(poi, minPrice) else null
        }.sortedBy { it.second }

        if (pricedPois.isEmpty()) return emptyList()

        if (isLuxembourg) {
            return pricedPois.map { it.first }
        }

        // We want at least top 5 stations, but including all ties for the 5th price.
        if (pricedPois.size <= 5) return pricedPois.map { it.first }

        val maxPrice = pricedPois[4].second
        return pricedPois.filter { it.second <= maxPrice }.map { it.first }
    }

    /**
     * Sorts [pois] by price (if [sortByPrice] and fuels selected) or distance from [lat]/[lon].
     */
    fun sortPois(
        pois: List<Poi>,
        lat: Double,
        lon: Double,
        sortByPrice: Boolean,
        selectedFuelIds: Set<String>
    ): List<Poi> {
        return if (sortByPrice && selectedFuelIds.isNotEmpty()) {
            pois.sortedWith { a, b ->
                val pricesA = a.fuelPrices?.filter { fuelNameToId(it.fuelName) in selectedFuelIds }
                val pricesB = b.fuelPrices?.filter { fuelNameToId(it.fuelName) in selectedFuelIds }

                val priceA = pricesA?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE
                val priceB = pricesB?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE

                if (priceA != priceB && (priceA != Double.MAX_VALUE || priceB != Double.MAX_VALUE)) {
                    priceA.compareTo(priceB)
                } else {
                    val distA = approxDistanceKm(lat, lon, a.latitude, a.longitude)
                    val distB = approxDistanceKm(lat, lon, b.latitude, b.longitude)
                    distA.compareTo(distB)
                }
            }
        } else {
            pois.sortedBy { approxDistanceKm(lat, lon, it.latitude, it.longitude) }
        }
    }
}

/**
 * Configuration rules for when a POI provider should be used.
 * Supports location checking by country codes or by a circular geofence.
 */
data class PoiProviderRules(
    val countries: Set<String>? = null,
    val circleCenter: Pair<Double, Double>? = null,
    val circleRadiusKm: Double? = null
) {
    fun isSatisfiedBy(latitude: Double, longitude: Double, viewport: MapViewport? = null): Boolean {
        if (circleCenter != null && circleRadiusKm != null) {
            val effectiveRadiusKm = viewport?.let { v ->
                radiusKmFromMapViewport(latitude, longitude, v.zoom, v.mapWidthPx, v.mapHeightPx).coerceIn(1, 50)
            } ?: 15
            val dist = haversineKm(latitude, longitude, circleCenter.first, circleCenter.second)
            if (dist > circleRadiusKm + effectiveRadiusKm) {
                return false
            }
        }

        if (!countries.isNullOrEmpty()) {
            val queryCountries = if (viewport != null) {
                val minLat = viewport.minLat ?: (latitude - 0.2)
                val maxLat = viewport.maxLat ?: (latitude + 0.2)
                val minLng = viewport.minLng ?: (longitude - 0.2)
                val maxLng = viewport.maxLng ?: (longitude + 0.2)
                ParkingRegion.allInViewport(minLat, maxLat, minLng, maxLng).map { it.countryCode }
            } else {
                val containing = ParkingRegion.allContaining(latitude, longitude).map { it.countryCode }
                containing.ifEmpty {
                    ParkingRegion.entries.filter { region ->
                        region.subBoxes.any { box ->
                            box.distanceToKm(latitude, longitude) <= 10.0
                        }
                    }.map { it.countryCode }
                }
            }

            val upperAllowed = countries.map { it.uppercase() }.toSet()
            val upperQuery = queryCountries.map { it.uppercase() }.toSet()
            if (upperQuery.isNotEmpty() && upperQuery.intersect(upperAllowed).isEmpty()) {
                return false
            }
        }

        return true
    }
}

/**
 * Unified POI provider: supports [search] by [PoiCategory] and optional legacy [getGasStations].
 * New providers implement [search] and [supportedCategories]; [getGasStations] is for backward compatibility.
 */
interface PoiProvider {
    /** Configuration rules defining where/when this provider is allowed to be used. */
    val usageRules: PoiProviderRules? get() = null

    /** Evaluates whether this provider should be queried based on the request parameters. */
    fun shouldQuery(latitude: Double, longitude: Double, viewport: MapViewport? = null): Boolean {
        return usageRules?.isSatisfiedBy(latitude, longitude, viewport) ?: true
    }

    /** Categories this provider can return. Used by the selector to build [PoiSearchRequest]. */
    fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    /**
     * Unified search: returns a [Flow] that emits [PoiSearchResult]s as they become available.
     * Default implementation emits the result of [searchResult].
     */
    fun searchFlow(request: PoiSearchRequest): Flow<PoiSearchResult> = flow {
        if (!shouldQuery(request.latitude, request.longitude, request.viewport)) {
            emit(PoiSearchResult())
            return@flow
        }
        emit(searchResult(request))
    }

    /**
     * Unified search: returns a [PoiSearchResult] containing the list of POIs and any errors encountered.
     * Default implementation delegates to [getGasStations] and filters by category intersection.
     */
    suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
        if (!shouldQuery(request.latitude, request.longitude, request.viewport)) {
            return PoiSearchResult()
        }
        return try {
            val pois = search(request)
            PoiSearchResult(pois = pois)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val code = (e as? fr.geoking.gaston.shared.network.NetworkException)?.httpCode
            PoiSearchResult(
                errors = listOf(
                    PoiProviderError(
                        providerName = this::class.simpleName ?: "Unknown Provider",
                        message = e.message ?: "Unknown error",
                        httpCode = code
                    )
                )
            )
        }
    }

    /**
     * Unified search: returns POIs for the requested [request.categories] (or provider default if empty).
     * Default implementation delegates to [getGasStations] and filters by category intersection.
     */
    suspend fun search(request: PoiSearchRequest): List<Poi> {
        if (!shouldQuery(request.latitude, request.longitude, request.viewport)) {
            return emptyList()
        }
        val cat = request.categories
        val supported = supportedCategories()
        val overlap = if (cat.isEmpty()) supported else cat.intersect(supported)
        if (overlap.isEmpty() || (PoiCategory.Gas !in overlap && PoiCategory.Irve !in overlap && PoiCategory.BatterySwap !in overlap)) {
            return emptyList()
        }
        val list = getGasStations(request.latitude, request.longitude, request.viewport)
            .map { p -> p.ensureCategory() }
        return if (cat.isEmpty()) list else list.filter { it.poiCategory!! in overlap }
    }

    /**
     * Fetches gas/IRVE stations near the given center (legacy).
     * When [viewport] is non-null, providers may use it to limit the search to the visible map.
     */
    suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport? = null
    ): List<Poi>

    /** Clears any internal cache this provider may have. */
    suspend fun clearCache() {}
}

/**
 * Base abstract class for POI providers to specify usage rules.
 */
abstract class AbstractPoiProvider : PoiProvider {
    override val usageRules: PoiProviderRules? = null
}

private fun Poi.ensureCategory(): Poi = copy(
    poiCategory = poiCategory ?: if (isElectric) PoiCategory.Irve else PoiCategory.Gas
)

class MockPoiProvider : PoiProvider {
    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        // Mock data around some common coordinates or relative to input
        return listOf(
            Poi("1", "BP Paris Sud", "123 Avenue du Maine, Paris", latitude + 0.01, longitude + 0.01, "BP"),
            Poi("2", "Aral Station", "45 Rue de Rivoli, Paris", latitude - 0.01, longitude + 0.02, "Aral"),
            Poi("3", "Eni Live", "88 Boulevard Haussmann, Paris", latitude + 0.02, longitude - 0.01, "Eni"),
            Poi("4", "Circle K", "10 Place de la Bastille, Paris", latitude - 0.02, longitude - 0.02, "Circle K"),
            Poi("5", "OMV Station", "22 Rue de la Paix, Paris", latitude + 0.005, longitude - 0.005, "OMV")
        )
    }
}
