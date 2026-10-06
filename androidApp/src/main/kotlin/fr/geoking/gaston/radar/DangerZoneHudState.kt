package fr.geoking.gaston.radar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HUD banner state for AAC danger-zone presence (zone + VMA — never a control pin).
 */
data class DangerZoneHudState(
    val active: Boolean = false,
    val speedLimitKmH: Int? = null,
)

/**
 * Shared presence indicator for phone Compose HUD and Android Auto map canvas badge.
 */
class DangerZoneHudStore {
    private val _hudState = MutableStateFlow(DangerZoneHudState())
    val hudState: StateFlow<DangerZoneHudState> = _hudState.asStateFlow()

    fun set(state: DangerZoneHudState) {
        _hudState.value = state
    }

    fun clear() {
        _hudState.value = DangerZoneHudState()
    }
}
