package fr.geoking.gaston.api.argentina

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ArgentinaEnergiaProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val energiaClient = ArgentinaEnergiaClient(client)
    private val mutex = Mutex()
    private var cachedData: List<ArgentinaCSVRow>? = null

    private val fuelMap = mapOf(
        "Nafta (súper) entre 92 y 95 Ron" to "SP95",
        "Nafta (premium) de más de 95 Ron" to "SP95 Premium",
        "Gas Oil Grado 2" to "Gazole",
        "Gas Oil Grado 3" to "Gazole Premium",
        "GNC" to "CNG"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val rows = getOrFetchData()
        if (rows.isEmpty()) return emptyList()

        val stationMap = mutableMapOf<String, Poi>()

        return withContext(Dispatchers.Default) {
            rows.asSequence()
                .filter { r ->
                    r.tipohorario == "Diurno" &&
                    haversineKm(latitude, longitude, r.latitude, r.longitude) <= radiusKm
                }
                .forEach { r ->
                    val externalId = "ar_${r.cuit}_${r.latitude}_${r.longitude}"
                    val fuelName = fuelMap[r.producto] ?: return@forEach

                    val poi = stationMap.getOrPut(externalId) {
                        val brand = if (r.empresabandera == "SIN EMPRESA BANDERA") null else r.empresabandera
                        Poi(
                            id = externalId,
                            name = brand ?: r.empresa,
                            address = r.direccion,
                            latitude = r.latitude,
                            longitude = r.longitude,
                            brand = brand,
                            poiCategory = PoiCategory.Gas,
                            fuelPrices = mutableListOf(),
                            source = "Secretaría de Energía (Argentina)"
                        )
                    }
                    (poi.fuelPrices as MutableList).add(FuelPrice(fuelName, r.precio))
                }
            stationMap.values.take(limit).toList()
        }
    }

    private suspend fun getOrFetchData(): List<ArgentinaCSVRow> = mutex.withLock {
        cachedData?.let { return@withLock it }
        return try {
            val rows = energiaClient.fetchAllData()
            cachedData = rows
            rows
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearCache() {
        cachedData = null
    }
}
