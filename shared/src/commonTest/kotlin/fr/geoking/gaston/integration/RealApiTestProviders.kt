package fr.geoking.gaston.integration

import fr.geoking.gaston.api.argentina.ArgentinaEnergiaProvider
import fr.geoking.gaston.api.belgium.BelgiumOfficialProvider
import fr.geoking.gaston.api.belgium.BelgiumPetrolPricesClient
import fr.geoking.gaston.api.croatia.CroatiaMzoeProvider
import fr.geoking.gaston.api.datagouv.DataGouvProvider
import fr.geoking.gaston.api.denmark.FuelpricesDKProvider
import fr.geoking.gaston.api.dgeg.PortugalDgegProvider
import fr.geoking.gaston.api.econtrol.AustriaEControlProvider
import fr.geoking.gaston.api.finland.PolttoaineProvider
import fr.geoking.gaston.api.greece.GreeceFuelGRProvider
import fr.geoking.gaston.api.ireland.IrelandPickAPumpProvider
import fr.geoking.gaston.api.it.MimitFuelProvider
import fr.geoking.gaston.api.mexico.MexicoCREProvider
import fr.geoking.gaston.api.minetur.SpainMineturProvider
import fr.geoking.gaston.api.moldova.MoldovaAnreProvider
import fr.geoking.gaston.api.netherlands.NetherlandsAnwbProvider
import fr.geoking.gaston.api.no.DrivstoffAppenProvider
import fr.geoking.gaston.api.openvan.OpenVanCampClient
import fr.geoking.gaston.api.openvan.OpenVanCampProvider
import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.romania.RomaniaPecoProvider
import fr.geoking.gaston.api.serbia.SerbiaNisProvider
import fr.geoking.gaston.api.si.GorivaSiProvider
import fr.geoking.gaston.api.tankerkoenig.GermanyTankerkoenigProvider
import fr.geoking.gaston.api.uk.UkCmaFuelProvider
import fr.geoking.gaston.poi.CountryStationProbe
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.shared.platform.getEnv
import io.ktor.client.HttpClient

internal object RealApiTestProviders {

    fun create(client: HttpClient, probe: CountryStationProbe): PoiProvider? = when (probe.fuelProvider) {
        PoiProviderType.DataGouv -> DataGouvProvider(client, radiusKm = 10, limit = 100)
        PoiProviderType.UkCma -> UkCmaFuelProvider(client, radiusKm = 15, limit = 200)
        PoiProviderType.ItalyMimit -> MimitFuelProvider(client, radiusKm = 15, limit = 200)
        PoiProviderType.SloveniaGorivaSi -> GorivaSiProvider(client, radiusKm = 15, limit = 200)
        PoiProviderType.NorwayDrivstoffAppen -> DrivstoffAppenProvider(
            client, country = "Norway", countryIso2 = "NO", radiusKm = 20, limit = 100,
        )
        PoiProviderType.SwedenDrivstoffAppen -> DrivstoffAppenProvider(
            client, country = "Sweden", countryIso2 = "SE", radiusKm = 20, limit = 100,
        )
        PoiProviderType.PortugalDgeg -> PortugalDgegProvider(client)
        PoiProviderType.NetherlandsAnwb -> NetherlandsAnwbProvider(client, radiusKm = 20, limit = 80)
        PoiProviderType.DenmarkFuelpricesDk -> {
            val key = getEnv("FUELPRICES_DK_KEY").orEmpty()
            if (key.isBlank()) null else FuelpricesDKProvider(client, apiKey = key, radiusKm = 20, limit = 80)
        }
        PoiProviderType.CroatiaMzoe -> CroatiaMzoeProvider(client, radiusKm = 40, limit = 80)
        PoiProviderType.FinlandPolttoaine -> PolttoaineProvider(client, limit = 40)
        PoiProviderType.GreeceFuelGr -> GreeceFuelGRProvider(client, limit = 60)
        PoiProviderType.IrelandPickAPump -> IrelandPickAPumpProvider(client, radiusKm = 20, limit = 80)
        PoiProviderType.MoldovaAnre -> MoldovaAnreProvider(client, radiusKm = 20, limit = 80)
        PoiProviderType.RomaniaPeco -> {
            val appId = getEnv("ROMANIA_PECO_APPLICATION_ID").orEmpty()
            val clientKey = getEnv("ROMANIA_PECO_CLIENT_KEY").orEmpty()
            if (appId.isBlank() || clientKey.isBlank()) null
            else RomaniaPecoProvider(client, applicationId = appId, clientKey = clientKey, radiusKm = 20, limit = 80)
        }
        PoiProviderType.SerbiaNis -> SerbiaNisProvider(client, radiusKm = 20, limit = 80)
        PoiProviderType.MexicoCre -> MexicoCREProvider(client, radiusKm = 20, limit = 80)
        PoiProviderType.ArgentinaEnergia -> ArgentinaEnergiaProvider(client, radiusKm = 20, limit = 80)
        PoiProviderType.SpainMinetur -> SpainMineturProvider(client, radiusKm = 100)
        PoiProviderType.GermanyTankerkoenig -> {
            val key = getEnv("GERMANY_TANKERKOENIG_KEY").orEmpty()
            if (key.isBlank()) null else GermanyTankerkoenigProvider(client, apiKey = key)
        }
        PoiProviderType.AustriaEControl -> AustriaEControlProvider(client)
        PoiProviderType.BelgiumOfficial -> BelgiumOfficialProvider(
            belgiumClient = BelgiumPetrolPricesClient(client),
            overpassClient = OverpassClient(client),
            radiusKm = 10,
            limit = 100,
        )
        PoiProviderType.OpenVanCamp -> OpenVanCampProvider(
            openVanClient = OpenVanCampClient(client),
            overpassClient = OverpassClient(client),
            radiusKm = 10,
            limit = 100,
        )
        else -> null
    }
}
