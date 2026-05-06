package fr.geoking.gaston.shared.platform

internal actual fun getEnv(name: String): String? = System.getenv(name)

