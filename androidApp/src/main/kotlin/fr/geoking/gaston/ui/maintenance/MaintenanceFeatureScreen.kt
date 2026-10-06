package fr.geoking.gaston.ui.maintenance

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.UserVehicle
import fr.geoking.gaston.feature.maintenance.MaintenanceEvent
import fr.geoking.gaston.feature.maintenance.MaintenanceEventType
import fr.geoking.gaston.feature.maintenance.MaintenanceReminderEvaluator
import fr.geoking.gaston.feature.maintenance.MaintenanceStatsCalculator
import fr.geoking.gaston.feature.maintenance.ServiceInterval
import fr.geoking.gaston.feature.maintenance.ServiceKind
import fr.geoking.gaston.feature.maintenance.newMaintenanceId
import fr.geoking.gaston.ui.dashboard.GastonTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

private val MaintenanceAccent = Color(0xFF1565C0)
private val MaintenanceOnAccent = Color.White

private enum class MaintenanceDest {
    Hub,
    AddType,
    EditEvent,
    Stats,
    Intervals,
    EditInterval,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceFeatureScreen(
    onBack: () -> Unit,
    viewModel: MaintenanceViewModel = koinViewModel(),
) {
    var dest by remember { mutableStateOf(MaintenanceDest.Hub) }
    var editingEvent by remember { mutableStateOf<MaintenanceEvent?>(null) }
    var editingInterval by remember { mutableStateOf<ServiceInterval?>(null) }
    val hubListState = rememberLazyListState()

    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val events by viewModel.events.collectAsState()
    val intervals by viewModel.intervals.collectAsState()
    val dueReminders by viewModel.dueReminders.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val statsPeriod by viewModel.statsPeriod.collectAsState()
    val latestOdometer by viewModel.latestOdometer.collectAsState()
    val context = LocalContext.current
    val exportSubject = stringResource(R.string.maintenance_export_subject)
    val exportChooser = stringResource(R.string.maintenance_export)

    BackHandler {
        when (dest) {
            MaintenanceDest.Hub -> onBack()
            MaintenanceDest.EditEvent, MaintenanceDest.AddType -> dest = MaintenanceDest.Hub
            MaintenanceDest.Stats, MaintenanceDest.Intervals -> dest = MaintenanceDest.Hub
            MaintenanceDest.EditInterval -> dest = MaintenanceDest.Intervals
        }
    }

    GastonTheme {
        when (dest) {
            MaintenanceDest.Hub -> MaintenanceHubScreen(
                vehicles = vehicles,
                selectedVehicle = selectedVehicle,
                events = events,
                dueReminders = dueReminders,
                listState = hubListState,
                onBack = onBack,
                onSelectVehicle = viewModel::selectVehicle,
                onOpenAdd = { dest = MaintenanceDest.AddType },
                onOpenEvent = {
                    editingEvent = it
                    dest = MaintenanceDest.EditEvent
                },
                onOpenStats = { dest = MaintenanceDest.Stats },
                onOpenIntervals = { dest = MaintenanceDest.Intervals },
                onExport = {
                    val csv = viewModel.exportCsv()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_SUBJECT, exportSubject)
                        putExtra(Intent.EXTRA_TEXT, csv)
                    }
                    context.startActivity(Intent.createChooser(send, exportChooser))
                },
            )
            MaintenanceDest.AddType -> AddEventTypeScreen(
                vehicleEnergy = selectedVehicle?.energy.orEmpty(),
                onBack = { dest = MaintenanceDest.Hub },
                onPick = { type ->
                    editingEvent = viewModel.buildNewEventDraft(type)
                    dest = MaintenanceDest.EditEvent
                },
            )
            MaintenanceDest.EditEvent -> {
                val event = editingEvent
                if (event == null) {
                    dest = MaintenanceDest.Hub
                } else {
                    EditEventScreen(
                        event = event,
                        onBack = { dest = MaintenanceDest.Hub },
                        onSave = {
                            viewModel.saveEvent(it)
                            dest = MaintenanceDest.Hub
                        },
                        onDelete = {
                            viewModel.deleteEvent(event.id)
                            dest = MaintenanceDest.Hub
                        },
                    )
                }
            }
            MaintenanceDest.Stats -> StatsScreen(
                stats = stats,
                period = statsPeriod,
                vehicle = selectedVehicle,
                onPeriodChange = viewModel::setStatsPeriod,
                onBack = { dest = MaintenanceDest.Hub },
            )
            MaintenanceDest.Intervals -> IntervalsScreen(
                intervals = intervals,
                dueReminders = dueReminders,
                latestOdometer = latestOdometer,
                onBack = { dest = MaintenanceDest.Hub },
                onEdit = {
                    editingInterval = it
                    dest = MaintenanceDest.EditInterval
                },
                onAdd = {
                    val vehicleId = selectedVehicle?.id ?: return@IntervalsScreen
                    editingInterval = ServiceInterval(
                        id = newMaintenanceId(),
                        vehicleId = vehicleId,
                        serviceKind = ServiceKind.OTHER,
                        everyKm = 10_000,
                        everyMonths = 12,
                    )
                    dest = MaintenanceDest.EditInterval
                },
            )
            MaintenanceDest.EditInterval -> {
                val interval = editingInterval
                if (interval == null) {
                    dest = MaintenanceDest.Intervals
                } else {
                    EditIntervalScreen(
                        interval = interval,
                        onBack = { dest = MaintenanceDest.Intervals },
                        onSave = {
                            viewModel.saveInterval(it)
                            dest = MaintenanceDest.Intervals
                        },
                        onDelete = {
                            viewModel.deleteInterval(interval.id)
                            dest = MaintenanceDest.Intervals
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MaintenanceHubScreen(
    vehicles: List<UserVehicle>,
    selectedVehicle: UserVehicle?,
    events: List<MaintenanceEvent>,
    dueReminders: List<MaintenanceReminderEvaluator.DueReminder>,
    listState: LazyListState,
    onBack: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenEvent: (MaintenanceEvent) -> Unit,
    onOpenStats: () -> Unit,
    onOpenIntervals: () -> Unit,
    onExport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(
                            painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.maintenance_export),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAdd,
                containerColor = MaintenanceAccent,
                contentColor = MaintenanceOnAccent,
                modifier = Modifier.testTag("maintenance_add_fab"),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.maintenance_add))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("maintenance_hub"),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Text(
                    text = stringResource(R.string.maintenance_hub_intro),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (vehicles.isNotEmpty()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        vehicles.forEachIndexed { index, vehicle ->
                            val label = vehicle.displayLabel()
                                .ifBlank { stringResource(R.string.vehicle_unnamed, index + 1) }
                            FilterChip(
                                selected = vehicle.id == selectedVehicle?.id,
                                onClick = { onSelectVehicle(vehicle.id) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.maintenance_no_vehicle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (dueReminders.isNotEmpty()) {
                item {
                    Card(
                        onClick = onOpenIntervals,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("maintenance_due_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.maintenance_due_count, dueReminders.size),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                            )
                            Text(
                                text = stringResource(R.string.maintenance_due_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onOpenStats, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.maintenance_stats))
                    }
                    OutlinedButton(onClick = onOpenIntervals, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.maintenance_intervals))
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.maintenance_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(events, key = { it.id }) { event ->
                    EventRow(event = event, onClick = { onOpenEvent(event) })
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun EventRow(event: MaintenanceEvent, onClick: () -> Unit) {
    val dateLabel = remember(event.dateEpochMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(event.dateEpochMs))
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = eventTypeLabel(event.type),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(dateLabel, style = MaterialTheme.typography.bodySmall)
            }
            val subtitle = buildList {
                event.title.takeIf { it.isNotBlank() }?.let { add(it) }
                event.serviceKind?.let { add(serviceKindLabel(it)) }
                event.odometerKm?.let { add(stringResource(R.string.maintenance_km_value, it)) }
                event.volumeLiters?.let { add(stringResource(R.string.maintenance_liters_value, it)) }
                event.energyKwh?.let { add(stringResource(R.string.maintenance_kwh_value, it)) }
                event.amountEur?.let { add(stringResource(R.string.maintenance_eur_value, it)) }
            }.joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventTypeScreen(
    vehicleEnergy: String,
    onBack: () -> Unit,
    onPick: (MaintenanceEventType) -> Unit,
) {
    val types = buildList {
        add(MaintenanceEventType.SERVICE)
        when (vehicleEnergy) {
            "electric" -> add(MaintenanceEventType.CHARGE)
            "hybrid" -> {
                add(MaintenanceEventType.FUEL)
                add(MaintenanceEventType.CHARGE)
            }
            else -> add(MaintenanceEventType.FUEL)
        }
        add(MaintenanceEventType.ODOMETER)
        add(MaintenanceEventType.EXPENSE)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_add)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            types.forEach { type ->
                Card(
                    onClick = { onPick(type) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = eventTypeLabel(type),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditEventScreen(
    event: MaintenanceEvent,
    onBack: () -> Unit,
    onSave: (MaintenanceEvent) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(event.id) { mutableStateOf(event) }
    var odometerText by remember(event.id) { mutableStateOf(event.odometerKm?.toString().orEmpty()) }
    var amountText by remember(event.id) { mutableStateOf(event.amountEur?.toString().orEmpty()) }
    var volumeText by remember(event.id) { mutableStateOf(event.volumeLiters?.toString().orEmpty()) }
    var energyText by remember(event.id) { mutableStateOf(event.energyKwh?.toString().orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(eventTypeLabel(draft.type)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onSave(
                            draft.copy(
                                odometerKm = odometerText.toIntOrNull(),
                                amountEur = amountText.replace(',', '.').toDoubleOrNull(),
                                volumeLiters = volumeText.replace(',', '.').toDoubleOrNull(),
                                energyKwh = energyText.replace(',', '.').toDoubleOrNull(),
                            )
                        )
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.maintenance_field_title)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = odometerText,
                onValueChange = { odometerText = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.maintenance_field_odometer)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.type == MaintenanceEventType.SERVICE) {
                Text(stringResource(R.string.maintenance_field_service_kind), fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceKind.entries.forEach { kind ->
                        FilterChip(
                            selected = draft.serviceKind == kind,
                            onClick = { draft = draft.copy(serviceKind = kind) },
                            label = { Text(serviceKindLabel(kind)) },
                        )
                    }
                }
            }
            if (draft.type == MaintenanceEventType.FUEL) {
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text(stringResource(R.string.maintenance_field_liters)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.stationName,
                    onValueChange = { draft = draft.copy(stationName = it) },
                    label = { Text(stringResource(R.string.maintenance_field_station)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (draft.type == MaintenanceEventType.CHARGE) {
                OutlinedTextField(
                    value = energyText,
                    onValueChange = { energyText = it },
                    label = { Text(stringResource(R.string.maintenance_field_kwh)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.stationName,
                    onValueChange = { draft = draft.copy(stationName = it) },
                    label = { Text(stringResource(R.string.maintenance_field_station)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (draft.type != MaintenanceEventType.ODOMETER) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.maintenance_field_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { draft = draft.copy(notes = it) },
                label = { Text(stringResource(R.string.maintenance_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StatsScreen(
    stats: MaintenanceStatsCalculator.FillStats,
    period: StatsPeriod,
    vehicle: UserVehicle?,
    onPeriodChange: (StatsPeriod) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_stats)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { onPeriodChange(p) },
                        label = { Text(statsPeriodLabel(p)) },
                    )
                }
            }
            StatLine(stringResource(R.string.maintenance_stats_samples), stats.sampleCount.toString())
            StatLine(stringResource(R.string.maintenance_stats_distance), stringResource(R.string.maintenance_km_value, stats.totalDistanceKm))
            stats.litersPer100Km?.let {
                StatLine(stringResource(R.string.maintenance_stats_l100), stringResource(R.string.maintenance_l100_value, it))
                vehicle?.gasConsumptionLper100km?.let { ref ->
                    StatLine(stringResource(R.string.maintenance_stats_ref_gas), stringResource(R.string.maintenance_l100_value, ref.toDouble()))
                }
            }
            stats.kwhPer100Km?.let {
                StatLine(stringResource(R.string.maintenance_stats_kwh100), stringResource(R.string.maintenance_kwh100_value, it))
                vehicle?.evConsumptionKwhPer100km?.let { ref ->
                    StatLine(stringResource(R.string.maintenance_stats_ref_ev), stringResource(R.string.maintenance_kwh100_value, ref.toDouble()))
                }
            }
            StatLine(stringResource(R.string.maintenance_stats_total_cost), stringResource(R.string.maintenance_eur_value, stats.totalCostEur))
            stats.costPer100Km?.let {
                StatLine(stringResource(R.string.maintenance_stats_cost100), stringResource(R.string.maintenance_eur_value, it))
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalsScreen(
    intervals: List<ServiceInterval>,
    dueReminders: List<MaintenanceReminderEvaluator.DueReminder>,
    latestOdometer: Int?,
    onBack: () -> Unit,
    onEdit: (ServiceInterval) -> Unit,
    onAdd: () -> Unit,
) {
    val dueIds = dueReminders.map { it.interval.id }.toSet()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_intervals)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.maintenance_add))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            latestOdometer?.let {
                item {
                    Text(stringResource(R.string.maintenance_latest_odometer, it))
                }
            }
            items(intervals, key = { it.id }) { interval ->
                val due = interval.id in dueIds
                Card(
                    onClick = { onEdit(interval) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (due) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(serviceKindLabel(interval.serviceKind), fontWeight = FontWeight.Bold)
                        val parts = buildList {
                            interval.everyKm?.let { add(stringResource(R.string.maintenance_every_km, it)) }
                            interval.everyMonths?.let { add(stringResource(R.string.maintenance_every_months, it)) }
                        }
                        if (parts.isNotEmpty()) {
                            Text(parts.joinToString(" · "))
                        }
                        if (due) {
                            Text(
                                stringResource(R.string.maintenance_due_badge),
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditIntervalScreen(
    interval: ServiceInterval,
    onBack: () -> Unit,
    onSave: (ServiceInterval) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(interval.id) { mutableStateOf(interval) }
    var everyKmText by remember(interval.id) { mutableStateOf(interval.everyKm?.toString().orEmpty()) }
    var everyMonthsText by remember(interval.id) { mutableStateOf(interval.everyMonths?.toString().orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_intervals)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onSave(
                            draft.copy(
                                everyKm = everyKmText.toIntOrNull(),
                                everyMonths = everyMonthsText.toIntOrNull(),
                            )
                        )
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceKind.entries.forEach { kind ->
                    FilterChip(
                        selected = draft.serviceKind == kind,
                        onClick = { draft = draft.copy(serviceKind = kind) },
                        label = { Text(serviceKindLabel(kind)) },
                    )
                }
            }
            OutlinedTextField(
                value = everyKmText,
                onValueChange = { everyKmText = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.maintenance_field_every_km)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = everyMonthsText,
                onValueChange = { everyMonthsText = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.maintenance_field_every_months)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun eventTypeLabel(type: MaintenanceEventType): String = stringResource(
    when (type) {
        MaintenanceEventType.SERVICE -> R.string.maintenance_type_service
        MaintenanceEventType.FUEL -> R.string.maintenance_type_fuel
        MaintenanceEventType.CHARGE -> R.string.maintenance_type_charge
        MaintenanceEventType.ODOMETER -> R.string.maintenance_type_odometer
        MaintenanceEventType.EXPENSE -> R.string.maintenance_type_expense
    }
)

@Composable
private fun serviceKindLabel(kind: ServiceKind): String = stringResource(
    when (kind) {
        ServiceKind.OIL_CHANGE -> R.string.maintenance_service_oil
        ServiceKind.TIRES -> R.string.maintenance_service_tires
        ServiceKind.BRAKES -> R.string.maintenance_service_brakes
        ServiceKind.INSPECTION -> R.string.maintenance_service_inspection
        ServiceKind.REVISION -> R.string.maintenance_service_revision
        ServiceKind.OTHER -> R.string.maintenance_service_other
    }
)

@Composable
private fun statsPeriodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.Days30 -> R.string.maintenance_period_30
        StatsPeriod.Days90 -> R.string.maintenance_period_90
        StatsPeriod.Year -> R.string.maintenance_period_year
        StatsPeriod.All -> R.string.maintenance_period_all
    }
)
