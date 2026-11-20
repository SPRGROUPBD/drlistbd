package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AdConfigEntity
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*
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

private tailrec fun Context.findActivityCtx(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityCtx()
    else -> null
}

/**
 * Official Unity Ads SDK (v4.14.1) controller + Dual-Network Ad Manager (Unity Ads + Start.io).
 * Configured dynamically via Admin Panel (`directory/ad_config` in Firebase RTDB).
 * Default `testMode = false` guarantees 100% REAL LIVE REVENUE ADS.
 */
object UnityAdsManager {
    private const val TAG = "UnityAdsManager"

    @Volatile
    private var initializedGameId: String = ""
    @Volatile
    private var initializedTestMode: Boolean? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    fun isConfigured(config: AdConfigEntity): Boolean =
        config.unityGameId.trim().isNotEmpty()

    fun init(context: Context, gameId: String, testMode: Boolean = false) {
        val cleanId = gameId.trim()
        if (cleanId.isEmpty()) {
            Log.d(TAG, "Unity Game ID not set yet in Admin Panel; skipping Unity SDK init.")
            return
        }
        if (initializedGameId == cleanId && initializedTestMode == testMode && (UnityAds.isInitialized || _isInitialized.value)) {
            return
        }
        initializedGameId = cleanId
        initializedTestMode = testMode
        val appCtx = context.applicationContext ?: context
        try {
            UnityAds.initialize(
                appCtx,
                cleanId,
                testMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        Log.d(TAG, "Unity Ads initialized (gameId=$cleanId, realLiveAds=${!testMode})")
                        _isInitialized.value = true
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError?,
                        message: String?
                    ) {
                        Log.w(TAG, "Unity Ads init failed: $error — $message")
                        _isInitialized.value = false
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unity Ads init exception: ${e.message}", e)
            _isInitialized.value = false
        }
    }

    /**
     * Loads and immediately displays a Unity App Open / Interstitial Ad unit.
     */
    fun loadAndShowInterstitial(
        activity: Activity,
        placementId: String,
        onShown: () -> Unit = {},
        onDismissed: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        val cleanPlacement = placementId.trim().ifBlank { "Interstitial_Android" }
        if (!UnityAds.isInitialized) {
            onFailed()
            return
        }
        try {
            UnityAds.load(cleanPlacement, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(id: String?) {
                    UnityAds.show(
                        activity,
                        cleanPlacement,
                        UnityAdsShowOptions(),
                        object : IUnityAdsShowListener {
                            override fun onUnityAdsShowFailure(
                                id: String?,
                                error: UnityAds.UnityAdsShowError?,
                                message: String?
                            ) {
                                Log.w(TAG, "Unity show failure ($cleanPlacement): $error $message")
                                onFailed()
                            }

                            override fun onUnityAdsShowStart(id: String?) {
                                onShown()
                            }

                            override fun onUnityAdsShowClick(id: String?) {}

                            override fun onUnityAdsShowComplete(
                                id: String?,
                                state: UnityAds.UnityAdsShowCompletionState?
                            ) {
                                onDismissed()
                            }
                        }
                    )
                }

                override fun onUnityAdsFailedToLoad(
                    id: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    Log.w(TAG, "Unity load failure ($cleanPlacement): $error $message")
                    onFailed()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Unity loadAndShowInterstitial exception: ${e.message}")
            onFailed()
        }
    }

    /**
     * Loads and displays a Unity Rewarded Video Ad unit.
     */
    fun loadAndShowRewarded(
        activity: Activity,
        placementId: String,
        onRewarded: () -> Unit,
        onFailed: () -> Unit
    ) {
        val cleanPlacement = placementId.trim().ifBlank { "Rewarded_Android" }
        if (!UnityAds.isInitialized) {
            onFailed()
            return
        }
        try {
            UnityAds.load(cleanPlacement, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(id: String?) {
                    UnityAds.show(
                        activity,
                        cleanPlacement,
                        UnityAdsShowOptions(),
                        object : IUnityAdsShowListener {
                            override fun onUnityAdsShowFailure(
                                id: String?,
                                error: UnityAds.UnityAdsShowError?,
                                message: String?
                            ) {
                                onFailed()
                            }

                            override fun onUnityAdsShowStart(id: String?) {}
                            override fun onUnityAdsShowClick(id: String?) {}

                            override fun onUnityAdsShowComplete(
                                id: String?,
                                state: UnityAds.UnityAdsShowCompletionState?
                            ) {
                                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                    onRewarded()
                                }
                            }
                        }
                    )
                }

                override fun onUnityAdsFailedToLoad(
                    id: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    Log.w(TAG, "Unity rewarded load failed ($cleanPlacement): $error $message")
                    onFailed()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Unity rewarded exception: ${e.message}")
            onFailed()
        }
    }
}

/**
 * Syncs live Admin AdConfigEntity with both Unity Ads SDK and Start.io SDK.
 */
@Composable
fun AdSdkInitializerEffect(adConfig: AdConfigEntity) {
    val context = LocalContext.current
    LaunchedEffect(
        adConfig.adsEnabled,
        adConfig.activeNetwork,
        adConfig.testMode,
        adConfig.unityGameId,
        adConfig.startIoAppId
    ) {
        if (!adConfig.adsEnabled) return@LaunchedEffect
        val useUnity = adConfig.activeNetwork != "STARTIO"
        val useStartIo = adConfig.activeNetwork != "UNITY"
        if (useUnity && adConfig.unityGameId.isNotBlank()) {
            UnityAdsManager.init(context, adConfig.unityGameId, adConfig.testMode)
        }
        if (useStartIo && adConfig.startIoAppId.isNotBlank()) {
            StartIoConfig.init(context, adConfig.startIoAppId, adConfig.testMode)
        }
    }
}

/**
 * Real Unity BannerView Composable (supports 320x50 Banner or 300x250 MREC/Native Banner).
 * Properly destroys the native BannerView when removed from composition.
 */
@Composable
fun UnityBannerAdView(
    placementId: String,
    widthDp: Int = 320,
    heightDp: Int = 50,
    onLoaded: () -> Unit = {},
    onFailed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val unityReady by UnityAdsManager.isInitialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityCtx() }

    if (!unityReady || activity == null) return

    var isLoaded by remember(placementId, widthDp, heightDp) { mutableStateOf(false) }
    var hasFailed by remember(placementId, widthDp, heightDp) { mutableStateOf(false) }

    val bannerView = remember(activity, placementId, widthDp, heightDp) {
        BannerView(activity, placementId, UnityBannerSize(widthDp, heightDp)).apply {
            listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    Log.d("UnityAdsManager", "Unity Banner loaded ($placementId ${widthDp}x${heightDp})")
                    isLoaded = true
                    onLoaded()
                }

                override fun onBannerShown(bannerAdView: BannerView?) {
                    Log.d("UnityAdsManager", "Unity Banner shown ($placementId)")
                }

                override fun onBannerClick(bannerAdView: BannerView?) {}

                override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
                    Log.w("UnityAdsManager", "Unity Banner failed ($placementId): ${errorInfo?.errorMessage}")
                    hasFailed = true
                    onFailed()
                }

                override fun onBannerLeftApplication(bannerView: BannerView?) {}
            }
            load()
        }
    }

