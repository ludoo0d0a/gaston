package fr.geoking.gaston.api.traffic

import org.kobjects.ktxml.api.EventType
import org.kobjects.ktxml.api.XmlPullParser
import org.kobjects.ktxml.mini.MiniXmlPullParser

/**
 * Streaming DATEX II 2.x [SituationPublication] parser (SOAP or bare, optional `ns2:` prefixes).
 *
 * Uses [MiniXmlPullParser] (KtXML / kxml2) — pull-based, no DOM, constant memory beyond the input
 * string and the emitted [TrafficEvent] list. Maps selected `situationRecord` types to events.
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
     * Parses DATEX SituationPublication XML into [TrafficEvent]s.
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
            out
        } catch (_: Exception) {
            emptyList()
        }
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

        val severity = severityFor(recordType, laneManagementType) ?: return null
        val endMs = parseDatexTime(overallEnd)
        if (endMs != null && endMs < nowMs) return null
        val bbox = bboxFromCoords(lats, lons) ?: return null

        prependTypeMessage(
            recordType = recordType,
            accidentType = accidentType,
            vehicleObstructionType = vehicleObstructionType,
            obstructionType = obstructionType,
            laneManagementType = laneManagementType,
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
        )
    }

    private fun severityFor(recordType: String, laneManagementType: String?): TrafficSeverity? =
        when (recordType) {
            "Accident" -> TrafficSeverity.Accident
            in obstructionRecordTypes -> TrafficSeverity.Accident
            "AbnormalTraffic" -> TrafficSeverity.Congestion
            "ConstructionWorks", "MaintenanceWorks" -> TrafficSeverity.Roadworks
            "RoadOrCarriagewayOrLaneManagement" -> {
                if (laneManagementType != null && laneManagementType in closureManagementTypes) {
                    TrafficSeverity.Closure
                } else {
                    null
                }
            }
            else -> null
        }

    private fun prependTypeMessage(
        recordType: String,
        accidentType: String?,
        vehicleObstructionType: String?,
        obstructionType: String?,
        laneManagementType: String?,
        messageParts: MutableList<String>,
    ) {
        val head: String? = when (recordType) {
            "Accident" -> accidentType
            "VehicleObstruction" -> vehicleObstructionType ?: recordType
            in obstructionRecordTypes -> obstructionType ?: recordType
            "RoadOrCarriagewayOrLaneManagement" -> laneManagementType
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
     * Parses DATEX timestamps like `2026-10-04T00:00:13.685+02:00` or `…Z` to epoch millis.
     */
    fun parseDatexTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            val trimmed = s.trim()
            val tIdx = trimmed.indexOf('T')
            if (tIdx < 0) return null
            val dateParts = trimmed.substring(0, tIdx).split("-")
            if (dateParts.size < 3) return null
            val y = dateParts[0].toInt()
            val m = dateParts[1].toInt().coerceIn(1, 12)
            val d = dateParts[2].toInt().coerceIn(1, 31)

            val afterT = trimmed.substring(tIdx + 1)
            var offsetMinutes = 0
            val timePart: String
            when {
                afterT.endsWith("Z", ignoreCase = true) -> {
                    timePart = afterT.dropLast(1)
                    offsetMinutes = 0
                }
                else -> {
                    val plusIdx = afterT.lastIndexOf('+')
                    val minusIdx = afterT.lastIndexOf('-')
                    val offIdx = when {
                        plusIdx > 0 -> plusIdx
                        minusIdx > 0 -> minusIdx
                        else -> -1
                    }
                    if (offIdx > 0) {
                        timePart = afterT.substring(0, offIdx)
                        val sign = if (afterT[offIdx] == '-') -1 else 1
                        val off = afterT.substring(offIdx + 1)
                        val oh = off.substringBefore(':').toIntOrNull() ?: 0
                        val om = off.substringAfter(':', "0").toIntOrNull() ?: 0
                        offsetMinutes = sign * (oh * 60 + om)
                    } else {
                        timePart = afterT
                    }
                }
            }
            val timeBits = timePart.split(":", ".")
            val h = timeBits.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return null
            val min = timeBits.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: return null
            val sec = timeBits.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

            val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
            val leap = if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 1 else 0
            val dayOfYear = monthDays.getOrElse(m - 1) { 0 } + d + if (m > 2) leap else 0
            val yearDays = (y - 1970) * 365 + (y - 1968) / 4 - (y - 1900) / 100 + (y - 1600) / 400
            val totalDays = yearDays + dayOfYear - 1
            val utcSeconds = totalDays * 86400L + h * 3600L + min * 60L + sec - offsetMinutes * 60L
            utcSeconds * 1000L
        } catch (_: Exception) {
            null
        }
    }
}
