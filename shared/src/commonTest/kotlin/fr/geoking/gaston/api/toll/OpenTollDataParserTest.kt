package fr.geoking.gaston.api.toll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenTollDataParserTest {

    @Test
    fun parse_minimalJson_returnsModel() {
        val json = """
            {
                "networks": [
                    {
                        "network_name": "component_1",
                        "tolls": ["A", "B"],
                        "connection": {
                            "A": {
                                "B": {
                                    "distance": "10",
                                    "price": {"class_1": "2.5", "class_2": "4.0", "class_5": "0.8"}
                                }
                            }
                        }
                    }
                ],
                "toll_description": {
                    "A": {"lat": "45.0", "lon": "5.0", "type": "close"},
                    "B": {"lat": "45.1", "lon": "5.1", "type": "close"}
                },
                "open_toll_price": {}
            }
        """.trimIndent()

        val model = OpenTollDataParser.parse(json)
        assertNotNull(model)
        assertEquals(1, model.networks.size)
        assertEquals(2, model.networks[0].tolls.size)
        assertEquals(2, model.tollDescription.size)
        assertEquals("45.0", model.tollDescription["A"]?.lat)
        assertEquals("5.0", model.tollDescription["A"]?.lon)
        val conn = model.networks[0].connection["A"]?.get("B")
        assertNotNull(conn)
        assertEquals("2.5", conn.price["class_1"])
    }

    @Test
    fun parse_invalidJson_returnsNull() {
        assertNull(OpenTollDataParser.parse("{"))
        assertNull(OpenTollDataParser.parse(""))
    }
}
