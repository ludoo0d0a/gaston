package fr.geoking.gaston.poi

import android.content.Context
import android.widget.Toast
import fr.geoking.gaston.R

/** Builds the end-of-refresh toast text (failure / unchanged / what changed). */
fun Context.poiRefreshMessage(before: Poi, after: Poi?): String {
    if (after == null) return getString(R.string.poi_refresh_failed)
    val changes = detectPoiRefreshChanges(before, after)
    if (changes.isEmpty()) return getString(R.string.poi_refresh_no_changes)
    val labels = changes.joinToString(", ") { change ->
        getString(
            when (change) {
                PoiRefreshChange.Prices -> R.string.poi_refresh_field_prices
                PoiRefreshChange.Availability -> R.string.poi_refresh_field_availability
                PoiRefreshChange.Status -> R.string.poi_refresh_field_status
            }
        )
    }
    return getString(R.string.poi_refresh_updated, labels)
}

fun Context.showPoiRefreshToast(before: Poi, after: Poi?) {
    Toast.makeText(this, poiRefreshMessage(before, after), Toast.LENGTH_SHORT).show()
}

fun Context.showPoiRefreshFailedToast() {
    Toast.makeText(this, getString(R.string.poi_refresh_failed), Toast.LENGTH_SHORT).show()
}
