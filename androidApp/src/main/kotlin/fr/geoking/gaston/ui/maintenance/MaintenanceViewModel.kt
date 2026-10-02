package fr.geoking.gaston.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.UserVehicle
import fr.geoking.gaston.activeVehicle
import fr.geoking.gaston.feature.maintenance.MaintenanceCsvExporter
import fr.geoking.gaston.feature.maintenance.MaintenanceEvent
import fr.geoking.gaston.feature.maintenance.MaintenanceEventType
import fr.geoking.gaston.feature.maintenance.MaintenanceReminderEvaluator
import fr.geoking.gaston.feature.maintenance.MaintenanceReminderNotifier
import fr.geoking.gaston.feature.maintenance.MaintenanceRepository
import fr.geoking.gaston.feature.maintenance.MaintenanceStatsCalculator
import fr.geoking.gaston.feature.maintenance.ServiceInterval
import fr.geoking.gaston.feature.maintenance.ServiceKind
import fr.geoking.gaston.feature.maintenance.newMaintenanceId
import fr.geoking.gaston.vehicleById
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StatsPeriod(val days: Int?) {
    Days30(30),
    Days90(90),
    Year(365),
    All(null),
}

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModel(
    private val repository: MaintenanceRepository,
    private val settingsManager: SettingsManager,
    private val reminderNotifier: MaintenanceReminderNotifier,
) : ViewModel() {

    private val _selectedVehicleId = MutableStateFlow("")
    val selectedVehicleId: StateFlow<String> = _selectedVehicleId.asStateFlow()

    val vehicles: StateFlow<List<UserVehicle>> = settingsManager.settings
        .map { it.vehicles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedVehicle: StateFlow<UserVehicle?> = combine(
        _selectedVehicleId,
        settingsManager.settings,
    ) { id, settings ->
        settings.vehicleById(id) ?: settings.activeVehicle() ?: settings.vehicles.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<MaintenanceEvent>> = selectedVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(emptyList())
            else repository.observeEvents(vehicle.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val intervals: StateFlow<List<ServiceInterval>> = selectedVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(emptyList())
            else repository.observeIntervals(vehicle.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _statsPeriod = MutableStateFlow(StatsPeriod.Days90)
    val statsPeriod: StateFlow<StatsPeriod> = _statsPeriod.asStateFlow()

    val stats: StateFlow<MaintenanceStatsCalculator.FillStats> = combine(
        events,
        _statsPeriod,
    ) { list, period ->
        val fromMs = period.days?.let {
            System.currentTimeMillis() - it * 24L * 60L * 60L * 1000L
        } ?: 0L
        MaintenanceStatsCalculator.compute(list, fromMs)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MaintenanceStatsCalculator.FillStats.EMPTY,
    )

    val dueReminders: StateFlow<List<MaintenanceReminderEvaluator.DueReminder>> = combine(
        intervals,
        events,
    ) { ints, evs ->
        val odo = evs.firstOrNull { it.odometerKm != null }?.odometerKm
        MaintenanceReminderEvaluator.evaluate(ints, odo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _latestOdometer = MutableStateFlow<Int?>(null)
    val latestOdometer: StateFlow<Int?> = _latestOdometer.asStateFlow()

    init {
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                val preferred = _selectedVehicleId.value
                val resolved = when {
                    preferred.isNotBlank() && settings.vehicles.any { it.id == preferred } -> preferred
                    settings.activeVehicleId.isNotBlank() -> settings.activeVehicleId
                    else -> settings.vehicles.firstOrNull()?.id.orEmpty()
                }
                if (resolved != _selectedVehicleId.value) {
                    _selectedVehicleId.value = resolved
                }
            }
        }
        viewModelScope.launch {
            selectedVehicle.collect { vehicle ->
                if (vehicle != null) {
                    repository.ensureDefaultIntervals(vehicle.id)
                    _latestOdometer.value = repository.latestOdometerKm(vehicle.id)
                    val due = MaintenanceReminderEvaluator.evaluate(
                        repository.listIntervals(vehicle.id),
                        repository.latestOdometerKm(vehicle.id),
                    )
                    if (due.isNotEmpty()) {
                        reminderNotifier.notifyDue(due)
                    }
                } else {
                    _latestOdometer.value = null
                }
            }
        }
    }

    fun selectVehicle(id: String) {
        _selectedVehicleId.value = id
    }

    fun setStatsPeriod(period: StatsPeriod) {
        _statsPeriod.value = period
    }

    fun saveEvent(event: MaintenanceEvent) {
        viewModelScope.launch {
            repository.upsertEvent(event)
            _latestOdometer.value = repository.latestOdometerKm(event.vehicleId)
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            val vehicleId = selectedVehicle.value?.id
            repository.deleteEvent(id)
            if (vehicleId != null) {
                _latestOdometer.value = repository.latestOdometerKm(vehicleId)
            }
        }
    }

    fun saveInterval(interval: ServiceInterval) {
        viewModelScope.launch {
            repository.upsertInterval(interval)
        }
    }

    fun deleteInterval(id: String) {
        viewModelScope.launch {
            repository.deleteInterval(id)
        }
    }

    fun deleteAllForVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.deleteAllForVehicle(vehicleId)
        }
    }

    fun buildNewEventDraft(type: MaintenanceEventType): MaintenanceEvent? {
        val vehicle = selectedVehicle.value ?: return null
        return MaintenanceEvent(
            id = newMaintenanceId(),
            vehicleId = vehicle.id,
            type = type,
            dateEpochMs = System.currentTimeMillis(),
            odometerKm = _latestOdometer.value,
            serviceKind = if (type == MaintenanceEventType.SERVICE) ServiceKind.OIL_CHANGE else null,
        )
    }

    fun exportCsv(): String = MaintenanceCsvExporter.export(events.value)
}
