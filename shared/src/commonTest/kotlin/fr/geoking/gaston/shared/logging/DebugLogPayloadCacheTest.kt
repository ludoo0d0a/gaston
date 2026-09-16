package fr.geoking.gaston.shared.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebugLogPayloadCacheTest {

    @Test
    fun testLimitJsonArraysSmallArray() {
        val json = """[1, 2, 3]"""
        val element = Json.parseToJsonElement(json)
        val limited = limitJsonArrays(element, maxItems = 20)
        assertTrue(limited is JsonArray)
        assertEquals(3, limited.size)
        assertEquals(JsonPrimitive(1), limited[0])
        assertEquals(JsonPrimitive(2), limited[1])
        assertEquals(JsonPrimitive(3), limited[2])
    }

    @Test
    fun testLimitJsonArraysHugeArray() {
        val list = (1..30).joinToString(", ")
        val json = """[$list]"""
        val element = Json.parseToJsonElement(json)
        val limited = limitJsonArrays(element, maxItems = 20)
        assertTrue(limited is JsonArray)
        assertEquals(21, limited.size)
        assertEquals(JsonPrimitive(1), limited[0])
        assertEquals(JsonPrimitive(20), limited[19])
        assertEquals(JsonPrimitive("and-more"), limited[20])
    }

    @Test
    fun testLimitJsonArraysNestedStructure() {
        val list = (1..25).map { """{"id": $it}""" }.joinToString(", ")
        val json = """{"status": "ok", "items": [$list]}"""
        val element = Json.parseToJsonElement(json)
        val limited = limitJsonArrays(element, maxItems = 20)
        assertTrue(limited is JsonObject)
        val items = limited["items"]
        assertTrue(items is JsonArray)
        assertEquals(21, items.size)
        assertEquals(JsonPrimitive("and-more"), items[20])
    }

    @Test
    fun testParseAndLimitJson() {
        val list = (1..22).joinToString(", ")
        val json = """{"data": [$list]}"""
        val limited = parseAndLimitJson(json, maxItems = 20)
        assertNotNull(limited)
        assertTrue(limited is JsonObject)
        val data = limited["data"] as JsonArray
        assertEquals(21, data.size)
        assertEquals(JsonPrimitive("and-more"), data[20])
    }

    @Test
    fun testInFileMemoryStorageMock() {
        val memoryStorage = object : DebugPayloadStorage {
            val map = mutableMapOf<String, String>()
            override fun storePayload(logId: String, isRequest: Boolean, body: String) {
                map["${logId}_$isRequest"] = body
            }
            override fun getPayload(logId: String, isRequest: Boolean): String? {
                return map["${logId}_$isRequest"]
            }
            override fun deletePayloads(logId: String) {
                map.remove("${logId}_true")
                map.remove("${logId}_false")
            }
            override fun clear() {
                map.clear()
            }
        }
        DebugLogPayloadCache.storage = memoryStorage
        DebugLogPayloadCache.store("log1", isRequest = false, body = "hello world")
        assertEquals("hello world", DebugLogPayloadCache.get("log1", isRequest = false))
        DebugLogPayloadCache.clear()
        assertEquals(null, DebugLogPayloadCache.get("log1", isRequest = false))
    }
}
