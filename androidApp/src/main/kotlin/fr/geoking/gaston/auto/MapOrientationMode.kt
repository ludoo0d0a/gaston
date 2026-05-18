package fr.geoking.gaston.auto

/**
 * Map orientation for custom (surface-rendered) Android Auto maps.
 * Native [androidx.car.app.model.PlaceListMapTemplate] maps are host-controlled and stay north-up.
 */
enum class MapOrientationMode {
    /** Map north points to the top of the screen (bearing = 0). */
    NorthUp,

    /** Map rotates so device/course heading points up. */
    HeadingUp,
}
