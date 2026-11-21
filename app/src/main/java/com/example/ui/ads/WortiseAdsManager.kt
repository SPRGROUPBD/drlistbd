package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.wortise.ads.AdError
import com.wortise.ads.AdSettings
import com.wortise.ads.AdSize
import com.wortise.ads.RevenueData
import com.wortise.ads.WortiseSdk
import com.wortise.ads.appopen.AppOpenAd
import com.wortise.ads.banner.BannerAd
import com.wortise.ads.interstitial.InterstitialAd
import com.wortise.ads.natives.GoogleNativeAd
import com.wortise.ads.rewarded.RewardedAd
import com.wortise.ads.rewarded.models.Reward
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Production Wortise Android SDK v1.8.0 Manager (ONLINE ONLY).
 *
 * Policy & Reliability Guarantees:
 * - Zero hardcoded private credentials: App ID & AdUnit IDs come strictly from Admin RTDB -> Room.
 * - Strictly disabled in Offline Mode (zero initialization, zero ad requests).
 * - Supports Banner, Native, Interstitial, Rewarded Video, and App Open formats.
 * - Rewards are granted ONLY inside verified `onRewardedCompleted` callbacks.
 * - Prevents duplicate simultaneous requests and applies configurable failure retry cooldowns.
 * - Reports genuine SDK callbacks & `RevenueData` paid events without fabricating metrics.
 */
object WortiseAdsManager {
    private const val TAG = "WortiseAdsManager"

    private var initializedAppId: String? = null
    private var initializedTestMode: Boolean? = null
    private var isInitializing = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isInterstitialLoaded = MutableStateFlow(false)
    val isInterstitialLoaded: StateFlow<Boolean> = _isInterstitialLoaded.asStateFlow()

    private val _isAppOpenLoaded = MutableStateFlow(false)
    val isAppOpenLoaded: StateFlow<Boolean> = _isAppOpenLoaded.asStateFlow()

    private val _isRewardedLoaded = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedLoaded.asStateFlow()

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialFailedAt = 0L

    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenLoading = false
    private var lastAppOpenFailedAt = 0L

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false
    private var lastRewardedFailedAt = 0L

    private val formatFailedAt = mutableMapOf<String, Long>()

    fun isRewardedRequestInFlight(): Boolean = isRewardedLoading || isInitializing

    internal fun retryCooldownMs(config: AdConfigEntity): Long =
        config.adRetryCooldownSeconds.coerceIn(10, 600) * 1000L

    internal fun isFormatInCooldown(format: String, cooldownMs: Long = 60_000L): Boolean {
        val lastFail = synchronized(formatFailedAt) { formatFailedAt[format] ?: 0L }
        return System.currentTimeMillis() - lastFail < cooldownMs
    }

    internal fun markFormatFailed(format: String) {
        synchronized(formatFailedAt) {
            formatFailedAt[format] = System.currentTimeMillis()
        }
    }

    internal fun clearFormatFailure(format: String) {
        synchronized(formatFailedAt) {
            formatFailedAt.remove(format)
        }
    }

    fun isWortiseEnabled(config: AdConfigEntity): Boolean {
        if (!config.adsEnabled || !config.wortiseEnabled) return false
        if (config.wortiseAppId.trim().isEmpty()) return false
        if (config.activeNetwork.equals("SPONSORS_ONLY", ignoreCase = true)) return false
        return true
    }

