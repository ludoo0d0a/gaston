package fr.geoking.gaston.api.qualicharge

import fr.geoking.gaston.api.common.CsvUtils
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client for QualiCharge IRVE dynamique (real-time PDC status) via transport.data.gouv.fr.
 * Joins with the static companion feed for coordinates / station id.
 * No API key required.
 */
class QualiChargeDynamiqueClient(
    private val client: HttpClient,
    private val dynamicUrl: String = DEFAULT_DYNAMIC_URL,
    private val staticUrl: String = DEFAULT_STATIC_URL,
    private val dynamicCacheTtlMs: Long = 45_000L,
    private val staticCacheTtlMs: Long = 3_600_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private var dynamicCache: CachedDynamic? = null
    private var staticCache: CachedStatic? = null

    /**
     * Returns PDC availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Downloads (or reuses cached) national CSV feeds, joins by [id_pdc_itinerance], then filters.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200
    ): List<QualiChargePdcRecord> {
        val dynamic = getOrFetchDynamic()
        val staticByPdc = getOrFetchStatic()
        if (dynamic.isEmpty() || staticByPdc.isEmpty()) return emptyList()

        return dynamic.asSequence()
            .mapNotNull { dyn ->
                val geo = staticByPdc[dyn.idPdcItinerance] ?: return@mapNotNull null
                val dist = haversineKm(latitude, longitude, geo.latitude, geo.longitude)
                if (dist > radiusKm) return@mapNotNull null
                QualiChargePdcRecord(
                    idPdcItinerance = dyn.idPdcItinerance,
                    etatPdc = dyn.etatPdc,
                    occupationPdc = dyn.occupationPdc,
                    latitude = geo.latitude,
                    longitude = geo.longitude,
                    stationId = geo.stationId,
                    distanceKm = dist
                ) to dist
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    internal fun parseDynamicCsv(text: String): List<QualiChargeDynamicRow> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = CsvUtils.parseLine(lines.first())
        val idxPdc = header.indexOf("id_pdc_itinerance")
        val idxEtat = header.indexOf("etat_pdc")
        val idxOcc = header.indexOf("occupation_pdc")
        if (idxPdc < 0 || idxEtat < 0 || idxOcc < 0) return emptyList()

        val out = ArrayList<QualiChargeDynamicRow>(lines.size - 1)
        for (i in 1 until lines.size) {
            val fields = CsvUtils.parseLine(lines[i])
            val id = fields.getOrNull(idxPdc)?.trim().orEmpty()
            if (id.isEmpty()) continue
            out.add(
                QualiChargeDynamicRow(
                    idPdcItinerance = id,
                    etatPdc = fields.getOrNull(idxEtat)?.trim().orEmpty(),
                    occupationPdc = fields.getOrNull(idxOcc)?.trim().orEmpty()
                )
            )
        }
        return out
    }

    internal fun parseStaticCsv(text: String): Map<String, QualiChargeStaticGeo> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return emptyMap()
        val header = CsvUtils.parseLine(lines.next())
        val idxPdc = header.indexOf("id_pdc_itinerance")
        val idxStation = header.indexOf("id_station_itinerance")
        val idxXy = header.indexOf("coordonneesXY")
        if (idxPdc < 0 || idxXy < 0) return emptyMap()

        val out = HashMap<String, QualiChargeStaticGeo>(65_536)
        while (lines.hasNext()) {
            val fields = CsvUtils.parseLine(lines.next())
            val id = fields.getOrNull(idxPdc)?.trim().orEmpty()
            if (id.isEmpty()) continue
            val (lon, lat) = parseCoordonneesXy(fields.getOrNull(idxXy)) ?: continue
            val stationId = fields.getOrNull(idxStation)?.trim()?.takeIf { it.isNotBlank() }
            out[id] = QualiChargeStaticGeo(
                stationId = stationId,
                latitude = lat,
                longitude = lon
            )
        }
        return out
    }

    /** Maps QualiCharge etat/occupation to Belib-aligned availability status. */
    internal fun mapStatus(etatPdc: String, occupationPdc: String): fr.geoking.gaston.api.belib.AvailabilityStatus {
        val etat = etatPdc.trim().lowercase()
        val occ = occupationPdc.trim().lowercase()
        return when {
            etat == "hors_service" -> fr.geoking.gaston.api.belib.AvailabilityStatus.Maintenance
            etat == "inconnu" && (occ.isEmpty() || occ == "inconnu") ->
                fr.geoking.gaston.api.belib.AvailabilityStatus.Unknown
            occ == "libre" -> fr.geoking.gaston.api.belib.AvailabilityStatus.Available
            occ == "occupe" || occ == "occupé" -> fr.geoking.gaston.api.belib.AvailabilityStatus.Occupied
            occ == "reserve" || occ == "réservé" -> fr.geoking.gaston.api.belib.AvailabilityStatus.Reserved
            etat == "en_service" && occ == "inconnu" -> fr.geoking.gaston.api.belib.AvailabilityStatus.Unknown
            else -> fr.geoking.gaston.api.belib.AvailabilityStatus.Unknown
        }
    }

    private suspend fun getOrFetchDynamic(): List<QualiChargeDynamicRow> = mutex.withLock {
        val cached = dynamicCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < dynamicCacheTtlMs) return@withLock cached.rows
        val text = fetchText(dynamicUrl, "QualiCharge dynamique")
        val rows = parseDynamicCsv(text)
        dynamicCache = CachedDynamic(rows, now)
        rows
    }

    private suspend fun getOrFetchStatic(): Map<String, QualiChargeStaticGeo> = mutex.withLock {
        val cached = staticCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < staticCacheTtlMs) return@withLock cached.byPdc
        val text = fetchText(staticUrl, "QualiCharge statique")
        val byPdc = parseStaticCsv(text)
        staticCache = CachedStatic(byPdc, now)
        byPdc
    }

    private suspend fun fetchText(url: String, label: String): String {
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "$label API error: $body")
        }
        return body
    }

    companion object {
        const val DEFAULT_DYNAMIC_URL =
            "https://proxy.transport.data.gouv.fr/resource/qualicharge-irve-dynamique"
        const val DEFAULT_STATIC_URL =
            "https://proxy.transport.data.gouv.fr/resource/qualicharge-irve-statique"

        /** Parses `[lon, lat]` (with optional quotes/spaces). */
        internal fun parseCoordonneesXy(raw: String?): Pair<Double, Double>? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim().removePrefix("\"").removeSuffix("\"").trim()
            val inner = cleaned.removePrefix("[").removeSuffix("]").trim()
            val parts = inner.split(',').map { it.trim() }
            if (parts.size < 2) return null
            val lon = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            return lon to lat
        }
    }

    private data class CachedDynamic(val rows: List<QualiChargeDynamicRow>, val atMs: Long)
    private data class CachedStatic(val byPdc: Map<String, QualiChargeStaticGeo>, val atMs: Long)
}

data class QualiChargeDynamicRow(
    val idPdcItinerance: String,
    val etatPdc: String,
    val occupationPdc: String
)

data class QualiChargeStaticGeo(
    val stationId: String?,
    val latitude: Double,
    val longitude: Double
)

data class QualiChargePdcRecord(
    val idPdcItinerance: String,
    val etatPdc: String,
    val occupationPdc: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val distanceKm: Double = 0.0
)
