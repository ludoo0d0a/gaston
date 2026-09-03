package fr.geoking.gaston.auto

/**
 * Shared driver chrome every **canvas** Android Auto map must implement.
 *
 * Host Google maps ([NativeMapPoiScreen] / [PlaceListMapTemplate]) cannot: zoom, bearing,
 * and the location glyph are owned by the car host.
 */
interface AaCanvasMapControls {
    fun bumpZoom(delta: Int)
    fun toggleMapOrientation()
    fun recenterMap()
}

/**
 * Shared renderer contract for zoom, compass orientation, and the heading arrow at the vehicle.
 */
interface AaMapDrivingChrome {
    fun bumpZoom(delta: Int)
    fun setMapOrientation(mode: MapOrientationMode, headingDegrees: Float = 0f)
    fun updateUserLocation(lat: Double, lon: Double, headingDegrees: Float)
}