    fun hasFormatUnit(config: AdConfigEntity, format: OnlineAdFormat): Boolean {
        if (!isWortiseEnabled(config)) return false
        return when (format) {
            OnlineAdFormat.BANNER -> config.wortiseBannerAdUnitId.isNotBlank()
            OnlineAdFormat.NATIVE -> config.wortiseNativeAdUnitId.isNotBlank() || config.wortiseBannerAdUnitId.isNotBlank()
            OnlineAdFormat.INTERSTITIAL -> config.wortiseInterstitialAdUnitId.isNotBlank()
            OnlineAdFormat.REWARDED -> config.wortiseRewardedAdUnitId.isNotBlank()
            OnlineAdFormat.APP_OPEN -> config.wortiseAppOpenAdUnitId.isNotBlank() || config.wortiseInterstitialAdUnitId.isNotBlank()
            OnlineAdFormat.ANY -> config.wortiseBannerAdUnitId.isNotBlank() ||
                config.wortiseNativeAdUnitId.isNotBlank() ||
                config.wortiseInterstitialAdUnitId.isNotBlank() ||
                config.wortiseRewardedAdUnitId.isNotBlank() ||
                config.wortiseAppOpenAdUnitId.isNotBlank()
        }
    }

    internal fun effectiveTestMode(config: AdConfigEntity): Boolean =
        config.testMode || HybridNetworkAdController.isDebugOrEmulator()

    fun ensureInitialized(context: Context, config: AdConfigEntity) {
        if (!ConnectivityMonitor.isOnline.value) return
        if (!isWortiseEnabled(config)) return
        val appId = config.wortiseAppId.trim()
        val useTestMode = effectiveTestMode(config)

        if (initializedAppId == appId && initializedTestMode == useTestMode && (_isReady.value || isInitializing)) {
            return
        }
        if (WortiseSdk.isInitialized && initializedAppId == appId && initializedTestMode == useTestMode) {
            isInitializing = false
            _isReady.value = true
            preloadPlacements(context.applicationContext, config)
            return
        }

        isInitializing = true
        initializedAppId = appId
        initializedTestMode = useTestMode

        runCatching {
            AdSettings.testEnabled = useTestMode
            WortiseSdk.initialize(context.applicationContext, appId) {
                isInitializing = false
                _isReady.value = true
                Log.d(TAG, "Wortise SDK initialized (testMode=$useTestMode)")
                preloadPlacements(context.applicationContext, config)
            }
        }.onFailure { err ->
            isInitializing = false
            _isReady.value = false
            Log.w(TAG, "Wortise SDK init exception: ${err.message}")
        }
    }

