package fr.geoking.gaston.shared.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface DebugPayloadStorage {
    fun storePayload(logId: String, isRequest: Boolean, body: String)
    fun getPayload(logId: String, isRequest: Boolean): String?
    fun deletePayloads(logId: String)
    fun clear()
}

object DebugLogPayloadCache {
    var storage: DebugPayloadStorage? = null

    fun store(logId: String, isRequest: Boolean, body: String) {
        if (body.isBlank()) return
        storage?.storePayload(logId, isRequest, body)
    }

    fun get(logId: String, isRequest: Boolean): String? {
        return storage?.getPayload(logId, isRequest)
    }

    fun remove(logId: String) {
        storage?.deletePayloads(logId)
    }

    fun clear() {
        storage?.clear()
    }
}

/**
 * Recursively traverses a [JsonElement] and limits any [JsonArray] to at most [maxItems] items.
 * If an array exceeds [maxItems], the array elements after [maxItems] are replaced with a single
 * 21st string element named `"and-more"`.
 */
fun limitJsonArrays(element: JsonElement, maxItems: Int = 20): JsonElement {
    return when (element) {
        is JsonObject -> {
            JsonObject(element.mapValues { limitJsonArrays(it.value, maxItems) })
        }
        is JsonArray -> {
            if (element.size > maxItems) {
                val truncatedList = element.take(maxItems).map { limitJsonArrays(it, maxItems) }.toMutableList()
                truncatedList.add(JsonPrimitive("and-more"))
                JsonArray(truncatedList)
            } else {
                JsonArray(element.map { limitJsonArrays(it, maxItems) })
            }
        }
        else -> element
    }
}

/**
 * Parses [body] into a [JsonElement] and applies [limitJsonArrays].
 * Returns `null` if [body] is not valid JSON.
 */
fun parseAndLimitJson(body: String, maxItems: Int = 20): JsonElement? {
    if (body.isBlank()) return null
    return try {
        val parsed = Json.parseToJsonElement(body)
        limitJsonArrays(parsed, maxItems)
    } catch (_: Throwable) {
        null
    }
}
