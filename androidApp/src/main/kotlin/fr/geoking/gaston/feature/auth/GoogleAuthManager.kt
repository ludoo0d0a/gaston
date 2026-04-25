package fr.geoking.gaston.feature.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "GoogleAuth"

class GoogleAuthManager(
    private val appContext: Context,
    private val settingsManager: SettingsManager,
    private val diagnosticStore: DiagnosticStore,
    private val firebaseAuth: FirebaseAuth
) {
    private val credentialManager = CredentialManager.create(appContext)
    private val scope = CoroutineScope(Dispatchers.Main)

    fun signIn(context: Context, onResult: (Boolean, String?) -> Unit) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        val isPlaceholder = clientId.isBlank() || clientId.contains("placeholder", ignoreCase = true)
        Log.d(TAG, "signIn: clientId configured=${!isPlaceholder}, length=${clientId.length}")
        if (isPlaceholder) {
            Log.w(TAG, "signIn: GOOGLE_WEB_CLIENT_ID is missing or placeholder; add it in GitHub secrets and rebuild")
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                Log.d(TAG, "signIn: requesting credential...")
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                val googleIdTokenCredential = try {
                    if (credential is GoogleIdTokenCredential) {
                        credential
                    } else {
                        GoogleIdTokenCredential.createFrom(credential.data)
                    }
                } catch (e: Exception) {
                    null
                }

                if (googleIdTokenCredential != null) {
                    Log.d(TAG, "signIn: got ID token, signing into Firebase...")
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                    try {
                        firebaseAuth.signInWithCredential(firebaseCredential).await()
                        val firebaseUser = firebaseAuth.currentUser

                        val settings = settingsManager.settings.value
                        val firstName = googleIdTokenCredential.givenName ?: googleIdTokenCredential.displayName ?: firebaseUser?.displayName ?: "User"
                        Log.d(TAG, "signIn: Firebase success user=$firstName")

                        settingsManager.saveSettings(settings.copy(
                            googleUserName = firstName,
                            isLoggedIn = true
                        ))

                        settingsManager.triggerPullAndMerge()

                        onResult(true, null)
                    } catch (e: Exception) {
                        val msg = "Firebase Auth failed: ${e.message}"
                        Log.e(TAG, "signIn: $msg", e)
                        diagnosticStore.recordError(null, msg)
                        onResult(false, msg)
                    }
                } else {
                    val msg = "Unexpected credential type: ${credential.javaClass.simpleName}"
                    Log.e(TAG, "signIn: $msg")
                    diagnosticStore.recordError(null, "Google Auth: $msg")
                    onResult(false, msg)
                }
            } catch (e: GetCredentialException) {
                val detail = buildErrorDetail(e)
                Log.e(TAG, "signIn: GetCredentialException $detail", e)
                diagnosticStore.recordError(null, "Google Auth: $detail")
                onResult(false, e.message ?: detail)
            } catch (e: Exception) {
                val detail = buildErrorDetail(e)
                Log.e(TAG, "signIn: Exception $detail", e)
                diagnosticStore.recordError(null, "Google Auth: $detail")
                onResult(false, e.message ?: detail)
            }
        }
    }

    private fun buildErrorDetail(e: Throwable): String {
        val type = e.javaClass.simpleName
        val msg = e.message ?: "no message"
        val cause = e.cause?.let { " cause=${it.javaClass.simpleName}: ${it.message}" } ?: ""
        return "$type: $msg$cause"
    }

    fun signOut(onResult: (Boolean) -> Unit) {
        scope.launch {
            try {
                firebaseAuth.signOut()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                val settings = settingsManager.settings.value
                settingsManager.saveSettings(settings.copy(
                    googleUserName = null,
                    isLoggedIn = false
                ))
                Log.d(TAG, "signOut: success")
                onResult(true)
            } catch (e: Exception) {
                val detail = buildErrorDetail(e)
                Log.e(TAG, "signOut: $detail", e)
                diagnosticStore.recordError(null, "Google Auth sign-out: $detail")
                onResult(false)
            }
        }
    }
}
