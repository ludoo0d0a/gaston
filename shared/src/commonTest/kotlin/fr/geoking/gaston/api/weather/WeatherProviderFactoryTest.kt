package fr.geoking.gaston.api.weather

import fr.geoking.gaston.api.traffic.GeographicRegion
import kotlin.test.Test
import kotlin.test.assertSame

class WeatherProviderFactoryTest {

    private val nordic = object : WeatherProvider {
        override suspend fun getWeather(latitude: Double, longitude: Double) = null
    }
    private val france = object : WeatherProvider {
        override suspend fun getWeather(latitude: Double, longitude: Double) = null
    }
    private val global = object : WeatherProvider {
        override suspend fun getWeather(latitude: Double, longitude: Double) = null
    }

    private val factory = WeatherProviderFactory(
        listOf(
            GeographicRegion.Bbox(55.0, -10.0, 72.0, 35.0) to nordic,
            GeographicRegion.Bbox(41.0, -5.5, 51.6, 10.0) to france,
            GeographicRegion.Everywhere to global
        )
    )

    @Test
    fun oslo_uses_nordic() {
        assertSame(nordic, factory.getProvider(59.91, 10.75))
    }

    @Test
    fun paris_uses_france_not_nordic() {
        assertSame(france, factory.getProvider(48.85, 2.35))
    }

    @Test
    fun new_york_uses_global() {
        assertSame(global, factory.getProvider(40.71, -74.0))
    }
}
