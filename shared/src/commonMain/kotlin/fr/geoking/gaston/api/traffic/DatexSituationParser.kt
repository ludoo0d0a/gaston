package fr.geoking.gaston.api.traffic

/**
 * Minimal regex parser for DATEX II 2.x SituationPublication XML (SOAP or bare),
 * with optional `ns2:` prefixes. Maps selected situationRecord types to [TrafficEvent].
 */
object DatexSituationParser {

    private val ns = """(?:ns2:)?"""
    private val recordStart = Regex(
        """<$ns(?:situationRecord)\s+([^>]*)xsi:type="(?:ns2:)?([^"]+)"([^>]*)>""",
        RegexOption.IGNORE_CASE
    )
    private val recordEndNs = "</ns2:situationRecord>"
    private val recordEndBare = "</situationRecord>"
    private val idAttr = Regex("""\bid\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val latitude = Regex("""<$ns(?:latitude)>([^<]+)</$ns(?:latitude)>""", RegexOption.IGNORE_CASE)
    private val longitude = Regex("""<$ns(?:longitude)>([^<]+)</$ns(?:longitude)>""", RegexOption.IGNORE_CASE)
    private val linkName = Regex(
        """<$ns(?:tpegOtherPointDescriptorType)>\s*linkName\s*</$ns(?:tpegOtherPointDescriptorType)>""",
        RegexOption.IGNORE_CASE
    )
    private val valueTag = Regex("""<$ns(?:value)(?:\s[^>]*)?>([^<]+)</$ns(?:value)>""", RegexOption.IGNORE_CASE)
    private val tpegDirection = Regex(
        """<$ns(?:tpegDirection)>([^<]+)</$ns(?:tpegDirection)>""",
        RegexOption.IGNORE_CASE
    )
    private val commentBlock = Regex(
        """<$ns(?:generalPublicComment)>(.*?)</$ns(?:generalPublicComment)>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val commentType = Regex(
        """<$ns(?:commentType)>([^<]+)</$ns(?:commentType)>""",
        RegexOption.IGNORE_CASE
    )
    private val overallEndTime = Regex(
        """<$ns(?:overallEndTime)>([^<]+)</$ns(?:overallEndTime)>""",
        RegexOption.IGNORE_CASE
    )
    private val versionTime = Regex(
        """<$ns(?:situationRecordVersionTime)>([^<]+)</$ns(?:situationRecordVersionTime)>""",
        RegexOption.IGNORE_CASE
    )
    private val vehicleObstructionType = Regex(
        """<$ns(?:vehicleObstructionType)>([^<]+)</$ns(?:vehicleObstructionType)>""",
        RegexOption.IGNORE_CASE
    )
    private val obstructionType = Regex(
        """<$ns(?:obstructionType)>([^<]+)</$ns(?:obstructionType)>""",
        RegexOption.IGNORE_CASE
    )
    private val accidentType = Regex(
        """<$ns(?:accidentType)>([^<]+)</$ns(?:accidentType)>""",
        RegexOption.IGNORE_CASE
    )
    private val laneManagementType = Regex(
        """<$ns(?:roadOrCarriagewayOrLaneManagementType)>([^<]+)</$ns(?:roadOrCarriagewayOrLaneManagementType)>""",
        RegexOption.IGNORE_CASE
    )

    private val obstructionRecordTypes = setOf(
        "VehicleObstruction",
        "GeneralObstruction",
        "EnvironmentalObstruction",
        "AnimalPresenceObstruction",
        "InfrastructureDamageObstruction"
    )

    private val closureManagementTypes = setOf(
        "roadClosed",
        "carriagewayClosures",
        "laneClosures",
        "closedPermanentlyForTheWinter"
    )

    /**
     * Parses DATEX SituationPublication XML into [TrafficEvent]s.
     * Skips records with [overallEndTime] in the past and unsupported record types.
     */
    fun parse(
        xml: String,
        nowMs: Long = System.currentTimeMillis(),
        defaultRoadRef: String = "FR"
    ): List<TrafficEvent> {
        if (xml.isBlank()) return emptyList()
        val out = mutableListOf<TrafficEvent>()
        var searchFrom = 0
        while (true) {
            val startMatch = recordStart.find(xml, searchFrom) ?: break
            val recordType = startMatch.groupValues[2]
            val attrs = startMatch.groupValues[1] + startMatch.groupValues[3]
            val contentStart = startMatch.range.last + 1
            val endNs = xml.indexOf(recordEndNs, contentStart, ignoreCase = true)
            val endBare = xml.indexOf(recordEndBare, contentStart, ignoreCase = true)
            val endIdx = when {
                endNs >= 0 && (endBare < 0 || endNs <= endBare) -> endNs
                endBare >= 0 -> endBare
                else -> -1
            }
            if (endIdx < 0) break
            val endTagLen = if (endIdx == endNs) recordEndNs.length else recordEndBare.length
            val block = xml.substring(contentStart, endIdx)
            searchFrom = endIdx + endTagLen

            val severity = severityFor(recordType, block) ?: continue
            val endStr = overallEndTime.find(block)?.groupValues?.get(1)
            val endMs = parseDatexTime(endStr)
            if (endMs != null && endMs < nowMs) continue

            val bbox = bboxFromCoords(block) ?: continue
            val id = idAttr.find(attrs)?.groupValues?.get(1)
            val road = extractLinkName(block) ?: defaultRoadRef
            val direction = tpegDirection.find(block)?.groupValues?.get(1)?.trim()
            val message = buildMessage(recordType, block)
            val updatedAt = parseDatexTime(versionTime.find(block)?.groupValues?.get(1))

            out.add(
                TrafficEvent(
                    roadRef = road,
                    direction = direction,
                    severity = severity,
                    message = message,
                    travelTimeSeconds = null,
                    bbox = bbox,
                    sourceId = id,
                    updatedAt = updatedAt
                )
            )
        }
        return out
    }

    private fun severityFor(recordType: String, block: String): TrafficSeverity? = when (recordType) {
        "Accident" -> TrafficSeverity.Accident
        in obstructionRecordTypes -> TrafficSeverity.Accident
        "AbnormalTraffic" -> TrafficSeverity.Congestion
        "ConstructionWorks", "MaintenanceWorks" -> TrafficSeverity.Roadworks
        "RoadOrCarriagewayOrLaneManagement" -> {
            val mgmt = laneManagementType.find(block)?.groupValues?.get(1)?.trim()
            if (mgmt != null && mgmt in closureManagementTypes) TrafficSeverity.Closure else null
        }
        else -> null
    }

    private fun extractLinkName(block: String): String? {
        val linkMatch = linkName.find(block) ?: return null
        val before = block.substring(0, linkMatch.range.first)
        val values = valueTag.findAll(before).toList()
        return values.lastOrNull()?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun buildMessage(recordType: String, block: String): String? {
        val parts = mutableListOf<String>()
        when (recordType) {
            "Accident" -> {
                accidentType.find(block)?.groupValues?.get(1)?.trim()?.let { parts.add(it) }
            }
            "VehicleObstruction" -> {
                vehicleObstructionType.find(block)?.groupValues?.get(1)?.trim()?.let { parts.add(it) }
                    ?: parts.add(recordType)
            }
            in obstructionRecordTypes -> {
                obstructionType.find(block)?.groupValues?.get(1)?.trim()?.let { parts.add(it) }
                    ?: parts.add(recordType)
            }
            "RoadOrCarriagewayOrLaneManagement" -> {
                laneManagementType.find(block)?.groupValues?.get(1)?.trim()?.let { parts.add(it) }
            }
            else -> parts.add(recordType)
        }
        for (m in commentBlock.findAll(block)) {
            val commentXml = m.groupValues[1]
            val type = commentType.find(commentXml)?.groupValues?.get(1)?.trim()
            if (type != null && type != "description" && type != "locationDescriptor") continue
            val text = valueTag.find(commentXml)?.groupValues?.get(1)?.trim() ?: continue
            if (text.isNotBlank()) parts.add(text)
        }
        return parts.take(4).joinToString(" — ").ifBlank { null }
    }

    private fun bboxFromCoords(block: String): Bbox? {
        val lats = latitude.findAll(block).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val lons = longitude.findAll(block).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        if (lats.isEmpty() || lons.isEmpty()) return null
        return Bbox(
            latMin = lats.minOrNull()!!,
            lonMin = lons.minOrNull()!!,
            latMax = lats.maxOrNull()!!,
            lonMax = lons.maxOrNull()!!
        )
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
