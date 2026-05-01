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
            // Switzerland
            lat in 45.8..47.85 && lon in 5.9..10.55 -> Triple("ch", "CH", FueloBounds(45.8, 47.85, 5.9, 10.55))
            // Bosnia
            lat in 42.5..45.3 && lon in 15.7..19.7 -> Triple("ba", "BA", FueloBounds(42.5, 45.3, 15.7, 19.7))
            // Turkey
            lat in 35.8..42.1 && lon in 25.6..44.8 -> Triple("tr", "TR", FueloBounds(35.8, 42.1, 25.6, 44.8))
            // North Macedonia
            lat in 40.8..42.4 && lon in 20.4..23.0 -> Triple("mk", "MK", FueloBounds(40.8, 42.4, 20.4, 23.0))
            // Azores
            lat in 36.9..39.8 && lon in -31.56..-24.9 -> Triple("pt", "PT-AC", FueloBounds(36.9, 39.8, -31.56, -24.9))
            // Madeira
            lat in 32.35..33.15 && lon in -17.31..-16.23 -> Triple("pt", "PT-MA", FueloBounds(32.35, 33.15, -17.31, -16.23))
            // Portugal (Mainland)
            lat in 36.84..42.15 && lon in -9.53..-6.19 -> Triple("pt", "PT", FueloBounds(36.84, 42.15, -9.53, -6.19))
            // Canary Islands
            lat in 27.4..29.5 && lon in -18.2..-13.3 -> Triple("es", "ES-CN", FueloBounds(27.4, 29.5, -18.2, -13.3))
            // Balearic Islands
            lat in 38.6..40.1 && lon in 1.2..4.5 -> Triple("es", "ES-IB", FueloBounds(38.6, 40.1, 1.2, 4.5))
            // Spain (Mainland)
            lat in 35.94..43.99 && lon in -9.39..3.04 -> Triple("es", "ES", FueloBounds(35.94, 43.99, -9.39, 3.04))
            // Ireland
            lat in 51.22..55.64 && lon in -11.01..-5.66 -> Triple("ie", "IE", FueloBounds(51.22, 55.64, -11.01, -5.66))
            // United Kingdom
            lat in 49.67..61.06 && lon in -8.18..2.09 -> Triple("gb", "GB", FueloBounds(49.67, 61.06, -8.18, 2.09))
            // Austria
            lat in 46.37..49.02 && lon in 9.53..17.16 -> Triple("at", "AT", FueloBounds(46.37, 49.02, 9.53, 17.16))
            // Belgium
            lat in 49.50..51.55 && lon in 2.39..6.41 -> Triple("be", "BE", FueloBounds(49.50, 51.55, 2.39, 6.41))
            // Germany
            lat in 47.27..55.10 && lon in 5.87..15.04 -> Triple("de", "DE", FueloBounds(47.27, 55.10, 5.87, 15.04))
            // France (Mainland)
            lat in 41.26..51.27 && lon in -5.45..9.87 -> Triple("fr", "FR", FueloBounds(41.26, 51.27, -5.45, 9.87))
            // Greece
            lat in 34.70..41.75 && lon in 19.25..29.73 -> Triple("gr", "GR", FueloBounds(34.70, 41.75, 19.25, 29.73))
            // Croatia
            lat in 42.18..46.56 && lon in 13.21..19.45 -> Triple("hr", "HR", FueloBounds(42.18, 46.56, 13.21, 19.45))
            // Italy
            lat in 35.29..47.09 && lon in 6.63..18.78 -> Triple("it", "IT", FueloBounds(35.29, 47.09, 6.63, 18.78))
            // Netherlands
            lat in 50.73..53.73 && lon in 1.92..7.23 -> Triple("nl", "NL", FueloBounds(50.73, 53.73, 1.92, 7.23))
            // Romania
            lat in 43.62..48.27 && lon in 20.26..30.05 -> Triple("ro", "RO", FueloBounds(43.62, 48.27, 20.26, 30.05))
            // Serbia
            lat in 42.23..46.19 && lon in 18.81..23.01 -> Triple("rs", "RS", FueloBounds(42.23, 46.19, 18.81, 23.01))
            // Slovenia
            lat in 45.42..46.88 && lon in 13.38..16.60 -> Triple("si", "SI", FueloBounds(45.42, 46.88, 13.38, 16.60))
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

    override fun clearCache() {
        cachedStations.clear()
    }
}
