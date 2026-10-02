package fr.geoking.gaston.ui.accident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.UserVehicle
import fr.geoking.gaston.activeVehicle
import fr.geoking.gaston.addEmptyVehicle
import fr.geoking.gaston.feature.accident.AccidentProfile
import fr.geoking.gaston.feature.accident.AccidentProfileStore
import fr.geoking.gaston.newUserVehicleId
import fr.geoking.gaston.selectActiveVehicle
import fr.geoking.gaston.upsertVehicle
import fr.geoking.gaston.vehicleById
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccidentViewModel(
    private val store: AccidentProfileStore,
    private val settingsManager: SettingsManager,
) : ViewModel() {

    val profile: StateFlow<AccidentProfile> = store.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.profile.value)

    val vehicles: StateFlow<List<UserVehicle>> = settingsManager.settings
        .map { it.vehicles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsManager.settings.value.vehicles)

    val selectedVehicle: StateFlow<UserVehicle?> = combine(
        store.profile,
        settingsManager.settings,
    ) { profile, settings ->
        settings.vehicleById(profile.selectedVehicleId)
            ?: settings.activeVehicle()
            ?: settings.vehicles.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _otherDriverPhone = MutableStateFlow("")
    val otherDriverPhone: StateFlow<String> = _otherDriverPhone.asStateFlow()

    private val _savedMessage = MutableStateFlow(false)
    val savedMessage: StateFlow<Boolean> = _savedMessage.asStateFlow()

    /**
     * Select a garage vehicle for the accident profile when none is set yet.
     */
    fun seedFromVehicleSettingsIfNeeded() {
        val settings = settingsManager.settings.value
        val current = store.profile.value
        val resolvedId = when {
            current.selectedVehicleId.isNotBlank() &&
                settings.vehicles.any { it.id == current.selectedVehicleId } ->
                current.selectedVehicleId
            settings.activeVehicleId.isNotBlank() -> settings.activeVehicleId
            else -> settings.vehicles.firstOrNull()?.id.orEmpty()
        }
        if (resolvedId.isNotBlank() && resolvedId != current.selectedVehicleId) {
            store.save(current.copy(selectedVehicleId = resolvedId))
        }
    }

    fun selectVehicle(vehicleId: String) {
        val settings = settingsManager.settings.value
        if (settings.vehicles.none { it.id == vehicleId }) return
        store.save(store.profile.value.copy(selectedVehicleId = vehicleId))
    }

    fun addVehicle() {
        val updated = settingsManager.settings.value.addEmptyVehicle(makeActive = true)
        settingsManager.saveSettings(updated)
        val id = updated.activeVehicleId
        if (id.isNotBlank()) {
            store.save(store.profile.value.copy(selectedVehicleId = id))
        }
    }

    fun updateSelectedVehicle(transform: (UserVehicle) -> UserVehicle): String {
        val settings = settingsManager.settings.value
        val current = selectedVehicle.value
            ?: settings.activeVehicle()
            ?: UserVehicle(id = newUserVehicleId())
        val updated = transform(current)
        val makeActive = settings.activeVehicleId == updated.id || settings.vehicles.isEmpty()
        var next = settings.upsertVehicle(updated, makeActive = makeActive)
        if (!makeActive && next.activeVehicleId == updated.id) {
            next = next.selectActiveVehicle(updated.id)
        }
        settingsManager.saveSettings(next)
        if (store.profile.value.selectedVehicleId != updated.id) {
            store.save(store.profile.value.copy(selectedVehicleId = updated.id))
        }
        return updated.id
    }

    fun saveProfile(profile: AccidentProfile) {
        store.save(profile)
        viewModelScope.launch {
            _savedMessage.value = true
        }
    }

    fun clearSavedMessage() {
        _savedMessage.value = false
    }

    fun setOtherDriverPhone(phone: String) {
        _otherDriverPhone.value = phone
    }
}
