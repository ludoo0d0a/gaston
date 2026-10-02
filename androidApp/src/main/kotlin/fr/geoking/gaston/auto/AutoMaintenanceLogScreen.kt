package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.activeVehicle
import fr.geoking.gaston.feature.maintenance.MaintenanceReminderEvaluator
import fr.geoking.gaston.feature.maintenance.MaintenanceRepository
import fr.geoking.gaston.feature.maintenance.ServiceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Read-only Android Auto view: last service + due reminders for the active vehicle.
 * Terminal list screen (no further pushes) to stay within template quota.
 */
class AutoMaintenanceLogScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val repository: MaintenanceRepository,
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var loading = true
    private var lastServiceTitle: String? = null
    private var lastServiceSubtitle: String? = null
    private var dueLines: List<Pair<String, String>> = emptyList()

    init {
        scope.launch {
            load()
        }
    }

    private suspend fun load() {
        val vehicleId = settingsManager.settings.value.activeVehicle()?.id.orEmpty()
        if (vehicleId.isBlank()) {
            loading = false
            invalidate()
            return
        }
        val (service, intervals, odo) = withContext(Dispatchers.IO) {
            Triple(
                repository.latestService(vehicleId),
                repository.listIntervals(vehicleId),
                repository.latestOdometerKm(vehicleId),
            )
        }
        if (service != null) {
            lastServiceTitle = carContext.getString(R.string.maintenance_auto_last_service)
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(service.dateEpochMs))
            val kind = service.serviceKind?.let { kindLabel(it) }.orEmpty()
            val km = service.odometerKm?.let { "$it km" }.orEmpty()
            lastServiceSubtitle = listOf(kind, date, km).filter { it.isNotBlank() }.joinToString(" · ")
                .ifBlank { service.title.ifBlank { service.type.name } }
        } else {
            lastServiceTitle = carContext.getString(R.string.maintenance_auto_last_service)
            lastServiceSubtitle = carContext.getString(R.string.maintenance_auto_no_service)
        }
        val due = MaintenanceReminderEvaluator.evaluate(intervals, odo)
        dueLines = due.map { reminder ->
            val title = kindLabel(reminder.interval.serviceKind)
            val parts = buildList {
                if (reminder.dueByKm) add(carContext.getString(R.string.maintenance_due_badge))
                if (reminder.dueByDate) add(carContext.getString(R.string.maintenance_due_badge))
                reminder.kmRemaining?.let { add("$it km") }
            }.distinct().joinToString(" · ")
            title to parts.ifBlank { carContext.getString(R.string.maintenance_auto_upcoming) }
        }
        loading = false
        invalidate()
    }

    private fun kindLabel(kind: ServiceKind): String = carContext.getString(
        when (kind) {
            ServiceKind.OIL_CHANGE -> R.string.maintenance_service_oil
            ServiceKind.TIRES -> R.string.maintenance_service_tires
            ServiceKind.BRAKES -> R.string.maintenance_service_brakes
            ServiceKind.INSPECTION -> R.string.maintenance_service_inspection
            ServiceKind.REVISION -> R.string.maintenance_service_revision
            ServiceKind.OTHER -> R.string.maintenance_service_other
        }
    )

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoMaintenanceLogScreen") {
        val listLimit = try {
            carContext.getCarService(androidx.car.app.constraints.ConstraintManager::class.java)
                .getContentLimit(androidx.car.app.constraints.ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

        if (loading) {
            return@safeCarTemplate ListTemplate.Builder()
                .setLoading(true)
                .setHeader(
                    Header.Builder()
                        .setTitle(carContext.getString(R.string.maintenance_auto_title))
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .build()
        }

        val items = ItemList.Builder()
        lastServiceTitle?.let { title ->
            items.addItem(
                Row.Builder()
                    .setTitle(title.take(500))
                    .addText((lastServiceSubtitle ?: "").take(500))
                    .build()
            )
        }
        if (dueLines.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.maintenance_auto_none_due).take(500))
                    .build()
            )
        } else {
            dueLines.take((listLimit - 1).coerceAtLeast(1)).forEach { (title, subtitle) ->
                items.addItem(
                    Row.Builder()
                        .setTitle(title.take(500))
                        .addText(subtitle.take(500))
                        .build()
                )
            }
        }

        ListTemplate.Builder()
            .setSingleList(items.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.maintenance_auto_title))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
