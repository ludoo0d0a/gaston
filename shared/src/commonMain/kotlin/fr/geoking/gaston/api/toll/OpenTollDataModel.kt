package fr.geoking.gaston.api.toll

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root model for OpenTollData JSON (French highway toll).
 * See https://github.com/louis2038/OpenTollData
 */
@Serializable
data class OpenTollDataModel(
    val networks: List<OpenTollNetwork> = emptyList(),
    @SerialName("toll_description") val tollDescription: Map<String, TollBoothDescription> = emptyMap(),
    @SerialName("open_toll_price") val openTollPrice: Map<String, OpenTollPriceEntry> = emptyMap()
)

@Serializable
data class OpenTollNetwork(
    @SerialName("network_name") val networkName: String = "",
    val tolls: List<String> = emptyList(),
    val connection: Map<String, Map<String, ConnectionPrice>> = emptyMap()
)

@Serializable
data class ConnectionPrice(
    val distance: String = "0",
    val price: Map<String, String> = emptyMap()
)

@Serializable
data class TollBoothDescription(
    val lat: String = "0",
    val lon: String = "0",
    val type: String = "close",
    val operator: String? = null,
    @SerialName("operator_ref") val operatorRef: String? = null,
    @SerialName("node_id") val nodeId: List<String> = emptyList(),
    @SerialName("ways_id") val waysId: List<String> = emptyList()
)

@Serializable
data class OpenTollPriceEntry(
    val distance: String = "0",
    val price: Map<String, String> = emptyMap()
)
