package fr.geoking.gaston.shared.network

/**
 * Interface to persist and retrieve the last known network status.
 */
interface NetworkSettings {
    var lastCountryCode: String?
    var lastCountryName: String?
    var lastOperatorName: String?
    var lastIsConnected: Boolean
    var lastIsRoaming: Boolean
}
