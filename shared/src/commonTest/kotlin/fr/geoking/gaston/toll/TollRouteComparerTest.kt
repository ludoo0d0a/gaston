package fr.geoking.gaston.toll

import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.routing.RouteResult
import fr.geoking.gaston.api.toll.ConnectionPrice
import fr.geoking.gaston.api.toll.OpenTollDataModel
import fr.geoking.gaston.api.toll.OpenTollNetwork
import fr.geoking.gaston.api.toll.TollBoothDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TollRouteComparerTest {

    @Test
    fun compare_sortsByTollThenDuration() = runBlocking {
        val calculator = TollCalculator {
            OpenTollDataModel(
                networks = listOf(
                    OpenTollNetwork(
                        networkName = "n1",
                        tolls = listOf("A", "B"),
                        connection = mapOf(
                            "A" to mapOf(
                                "B" to ConnectionPrice(distance = "10", price = mapOf("class_1" to "5.0"))
                            )
                        )
                    )
                ),
                tollDescription = mapOf(
                    "A" to TollBoothDescription(lat = "45.0", lon = "5.0", type = "close"),
                    "B" to TollBoothDescription(lat = "45.01", lon = "5.01", type = "close")
                ),
            )
        }
        val service = TollEstimateService(calculator, backendClient = null, useBackend = { false })
        val comparer = TollRouteComparer(service)
        val expensive = RouteResult(
            points = listOf(45.0 to 5.0, 45.01 to 5.01),
            distanceMeters = 2000.0,
            durationSeconds = 100.0,
        )
        val free = RouteResult(
            points = listOf(46.0 to 6.0, 46.01 to 6.01),
            distanceMeters = 3000.0,
            durationSeconds = 200.0,
        )
        val ranked = comparer.compare(listOf(expensive, free), VehicleType.Car)
        assertEquals(2, ranked.size)
        // free route has no booths → null toll sorts last (POSITIVE_INFINITY)
        assertTrue(ranked[0].toll != null)
        assertEquals(5.0, ranked[0].toll!!.amountEur, 0.01)
    }
}
