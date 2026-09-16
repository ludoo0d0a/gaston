package fr.geoking.gaston.shared.logging

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugLogStoreTest {

    @BeforeTest
    fun setUp() {
        DebugLogStore.clearAll()
    }

    @Test
    fun testResolveProviderName() {
        assertEquals("Austria E-Control", resolveProviderName("api.e-control.at"))
        assertEquals("OpenChargeMap", resolveProviderName("api.openchargemap.io"))
        assertEquals("DataGouv / Etalab", resolveProviderName("data.economie.gouv.fr"))
        assertEquals("Freshmile", resolveProviderName("prod-driver-api.freshmile.com"))
        assertEquals("Germany Tankerkoenig", resolveProviderName("api.tankerkoenig.de"))
        assertNull(resolveProviderName("unknown-host.example.com"))
    }

    @Test
    fun testAddLogAccumulatesHostConsumption() {
        val log1 = NetworkLog(
            id = "1",
            url = "https://api.e-control.at/search",
            host = "api.e-control.at",
            method = "GET",
            requestHeaders = emptyMap(),
            requestBody = null,
            responseHeaders = null,
            responseBody = null,
            statusCode = 200,
            durationMs = 120,
            timestamp = 1000L,
            requestSizeBytes = 100L,
            responseSizeBytes = 500L
        )

        val log2 = NetworkLog(
            id = "2",
            url = "https://api.e-control.at/details",
            host = "api.e-control.at",
            method = "GET",
            requestHeaders = emptyMap(),
            requestBody = null,
            responseHeaders = null,
            responseBody = null,
            statusCode = 200,
            durationMs = 150,
            timestamp = 1005L,
            requestSizeBytes = 50L,
            responseSizeBytes = 1000L
        )

        val log3 = NetworkLog(
            id = "3",
            url = "https://openchargemap.org/v3/poi",
            host = "openchargemap.org",
            method = "GET",
            requestHeaders = emptyMap(),
            requestBody = null,
            responseHeaders = null,
            responseBody = null,
            statusCode = 200,
            durationMs = 200,
            timestamp = 1010L,
            requestSizeBytes = 200L,
            responseSizeBytes = 3000L
        )

        DebugLogStore.addLog(log1)
        DebugLogStore.addLog(log2)
        DebugLogStore.addLog(log3)

        val consumptions = DebugLogStore.hostConsumption.value
        assertEquals(2, consumptions.size)

        val eControl = consumptions["api.e-control.at"]
        assertTrue(eControl != null)
        assertEquals("api.e-control.at", eControl.host)
        assertEquals("Austria E-Control", eControl.providerName)
        assertEquals(150L, eControl.bytesSent)
        assertEquals(1500L, eControl.bytesReceived)
        assertEquals(1650L, eControl.totalBytes)
        assertEquals(2, eControl.requestCount)

        val ocm = consumptions["openchargemap.org"]
        assertTrue(ocm != null)
        assertEquals("openchargemap.org", ocm.host)
        assertEquals("OpenChargeMap", ocm.providerName)
        assertEquals(200L, ocm.bytesSent)
        assertEquals(3000L, ocm.bytesReceived)
        assertEquals(3200L, ocm.totalBytes)
        assertEquals(1, ocm.requestCount)

        assertEquals(350L, DebugLogStore.totalBytesSent.value)
        assertEquals(4500L, DebugLogStore.totalBytesReceived.value)
    }

    @Test
    fun testResetDataConsumption() {
        val log = NetworkLog(
            id = "1",
            url = "https://api.e-control.at/search",
            host = "api.e-control.at",
            method = "GET",
            requestHeaders = emptyMap(),
            requestBody = null,
            responseHeaders = null,
            responseBody = null,
            statusCode = 200,
            durationMs = 120,
            timestamp = 1000L,
            requestSizeBytes = 100L,
            responseSizeBytes = 500L
        )
        DebugLogStore.addLog(log)

        assertEquals(1, DebugLogStore.hostConsumption.value.size)
        assertEquals(100L, DebugLogStore.totalBytesSent.value)
        assertEquals(500L, DebugLogStore.totalBytesReceived.value)

        DebugLogStore.resetDataConsumption()

        assertTrue(DebugLogStore.hostConsumption.value.isEmpty())
        assertEquals(0L, DebugLogStore.totalBytesSent.value)
        assertEquals(0L, DebugLogStore.totalBytesReceived.value)
        // Log entries in `logs` remain until clearLogs()/clearAll() is called
        assertEquals(1, DebugLogStore.logs.value.size)
    }

    @Test
    fun testClearAllResetsEverything() {
        val log = NetworkLog(
            id = "1",
            url = "https://api.e-control.at/search",
            host = "api.e-control.at",
            method = "GET",
            requestHeaders = emptyMap(),
            requestBody = null,
            responseHeaders = null,
            responseBody = null,
            statusCode = 200,
            durationMs = 120,
            timestamp = 1000L,
            requestSizeBytes = 100L,
            responseSizeBytes = 500L
        )
        DebugLogStore.addLog(log)

        DebugLogStore.clearAll()

        assertTrue(DebugLogStore.logs.value.isEmpty())
        assertTrue(DebugLogStore.hostConsumption.value.isEmpty())
        assertEquals(0L, DebugLogStore.totalBytesSent.value)
        assertEquals(0L, DebugLogStore.totalBytesReceived.value)
    }
}
