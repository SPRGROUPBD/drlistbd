package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AdConfigEntity
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RewardedAdOutcome {
    COMPLETED,
    SKIPPED,
    FAILED_TO_SHOW
}

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

    fun isRewardedRequestInFlight(): Boolean = isRewardedLoading || isInitializing

    fun isUnityEnabled(config: AdConfigEntity): Boolean {
        if (!config.adsEnabled) return false
        if (config.unityGameId.isBlank()) return false
        return when (config.activeNetwork.uppercase()) {
            "UNITY", "BOTH_UNITY_PRIMARY", "BOTH_STARTIO_PRIMARY" -> true
            else -> false
        }
    }

    internal fun effectiveTestMode(config: AdConfigEntity): Boolean = config.testMode

    fun ensureInitialized(context: Context, config: AdConfigEntity) {
        if (!ConnectivityMonitor.isOnline.value) return
        if (!isUnityEnabled(config)) return
        val gameId = config.unityGameId.trim()
        val useTestMode = effectiveTestMode(config)
        if (initializedGameId == gameId && initializedTestMode == useTestMode && (_isReady.value || isInitializing)) {
            return
        }
        if (UnityAds.isInitialized && initializedGameId == gameId && initializedTestMode == useTestMode) {
            isInitializing = false
            _isReady.value = true
            preloadPlacements(config)
            return
        }
        isInitializing = true
        initializedGameId = gameId
        initializedTestMode = useTestMode

        runCatching {
            UnityAds.initialize(
                context.applicationContext,
                gameId,
                useTestMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isInitializing = false
                        _isReady.value = true
                        Log.d(TAG, "Unity Ads initialized (gameId=$gameId, testMode=$useTestMode)")
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
        onFinished: (RewardedAdOutcome) -> Unit
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
                    onFinished(RewardedAdOutcome.FAILED_TO_SHOW)
                }

                override fun onUnityAdsShowStart(id: String?) {
                    AdRevenueTracker.trackNetworkImpression("unity", "rewarded")
                }

                override fun onUnityAdsShowClick(id: String?) {
                    AdRevenueTracker.trackNetworkClick("unity", "rewarded")
                }

                override fun onUnityAdsShowComplete(id: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                    val outcome = if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        AdRevenueTracker.trackRewardedCompleted("unity")
                        RewardedAdOutcome.COMPLETED
                    } else {
                        RewardedAdOutcome.SKIPPED
                    }
                    preloadRewarded(pid)
                    onFinished(outcome)
                }
            })
            true
        }.getOrDefault(false)
    }
}

@Composable
internal fun UnityBannerViewSlot(
    activity: Activity,
    adConfig: AdConfigEntity,
    placementId: String,
    formatKey: String,
    onLoaded: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnityReady by UnityAdsManager.isReady.collectAsStateWithLifecycle()
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnFailed by rememberUpdatedState(onFailed)
    var bannerLoaded by remember(placementId) { mutableStateOf(false) }
    var loadRequested by remember(placementId) { mutableStateOf(false) }
    var failedReported by remember(placementId) { mutableStateOf(false) }

    val bannerRef = remember(activity, placementId) {
        runCatching {
            BannerView(activity, placementId, UnityBannerSize(320, 50)).apply {
                id = R.id.unity_banner_view
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                listener = object : BannerView.IListener {
                    override fun onBannerLoaded(bannerAdView: BannerView?) {
                        bannerLoaded = true
                        AdRevenueTracker.trackNetworkLoad("unity", formatKey)
                        latestOnLoaded()
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
                        if (!failedReported) {
                            failedReported = true
                            latestOnFailed()
                        }
                    }

                    override fun onBannerLeftApplication(bannerView: BannerView?) = Unit
                }
            }
        }.getOrNull()
    }

    LaunchedEffect(adConfig.unityGameId, adConfig.testMode) {
        UnityAdsManager.ensureInitialized(activity, adConfig)
    }

    LaunchedEffect(bannerRef, isUnityReady) {
        if (bannerRef == null) {
            if (!failedReported) {
                failedReported = true
                latestOnFailed()
            }
            return@LaunchedEffect
        }
        if (!ConnectivityMonitor.isOnline.value) {
            if (!failedReported) {
                failedReported = true
                latestOnFailed()
            }
            return@LaunchedEffect
        }
        if (isUnityReady && !loadRequested) {
            loadRequested = true
            AdRevenueTracker.trackNetworkRequest("unity", formatKey)
            runCatching { bannerRef.load() }.onFailure {
                if (!failedReported) {
                    failedReported = true
                    latestOnFailed()
                }
            }
            delay(10_000L)
            if (!bannerLoaded && !failedReported) {
                failedReported = true
                latestOnFailed()
            }
        } else if (!isUnityReady) {
            delay(6500L)
            if (!UnityAdsManager.isReady.value && !bannerLoaded && !failedReported) {
                failedReported = true
                latestOnFailed()
            }
        }
    }

    DisposableEffect(bannerRef) {
        onDispose {
            runCatching { bannerRef?.destroy() }
        }
    }

    if (bannerRef != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
                .clipToBounds()
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        id = R.id.unity_banner_container
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                ensureAncestorViewIds(v)
                            }
                            override fun onViewDetachedFromWindow(v: View) = Unit
                        })
                        (bannerRef.parent as? ViewGroup)?.removeView(bannerRef)
                        addView(bannerRef)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(50.dp)
            )
        }
    }
}
