package fr.geoking.gaston.poi

import fr.geoking.gaston.api.routex.PoiAmenities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoiMergerAmenitiesEnrichTest {

    @Test
    fun enrichAmenitiesFrom_keepsNameBrandAndCoords() {
        val station = Poi(
            id = "57120005",
            refId = "57120005",
            name = "Total Energies Paris",
            address = "1 rue Test",
            latitude = 48.85,
            longitude = 2.35,
            brand = "Total",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.5)),
            source = "DataGouvPrixCarburant",
        )
        val osm = Poi(
            id = "osm-fuel-enrich:1",
            name = "Gas station",
            address = "",
            latitude = 48.851,
            longitude = 2.351,
            brand = null,
            poiCategory = PoiCategory.Gas,
            amenities = PoiAmenities(toilets = true, shop = true, openingHoursFuel = listOf("24/7")),
            source = "OpenStreetMap",
        )
        val enriched = PoiMerger.enrichAmenitiesFrom(station, osm)
        assertEquals("Total Energies Paris", enriched.name)
        assertEquals("Total", enriched.brand)
        assertEquals(48.85, enriched.latitude, 1e-9)
        assertEquals(2.35, enriched.longitude, 1e-9)
        assertEquals(true, enriched.amenities?.toilets)
        assertEquals(true, enriched.amenities?.shop)
        assertEquals(listOf(FuelPrice("Gazole", 1.5)), enriched.fuelPrices)
    }

    @Test
    fun enrichAmenitiesFrom_doesNotClearExistingBrandWhenOsmHasNone() {
        val station = Poi(
            id = "a",
            name = "Avia",
            address = "",
            latitude = 1.0,
            longitude = 2.0,
            brand = "Avia",
            poiCategory = PoiCategory.Gas,
        )
        val osm = Poi(
            id = "osm-fuel-enrich:2",
            name = "Station",
            address = "",
            latitude = 1.0,
            longitude = 2.0,
            poiCategory = PoiCategory.Gas,
            amenities = PoiAmenities(carWash = true),
        )
        val enriched = PoiMerger.enrichAmenitiesFrom(station, osm)
        assertEquals("Avia", enriched.brand)
        assertEquals("Avia", enriched.name)
        assertEquals(true, enriched.amenities?.carWash)
        assertNull(enriched.amenities?.toilets)
    }
}
