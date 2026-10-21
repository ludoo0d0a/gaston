package fr.geoking.gaston.api.traffic

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatexStreamParserTest {

    @Test
    fun testParseStream_parsesXmlCorrectly() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d2LogicalModel xmlns="http://datex2.eu/schema/2/2_0">
                <payloadPublication>
                    <situation>
                        <situationRecord id="FR_123" type="Accident">
                            <situationRecordVersionTime>2026-10-21T10:00:00Z</situationRecordVersionTime>
                            <latitude>48.8566</latitude>
                            <longitude>2.3522</longitude>
                            <value>A10</value>
                            <tpegOtherPointDescriptorType>linkName</tpegOtherPointDescriptorType>
                            <generalPublicComment>
                                <commentType>description</commentType>
                                <value>Accident grave sur A10</value>
                            </generalPublicComment>
                        </situationRecord>
                    </situation>
                </payloadPublication>
            </d2LogicalModel>
        """.trimIndent()

        val inputStream = xml.byteInputStream(Charsets.UTF_8)
        val events = DatexSituationParser.parseStream(inputStream)

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("A10", event.roadRef)
        assertEquals(TrafficSeverity.Accident, event.severity)
        assertEquals("FR_123", event.sourceId)
        assertEquals(48.8566, event.latitude)
        assertEquals(2.3522, event.longitude)
    }

    @Test
    fun testTrafficRepository_inMemoryImplementation() = runBlocking {
        val repo = FakeTrafficRepository()
        val event = TrafficEvent(
            roadRef = "A1",
            severity = TrafficSeverity.Congestion,
            bbox = Bbox(latMin = 48.0, lonMin = 2.0, latMax = 49.0, lonMax = 3.0),
            sourceId = "event_1"
        )

        repo.replaceEventsForSource("bison_fute_rrn", listOf(event))
        assertTrue(repo.isSourceFresh("bison_fute_rrn", 3600_000L))

        val results = repo.getTrafficInBbox(48.0, 2.0, 49.0, 3.0)
        assertEquals(1, results.size)
        assertEquals("A1", results[0].roadRef)

        repo.updateZonePoints("event_1", "bison_fute_rrn", listOf(Pair(48.1, 2.1), Pair(48.2, 2.2)))
        val updated = repo.getTrafficInBbox(48.0, 2.0, 49.0, 3.0)
        assertNotNull(updated[0].zonePoints)
        assertEquals(2, updated[0].zonePoints?.size)
    }
}
