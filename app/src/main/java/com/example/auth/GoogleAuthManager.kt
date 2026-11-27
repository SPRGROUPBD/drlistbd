package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
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
 * - Exclusive Google Sign-In (`GetSignInWithGoogleOption` for interactive, `GetGoogleIdOption` for silent startup).
 * - Exactly ONE credential option per `GetCredentialRequest` (API 36 bottom-sheet responsiveness).
 * - Explicit `GetCredentialCancellationException` handling with diagnostic logging.
 * - `NonCancellable` sign-out cleanup clearing Credential Manager before `Firebase.auth.signOut()`.
 */
object GoogleAuthManager {
    private const val TAG = "Auth"

    private val _currentUser = MutableStateFlow<GoogleUserAccount?>(null)
    val currentUser: StateFlow<GoogleUserAccount?> = _currentUser.asStateFlow()

    @Volatile
    private var listenerAttached = false

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        runCatching {
            _currentUser.value = auth.currentUser?.toGoogleUserAccount()
        }
    }

    fun startObserving() {
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
     * Never blocks browsing if no previously authorized account exists.
     */
    fun attemptAutoSignIn(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: (GoogleUserAccount) -> Unit = {},
        onUnauthenticated: () -> Unit = {}
    ) {
        startObserving()
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
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Throwable) {
            onUnauthenticated()
            return
        }
        if (clientId.isBlank()) {
            onUnauthenticated()
            return
        }
        val credentialManager = try {
            CredentialManager.create(context)
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
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val result = Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    val account = result.user?.toGoogleUserAccount()
                    if (account != null) {
                        _currentUser.value = account
                        onAuthSuccess(account)
                    } else {
                        onUnauthenticated()
                    }
                } else {
                    onUnauthenticated()
                }
            } catch (_: Throwable) {
                onUnauthenticated()
            }
        }
    }

    /**
     * Interactive "Continue with Google" flow using `GetSignInWithGoogleOption` ONLY (single option per request).
     */
    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (GoogleUserAccount) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        startObserving()
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (_: Throwable) {
            onError("Google Sign-In configuration missing: default_web_client_id not found.")
            return
        }
        if (clientId.isBlank()) {
            onError("Google Sign-In configuration missing: default_web_client_id is empty.")
            return
        }
        val credentialManager = try {
            CredentialManager.create(activity)
        } catch (t: Throwable) {
            Log.e(TAG, "CredentialManager unavailable", t)
            onError(t.localizedMessage ?: "Google Sign-In is unavailable on this device.")
            return
        }
        val request = try {
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
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val result = Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    val account = result.user?.toGoogleUserAccount()
                    if (account != null) {
                        _currentUser.value = account
                        onSuccess(account)
                    } else {
                        onError("Could not retrieve Google user account.")
                    }
                } else {
                    onError("Unexpected credential type received from Google Sign-In.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onCancelled()
            } catch (t: Throwable) {
                Log.e(TAG, "Google Sign-In failed", t)
                onError(t.localizedMessage ?: "Google Sign-In failed. Please try again.")
            }
        }
    }

    /**
     * Secure logout removing device FCM token, clearing Credential Manager state, and signing out of Firebase Auth inside `NonCancellable`.
     */
    fun signOut(
        context: Context,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        val credentialManager = runCatching { CredentialManager.create(context) }.getOrNull()
        val activeUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull() ?: _currentUser.value?.uid
        scope.launch {
            withContext(NonCancellable) {
                try {
                    com.example.notifications.PushNotificationManager
                        .unregisterCurrentDeviceTokenOnLogout(context.applicationContext, activeUid)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to unregister FCM token on logout: ${t.message}")
                }
                try {
                    credentialManager?.clearCredentialState(ClearCredentialStateRequest())
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to clear credential state: ${t.message}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    _currentUser.value = null
                    onComplete()
                }
            }
        }
    }
}
