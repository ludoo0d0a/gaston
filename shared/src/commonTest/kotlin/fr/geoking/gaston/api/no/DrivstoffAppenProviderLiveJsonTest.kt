package fr.geoking.gaston.api.no

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DrivstoffAppenProviderLiveJsonTest {

    @Test
    fun parsesCurrentBackendResponseShape() = runBlocking {
        val body =
            """[{"id":"j1i4da8bf8wkcql","name":"Fredensborg","brand":null,"country":"Norway","location":{"latitude":59.92082191643188,"longitude":10.75106396077194,"address":"Maridalsveien 10, 0178 Oslo","country":"Norway"},"prices":{"gasoline_price":19.04,"gasoline_95_price":19.04,"gasoline_98_price":null,"diesel_price":18.14,"fd_price":null,"hvo100_price":null,"last_updated":"2026-05-18T13:00:21Z"},"amenities":{"services":null,"amenities":null,"opening_hours":null,"phone":0,"rating":null,"review_count":null},"station_name":"Fredensborg","description":"✅✅Success✅✅","logo":"https://drivstoffapp-db.valiantlynx.com/api/files/pbc_4040973964/1a4ese2pswjfp19/uno_x_ppf9f3vu9b.png","street":"Maridalsveien 10","city":"OSLO","zip":"","municipality":"","county":"","has_electric":false,"has_wash":false,"has_gas":false,"is_truck_friendly":false,"is_company_car_or_van_friendly":false,"is_pure_truck_stop":false,"gas_details":null,"wash_details":null,"nearby_services":null,"price_history":null,"is_active":true,"verified":false,"closed":false,"visits":360,"primary":"","station_link":"https://raw.githubusercontent.com/edatut/models/main/uno-x-logo.webp","created":"2023-04-04T16:54:44.627000Z","updated":"2026-05-18T13:00:21Z"}]"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.OK) })
        val provider = DrivstoffAppenProvider(client, country = "Norway", countryIso2 = "NO")

        val pois = provider.getGasStations(59.9139, 10.7522)

        assertEquals(1, pois.size, "expected one station from live-shaped JSON")
    }
}
