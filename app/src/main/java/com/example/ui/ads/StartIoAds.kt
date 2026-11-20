package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.local.AdConfigEntity
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.banner.Mrec
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdDisplayListener
import com.startapp.sdk.ads.nativead.NativeAdInterface
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production Start.io SDK + Hybrid Online Ad Controller (Start.io + Unity Ads).
 *
 * Strict Hybrid Policy:
 * - ONLINE:  Start.io Ads + Unity Ads + Custom Direct Sponsor Ads.
 * - OFFLINE: Start.io & Unity Ads strictly disabled (0 requests, 0 SDK calls) -> Direct Sponsor Ads ONLY.
 * - ZERO AdMob anywhere in the application.
 */
object StartIoAdManager {
    private const val TAG = "StartIoAdManager"

    private var initializedAppId: String? = null
    private var initializedTestMode: Boolean? = null

    private var interstitialAd: StartAppAd? = null
    private var isInterstitialLoaded = false
    private var isInterstitialLoading = false
    private var lastInterstitialShownAt = 0L

    private var rewardedAd: StartAppAd? = null
    private val _isRewardedLoaded = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedLoaded.asStateFlow()
    private var isRewardedLoading = false

    fun isRewardedRequestInFlight(): Boolean = isRewardedLoading

    fun isStartIoEnabled(config: AdConfigEntity): Boolean {
        if (!config.adsEnabled) return false
        if (config.startIoAppId.isBlank()) return false
        return when (config.activeNetwork.uppercase()) {
            "STARTIO", "BOTH_UNITY_PRIMARY", "BOTH_STARTIO_PRIMARY" -> true
            else -> false
        }
    }

    internal fun effectiveTestMode(config: AdConfigEntity): Boolean = config.testMode

    fun ensureInitialized(context: Context, config: AdConfigEntity) {
        if (!ConnectivityMonitor.isOnline.value) return
        if (!isStartIoEnabled(config)) return
        val appId = config.startIoAppId.trim()
        val useTestMode = effectiveTestMode(config)
        if (initializedAppId == appId && initializedTestMode == useTestMode) return

        initializedAppId = appId
        initializedTestMode = useTestMode

        runCatching {
            StartAppSDK.setTestAdsEnabled(useTestMode)
            StartAppSDK.initParams(context.applicationContext, appId)
                .setReturnAdsEnabled(false)
                .init()
            StartAppAd.disableSplash()
            StartAppSDK.setTestAdsEnabled(useTestMode)
            Log.d(TAG, "Start.io initialized (appId=$appId, testMode=$useTestMode)")
            preloadPlacements(context.applicationContext, config)
        }.onFailure { err ->
            Log.w(TAG, "Start.io init exception: ${err.message}")
        }
    }

    fun preloadPlacements(context: Context, config: AdConfigEntity) {
        if (!ConnectivityMonitor.isOnline.value || !isStartIoEnabled(config)) return
        if (config.appOpenEnabled) preloadInterstitial(context)
        if (config.rewardedEnabled) preloadRewarded(context)
    }

