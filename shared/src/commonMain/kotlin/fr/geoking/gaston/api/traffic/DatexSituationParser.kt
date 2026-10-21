package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.shared.datetime.DateTimeUtils
import org.kobjects.ktxml.api.EventType
import org.kobjects.ktxml.api.XmlPullParser
import org.kobjects.ktxml.mini.MiniXmlPullParser
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Streaming DATEX II 2.x [SituationPublication] parser (SOAP or bare, optional `ns2:` prefixes).
 *
 * Uses [MiniXmlPullParser] (KtXML / kxml2) — pull-based, no DOM, constant memory reading directly
 * from an [InputStream] or String source. Maps selected `situationRecord` types to events.
 */
object DatexSituationParser {

    private val obstructionRecordTypes = setOf(
        "VehicleObstruction",
        "GeneralObstruction",
        "EnvironmentalObstruction",
        "AnimalPresenceObstruction",
        "InfrastructureDamageObstruction",
    )

    private val closureManagementTypes = setOf(
        "roadClosed",
        "carriagewayClosures",
        "laneClosures",
        "closedPermanentlyForTheWinter",
    )

    /**
     * Parses DATEX SituationPublication XML stream into [TrafficEvent]s without buffering the full XML string in memory.
     */
    fun parseStream(
        inputStream: InputStream,
        nowMs: Long = System.currentTimeMillis(),
        defaultRoadRef: String = "FR",
    ): List<TrafficEvent> {
        return try {
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val iterator = object : CharIterator() {
                private var current = reader.read()

                override fun hasNext(): Boolean = current != -1

                override fun nextChar(): Char {
                    val c = current.toChar()
                    current = reader.read()
                    return c
                }
            }
            val parser = MiniXmlPullParser(
                source = iterator,
                processNamespaces = false,
                relaxed = true,
            )
            parseFromParser(parser, nowMs, defaultRoadRef)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses DATEX SituationPublication XML string into [TrafficEvent]s.
     * Skips records with [overallEndTime] in the past and unsupported record types.
     */
    fun parse(
        xml: String,
        nowMs: Long = System.currentTimeMillis(),
        defaultRoadRef: String = "FR",
    ): List<TrafficEvent> {
        if (xml.isBlank()) return emptyList()
        return try {
            val parser = MiniXmlPullParser(
                source = xml,
                processNamespaces = false,
                relaxed = true,
            )
            parseFromParser(parser, nowMs, defaultRoadRef)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseFromParser(
        parser: XmlPullParser,
        nowMs: Long,
        defaultRoadRef: String
    ): List<TrafficEvent> {
        val out = ArrayList<TrafficEvent>()
        while (parser.next() != EventType.END_DOCUMENT) {
            if (parser.eventType != EventType.START_TAG) continue
            if (localName(parser.name) != "situationRecord") continue
            try {
                parseSituationRecord(parser, nowMs, defaultRoadRef)?.let { out.add(it) }
            } catch (_: Exception) {
                // Skip malformed record; continue scanning the feed.
            }
        }
        return out
    }

    private fun parseSituationRecord(
        parser: XmlPullParser,
        nowMs: Long,
        defaultRoadRef: String,
    ): TrafficEvent? {
        val recordType = stripNsPrefix(attrLocal(parser, "type") ?: return null) ?: return null
        val id = attrLocal(parser, "id")
        val recordDepth = parser.depth

        var overallEnd: String? = null
        var versionTime: String? = null
        var direction: String? = null
        var accidentType: String? = null
        var vehicleObstructionType: String? = null
        var obstructionType: String? = null
        var laneManagementType: String? = null
        var roadsideServiceDisruptionType: String? = null
        var reroutingManagementType: String? = null
        var generalNetworkManagementType: String? = null
        var speedManagementType: String? = null
        var disturbanceActivityType: String? = null
        var weatherRelatedRoadConditionType: String? = null
        var lastValue: String? = null
        var roadRef: String? = null
        var inPublicComment = false
        var commentValue: String? = null
        var commentType: String? = null
        val messageParts = ArrayList<String>(4)
        val lats = ArrayList<Double>(4)
        val lons = ArrayList<Double>(4)

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_TAG -> {
                    when (localName(parser.name)) {
                        "generalPublicComment" -> {
                            inPublicComment = true
                            commentValue = null
                            commentType = null
                        }
                        "overallEndTime" -> overallEnd = parser.nextText().trim()
                        "situationRecordVersionTime" -> versionTime = parser.nextText().trim()
                        "latitude" -> parser.nextText().toDoubleOrNull()?.let { lats.add(it) }
                        "longitude" -> parser.nextText().toDoubleOrNull()?.let { lons.add(it) }
                        "tpegDirection" -> direction = parser.nextText().trim().ifBlank { null }
                        "value" -> {
                            val v = parser.nextText().trim()
                            lastValue = v
                            if (inPublicComment) commentValue = v
                        }
                        "tpegOtherPointDescriptorType" -> {
                            if (parser.nextText().trim() == "linkName") {
                                roadRef = lastValue?.takeIf { it.isNotBlank() }
                            }
                        }
                        "commentType" -> commentType = parser.nextText().trim().ifBlank { null }
                        "accidentType" -> accidentType = parser.nextText().trim().ifBlank { null }
                        "vehicleObstructionType" ->
                            vehicleObstructionType = parser.nextText().trim().ifBlank { null }
                        "obstructionType" ->
                            obstructionType = parser.nextText().trim().ifBlank { null }
                        "roadOrCarriagewayOrLaneManagementType" ->
                            laneManagementType = parser.nextText().trim().ifBlank { null }
                        "roadsideServiceDisruptionType" ->
                            roadsideServiceDisruptionType = parser.nextText().trim().ifBlank { null }
                        "reroutingManagementType" ->
                            reroutingManagementType = parser.nextText().trim().ifBlank { null }
                        "generalNetworkManagementType" ->
                            generalNetworkManagementType = parser.nextText().trim().ifBlank { null }
                        "speedManagementType" ->
                            speedManagementType = parser.nextText().trim().ifBlank { null }
                        "disturbanceActivityType" ->
                            disturbanceActivityType = parser.nextText().trim().ifBlank { null }
                        "weatherRelatedRoadConditionType" ->
                            weatherRelatedRoadConditionType = parser.nextText().trim().ifBlank { null }
                    }
                }
                EventType.END_TAG -> {
                    val tag = localName(parser.name)
                    if (tag == "generalPublicComment") {
                        if (commentType == "description" || commentType == "locationDescriptor") {
                            commentValue?.takeIf { it.isNotBlank() }?.let { messageParts.add(it) }
                        }
                        inPublicComment = false
                    }
                    if (tag == "situationRecord" && parser.depth == recordDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        val severity = severityFor(recordType, laneManagementType, roadsideServiceDisruptionType) ?: return null
        val endMs = parseDatexTime(overallEnd)
        if (endMs != null && endMs < nowMs) return null
        val bbox = bboxFromCoords(lats, lons) ?: return null

        val firstLat = lats.firstOrNull()
        val firstLon = lons.firstOrNull()

        val distinctPoints = ArrayList<Pair<Double, Double>>()
        val count = minOf(lats.size, lons.size)
        for (i in 0 until count) {
            val pt = Pair(lats[i], lons[i])
            if (distinctPoints.isEmpty() || distinctPoints.last() != pt) {
                distinctPoints.add(pt)
            }
        }
        val zonePoints = if (distinctPoints.size >= 2) distinctPoints else null

        prependTypeMessage(
            recordType = recordType,
            accidentType = accidentType,
            vehicleObstructionType = vehicleObstructionType,
            obstructionType = obstructionType,
            laneManagementType = laneManagementType,
            roadsideServiceDisruptionType = roadsideServiceDisruptionType,
            reroutingManagementType = reroutingManagementType,
            generalNetworkManagementType = generalNetworkManagementType,
            speedManagementType = speedManagementType,
            disturbanceActivityType = disturbanceActivityType,
            weatherRelatedRoadConditionType = weatherRelatedRoadConditionType,
            messageParts = messageParts,
        )
        val message = messageParts.take(4).joinToString(" — ").ifBlank { null }

        return TrafficEvent(
            roadRef = roadRef ?: defaultRoadRef,
            direction = direction,
            severity = severity,
            message = message,
            travelTimeSeconds = null,
            bbox = bbox,
            sourceId = id,
            updatedAt = parseDatexTime(versionTime),
            latitude = firstLat,
            longitude = firstLon,
            zonePoints = zonePoints,
        )
    }

    private fun severityFor(
        recordType: String,
        laneManagementType: String?,
        roadsideServiceDisruptionType: String?,
    ): TrafficSeverity? =
        when (recordType) {
            "Accident" -> TrafficSeverity.Accident
            in obstructionRecordTypes -> TrafficSeverity.Accident
            "AbnormalTraffic" -> TrafficSeverity.Congestion
            "ConstructionWorks", "MaintenanceWorks" -> TrafficSeverity.Roadworks
            "RoadOrCarriagewayOrLaneManagement" -> {
                if (laneManagementType != null && laneManagementType in closureManagementTypes) {
                    TrafficSeverity.Closure
                } else {
                    TrafficSeverity.Roadworks
                }
            }
            "RoadsideServiceDisruption" -> {
                if (roadsideServiceDisruptionType?.contains("Closed", ignoreCase = true) == true) {
                    TrafficSeverity.Closure
                } else {
                    TrafficSeverity.Unknown
                }
            }
            "WeatherRelatedRoadConditions",
            "ReroutingManagement",
            "GeneralNetworkManagement",
            "SpeedManagement",
            "DisturbanceActivity",
            "GeneralInstructionOrMessageToRoadUsers",
            "OperatorAction" -> TrafficSeverity.Unknown
            else -> TrafficSeverity.Unknown
        }

    private fun prependTypeMessage(
        recordType: String,
        accidentType: String?,
        vehicleObstructionType: String?,
        obstructionType: String?,
        laneManagementType: String?,
        roadsideServiceDisruptionType: String?,
        reroutingManagementType: String?,
        generalNetworkManagementType: String?,
        speedManagementType: String?,
        disturbanceActivityType: String?,
        weatherRelatedRoadConditionType: String?,
        messageParts: MutableList<String>,
    ) {
        val head: String? = when (recordType) {
            "Accident" -> accidentType
            "VehicleObstruction" -> vehicleObstructionType ?: recordType
            in obstructionRecordTypes -> obstructionType ?: recordType
            "RoadOrCarriagewayOrLaneManagement" -> laneManagementType
            "RoadsideServiceDisruption" -> roadsideServiceDisruptionType ?: recordType
            "ReroutingManagement" -> reroutingManagementType ?: recordType
            "GeneralNetworkManagement" -> generalNetworkManagementType ?: recordType
            "SpeedManagement" -> speedManagementType ?: recordType
            "DisturbanceActivity" -> disturbanceActivityType ?: recordType
            "WeatherRelatedRoadConditions" -> weatherRelatedRoadConditionType ?: recordType
            else -> recordType
        }
        if (!head.isNullOrBlank()) {
            messageParts.add(0, head)
        }
    }

    private fun bboxFromCoords(lats: List<Double>, lons: List<Double>): Bbox? {
        if (lats.isEmpty() || lons.isEmpty()) return null
        return Bbox(
            latMin = lats.minOrNull()!!,
            lonMin = lons.minOrNull()!!,
            latMax = lats.maxOrNull()!!,
            lonMax = lons.maxOrNull()!!,
        )
    }

    private fun localName(raw: String): String {
        val i = raw.indexOf(':')
        return if (i >= 0) raw.substring(i + 1) else raw
    }

    private fun stripNsPrefix(raw: String): String? {
        val local = localName(raw).trim()
        return local.takeIf { it.isNotEmpty() }
    }

    private fun attrLocal(parser: XmlPullParser, local: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (localName(parser.getAttributeName(i)).equals(local, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    /**
     * Parses DATEX timestamps like `2026-10-04T00:00:13.685+02:00` or `…Z` to epoch millis using [DateTimeUtils].
     */
    fun parseDatexTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return DateTimeUtils.parseFlexible(s)?.toEpochMilliseconds()
    }
}
