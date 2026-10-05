package fr.geoking.gaston.parked

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the active [ParkCandidate] across process survival for the post-AA walk-away window.
 */
class ParkCandidateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _candidate = MutableStateFlow(load())
    val candidate: StateFlow<ParkCandidate?> = _candidate.asStateFlow()

    fun get(): ParkCandidate? = _candidate.value

    fun save(candidate: ParkCandidate) {
        prefs.edit()
            .putString(KEY_VEHICLE_ID, candidate.vehicleId)
            .putLong(KEY_LAT_BITS, candidate.latitude.toBits())
            .putLong(KEY_LON_BITS, candidate.longitude.toBits())
            .putLong(KEY_CREATED_AT, candidate.createdAtEpochMs)
            .putBoolean(KEY_AA_SHOWN, candidate.aaSuggestionShown)
            .putBoolean(KEY_SAVED_FROM_AA, candidate.savedFromAa)
            .apply()
        _candidate.value = candidate
    }

    fun markAaSuggestionShown() {
        val current = _candidate.value ?: return
        save(current.copy(aaSuggestionShown = true))
    }

    fun markSavedFromAa() {
        val current = _candidate.value ?: return
        save(current.copy(savedFromAa = true))
    }

    fun clear() {
        prefs.edit().clear().apply()
        _candidate.value = null
    }

    private fun load(): ParkCandidate? {
        if (!prefs.contains(KEY_VEHICLE_ID)) return null
        val vehicleId = prefs.getString(KEY_VEHICLE_ID, null) ?: return null
        return ParkCandidate(
            vehicleId = vehicleId,
            latitude = Double.fromBits(prefs.getLong(KEY_LAT_BITS, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_LON_BITS, 0L)),
            createdAtEpochMs = prefs.getLong(KEY_CREATED_AT, 0L),
            aaSuggestionShown = prefs.getBoolean(KEY_AA_SHOWN, false),
            savedFromAa = prefs.getBoolean(KEY_SAVED_FROM_AA, false),
        )
    }

    companion object {
        private const val PREFS_NAME = "park_candidate"
        private const val KEY_VEHICLE_ID = "vehicle_id"
        private const val KEY_LAT_BITS = "lat_bits"
        private const val KEY_LON_BITS = "lon_bits"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_AA_SHOWN = "aa_shown"
        private const val KEY_SAVED_FROM_AA = "saved_from_aa"
    }
}
