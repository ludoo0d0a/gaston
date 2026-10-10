package fr.geoking.gaston.api.traffic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BisonFuteDatexParserTest {

    private val nowMs = 1_760_000_000_000L // ~2025-10-09 UTC — after expired, before active ends

    @Test
    fun parse_extractsAccidentAndBrokenDownVehicle_skipsExpired() {
        val events = BisonFuteDatexParser.parse(FIXTURE_XML, nowMs = nowMs)
        assertEquals(2, events.size)

        val accident = events.first { it.sourceId == "acc-1" }
        assertEquals(TrafficSeverity.Accident, accident.severity)
        assertEquals("A7", accident.roadRef)
        assertEquals("eastBound", accident.direction)
        val accidentMsg = assertNotNull(accident.message)
        assertTrue(accidentMsg.contains("accident"))
        assertEquals(43.37, accident.bbox!!.latMin, 0.01)

        val breakdown = events.first { it.sourceId == "veh-1" }
        assertEquals(TrafficSeverity.Accident, breakdown.severity)
        assertEquals("N20", breakdown.roadRef)
        val breakdownMsg = assertNotNull(breakdown.message)
        assertTrue(breakdownMsg.contains("brokenDownVehicle"))
    }

    @Test
    fun parse_mapsRoadClosedToClosure_andRoadworks() {
        val events = BisonFuteDatexParser.parse(FIXTURE_MANAGEMENT_XML, nowMs = nowMs)
        assertEquals(2, events.size)
        assertEquals(TrafficSeverity.Closure, events.first { it.sourceId == "close-1" }.severity)
        assertEquals(TrafficSeverity.Roadworks, events.first { it.sourceId == "works-1" }.severity)
    }

    @Test
    fun parse_ignoresNonClosureLaneManagement() {
        val events = BisonFuteDatexParser.parse(FIXTURE_NARROW_LANES_XML, nowMs = nowMs)
        assertTrue(events.isEmpty())
    }

    @Test
    fun parseDatexTime_handlesOffsetAndZ() {
        val withOffset = BisonFuteDatexParser.parseDatexTime("2026-10-04T00:00:13.685+02:00")
        assertNotNull(withOffset)
        val zulu = BisonFuteDatexParser.parseDatexTime("2026-10-03T22:00:13.685Z")
        assertNotNull(zulu)
        // Same instant: 2026-10-03 22:00:13 UTC
        assertEquals(zulu, withOffset)
        assertNull(BisonFuteDatexParser.parseDatexTime(null))
        assertNull(BisonFuteDatexParser.parseDatexTime("not-a-date"))
    }

    companion object {
        // Active until 2026-12-01; expired until 2024-01-01 — nowMs is mid-2025-ish.
        private val FIXTURE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
            <soap:Body>
            <d2LogicalModel xmlns:ns2="http://datex2.eu/schema/2/2_0" modelBaseVersion="2">
            <ns2:payloadPublication xsi:type="ns2:SituationPublication" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" lang="fr">
            <ns2:situation id="s1" version="1">
              <ns2:situationRecord xsi:type="ns2:Accident" id="acc-1" version="1">
                <ns2:situationRecordVersionTime>2025-10-01T10:00:00.000+02:00</ns2:situationRecordVersionTime>
                <ns2:validity>
                  <ns2:validityTimeSpecification>
                    <ns2:overallStartTime>2025-10-01T10:00:00.000+02:00</ns2:overallStartTime>
                    <ns2:overallEndTime>2026-12-01T00:00:00.000+01:00</ns2:overallEndTime>
                  </ns2:validityTimeSpecification>
                </ns2:validity>
                <ns2:generalPublicComment>
                  <ns2:comment><ns2:values><ns2:value lang="fr">près de Marseille</ns2:value></ns2:values></ns2:comment>
                  <ns2:commentType>locationDescriptor</ns2:commentType>
                </ns2:generalPublicComment>
                <ns2:groupOfLocations xsi:type="ns2:Point">
                  <ns2:tpegPointLocation xsi:type="ns2:TpegSimplePoint">
                    <ns2:tpegDirection>eastBound</ns2:tpegDirection>
                    <ns2:point xsi:type="ns2:TpegNonJunctionPoint">
                      <ns2:pointCoordinates>
                        <ns2:latitude>43.37144</ns2:latitude>
                        <ns2:longitude>5.32000</ns2:longitude>
                      </ns2:pointCoordinates>
                      <ns2:name>
                        <ns2:descriptor><ns2:values><ns2:value lang="fr">A7</ns2:value></ns2:values></ns2:descriptor>
                        <ns2:tpegOtherPointDescriptorType>linkName</ns2:tpegOtherPointDescriptorType>
                      </ns2:name>
                    </ns2:point>
                  </ns2:tpegPointLocation>
                </ns2:groupOfLocations>
                <ns2:accidentType>accident</ns2:accidentType>
              </ns2:situationRecord>
              <ns2:situationRecord xsi:type="ns2:VehicleObstruction" id="veh-1" version="1">
                <ns2:situationRecordVersionTime>2025-10-02T11:00:00.000+02:00</ns2:situationRecordVersionTime>
                <ns2:validity>
                  <ns2:validityTimeSpecification>
                    <ns2:overallStartTime>2025-10-02T11:00:00.000+02:00</ns2:overallStartTime>
                    <ns2:overallEndTime>2026-12-01T00:00:00.000+01:00</ns2:overallEndTime>
                  </ns2:validityTimeSpecification>
                </ns2:validity>
                <ns2:groupOfLocations xsi:type="ns2:Point">
                  <ns2:tpegPointLocation xsi:type="ns2:TpegSimplePoint">
                    <ns2:tpegDirection>bothWays</ns2:tpegDirection>
                    <ns2:point xsi:type="ns2:TpegNonJunctionPoint">
                      <ns2:pointCoordinates>
                        <ns2:latitude>42.844486</ns2:latitude>
                        <ns2:longitude>1.601671</ns2:longitude>
                      </ns2:pointCoordinates>
                      <ns2:name>
                        <ns2:descriptor><ns2:values><ns2:value lang="fr">N20</ns2:value></ns2:values></ns2:descriptor>
                        <ns2:tpegOtherPointDescriptorType>linkName</ns2:tpegOtherPointDescriptorType>
                      </ns2:name>
                    </ns2:point>
                  </ns2:tpegPointLocation>
                </ns2:groupOfLocations>
                <ns2:vehicleObstructionType>brokenDownVehicle</ns2:vehicleObstructionType>
              </ns2:situationRecord>
              <ns2:situationRecord xsi:type="ns2:Accident" id="expired-1" version="1">
                <ns2:situationRecordVersionTime>2023-01-01T10:00:00.000+01:00</ns2:situationRecordVersionTime>
                <ns2:validity>
                  <ns2:validityTimeSpecification>
                    <ns2:overallStartTime>2023-01-01T10:00:00.000+01:00</ns2:overallStartTime>
                    <ns2:overallEndTime>2024-01-01T00:00:00.000+01:00</ns2:overallEndTime>
                  </ns2:validityTimeSpecification>
                </ns2:validity>
                <ns2:groupOfLocations xsi:type="ns2:Point">
                  <ns2:tpegPointLocation xsi:type="ns2:TpegSimplePoint">
                    <ns2:point xsi:type="ns2:TpegNonJunctionPoint">
                      <ns2:pointCoordinates>
                        <ns2:latitude>48.85</ns2:latitude>
                        <ns2:longitude>2.35</ns2:longitude>
                      </ns2:pointCoordinates>
                    </ns2:point>
                  </ns2:tpegPointLocation>
                </ns2:groupOfLocations>
                <ns2:accidentType>accident</ns2:accidentType>
              </ns2:situationRecord>
            </ns2:situation>
            </ns2:payloadPublication>
            </d2LogicalModel>
            </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        private val FIXTURE_MANAGEMENT_XML = """
            <ns2:situationRecord xsi:type="ns2:RoadOrCarriagewayOrLaneManagement" id="close-1" version="1">
              <ns2:validity>
                <ns2:validityTimeSpecification>
                  <ns2:overallEndTime>2026-12-01T00:00:00.000+01:00</ns2:overallEndTime>
                </ns2:validityTimeSpecification>
              </ns2:validity>
              <ns2:groupOfLocations xsi:type="ns2:Point">
                <ns2:tpegPointLocation>
                  <ns2:point>
                    <ns2:pointCoordinates>
                      <ns2:latitude>45.0</ns2:latitude>
                      <ns2:longitude>5.0</ns2:longitude>
                    </ns2:pointCoordinates>
                    <ns2:name>
                      <ns2:descriptor><ns2:values><ns2:value>N7</ns2:value></ns2:values></ns2:descriptor>
                      <ns2:tpegOtherPointDescriptorType>linkName</ns2:tpegOtherPointDescriptorType>
                    </ns2:name>
                  </ns2:point>
                </ns2:tpegPointLocation>
              </ns2:groupOfLocations>
              <ns2:roadOrCarriagewayOrLaneManagementType>roadClosed</ns2:roadOrCarriagewayOrLaneManagementType>
            </ns2:situationRecord>
            <ns2:situationRecord xsi:type="ns2:MaintenanceWorks" id="works-1" version="1">
              <ns2:validity>
                <ns2:validityTimeSpecification>
                  <ns2:overallEndTime>2026-12-01T00:00:00.000+01:00</ns2:overallEndTime>
                </ns2:validityTimeSpecification>
              </ns2:validity>
              <ns2:groupOfLocations xsi:type="ns2:Point">
                <ns2:tpegPointLocation>
                  <ns2:point>
                    <ns2:pointCoordinates>
                      <ns2:latitude>46.0</ns2:latitude>
                      <ns2:longitude>6.0</ns2:longitude>
                    </ns2:pointCoordinates>
                  </ns2:point>
                </ns2:tpegPointLocation>
              </ns2:groupOfLocations>
            </ns2:situationRecord>
        """.trimIndent()

        private val FIXTURE_NARROW_LANES_XML = """
            <ns2:situationRecord xsi:type="ns2:RoadOrCarriagewayOrLaneManagement" id="narrow-1" version="1">
              <ns2:validity>
                <ns2:validityTimeSpecification>
                  <ns2:overallEndTime>2026-12-01T00:00:00.000+01:00</ns2:overallEndTime>
                </ns2:validityTimeSpecification>
              </ns2:validity>
              <ns2:groupOfLocations xsi:type="ns2:Point">
                <ns2:tpegPointLocation>
                  <ns2:point>
                    <ns2:pointCoordinates>
                      <ns2:latitude>47.0</ns2:latitude>
                      <ns2:longitude>3.0</ns2:longitude>
                    </ns2:pointCoordinates>
                  </ns2:point>
                </ns2:tpegPointLocation>
              </ns2:groupOfLocations>
              <ns2:roadOrCarriagewayOrLaneManagementType>narrowLanes</ns2:roadOrCarriagewayOrLaneManagementType>
            </ns2:situationRecord>
        """.trimIndent()
    }
}
