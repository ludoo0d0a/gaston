package fr.geoking.gaston.api.toll

import kotlinx.serialization.json.Json

/**
 * Parses OpenTollData JSON string into [OpenTollDataModel].
 * Returns null on parse error.
 */
object OpenTollDataParser {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(jsonString: String): OpenTollDataModel? = runCatching {
        json.decodeFromString<OpenTollDataModel>(jsonString)
    }.getOrNull()
}
