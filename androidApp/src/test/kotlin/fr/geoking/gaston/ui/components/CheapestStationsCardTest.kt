package fr.geoking.gaston.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheapestStationsCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createDummyStation(id: Int, name: String, price: Double): Poi {
        return Poi(
            id = "station_$id",
            name = name,
            address = "Street $id",
            latitude = 48.8566 + (id * 0.001),
            longitude = 2.3522 + (id * 0.001),
            brand = "Total",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("SP95", price))
        )
    }

    @Test
    fun cheapestStationsCard_displaysMax5Stations_andShowsSeeAllLinkWhenMoreThan5() {
        val stations = (1..7).map { id ->
            createDummyStation(id = id, name = "Station $id", price = 1.50 + id * 0.01)
        }

        var mapClicked = false

        composeTestRule.setContent {
            CheapestStationsCard(
                stations = stations,
                userLatitude = 48.8566,
                userLongitude = 2.3522,
                selectedEnergyIds = setOf("sp95"),
                onClick = {},
                onMapClick = { mapClicked = true },
                title = "Nearby cheapest"
            )
        }

        // Verify first 5 stations exist in node tree
        (1..5).forEach { id ->
            composeTestRule.onNodeWithText("Station $id").assertExists()
        }

        // Verify 6th and 7th stations do NOT exist
        composeTestRule.onNodeWithText("Station 6").assertDoesNotExist()
        composeTestRule.onNodeWithText("Station 7").assertDoesNotExist()

        // Verify "See all matches" link exists
        val seeAllButton = composeTestRule.onNodeWithTag("dashboard_see_all_matches_btn")
        seeAllButton.assertExists()
    }

    @Test
    fun cheapestStationsCard_doesNotShowSeeAllLinkWhen5OrFewerStations() {
        val stations = (1..5).map { id ->
            createDummyStation(id = id, name = "Station $id", price = 1.50 + id * 0.01)
        }

        composeTestRule.setContent {
            CheapestStationsCard(
                stations = stations,
                userLatitude = 48.8566,
                userLongitude = 2.3522,
                selectedEnergyIds = setOf("sp95"),
                onClick = {},
                onMapClick = {},
                title = "Nearby cheapest"
            )
        }

        // Verify all 5 stations exist in node tree
        (1..5).forEach { id ->
            composeTestRule.onNodeWithText("Station $id").assertExists()
        }

        // Verify "See all matches" link is not displayed
        composeTestRule.onNodeWithTag("dashboard_see_all_matches_btn").assertDoesNotExist()
    }
}
