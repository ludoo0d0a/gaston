package fr.geoking.gaston.ui.map.preview

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory

/**
 * Per-marker map filter simulation for [MapMarkerPreview] (each marker can use a different energy/power selection).
 */
data class PreviewMapMarkerSpec(
    val poi: Poi,
    val effectiveEnergyTypes: Set<String>,
    val effectivePowerLevels: Set<Int> = emptySet(),
    /** Caption under the pin in [MapMarkerPreview]: IRVE context line, or fuel type key (e.g. gazole, sp98). */
    val legend: String,
)

/**
 * Shared mock POIs for map marker and [PoiDetailCard] previews: mixed brands, fuels, IRVE power bands,
 * tariff, stock, and motorway — aligned with [fr.geoking.gaston.ui.BrandHelper] keys.
 */
object PreviewPoiSamples {

    /** Same buckets as [fr.geoking.gaston.ui.MAP_IRVE_POWER_OPTIONS] — all selected for marker previews. */
    val previewAllIrvePowerLevels: Set<Int> = setOf(0, 20, 50, 100, 200, 300)

    /** Map canvas: POIs plus per-marker filter overrides so each pin shows the right label/color (incl. “no filter”). */
    fun diverseMapMarkerSpecs(): List<PreviewMapMarkerSpec> {
        val irveRow = listOf(
            PreviewMapMarkerSpec(pvIrve11(), setOf("electric"), setOf(0), "IRVE · max ≤20 kW"),
            PreviewMapMarkerSpec(pvIrve35(), setOf("electric"), setOf(20), "IRVE · max 20 kW"),
            PreviewMapMarkerSpec(pvIrve75(), setOf("electric"), setOf(50), "IRVE · max 50 kW"),
            PreviewMapMarkerSpec(pvIrve150(), setOf("electric"), setOf(100), "IRVE · max 100 kW"),
            PreviewMapMarkerSpec(pvIrve250(), setOf("electric"), setOf(200), "IRVE · max 200 kW"),
            PreviewMapMarkerSpec(pvIrve350(), setOf("electric"), setOf(300), "IRVE · max 300 kW"),
            PreviewMapMarkerSpec(pvIrveNoFilter(), emptySet(), emptySet(), "IRVE · no energy filter"),
        )
        val fuelRow = listOf(
            PreviewMapMarkerSpec(pvFuelGazole(), setOf("gazole"), emptySet(), "gazole"),
            PreviewMapMarkerSpec(pvFuelSp98(), setOf("sp98"), emptySet(), "sp98"),
            PreviewMapMarkerSpec(pvFuelSp95(), setOf("sp95"), emptySet(), "sp95"),
            PreviewMapMarkerSpec(pvFuelE85(), setOf("e85"), emptySet(), "e85"),
            PreviewMapMarkerSpec(pvFuelGplc(), setOf("gplc"), emptySet(), "gplc"),
            PreviewMapMarkerSpec(pvFuelNoSelection(), emptySet(), emptySet(), "none"),
        )
        return irveRow + fuelRow
    }

    fun diverseMapPois(): List<Poi> = diverseMapMarkerSpecs().map { it.poi }

    private fun pvIrve11(): Poi = Poi(
        id = "pv-ev-11",
        name = "11 kW",
        address = "Preview",
        latitude = 48.86800,
        longitude = 2.35150,
        brand = null,
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 11.0,
        operator = "Preview",
        chargePointCount = 1,
        irveDetails = IrveDetails(connectorTypes = setOf("type_2"), tarification = "Gratuit", gratuit = true),
        source = "DataGouv"
    )

    private fun pvIrve35(): Poi = Poi(
        id = "pv-ev-35",
        name = "35 kW",
        address = "Preview",
        latitude = 48.86800,
        longitude = 2.35210,
        brand = "Tesla",
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 35.0,
        operator = "Tesla",
        chargePointCount = 4,
        irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
        source = "DataGouv"
    )

    private fun pvIrve75(): Poi = Poi(
        id = "pv-ev-75",
        name = "75 kW",
        address = "Preview",
        latitude = 48.86800,
        longitude = 2.35270,
        brand = "Allego",
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 75.0,
        operator = "Allego",
        chargePointCount = 2,
        irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
        source = "DataGouv"
    )

    private fun pvIrve150(): Poi = Poi(
        id = "pv-ev-150",
        name = "150 kW",
        address = "Preview",
        latitude = 48.86800,
        longitude = 2.35330,
        brand = "Lidl",
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 150.0,
        operator = "Lidl",
        chargePointCount = 2,
        irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
        source = "DataGouv"
    )

