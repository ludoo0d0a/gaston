package fr.geoking.gaston.shared.platform

internal expect fun getEnv(name: String): String?

internal expect fun getSystemLanguage(): String
