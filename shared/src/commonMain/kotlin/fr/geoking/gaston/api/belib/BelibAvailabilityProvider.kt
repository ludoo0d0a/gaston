package fr.geoking.gaston.api.belib

/**
 * [BorneAvailabilityProvider] implementation for Belib' (Paris) real-time availability
 * via Paris Data Opendatasoft API.
 */
class BelibAvailabilityProvider(
    private val client: BelibAvailabilityClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int
    ): List<PdcAvailability> {
        val records = client.getAvailability(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit
        )
        return records.map { record ->
            PdcAvailability(
                id = record.idPdc,
                status = mapStatut(record.statutPdc),
                latitude = record.latitude,
                longitude = record.longitude,
                address = record.adresseStation,
                stationId = stationIdFromPdcId(record.idPdc)
            )
        }
    }

    internal fun mapStatut(statutPdc: String): AvailabilityStatus {
        val s = statutPdc.trim().lowercase()
        return when {
            s == "disponible" -> AvailabilityStatus.Available
            s.contains("occupé") || s == "occupe" -> AvailabilityStatus.Occupied
            s.contains("maintenance") -> AvailabilityStatus.Maintenance
            s == "réservé" || s == "reserve" -> AvailabilityStatus.Reserved
            s.contains("pas implémenté") || s.contains("pas implemente") -> AvailabilityStatus.NotImplemented
            s.contains("en cours de mise en service") -> AvailabilityStatus.ComingIntoService
            s.contains("mise en service planifiée") || s.contains("planifiée") -> AvailabilityStatus.PlannedIntoService
            s == "supprimé" || s == "supprime" -> AvailabilityStatus.Removed
            s == "inconnu" -> AvailabilityStatus.Unknown
            else -> AvailabilityStatus.Unknown
        }
    }

    /** Derive a station key from PDC id (e.g. FR*V75*E9004*01*1 -> FR*V75*E9004*01) for grouping. */
    internal fun stationIdFromPdcId(idPdc: String): String? {
        val parts = idPdc.split("*")
        return if (parts.size >= 5) parts.dropLast(1).joinToString("*") else null
    }
}