    fun preloadInterstitial(context: Context) {
        if (!ConnectivityMonitor.isOnline.value || isInterstitialLoaded || isInterstitialLoading) return
        isInterstitialLoading = true
        AdRevenueTracker.trackNetworkRequest("startio", "app_open")
        runCatching {
            val ad = StartAppAd(context.applicationContext)
            interstitialAd = ad
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(adObj: Ad) {
                    isInterstitialLoading = false
                    isInterstitialLoaded = true
                    AdRevenueTracker.trackNetworkLoad("startio", "app_open")
                }

                override fun onFailedToReceiveAd(adObj: Ad?) {
                    isInterstitialLoading = false
                    isInterstitialLoaded = false
                    AdRevenueTracker.trackNetworkError("startio", "app_open")
                }
            })
        }.onFailure {
            isInterstitialLoading = false
            isInterstitialLoaded = false
        }
    }

    fun preloadRewarded(context: Context) {
        if (!ConnectivityMonitor.isOnline.value || _isRewardedLoaded.value || isRewardedLoading) return
        isRewardedLoading = true
        AdRevenueTracker.trackNetworkRequest("startio", "rewarded")
        runCatching {
            val ad = StartAppAd(context.applicationContext)
            rewardedAd = ad
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(adObj: Ad) {
                    isRewardedLoading = false
                    _isRewardedLoaded.value = true
                    AdRevenueTracker.trackNetworkLoad("startio", "rewarded")
                }

                override fun onFailedToReceiveAd(adObj: Ad?) {
                    isRewardedLoading = false
                    _isRewardedLoaded.value = false
                    AdRevenueTracker.trackNetworkError("startio", "rewarded")
                }
            })
        }.onFailure {
            isRewardedLoading = false
            _isRewardedLoaded.value = false
        }
    }

    fun showInterstitialIfReady(context: Context, config: AdConfigEntity): Boolean {
        if (!ConnectivityMonitor.isOnline.value || !config.appOpenEnabled || !isStartIoEnabled(config)) return false
        val now = System.currentTimeMillis()
        val cooldownMs = config.appOpenCooldownSeconds.coerceIn(15, 3600) * 1000L
        if (now - lastInterstitialShownAt < cooldownMs) return false
        val ad = interstitialAd
        if (!isInterstitialLoaded || ad == null) {
            preloadInterstitial(context)
            return false
        }
        isInterstitialLoaded = false
        lastInterstitialShownAt = now
        return runCatching {
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(adObj: Ad) {
                    preloadInterstitial(context)
                }

                override fun adDisplayed(adObj: Ad) {
                    AdRevenueTracker.trackNetworkImpression("startio", "app_open")
                }

                override fun adClicked(adObj: Ad) {
                    AdRevenueTracker.trackNetworkClick("startio", "app_open")
                }

                override fun adNotDisplayed(adObj: Ad) {
                    AdRevenueTracker.trackNetworkError("startio", "app_open")
                    preloadInterstitial(context)
                }
            })
        }.getOrDefault(false)
    }

    fun showRewardedIfReady(
        context: Context,
        config: AdConfigEntity,
        onFinished: (RewardedAdOutcome) -> Unit
    ): Boolean {
        if (!ConnectivityMonitor.isOnline.value || !config.rewardedEnabled || !isStartIoEnabled(config)) return false
        val ad = rewardedAd
        if (!_isRewardedLoaded.value || ad == null) {
            preloadRewarded(context)
            return false
        }
        _isRewardedLoaded.value = false
        var earned = false
        return runCatching {
            ad.setVideoListener(VideoListener {
                earned = true
                AdRevenueTracker.trackRewardedCompleted("startio")
            })
            val shown = ad.showAd(object : AdDisplayListener {
                override fun adHidden(adObj: Ad) {
                    preloadRewarded(context)
                    onFinished(if (earned) RewardedAdOutcome.COMPLETED else RewardedAdOutcome.SKIPPED)
                }

                override fun adDisplayed(adObj: Ad) {
                    AdRevenueTracker.trackNetworkImpression("startio", "rewarded")
                }

                override fun adClicked(adObj: Ad) {
                    AdRevenueTracker.trackNetworkClick("startio", "rewarded")
                }

                override fun adNotDisplayed(adObj: Ad) {
                    AdRevenueTracker.trackNetworkError("startio", "rewarded")
                    preloadRewarded(context)
                    onFinished(RewardedAdOutcome.FAILED_TO_SHOW)
                }
            })
            if (!shown) {
                preloadRewarded(context)
            }
            shown
        }.getOrDefault(false)
    }
}

/**
 * Coordinates online-only Start.io + Unity Ads initialization & failover.
 * Completely silent and inactive when offline so Offline Mode serves Custom Direct Sponsor Ads ONLY.
 */
