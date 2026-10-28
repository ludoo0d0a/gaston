package fr.geoking.gaston.parked

import fr.geoking.gaston.parking.ParkingPoi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndergroundParkingDetectorTest {

    @Test
    fun detectsUndergroundPoiByKeywords() {
        val undergroundPoi = ParkingPoi(
            id = "p1",
            name = "Parking Souterrain Central",
            latitude = 48.8566,
            longitude = 2.3522,
        )
        val normalPoi = ParkingPoi(
            id = "p2",
            name = "Parking Indigo Place",
            latitude = 48.8566,
            longitude = 2.3522,
        )

        assertTrue(UndergroundParkingDetector.isPoiUnderground(undergroundPoi))
        assertFalse(UndergroundParkingDetector.isPoiUnderground(normalPoi))
    }

    @Test
    fun isLikelyUndergroundWhenCloseToUndergroundPoi() {
        val undergroundPoi = ParkingPoi(
            id = "p1",
            name = "Parking Underground Indigo",
            latitude = 48.8566,
            longitude = 2.3522,
        )

        val result = UndergroundParkingDetector.isLikelyUnderground(
            latitude = 48.8567,
            longitude = 2.3523,
            nearbyParkings = listOf(undergroundPoi),
            isGpsLostNearParking = false
        )

        assertTrue(result)
    }

    @Test
    fun isLikelyUndergroundWhenGpsLostNearParking() {
        val normalPoi = ParkingPoi(
            id = "p2",
            name = "Parking Station",
            latitude = 48.8566,
            longitude = 2.3522,
        )

        val result = UndergroundParkingDetector.isLikelyUnderground(
            latitude = 48.8567,
            longitude = 2.3523,
            nearbyParkings = listOf(normalPoi),
            isGpsLostNearParking = true
        )

        assertTrue(result)
    }

    @Test
    fun returnsFalseWhenFarOrNoPoi() {
        val normalPoi = ParkingPoi(
            id = "p2",
            name = "Parking Surface",
            latitude = 48.8000,
            longitude = 2.3000,
        )

        val result = UndergroundParkingDetector.isLikelyUnderground(
            latitude = 48.8567,
            longitude = 2.3523,
            nearbyParkings = listOf(normalPoi),
            isGpsLostNearParking = false
        )

        assertFalse(result)
    }
}
