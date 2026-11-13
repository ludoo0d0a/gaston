package fr.geoking.gaston.poi

/**
 * Canonical TTLs for fuel / EV station caches.
 *
 * Stations and chargers move rarely; prices change within a day; live EV
 * availability changes within hours. See `docs/cache.md`.
 */
object PoiCacheTtl {
    /** Stations and charging points (location, connectors, brand) — 1 month. */
    const val STATION_MS: Long = 30L * 24 * 60 * 60 * 1000L

    /** Fuel prices and EV tariffs — 6 hours. */
    const val PRICE_MS: Long = 6L * 60 * 60 * 1000L

    /** Live EV connector availability / occupation — 1 hour. */
    const val AVAILABILITY_MS: Long = 60L * 60 * 1000L

    /** OSM amenities (parking, toilets, …) — 7 days. */
    const val AMENITY_MS: Long = 7L * 24 * 60 * 60 * 1000L
}
