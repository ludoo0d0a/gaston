package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.poi.PoiCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CitaGeoJsonParserTest {

    @Test
    fun parsePolygon_setsPoiAndZonePoints() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [{
                "type": "Feature",
                "id": "A1-test",
                "geometry": {
                  "type": "Polygon",
                  "coordinates": [[[6.1, 49.6], [6.2, 49.6], [6.2, 49.7], [6.1, 49.7], [6.1, 49.6]]]
                },
                "properties": {
                  "road": "a1",
                  "direction": "ns",
                  "name": "A1 test",
                  "service_level": 3,
                  "service_level_desc": "dense"
                }
              }]
            }
        """.trimIndent()

        val events = CitaGeoJsonParser.parse(body)
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals("A1", e.roadRef)
        assertEquals(TrafficSeverity.Congestion, e.severity)
        assertEquals(49.6, e.poiLatitude)
        assertEquals(6.1, e.poiLongitude)
        assertNotNull(e.bbox)
        assertTrue(e.bbox!!.intersects(Bbox(49.55, 6.05, 49.75, 6.25)))
        assertTrue((e.zonePoints?.size ?: 0) >= 2)
    }

    @Test
    fun parseJam_mapsToCongestionAndMapPoi() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [{
                "type": "Feature",
                "id": "A3-jam",
                "geometry": {
                  "type": "Polygon",
                  "coordinates": [[[6.12, 49.55], [6.13, 49.55], [6.13, 49.56], [6.12, 49.56], [6.12, 49.55]]]
                },
                "properties": {
                  "road": "a3",
                  "direction": "mv",
                  "name": "A3 jam",
                  "service_level": 4,
                  "service_level_desc": "jam"
                }
              }]
            }
        """.trimIndent()

        val events = CitaGeoJsonParser.parse(body)
        assertEquals(1, events.size)
        assertEquals(TrafficSeverity.Congestion, events[0].severity)
        val poi = events[0].toMapPoi(CitaTrafficProvider.PROVIDER_ID)
        assertNotNull(poi)
        assertEquals(PoiCategory.Traffic, poi!!.poiCategory)
        assertEquals("cita", poi.source)
    }

    @Test
    fun parseNormalFluid_isFilteredOut() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [{
                "type": "Feature",
                "id": "A1-fluid",
                "geometry": {
                  "type": "Polygon",
                  "coordinates": [[[6.1, 49.6], [6.2, 49.6], [6.2, 49.7], [6.1, 49.7], [6.1, 49.6]]]
                },
                "properties": {
                  "road": "a1",
                  "direction": "ns",
                  "name": "A1 fluid",
                  "service_level": 1,
                  "service_level_desc": "fluide"
                }
              }]
            }
        """.trimIndent()

        val events = CitaGeoJsonParser.parse(body)
        assertEquals(0, events.size)
    }
}
