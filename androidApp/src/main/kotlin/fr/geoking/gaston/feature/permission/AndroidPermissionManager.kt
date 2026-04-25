package fr.geoking.gaston.feature.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import fr.geoking.gaston.shared.platform.PermissionManager
import kotlinx.coroutines.CompletableDeferred

class AndroidPermissionManager(
    private val context: Context
) : PermissionManager {

    private var onPermissionRequest: ((String, CompletableDeferred<Boolean>) -> Unit)? = null

    fun setOnPermissionRequest(callback: (String, CompletableDeferred<Boolean>) -> Unit) {
        onPermissionRequest = callback
    }

    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun requestPermission(permission: String): Boolean {
        if (hasPermission(permission)) return true

        val deferred = CompletableDeferred<Boolean>()
        onPermissionRequest?.invoke(permission, deferred) ?: deferred.complete(false)
        return deferred.await()
    }
}
