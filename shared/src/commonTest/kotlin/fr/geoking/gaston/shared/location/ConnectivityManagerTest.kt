package fr.geoking.gaston.shared.location

import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkSettings
import fr.geoking.gaston.shared.network.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.AfterTest

class ConnectivityManagerTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    class MockNetworkSettings : NetworkSettings {
        override var lastCountryCode: String? = null
        override var lastCountryName: String? = null
        override var lastOperatorName: String? = null
        override var lastIsConnected: Boolean = false
        override var lastIsRoaming: Boolean = false
    }

    class MockNetworkService : NetworkService {
        private val _status = MutableStateFlow(NetworkStatus())
        override val status: StateFlow<NetworkStatus> = _status

        fun updateStatus(newStatus: NetworkStatus) {
            _status.value = newStatus
        }

        override suspend fun getCurrentStatus(): NetworkStatus = _status.value
    }

    @Test
    fun testInitialStatus_persistsToSettings() = runBlocking {
        val settings = MockNetworkSettings()
        val service = MockNetworkService()

        val initialStatus = NetworkStatus(
            countryCode = "FR",
            countryName = "France",
            operatorName = "Orange",
            isConnected = true,
            isRoaming = false
        )
        service.updateStatus(initialStatus)

        ConnectivityManager(testScope, service, settings)

        // Yield to allow coroutine to run
        delay(200)

        assertEquals("FR", settings.lastCountryCode)
        assertEquals("France", settings.lastCountryName)
        assertEquals("Orange", settings.lastOperatorName)
        assertEquals(true, settings.lastIsConnected)
        assertEquals(false, settings.lastIsRoaming)
    }

    @Test
    fun testCountryChange_emitsWelcomeEvent() = runBlocking {
        val settings = MockNetworkSettings()
        val service = MockNetworkService()

        service.updateStatus(NetworkStatus(
            countryCode = "FR",
            countryName = "France",
            isConnected = true
        ))

        val manager = ConnectivityManager(testScope, service, settings)
        delay(200)

        val events = mutableListOf<ConnectivityEvent>()
        val job = testScope.launch {
            manager.connectivityEvents.collect {
                events.add(it)
            }
        }
        delay(100)

        service.updateStatus(NetworkStatus(
            countryCode = "DE",
            countryName = "Germany",
            isConnected = true
        ))

        delay(500)

        assertEquals(1, events.size, "Expected 1 event, got ${events.size}")
        assertEquals("welcome to Germany", events[0].title)

        job.cancel()
    }

    @Test
    fun testOperatorChange_emitsOperatorEvent() = runBlocking {
        val settings = MockNetworkSettings()
        val service = MockNetworkService()

        service.updateStatus(NetworkStatus(
            countryCode = "FR",
            countryName = "France",
            operatorName = "Orange",
            isConnected = true
        ))

        val manager = ConnectivityManager(testScope, service, settings)
        delay(200)

        val events = mutableListOf<ConnectivityEvent>()
        val job = testScope.launch {
            manager.connectivityEvents.collect { events.add(it) }
        }
        delay(100)

        service.updateStatus(NetworkStatus(
            countryCode = "FR",
            countryName = "France",
            operatorName = "SFR",
            isConnected = true
        ))

        delay(500)

        assertEquals(1, events.size, "Expected 1 event, got ${events.size}")
        assertEquals("Network changed from Orange to SFR.", events[0].title)
        assertEquals("SFR", events[0].message)

        job.cancel()
    }

    @Test
    fun testConnectionLost_doesNotEmitEvent() = runBlocking {
        val settings = MockNetworkSettings()
        val service = MockNetworkService()

        service.updateStatus(NetworkStatus(
            isConnected = true
        ))

        val manager = ConnectivityManager(testScope, service, settings)
        delay(200)

        val events = mutableListOf<ConnectivityEvent>()
        val job = testScope.launch {
            manager.connectivityEvents.collect { events.add(it) }
        }
        delay(100)

        service.updateStatus(NetworkStatus(
            isConnected = false
        ))

        delay(500)

        assertEquals(0, events.size, "Expected 0 events, got ${events.size}")
        assertEquals(false, settings.lastIsConnected, "State should still be followed")

        job.cancel()
    }
}