    fun preloadPlacements(context: Context, config: AdConfigEntity) {
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || !isWortiseEnabled(config)) return
        if (config.appOpenEnabled && config.wortiseAppOpenAdUnitId.isNotBlank()) {
            preloadAppOpen(context, config)
        }
        if (config.interstitialEnabled && config.wortiseInterstitialAdUnitId.isNotBlank()) {
            preloadInterstitial(context, config)
        }
        if (config.rewardedEnabled && config.wortiseRewardedAdUnitId.isNotBlank()) {
            preloadRewarded(context, config)
        }
    }

    fun preloadInterstitial(context: Context, config: AdConfigEntity) {
        val unitId = config.wortiseInterstitialAdUnitId.trim()
        val now = System.currentTimeMillis()
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || unitId.isEmpty()) return
        if (_isInterstitialLoaded.value || isInterstitialLoading) return
        if (now - lastInterstitialFailedAt < retryCooldownMs(config)) return

        isInterstitialLoading = true
        AdRevenueTracker.trackNetworkRequest("wortise", "interstitial")
        runCatching {
            interstitialAd?.destroy()
            val ad = InterstitialAd(context.applicationContext, unitId)
            ad.listener = object : InterstitialAd.Listener {
                override fun onInterstitialLoaded(ad: InterstitialAd) {
                    isInterstitialLoading = false
                    _isInterstitialLoaded.value = true
                    lastInterstitialFailedAt = 0L
                    AdRevenueTracker.trackNetworkLoad("wortise", "interstitial")
                }

                override fun onInterstitialFailedToLoad(ad: InterstitialAd, error: AdError) {
                    isInterstitialLoading = false
                    _isInterstitialLoaded.value = false
                    lastInterstitialFailedAt = System.currentTimeMillis()
                    AdRevenueTracker.trackNetworkError("wortise", "interstitial")
                    Log.d(TAG, "Wortise interstitial failed to load: $error")
                }

                override fun onInterstitialImpression(ad: InterstitialAd) {
                    AdRevenueTracker.trackNetworkImpression("wortise", "interstitial")
                }

                override fun onInterstitialClicked(ad: InterstitialAd) {
                    AdRevenueTracker.trackNetworkClick("wortise", "interstitial")
                }

                override fun onInterstitialRevenuePaid(ad: InterstitialAd, data: RevenueData) {
                    trackPaidRevenue("interstitial", data)
                }

                override fun onInterstitialFailedToShow(ad: InterstitialAd, error: AdError) {
                    _isInterstitialLoaded.value = false
                    AdRevenueTracker.trackNetworkError("wortise", "interstitial")
                }

                override fun onInterstitialDismissed(ad: InterstitialAd) {
                    _isInterstitialLoaded.value = false
                    preloadInterstitial(context.applicationContext, config)
                }
            }
            interstitialAd = ad
            ad.loadAd()
        }.onFailure {
            isInterstitialLoading = false
            _isInterstitialLoaded.value = false
            lastInterstitialFailedAt = System.currentTimeMillis()
        }
    }

    fun showInterstitialIfReady(activity: Activity, config: AdConfigEntity): Boolean {
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || !config.interstitialEnabled || !isWortiseEnabled(config)) {
            return false
        }
        val ad = interstitialAd
        if (!_isInterstitialLoaded.value || ad == null || !ad.isAvailable) {
            preloadInterstitial(activity.applicationContext, config)
            return false
        }
        _isInterstitialLoaded.value = false
        return runCatching {
            ad.showAd(activity)
            true
        }.getOrDefault(false)
    }

    fun preloadAppOpen(context: Context, config: AdConfigEntity) {
        val unitId = config.wortiseAppOpenAdUnitId.trim()
        val now = System.currentTimeMillis()
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || unitId.isEmpty()) return
        if (_isAppOpenLoaded.value || isAppOpenLoading) return
        if (now - lastAppOpenFailedAt < retryCooldownMs(config)) return

        isAppOpenLoading = true
        AdRevenueTracker.trackNetworkRequest("wortise", "app_open")
        runCatching {
            appOpenAd?.destroy()
            val ad = AppOpenAd(context.applicationContext, unitId)
            ad.autoReload = false
            ad.listener = object : AppOpenAd.Listener {
                override fun onAppOpenLoaded(ad: AppOpenAd) {
                    isAppOpenLoading = false
                    _isAppOpenLoaded.value = true
                    lastAppOpenFailedAt = 0L
                    AdRevenueTracker.trackNetworkLoad("wortise", "app_open")
                }

                override fun onAppOpenFailedToLoad(ad: AppOpenAd, error: AdError) {
                    isAppOpenLoading = false
                    _isAppOpenLoaded.value = false
                    lastAppOpenFailedAt = System.currentTimeMillis()
                    AdRevenueTracker.trackNetworkError("wortise", "app_open")
                    Log.d(TAG, "Wortise App Open failed to load: $error")
                }

                override fun onAppOpenImpression(ad: AppOpenAd) {
                    AdRevenueTracker.trackNetworkImpression("wortise", "app_open")
                }

                override fun onAppOpenClicked(ad: AppOpenAd) {
                    AdRevenueTracker.trackNetworkClick("wortise", "app_open")
                }

                override fun onAppOpenRevenuePaid(ad: AppOpenAd, data: RevenueData) {
                    trackPaidRevenue("app_open", data)
                }

                override fun onAppOpenFailedToShow(ad: AppOpenAd, error: AdError) {
                    _isAppOpenLoaded.value = false
                    AdRevenueTracker.trackNetworkError("wortise", "app_open")
                }

                override fun onAppOpenDismissed(ad: AppOpenAd) {
                    _isAppOpenLoaded.value = false
                    preloadAppOpen(context.applicationContext, config)
                }
            }
            appOpenAd = ad
            ad.loadAd()
        }.onFailure {
            isAppOpenLoading = false
            _isAppOpenLoaded.value = false
            lastAppOpenFailedAt = System.currentTimeMillis()
        }
    }

    fun showAppOpenIfReady(activity: Activity, config: AdConfigEntity): Boolean {
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || !config.appOpenEnabled || !isWortiseEnabled(config)) {
            return false
        }
        val ad = appOpenAd
        if (!_isAppOpenLoaded.value || ad == null || !ad.isAvailable) {
            if (config.wortiseAppOpenAdUnitId.isNotBlank()) {
                preloadAppOpen(activity.applicationContext, config)
            }
            return false
        }
        _isAppOpenLoaded.value = false
        return runCatching {
            ad.showAd(activity)
            true
        }.getOrDefault(false)
    }

    fun preloadRewarded(context: Context, config: AdConfigEntity) {
        val unitId = config.wortiseRewardedAdUnitId.trim()
        val now = System.currentTimeMillis()
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || unitId.isEmpty()) return
        if (_isRewardedLoaded.value || isRewardedLoading) return
        if (now - lastRewardedFailedAt < retryCooldownMs(config)) return

        isRewardedLoading = true
        AdRevenueTracker.trackNetworkRequest("wortise", "rewarded")
        runCatching {
            rewardedAd?.destroy()
            val ad = RewardedAd(context.applicationContext, unitId)
            ad.listener = object : RewardedAd.Listener {
                override fun onRewardedLoaded(ad: RewardedAd) {
                    isRewardedLoading = false
                    _isRewardedLoaded.value = true
                    lastRewardedFailedAt = 0L
                    AdRevenueTracker.trackNetworkLoad("wortise", "rewarded")
                }

                override fun onRewardedFailedToLoad(ad: RewardedAd, error: AdError) {
                    isRewardedLoading = false
                    _isRewardedLoaded.value = false
                    lastRewardedFailedAt = System.currentTimeMillis()
                    AdRevenueTracker.trackNetworkError("wortise", "rewarded")
                    Log.d(TAG, "Wortise rewarded failed to load: $error")
                }
            }
            rewardedAd = ad
            ad.loadAd()
        }.onFailure {
            isRewardedLoading = false
            _isRewardedLoaded.value = false
            lastRewardedFailedAt = System.currentTimeMillis()
        }
    }

    fun showRewardedIfReady(
        activity: Activity,
        config: AdConfigEntity,
        onFinished: (RewardedAdOutcome) -> Unit
    ): Boolean {
        if (!ConnectivityMonitor.isOnline.value || !_isReady.value || !config.rewardedEnabled || !isWortiseEnabled(config)) {
            return false
        }
        val ad = rewardedAd
        if (!_isRewardedLoaded.value || ad == null || !ad.isAvailable) {
            preloadRewarded(activity.applicationContext, config)
            return false
        }
        _isRewardedLoaded.value = false
        var completed = false
        var callbackDispatched = false
        fun finishOnce(outcome: RewardedAdOutcome) {
            if (!callbackDispatched) {
                callbackDispatched = true
                onFinished(outcome)
            }
        }

        ad.listener = object : RewardedAd.Listener {
            override fun onRewardedImpression(ad: RewardedAd) {
                AdRevenueTracker.trackNetworkImpression("wortise", "rewarded")
            }

            override fun onRewardedClicked(ad: RewardedAd) {
                AdRevenueTracker.trackNetworkClick("wortise", "rewarded")
            }

            override fun onRewardedRevenuePaid(ad: RewardedAd, data: RevenueData) {
                trackPaidRevenue("rewarded", data)
            }

            override fun onRewardedCompleted(ad: RewardedAd, reward: Reward) {
                // Strictly verify genuine Wortise completion callback before granting reward
                completed = true
                AdRevenueTracker.trackRewardedCompleted("wortise")
            }

            override fun onRewardedDismissed(ad: RewardedAd) {
                preloadRewarded(activity.applicationContext, config)
                finishOnce(if (completed) RewardedAdOutcome.COMPLETED else RewardedAdOutcome.SKIPPED)
            }

            override fun onRewardedFailedToShow(ad: RewardedAd, error: AdError) {
                AdRevenueTracker.trackNetworkError("wortise", "rewarded")
                preloadRewarded(activity.applicationContext, config)
                finishOnce(RewardedAdOutcome.FAILED_TO_SHOW)
            }
        }

        return runCatching {
            ad.showAd(activity)
            true
        }.getOrElse {
            preloadRewarded(activity.applicationContext, config)
            false
        }
    }

    internal fun trackPaidRevenue(format: String, data: RevenueData) {
        val value = data.revenue.value
        if (value.isFinite() && value > 0.0 && data.revenue.currency.equals("USD", ignoreCase = true)) {
            AdRevenueTracker.trackNetworkPaidRevenue("wortise", format, value)
        }
    }
}