    private fun pvIrve250(): Poi = Poi(
        id = "pv-ev-250",
        name = "250 kW",
        address = "Preview",
        latitude = 48.86800,
        longitude = 2.35390,
        brand = "Fastned",
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 250.0,
        operator = "Fastned",
        chargePointCount = 6,
        irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
        source = "DataGouv"
    )

    private fun pvIrve350(): Poi = Poi(
        id = "pv-ev-350",
        name = "350 kW",
        address = "Preview",
        latitude = 48.86800,
        longitude = 2.35450,
        brand = "Ionity",
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 350.0,
        operator = "Ionity",
        isOnHighway = true,
        chargePointCount = 8,
        irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
        siteName = "Ionity",
        source = "DataGouv"
    )

    /** IRVE with no energy/power filter — no kW pill, solid green pin. */
    private fun pvIrveNoFilter(): Poi = Poi(
        id = "pv-ev-no-filter",
        name = "No filter",
        address = "Preview",
        latitude = 48.86800,
        longitude = 2.35510,
        brand = null,
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 45.0,
        operator = "Preview",
        chargePointCount = 2,
        irveDetails = IrveDetails(connectorTypes = setOf("type_2"), tarification = "Payant", gratuit = false),
        source = "DataGouv"
    )

    private fun pvFuelGazole(): Poi = Poi(
        id = "pv-total-gazole",
        name = "Total Access",
        address = "1 Av. de la République, 75011 Paris",
        latitude = 48.86670,
        longitude = 2.35090,
        brand = "Total",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.769, "2025-03-28"),
            FuelPrice("SP95", 1.899, "2025-03-28"),
            FuelPrice("E10", 1.859, "2025-03-28")
        ),
        siteName = "Total Access République",
        postcode = "75011",
        addressLocal = "1 Av. de la République",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv"
    )

    private fun pvFuelSp98(): Poi = Poi(
        id = "pv-shell-sp98",
        name = "Shell",
        address = "45 Bd Voltaire, 75011 Paris",
        latitude = 48.86670,
        longitude = 2.35170,
        brand = "Shell",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.799),
            FuelPrice("SP95", 1.919),
            FuelPrice("SP98", 2.049)
        ),
        siteName = "Shell Voltaire",
        postcode = "75011",
        addressLocal = "45 Bd Voltaire",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv"
    )

    private fun pvFuelSp95(): Poi = Poi(
        id = "pv-carrefour-sp95",
        name = "Carrefour",
        address = "30 Rue du Faubourg Saint-Antoine, 75011 Paris",
        latitude = 48.86670,
        longitude = 2.35250,
        brand = "Carrefour",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.759),
            FuelPrice("SP95", 1.839),
            FuelPrice("E10", 1.819)
        ),
        siteName = "Carrefour Saint-Antoine",
        postcode = "75011",
        addressLocal = "30 Rue du Faubourg Saint-Antoine",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv"
    )

    private fun pvFuelE85(): Poi = Poi(
        id = "pv-bp-e85",
        name = "BP",
        address = "200 Rue de Bercy, 75012 Paris",
        latitude = 48.86670,
        longitude = 2.35330,
        brand = "BP",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.779),
            FuelPrice("SP95", 1.879),
            FuelPrice("E85", 1.129)
        ),
        siteName = "BP Bercy",
        postcode = "75012",
        addressLocal = "200 Rue de Bercy",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv"
    )

    private fun pvFuelGplc(): Poi = Poi(
        id = "pv-intermarche-gpl",
        name = "Intermarché GPL",
        address = "12 Av. de la Liberté, 75012 Paris",
        latitude = 48.86670,
        longitude = 2.35410,
        brand = "Intermarché",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.769),
            FuelPrice("GPLc", 0.989)
        ),
        siteName = "Intermarché Liberté",
        postcode = "75012",
        addressLocal = "12 Av. de la Liberté",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv"
    )

    /** Gas station when map energy filter is empty — no € pill, blue pin. */
    private fun pvFuelNoSelection(): Poi = Poi(
        id = "pv-gas-no-filter",
        name = "No filter",
        address = "8 Rue de la Roquette, 75011 Paris",
        latitude = 48.86670,
        longitude = 2.35490,
        brand = null,
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.749),
            FuelPrice("SP95", 1.859)
        ),
        siteName = "Station Roquette",
        postcode = "75011",
        addressLocal = "8 Rue de la Roquette",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv"
    )

    /**
     * Six POIs for [PoiDetailCard] previews — merged [Poi.source] so expanded fuel/IRVE rows appear.
     */
    fun diverseCardPois(): List<Poi> = listOf(
        previewCardTotal(),
        previewCardShell(),
        previewCardIonity(),
        previewCardUrbanEv(),
        previewCardEssoRupture(),
        previewCardBpE85()
    )

    private fun previewCardTotal(): Poi = Poi(
        id = "card-total",
        name = "Station",
        address = "1 Av. de la République, 75011 Paris",
        latitude = 48.8676,
        longitude = 2.3635,
        brand = "Total",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.769, "2025-03-28"),
            FuelPrice("SP95", 1.899, "2025-03-28"),
            FuelPrice("SP98", 2.019, "2025-03-28"),
            FuelPrice("E10", 1.859, "2025-03-28")
        ),
        siteName = "Total Access République",
        postcode = "75011",
        addressLocal = "1 Av. de la République",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )

    private fun previewCardShell(): Poi = Poi(
        id = "card-shell",
        name = "Station",
        address = "45 Bd Voltaire, 75011 Paris",
        latitude = 48.858,
        longitude = 2.37,
        brand = "Shell",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.799),
            FuelPrice("SP95", 1.919),
            FuelPrice("SP98", 2.049)
        ),
        siteName = "Shell Voltaire",
        postcode = "75011",
        addressLocal = "45 Bd Voltaire",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )

    private fun previewCardIonity(): Poi = Poi(
        id = "card-ionity",
        name = "Ionity • 350 kW",
        address = "A1 Aire de Régny",
        latitude = 46.0,
        longitude = 4.0,
        brand = "Ionity",
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 350.0,
        operator = "Ionity",
        isOnHighway = true,
        chargePointCount = 8,
        irveDetails = IrveDetails(
            connectorTypes = setOf("combo_ccs"),
            tarification = "Payant",
            gratuit = false
        ),
        siteName = "Ionity A1",
        postcode = "42370",
        addressLocal = "Aire de Régny",
        townLocal = "Régny",
        countryLocal = "France",
        source = "DataGouv + OpenChargeMap"
    )

    private fun previewCardUrbanEv(): Poi = Poi(
        id = "card-urban-ev",
        name = "Borne • 22 kW",
        address = "8 Rue du Faubourg Poissonnière, 75010 Paris",
        latitude = 48.87,
        longitude = 2.35,
        brand = null,
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 22.0,
        operator = "Paris",
        isOnHighway = false,
        chargePointCount = 2,
        irveDetails = IrveDetails(
            connectorTypes = setOf("type_2"),
            tarification = "Gratuit",
            gratuit = true,
            openingHours = "24/7"
        ),
        siteName = "IRVE — Faubourg Poissonnière",
        postcode = "75010",
        addressLocal = "8 Rue du Faubourg Poissonnière",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + OpenChargeMap"
    )

    private fun previewCardEssoRupture(): Poi = Poi(
        id = "card-esso",
        name = "Station",
        address = "102 Rue de la Roquette, 75011 Paris",
        latitude = 48.855,
        longitude = 2.38,
        brand = "Esso",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.789),
            FuelPrice("SP95", 1.889, outOfStock = true),
            FuelPrice("E10", 1.849)
        ),
        siteName = "Esso Roquette",
        postcode = "75011",
        addressLocal = "102 Rue de la Roquette",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )

    /** Full-screen marketing / store capture — fuel price list. */
    fun marketingFuelDetailPoi(): Poi = previewCardTotal()

    /** Full-screen marketing / store capture — EV connectors. */
    fun marketingEvDetailPoi(): Poi = previewCardIonity()

    /** Bottom carousel on map marketing frame. */
    fun marketingMapCarouselPois(): List<Poi> = diverseCardPois().take(3)

    /** Subset of map pins (fuel + fast charge) for readable store screenshots. */
    fun marketingMapMarkerSpecs(): List<PreviewMapMarkerSpec> = diverseMapMarkerSpecs().filter { spec ->
        spec.legend == "gazole" ||
            spec.legend == "sp98" ||
            spec.legend == "IRVE · max 200 kW" ||
            spec.legend == "IRVE · max 300 kW" ||
            spec.legend == "sp95"
    }

    /** Android Auto–style list rows. */
    fun marketingAutoListPois(): List<Poi> = listOf(
        previewCardTotal(),
        previewCardShell(),
        previewCardIonity(),
        previewCardUrbanEv(),
    )

    private fun previewCardBpE85(): Poi = Poi(
        id = "card-bp",
        name = "Station",
        address = "200 Rue de Bercy, 75012 Paris",
        latitude = 48.84,
        longitude = 2.38,
        brand = "BP",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.779),
            FuelPrice("SP95", 1.879),
            FuelPrice("E85", 1.129)
        ),
        siteName = "BP Bercy",
        postcode = "75012",
        addressLocal = "200 Rue de Bercy",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )
}
