package fr.geoking.gaston.api.traffic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatexSituationParserTest {

    private val nowMs = 1_760_000_000_000L // ~2025-10-09 UTC

    @Test
    fun parse_ns2_extractsAccidentAndBrokenDownVehicle_skipsExpired() {
        val events = DatexSituationParser.parse(FIXTURE_NS2_XML, nowMs = nowMs)
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
    fun test_lat_lon_parsing_directly_adjacent() {
        val xml = """
            <situationRecord xsi:type="Accident" id="adj-1" version="1">
              <validity>
                <validityTimeSpecification>
                  <overallEndTime>2026-12-01T00:00:00.000+01:00</overallEndTime>
                </validityTimeSpecification>
              </validity>
              <pointCoordinates><latitude>48.85</latitude><longitude>2.35</longitude></pointCoordinates>
              <accidentType>accident</accidentType>
            </situationRecord>
        """.trimIndent()
        val events = DatexSituationParser.parse(xml, nowMs = nowMs)
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(48.85, event.latitude)
        assertEquals(2.35, event.longitude)
        assertNotNull(event.bbox)
    }

    @Test
    fun parse_bare_tags_without_ns2_prefix() {
        val events = DatexSituationParser.parse(FIXTURE_BARE_XML, nowMs = nowMs)
        assertEquals(1, events.size)
        assertEquals("bare-1", events[0].sourceId)
        assertEquals(TrafficSeverity.Accident, events[0].severity)
        assertEquals("A10", events[0].roadRef)
    }

    @Test
    fun parse_mapsRoadClosedToClosure_andRoadworks() {
        val events = DatexSituationParser.parse(FIXTURE_MANAGEMENT_XML, nowMs = nowMs)
        assertEquals(2, events.size)
        assertEquals(TrafficSeverity.Closure, events.first { it.sourceId == "close-1" }.severity)
        assertEquals(TrafficSeverity.Roadworks, events.first { it.sourceId == "works-1" }.severity)
    }

    @Test
    fun parse_mapsNonClosureLaneManagementToRoadworks() {
        val events = DatexSituationParser.parse(FIXTURE_NARROW_LANES_XML, nowMs = nowMs)
        assertEquals(1, events.size)
        assertEquals(TrafficSeverity.Roadworks, events[0].severity)
        assertTrue(events[0].message?.contains("narrowLanes") == true)
    }

    @Test
    fun parse_roadsideServiceDisruption_parkingClosure() {
        val events = DatexSituationParser.parse(FIXTURE_PARKING_SERVICE_DISRUPTION_XML, nowMs = nowMs)
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("p-closed-1", event.sourceId)
        assertEquals(TrafficSeverity.Closure, event.severity)
        assertNotNull(event.bbox)
        val bbox = event.bbox!!
        assertEquals(48.47, bbox.latMin, 0.01)
        assertEquals(-2.58, bbox.lonMin, 0.01)
        assertNotNull(event.message)
        val message = event.message!!
        assertTrue(message.contains("serviceAreaClosed"))
        assertTrue(message.contains("Station TOTAL de Bel Air fermée"))
    }

    @Test
    fun parse_and_bbox_filtering() {
        val events = DatexSituationParser.parse(FIXTURE_NS2_XML, nowMs = nowMs)
        assertEquals(2, events.size)

        // Bbox covering Marseille area (lat ~43.37, lon ~5.32)
        val queryMarseille = Bbox(latMin = 43.0, lonMin = 5.0, latMax = 44.0, lonMax = 6.0)
        val filteredMarseille = events.filter { e -> e.bbox?.let { it.intersects(queryMarseille) } == true }
        assertEquals(1, filteredMarseille.size)
        assertEquals("acc-1", filteredMarseille[0].sourceId)

        // Bbox around Paris (lat ~48.8, lon ~2.3) where acc-1 and veh-1 are not present
        val queryParis = Bbox(latMin = 48.0, lonMin = 2.0, latMax = 49.0, lonMax = 3.0)
        val filteredParis = events.filter { e -> e.bbox?.let { it.intersects(queryParis) } == true }
        assertTrue(filteredParis.isEmpty())
    }

    @Test
    fun parseDatexTime_handlesOffsetAndZ() {
        val withOffset = DatexSituationParser.parseDatexTime("2026-10-04T00:00:13.685+02:00")
        assertNotNull(withOffset)
        val zulu = DatexSituationParser.parseDatexTime("2026-10-03T22:00:13.685Z")
        assertNotNull(zulu)
        assertEquals(zulu, withOffset)
        assertNull(DatexSituationParser.parseDatexTime(null))
        assertNull(DatexSituationParser.parseDatexTime("not-a-date"))
    }

    @Test
    fun bisonWrapper_delegates() {
        val viaWrapper = BisonFuteDatexParser.parse(FIXTURE_NS2_XML, nowMs = nowMs)
        val viaGeneric = DatexSituationParser.parse(FIXTURE_NS2_XML, nowMs = nowMs)
        assertEquals(viaGeneric.size, viaWrapper.size)
        assertEquals(viaGeneric.map { it.sourceId }, viaWrapper.map { it.sourceId })
    }

    @Test
    fun parse_zone_with_two_different_points_uses_first_point_for_poi_and_populates_zonePoints() {
        val events = DatexSituationParser.parse(FIXTURE_ZONE_DIFF_POINTS_XML, nowMs = nowMs)
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("zone-diff-1", event.sourceId)
        assertEquals(48.85, event.latitude)
        assertEquals(2.35, event.longitude)
        assertEquals(48.85, event.poiLatitude)
        assertEquals(2.35, event.poiLongitude)

        val points = assertNotNull(event.zonePoints)
        assertEquals(2, points.size)
        assertEquals(Pair(48.85, 2.35), points[0])
        assertEquals(Pair(48.88, 2.40), points[1])
    }

    @Test
    fun parse_zone_with_two_identical_points_uses_first_point_for_poi_and_clears_zonePoints() {
        val events = DatexSituationParser.parse(FIXTURE_ZONE_SAME_POINTS_XML, nowMs = nowMs)
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("zone-same-1", event.sourceId)
        assertEquals(48.85, event.latitude)
        assertEquals(2.35, event.longitude)
        assertEquals(48.85, event.poiLatitude)
        assertEquals(2.35, event.poiLongitude)

        assertNull(event.zonePoints)
    }

    companion object {
        private val FIXTURE_NS2_XML = """
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

        private val FIXTURE_BARE_XML = """
            <situationRecord xsi:type="Accident" id="bare-1" version="1">
              <validity>
                <validityTimeSpecification>
                  <overallEndTime>2026-12-01T00:00:00.000+01:00</overallEndTime>
                </validityTimeSpecification>
              </validity>
              <groupOfLocations>
                <tpegPointLocation>
                  <point>
                    <pointCoordinates>
                      <latitude>48.7</latitude>
                      <longitude>2.1</longitude>
                    </pointCoordinates>
                    <name>
                      <descriptor><values><value>A10</value></values></descriptor>
                      <tpegOtherPointDescriptorType>linkName</tpegOtherPointDescriptorType>
                    </name>
                  </point>
                </tpegPointLocation>
              </groupOfLocations>
              <accidentType>accident</accidentType>
            </situationRecord>
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

        private val FIXTURE_ZONE_DIFF_POINTS_XML = """
            <situationRecord xsi:type="Accident" id="zone-diff-1" version="1">
              <validity>
                <validityTimeSpecification>
                  <overallEndTime>2026-12-01T00:00:00.000+01:00</overallEndTime>
                </validityTimeSpecification>
              </validity>
              <groupOfLocations xsi:type="Linear">
                <from>
                  <pointCoordinates>
                    <latitude>48.85</latitude>
                    <longitude>2.35</longitude>
                  </pointCoordinates>
                </from>
                <to>
                  <pointCoordinates>
                    <latitude>48.88</latitude>
                    <longitude>2.40</longitude>
                  </pointCoordinates>
                </to>
              </groupOfLocations>
              <accidentType>accident</accidentType>
            </situationRecord>
        """.trimIndent()

        private val FIXTURE_ZONE_SAME_POINTS_XML = """
            <situationRecord xsi:type="Accident" id="zone-same-1" version="1">
              <validity>
                <validityTimeSpecification>
                  <overallEndTime>2026-12-01T00:00:00.000+01:00</overallEndTime>
                </validityTimeSpecification>
              </validity>
              <groupOfLocations xsi:type="Linear">
                <from>
                  <pointCoordinates>
                    <latitude>48.85</latitude>
                    <longitude>2.35</longitude>
                  </pointCoordinates>
                </from>
                <to>
                  <pointCoordinates>
                    <latitude>48.85</latitude>
                    <longitude>2.35</longitude>
                  </pointCoordinates>
                </to>
              </groupOfLocations>
              <accidentType>accident</accidentType>
            </situationRecord>
        """.trimIndent()

        private val FIXTURE_PARKING_SERVICE_DISRUPTION_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
            <soap:Body>
            <d2LogicalModel xmlns:ns2="http://datex2.eu/schema/2/2_0" modelBaseVersion="2">
            <ns2:payloadPublication xsi:type="ns2:SituationPublication" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" lang="fr">
            <ns2:situation id="s-parking" version="1">
              <ns2:situationRecord xsi:type="ns2:RoadsideServiceDisruption" id="p-closed-1" version="1">
                <ns2:validity>
                  <ns2:validityTimeSpecification>
                    <ns2:overallEndTime>2026-12-01T00:00:00.000+01:00</ns2:overallEndTime>
                  </ns2:validityTimeSpecification>
                </ns2:validity>
                <ns2:generalPublicComment>
                  <ns2:comment><ns2:values><ns2:value lang="fr">Station TOTAL de Bel Air fermée</ns2:value></ns2:values></ns2:comment>
                  <ns2:commentType>description</ns2:commentType>
                </ns2:generalPublicComment>
                <ns2:groupOfLocations xsi:type="ns2:Point">
                  <ns2:tpegPointLocation xsi:type="ns2:TpegSimplePoint">
                    <ns2:point xsi:type="ns2:TpegNonJunctionPoint">
                      <ns2:pointCoordinates>
                        <ns2:latitude>48.476738</ns2:latitude>
                        <ns2:longitude>-2.5898418</ns2:longitude>
                      </ns2:pointCoordinates>
                      <ns2:name>
                        <ns2:descriptor><ns2:values><ns2:value lang="fr">N12</ns2:value></ns2:values></ns2:descriptor>
                        <ns2:tpegOtherPointDescriptorType>linkName</ns2:tpegOtherPointDescriptorType>
                      </ns2:name>
                    </ns2:point>
                  </ns2:tpegPointLocation>
                </ns2:groupOfLocations>
                <ns2:roadsideServiceDisruptionType>serviceAreaClosed</ns2:roadsideServiceDisruptionType>
              </ns2:situationRecord>
            </ns2:situation>
            </ns2:payloadPublication>
            </d2LogicalModel>
            </soap:Body>
            </soap:Envelope>
        """.trimIndent()
    }
}
