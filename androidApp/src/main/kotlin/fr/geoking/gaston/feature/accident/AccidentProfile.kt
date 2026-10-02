package fr.geoking.gaston.feature.accident

/**
 * Locally stored personal / emergency data used to pre-fill
 * European accident statement (constat amiable) assistance screens.
 *
 * Vehicle and insurance live on the shared "Ma voiture" garage
 * ([fr.geoking.gaston.UserVehicle]); [selectedVehicleId] picks which one
 * was involved.
 */
data class AccidentProfile(
    // Personal contact
    val fullName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val licenseNumber: String = "",
    val licenseCountry: String = "",
    /** Id of the [fr.geoking.gaston.UserVehicle] involved (from Ma voiture garage). */
    val selectedVehicleId: String = "",
    // Optional emergency contact
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
) {
    fun isPartiallyFilled(): Boolean =
        fullName.isNotBlank() ||
            phone.isNotBlank() ||
            selectedVehicleId.isNotBlank() ||
            emergencyContactName.isNotBlank()
}
