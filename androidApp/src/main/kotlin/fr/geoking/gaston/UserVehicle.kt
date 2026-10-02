package fr.geoking.gaston

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One entry in the shared "Ma voiture" garage.
 * Used for map filters (via [AppSettings] active vehicle), consumption/energy prefs, and accident/constat prefill.
 */
@Serializable
data class UserVehicle(
    val id: String,
    val brand: String = "",
    val model: String = "",
    val plate: String = "",
    val color: String = "",
    /** Vehicle serial number / VIN-style identifier for constat forms. */
    val serialNumber: String = "",
    val fuelCard: FuelCard = FuelCard.None,
    /** gas, electric, hybrid */
    val energy: String = "gas",
    val vehicleType: VehicleType = VehicleType.Car,
    val gasTypes: Set<String> = emptySet(),
    val powerLevels: Set<Int> = emptySet(),
    val gasTankCapacityLiters: Float? = null,
    val gasConsumptionLper100km: Float? = null,
    val batteryCapacityKwh: Float? = null,
    val evRangeKm: Int = DEFAULT_EV_RANGE_KM,
    val evConsumptionKwhPer100km: Float? = null,
    val insurerName: String = "",
    val policyNumber: String = "",
    val greenCardNumber: String = "",
    val insurerPhone: String = "",
    val agencyName: String = "",
    val agencyPhone: String = "",
) {
    fun displayLabel(): String {
        val name = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")
        return when {
            name.isNotBlank() && plate.isNotBlank() -> "$name · $plate"
            name.isNotBlank() -> name
            plate.isNotBlank() -> plate
            else -> ""
        }
    }

    fun hasIdentity(): Boolean =
        brand.isNotBlank() || model.isNotBlank() || plate.isNotBlank() || serialNumber.isNotBlank()

    fun hasInsurance(): Boolean =
        insurerName.isNotBlank() ||
            policyNumber.isNotBlank() ||
            greenCardNumber.isNotBlank() ||
            insurerPhone.isNotBlank() ||
            agencyName.isNotBlank() ||
            agencyPhone.isNotBlank()
}

fun newUserVehicleId(): String = UUID.randomUUID().toString()

fun AppSettings.activeVehicle(): UserVehicle? =
    vehicles.firstOrNull { it.id == activeVehicleId } ?: vehicles.firstOrNull()

fun AppSettings.vehicleById(id: String): UserVehicle? =
    vehicles.firstOrNull { it.id == id }

/** Mirror garage active entry into flat filter/consumption fields used across the app. */
fun AppSettings.withFlatFieldsFrom(vehicle: UserVehicle): AppSettings = copy(
    vehicleBrand = vehicle.brand,
    vehicleModel = vehicle.model,
    fuelCard = vehicle.fuelCard,
    vehicleEnergy = vehicle.energy,
    vehicleType = vehicle.vehicleType,
    vehicleGasTypes = vehicle.gasTypes,
    vehiclePowerLevels = vehicle.powerLevels,
    gasTankCapacityLiters = vehicle.gasTankCapacityLiters,
    gasConsumptionLper100km = vehicle.gasConsumptionLper100km,
    batteryCapacityKwh = vehicle.batteryCapacityKwh,
    evRangeKm = vehicle.evRangeKm,
    evConsumptionKwhPer100km = vehicle.evConsumptionKwhPer100km,
)

/** Keep [activeVehicleId] valid and mirror the active garage entry into flat filter fields. */
fun AppSettings.ensureVehicleGarageConsistent(): AppSettings {
    if (vehicles.isEmpty()) {
        return copy(activeVehicleId = "")
    }
    val activeId = if (vehicles.any { it.id == activeVehicleId }) {
        activeVehicleId
    } else {
        vehicles.first().id
    }
    val active = vehicles.first { it.id == activeId }
    return copy(activeVehicleId = activeId).withFlatFieldsFrom(active)
}

fun AppSettings.selectActiveVehicle(id: String): AppSettings {
    val vehicle = vehicles.firstOrNull { it.id == id } ?: return this
    return withFlatFieldsFrom(vehicle).copy(activeVehicleId = vehicle.id)
}

fun AppSettings.upsertVehicle(vehicle: UserVehicle, makeActive: Boolean = false): AppSettings {
    val exists = vehicles.any { it.id == vehicle.id }
    val list = if (exists) {
        vehicles.map { if (it.id == vehicle.id) vehicle else it }
    } else {
        vehicles + vehicle
    }
    val next = copy(vehicles = list)
    return if (makeActive || next.activeVehicleId.isBlank() || next.activeVehicleId == vehicle.id) {
        next.selectActiveVehicle(vehicle.id)
    } else {
        next
    }
}

fun AppSettings.removeVehicle(id: String): AppSettings {
    val list = vehicles.filterNot { it.id == id }
    if (list.isEmpty()) {
        return copy(
            vehicles = emptyList(),
            activeVehicleId = "",
            vehicleBrand = "",
            vehicleModel = "",
            fuelCard = FuelCard.None,
            vehicleEnergy = "gas",
            vehicleType = VehicleType.Car,
            vehicleGasTypes = emptySet(),
            vehiclePowerLevels = emptySet(),
            gasTankCapacityLiters = null,
            gasConsumptionLper100km = null,
            batteryCapacityKwh = null,
            evRangeKm = DEFAULT_EV_RANGE_KM,
            evConsumptionKwhPer100km = null,
        )
    }
    val nextActive = if (activeVehicleId == id) list.first().id else activeVehicleId
    return copy(vehicles = list, activeVehicleId = nextActive).selectActiveVehicle(nextActive)
}

fun AppSettings.addEmptyVehicle(makeActive: Boolean = true): AppSettings {
    val vehicle = UserVehicle(id = newUserVehicleId())
    return upsertVehicle(vehicle, makeActive = makeActive)
}
