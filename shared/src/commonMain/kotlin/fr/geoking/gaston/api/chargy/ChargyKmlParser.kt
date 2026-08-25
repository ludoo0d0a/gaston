package fr.geoking.gaston.api.chargy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parser for Chargy KML data.
 * Extracts station name, address, coordinates, and real-time availability from ExtendedData.
 * Embedded availability is in a JSON string within <value> tags inside <Data name="chargingdevice">.
 */
object ChargyKmlParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(kml: String): List<ChargyStation> {
        val stations = mutableListOf<ChargyStation>()
        var offset = 0
        while (true) {
            // Find start of Placemark tag, allowing for attributes/namespaces (e.g. <Placemark or <ns:Placemark)
            val match = "<(?:[a-zA-Z0-9]+:)?Placemark\\b".toRegex().find(kml, offset)
            if (match == null) break

            val startPlacemark = match.range.first
            val actualTag = match.value.substring(1)

            val endTag = "</$actualTag>"
            val endPlacemark = kml.indexOf(endTag, startPlacemark)
            if (endPlacemark == -1) break

            val placemarkContent = kml.substring(startPlacemark, endPlacemark)
            parsePlacemark(placemarkContent)?.let { stations.add(it) }

            offset = endPlacemark + endTag.length
        }
        return stations
    }

    private fun parsePlacemark(content: String): ChargyStation? {
        val name = extractTag(content, "name") ?: "Chargy Station"
        val address = extractTag(content, "address") ?: ""

        // Find coordinates robustly, it might be inside a <Point> tag
        var coordsStr = extractTag(content, "coordinates")
        if (coordsStr == null) {
            val pointContent = extractTag(content, "Point")
            if (pointContent != null) {
                coordsStr = extractTag(pointContent, "coordinates")
            }
        }

        if (coordsStr == null) return null
        val coords = coordsStr.split(",")
        if (coords.size < 2) return null
        val lon = coords[0].trim().toDoubleOrNull() ?: return null
        val lat = coords[1].trim().toDoubleOrNull() ?: return null

        var totalConnectors = 0
        var availableConnectors = 0
        val connectorTypes = mutableSetOf<String>()
        var maxPower = 0.0

        // Availability and power are in ExtendedData/Data[@name='chargingdevice']/value
        var dataOffset = 0
        while (true) {
            val startData = content.indexOf("<Data", dataOffset)
            if (startData == -1) break

            val endData = content.indexOf("</Data>", startData)
            if (endData == -1) break

            val dataOpeningEnd = content.indexOf(">", startData)
            if (dataOpeningEnd == -1 || dataOpeningEnd > endData) break

            val openingTag = content.substring(startData, dataOpeningEnd + 1)
            if (openingTag.contains("chargingdevice")) {
                val dataContent = content.substring(dataOpeningEnd + 1, endData)
                val jsonStr = extractTag(dataContent, "value")
                if (jsonStr != null) {
                    try {
                        val decoded = json.parseToJsonElement(jsonStr).jsonObject
                        val connectors = decoded["connectors"]?.jsonArray
                        connectors?.forEach {
                            val c = it.jsonObject
                            totalConnectors++
                            val status = c["description"]?.jsonPrimitive?.content
                                ?: c["status"]?.jsonPrimitive?.content
                            if (status?.equals("AVAILABLE", ignoreCase = true) == true ||
                                status?.equals("FREE", ignoreCase = true) == true ||
                                status?.equals("IDLE", ignoreCase = true) == true) {
                                availableConnectors++
                            }
                            val power = c["maxchspeed"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                ?: c["power"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                ?: 0.0
                            if (power > maxPower) maxPower = power

                            val connectorTypeStr = c["connectorType"]?.jsonPrimitive?.content
                                ?: c["type"]?.jsonPrimitive?.content
                                ?: c["standard"]?.jsonPrimitive?.content
                            if (!connectorTypeStr.isNullOrBlank()) {
                                connectorTypes.add(connectorTypeStr.lowercase())
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            dataOffset = endData + "</Data>".length
        }

        if (connectorTypes.isEmpty() && totalConnectors > 0) {
            connectorTypes.add("type_2")
        }

        return ChargyStation(
            name = name,
            address = address,
            latitude = lat,
            longitude = lon,
            totalConnectors = totalConnectors,
            availableConnectors = availableConnectors,
            maxPowerKw = maxPower,
            connectorTypes = connectorTypes
        )
    }

    private fun extractTag(content: String, tag: String): String? {
        // Robustly find start tag with potential attributes: <tag ...> or <prefix:tag ...>
        var startTagIndex = content.indexOf("<$tag")
        var actualTag = tag

        if (startTagIndex == -1) {
            val match = "<[a-zA-Z0-9]+:$tag".toRegex().find(content)
            if (match != null) {
                startTagIndex = match.range.first
                actualTag = match.value.substring(1)
            }
        }

        if (startTagIndex == -1) return null

        val nextChar = content.getOrNull(startTagIndex + actualTag.length + 1)
        if (nextChar != null && nextChar != '>' && !nextChar.isWhitespace() && nextChar != '/') return null

        val startTagEndIndex = content.indexOf(">", startTagIndex)
        if (startTagEndIndex == -1) return null

        val endTag = "</$actualTag>"
        val endTagIndex = content.indexOf(endTag, startTagEndIndex)
        if (endTagIndex == -1) return null

        return content.substring(startTagEndIndex + 1, endTagIndex).trim()
    }
}

data class ChargyStation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val totalConnectors: Int,
    val availableConnectors: Int,
    val maxPowerKw: Double,
    val connectorTypes: Set<String>
)
