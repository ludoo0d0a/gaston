package fr.geoking.gaston.feature.maintenance

/**
 * Builds a CSV export of maintenance events for share / backup.
 */
object MaintenanceCsvExporter {
    private const val HEADER =
        "id,vehicleId,type,dateEpochMs,odometerKm,title,notes,amountEur,volumeLiters,energyKwh,serviceKind,stationName"

    fun export(events: List<MaintenanceEvent>): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        events.sortedBy { it.dateEpochMs }.forEach { e ->
            sb.appendLine(
                listOf(
                    e.id,
                    e.vehicleId,
                    e.type.name,
                    e.dateEpochMs.toString(),
                    e.odometerKm?.toString().orEmpty(),
                    escape(e.title),
                    escape(e.notes),
                    e.amountEur?.toString().orEmpty(),
                    e.volumeLiters?.toString().orEmpty(),
                    e.energyKwh?.toString().orEmpty(),
                    e.serviceKind?.name.orEmpty(),
                    escape(e.stationName),
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