object HybridNetworkAdController {
    fun syncOnlineSdks(context: Context, config: AdConfigEntity, isOnline: Boolean) {
        if (!isOnline || !ConnectivityMonitor.isOnline.value || !config.adsEnabled) return
        if (config.activeNetwork.equals("SPONSORS_ONLY", ignoreCase = true)) return

        if (UnityAdsManager.isUnityEnabled(config)) {
            UnityAdsManager.ensureInitialized(context, config)
        }
        if (StartIoAdManager.isStartIoEnabled(config)) {
            StartIoAdManager.ensureInitialized(context, config)
        }
    }

    fun getOrderedNetworksForOnline(config: AdConfigEntity): List<String> {
        if (!config.adsEnabled) return emptyList()
        val hasUnity = UnityAdsManager.isUnityEnabled(config)
        val hasStartIo = StartIoAdManager.isStartIoEnabled(config)
        return when (config.activeNetwork.uppercase()) {
            "BOTH_UNITY_PRIMARY" -> listOfNotNull(
                if (hasUnity) "UNITY" else null,
                if (hasStartIo) "STARTIO" else null
            )
            "BOTH_STARTIO_PRIMARY" -> listOfNotNull(
                if (hasStartIo) "STARTIO" else null,
                if (hasUnity) "UNITY" else null
            )
            "UNITY" -> if (hasUnity) listOf("UNITY") else emptyList()
            "STARTIO" -> if (hasStartIo) listOf("STARTIO") else emptyList()
            else -> emptyList()
        }
    }

    fun preloadRewardedForOnline(context: Context, config: AdConfigEntity, isOnline: Boolean) {
        if (!isOnline || !ConnectivityMonitor.isOnline.value || !config.adsEnabled || !config.rewardedEnabled) return
        syncOnlineSdks(context, config, isOnline)
        for (net in getOrderedNetworksForOnline(config)) {
            when (net) {
                "UNITY" -> UnityAdsManager.preloadRewarded(config.unityRewardedPlacement)
                "STARTIO" -> StartIoAdManager.preloadRewarded(context)
            }
        }
    }

    fun isAnyRewardedReady(config: AdConfigEntity): Boolean {
        for (net in getOrderedNetworksForOnline(config)) {
            val ready = when (net) {
                "UNITY" -> UnityAdsManager.isRewardedLoaded.value
                "STARTIO" -> StartIoAdManager.isRewardedReady.value
                else -> false
            }
            if (ready) return true
        }
        return false
    }

    fun isAnyRewardedLoading(config: AdConfigEntity): Boolean {
        for (net in getOrderedNetworksForOnline(config)) {
            val loading = when (net) {
                "UNITY" -> UnityAdsManager.isRewardedRequestInFlight()
                "STARTIO" -> StartIoAdManager.isRewardedRequestInFlight()
                else -> false
            }
            if (loading) return true
        }
        return false
    }

    fun showOnlineRewardedIfReady(
        activity: Activity,
        config: AdConfigEntity,
        isOnline: Boolean,
        onOutcome: (RewardedAdOutcome) -> Unit
    ): Boolean {
        if (!isOnline || !ConnectivityMonitor.isOnline.value || !config.adsEnabled || !config.rewardedEnabled) {
            return false
        }
        for (net in getOrderedNetworksForOnline(config)) {
            val shown = when (net) {
                "UNITY" -> UnityAdsManager.showRewardedIfReady(activity, config, onOutcome)
                "STARTIO" -> StartIoAdManager.showRewardedIfReady(activity, config, onOutcome)
                else -> false
            }
            if (shown) return true
        }
        return false
    }

