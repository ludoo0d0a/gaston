package fr.geoking.gaston.api.us

/**
 * Resolves US coordinates to an EIA [duoarea] for petroleum/pri/gnd retail averages.
 *
 * Metro areas (coarse lat/lon boxes) take precedence over state centroids from [UsStateLookup].
 * Boxes are approximate — see [EIA metro geographies](https://www.eia.gov/petroleum/gasdiesel/gas_geographies.php).
 */
object UsEiaAreaLookup {

    enum class Granularity {
        Metro,
        State,
    }

    data class Area(
        val duoArea: String,
        /** Short label for UI source strings, e.g. "Boston metro" or "MA state". */
        val label: String,
        val granularity: Granularity,
    )

    /**
     * Inclusive lat/lon bounds; checked before state fallback.
     * Order matters when boxes overlap — list more specific metros first if needed.
     */
    private data class MetroBox(
        val duoArea: String,
        val label: String,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    ) {
        fun contains(lat: Double, lon: Double): Boolean =
            lat in minLat..maxLat && lon in minLon..maxLon
    }

    private val metros: List<MetroBox> = listOf(
        // Boston–Cambridge–Quincy (YBOS)
        MetroBox("YBOS", "Boston metro", 42.25, 42.55, -71.30, -70.85),
        // New York City metro (YNYC)
        MetroBox("YNYC", "NYC metro", 40.50, 40.92, -74.25, -73.70),
        // Chicago (YCHI)
        MetroBox("YCHI", "Chicago metro", 41.65, 42.05, -88.00, -87.55),
        // Cleveland (YCLE)
        MetroBox("YCLE", "Cleveland metro", 41.35, 41.58, -81.90, -81.55),
        // Denver (YDEN)
        MetroBox("YDEN", "Denver metro", 39.55, 39.85, -105.15, -104.75),
        // Houston (YHOU)
        MetroBox("YHOU", "Houston metro", 29.55, 30.05, -95.75, -95.00),
        // Los Angeles (YLOS)
        MetroBox("YLOS", "Los Angeles metro", 33.85, 34.15, -118.55, -118.10),
        // Miami (YMIA)
        MetroBox("YMIA", "Miami metro", 25.60, 26.05, -80.45, -80.10),
        // San Francisco (YSFO)
        MetroBox("YSFO", "San Francisco metro", 37.65, 37.85, -122.52, -122.32),
        // Seattle (YSEA)
        MetroBox("YSEA", "Seattle metro", 47.45, 47.80, -122.45, -122.20),
    )

    /**
     * Finest EIA pricing area for [lat]/[lon], or null outside the US bounding box.
     */
    fun resolve(lat: Double, lon: Double): Area? {
        if (!UsStateLookup.isInUnitedStates(lat, lon)) return null

        metros.firstOrNull { it.contains(lat, lon) }?.let { metro ->
            return Area(
                duoArea = metro.duoArea,
                label = metro.label,
                granularity = Granularity.Metro,
            )
        }

        val state = UsStateLookup.nearestState(lat, lon) ?: return null
        return Area(
            duoArea = state.eiaDuoArea,
            label = "${state.iso2} state",
            granularity = Granularity.State,
        )
    }
}
