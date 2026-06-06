package fr.geoking.gaston.api.it

import fr.geoking.gaston.api.common.CsvUtils
import fr.geoking.gaston.poi.radiusKmFromMapViewport
import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Italy MIMIT open data:
 * - anagrafica_impianti_attivi.csv (stations)
 * - prezzo_alle_8.csv (prices)
 *
 * Pipe-delimited (|) since Feb 2026.
 */
class MimitFuelProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 150,
    private val cacheMaxAgeMs: Long = 6 * 60 * 60_000L,
) : PoiProvider {

    private val mutex = Mutex()
    private var cachedAtMs: Long = 0L
    private var cachedStations: Map<String, MimitStation> = emptyMap()
    private var cachedPrices: Map<String, List<FuelPrice>> = emptyMap()

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let { radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx).coerceIn(1, 50) }
            ?: radiusKm

        ensureCache()
        if (cachedStations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            cachedStations.values.asSequence()
                .map { s -> s to haversineKm(latitude, longitude, s.lat, s.lon) }
                .filter { (_, km) -> km <= effectiveRadiusKm }
                .sortedBy { it.second }
                .take(limit)
                .map { (s, _) ->
                    val prices = cachedPrices[s.id]
                    Poi(
                        id = "mimit:${s.id}",
                        name = s.name ?: s.brand ?: "Fuel station",
                        address = buildString {
                            append(s.address ?: "")
                            if (!s.city.isNullOrBlank()) {
                                if (isNotEmpty()) append(", ")
                                append(s.city)
                            }
                            append(" IT")
                        },
                        latitude = s.lat,
                        longitude = s.lon,
                        brand = s.brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices?.ifEmpty { null },
                        source = "MIMIT (Italy)"
                    )
                }
                .toList()
        }
    }

    override suspend fun clearCache() {
        cachedAtMs = 0L
        cachedStations = emptyMap()
        cachedPrices = emptyMap()
    }

    private suspend fun ensureCache() {
        val now = System.currentTimeMillis()
        if (cachedStations.isNotEmpty() && now - cachedAtMs < cacheMaxAgeMs) return
        mutex.withLock {
            val now2 = System.currentTimeMillis()
            if (cachedStations.isNotEmpty() && now2 - cachedAtMs < cacheMaxAgeMs) return@withLock

            val stations = fetchStations()
            val prices = fetchPrices()
            cachedStations = stations
            cachedPrices = prices
            cachedAtMs = System.currentTimeMillis()
        }
    }

    private suspend fun fetchStations(): Map<String, MimitStation> {
        val url = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
        val body = try { client.get(url).bodyAsText() } catch (_: Exception) { return emptyMap() }
        val lines = body.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyMap()

        // Skip first line: "Estrazione del ..."
        val header = CsvUtils.parseLine(lines[1], delimiter = '|')
        val idx = header.withIndex().associate { it.value.trim() to it.index }

        fun col(row: List<String>, name: String): String? = idx[name]?.let { i -> row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() } }

        val out = LinkedHashMap<String, MimitStation>(lines.size)
        for (i in 2 until lines.size) {
            val row = CsvUtils.parseLine(lines[i], delimiter = '|')
            val id = col(row, "idImpianto") ?: continue
            val lat = col(row, "Latitudine")?.replace(',', '.')?.toDoubleOrNull() ?: continue
            val lon = col(row, "Longitudine")?.replace(',', '.')?.toDoubleOrNull() ?: continue
            out[id] = MimitStation(
                id = id,
                brand = col(row, "Bandiera"),
                name = col(row, "Nome Impianto"),
                address = col(row, "Indirizzo"),
                city = col(row, "Comune"),
                province = col(row, "Provincia"),
                lat = lat,
                lon = lon,
            )
        }
        return out
    }

    private suspend fun fetchPrices(): Map<String, List<FuelPrice>> {
        val url = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
        val body = try { client.get(url).bodyAsText() } catch (_: Exception) { return emptyMap() }
        val lines = body.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 3) return emptyMap()

        // Skip first line: "Estrazione del ..."
        val header = CsvUtils.parseLine(lines[1], delimiter = '|')
        val idx = header.withIndex().associate { it.value.trim() to it.index }
        fun col(row: List<String>, name: String): String? = idx[name]?.let { i -> row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() } }

        val out = HashMap<String, MutableList<FuelPrice>>(8192)
        for (i in 2 until lines.size) {
            val row = CsvUtils.parseLine(lines[i], delimiter = '|')
            val id = col(row, "idImpianto") ?: continue
            val fuel = col(row, "descCarburante") ?: continue
            val price = col(row, "prezzo")?.replace(',', '.')?.toDoubleOrNull() ?: continue
            val updatedAt = col(row, "dtComu")
            val isSelf = col(row, "isSelf") == "0" // inverted in the export: keep as metadata in name

            val normalizedFuel = when {
                fuel.equals("Benzina", ignoreCase = true) -> "SP95"
                fuel.contains("benzina", ignoreCase = true) -> "SP95"
                fuel.contains("gasolio", ignoreCase = true) -> "Diesel"
                fuel.contains("gpl", ignoreCase = true) -> "LPG"
                fuel.contains("metano", ignoreCase = true) -> "CNG"
                else -> fuel
            }
            val label = if (isSelf) "$normalizedFuel (self)" else normalizedFuel
            out.getOrPut(id) { mutableListOf() }.add(
                FuelPrice(fuelName = label, price = price, updatedAt = updatedAt)
            )
        }
        return out
    }
}

private data class MimitStation(
    val id: String,
    val brand: String?,
    val name: String?,
    val address: String?,
    val city: String?,
    val province: String?,
    val lat: Double,
    val lon: Double,
)

