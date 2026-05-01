package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.BuildConfig

class AutoAboutScreen(
    carContext: CarContext
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoAboutScreen") {
        val apis = listOf(
            "OSRM — project-osrm.org",
            "Overpass API (OpenStreetMap) — wiki.openstreetmap.org/wiki/Overpass_API",
            "Open Charge Map — openchargemap.org",
            "data.gouv.fr — data.gouv.fr",
            "ODRE (bornes IRVE) — odre.opendatasoft.com",
            "Gas API (prix carburants) — gas-api.ovh",
            "OpenVan.camp — openvan.camp (CC BY 4.0 attribution required)",
            "data.economie.gouv.fr — data.economie.gouv.fr",
            "Routex / Wigeogis — wigeogis.com",
            "Belib (Paris EV) — opendata.paris.fr",
            "Hérault Data (camping-car) — herault-data.fr",
            "CITA (trafic Luxembourg) — cita.lu",
            "OpenTollData — github.com/louis2038/OpenTollData",
        )

        val body = buildString {
            appendLine("Gaston")
            appendLine()
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build date: ${BuildConfig.BUILD_DATE}")
            appendLine()
            appendLine("Used APIs & services:")
            apis.forEach { appendLine("- $it") }
        }.trim().take(5000)

        LongMessageTemplate.Builder(body)
            .setTitle("About")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