    DisposableEffect(bannerView) {
        onDispose {
            try {
                bannerView.destroy()
            } catch (_: Exception) {}
        }
    }

    if (!hasFailed) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(if (isLoaded) heightDp.dp else 1.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = {
                    val density = activity.resources.displayMetrics.density
                    val wPx = (widthDp * density).toInt()
                    val hPx = (heightDp * density).toInt()
                    FrameLayout(activity).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            hPx
                        )
                        (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                        addView(bannerView, FrameLayout.LayoutParams(wPx, hPx, Gravity.CENTER))
                    }
                },
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
            )
        }
    }
}

/**
 * Unified Dual-Network Bottom Banner Ad (Unity Ads + Start.io) controlled by Admin Panel.
 */
@Composable
fun ManagedBannerAd(
    adConfig: AdConfigEntity,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled || !adConfig.bannerEnabled) return

    val unityConfigured = UnityAdsManager.isConfigured(adConfig)
    var unityFailed by remember(adConfig.unityGameId, adConfig.unityBannerPlacement, adConfig.activeNetwork) {
        mutableStateOf(false)
    }
    var startIoFailed by remember(adConfig.startIoAppId, adConfig.activeNetwork) {
        mutableStateOf(false)
    }

    val useUnityNow = when (adConfig.activeNetwork) {
        "UNITY" -> unityConfigured && !unityFailed
        "STARTIO" -> false
        "BOTH_STARTIO_PRIMARY" -> startIoFailed && unityConfigured && !unityFailed
        else -> unityConfigured && !unityFailed // BOTH_UNITY_PRIMARY
    }

    val useStartIoNow = when (adConfig.activeNetwork) {
        "UNITY" -> false
        "STARTIO" -> true
        "BOTH_STARTIO_PRIMARY" -> !startIoFailed
        else -> (!unityConfigured || unityFailed) // BOTH_UNITY_PRIMARY fallback
    }

    if (useUnityNow) {
        Surface(
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = modifier
                .fillMaxWidth()
                .testTag("unity_banner_ad")
        ) {
            UnityBannerAdView(
                placementId = adConfig.unityBannerPlacement.ifBlank { "Banner_Android" },
                widthDp = 320,
                heightDp = 50,
                onFailed = { unityFailed = true }
            )
        }
    } else if (useStartIoNow) {
        StartIoBannerAd(
            modifier = modifier,
            onFailed = { startIoFailed = true }
        )
    }
}

