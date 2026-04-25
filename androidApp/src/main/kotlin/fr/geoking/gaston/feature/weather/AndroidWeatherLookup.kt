package fr.geoking.gaston.feature.weather

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.location.LocationManager
import fr.geoking.gaston.api.weather.OpenMeteoGeocodingClient
import fr.geoking.gaston.api.weather.WeatherProviderFactory
import fr.geoking.gaston.di.MapModuleLoader
import fr.geoking.gaston.shared.action.ActionResult
import fr.geoking.gaston.shared.platform.PermissionManager
import fr.geoking.gaston.shared.weather.WeatherLookup
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidWeatherLookup(
    private val context: Context,
    private val permissionManager: PermissionManager
) : WeatherLookup, KoinComponent {

    override suspend fun getCurrentWeather(locationQuery: String?): ActionResult = withContext(Dispatchers.IO) {
        MapModuleLoader.ensureLoaded()
        val factory = get<WeatherProviderFactory>()
        val geocode = get<OpenMeteoGeocodingClient>()

        val (lat, lon, label) = if (locationQuery.isNullOrBlank()) {
            if (!permissionManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return@withContext ActionResult(
                    false,
                    "Location permission is needed for weather at your position. Ask the user to grant location, or ask for a city name."
                )
            }
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            @SuppressLint("MissingPermission")
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location == null) {
                return@withContext ActionResult(
                    false,
                    "Could not read GPS position yet. Suggest the user try again or ask for weather in a named city."
                )
            }
            Triple(location.latitude, location.longitude, "your location")
        } else {
            val place = geocode.searchFirst(locationQuery.trim())
                ?: return@withContext ActionResult(
                    false,
                    "Could not find a place named \"${locationQuery.trim()}\"."
                )
            Triple(place.latitude, place.longitude, place.label)
        }

        val provider = factory.getProvider(lat, lon)
            ?: return@withContext ActionResult(false, "No weather data source available.")
        val info = try {
            provider.getWeather(lat, lon)
        } catch (e: Exception) {
            return@withContext ActionResult(false, "Weather request failed: ${e.message ?: "unknown error"}")
        } ?: return@withContext ActionResult(false, "Weather data unavailable for this location.")

        val msg = buildString {
            append("Weather near ").append(label).append(": ")
            val parts = mutableListOf<String>()
            info.temperatureC?.let {
                parts += "${String.format(Locale.US, "%.0f", it)}°C"
            }
            info.conditionLabel?.let { parts += it }
            info.windSpeedMs?.let {
                parts += "wind ${String.format(Locale.US, "%.0f", it)} m/s"
            }
            info.relativeHumidityPercent?.let { parts += "humidity ${it}%" }
            if (parts.isEmpty()) {
                append("(no current details)")
            } else {
                append(parts.joinToString(", "))
            }
            append(". Source: ").append(info.providerId)
        }
        return@withContext ActionResult(true, msg)
    }
}
