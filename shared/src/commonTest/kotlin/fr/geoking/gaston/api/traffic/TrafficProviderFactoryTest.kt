package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.poi.PoiProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrafficProviderFactoryTest {

    private val cita = object : TrafficProvider {
        override val enabled: Boolean = true
        override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? = null
    }
    private val france = object : TrafficProvider {
        override val enabled: Boolean = true
        override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? = null
    }
    private val tomtom = object : TrafficProvider {
        override val enabled: Boolean = true
        override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? = null
    }
    private val tomtomDisabled = object : TrafficProvider {
        override val enabled: Boolean = false
        override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? = null
    }

    private val franceBbox = GeographicRegion.Bbox(
        BisonFuteTrafficProvider.FRANCE_LAT_MIN,
        BisonFuteTrafficProvider.FRANCE_LON_MIN,
        BisonFuteTrafficProvider.FRANCE_LAT_MAX,
        BisonFuteTrafficProvider.FRANCE_LON_MAX
    )

    private val factory = TrafficProviderFactory(
        listOf(
            TrafficProviderRegistration(
                GeographicRegion.Bbox(49.4, 5.7, 50.2, 6.6),
                cita,
                selectionKey = null,
            ),
            TrafficProviderRegistration(
                franceBbox,
                france,
                selectionKey = PoiProviderType.TipiBisonFute,
            ),
            TrafficProviderRegistration(
                franceBbox,
                tomtom,
                selectionKey = PoiProviderType.TomTomTraffic,
            ),
            TrafficProviderRegistration(
                GeographicRegion.Everywhere,
                tomtom,
                selectionKey = PoiProviderType.TomTomTraffic,
            ),
        )
    )

    @Test
    fun luxembourg_uses_cita_not_france() {
        assertSame(cita, factory.getProvider(49.61, 6.13))
    }

    @Test
    fun paris_defaults_to_tipi_and_tomtom() {
        val providers = factory.getProviders(48.85, 2.35)
        assertEquals(listOf(france, tomtom), providers)
    }

    @Test
    fun paris_tipi_only_when_selected() {
        val providers = factory.getProviders(
            48.85,
            2.35,
            selectedProviders = setOf(PoiProviderType.TipiBisonFute),
        )
        assertEquals(listOf(france), providers)
    }

    @Test
    fun paris_tomtom_only_when_selected() {
        val providers = factory.getProviders(
            48.85,
            2.35,
            selectedProviders = setOf(PoiProviderType.TomTomTraffic),
        )
        assertEquals(listOf(tomtom), providers)
    }

    @Test
    fun paris_both_when_both_selected() {
        val providers = factory.getProviders(
            48.85,
            2.35,
            selectedProviders = setOf(
                PoiProviderType.TipiBisonFute,
                PoiProviderType.TomTomTraffic,
            ),
        )
        assertEquals(listOf(france, tomtom), providers)
    }

    @Test
    fun newYork_uses_tomtom_when_enabled() {
        assertSame(tomtom, factory.getProvider(40.71, -74.0))
    }

    @Test
    fun newYork_null_when_tomtom_deselected() {
        assertTrue(
            factory.getProviders(
                40.71,
                -74.0,
                selectedProviders = setOf(PoiProviderType.TipiBisonFute),
            ).isEmpty()
        )
    }

    @Test
    fun newYork_null_when_tomtom_disabled() {
        val f = TrafficProviderFactory(
            listOf(
                TrafficProviderRegistration(
                    GeographicRegion.Bbox(49.4, 5.7, 50.2, 6.6),
                    cita,
                ),
                TrafficProviderRegistration(
                    GeographicRegion.Everywhere,
                    tomtomDisabled,
                    selectionKey = PoiProviderType.TomTomTraffic,
                ),
            )
        )
        assertNull(f.getProvider(40.71, -74.0))
    }
}
