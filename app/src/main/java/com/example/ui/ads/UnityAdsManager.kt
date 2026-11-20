package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.AdConfigEntity
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production Unity Ads SDK v4.14.1 Manager (ONLINE ONLY).
 * - Strictly disabled when offline (zero initialization, zero ad requests).
 * - Initializes once per (gameId, testMode) — never repeatedly on page navigation.
 * - Enforces frequency caps and non-duplicate placement loading.
 */
object UnityAdsManager {
    private const val TAG = "UnityAdsManager"

    private var initializedGameId: String? = null
    private var initializedTestMode: Boolean? = null
    private var isInitializing = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isInterstitialLoaded = MutableStateFlow(false)
    val isInterstitialLoaded: StateFlow<Boolean> = _isInterstitialLoaded.asStateFlow()

    private val _isRewardedLoaded = MutableStateFlow(false)
    val isRewardedLoaded: StateFlow<Boolean> = _isRewardedLoaded.asStateFlow()

    private var isInterstitialLoading = false
    private var isRewardedLoading = false
    private var lastInterstitialShownAt = 0L

    fun isUnityEnabled(config: AdConfigEntity): Boolean {
        if (!config.adsEnabled) return false
        if (config.unityGameId.isBlank()) return false
        return when (config.activeNetwork.uppercase()) {
            "UNITY", "BOTH_UNITY_PRIMARY", "BOTH_STARTIO_PRIMARY" -> true
            else -> false
        }
    }

    fun ensureInitialized(context: Context, config: AdConfigEntity) {
        if (!ConnectivityMonitor.isOnline.value) return
        if (!isUnityEnabled(config)) return
        val gameId = config.unityGameId.trim()
        if (initializedGameId == gameId && initializedTestMode == config.testMode && (_isReady.value || isInitializing)) {
            return
        }
        isInitializing = true
        initializedGameId = gameId
        initializedTestMode = config.testMode

        runCatching {
            UnityAds.initialize(
                context.applicationContext,
                gameId,
                config.testMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isInitializing = false
                        _isReady.value = true
                        Log.d(TAG, "Unity Ads initialized (gameId=$gameId, testMode=${config.testMode})")
                        preloadPlacements(config)
                    }

                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                        isInitializing = false
                        _isReady.value = false
                        Log.w(TAG, "Unity Ads init failed: $error - $message")
                    }
                }
            )
        }.onFailure { err ->
            isInitializing = false
            _isReady.value = false
            Log.w(TAG, "Unity Ads init exception: ${err.message}")
        }
    }

    fun preloadPlacements(config: AdConfigEntity) {
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || !isUnityEnabled(config)) return
        if (config.appOpenEnabled) {
            preloadInterstitial(config.unityAppOpenPlacement)
        }
        if (config.rewardedEnabled) {
            preloadRewarded(config.unityRewardedPlacement)
        }
    }

    fun preloadInterstitial(placementId: String) {
        val pid = placementId.trim()
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || pid.isEmpty()) return
        if (_isInterstitialLoaded.value || isInterstitialLoading) return
        isInterstitialLoading = true
        AdRevenueTracker.trackNetworkRequest("unity", "app_open")
        runCatching {
            UnityAds.load(pid, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(id: String?) {
                    isInterstitialLoading = false
                    _isInterstitialLoaded.value = true
                    AdRevenueTracker.trackNetworkLoad("unity", "app_open")
                }

                override fun onUnityAdsFailedToLoad(id: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                    isInterstitialLoading = false
                    _isInterstitialLoaded.value = false
                    AdRevenueTracker.trackNetworkError("unity", "app_open")
                    Log.d(TAG, "Unity interstitial load failed ($pid): $error - $message")
                }
            })
        }.onFailure {
            isInterstitialLoading = false
            _isInterstitialLoaded.value = false
        }
    }

    fun preloadRewarded(placementId: String) {
        val pid = placementId.trim()
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || pid.isEmpty()) return
        if (_isRewardedLoaded.value || isRewardedLoading) return
        isRewardedLoading = true
        AdRevenueTracker.trackNetworkRequest("unity", "rewarded")
        runCatching {
            UnityAds.load(pid, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(id: String?) {
                    isRewardedLoading = false
                    _isRewardedLoaded.value = true
                    AdRevenueTracker.trackNetworkLoad("unity", "rewarded")
                }

                override fun onUnityAdsFailedToLoad(id: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                    isRewardedLoading = false
                    _isRewardedLoaded.value = false
                    AdRevenueTracker.trackNetworkError("unity", "rewarded")
                    Log.d(TAG, "Unity rewarded load failed ($pid): $error - $message")
                }
            })
        }.onFailure {
            isRewardedLoading = false
            _isRewardedLoaded.value = false
        }
    }

    fun showInterstitialIfReady(activity: Activity, config: AdConfigEntity): Boolean {
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || !config.appOpenEnabled || !isUnityEnabled(config)) {
            return false
        }
        val now = System.currentTimeMillis()
        val cooldownMs = config.appOpenCooldownSeconds.coerceIn(15, 3600) * 1000L
        if (now - lastInterstitialShownAt < cooldownMs) return false
        if (!_isInterstitialLoaded.value) {
            preloadInterstitial(config.unityAppOpenPlacement)
            return false
        }
        val pid = config.unityAppOpenPlacement.trim()
        if (pid.isEmpty()) return false
        _isInterstitialLoaded.value = false
        lastInterstitialShownAt = now

        return runCatching {
            UnityAds.show(activity, pid, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(id: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                    AdRevenueTracker.trackNetworkError("unity", "app_open")
                    preloadInterstitial(pid)
                }

                override fun onUnityAdsShowStart(id: String?) {
                    AdRevenueTracker.trackNetworkImpression("unity", "app_open")
                }

                override fun onUnityAdsShowClick(id: String?) {
                    AdRevenueTracker.trackNetworkClick("unity", "app_open")
                }

                override fun onUnityAdsShowComplete(id: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                    preloadInterstitial(pid)
                }
            })
            true
        }.getOrDefault(false)
    }

    fun showRewardedIfReady(
        activity: Activity,
        config: AdConfigEntity,
        onFinished: (rewardEarned: Boolean) -> Unit
    ): Boolean {
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || !config.rewardedEnabled || !isUnityEnabled(config)) {
            return false
        }
        val pid = config.unityRewardedPlacement.trim()
        if (!_isRewardedLoaded.value || pid.isEmpty()) {
            preloadRewarded(pid)
            return false
        }
        _isRewardedLoaded.value = false
        return runCatching {
            UnityAds.show(activity, pid, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(id: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                    AdRevenueTracker.trackNetworkError("unity", "rewarded")
                    preloadRewarded(pid)
                    onFinished(false)
                }

                override fun onUnityAdsShowStart(id: String?) {
                    AdRevenueTracker.trackNetworkImpression("unity", "rewarded")
                }

                override fun onUnityAdsShowClick(id: String?) {
                    AdRevenueTracker.trackNetworkClick("unity", "rewarded")
                }

                override fun onUnityAdsShowComplete(id: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                    val completed = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
                    if (completed) {
                        AdRevenueTracker.trackRewardedCompleted("unity")
                    }
                    preloadRewarded(pid)
                    onFinished(completed)
                }
            })
            true
        }.getOrDefault(false)
    }
}

