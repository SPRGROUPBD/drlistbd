package com.example.ui.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Production connectivity monitor that tracks real internet reachability via
 * ConnectivityManager callbacks paired with lightweight HTTP reachability checks.
 *
 * Reconnect callbacks ONLY fire on an actual offline -> online transition so
 * manual syncs and foreground resumes never cause infinite sync loops.
 */
object ConnectivityMonitor {
    private const val TAG = "ConnectivityMonitor"
    private const val PROBE_URL_PRIMARY = "https://connectivitycheck.gstatic.com/generate_204"
    private const val PROBE_URL_FALLBACK =
        "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app/directory/settings/siteTitle.json"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "Connectivity probe handled error: ${throwable.message}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _hasVerifiedOnce = MutableStateFlow(false)
    val hasVerifiedOnce: StateFlow<Boolean> = _hasVerifiedOnce.asStateFlow()

    @Volatile
    private var started = false
    @Volatile
    private var lastProbeAtMs = 0L
    @Volatile
    private var reconnectListener: (() -> Unit)? = null

    fun start(context: Context, onReconnect: (() -> Unit)? = null) {
        if (onReconnect != null) {
            reconnectListener = onReconnect
        }
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val appCtx = context.applicationContext ?: context
        val cm = getConnectivityManager(appCtx)
        if (cm == null) {
            _isOnline.value = false
            _hasVerifiedOnce.value = true
            return
        }

        // Fast optimistic transport check, immediately verified by real internet probe
        val initialTransport = hasValidatedTransport(cm)
        _isOnline.value = initialTransport
        verifyInternetAccessAsync(cm, triggerReconnect = false, onReconnect = onReconnect)

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runCatching {
                        verifyInternetAccessAsync(cm, triggerReconnect = true, onReconnect = null)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    runCatching {
                        val hasNet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        if (!hasNet) {
                            setOnlineState(false)
                        } else if (!_isOnline.value) {
                            verifyInternetAccessAsync(cm, triggerReconnect = true, onReconnect = null)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    runCatching {
                        if (!hasValidatedTransport(cm)) {
                            setOnlineState(false)
                        } else {
                            verifyInternetAccessAsync(cm, triggerReconnect = false, onReconnect = null)
                        }
                    }
                }

                override fun onUnavailable() {
                    setOnlineState(false)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "NetworkCallback registration warning: ${t.message}")
        }
    }

    /**
     * Asynchronously refreshes connectivity state. Only invokes reconnectListener
     * if [triggerReconnect] is true AND the device genuinely transitioned from offline -> online.
     */
    fun refreshNow(
        context: Context,
        onReconnect: (() -> Unit)? = null,
        triggerReconnect: Boolean = true
    ) {
        if (onReconnect != null) {
            reconnectListener = onReconnect
        }
        val appCtx = context.applicationContext ?: context
        val cm = getConnectivityManager(appCtx) ?: return
        verifyInternetAccessAsync(
            cm = cm,
            triggerReconnect = triggerReconnect,
            onReconnect = onReconnect,
            force = true
        )
    }

    /**
     * Suspending reachability check used directly by MainViewModel.performFirebaseSync so
     * sync steps have an up-to-date online status without triggering recursive reconnect callbacks.
     */
    suspend fun verifyOnlineNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext ?: context
        val cm = getConnectivityManager(appCtx)
        if (cm == null) {
            setOnlineState(false)
            return@withContext false
        }
        val hasTransport = hasValidatedTransport(cm)
        if (!hasTransport) {
            setOnlineState(false)
            return@withContext false
        }
        val reachable = probeActualInternet() || hasSystemValidatedInternet(cm)
        setOnlineState(reachable)
        lastProbeAtMs = System.currentTimeMillis()
        reachable
    }

    /**
     * Marks connectivity as confirmed online after a real Firebase/HTTP call succeeds,
     * preventing false offline states on networks that block Google's 204 probe endpoint.
     */
    fun markOnlineConfirmed() {
        setOnlineState(true)
        lastProbeAtMs = System.currentTimeMillis()
    }

    /**
     * Allows deterministic state injection in unit/Robolectric tests without live sockets.
     */
    internal fun setOnlineForTesting(online: Boolean) {
        _isOnline.value = online
        _hasVerifiedOnce.value = true
    }

    private fun getConnectivityManager(context: Context): ConnectivityManager? = try {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (t: Throwable) {
        Log.w(TAG, "ConnectivityService unavailable: ${t.message}")
        null
    }

    private fun setOnlineState(online: Boolean) {
        _isOnline.value = online
        _hasVerifiedOnce.value = true
    }

    private fun hasValidatedTransport(cm: ConnectivityManager): Boolean {
        return try {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            hasInternet && hasTransport
        } catch (t: Throwable) {
            Log.w(TAG, "Transport check warning: ${t.message}")
            false
        }
    }

    private fun hasSystemValidatedInternet(cm: ConnectivityManager): Boolean {
        return try {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Throwable) {
            false
        }
    }

    private fun verifyInternetAccessAsync(
        cm: ConnectivityManager,
        triggerReconnect: Boolean,
        onReconnect: (() -> Unit)?,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastProbeAtMs) < 2500L) return
        lastProbeAtMs = now

        scope.launch {
            try {
                val wasOnline = _isOnline.value
                val hasTransport = hasValidatedTransport(cm)
                var reachable = if (!hasTransport) {
                    false
                } else {
                    probeActualInternet() || hasSystemValidatedInternet(cm)
                }
                if (!reachable && hasTransport) {
                    kotlinx.coroutines.delay(2500L)
                    if (hasValidatedTransport(cm)) {
                        reachable = probeActualInternet() || hasSystemValidatedInternet(cm)
                    }
                }
                setOnlineState(reachable)
                Log.d(TAG, "Verified internet reachability: online=$reachable (wasOnline=$wasOnline)")
                // Fire reconnect callback strictly on offline -> online transitions (prevents infinite sync loops)
                if (reachable && !wasOnline && triggerReconnect) {
                    val listener = onReconnect ?: reconnectListener
                    runCatching { listener?.invoke() }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Connectivity async probe warning: ${t.message}")
            }
        }
    }

    suspend fun probeActualInternet(): Boolean = withContext(Dispatchers.IO) {
        probeUrl(PROBE_URL_PRIMARY, method = "HEAD") || probeUrl(PROBE_URL_FALLBACK, method = "GET")
    }

    private fun probeUrl(target: String, method: String = "HEAD"): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 3000
                readTimeout = 3000
                instanceFollowRedirects = false
                useCaches = false
            }
            val code = conn.responseCode
            code in 200..399
        } catch (_: Throwable) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
