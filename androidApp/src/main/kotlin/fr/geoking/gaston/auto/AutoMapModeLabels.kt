package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R

fun CarMapMode.displayLabel(carContext: CarContext): String = when (this) {
    CarMapMode.Native -> carContext.getString(R.string.map_mode_google)
    CarMapMode.Raster -> carContext.getString(R.string.map_mode_custom)
    CarMapMode.MapLibreMapSnapshotter -> carContext.getString(R.string.map_mode_maplibre_snapshotter)
    CarMapMode.Mapbox -> carContext.getString(R.string.map_mode_mapbox)
    CarMapMode.MapLibreVirtualDisplay -> carContext.getString(R.string.map_mode_maplibre_virtualdisplay)
    CarMapMode.MapLibreEglDirect -> carContext.getString(R.string.map_mode_maplibre_egl_direct)
    CarMapMode.Mapsforge -> carContext.getString(R.string.map_mode_mapsforge)
}

fun CarMapMode.displayDescription(carContext: CarContext): String = when (this) {
    CarMapMode.Native -> carContext.getString(R.string.map_mode_google_desc)
    CarMapMode.Raster -> carContext.getString(R.string.map_mode_custom_desc)
    CarMapMode.MapLibreMapSnapshotter -> carContext.getString(R.string.map_mode_maplibre_snapshotter_desc)
    CarMapMode.Mapbox -> carContext.getString(R.string.map_mode_mapbox_desc)
    CarMapMode.MapLibreVirtualDisplay -> carContext.getString(R.string.map_mode_maplibre_virtualdisplay_desc)
    CarMapMode.MapLibreEglDirect -> carContext.getString(R.string.map_mode_maplibre_egl_direct_desc)
    CarMapMode.Mapsforge -> carContext.getString(R.string.map_mode_mapsforge_desc)
}
