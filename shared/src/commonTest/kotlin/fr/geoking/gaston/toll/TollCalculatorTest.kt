package fr.geoking.gaston.toll

import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.toll.ConnectionPrice
import fr.geoking.gaston.api.toll.OpenTollDataModel
import fr.geoking.gaston.api.toll.OpenTollNetwork
import fr.geoking.gaston.api.toll.OpenTollPriceEntry
import fr.geoking.gaston.api.toll.TollBoothDescription
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val calculator = TollCalculator(dataSource = { twoBoothModel() })
        assertNull(calculator.estimateToll(emptyList(), VehicleType.Car))
        assertNull(calculator.estimateToll(listOf(45.0 to 5.0), VehicleType.Car))
    }

    @Test
    fun estimateToll_routeNearTwoBooths_returnsEntryExitPrice() {
        val calculator = TollCalculator(dataSource = { twoBoothModel() })
        val route = listOf(
            44.99 to 4.99,
            45.0 to 5.0,
            45.005 to 5.005,
            45.01 to 5.01,
            45.02 to 5.02
        )
        val estimate = calculator.estimateToll(route, VehicleType.Car)
        assertTrue(estimate != null, "Expected non-null toll for route passing near two booths")
        assertEquals(2.5, requireNotNull(estimate).amountEur, 0.01)
    }

    @Test
    fun estimateToll_threeCloseBooths_usesEntryExitNotHopSum() {
        // Hop sum would be A→B (2.0) + B→C (3.0) = 5.0; correct closed price A→C = 4.0
        val calculator = TollCalculator(dataSource = {
            OpenTollDataModel(
                networks = listOf(
                    OpenTollNetwork(
                        networkName = "n1",
                        tolls = listOf("A", "B", "C"),
                        connection = mapOf(
                            "A" to mapOf(
                                "B" to ConnectionPrice(distance = "10", price = mapOf("class_1" to "2.0")),
                                "C" to ConnectionPrice(distance = "25", price = mapOf("class_1" to "4.0"))
                            ),
                            "B" to mapOf(
                                "C" to ConnectionPrice(distance = "15", price = mapOf("class_1" to "3.0"))
                            )
                        )
                    )
                ),
                tollDescription = mapOf(
                    "A" to TollBoothDescription(lat = "45.00", lon = "5.00", type = "close"),
                    "B" to TollBoothDescription(lat = "45.01", lon = "5.01", type = "close"),
                    "C" to TollBoothDescription(lat = "45.02", lon = "5.02", type = "close")
                ),
                openTollPrice = emptyMap()
            )
        })
        val route = listOf(
            44.99 to 4.99,
            45.00 to 5.00,
            45.01 to 5.01,
            45.02 to 5.02,
            45.03 to 5.03
        )
        val estimate = calculator.estimateToll(route, VehicleType.Car)
        assertEquals(4.0, requireNotNull(estimate).amountEur, 0.01)
    }

    @Test
    fun estimateToll_twoNetworks_twoEntryExitLookups() {
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
                    ),
                    OpenTollNetwork(
                        networkName = "n2",
                        tolls = listOf("C", "D"),
                        connection = mapOf(
                            "C" to mapOf(
                                "D" to ConnectionPrice(distance = "20", price = mapOf("class_1" to "3.5"))
                            )
                        )
                    )
                ),
                tollDescription = mapOf(
                    "A" to TollBoothDescription(lat = "45.00", lon = "5.00", type = "close"),
                    "B" to TollBoothDescription(lat = "45.01", lon = "5.01", type = "close"),
                    "C" to TollBoothDescription(lat = "45.02", lon = "5.02", type = "close"),
                    "D" to TollBoothDescription(lat = "45.03", lon = "5.03", type = "close")
                ),
                openTollPrice = emptyMap()
            )
        })
        val route = listOf(
            44.99 to 4.99,
            45.00 to 5.00,
            45.01 to 5.01,
            45.02 to 5.02,
            45.03 to 5.03,
            45.04 to 5.04
        )
        val estimate = calculator.estimateToll(route, VehicleType.Car)
        assertEquals(6.0, requireNotNull(estimate).amountEur, 0.01)
    }

    @Test
    fun estimateToll_openPlusClose_sumsBoth() {
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
                    "OPEN1" to TollBoothDescription(lat = "44.995", lon = "4.995", type = "open"),
                    "A" to TollBoothDescription(lat = "45.00", lon = "5.00", type = "close"),
                    "B" to TollBoothDescription(lat = "45.01", lon = "5.01", type = "close")
                ),
                openTollPrice = mapOf(
                    "OPEN1" to OpenTollPriceEntry(distance = "0", price = mapOf("class_1" to "1.2"))
                )
            )
        })
        val route = listOf(
            44.99 to 4.99,
            44.995 to 4.995,
            45.00 to 5.00,
            45.01 to 5.01,
            45.02 to 5.02
        )
        val estimate = calculator.estimateToll(route, VehicleType.Car)
        assertEquals(3.7, requireNotNull(estimate).amountEur, 0.01)
    }

    @Test
    fun estimateToll_bicycle_returnsNull() {
        val calculator = TollCalculator(dataSource = { twoBoothModel() })
        val route = listOf(45.0 to 5.0, 45.01 to 5.01)
        assertNull(calculator.estimateToll(route, VehicleType.Bicycle))
    }

    private fun twoBoothModel() = OpenTollDataModel(
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
}
