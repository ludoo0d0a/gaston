package fr.geoking.gaston.shared.action

/** Result of a device-side action (e.g. weather lookup). Kept for shared weather helpers. */
data class ActionResult(
    val success: Boolean,
    val message: String
)