/**
 * Unified Dual-Network Native Banner Ad Card for periodic inline placement inside feeds
 * ("Majhe majhe native ad show hobe").
 * Uses Start.io Native Ad or Unity Native/MREC Banner with automatic cross-network fallback.
 */
@Composable
fun ManagedNativeBannerAd(
    adConfig: AdConfigEntity,
    lang: AppLang,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled || !adConfig.nativeBannerEnabled) return

    val unityConfigured = UnityAdsManager.isConfigured(adConfig)
    var unityFailed by remember(adConfig.unityGameId, adConfig.unityNativeBannerPlacement, adConfig.activeNetwork) {
        mutableStateOf(false)
    }
    var unityLoaded by remember(adConfig.unityGameId, adConfig.unityNativeBannerPlacement, adConfig.activeNetwork) {
        mutableStateOf(false)
    }

    val preferUnity = when (adConfig.activeNetwork) {
        "UNITY" -> unityConfigured && !unityFailed
        "BOTH_UNITY_PRIMARY" -> unityConfigured && !unityFailed
        else -> false
    }

    if (preferUnity) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = modifier
                .fillMaxWidth()
                .testTag("unity_native_banner_ad")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = if (unityLoaded) 8.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (unityLoaded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "বিজ্ঞাপন • Sponsored", "Ad • Sponsored"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MedicalBlueDark
                        )
                        Box(
                            modifier = Modifier
                                .background(MedicalBlueLight, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MedicalBlueDark
                            )
                        }
                    }
                }
                UnityBannerAdView(
                    placementId = adConfig.unityNativeBannerPlacement.ifBlank { adConfig.unityBannerPlacement.ifBlank { "Banner_Android" } },
                    widthDp = 320,
                    heightDp = 50,
                    onLoaded = { unityLoaded = true },
                    onFailed = { unityFailed = true }
                )
            }
        }
    } else if (adConfig.activeNetwork != "UNITY") {
        StartIoNativeBannerCard(
            lang = lang,
            modifier = modifier
        )
    }
}

/**
 * Real App Open Ad controller (fires once shortly after Splash + on foreground resume)
 * with Admin-configurable cooldown (`appOpenCooldownSeconds`) and Unity <-> Start.io fallback.
 */
