package fr.geoking.gaston.api.traffic

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FranceTrafficProviderTest {

    private fun event(id: String, road: String, providerHint: String) = TrafficEvent(
        roadRef = road,
        severity = TrafficSeverity.Accident,
        message = providerHint,
        bbox = Bbox(48.8, 2.3, 48.9, 2.4),
        sourceId = id
    )

    @Test
    fun mergesFeeds_actionCOverwritesSameSourceId() = runBlocking {
        val open = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(
                events = listOf(event("same", "N1", "open"), event("only-open", "N2", "open")),
                providerId = "bison_fute"
            )
        )
        val actionB = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(
                events = listOf(event("same", "N1", "B")),
                providerId = TipiActionTrafficProvider.ACTION_B_ID
            )
        )
        val actionC = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(
                events = listOf(event("same", "N1", "C")),
                providerId = TipiActionTrafficProvider.ACTION_C_ID
            )
        )
        val france = FranceTrafficProvider(
            openProvider = open,
            actionB = actionB,
            actionC = actionC
        )
        val result = france.getTraffic(TrafficRequest.Bbox(48.8, 2.3, 48.9, 2.4))
        assertEquals(FranceTrafficProvider.PROVIDER_ID, result!!.providerId)
        val byId = result.events.associateBy { it.sourceId }
        assertEquals("C", byId["same"]!!.message)
        assertEquals("open", byId["only-open"]!!.message)
    }

    @Test
    fun outsideFrance_returnsNull() = runBlocking {
        val open = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(events = listOf(event("x", "N1", "open")), providerId = "bison_fute")
        )
        val france = FranceTrafficProvider(openProvider = open)
        assertNull(france.getTraffic(TrafficRequest.Bbox(40.0, -74.0, 41.0, -73.0)))
    }

    @Test
    fun withoutRestrictedCreds_openOnlyStillWorks() = runBlocking {
        val open = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(events = listOf(event("o1", "A6", "open")), providerId = "bison_fute")
        )
        val disabledTipi = FakeProvider(enabledFlag = false, info = null)
        val france = FranceTrafficProvider(
            openProvider = open,
            actionB = disabledTipi,
            actionC = disabledTipi
        )
        val result = france.getTraffic(TrafficRequest.Bbox(48.8, 2.3, 48.9, 2.4))
        assertEquals(1, result!!.events.size)
        assertTrue(result.events[0].message!!.contains("open"))
    }

    @Test
    fun actionBException_doesNotFailOpenTraffic() = runBlocking {
        val open = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(events = listOf(event("o1", "A6", "open")), providerId = "bison_fute")
        )
        val failingActionB = ThrowingProvider()
        val france = FranceTrafficProvider(
            openProvider = open,
            actionB = failingActionB,
            actionC = null
        )
        val result = france.getTraffic(TrafficRequest.Bbox(48.8, 2.3, 48.9, 2.4))
        assertEquals(1, result!!.events.size)
        assertEquals("o1", result.events[0].sourceId)
    }

    @Test
    fun actionCOverwriteWithoutCoords_doesNotStripCoordinatesFromOpen() = runBlocking {
        val open = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(events = listOf(event("o1", "A6", "open_with_coords")), providerId = "bison_fute")
        )
        val actionCNoCoords = FakeProvider(
            enabledFlag = true,
            info = TrafficInfo(
                events = listOf(
                    TrafficEvent(
                        roadRef = "A6",
                        severity = TrafficSeverity.Accident,
                        message = "action_c_no_coords",
                        bbox = null,
                        latitude = null,
                        longitude = null,
                        sourceId = "o1"
                    )
                ),
                providerId = TipiActionTrafficProvider.ACTION_C_ID
            )
        )
        val france = FranceTrafficProvider(
            openProvider = open,
            actionB = null,
            actionC = actionCNoCoords
        )
        val result = france.getTraffic(TrafficRequest.Bbox(48.8, 2.3, 48.9, 2.4))
        assertEquals(1, result!!.events.size)
        val event = result.events[0]
        assertEquals("o1", event.sourceId)
        assertTrue(event.poiLatitude != null, "Coordinates should be preserved from open provider")
    }

    private class FakeProvider(
        private val enabledFlag: Boolean,
        private val info: TrafficInfo?
    ) : TrafficProvider {
        override val enabled: Boolean get() = enabledFlag
        override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? = info
    }

    private class ThrowingProvider : TrafficProvider {
        override val enabled: Boolean get() = true
        override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
            throw RuntimeException("TIPI HTTP 401 Unauthorized")
        }
    }

    @Test
    fun trafficEventToPoi_resolvesCorrectSourceNames() {
        val tomtomEvent = TrafficEvent(
            roadRef = "TomTom",
            severity = TrafficSeverity.Accident,
            bbox = Bbox(48.8, 2.3, 48.9, 2.4),
            sourceId = "tt_1"
        )
        val tipiEvent = TrafficEvent(
            roadRef = "A6",
            severity = TrafficSeverity.Congestion,
            bbox = Bbox(48.8, 2.3, 48.9, 2.4),
            sourceId = "ACTION_B_123"
        )
        val bisonEvent = TrafficEvent(
            roadRef = "A13",
            severity = TrafficSeverity.Roadworks,
            bbox = Bbox(48.8, 2.3, 48.9, 2.4),
            sourceId = "bison_fute_456"
        )

        assertEquals("TomTom Traffic", tomtomEvent.toPoi().source)
        assertEquals("TIPI", tipiEvent.toPoi().source)
        assertEquals("Bison Futé", bisonEvent.toPoi().source)
    }
}
