package fr.geoking.gaston.api.chargy

import fr.geoking.gaston.poi.IrveDetails
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
            val startPlacemark = kml.indexOf("<Placemark>", offset)
            if (startPlacemark == -1) break
            val endPlacemark = kml.indexOf("</Placemark>", startPlacemark)
            if (endPlacemark == -1) break

            val placemarkContent = kml.substring(startPlacemark, endPlacemark)
            parsePlacemark(placemarkContent)?.let { stations.add(it) }

            offset = endPlacemark + "</Placemark>".length
        }
        return stations
    }

    private fun parsePlacemark(content: String): ChargyStation? {
        val name = extractTag(content, "name") ?: "Chargy Station"
        val address = extractTag(content, "address") ?: ""
        val coordsStr = extractTag(content, "coordinates") ?: return null
        val coords = coordsStr.split(",")
        if (coords.size < 2) return null
        val lon = coords[0].toDoubleOrNull() ?: return null
        val lat = coords[1].toDoubleOrNull() ?: return null

        var totalConnectors = 0
        var availableConnectors = 0
        val connectorTypes = mutableSetOf<String>()
        var maxPower = 0.0

        // Availability and power are in ExtendedData/Data[@name='chargingdevice']/value
        var dataOffset = 0
        while (true) {
            val startData = content.indexOf("<Data name=\"chargingdevice\">", dataOffset)
            if (startData == -1) break
            val endData = content.indexOf("</Data>", startData)
            if (endData == -1) break

            val dataContent = content.substring(startData, endData)
            val jsonStr = extractTag(dataContent, "value")
            if (jsonStr != null) {
                try {
                    val decoded = json.parseToJsonElement(jsonStr).jsonObject
                    val connectors = decoded["connectors"]?.jsonArray
                    connectors?.forEach {
                        val c = it.jsonObject
                        totalConnectors++
                        val status = c["description"]?.jsonPrimitive?.content
                        if (status == "AVAILABLE") {
                            availableConnectors++
                        }
                        val power = c["maxchspeed"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        if (power > maxPower) maxPower = power

                        // Chargy mostly uses Type 2
                        connectorTypes.add("type_2")
                    }
                } catch (_: Exception) {}
            }
            dataOffset = endData + "</Data>".length
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
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val start = content.indexOf(startTag)
        if (start == -1) return null
        val end = content.indexOf(endTag, start)
        if (end == -1) return null
        return content.substring(start + startTag.length, end).trim()
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
