package fr.geoking.gaston.api.gireve

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.common.CsvUtils
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client for Gireve IRVE static and dynamic feeds via transport.data.gouv.fr.
 * Provides real-time PDC (point de charge) status for over 34,000 charging points in France,
 * including extensive coverage for Freshmile and other EV networks.
 * No API key required.
 */
class GireveClient(
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
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 300
    ): List<GirevePdcRecord> {
        val dynamic = getOrFetchDynamic()
        val staticByPdc = getOrFetchStatic()
        if (dynamic.isEmpty() || staticByPdc.isEmpty()) return emptyList()

        return dynamic.asSequence()
            .mapNotNull { dyn ->
                val staticInfo = staticByPdc[dyn.idPdcItinerance] ?: return@mapNotNull null
                val dist = haversineKm(latitude, longitude, staticInfo.latitude, staticInfo.longitude)
                if (dist > radiusKm) return@mapNotNull null
                GirevePdcRecord(
                    idPdcItinerance = dyn.idPdcItinerance,
                    etatPdc = dyn.etatPdc,
                    occupationPdc = dyn.occupationPdc,
                    latitude = staticInfo.latitude,
                    longitude = staticInfo.longitude,
                    stationId = staticInfo.stationId,
                    stationName = staticInfo.stationName,
                    operatorName = staticInfo.operatorName,
                    amenageurName = staticInfo.amenageurName,
                    enseigneName = staticInfo.enseigneName,
                    address = staticInfo.address,
                    puissanceNominale = staticInfo.puissanceNominale,
                    distanceKm = dist
                ) to dist
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    internal fun parseDynamicCsv(text: String): List<GireveDynamicRow> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = CsvUtils.parseLine(lines.first())
        val idxPdc = header.indexOf("id_pdc_itinerance")
        val idxEtat = header.indexOf("etat_pdc")
        val idxOcc = header.indexOf("occupation_pdc")
        if (idxPdc < 0 || idxEtat < 0 || idxOcc < 0) return emptyList()

        val out = ArrayList<GireveDynamicRow>(lines.size - 1)
        for (i in 1 until lines.size) {
            val fields = CsvUtils.parseLine(lines[i])
            val id = fields.getOrNull(idxPdc)?.trim().orEmpty()
            if (id.isEmpty()) continue
            out.add(
                GireveDynamicRow(
                    idPdcItinerance = id,
                    etatPdc = fields.getOrNull(idxEtat)?.trim().orEmpty(),
                    occupationPdc = fields.getOrNull(idxOcc)?.trim().orEmpty()
                )
            )
        }
        return out
    }

    internal fun parseStaticCsv(text: String): Map<String, GireveStaticInfo> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return emptyMap()
        val header = CsvUtils.parseLine(lines.next())
        val idxPdc = header.indexOf("id_pdc_itinerance")
        val idxStation = header.indexOf("id_station_itinerance")
        val idxNomStation = header.indexOf("nom_station")
        val idxOp = header.indexOf("nom_operateur")
        val idxAmen = header.indexOf("nom_amenageur")
        val idxEns = header.indexOf("nom_enseigne")
        val idxAddr = header.indexOf("adresse_station")
        val idxXy = header.indexOf("coordonneesXY")
        val idxPuissance = header.indexOf("puissance_nominale").takeIf { it >= 0 }
            ?: header.indexOf("puissance_maximale")
        if (idxPdc < 0 || idxXy < 0) return emptyMap()

        val out = HashMap<String, GireveStaticInfo>(65_536)
        while (lines.hasNext()) {
            val fields = CsvUtils.parseLine(lines.next())
            val id = fields.getOrNull(idxPdc)?.trim().orEmpty()
            if (id.isEmpty()) continue
            val (lon, lat) = parseCoordonneesXy(fields.getOrNull(idxXy)) ?: continue
            val stationId = fields.getOrNull(idxStation)?.trim()?.takeIf { it.isNotBlank() }
            val stationName = fields.getOrNull(idxNomStation)?.trim()?.takeIf { it.isNotBlank() }
            val operatorName = fields.getOrNull(idxOp)?.trim()?.takeIf { it.isNotBlank() }
            val amenageurName = fields.getOrNull(idxAmen)?.trim()?.takeIf { it.isNotBlank() }
            val enseigneName = fields.getOrNull(idxEns)?.trim()?.takeIf { it.isNotBlank() }
            val address = fields.getOrNull(idxAddr)?.trim()?.takeIf { it.isNotBlank() }
            val puissanceNominale = if (idxPuissance >= 0) {
                fields.getOrNull(idxPuissance)?.trim()?.replace(',', '.')?.toDoubleOrNull()
            } else null

            out[id] = GireveStaticInfo(
                stationId = stationId,
                stationName = stationName,
                operatorName = operatorName,
                amenageurName = amenageurName,
                enseigneName = enseigneName,
                address = address,
                puissanceNominale = puissanceNominale,
                latitude = lat,
                longitude = lon
            )
        }
        return out
    }

    /** Maps Gireve etat/occupation to AvailabilityStatus. */
    internal fun mapStatus(etatPdc: String, occupationPdc: String): AvailabilityStatus {
        val etat = etatPdc.trim().lowercase()
        val occ = occupationPdc.trim().lowercase()
        return when {
            etat == "hors_service" -> AvailabilityStatus.Maintenance
            etat == "inconnu" && (occ.isEmpty() || occ == "inconnu") -> AvailabilityStatus.Unknown
            occ == "libre" -> AvailabilityStatus.Available
            occ == "occupe" || occ == "occupé" -> AvailabilityStatus.Occupied
            occ == "reserve" || occ == "réservé" -> AvailabilityStatus.Reserved
            etat == "en_service" && occ == "inconnu" -> AvailabilityStatus.Unknown
            else -> AvailabilityStatus.Unknown
        }
    }

    private suspend fun getOrFetchDynamic(): List<GireveDynamicRow> = mutex.withLock {
        val cached = dynamicCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < dynamicCacheTtlMs) return@withLock cached.rows
        val text = fetchText(dynamicUrl, "Gireve dynamique")
        val rows = parseDynamicCsv(text)
        dynamicCache = CachedDynamic(rows, now)
        rows
    }

    private suspend fun getOrFetchStatic(): Map<String, GireveStaticInfo> = mutex.withLock {
        val cached = staticCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < staticCacheTtlMs) return@withLock cached.byPdc
        val text = fetchText(staticUrl, "Gireve statique")
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
            "https://proxy.transport.data.gouv.fr/resource/gireve-irve-dynamique"
        const val DEFAULT_STATIC_URL =
            "https://proxy.transport.data.gouv.fr/resource/gireve-irve-statique?format=csv"

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

    private data class CachedDynamic(val rows: List<GireveDynamicRow>, val atMs: Long)
    private data class CachedStatic(val byPdc: Map<String, GireveStaticInfo>, val atMs: Long)
}

data class GireveDynamicRow(
    val idPdcItinerance: String,
    val etatPdc: String,
    val occupationPdc: String
)

data class GireveStaticInfo(
    val stationId: String?,
    val stationName: String?,
    val operatorName: String?,
    val amenageurName: String?,
    val enseigneName: String?,
    val address: String?,
    val puissanceNominale: Double? = null,
    val latitude: Double,
    val longitude: Double
)

data class GirevePdcRecord(
    val idPdcItinerance: String,
    val etatPdc: String,
    val occupationPdc: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val stationName: String? = null,
    val operatorName: String? = null,
    val amenageurName: String? = null,
    val enseigneName: String? = null,
    val address: String? = null,
    val puissanceNominale: Double? = null,
    val distanceKm: Double = 0.0
)