@Composable
fun AppOpenAdController(
    adConfig: AdConfigEntity,
    isReadyToShow: Boolean
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityCtx() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val unityReady by UnityAdsManager.isInitialized.collectAsStateWithLifecycle()
    val startIoReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()

    var lastShownAtMs by remember { mutableLongStateOf(0L) }
    var isShowing by remember { mutableStateOf(false) }
    var coldLaunchAttempted by remember { mutableStateOf(false) }

    fun attemptShowAppOpen(act: Activity) {
        if (!adConfig.adsEnabled || !adConfig.appOpenEnabled || isShowing) return
        val now = System.currentTimeMillis()
        val cooldownMs = adConfig.appOpenCooldownSeconds.coerceAtLeast(15) * 1000L
        if (lastShownAtMs > 0L && (now - lastShownAtMs) < cooldownMs) return

        val unityAvailable = UnityAdsManager.isConfigured(adConfig) && unityReady
        val startIoAvailable = adConfig.activeNetwork != "UNITY" && startIoReady
        if (!unityAvailable && !startIoAvailable) return

        isShowing = true
        val tryStartIo = {
            if (startIoAvailable) {
                StartIoConfig.showAppOpenAd(
                    activity = act,
                    onShown = { lastShownAtMs = System.currentTimeMillis() },
                    onDismissed = { isShowing = false },
                    onFailed = { isShowing = false }
                )
            } else {
                isShowing = false
            }
        }
        val tryUnity: (() -> Unit) -> Unit = { onUnityFail ->
            if (unityAvailable) {
                UnityAdsManager.loadAndShowInterstitial(
                    activity = act,
                    placementId = adConfig.unityAppOpenPlacement.ifBlank { "Interstitial_Android" },
                    onShown = { lastShownAtMs = System.currentTimeMillis() },
                    onDismissed = { isShowing = false },
                    onFailed = { onUnityFail() }
                )
            } else {
                onUnityFail()
            }
        }

        when (adConfig.activeNetwork) {
            "UNITY" -> tryUnity { isShowing = false }
            "STARTIO" -> tryStartIo()
            "BOTH_STARTIO_PRIMARY" -> {
                if (startIoAvailable) {
                    StartIoConfig.showAppOpenAd(
                        activity = act,
                        onShown = { lastShownAtMs = System.currentTimeMillis() },
                        onDismissed = { isShowing = false },
                        onFailed = { tryUnity { isShowing = false } }
                    )
                } else {
                    tryUnity { isShowing = false }
                }
            }
            else -> tryUnity { tryStartIo() } // BOTH_UNITY_PRIMARY
        }
    }

    // Cold launch App Open trigger right after Splash finishes & SDK initializes
    LaunchedEffect(isReadyToShow, unityReady, startIoReady, adConfig.adsEnabled, adConfig.appOpenEnabled) {
        if (!isReadyToShow || coldLaunchAttempted || activity == null) return@LaunchedEffect
        if (!adConfig.adsEnabled || !adConfig.appOpenEnabled) return@LaunchedEffect
        val anyReady = (UnityAdsManager.isConfigured(adConfig) && unityReady) ||
            (adConfig.activeNetwork != "UNITY" && startIoReady)
        if (!anyReady) return@LaunchedEffect
        coldLaunchAttempted = true
        delay(650L)
        attemptShowAppOpen(activity)
    }

    // Foreground resume App Open trigger respecting cooldown
    DisposableEffect(lifecycleOwner, activity, adConfig) {
        var wasBackgrounded = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasBackgrounded = true
                Lifecycle.Event.ON_START -> {
                    if (wasBackgrounded && isReadyToShow && activity != null) {
                        wasBackgrounded = false
                        attemptShowAppOpen(activity)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Unified Rewarded Video helper supporting both Unity Ads Rewarded & Start.io Rewarded
 * with automatic cross-network fallback based on Admin Panel `activeNetwork`.
 */
class UnifiedRewardedAdHelper(
    private val activity: Activity?,
    private val startIoHelper: StartIoRewardedHelper,
    private val adConfigProvider: () -> AdConfigEntity
) {
    val isReady: Boolean
        get() {
            val cfg = adConfigProvider()
            val unityReady = UnityAdsManager.isConfigured(cfg) && UnityAdsManager.isInitialized.value
            return when (cfg.activeNetwork) {
                "UNITY" -> unityReady
                "STARTIO" -> startIoHelper.isReady
                else -> unityReady || startIoHelper.isReady
            }
        }

    val isLoading: Boolean
        get() = startIoHelper.isLoading

    fun show(
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val cfg = adConfigProvider()
        if (!cfg.adsEnabled || !cfg.rewardedEnabled) {
            onUnavailable()
            return
        }
        val act = activity ?: run {
            onUnavailable()
            return
        }
        val unityReady = UnityAdsManager.isConfigured(cfg) && UnityAdsManager.isInitialized.value
        val placement = cfg.unityRewardedPlacement.ifBlank { "Rewarded_Android" }

        when (cfg.activeNetwork) {
            "UNITY" -> {
                if (unityReady) {
                    UnityAdsManager.loadAndShowRewarded(act, placement, onRewarded, onUnavailable)
                } else {
                    onUnavailable()
                }
            }
            "STARTIO" -> startIoHelper.show(onRewarded, onUnavailable)
            "BOTH_STARTIO_PRIMARY" -> {
                startIoHelper.show(
                    onRewarded = onRewarded,
                    onUnavailable = {
                        if (unityReady) {
                            UnityAdsManager.loadAndShowRewarded(act, placement, onRewarded, onUnavailable)
                        } else {
                            onUnavailable()
                        }
                    }
                )
            }
            else -> { // BOTH_UNITY_PRIMARY
                if (unityReady) {
                    UnityAdsManager.loadAndShowRewarded(
                        activity = act,
                        placementId = placement,
                        onRewarded = onRewarded,
                        onFailed = { startIoHelper.show(onRewarded, onUnavailable) }
                    )
                } else {
                    startIoHelper.show(onRewarded, onUnavailable)
                }
            }
        }
    }
}

@Composable
fun rememberUnifiedRewardedHelper(adConfig: AdConfigEntity): UnifiedRewardedAdHelper {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityCtx() }
    val startIoHelper = rememberStartIoRewardedHelper()
    val latestConfig by rememberUpdatedState(adConfig)
    return remember(activity, startIoHelper) {
        UnifiedRewardedAdHelper(
            activity = activity,
            startIoHelper = startIoHelper,
            adConfigProvider = { latestConfig }
        )
    }
}
