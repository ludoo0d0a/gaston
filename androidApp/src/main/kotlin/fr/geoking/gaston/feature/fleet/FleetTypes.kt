package fr.geoking.gaston.feature.fleet

/**
 * Represents the type of maintenance event
 */
enum class MaintenanceEventType {
    SERVICE,
    FUEL,
    CHARGE,
    ODOMETER,
    INSPECTION,
    RECALL,
    OTHER,
}

/**
 * Represents the kind of service for maintenance scheduling
 */
enum class ServiceKind {
    OIL_CHANGE,
    TIRES,
    BRAKES,
    INSPECTION,
    REVISION,
    OTHER,
}

/**
 * Priority level for fleet maintenance tasks
 */
enum class MaintenancePriority {
    HIGH,
    MEDIUM,
    LOW,
}
