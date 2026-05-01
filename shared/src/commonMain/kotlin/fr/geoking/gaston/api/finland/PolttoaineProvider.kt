package fr.geoking.gaston.api.finland

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PolttoaineProvider(
    client: HttpClient,
    private val limit: Int = 20
) : PoiProvider {

    private val polttoaineClient = PolttoaineClient(client)

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        if (latitude < 59.7 || latitude > 70.1 || longitude < 20.5 || longitude > 31.6) {
            return emptyList()
        }

        val cities = listOf("Helsinki", "Espoo", "Vantaa", "Tampere", "Turku", "Oulu")
        val results = mutableListOf<Poi>()

        return withContext(Dispatchers.Default) {
            for (city in cities) {
                try {
                    val html = polttoaineClient.fetchCityPage(city)
                    val stations = parseCityPage(html)
                    for (s in stations) {
                        val coords = polttoaineClient.fetchCoordinates(s.mapId) ?: continue
                        if (haversineKm(latitude, longitude, coords.first, coords.second) <= 20.0) {
                            results.add(Poi(
                                id = "fi-${s.mapId}",
                                name = s.name,
                                address = s.address,
                                latitude = coords.first,
                                longitude = coords.second,
                                brand = extractBrand(s.name),
                                poiCategory = PoiCategory.Gas,
                                fuelPrices = s.prices,
                                source = "Polttoaine.net (Finland)"
                            ))
                        }
                        if (results.size >= limit) break
                    }
                } catch (e: Exception) { }
                if (results.size >= limit) break
            }
            results
        }
    }

    private data class PolttoaineStation(
        val mapId: String,
        val name: String,
        val address: String,
        val prices: List<FuelPrice>
    )

    private fun parseCityPage(html: String): List<PolttoaineStation> {
        val stations = mutableListOf<PolttoaineStation>()
        val trRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
        val tdRegex = Regex("<td[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)

        trRegex.findAll(html).forEach { trMatch ->
            val rowHtml = trMatch.groupValues[1]
            val cells = tdRegex.findAll(rowHtml).map { it.groupValues[1] }.toList()
            if (cells.size != 5) return@forEach

            val stationCell = cells[0]
            val mapIdMatch = Regex("id=(\\d+)", RegexOption.IGNORE_CASE).find(stationCell) ?: return@forEach
            val mapId = mapIdMatch.groupValues[1]

            var nameText = stationCell.replace(Regex("<a[^>]*>[\\s\\S]*?</a>", RegexOption.IGNORE_CASE), "")
            nameText = nameText.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()
            if (nameText.isEmpty()) return@forEach

            val prices = mutableListOf<FuelPrice>()
            extractPrice(cells[2])?.let { prices.add(FuelPrice("E10", it)) }
            extractPrice(cells[3])?.let { prices.add(FuelPrice("SP98", it)) }
            extractPrice(cells[4])?.let { prices.add(FuelPrice("Gazole", it)) }

            if (prices.isNotEmpty()) {
                val nameParts = nameText.split(",")
                val name = nameParts[0].trim()
                val address = if (nameParts.size > 1) nameParts[1].trim() else ""
                stations.add(PolttoaineStation(mapId, name, address, prices))
            }
        }
        return stations
    }

    private fun extractPrice(cellHtml: String): Double? {
        val text = cellHtml.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("*", "").trim()
        return text.toDoubleOrNull()?.takeIf { it in 0.8..4.0 }
    }

    private fun extractBrand(name: String): String? {
        val brands = listOf("ABC", "Neste", "Shell", "Teboil", "St1", "Seo")
        return brands.find { name.contains(it, ignoreCase = true) }
    }
}
