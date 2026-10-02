package fr.geoking.gaston.feature.accident

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Offline-first local storage for accident / constat personal prefill data.
 * Vehicle / insurance live on [fr.geoking.gaston.UserVehicle] in app settings.
 */
class AccidentProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<AccidentProfile> = _profile.asStateFlow()

    fun save(profile: AccidentProfile) {
        prefs.edit()
            .putString(KEY_FULL_NAME, profile.fullName)
            .putString(KEY_ADDRESS, profile.address)
            .putString(KEY_PHONE, profile.phone)
            .putString(KEY_EMAIL, profile.email)
            .putString(KEY_LICENSE_NUMBER, profile.licenseNumber)
            .putString(KEY_LICENSE_COUNTRY, profile.licenseCountry)
            .putString(KEY_SELECTED_VEHICLE_ID, profile.selectedVehicleId)
            .putString(KEY_EMERGENCY_NAME, profile.emergencyContactName)
            .putString(KEY_EMERGENCY_PHONE, profile.emergencyContactPhone)
            .apply()
        _profile.value = profile
    }

    private fun load(): AccidentProfile = AccidentProfile(
        fullName = prefs.getString(KEY_FULL_NAME, "") ?: "",
        address = prefs.getString(KEY_ADDRESS, "") ?: "",
        phone = prefs.getString(KEY_PHONE, "") ?: "",
        email = prefs.getString(KEY_EMAIL, "") ?: "",
        licenseNumber = prefs.getString(KEY_LICENSE_NUMBER, "") ?: "",
        licenseCountry = prefs.getString(KEY_LICENSE_COUNTRY, "") ?: "",
        selectedVehicleId = prefs.getString(KEY_SELECTED_VEHICLE_ID, "") ?: "",
        emergencyContactName = prefs.getString(KEY_EMERGENCY_NAME, "") ?: "",
        emergencyContactPhone = prefs.getString(KEY_EMERGENCY_PHONE, "") ?: "",
    )

    companion object {
        private const val PREFS_NAME = "accident_profile"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PHONE = "phone"
        private const val KEY_EMAIL = "email"
        private const val KEY_LICENSE_NUMBER = "license_number"
        private const val KEY_LICENSE_COUNTRY = "license_country"
        private const val KEY_SELECTED_VEHICLE_ID = "selected_vehicle_id"
        private const val KEY_EMERGENCY_NAME = "emergency_name"
        private const val KEY_EMERGENCY_PHONE = "emergency_phone"
    }
}