@Composable
internal fun UnityBannerViewSlot(
    activity: Activity,
    placementId: String,
    formatKey: String,
    onLoaded: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bannerLoaded by remember(placementId) { mutableStateOf(false) }
    val bannerRef = remember(activity, placementId) {
        BannerView(activity, placementId, UnityBannerSize(320, 50)).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    bannerLoaded = true
                    AdRevenueTracker.trackNetworkLoad("unity", formatKey)
                    onLoaded()
                }

                override fun onBannerShown(bannerAdView: BannerView?) {
                    AdRevenueTracker.trackNetworkImpression("unity", formatKey)
                }

                override fun onBannerClick(bannerAdView: BannerView?) {
                    AdRevenueTracker.trackNetworkClick("unity", formatKey)
                }

                override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
                    bannerLoaded = false
                    AdRevenueTracker.trackNetworkError("unity", formatKey)
                    onFailed()
                }

                override fun onBannerLeftApplication(bannerView: BannerView?) = Unit
            }
        }
    }

    DisposableEffect(bannerRef) {
        if (ConnectivityMonitor.isOnline.value) {
            AdRevenueTracker.trackNetworkRequest("unity", formatKey)
            runCatching { bannerRef.load() }.onFailure { onFailed() }
        } else {
            onFailed()
        }
        onDispose {
            runCatching { bannerRef.destroy() }
        }
    }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                (bannerRef.parent as? ViewGroup)?.removeView(bannerRef)
                addView(bannerRef)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(if (bannerLoaded) 52.dp else 1.dp)
    )
}
