package fr.geoking.gaston.shared.platform

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual fun getEnv(name: String): String? = null

internal actual fun getSystemLanguage(): String {
    val lang = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
    return lang.split("-").first().split("_").first().lowercase()
}
