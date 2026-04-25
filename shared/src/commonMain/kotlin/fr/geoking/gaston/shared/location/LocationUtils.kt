package fr.geoking.gaston.shared.location

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Standard Haversine formula for distance in kilometers. */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val rad = PI / 180.0
    val dLat = (lat2 - lat1) * rad
    val dLon = (lon2 - lon1) * rad
    val sinDLat = sin(dLat / 2)
    val sinDLon = sin(dLon / 2)
    val a = sinDLat * sinDLat +
        cos(lat1 * rad) * cos(lat2 * rad) *
        sinDLon * sinDLon
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

/** Fast equirectangular approximation for distance in kilometers (good for small distances). */
fun approxDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLatKm = (lat2 - lat1) * 111.0
    val avgLatRad = ((lat1 + lat2) / 2.0) * PI / 180.0
    val dLonKm = (lon2 - lon1) * 111.0 * cos(avgLatRad)
    return sqrt(dLatKm * dLatKm + dLonKm * dLonKm)
}
