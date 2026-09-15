package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R

fun CarMapMode.displayLabel(carContext: CarContext): String = when (this) {
    CarMapMode.Native -> carContext.getString(R.string.map_mode_google)
    CarMapMode.Custom -> carContext.getString(R.string.map_mode_custom)
    CarMapMode.MapLibre -> carContext.getString(R.string.map_mode_maplibre)
    CarMapMode.MapTiler -> carContext.getString(R.string.map_mode_maptiler)
    CarMapMode.Protomaps -> carContext.getString(R.string.map_mode_protomaps)
    CarMapMode.Mapbox -> carContext.getString(R.string.map_mode_mapbox)
    CarMapMode.MapLibrePresentation -> carContext.getString(R.string.map_mode_maplibre_presentation)
}

fun CarMapMode.displayDescription(carContext: CarContext): String = when (this) {
    CarMapMode.Native -> carContext.getString(R.string.map_mode_google_desc)
    CarMapMode.Custom -> carContext.getString(R.string.map_mode_custom_desc)
    CarMapMode.MapLibre -> carContext.getString(R.string.map_mode_maplibre_desc)
    CarMapMode.MapTiler -> carContext.getString(R.string.map_mode_maptiler_desc)
    CarMapMode.Protomaps -> carContext.getString(R.string.map_mode_protomaps_desc)
    CarMapMode.Mapbox -> carContext.getString(R.string.map_mode_mapbox_desc)
    CarMapMode.MapLibrePresentation -> carContext.getString(R.string.map_mode_maplibre_presentation_desc)
}
