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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    fun testCountryChange_emitsBorderCrossingEvent() = runBlocking {
        val settings = MockNetworkSettings()
        settings.lastCountryCode = "FR"
        settings.lastCountryName = "France"

        val service = MockNetworkService()
        service.updateStatus(NetworkStatus(countryCode = "FR", countryName = "France"))

        val manager = ConnectivityManager(testScope, service, settings)

        // Give it a moment to initialize with the initial status
        delay(100)

        val crossingEvents = mutableListOf<String>()
        val job = launch {
            manager.borderCrossingEvents.collect {
                crossingEvents.add(it)
            }
        }

        // Change country
        service.updateStatus(NetworkStatus(countryCode = "BE", countryName = "Belgium"))

        // Wait for event
        delay(200)

        assertEquals(1, crossingEvents.size)
        assertEquals("Belgium", crossingEvents[0])
        assertEquals("BE", settings.lastCountryCode)

        job.cancel()
    }
}
