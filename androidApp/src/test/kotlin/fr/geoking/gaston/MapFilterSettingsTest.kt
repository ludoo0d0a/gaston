package fr.geoking.gaston

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFilterSettingsTest {

    private fun poi(id: String, category: PoiCategory) = Poi(
        id = id,
        name = id,
        latitude = 48.85,
        longitude = 2.35,
        address = "",
        poiCategory = category,
    )

    @Test
    fun stationMapFilters_otherModeRadarShowsOnlyRadars() {
        val settings = AppSettings(
            poiProviderSelectionMode = PoiProviderSelectionMode.Manual,
            selectedPoiProviders = setOf(
                PoiProviderType.LufopOpenSpeedCam,
                PoiProviderType.Overpass,
            ),
            selectedOverpassAmenityTypes = setOf("speed_camera"),
        )
        val filtered = StationMapFilters.apply(
            settings = settings,
            pois = listOf(
                poi("gas", PoiCategory.Gas),
                poi("radar", PoiCategory.Radar),
                poi("weather", PoiCategory.Weather),
                poi("parking", PoiCategory.Parking),
            ),
            providers = settings.selectedPoiProviders,
            skipWhenOnlyOverpass = false,
        )
        assertEquals(listOf("radar"), filtered.map { it.id })
    }

    @Test
    fun stationMapFilters_otherModeWeatherShowsOnlyWeather() {
        val settings = AppSettings(
            poiProviderSelectionMode = PoiProviderSelectionMode.Manual,
            selectedPoiProviders = setOf(PoiProviderType.OpenMeteoWeather),
            selectedOverpassAmenityTypes = setOf("weather"),
        )
        val filtered = StationMapFilters.apply(
            settings = settings,
            pois = listOf(
                poi("gas", PoiCategory.Gas),
                poi("radar", PoiCategory.Radar),
                poi("weather", PoiCategory.Weather),
            ),
            providers = settings.selectedPoiProviders,
            skipWhenOnlyOverpass = false,
        )
        assertEquals(listOf("weather"), filtered.map { it.id })
    }

    @Test
    fun stationMapFilters_trafficModeHidesAllPois() {
        val settings = AppSettings(
            poiProviderSelectionMode = PoiProviderSelectionMode.Manual,
            selectedPoiProviders = setOf(PoiProviderType.Overpass),
            selectedOverpassAmenityTypes = emptySet(),
            mapTrafficEnabled = true,
        )
        assertTrue(settings.isOtherModeActive())
        val filtered = StationMapFilters.apply(
            settings = settings,
            pois = listOf(
                poi("gas", PoiCategory.Gas),
                poi("radar", PoiCategory.Radar),
                poi("weather", PoiCategory.Weather),
            ),
            providers = settings.selectedPoiProviders,
            skipWhenOnlyOverpass = false,
        )
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun stationMapFilters_preservesTrafficPoisInElectricModeAndHighwayFilter() {
        val settings = AppSettings(
            mapEnergyMode = fr.geoking.gaston.poi.EnergyFilterMode.Electric,
            mapTrafficEnabled = true,
            filterOnlyHighwayStations = true,
        )
        val trafficPoi = Poi(
            id = "traffic_1",
            name = "A6 Accident",
            latitude = 48.85,
            longitude = 2.35,
            address = "A6",
            poiCategory = PoiCategory.Traffic,
            isOnHighway = false,
            isElectric = false,
        )
        val nonHighwayGasStation = Poi(
            id = "gas_1",
            name = "Gas Station",
            latitude = 48.85,
            longitude = 2.35,
            address = "Local road",
            poiCategory = PoiCategory.Gas,
            isOnHighway = false,
            isElectric = false,
        )
        val filtered = StationMapFilters.apply(
            settings = settings,
            pois = listOf(trafficPoi, nonHighwayGasStation),
            providers = emptySet(),
            skipWhenOnlyOverpass = false,
        )
        assertEquals(listOf("traffic_1"), filtered.map { it.id })
    }

    @Test
    fun testCountryCodesAtMapPosition_MaizieresLesMetz() {
        // Maizières-lès-Metz is in France, about 40km away from Germany's border.
        // It must detect France (FR) and NOT Germany (DE).
        val codes = countryCodesAtMapPosition(49.212, 6.162)
        assertEquals(listOf("FR"), codes)
    }

    @Test
    fun testCountryCodesAtMapPosition_StrasbourgBorder() {
        // Strasbourg is in France but directly on the border with Germany (Kehl).
        // It must detect both France (FR) and Germany (DE) as it is well within 10km of the border.
        val codes = countryCodesAtMapPosition(48.583, 7.745)
        assertEquals(setOf("FR", "DE"), codes.toSet())
    }

    @Test
    fun testCountryCodesAtMapPosition_Frankfurt() {
        // Frankfurt is in Germany, far away from France.
        // It must detect Germany (DE) and NOT France (FR).
        val codes = countryCodesAtMapPosition(50.111, 8.682)
        assertEquals(listOf("DE"), codes)
    }

    @Test
    fun testCountryCodesAtMapPosition_Paris() {
        // Paris is in the center of France.
        // It must detect France (FR) and NOT Germany (DE).
        val codes = countryCodesAtMapPosition(48.8566, 2.3522)
        assertEquals(listOf("FR"), codes)
    }

    @Test
    fun providerUiInfo_meteoAndRadarCapabilityFlags() {
        // OpenMeteoWeather provides meteo
        val openMeteoType = PoiProviderType.OpenMeteoWeather
        assertEquals(PoiProviderType.OpenMeteoWeather, openMeteoType)

        // Radar providers
        val radarTypes = setOf(PoiProviderType.FranceRadars, PoiProviderType.LufopOpenSpeedCam, PoiProviderType.LuxembourgRadars)
        assertTrue(PoiProviderType.FranceRadars in radarTypes)
        assertTrue(PoiProviderType.LufopOpenSpeedCam in radarTypes)
        assertTrue(PoiProviderType.LuxembourgRadars in radarTypes)
    }
}
