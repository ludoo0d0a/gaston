package fr.geoking.gaston.shared.platform

interface PermissionManager {
    fun hasPermission(permission: String): Boolean
    suspend fun requestPermission(permission: String): Boolean
}
