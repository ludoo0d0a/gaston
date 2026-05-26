package fr.geoking.gaston.api.us

import fr.geoking.gaston.shared.location.haversineKm

/**
 * Resolves a US location to a state/territory and EIA state [duoarea] (e.g. SCA = California).
 * For metro-aware pricing use [UsEiaAreaLookup.resolve]; this type is the state-level fallback.
 */
object UsStateLookup {

    data class State(
        val iso2: String,
        /** EIA duoarea facet id (typically "S" + postal abbreviation). */
        val eiaDuoArea: String,
        val lat: Double,
        val lon: Double,
    )

    private val states: List<State> = listOf(
        State("AL", "SAL", 32.806671, -86.791130),
        State("AK", "SAK", 61.370716, -152.404419),
        State("AZ", "SAZ", 33.729759, -111.431221),
        State("AR", "SAR", 34.969704, -92.373123),
        State("CA", "SCA", 36.116203, -119.681564),
        State("CO", "SCO", 39.059811, -105.311104),
        State("CT", "SCT", 41.597782, -72.755371),
        State("DE", "SDE", 39.318523, -75.507141),
        // EIA petroleum/pri/gnd has no SDC series; DC retail averages use Maryland (SMD).
        State("DC", "SMD", 38.897438, -77.026817),
        State("FL", "SFL", 27.766279, -81.686783),
        State("GA", "SGA", 33.040619, -83.643074),
        State("HI", "SHI", 21.094318, -157.498337),
        State("ID", "SID", 44.240459, -114.478828),
        State("IL", "SIL", 40.349457, -88.986137),
        State("IN", "SIN", 39.849426, -86.258278),
        State("IA", "SIA", 42.011539, -93.210526),
        State("KS", "SKS", 38.526600, -96.726486),
        State("KY", "SKY", 37.668140, -84.670067),
        State("LA", "SLA", 31.169546, -91.867805),
        State("ME", "SME", 44.693947, -69.381927),
        State("MD", "SMD", 39.063946, -76.802101),
        State("MA", "SMA", 42.230171, -71.530106),
        State("MI", "SMI", 43.326618, -84.536095),
        State("MN", "SMN", 45.694454, -93.900192),
        State("MS", "SMS", 32.741646, -89.678696),
        State("MO", "SMO", 38.456085, -92.288368),
        State("MT", "SMT", 46.921925, -110.454353),
        State("NE", "SNE", 41.125370, -98.268082),
        State("NV", "SNV", 38.313515, -117.055374),
        State("NH", "SNH", 43.452492, -71.563896),
        State("NJ", "SNJ", 40.298904, -74.521011),
        State("NM", "SNM", 34.840515, -106.248482),
        State("NY", "SNY", 42.165726, -74.948051),
        State("NC", "SNC", 35.630066, -79.806419),
        State("ND", "SND", 47.528912, -99.784012),
        State("OH", "SOH", 40.388783, -82.764915),
        State("OK", "SOK", 35.565342, -96.928917),
        State("OR", "SOR", 44.572021, -122.070938),
        State("PA", "SPA", 40.590752, -77.209755),
        State("RI", "SRI", 41.680893, -71.511780),
        State("SC", "SSC", 33.856892, -80.945007),
        State("SD", "SSD", 44.299782, -99.438828),
        State("TN", "STN", 35.747845, -86.692345),
        State("TX", "STX", 31.054487, -97.563461),
        State("UT", "SUT", 40.150032, -111.862434),
        State("VT", "SVT", 44.045876, -72.710686),
        State("VA", "SVA", 37.769337, -78.169968),
        State("WA", "SWA", 47.400902, -121.490494),
        State("WV", "SWV", 38.491226, -80.954453),
        State("WI", "SWI", 44.268543, -89.616508),
        State("WY", "SWY", 42.755966, -107.302490),
    )

    fun isInUnitedStates(lat: Double, lon: Double): Boolean {
        // Synchronized with ParkingRegion.UnitedStates (includes CONUS, AK, HI, PR, USVI)
        return lat in 17.0..71.5 && lon in -170.0..-64.0
    }

    fun nearestState(lat: Double, lon: Double): State? {
        if (!isInUnitedStates(lat, lon)) return null
        return states.minByOrNull { haversineKm(lat, lon, it.lat, it.lon) }
    }
}
