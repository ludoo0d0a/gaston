package fr.geoking.gaston.shared.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkLogQueryTest {

    @Test
    fun testParseQueryParamsNoQueryString() {
        val log = NetworkLog(
            id = "1",
            url = "https://api.example.com/v1/resource",
            host = "api.example.com",
            method = "GET",
            requestHeaders = emptyMap(),
            requestBody = null,
            responseHeaders = null,
            responseBody = null,
            statusCode = 200,
            durationMs = 100,
            timestamp = 1000L
        )
        assertTrue(log.queryParams.isEmpty())
    }

    @Test
    fun testParseQueryParamsEmptyQueryString() {
        val params = parseQueryParams("https://api.example.com/v1/resource?")
        assertTrue(params.isEmpty())
    }

    @Test
    fun testParseQueryParamsSimple() {
        val params = parseQueryParams("https://api.example.com/v1/resource?lat=48.85&lon=2.35")
        assertEquals(2, params.size)
        assertEquals(listOf("48.85"), params["lat"])
        assertEquals(listOf("2.35"), params["lon"])
    }

    @Test
    fun testParseQueryParamsEncodedAndSpaces() {
        val params = parseQueryParams("https://api.example.com/v1/search?q=hello+world&data=%7B%22foo%22%3A%22bar%22%7D")
        assertEquals(listOf("hello world"), params["q"])
        assertEquals(listOf("{\"foo\":\"bar\"}"), params["data"])
    }

    @Test
    fun testParseQueryParamsMultipleValuesAndFragment() {
        val params = parseQueryParams("https://api.example.com/v1/filter?type=gas&type=diesel#results")
        assertEquals(listOf("gas", "diesel"), params["type"])
    }
}
