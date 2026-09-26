package fr.geoking.gaston.api.fuelo

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FueloProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val fueloClient = FueloClient(client)
    private val mutex = Mutex()
    private val cachedStations = mutableMapOf<String, List<FueloStation>>()

    private val imgFuelMap = mapOf(
        "gasoline.png" to "SP95",
        "diesel.png" to "Gazole",
        "lpg.png" to "GPL",
        "gasoline95plus.png" to "SP95 Premium",
        "gasoline98.png" to "SP98",
        "gasoline98plus.png" to "SP98",
        "dieselplus.png" to "Gazole Premium",
        "methane.png" to "CNG",
        "cng.png" to "CNG",
        "lng.png" to "LNG",
        "adblue.png" to "AdBlue"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    /**
     * Countries without a dedicated fuel provider in Gaston.
     * Keep in sync with SettingsScreen `fueloSupported` and auto-mode fallbacks
     * (see [fr.geoking.gaston.poi.autoProvidersForCountries]).
     */
    private fun getConfigForLocation(lat: Double, lon: Double): Triple<String, String, FueloBounds>? {
        return when {
            // Bulgaria
            lat in 41.2..44.2 && lon in 22.4..28.6 -> Triple("bg", "BG", FueloBounds(41.2, 44.2, 22.4, 28.6))
            // Czechia
            lat in 48.55..51.06 && lon in 12.09..18.87 -> Triple("cz", "CZ", FueloBounds(48.55, 51.06, 12.09, 18.87))
            // Hungary
            lat in 45.7..48.6 && lon in 16.1..22.9 -> Triple("hu", "HU", FueloBounds(45.7, 48.6, 16.1, 22.9))
            // Poland
            lat in 49.0..54.85 && lon in 14.1..24.15 -> Triple("pl", "PL", FueloBounds(49.0, 54.85, 14.1, 24.15))
            // Slovakia
            lat in 47.7..49.6 && lon in 16.8..22.6 -> Triple("sk", "SK", FueloBounds(47.7, 49.6, 16.8, 22.6))
            // Estonia
            lat in 57.5..59.7 && lon in 21.8..28.2 -> Triple("ee", "EE", FueloBounds(57.5, 59.7, 21.8, 28.2))
            // Latvia
            lat in 55.7..58.1 && lon in 20.9..28.2 -> Triple("lv", "LV", FueloBounds(55.7, 58.1, 20.9, 28.2))
            // Lithuania
            lat in 53.9..56.5 && lon in 21.0..26.8 -> Triple("lt", "LT", FueloBounds(53.9, 56.5, 21.0, 26.8))
            // Bosnia
            lat in 42.5..45.3 && lon in 15.7..19.7 -> Triple("ba", "BA", FueloBounds(42.5, 45.3, 15.7, 19.7))
            // Turkey
            lat in 35.8..42.1 && lon in 25.6..44.8 -> Triple("tr", "TR", FueloBounds(35.8, 42.1, 25.6, 44.8))
            // North Macedonia
            lat in 40.8..42.4 && lon in 20.4..23.0 -> Triple("mk", "MK", FueloBounds(40.8, 42.4, 20.4, 23.0))
            else -> null
        }
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val config = getConfigForLocation(latitude, longitude) ?: return emptyList()
        val (subdomain, countryCode, bounds) = config

        val stations = getOrFetchStations(subdomain, bounds)
        if (stations.isEmpty()) return emptyList()

        val nearbyStations = stations.asSequence()
            .filter { s ->
                haversineKm(latitude, longitude, s.lat, s.lon) <= radiusKm
            }
            .take(limit)
            .toList()

        val results = mutableListOf<Poi>()
        for (s in nearbyStations) {
            val html = try {
                fueloClient.fetchStationInfo(subdomain, s.id)
            } catch (e: Exception) {
                null
            } ?: continue

            val parsed = parseInfoWindow(html)
            if (parsed.prices.isEmpty()) continue

            results.add(Poi(
                id = "fuelo_${subdomain}_${s.id}",
                name = parsed.name.ifEmpty { "Gas Station" },
                address = parsed.address,
                latitude = s.lat,
                longitude = s.lon,
                brand = brandFromLogo(s.logo),
                poiCategory = PoiCategory.Gas,
                fuelPrices = parsed.prices.ifEmpty { null },
                source = "Fuelo.net ($countryCode)"
            ))
            // Minimal rate limit as per Gaston logic
            delay(100)
        }

        return results
    }

    private suspend fun getOrFetchStations(subdomain: String, bounds: FueloBounds): List<FueloStation> = mutex.withLock {
        cachedStations[subdomain]?.let { return@withLock it }
        return try {
            val stations = fueloClient.fetchStationList(subdomain, bounds)
            cachedStations[subdomain] = stations
            stations
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseInfoWindow(html: String): ParsedInfoWindow {
        val nameMatch = Regex("<h4>([^<]+)</h4>").find(html)
        val name = nameMatch?.groupValues?.get(1)?.trim() ?: ""

        val addrMatch = Regex("<h5>([^<]+)</h5>").find(html)
        val fullAddr = addrMatch?.groupValues?.get(1)?.trim() ?: ""
        val addrParts = fullAddr.split(",").map { it.trim() }
        val address = if (addrParts.size >= 3) addrParts.drop(2).joinToString(", ") else fullAddr

        val prices = mutableListOf<FuelPrice>()
        val imgRegex = Regex("src=\"/img/fuels/default/([^\"]+)\"[^>]*title=\"([^\"]+)\"")
        imgRegex.findAll(html).forEach { match ->
            val imgFile = match.groupValues[1]
            val titleText = match.groupValues[2]

            val fuelName = imgFuelMap[imgFile] ?: return@forEach
            val priceMatch = Regex(":\\s*([\\d.,]+)\\s+\\S+/").find(titleText)
            if (priceMatch != null) {
                val priceStr = priceMatch.groupValues[1]
                val price = parsePrice(priceStr)
                if (price > 0) {
                    prices.add(FuelPrice(fuelName, price))
                }
            }
        }

        return ParsedInfoWindow(name, address, prices)
    }

    private fun parsePrice(raw: String): Double {
        val cleaned = raw.replace(" ", "")
        return if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        } else if (cleaned.contains(",")) {
            cleaned.replace(",", ".").toDoubleOrNull() ?: 0.0
        } else {
            cleaned.toDoubleOrNull() ?: 0.0
        }
    }

    private fun brandFromLogo(logo: String): String? {
        if (logo.isEmpty() || logo == "gasstation") return null
        val map = mapOf(
            "omv-new" to "OMV", "omv" to "OMV", "mol" to "MOL", "shell" to "Shell",
            "eni" to "Eni", "lukoil" to "Lukoil", "slovnaft" to "Slovnaft", "bp" to "BP",
            "avia" to "AVIA", "orlen" to "Orlen", "rompetrol" to "Rompetrol",
            "eko" to "EKO", "nis" to "NIS", "petrol" to "Petrol", "gazprom" to "Gazprom",
            "total-new" to "TotalEnergies", "total" to "TotalEnergies", "circle-k" to "Circle K",
            "galp" to "Galp", "prio" to "Prio", "alves-bandeira" to "Alves Bandeira"
        )
        return map[logo] ?: (logo.replaceFirstChar { it.uppercase() })
    }

    private data class ParsedInfoWindow(
        val name: String,
        val address: String,
        val prices: List<FuelPrice>
    )

    override suspend fun clearCache() {
        cachedStations.clear()
    }
}
