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
 * Production connectivity monitor that verifies ACTUAL internet accessibility
 * (not just Wi-Fi / Mobile transport attachment) via ConnectivityManager callbacks
 * paired with a lightweight HTTP 204 reachability check.
 */
object ConnectivityMonitor {
    private const val TAG = "ConnectivityMonitor"
    private const val PROBE_URL_PRIMARY = "https://connectivitycheck.gstatic.com/generate_204"
    private const val PROBE_URL_FALLBACK = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app/directory/settings/siteTitle.json"

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
        val cm = try {
            appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        } catch (t: Throwable) {
            Log.w(TAG, "ConnectivityService unavailable: ${t.message}")
            null
        }
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
                        verifyInternetAccessAsync(cm, triggerReconnect = true, onReconnect = onReconnect)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    runCatching {
                        val hasNet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        if (!hasNet) {
                            setOnlineState(false)
                        } else if (!_isOnline.value) {
                            verifyInternetAccessAsync(cm, triggerReconnect = true, onReconnect = onReconnect)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    runCatching {
                        if (!hasValidatedTransport(cm)) {
                            setOnlineState(false)
                        } else {
                            verifyInternetAccessAsync(cm, triggerReconnect = false, onReconnect = onReconnect)
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

    fun refreshNow(context: Context, onReconnect: (() -> Unit)? = null) {
        if (onReconnect != null) {
            reconnectListener = onReconnect
        }
        val appCtx = context.applicationContext ?: context
        val cm = try {
            appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        } catch (t: Throwable) {
            Log.w(TAG, "ConnectivityService refresh warning: ${t.message}")
            null
        } ?: return
        verifyInternetAccessAsync(cm, triggerReconnect = true, onReconnect = onReconnect, force = true)
    }

    /**
     * Allows deterministic state injection in unit/Robolectric tests without live sockets.
     */
    internal fun setOnlineForTesting(online: Boolean) {
        _isOnline.value = online
        _hasVerifiedOnce.value = true
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
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            hasInternet && hasTransport
        } catch (t: Throwable) {
            Log.w(TAG, "Transport check warning: ${t.message}")
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
                    probeActualInternet()
                }
                if (!reachable && hasTransport) {
                    kotlinx.coroutines.delay(4000L)
                    if (hasValidatedTransport(cm)) {
                        reachable = probeActualInternet()
                    }
                }
                setOnlineState(reachable)
                Log.d(TAG, "Verified internet reachability: online=$reachable (wasOnline=$wasOnline)")
                if (reachable && (!wasOnline || force) && triggerReconnect) {
                    val listener = onReconnect ?: reconnectListener
                    runCatching { listener?.invoke() }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Connectivity async probe warning: ${t.message}")
            }
        }
    }

    suspend fun probeActualInternet(): Boolean = withContext(Dispatchers.IO) {
        probeUrl(PROBE_URL_PRIMARY) || probeUrl(PROBE_URL_FALLBACK)
    }

    private fun probeUrl(target: String): Boolean {
        return try {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = false
                useCaches = false
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Throwable) {
            false
        }
    }
}
