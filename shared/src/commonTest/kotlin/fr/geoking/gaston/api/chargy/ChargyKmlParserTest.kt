package fr.geoking.gaston.api.chargy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargyKmlParserTest {

    @Test
    fun parse_withPlainTags_works() {
        val kml = """
            <Placemark>
                <name>Station 1</name>
                <address>Address 1</address>
                <coordinates>6.12,49.61,0</coordinates>
            </Placemark>
        """.trimIndent()
        val stations = ChargyKmlParser.parse(kml)
        assertEquals(1, stations.size)
        assertEquals("Station 1", stations[0].name)
        assertEquals("Address 1", stations[0].address)
        assertEquals(49.61, stations[0].latitude)
        assertEquals(6.12, stations[0].longitude)
    }

    @Test
    fun parse_withAttributesAndNamespaces_worksWithImprovedParser() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <Placemark id="station_123">
                        <name xmlns:custom="http://example.com">Station with Namespace</name>
                        <address>Some Address</address>
                        <ExtendedData>
                            <Data name="chargingdevice">
                                <value>{"connectors":[{"description":"AVAILABLE","maxchspeed":22.0}]}</value>
                            </Data>
                        </ExtendedData>
                        <Point>
                            <coordinates>6.1319,49.6116,0</coordinates>
                        </Point>
                    </Placemark>
                </Document>
            </kml>
        """.trimIndent()

        val stations = ChargyKmlParser.parse(kml)

        assertEquals(1, stations.size)
        assertEquals("Station with Namespace", stations[0].name)
        assertEquals("Some Address", stations[0].address)
        assertEquals(49.6116, stations[0].latitude)
        assertEquals(6.1319, stations[0].longitude)
        assertEquals(1, stations[0].totalConnectors)
        assertEquals(1, stations[0].availableConnectors)
        assertEquals(22.0, stations[0].maxPowerKw)
    }

    @Test
    fun parse_withDataAttributesAndMixedCaseStatus_works() {
        val kml = """
            <Placemark>
                <name>Station 2</name>
                <coordinates>6.1,49.6,0</coordinates>
                <ExtendedData>
                    <Data name="chargingdevice" someattr="value">
                        <value>{"connectors":[{"description":"Available","maxchspeed":11.0}]}</value>
                    </Data>
                </ExtendedData>
            </Placemark>
        """.trimIndent()
        val stations = ChargyKmlParser.parse(kml)
        assertEquals(1, stations.size)
        assertEquals(1, stations[0].availableConnectors)
        assertEquals(11.0, stations[0].maxPowerKw)
    }
}