@Composable
internal fun WortiseBannerViewSlot(
    activity: Activity,
    adConfig: AdConfigEntity,
    adUnitId: String,
    formatKey: String,
    refreshSeconds: Int = adConfig.bannerRefreshSeconds,
    onLoaded: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWortiseReady by WortiseAdsManager.isReady.collectAsStateWithLifecycle()
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnFailed by rememberUpdatedState(onFailed)
    var bannerLoaded by remember(adUnitId) { mutableStateOf(false) }
    var loadRequested by remember(adUnitId) { mutableStateOf(false) }
    var failedReported by remember(adUnitId) { mutableStateOf(false) }

    val cooldownMs = WortiseAdsManager.retryCooldownMs(adConfig)
    val timeoutMs = adConfig.adRequestTimeoutSeconds.coerceIn(4, 30) * 1000L

    fun reportFail() {
        if (!failedReported) {
            failedReported = true
            latestOnFailed()
        }
    }

    val bannerRef = remember(activity, adUnitId) {
        if (adUnitId.isBlank()) null
        else runCatching {
            BannerAd(activity).apply {
                id = R.id.wortise_banner_view
                this.adUnitId = adUnitId
                this.adSize = AdSize.HEIGHT_50
                setAutoRefreshTime(refreshSeconds.coerceIn(30, 300).toLong(), TimeUnit.SECONDS)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                listener = object : BannerAd.Listener {
                    override fun onBannerLoaded(ad: BannerAd) {
                        bannerLoaded = true
                        WortiseAdsManager.clearFormatFailure("banner_$adUnitId")
                        AdRevenueTracker.trackNetworkLoad("wortise", formatKey)
                        latestOnLoaded()
                    }

                    override fun onBannerFailedToLoad(ad: BannerAd, error: AdError) {
                        bannerLoaded = false
                        WortiseAdsManager.markFormatFailed("banner_$adUnitId")
                        AdRevenueTracker.trackNetworkError("wortise", formatKey)
                        reportFail()
                    }

                    override fun onBannerImpression(ad: BannerAd) {
                        AdRevenueTracker.trackNetworkImpression("wortise", formatKey)
                    }

                    override fun onBannerClicked(ad: BannerAd) {
                        AdRevenueTracker.trackNetworkClick("wortise", formatKey)
                    }

                    override fun onBannerRevenuePaid(ad: BannerAd, data: RevenueData) {
                        WortiseAdsManager.trackPaidRevenue(formatKey, data)
                    }
                }
            }
        }.getOrNull()
    }

    LaunchedEffect(adConfig.wortiseAppId, adConfig.testMode) {
        WortiseAdsManager.ensureInitialized(activity, adConfig)
    }

    LaunchedEffect(bannerRef, isWortiseReady) {
        if (bannerRef == null || !ConnectivityMonitor.isOnline.value ||
            WortiseAdsManager.isFormatInCooldown("banner_$adUnitId", cooldownMs)
        ) {
            reportFail()
            return@LaunchedEffect
        }
        if (isWortiseReady && !loadRequested) {
            loadRequested = true
            AdRevenueTracker.trackNetworkRequest("wortise", formatKey)
            runCatching { bannerRef.loadAd() }.onFailure {
                WortiseAdsManager.markFormatFailed("banner_$adUnitId")
                reportFail()
            }
            delay(timeoutMs)
            if (!bannerLoaded && !failedReported) {
                WortiseAdsManager.markFormatFailed("banner_$adUnitId")
                reportFail()
            }
        } else if (!isWortiseReady) {
            delay(minOf(timeoutMs, 6500L))
            if (!WortiseAdsManager.isReady.value && !bannerLoaded && !failedReported) {
                reportFail()
            }
        }
    }

    DisposableEffect(bannerRef) {
        onDispose {
            runCatching {
                (bannerRef?.parent as? ViewGroup)?.removeView(bannerRef)
                bannerRef?.destroy()
            }
        }
    }

    if (bannerRef != null && !failedReported) {
        // Collapse container cleanly until banner is filled (never show an empty broken gap)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(if (bannerLoaded) 52.dp else 1.dp)
                .clipToBounds()
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        id = R.id.wortise_banner_container
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) = ensureAncestorViewIds(v)
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

@Composable
internal fun WortiseNativeAdSlot(
    activity: Activity,
    adConfig: AdConfigEntity,
    adUnitId: String,
    onLoaded: () -> Unit,
    onFailed: () -> Unit
) {
    val isWortiseReady by WortiseAdsManager.isReady.collectAsStateWithLifecycle()
    var loadedNativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }
    var nativeLoader by remember(adUnitId) { mutableStateOf<GoogleNativeAd?>(null) }
    var failedReported by remember(adUnitId) { mutableStateOf(false) }
    var loadRequested by remember(adUnitId) { mutableStateOf(false) }
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnFailed by rememberUpdatedState(onFailed)

    val cooldownMs = WortiseAdsManager.retryCooldownMs(adConfig)
    val timeoutMs = adConfig.adRequestTimeoutSeconds.coerceIn(4, 30) * 1000L

    fun reportFail() {
        if (!failedReported) {
            failedReported = true
            latestOnFailed()
        }
    }

    LaunchedEffect(adConfig.wortiseAppId, adConfig.testMode) {
        WortiseAdsManager.ensureInitialized(activity, adConfig)
    }

    LaunchedEffect(adUnitId, isWortiseReady) {
        if (adUnitId.isBlank() || !ConnectivityMonitor.isOnline.value ||
            WortiseAdsManager.isFormatInCooldown("native_$adUnitId", cooldownMs)
        ) {
            reportFail()
            return@LaunchedEffect
        }
        if (isWortiseReady && !loadRequested) {
            loadRequested = true
            AdRevenueTracker.trackNetworkRequest("wortise", "native")
            runCatching {
                val loader = GoogleNativeAd(activity, adUnitId, object : GoogleNativeAd.Listener {
                    override fun onNativeLoaded(ad: GoogleNativeAd, nativeAd: NativeAd) {
                        loadedNativeAd = nativeAd
                        WortiseAdsManager.clearFormatFailure("native_$adUnitId")
                        AdRevenueTracker.trackNetworkLoad("wortise", "native")
                        latestOnLoaded()
                    }

                    override fun onNativeFailedToLoad(ad: GoogleNativeAd, error: AdError) {
                        WortiseAdsManager.markFormatFailed("native_$adUnitId")
                        AdRevenueTracker.trackNetworkError("wortise", "native")
                        reportFail()
                    }

                    override fun onNativeImpression(ad: GoogleNativeAd) {
                        AdRevenueTracker.trackNetworkImpression("wortise", "native")
                    }

                    override fun onNativeClicked(ad: GoogleNativeAd) {
                        AdRevenueTracker.trackNetworkClick("wortise", "native")
                    }

                    override fun onNativeRevenuePaid(ad: GoogleNativeAd, data: RevenueData) {
                        WortiseAdsManager.trackPaidRevenue("native", data)
                    }
                })
                nativeLoader = loader
                loader.load()
            }.onFailure {
                WortiseAdsManager.markFormatFailed("native_$adUnitId")
                reportFail()
            }
            delay(timeoutMs)
            if (loadedNativeAd == null && !failedReported) {
                WortiseAdsManager.markFormatFailed("native_$adUnitId")
                reportFail()
            }
        } else if (!isWortiseReady) {
            delay(minOf(timeoutMs, 6500L))
            if (!WortiseAdsManager.isReady.value && loadedNativeAd == null && !failedReported) {
                reportFail()
            }
        }
    }

    DisposableEffect(nativeLoader, loadedNativeAd) {
        onDispose {
            runCatching { loadedNativeAd?.destroy() }
            runCatching { nativeLoader?.destroy() }
        }
    }

    val nativeAd = loadedNativeAd ?: return

    AndroidView(
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            NativeAdView(ctx).apply {
                id = R.id.wortise_native_container
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = ensureAncestorViewIds(v)
                    override fun onViewDetachedFromWindow(v: View) = Unit
                })

                val root = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.WHITE)
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), AndroidColor.parseColor("#CBD5E1"))
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val topRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val iconDrawable = nativeAd.icon?.drawable
                if (iconDrawable != null) {
                    val iconView = ImageView(ctx).apply {
                        setImageDrawable(iconDrawable)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                            marginEnd = dp(10)
                        }
                    }
                    this.iconView = iconView
                    topRow.addView(iconView)
                }

                val titleCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val badgeRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val adBadge = TextView(ctx).apply {
                    text = "Ad"
                    textSize = 10f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(AndroidColor.parseColor("#6D28D9"))
                    setPadding(dp(6), dp(1), dp(6), dp(1))
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.parseColor("#EDE9FE"))
                        cornerRadius = dp(6).toFloat()
                    }
                }
                badgeRow.addView(adBadge)
                if (!nativeAd.advertiser.isNullOrBlank()) {
                    val advText = TextView(ctx).apply {
                        text = " • ${nativeAd.advertiser}"
                        textSize = 11f
                        setTextColor(AndroidColor.parseColor("#64748B"))
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    this.advertiserView = advText
                    badgeRow.addView(advText)
                }
                titleCol.addView(badgeRow)

                val headlineTv = TextView(ctx).apply {
                    text = nativeAd.headline.orEmpty()
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(AndroidColor.parseColor("#0F172A"))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                this.headlineView = headlineTv
                titleCol.addView(headlineTv)
                topRow.addView(titleCol)

                val ctaBtn = TextView(ctx).apply {
                    text = nativeAd.callToAction.orEmpty().ifBlank { "Open" }
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(AndroidColor.WHITE)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.parseColor("#7C3AED"))
                        cornerRadius = dp(999).toFloat()
                    }
                }
                this.callToActionView = ctaBtn
                topRow.addView(ctaBtn)
                root.addView(topRow)

                if (!nativeAd.body.isNullOrBlank()) {
                    val bodyTv = TextView(ctx).apply {
                        text = nativeAd.body
                        textSize = 12f
                        setTextColor(AndroidColor.parseColor("#475569"))
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, dp(8), 0, 0)
                    }
                    this.bodyView = bodyTv
                    root.addView(bodyTv)
                }

                if (nativeAd.mediaContent != null) {
                    val mediaView = MediaView(ctx).apply {
                        mediaContent = nativeAd.mediaContent
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(140)
                        ).apply {
                            topMargin = dp(8)
                        }
                    }
                    this.mediaView = mediaView
                    root.addView(mediaView)
                }

                addView(root)
                setNativeAd(nativeAd)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
