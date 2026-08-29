package fr.geoking.gaston.api.belib

import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.autoProvidersForCountries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * European EV coverage for countries with a [ParkingRegion] bbox:
 * two major cities each must (1) sit inside that country's bbox,
 * (2) resolve the expected electric station-finder providers,
 * (3) resolve the expected live-availability provider.
 */
class EuropeanEvCoverageTest {

    private enum class AvailabilityKind {
        Quali, ParisMerged, Belgium, DotNl, IchTankeStrom, Digitraffic, AustriaEv,
        Eipa, NobilNor, NobilSwe, ItalyPun, Eco,
    }

    private data class City(
        val iso: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val availability: AvailabilityKind,
    )

    private val belib = stubProvider()
    private val quali = stubProvider()
    private val belgium = stubProvider()
    private val eco = stubProvider()
    private val dotNl = stubProvider()
    private val ichTanke = stubProvider()
    private val digitraffic = stubProvider()
    private val austriaEv = stubProvider()
    private val eipa = stubProvider()
    private val nobilNor = stubProvider()
    private val nobilSwe = stubProvider()
    private val italyPun = stubProvider()
    private val availabilityFactory = BorneAvailabilityProviderFactory(
        belibProvider = belib,
        qualiChargeProvider = quali,
        belgiumNapProvider = belgium,
        ecoMovementProvider = eco,
        dotNlProvider = dotNl,
        ichTankeStromProvider = ichTanke,
        digitrafficAfirProvider = digitraffic,
        austriaEControlEvProvider = austriaEv,
        eipaProvider = eipa,
        nobilNorProvider = nobilNor,
        nobilSweProvider = nobilSwe,
        italyPunProvider = italyPun,
    )

    @Test
    fun probes_coverEveryEuropeanParkingRegion_withTwoCities() {
        val europeanIsos = ParkingRegion.entries
            .map { it.countryCode }
            .filter { it.length == 2 && it !in setOf("MX", "AR", "AU", "US", "CA") }
            .toSet()
        val byIso = cities.groupBy { it.iso }
        assertEquals(europeanIsos, byIso.keys, "missing or extra European ParkingRegion countries")
        byIso.forEach { (iso, list) ->
            assertEquals(2, list.size, "$iso should have exactly 2 cities")
        }
    }

    @Test
    fun eachCity_matchesCountryParkingRegionBbox() {
        for (city in cities) {
            val region = ParkingRegion.entries.first { it.countryCode == city.iso }
            assertTrue(
                region.contains(city.lat, city.lon),
                "${city.iso} ${city.name} (${city.lat}, ${city.lon}) outside ${region.name} bbox",
            )
        }
    }

    @Test
    fun eachCity_resolvesElectricStationFinderProviders() {
        for (city in cities) {
            val resolved = autoProvidersForCountries(
                countryCodes = listOf(city.iso),
                wantFuel = false,
                wantElectric = true,
                fallbackManual = emptySet(),
            )
            val expected = expectedElectricProviders(city.iso)
            assertTrue(
                resolved.containsAll(expected),
                "${city.iso} ${city.name}: expected $expected in $resolved",
            )
        }
    }

    @Test
    fun eachCity_resolvesAvailabilityProvider() {
        val parisMerged = availabilityFactory.getProvider(48.8566, 2.3522)
        assertNotNull(parisMerged)
        for (city in cities) {
            val expected = when (city.availability) {
                AvailabilityKind.Quali -> quali
                AvailabilityKind.ParisMerged -> parisMerged
                AvailabilityKind.Belgium -> belgium
                AvailabilityKind.DotNl -> dotNl
                AvailabilityKind.IchTankeStrom -> ichTanke
                AvailabilityKind.Digitraffic -> digitraffic
                AvailabilityKind.AustriaEv -> austriaEv
                AvailabilityKind.Eipa -> eipa
                AvailabilityKind.NobilNor -> nobilNor
                AvailabilityKind.NobilSwe -> nobilSwe
                AvailabilityKind.ItalyPun -> italyPun
                AvailabilityKind.Eco -> eco
            }
            assertEquals(
                expected,
                availabilityFactory.getProvider(city.lat, city.lon),
                "${city.iso} ${city.name} (${city.lat}, ${city.lon})",
            )
        }
    }

    private fun expectedElectricProviders(iso: String): Set<PoiProviderType> =
        when (iso) {
            "FR" -> setOf(
                PoiProviderType.DataGouvElec,
                PoiProviderType.OpenChargeMap,
                PoiProviderType.EcoMovement,
                PoiProviderType.Overpass,
            )
            "LU" -> setOf(
                PoiProviderType.Chargy,
                PoiProviderType.OpenChargeMap,
                PoiProviderType.EcoMovement,
                PoiProviderType.Overpass,
            )
            "GB" -> setOf(
                PoiProviderType.CharGyUk,
                PoiProviderType.Fastned,
                PoiProviderType.OpenChargeMap,
                PoiProviderType.EcoMovement,
                PoiProviderType.Overpass,
            )
            else -> setOf(
                PoiProviderType.OpenChargeMap,
                PoiProviderType.EcoMovement,
                PoiProviderType.Overpass,
            )
        }

    private fun stubProvider(): BorneAvailabilityProvider =
        object : BorneAvailabilityProvider {
            override suspend fun getAvailability(
                latitude: Double,
                longitude: Double,
                radiusKm: Int,
            ): List<PdcAvailability> = emptyList()
        }

