package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GoogleUserAccount(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String
)

/**
 * Production Firebase Authentication + Jetpack Credential Manager Google Sign-In Controller.
 *
 * Enforces:
 * - Guaranteed FirebaseApp initialization before FirebaseAuth calls.
 * - Exclusive Google Sign-In (`GetSignInWithGoogleOption` primary + `GetGoogleIdOption`
 *   fallback on unsupported/no-credential devices; `GetGoogleIdOption` for silent startup).
 * - Exactly ONE credential option per `GetCredentialRequest` (API 36 bottom-sheet responsiveness).
 * - Clean main-thread callback dispatch and actionable error messages for all Credential Manager
 *   and FirebaseAuth error cases.
 * - `NonCancellable` sign-out cleanup clearing Credential Manager before `Firebase.auth.signOut()`.
 */
object GoogleAuthManager {
    private const val TAG = "GoogleAuthManager"

    private val _currentUser = MutableStateFlow<GoogleUserAccount?>(null)
    val currentUser: StateFlow<GoogleUserAccount?> = _currentUser.asStateFlow()

    @Volatile
    private var listenerAttached = false

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        runCatching {
            _currentUser.value = auth.currentUser?.toGoogleUserAccount()
        }
    }

    private fun ensureFirebaseInitialized(context: Context?): Boolean {
        return try {
            if (context != null && FirebaseApp.getApps(context.applicationContext ?: context).isEmpty()) {
                FirebaseApp.initializeApp(context.applicationContext ?: context)
            }
            FirebaseApp.getApps(context?.applicationContext ?: return true).isNotEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseApp initialization warning: ${t.message}")
            false
        }
    }

    private fun resolveWebClientId(context: Context): String {
        return try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (t: Throwable) {
            Log.e(TAG, "Missing R.string.default_web_client_id (verify app/google-services.json)", t)
            ""
        }
    }

    fun startObserving(context: Context? = null) {
        if (context != null) {
            ensureFirebaseInitialized(context)
        }
        if (listenerAttached) return
        synchronized(this) {
            if (listenerAttached) return
            try {
                val auth = Firebase.auth
                _currentUser.value = auth.currentUser?.toGoogleUserAccount()
                auth.addAuthStateListener(authStateListener)
                listenerAttached = true
            } catch (t: Throwable) {
                Log.w(TAG, "FirebaseAuth observer initialization warning: ${t.message}")
            }
        }
    }

    private fun FirebaseUser.toGoogleUserAccount(): GoogleUserAccount = GoogleUserAccount(
        uid = uid,
        displayName = displayName?.trim().orEmpty().ifBlank {
            email?.substringBefore("@").orEmpty().ifBlank { "Doctor List User" }
        },
        email = email?.trim().orEmpty(),
        photoUrl = photoUrl?.toString().orEmpty()
    )

    suspend fun getIdToken(forceRefresh: Boolean = false): String? {
        return try {
            Firebase.auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to fetch Firebase ID token: ${t.message}")
            null
        }
    }

    /**
     * Silent auto sign-in on app startup using `GetGoogleIdOption` ONLY (single option per request).
     * Requires an Activity context for Credential Manager on Android 14+; safely restores any
     * existing FirebaseAuth session first and never blocks browsing if unauthenticated.
     */
    fun attemptAutoSignIn(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: (GoogleUserAccount) -> Unit = {},
        onUnauthenticated: () -> Unit = {}
    ) {
        startObserving(context)
        val existing = try {
            Firebase.auth.currentUser
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase.auth unavailable on startup: ${t.message}")
            null
        }
        if (existing != null) {
            val account = existing.toGoogleUserAccount()
            _currentUser.value = account
            onAuthSuccess(account)
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            onUnauthenticated()
            return
        }
        val clientId = resolveWebClientId(activity)
        if (clientId.isBlank()) {
            onUnauthenticated()
            return
        }
        val credentialManager = try {
            CredentialManager.create(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "CredentialManager creation skipped: ${t.message}")
            onUnauthenticated()
            return
        }
        val request = try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "Credential request build skipped: ${t.message}")
            onUnauthenticated()
            return
        }
        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                val account = exchangeCredentialWithFirebase(cred)
                withContext(Dispatchers.Main.immediate) {
                    if (account != null) {
                        _currentUser.value = account
                        onAuthSuccess(account)
                    } else {
                        onUnauthenticated()
                    }
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    onUnauthenticated()
                }
            }
        }
    }

    /**
     * Interactive "Continue with Google" flow using `GetSignInWithGoogleOption` (primary) with
     * automatic fallback to a single-option `GetGoogleIdOption(filterByAuthorizedAccounts = false)`
     * if the device Credential Provider returns unsupported/no-credential.
     */
    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (GoogleUserAccount) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        startObserving(activity)
        val clientId = resolveWebClientId(activity)
        if (clientId.isBlank()) {
            onError("Google Sign-In config missing (default_web_client_id). Check app/google-services.json.")
            return
        }
        val credentialManager = try {
            CredentialManager.create(activity)
        } catch (t: Throwable) {
            Log.e(TAG, "CredentialManager unavailable", t)
            onError(t.localizedMessage ?: "Google Sign-In is unavailable on this device.")
            return
        }

        val primaryRequest = try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
        } catch (t: Throwable) {
            onError(t.localizedMessage ?: "Could not initialize Google Sign-In request.")
            return
        }

        scope.launch {
            try {
                val credential = try {
                    credentialManager.getCredential(activity, primaryRequest).credential
                } catch (e: GetCredentialCancellationException) {
                    throw e
                } catch (primaryErr: GetCredentialException) {
                    if (shouldRetryWithGoogleIdOption(primaryErr)) {
                        Log.w(TAG, "Primary SignInWithGoogleOption failed (${primaryErr.type}); retrying with GetGoogleIdOption")
                        val fallbackOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(clientId)
                            .setAutoSelectEnabled(false)
                            .build()
                        val fallbackRequest = GetCredentialRequest.Builder()
                            .addCredentialOption(fallbackOption)
                            .build()
                        credentialManager.getCredential(activity, fallbackRequest).credential
                    } else {
                        throw primaryErr
                    }
                }

                val account = exchangeCredentialWithFirebase(credential)
                withContext(Dispatchers.Main.immediate) {
                    if (account != null) {
                        _currentUser.value = account
                        onSuccess(account)
                    } else {
                        onError("Could not complete Google Sign-In. Please try again.")
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled by user: ${e.message}")
                withContext(Dispatchers.Main.immediate) { onCancelled() }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main.immediate) { onCancelled() }
            } catch (e: NoCredentialException) {
                Log.e(TAG, "No Google credentials available", e)
                withContext(Dispatchers.Main.immediate) {
                    onError("No Google account available. Please sign into a Google account in device Settings and ensure this app's SHA-1 is registered in Firebase.")
                }
            } catch (e: GetCredentialProviderConfigurationException) {
                Log.e(TAG, "Credential provider configuration error", e)
                withContext(Dispatchers.Main.immediate) {
                    onError("Google Play Services is unavailable or needs updating for Google Sign-In.")
                }
            } catch (e: GetCredentialInterruptedException) {
                Log.w(TAG, "Google Sign-In interrupted", e)
                withContext(Dispatchers.Main.immediate) {
                    onError("Google Sign-In was interrupted. Please tap Continue with Google again.")
                }
            } catch (e: FirebaseNetworkException) {
                Log.e(TAG, "Firebase network error during Google Sign-In", e)
                withContext(Dispatchers.Main.immediate) {
                    onError("Network error while signing in. Check your internet connection and try again.")
                }
            } catch (e: FirebaseAuthException) {
                Log.e(TAG, "FirebaseAuth sign-in error (${e.errorCode})", e)
                withContext(Dispatchers.Main.immediate) {
                    onError("Firebase Sign-In failed (${e.errorCode}): ${e.localizedMessage ?: "Please try again."}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Google Sign-In failed", t)
                val friendly = formatFriendlySignInError(t)
                withContext(Dispatchers.Main.immediate) {
                    onError(friendly)
                }
            }
        }
    }

    private fun shouldRetryWithGoogleIdOption(err: GetCredentialException): Boolean {
        return err is NoCredentialException ||
            err is GetCredentialUnsupportedException ||
            err is GetCredentialCustomException ||
            err.message?.contains("Cannot find a matching credential", ignoreCase = true) == true
    }

    private suspend fun exchangeCredentialWithFirebase(cred: androidx.credentials.Credential): GoogleUserAccount? {
        if (cred !is CustomCredential || cred.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return null
        }
        val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
        if (idToken.isBlank()) return null
        val result = Firebase.auth.signInWithCredential(
            GoogleAuthProvider.getCredential(idToken, null)
        ).await()
        return result.user?.toGoogleUserAccount()
    }

    private fun formatFriendlySignInError(t: Throwable): String {
        val raw = t.localizedMessage.orEmpty()
        return when {
            raw.contains("16:", ignoreCase = true) || raw.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                raw.contains("reauth failed", ignoreCase = true) ->
                "Google Sign-In configuration mismatch (check SHA-1 fingerprint & OAuth client in Firebase Console)."
            raw.contains("network", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "Network error during Google Sign-In. Please check your internet connection."
            raw.isNotBlank() -> raw
            else -> "Google Sign-In failed. Please try again."
        }
    }

    /**
     * Secure logout clearing Credential Manager state before Firebase Auth sign-out inside `NonCancellable`.
     */
    fun signOut(
        context: Context,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        val credentialManager = runCatching { CredentialManager.create(context) }.getOrNull()
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager?.clearCredentialState(ClearCredentialStateRequest())
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to clear credential state: ${t.message}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    _currentUser.value = null
                    withContext(Dispatchers.Main.immediate) {
                        onComplete()
                    }
                }
            }
        }
    }
}
