package fr.geoking.gaston.feature.maintenance

enum class MaintenanceEventType {
    SERVICE,
    FUEL,
    CHARGE,
    ODOMETER,
    EXPENSE,
    ;

    companion object {
        fun fromStorage(value: String): MaintenanceEventType =
            entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}

enum class ServiceKind {
    OIL_CHANGE,
    TIRES,
    BRAKES,
    INSPECTION,
    REVISION,
    OTHER,
    ;

    companion object {
        fun fromStorage(value: String?): ServiceKind? =
            value?.let { raw -> entries.firstOrNull { it.name == raw } }
    }
}

data class MaintenanceEvent(
    val id: String,
    val vehicleId: String,
    val type: MaintenanceEventType,
    val dateEpochMs: Long,
    val odometerKm: Int? = null,
    val title: String = "",
    val notes: String = "",
    val amountEur: Double? = null,
    val volumeLiters: Double? = null,
    val energyKwh: Double? = null,
    val serviceKind: ServiceKind? = null,
    val stationName: String = "",
)

data class ServiceInterval(
    val id: String,
    val vehicleId: String,
    val serviceKind: ServiceKind,
    val everyKm: Int? = null,
    val everyMonths: Int? = null,
    val lastEventId: String? = null,
    val lastDoneAtMs: Long? = null,
    val lastOdometerKm: Int? = null,
)
