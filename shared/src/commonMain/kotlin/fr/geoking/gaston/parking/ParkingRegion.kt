package fr.geoking.gaston.parking

import fr.geoking.gaston.shared.location.haversineKm

data class SubBox(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax

    fun distanceToKm(lat: Double, lon: Double): Double {
        val closestLat = lat.coerceIn(latMin, latMax)
        val closestLon = lon.coerceIn(lonMin, lonMax)
        return haversineKm(lat, lon, closestLat, closestLon)
    }
}

/**
 * Geographic region (country-level) for parking APIs. Used by [ParkingApiSelector] to choose
 * which providers to call based on user location. Smaller regions are checked first so e.g.
 * Luxembourg matches Luxembourg, not Germany or France.
 */
enum class ParkingRegion(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double,
    val countryCode: String
) {
    Luxembourg(
        latMin = 49.44,
        latMax = 50.18,
        lonMin = 5.73,
        lonMax = 6.53,
        countryCode = "LU"
    ),
    Belgium(
        latMin = 49.50,
        latMax = 51.51,
        lonMin = 2.54,
        lonMax = 6.41,
        countryCode = "BE"
    ),
    Switzerland(
        latMin = 45.82,
        latMax = 47.81,
        lonMin = 5.96,
        lonMax = 10.49,
        countryCode = "CH"
    ),
    Netherlands(
        latMin = 50.75,
        latMax = 53.56,
        lonMin = 3.36,
        lonMax = 7.23,
        countryCode = "NL"
    ),
    Denmark(
        latMin = 54.56,
        latMax = 57.75,
        lonMin = 8.08,
        lonMax = 15.16,
        countryCode = "DK"
    ),
    Austria(
        latMin = 46.37,
        latMax = 49.02,
        lonMin = 9.53,
        lonMax = 17.16,
        countryCode = "AT"
    ),
    Germany(
        latMin = 47.27,
        latMax = 55.06,
        lonMin = 5.87,
        lonMax = 15.04,
        countryCode = "DE"
    ),
    France(
        latMin = 41.33,
        latMax = 51.09,
        lonMin = -5.14,
        lonMax = 9.56,
        countryCode = "FR"
    ),
    UnitedKingdom(
        latMin = 49.86,
        latMax = 60.86,
        lonMin = -8.65,
        lonMax = 1.76,
        countryCode = "GB"
    ),
    Spain(
        latMin = 35.95,
        latMax = 43.79,
        lonMin = -9.30,
        lonMax = 4.33,
        countryCode = "ES"
    ),
    Italy(
        latMin = 36.65,
        latMax = 47.09,
        lonMin = 6.63,
        lonMax = 18.52,
        countryCode = "IT"
    ),
    Croatia(
        latMin = 42.39,
        latMax = 46.55,
        lonMin = 13.49,
        lonMax = 19.45,
        countryCode = "HR"
    ),
    Slovenia(
        latMin = 45.42,
        latMax = 46.88,
        lonMin = 13.38,
        lonMax = 16.61,
        countryCode = "SI"
    ),
    Montenegro(
        latMin = 41.85,
        latMax = 43.55,
        lonMin = 18.43,
        lonMax = 20.35,
        countryCode = "ME"
    ),
    NorthMacedonia(
        latMin = 40.85,
        latMax = 42.37,
        lonMin = 20.45,
        lonMax = 23.03,
        countryCode = "MK"
    ),
    Norway(
        latMin = 57.9,
        latMax = 71.2,
        lonMin = 4.6,
        lonMax = 31.1,
        countryCode = "NO"
    ),
    Sweden(
        latMin = 55.3,
        latMax = 69.1,
        lonMin = 10.9,
        lonMax = 24.2,
        countryCode = "SE"
    ),
    Portugal(
        latMin = 36.9,
        latMax = 42.2,
        lonMin = -9.5,
        lonMax = -6.1,
        countryCode = "PT"
    ),
    Finland(
        latMin = 59.7,
        latMax = 70.1,
        lonMin = 19.1,
        lonMax = 31.6,
        countryCode = "FI"
    ),
    Greece(
        latMin = 34.8,
        latMax = 41.8,
        lonMin = 19.3,
        lonMax = 28.3,
        countryCode = "GR"
    ),
    Ireland(
        latMin = 51.4,
        latMax = 55.4,
        lonMin = -10.5,
        lonMax = -5.9,
        countryCode = "IE"
    ),
    Moldova(
        latMin = 45.4,
        latMax = 48.5,
        lonMin = 26.6,
        lonMax = 30.2,
        countryCode = "MD"
    ),
    Romania(
        latMin = 43.6,
        latMax = 48.3,
        lonMin = 20.2,
        lonMax = 29.7,
        countryCode = "RO"
    ),
    Serbia(
        latMin = 42.2,
        latMax = 46.2,
        lonMin = 18.8,
        lonMax = 23.0,
        countryCode = "RS"
    ),
    Mexico(
        latMin = 14.5,
        latMax = 32.8,
        lonMin = -118.4,
        lonMax = -86.7,
        countryCode = "MX"
    ),
    Argentina(
        latMin = -55.2,
        latMax = -21.8,
        lonMin = -73.6,
        lonMax = -53.6,
        countryCode = "AR"
    ),
    Australia(
        latMin = -43.7,
        latMax = -10.0,
        lonMin = 112.9,
        lonMax = 153.6,
        countryCode = "AU"
    ),
    UnitedStates(
        latMin = 17.0,
        latMax = 71.5,
        lonMin = -170.0,
        lonMax = -64.0,
        countryCode = "US"
    );

    val subBoxes: List<SubBox> = when (this.name) {
        "Germany" -> listOf(
            SubBox(50.5, 55.06, 5.87, 15.04),
            SubBox(48.9, 50.5, 6.35, 15.04),
            SubBox(47.27, 48.9, 7.4, 15.04)
        )
        "France" -> listOf(
            SubBox(43.0, 51.09, -5.14, 8.25),
            SubBox(41.33, 43.0, 8.5, 9.56)
        )
        else -> listOf(SubBox(latMin, latMax, lonMin, lonMax))
    }

    fun contains(lat: Double, lon: Double): Boolean =
        subBoxes.any { it.contains(lat, lon) }

    companion object {
        /** Order: smaller / more specific regions first so e.g. Luxembourg is chosen over Germany. */
        private val bySpecificity = listOf(
            Luxembourg, Montenegro, NorthMacedonia, Slovenia, Croatia,
            Ireland, Moldova, Portugal, Belgium, Switzerland, Netherlands,
            Denmark, Austria, Romania, Serbia, Greece, Norway, Finland,
            Sweden, Germany, France, UnitedKingdom, Spain, Italy,
            Mexico, Argentina, Australia
            // UnitedStates is excluded from 'containing' (single region choice) to avoid breaking
            // logic that expects null for non-European regions, but is still available in 'allContaining'
            // and 'allInViewport' for provider resolution.
        )

        private val allRegions = bySpecificity + UnitedStates

        /** Returns the region containing (lat, lon), or null if none. */
        fun containing(lat: Double, lon: Double): ParkingRegion? =
            bySpecificity.firstOrNull { it.contains(lat, lon) }

        /** Returns all regions containing (lat, lon). Useful for cross-border areas. */
        fun allContaining(lat: Double, lon: Double): List<ParkingRegion> =
            allRegions.filter { it.contains(lat, lon) }

        /** Returns all regions intersecting with the given viewport. */
        fun allInViewport(
            latMin: Double,
            latMax: Double,
            lonMin: Double,
            lonMax: Double
        ): List<ParkingRegion> {
            return allRegions.filter { region ->
                region.latMin <= latMax && region.latMax >= latMin &&
                        region.lonMin <= lonMax && region.lonMax >= lonMin
            }
        }
    }
}
