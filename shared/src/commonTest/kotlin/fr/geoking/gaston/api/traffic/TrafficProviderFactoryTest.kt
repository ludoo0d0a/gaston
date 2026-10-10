package fr.geoking.gaston.api.traffic

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

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

    private val factory = TrafficProviderFactory(
        listOf(
            GeographicRegion.Bbox(49.4, 5.7, 50.2, 6.6) to cita,
            GeographicRegion.Bbox(
                BisonFuteTrafficProvider.FRANCE_LAT_MIN,
                BisonFuteTrafficProvider.FRANCE_LON_MIN,
                BisonFuteTrafficProvider.FRANCE_LAT_MAX,
                BisonFuteTrafficProvider.FRANCE_LON_MAX
            ) to france,
            GeographicRegion.Everywhere to tomtom
        )
    )

    @Test
    fun luxembourg_uses_cita_not_france() {
        assertSame(cita, factory.getProvider(49.61, 6.13))
    }

    @Test
    fun paris_uses_france() {
        assertSame(france, factory.getProvider(48.85, 2.35))
    }

    @Test
    fun newYork_uses_tomtom_when_enabled() {
        assertSame(tomtom, factory.getProvider(40.71, -74.0))
    }

    @Test
    fun newYork_null_when_tomtom_disabled() {
        val f = TrafficProviderFactory(
            listOf(
                GeographicRegion.Bbox(49.4, 5.7, 50.2, 6.6) to cita,
                GeographicRegion.Everywhere to tomtomDisabled
            )
        )
        assertNull(f.getProvider(40.71, -74.0))
    }
}