    /**
     * Two big cities per European [ParkingRegion], coords inside that country's bbox.
     * Availability expectation follows [ParkingRegion.containing] (e.g. Stockholm→NO→NobilNor).
     */
    private val cities: List<City> = listOf(
        City("LU", "Luxembourg City", 49.6116, 6.1319, AvailabilityKind.Eco),
        City("LU", "Esch-sur-Alzette", 49.4958, 5.9806, AvailabilityKind.Eco),
        City("BE", "Brussels", 50.8503, 4.3517, AvailabilityKind.Belgium),
        City("BE", "Antwerp", 51.2194, 4.4025, AvailabilityKind.Belgium),
        City("CH", "Zurich", 47.3769, 8.5417, AvailabilityKind.IchTankeStrom),
        City("CH", "Geneva", 46.2044, 6.1432, AvailabilityKind.IchTankeStrom),
        City("NL", "Amsterdam", 52.3676, 4.9041, AvailabilityKind.DotNl),
        City("NL", "Rotterdam", 51.9244, 4.4777, AvailabilityKind.DotNl),
        City("DK", "Copenhagen", 55.6761, 12.5683, AvailabilityKind.Eco),
        City("DK", "Aarhus", 56.1629, 10.2039, AvailabilityKind.Eco),
        City("AT", "Vienna", 48.2082, 16.3738, AvailabilityKind.AustriaEv),
        City("AT", "Graz", 47.0707, 15.4395, AvailabilityKind.AustriaEv),
        City("DE", "Berlin", 52.5200, 13.4050, AvailabilityKind.Eco),
        City("DE", "Hamburg", 53.5511, 9.9937, AvailabilityKind.Eco),
        City("FR", "Paris", 48.8566, 2.3522, AvailabilityKind.ParisMerged),
        City("FR", "Lyon", 45.7640, 4.8357, AvailabilityKind.Quali),
        City("GB", "London", 51.5074, -0.1278, AvailabilityKind.Eco),
        City("GB", "Manchester", 53.4808, -2.2426, AvailabilityKind.Eco),
        City("ES", "Madrid", 40.4168, -3.7038, AvailabilityKind.Eco),
        City("ES", "Barcelona", 41.3874, 2.1686, AvailabilityKind.Eco),
        City("IT", "Rome", 41.9028, 12.4964, AvailabilityKind.ItalyPun),
        City("IT", "Milan", 45.4642, 9.1900, AvailabilityKind.ItalyPun),
        City("HR", "Zagreb", 45.8150, 15.9819, AvailabilityKind.Eco),
        City("HR", "Split", 43.5081, 16.4402, AvailabilityKind.Eco),
        City("SI", "Ljubljana", 46.0569, 14.5058, AvailabilityKind.Eco),
        City("SI", "Maribor", 46.5547, 15.6459, AvailabilityKind.Eco),
        City("ME", "Podgorica", 42.4304, 19.2594, AvailabilityKind.Eco),
        City("ME", "Nikšić", 42.7731, 18.9445, AvailabilityKind.Eco),
        City("MK", "Skopje", 41.9981, 21.4254, AvailabilityKind.Eco),
        City("MK", "Bitola", 41.0297, 21.3292, AvailabilityKind.Eco),
        City("NO", "Oslo", 59.9139, 10.7522, AvailabilityKind.NobilNor),
        City("NO", "Bergen", 60.3913, 5.3221, AvailabilityKind.NobilNor),
        // Stockholm sits in Norway bbox (specificity); Gothenburg in Denmark → Eco
        City("SE", "Stockholm", 59.3293, 18.0686, AvailabilityKind.NobilNor),
        City("SE", "Gothenburg", 57.7089, 11.9746, AvailabilityKind.Eco),
        City("PT", "Lisbon", 38.7223, -9.1393, AvailabilityKind.Eco),
        City("PT", "Porto", 41.1579, -8.6291, AvailabilityKind.Eco),
        City("FI", "Helsinki", 60.1699, 24.9384, AvailabilityKind.Digitraffic),
        City("FI", "Tampere", 61.4978, 23.7610, AvailabilityKind.Digitraffic),
        City("GR", "Athens", 37.9838, 23.7275, AvailabilityKind.Eco),
        City("GR", "Thessaloniki", 40.6401, 22.9444, AvailabilityKind.Eco),
        City("IE", "Dublin", 53.3498, -6.2603, AvailabilityKind.Eco),
        City("IE", "Cork", 51.8985, -8.4756, AvailabilityKind.Eco),
        City("MD", "Chișinău", 47.0105, 28.8638, AvailabilityKind.Eco),
        City("MD", "Bălți", 47.7530, 27.9050, AvailabilityKind.Eco),
        City("RO", "Bucharest", 44.4268, 26.1025, AvailabilityKind.Eco),
        City("RO", "Cluj-Napoca", 46.7712, 23.6236, AvailabilityKind.Eco),
        City("RS", "Belgrade", 44.7866, 20.4489, AvailabilityKind.Eco),
        City("RS", "Novi Sad", 45.2671, 19.8335, AvailabilityKind.Eco),
        City("PL", "Warsaw", 52.2297, 21.0122, AvailabilityKind.Eipa),
        City("PL", "Kraków", 50.0647, 19.9450, AvailabilityKind.Eipa),
    )
}