    fun maybeShowOnlineInterstitial(activity: Activity, config: AdConfigEntity, isOnline: Boolean): Boolean {
        if (!isOnline || !ConnectivityMonitor.isOnline.value || !config.adsEnabled || !config.appOpenEnabled) {
            return false
        }
        for (net in getOrderedNetworksForOnline(config)) {
            val shown = when (net) {
                "UNITY" -> UnityAdsManager.showInterstitialIfReady(activity, config)
                "STARTIO" -> StartIoAdManager.showInterstitialIfReady(activity, config)
                else -> false
            }
            if (shown) return true
        }
        return false
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ManagedBannerAd(
    adConfig: AdConfigEntity,
    isOnline: Boolean,
    refreshKey: String = "",
    modifier: Modifier = Modifier
) {
    if (!isOnline || !adConfig.adsEnabled || !adConfig.bannerEnabled) return
    val ordered = remember(
        adConfig.activeNetwork,
        adConfig.unityGameId,
        adConfig.startIoAppId,
        adConfig.adsEnabled,
        adConfig.unityBannerPlacement
    ) {
        HybridNetworkAdController.getOrderedNetworksForOnline(adConfig)
    }
    if (ordered.isEmpty()) return

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    var retryCycle by remember(ordered, refreshKey) { mutableIntStateOf(0) }
    var attemptIdx by remember(ordered, refreshKey, retryCycle) { mutableIntStateOf(0) }
    var filled by remember(ordered, refreshKey, retryCycle) { mutableStateOf(false) }

    LaunchedEffect(isOnline, adConfig.unityGameId, adConfig.startIoAppId, adConfig.testMode) {
        HybridNetworkAdController.syncOnlineSdks(context, adConfig, isOnline)
    }

    // Automatic cooldown recovery if initial cold-start request received NO FILL
    LaunchedEffect(attemptIdx, ordered.size, isOnline, retryCycle) {
        if (isOnline && attemptIdx >= ordered.size) {
            delay(45_000L)
            retryCycle++
        }
    }

    if (attemptIdx >= ordered.size) return
    val currentNet = ordered[attemptIdx]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("online_network_banner_${currentNet.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        key(currentNet, refreshKey, retryCycle) {
            when (currentNet) {
                "UNITY" -> {
                    UnityBannerViewSlot(
                        activity = activity,
                        adConfig = adConfig,
                        placementId = adConfig.unityBannerPlacement.ifBlank { "Banner_Android" },
                        formatKey = "banner",
                        onLoaded = { filled = true },
                        onFailed = {
                            if (!filled) attemptIdx++
                        }
                    )
                }
                "STARTIO" -> {
                    StartIoBannerViewSlot(
                        activity = activity,
                        adConfig = adConfig,
                        isMrec = false,
                        formatKey = "banner",
                        onLoaded = { filled = true },
                        onFailed = {
                            if (!filled) attemptIdx++
                        }
                    )
                }
            }
        }
    }
}

/**
 * Online-ONLY Start.io (Native Ad -> MREC -> Banner) + Unity Ads in-feed Native Banner with automatic failover.
 * Strictly renders NOTHING (0dp) when offline so Offline Mode serves Custom Direct Sponsor Ads ONLY.
 */
@Composable
fun ManagedNativeBannerAd(
    adConfig: AdConfigEntity,
    isOnline: Boolean,
    refreshKey: String = "default",
    modifier: Modifier = Modifier
) {
    if (!isOnline || !adConfig.adsEnabled || !adConfig.nativeBannerEnabled) return
    val steps = remember(
        adConfig.activeNetwork,
        adConfig.unityGameId,
        adConfig.startIoAppId,
        adConfig.adsEnabled,
        adConfig.unityBannerPlacement,
        adConfig.unityNativeBannerPlacement
    ) {
        val base = HybridNetworkAdController.getOrderedNetworksForOnline(adConfig)
        val bannerPid = adConfig.unityBannerPlacement.ifBlank { "Banner_Android" }
        val nativePid = adConfig.unityNativeBannerPlacement.ifBlank { bannerPid }
        val prioritizeStartIo = base.size > 1 && "STARTIO" in base &&
            (adConfig.activeNetwork.equals("BOTH_STARTIO_PRIMARY", ignoreCase = true) ||
                nativePid.equals(bannerPid, ignoreCase = true))
        val networkOrder = if (prioritizeStartIo) {
            listOf("STARTIO") + base.filter { it != "STARTIO" }
        } else {
            base
        }
        buildList {
            for (net in networkOrder) {
                when (net) {
                    "STARTIO" -> {
                        add("STARTIO_NATIVE")
                        add("STARTIO_MREC")
                        add("STARTIO_BANNER")
                    }
                    "UNITY" -> add("UNITY_NATIVE")
                }
            }
        }
    }
    if (steps.isEmpty()) return

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    var retryCycle by remember(steps, refreshKey) { mutableIntStateOf(0) }
    var attemptIdx by remember(steps, refreshKey, retryCycle) { mutableIntStateOf(0) }
    var filled by remember(steps, refreshKey, retryCycle) { mutableStateOf(false) }

    LaunchedEffect(isOnline, adConfig.unityGameId, adConfig.startIoAppId, adConfig.testMode) {
        HybridNetworkAdController.syncOnlineSdks(context, adConfig, isOnline)
    }

    LaunchedEffect(attemptIdx, steps.size, isOnline, retryCycle) {
        if (isOnline && attemptIdx >= steps.size) {
            delay(45_000L)
            retryCycle++
        }
    }

    if (attemptIdx >= steps.size) return
    val currentStep = steps[attemptIdx]
    val netTag = if (currentStep.startsWith("STARTIO")) "startio" else "unity"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (filled) 4.dp else 0.dp)
            .testTag("online_network_native_$netTag"),
        contentAlignment = Alignment.Center
    ) {
        key(currentStep, refreshKey, retryCycle) {
            when (currentStep) {
                "STARTIO_NATIVE" -> {
                    StartIoNativeAdSlot(
                        activity = activity,
                        adConfig = adConfig,
                        onLoaded = { filled = true },
                        onFailed = {
                            if (!filled) attemptIdx++
                        }
                    )
                }
                "STARTIO_MREC" -> {
                    StartIoBannerViewSlot(
                        activity = activity,
                        adConfig = adConfig,
                        isMrec = true,
                        formatKey = "native",
                        onLoaded = { filled = true },
                        onFailed = {
                            if (!filled) attemptIdx++
                        }
                    )
                }
                "STARTIO_BANNER" -> {
                    StartIoBannerViewSlot(
                        activity = activity,
                        adConfig = adConfig,
                        isMrec = false,
                        formatKey = "native",
                        onLoaded = { filled = true },
                        onFailed = {
                            if (!filled) attemptIdx++
                        }
                    )
                }
                "UNITY_NATIVE" -> {
                    UnityBannerViewSlot(
                        activity = activity,
                        adConfig = adConfig,
                        placementId = adConfig.unityNativeBannerPlacement.ifBlank { adConfig.unityBannerPlacement.ifBlank { "Banner_Android" } },
                        formatKey = "native",
                        onLoaded = { filled = true },
                        onFailed = {
                            if (!filled) attemptIdx++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartIoNativeAdSlot(
    activity: Activity,
    adConfig: AdConfigEntity,
    onLoaded: () -> Unit,
    onFailed: () -> Unit
) {
    var nativeDetails by remember { mutableStateOf<NativeAdDetails?>(null) }
    var failedReported by remember { mutableStateOf(false) }
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnFailed by rememberUpdatedState(onFailed)

    fun reportFail() {
        if (!failedReported) {
            failedReported = true
            latestOnFailed()
        }
    }

    LaunchedEffect(adConfig.startIoAppId, adConfig.testMode) {
        if (!ConnectivityMonitor.isOnline.value) {
            reportFail()
            return@LaunchedEffect
        }
        StartIoAdManager.ensureInitialized(activity, adConfig)
        AdRevenueTracker.trackNetworkRequest("startio", "native")
        val nativeAd = StartAppNativeAd(activity.applicationContext)
        val prefs = NativeAdPreferences()
            .setAdsNumber(1)
            .setAutoBitmapDownload(true)
            .setPrimaryImageSize(4)
            .setSecondaryImageSize(2)
        runCatching {
            nativeAd.loadAd(prefs, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    val details = nativeAd.nativeAds?.firstOrNull()
                    if (details != null) {
                        nativeDetails = details
                        AdRevenueTracker.trackNetworkLoad("startio", "native")
                        latestOnLoaded()
                    } else {
                        AdRevenueTracker.trackNetworkError("startio", "native")
                        reportFail()
                    }
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    AdRevenueTracker.trackNetworkError("startio", "native")
                    reportFail()
                }
            })
        }.onFailure {
            reportFail()
        }
        delay(9000L)
        if (nativeDetails == null && !failedReported) {
            reportFail()
        }
    }

    DisposableEffect(nativeDetails) {
        onDispose {
            runCatching { nativeDetails?.unregisterView() }
        }
    }

    val details = nativeDetails ?: return

    AndroidView(
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            LinearLayout(ctx).apply {
                id = R.id.startio_native_container
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
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = ensureAncestorViewIds(v)
                    override fun onViewDetachedFromWindow(v: View) = Unit
                })

                val topRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val iconBmp = details.secondaryImageBitmap ?: details.imageBitmap
                if (iconBmp != null) {
                    val iconView = ImageView(ctx).apply {
                        setImageBitmap(iconBmp)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                            marginEnd = dp(10)
                        }
                    }
                    topRow.addView(iconView)
                }

                val titleCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val badgeAndCat = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val adBadge = TextView(ctx).apply {
                    text = "Ad"
                    textSize = 10f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(AndroidColor.parseColor("#0369A1"))
                    setPadding(dp(6), dp(1), dp(6), dp(1))
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.parseColor("#E0F2FE"))
                        cornerRadius = dp(6).toFloat()
                    }
                }
                badgeAndCat.addView(adBadge)
                if (!details.category.isNullOrBlank()) {
                    val catText = TextView(ctx).apply {
                        text = " • ${details.category}"
                        textSize = 11f
                        setTextColor(AndroidColor.parseColor("#64748B"))
                    }
                    badgeAndCat.addView(catText)
                }
                titleCol.addView(badgeAndCat)

                val titleTv = TextView(ctx).apply {
                    text = details.title.orEmpty()
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(AndroidColor.parseColor("#0F172A"))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                titleCol.addView(titleTv)
                topRow.addView(titleCol)

                val ctaBtn = TextView(ctx).apply {
                    text = details.callToAction.orEmpty().ifBlank { "Open" }
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(AndroidColor.WHITE)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.parseColor("#0284C7"))
                        cornerRadius = dp(999).toFloat()
                    }
                }
                topRow.addView(ctaBtn)
                addView(topRow)

                if (!details.description.isNullOrBlank()) {
                    val descTv = TextView(ctx).apply {
                        text = details.description
                        textSize = 12f
                        setTextColor(AndroidColor.parseColor("#475569"))
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, dp(8), 0, 0)
                    }
                    addView(descTv)
                }

                if (details.imageBitmap != null && details.imageBitmap != iconBmp) {
                    val mainImg = ImageView(ctx).apply {
                        setImageBitmap(details.imageBitmap)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(140)
                        ).apply {
                            topMargin = dp(8)
                        }
                    }
                    addView(mainImg)
                }

                details.registerViewForInteraction(
                    this,
                    listOf(this, ctaBtn),
                    object : NativeAdDisplayListener {
                        override fun adHidden(adInterface: NativeAdInterface?) = Unit
                        override fun adDisplayed(adInterface: NativeAdInterface?) {
                            AdRevenueTracker.trackNetworkImpression("startio", "native")
                        }
                        override fun adClicked(adInterface: NativeAdInterface?) {
                            AdRevenueTracker.trackNetworkClick("startio", "native")
                        }
                        override fun adNotDisplayed(adInterface: NativeAdInterface?) {
                            AdRevenueTracker.trackNetworkError("startio", "native")
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StartIoBannerViewSlot(
    activity: Activity,
    adConfig: AdConfigEntity,
    isMrec: Boolean,
    formatKey: String,
    onLoaded: () -> Unit,
    onFailed: () -> Unit
) {
    var adLoaded by remember(isMrec, formatKey) { mutableStateOf(false) }
    var failedReported by remember(isMrec, formatKey) { mutableStateOf(false) }
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnFailed by rememberUpdatedState(onFailed)

    val reportFailed = {
        if (!failedReported) {
            failedReported = true
            latestOnFailed()
        }
    }

    LaunchedEffect(isMrec, formatKey) {
        delay(10_000L)
        if (!adLoaded && !failedReported) {
            reportFailed()
        }
    }

    val targetWidthDp = if (isMrec) 300 else 320
    val targetHeightDp = if (isMrec) 250 else 50

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((targetHeightDp + 2).dp)
            .clipToBounds()
    ) {
        AndroidView(
            factory = { ctx ->
                val density = ctx.resources.displayMetrics.density
                val wPx = (targetWidthDp * density).toInt().coerceAtLeast(1)
                val hPx = (targetHeightDp * density).toInt().coerceAtLeast(1)
                FrameLayout(ctx).apply {
                    id = R.id.startio_banner_container
                    val listener = object : BannerListener {
                        override fun onReceiveAd(banner: View?) {
                            adLoaded = true
                            AdRevenueTracker.trackNetworkLoad("startio", formatKey)
                            latestOnLoaded()
                        }

                        override fun onFailedToReceiveAd(banner: View?) {
                            adLoaded = false
                            AdRevenueTracker.trackNetworkError("startio", formatKey)
                            reportFailed()
                        }

                        override fun onImpression(banner: View?) {
                            AdRevenueTracker.trackNetworkImpression("startio", formatKey)
                        }

                        override fun onClick(banner: View?) {
                            AdRevenueTracker.trackNetworkClick("startio", formatKey)
                        }
                    }
                    if (ConnectivityMonitor.isOnline.value) {
                        StartIoAdManager.ensureInitialized(activity, adConfig)
                        AdRevenueTracker.trackNetworkRequest("startio", formatKey)
                        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                ensureAncestorViewIds(v)
                            }
                            override fun onViewDetachedFromWindow(v: View) = Unit
                        })
                        runCatching {
                            val adView: View = if (isMrec) Mrec(activity, listener) else Banner(activity, listener)
                            adView.id = R.id.startio_banner_view
                            val params = FrameLayout.LayoutParams(wPx, hPx, Gravity.CENTER)
                            addView(adView, params)
                        }.onFailure {
                            reportFailed()
                        }
                    } else {
                        reportFailed()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(targetHeightDp.dp)
        )
    }
}

internal fun ensureAncestorViewIds(startView: View) {
    val fallbackIds = intArrayOf(
        R.id.ad_ancestor_slot_1,
        R.id.ad_ancestor_slot_2,
        R.id.ad_ancestor_slot_3,
        R.id.ad_ancestor_slot_4,
        R.id.ad_ancestor_slot_5,
        R.id.ad_ancestor_slot_6,
        R.id.ad_ancestor_slot_7,
        R.id.ad_ancestor_slot_8
    )
    var idx = 0
    var parent = startView.parent
    while (parent is View && idx < fallbackIds.size) {
        if (parent.id == View.NO_ID) {
            parent.id = fallbackIds[idx]
            idx++
        }
        parent = parent.parent
    }
}
