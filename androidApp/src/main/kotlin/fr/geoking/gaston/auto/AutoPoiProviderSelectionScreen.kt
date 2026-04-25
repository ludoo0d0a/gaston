package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.isUserSelectablePoiDataSource

class AutoPoiProviderSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val options = listOf(
        PoiProviderType.Routex to "Routex",
        PoiProviderType.Etalab to "Prix carburant (France official)",
        PoiProviderType.GasApi to "gas-api.ovh",
        PoiProviderType.DataGouv to "data.gouv (France official)",
        PoiProviderType.DataGouvElec to "data.gouv.fr (EV)",
        PoiProviderType.OpenChargeMap to "Open Charge Map",
        PoiProviderType.Chargy to "Chargy (Luxembourg)",
        PoiProviderType.OpenVanCamp to "OpenVan.camp (LU, HR, SI...)",
        PoiProviderType.SpainMinetur to "Spain Minetur (official)",
        PoiProviderType.GermanyTankerkoenig to "Tankerkönig (Germany)",
        PoiProviderType.AustriaEControl to "E-Control (Austria)",
        PoiProviderType.BelgiumOfficial to "Belgium (official)",
        PoiProviderType.Overpass to "Overpass",
        PoiProviderType.Hybrid to "Hybrid (Gas + EV)"
    ).filter { (type, _) -> type.isUserSelectablePoiDataSource() }

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        options.forEach { (type, label) ->
            val isSelected = settings.selectedPoiProviders.contains(type)
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.togglePoiProviderType(type)
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Data Source").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
