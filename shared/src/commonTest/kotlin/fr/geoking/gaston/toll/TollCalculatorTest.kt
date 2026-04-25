package fr.geoking.gaston.toll

import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.toll.ConnectionPrice
import fr.geoking.gaston.api.toll.OpenTollDataModel
import fr.geoking.gaston.api.toll.OpenTollNetwork
import fr.geoking.gaston.api.toll.TollBoothDescription
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TollCalculatorTest {

    @Test
    fun estimateToll_noData_returnsNull() {
        val calculator = TollCalculator(dataSource = { null })
        val route = listOf(45.0 to 5.0, 45.1 to 5.1)
        assertNull(calculator.estimateToll(route, VehicleType.Car))
    }

    @Test
    fun estimateToll_emptyRoute_returnsNull() {
        val calculator = TollCalculator(dataSource = {
            OpenTollDataModel(
                networks = listOf(
                    OpenTollNetwork(
                        networkName = "n1",
                        tolls = listOf("A", "B"),
                        connection = mapOf(
                            "A" to mapOf(
                                "B" to ConnectionPrice(distance = "10", price = mapOf("class_1" to "2.5"))
                            )
                        )
                    )
                ),
                tollDescription = mapOf(
                    "A" to TollBoothDescription(lat = "45.0", lon = "5.0", type = "close"),
                    "B" to TollBoothDescription(lat = "45.1", lon = "5.1", type = "close")
                ),
                openTollPrice = emptyMap()
            )
        })
        assertNull(calculator.estimateToll(emptyList(), VehicleType.Car))
        assertNull(calculator.estimateToll(listOf(45.0 to 5.0), VehicleType.Car))
    }

    @Test
    fun estimateToll_routeNearTwoBooths_returnsSum() {
        val calculator = TollCalculator(dataSource = {
            OpenTollDataModel(
                networks = listOf(
                    OpenTollNetwork(
                        networkName = "n1",
                        tolls = listOf("A", "B"),
                        connection = mapOf(
                            "A" to mapOf(
                                "B" to ConnectionPrice(distance = "10", price = mapOf("class_1" to "2.5", "class_5" to "0.8"))
                            )
                        )
                    )
                ),
                tollDescription = mapOf(
                    "A" to TollBoothDescription(lat = "45.0", lon = "5.0", type = "close"),
                    "B" to TollBoothDescription(lat = "45.01", lon = "5.01", type = "close")
                ),
                openTollPrice = emptyMap()
            )
        })
        val route = listOf(
            44.99 to 4.99,
            45.0 to 5.0,
            45.005 to 5.005,
            45.01 to 5.01,
            45.02 to 5.02
        )
        val estimate = calculator.estimateToll(route, VehicleType.Car)
        assertTrue(estimate != null, "Expected non-null toll for route passing near two booths")
        val toll = requireNotNull(estimate)
        assertTrue(toll.amountEur > 0, "Expected positive toll")
        assertTrue(toll.amountEur >= 2.0 && toll.amountEur <= 3.0, "Expected ~2.5 for class_1")
    }

    @Test
    fun estimateToll_bicycle_returnsNull() {
        val calculator = TollCalculator(dataSource = {
            OpenTollDataModel(
                networks = listOf(
                    OpenTollNetwork(
                        networkName = "n1",
                        tolls = listOf("A", "B"),
                        connection = mapOf("A" to mapOf("B" to ConnectionPrice(distance = "10", price = mapOf("class_1" to "2.5"))))
                    )
                ),
                tollDescription = mapOf(
                    "A" to TollBoothDescription(lat = "45.0", lon = "5.0", type = "close"),
                    "B" to TollBoothDescription(lat = "45.01", lon = "5.01", type = "close")
                ),
                openTollPrice = emptyMap()
            )
        })
        val route = listOf(45.0 to 5.0, 45.01 to 5.01)
        assertNull(calculator.estimateToll(route, VehicleType.Bicycle))
    }
}
