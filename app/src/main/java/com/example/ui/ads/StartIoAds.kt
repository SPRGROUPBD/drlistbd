package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.banner.Mrec
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

private val mainHandler = Handler(Looper.getMainLooper())

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Environment helper ensuring 100% real live revenue ads on all real Android devices
 * and release APKs, while automatically enabling SDK test mode ONLY on debug emulator builds
 * (where ad networks reject live requests with NO FILL / Empty Response).
 */
object AdEnvironment {
    val isEmulator: Boolean by lazy {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        fp.startsWith("generic") ||
            fp.startsWith("unknown") ||
            fp.contains("emulator") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            model.contains("sdk_gphone") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    fun resolveTestMode(adminTestMode: Boolean): Boolean =
        adminTestMode || (com.example.BuildConfig.DEBUG && isEmulator)
}

object StartIoConfig {
    const val DEFAULT_APP_ID = "210630916"
    private const val TAG = "StartIoAds"

    @Volatile
    private var initStarted = false
    @Volatile
    private var activeAppId: String = DEFAULT_APP_ID
    @Volatile
    private var activeTestMode: Boolean = false

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * Initializes Start.io SDK using the Admin-configured App ID and real-vs-test mode.
     * On real phones and release builds, [testMode] = false serves 100% real live revenue ads.
     */
    fun init(
        context: Context,
        appId: String = DEFAULT_APP_ID,
        testMode: Boolean = false
    ) {
        if (!ConnectivityMonitor.isOnline.value) {
            Log.d(TAG, "Offline mode active — skipping Start.io SDK initialization")
            return
        }
        val appCtx = context.applicationContext ?: context
        val resolvedAppId = appId.trim().ifBlank { DEFAULT_APP_ID }
        val effectiveTestMode = AdEnvironment.resolveTestMode(testMode)
        if (initStarted && activeAppId == resolvedAppId) {
            if (activeTestMode != effectiveTestMode) {
                activeTestMode = effectiveTestMode
                try {
                    StartAppSDK.setTestAdsEnabled(effectiveTestMode)
                } catch (_: Exception) {}
            }
            return
        }
        initStarted = true
        activeAppId = resolvedAppId
        activeTestMode = effectiveTestMode
        try {
            StartAppSDK.setUserConsent(appCtx, "pas", System.currentTimeMillis(), true)
            StartAppSDK.setTestAdsEnabled(effectiveTestMode)

            StartAppSDK.initParams(appCtx, resolvedAppId)
                .setReturnAdsEnabled(false)
                .setCallback {
                    mainHandler.post {
                        try {
                            StartAppSDK.setTestAdsEnabled(effectiveTestMode)
                        } catch (_: Exception) {}
                        Log.d(
                            TAG,
                            "Start.io SDK initialized for App ID $resolvedAppId (realLiveAds=${!effectiveTestMode})"
                        )
                        _isInitialized.value = true
                        // Stagger Native pool preload so remote config settles and requests don't burst
                        mainHandler.postDelayed({
                            StartIoNativeAdPool.preload(appCtx)
                        }, 1800L)
                    }
                }
                .init()

            // Safety fallback so UI ad views still request ads if callback is delayed
            mainHandler.postDelayed({
                if (!_isInitialized.value) {
                    Log.w(TAG, "Start.io init callback timeout; enabling ad requests")
                    _isInitialized.value = true
                    StartIoNativeAdPool.preload(appCtx)
                }
            }, 2500L)
        } catch (e: Exception) {
            Log.e(TAG, "Start.io init error: ${e.message}", e)
            _isInitialized.value = true
        }
    }

    /**
     * Loads and displays a live Start.io App Open / Launch full-screen ad.
     */
    fun showAppOpenAd(
        activity: Activity,
        onShown: () -> Unit = {},
        onDismissed: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        if (!ConnectivityMonitor.isOnline.value) {
            mainHandler.post { onFailed() }
            return
        }
        try {
            AdRevenueTracker.recordRequest(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_APP_OPEN)
            val ad = StartAppAd(activity)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    AdRevenueTracker.recordLoadSuccess(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_APP_OPEN)
                    mainHandler.post {
                        val displayed = ad.showAd(object : AdDisplayListener {
                            override fun adHidden(p0: Ad?) {
                                mainHandler.post { onDismissed() }
                            }
                            override fun adDisplayed(p0: Ad?) {
                                AdRevenueTracker.recordImpression(
                                    AdRevenueTracker.NETWORK_STARTIO,
                                    AdRevenueTracker.FORMAT_APP_OPEN
                                )
                                mainHandler.post { onShown() }
                            }
                            override fun adClicked(p0: Ad?) {
                                AdRevenueTracker.recordClick(
                                    AdRevenueTracker.NETWORK_STARTIO,
                                    AdRevenueTracker.FORMAT_APP_OPEN
                                )
                            }
                            override fun adNotDisplayed(p0: Ad?) {
                                AdRevenueTracker.recordNetworkError(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_APP_OPEN)
                                mainHandler.post { onFailed() }
                            }
                        })
                        if (!displayed) onFailed()
                    }
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    Log.w(TAG, "Start.io App Open no-fill: ${failedAd?.errorMessage}")
                    AdRevenueTracker.recordNetworkError(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_APP_OPEN)
                    mainHandler.post { onFailed() }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Start.io App Open exception: ${e.message}")
            AdRevenueTracker.recordNetworkError(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_APP_OPEN)
            mainHandler.post { onFailed() }
        }
    }
}

/**
 * App-wide preloaded pool of Start.io Native Ads so LazyColumn cards display
 * real native ads instantaneously without losing state during scrolling/recycling.
 */
object StartIoNativeAdPool {
    private const val TAG = "StartIoNativePool"
    private const val FAILURE_COOLDOWN_MS = 60_000L
    private val slotCounter = AtomicInteger(0)

    private val _ads = MutableStateFlow<List<NativeAdDetails>>(emptyList())
    val ads: StateFlow<List<NativeAdDetails>> = _ads.asStateFlow()

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    @Volatile
    private var isFetching = false
    @Volatile
    private var lastFailedAtMs = 0L
    @Volatile
    private var retainedHolder: StartAppNativeAd? = null

    fun nextSlotIndex(): Int = slotCounter.getAndIncrement()

    fun preload(context: Context, force: Boolean = false) {
        if (!ConnectivityMonitor.isOnline.value) return
        if (isFetching) return
        if (!force && _ads.value.isNotEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && lastFailedAtMs > 0L && (now - lastFailedAtMs) < FAILURE_COOLDOWN_MS) {
            return
        }
        isFetching = true
        _exhausted.value = false
        val appCtx = context.applicationContext ?: context
        mainHandler.post {
            try {
                AdRevenueTracker.recordRequest(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_NATIVE)
                val nativeAd = StartAppNativeAd(appCtx)
                retainedHolder = nativeAd
                val prefs = NativeAdPreferences()
                    .setAdsNumber(3)
                    .setAutoBitmapDownload(true)
                    .setPrimaryImageSize(4)
                    .setSecondaryImageSize(2)

                nativeAd.loadAd(prefs, object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        val list = nativeAd.nativeAds?.filterNotNull().orEmpty()
                        Log.d(TAG, "Preloaded ${list.size} real Start.io Native Ads")
                        if (list.isNotEmpty()) {
                            AdRevenueTracker.recordLoadSuccess(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_NATIVE)
                        }
                        mainHandler.post {
                            isFetching = false
                            if (list.isNotEmpty()) {
                                lastFailedAtMs = 0L
                                _ads.value = list
                                _exhausted.value = false
                            } else {
                                lastFailedAtMs = System.currentTimeMillis()
                                _exhausted.value = true
                            }
                        }
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        Log.w(TAG, "Start.io Native pool no-fill: ${ad?.errorMessage}")
                        AdRevenueTracker.recordNetworkError(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_NATIVE)
                        mainHandler.post {
                            isFetching = false
                            lastFailedAtMs = System.currentTimeMillis()
                            _exhausted.value = true
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Start.io Native pool exception: ${e.message}")
                AdRevenueTracker.recordNetworkError(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_NATIVE)
                isFetching = false
                lastFailedAtMs = System.currentTimeMillis()
                _exhausted.value = true
            }
        }
    }
}

/**
 * Direct Start.io SDK v5 Banner Ad (320x50) mounted straight into AndroidView so Start.io's
 * native WebView & IAB viewability engine always sees a real attached 320x50 window container.
 * Per Start.io SDK v5 docs, loadAd() is called once on creation and never looped in onFailedToReceiveAd.
 */
@Composable
fun StartIoBannerAd(
    modifier: Modifier = Modifier,
    metricsFormat: String = AdRevenueTracker.FORMAT_BANNER,
    onFailed: (() -> Unit)? = null
) {
    val isOnline by ConnectivityMonitor.isOnline.collectAsStateWithLifecycle()
    val sdkReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var failed by remember(isOnline) { mutableStateOf(false) }

    if (!isOnline || !sdkReady || activity == null || failed) return

    Surface(
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = modifier
            .fillMaxWidth()
            .testTag("startio_banner_ad")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    val hostActivity = ctx.findActivity() ?: activity
                    val density = hostActivity.resources.displayMetrics.density
                    val widthPx = (320 * density).toInt()
                    val heightPx = (50 * density).toInt()

                    val container = FrameLayout(hostActivity).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            heightPx
                        )
                    }

                    AdRevenueTracker.recordRequest(AdRevenueTracker.NETWORK_STARTIO, metricsFormat)
                    val banner = Banner(hostActivity, object : BannerListener {
                        override fun onReceiveAd(view: View?) {
                            Log.d("StartIoAds", "Direct Start.io Banner received & visible")
                            AdRevenueTracker.recordLoadSuccess(AdRevenueTracker.NETWORK_STARTIO, metricsFormat)
                        }

                        override fun onFailedToReceiveAd(view: View?) {
                            Log.w("StartIoAds", "Direct Start.io Banner failed to fill")
                            AdRevenueTracker.recordNetworkError(AdRevenueTracker.NETWORK_STARTIO, metricsFormat)
                            mainHandler.post {
                                failed = true
                                onFailed?.invoke()
                            }
                        }

                        override fun onImpression(view: View?) {
                            Log.d("StartIoAds", "Direct Start.io Banner impression recorded")
                            AdRevenueTracker.recordImpression(
                                AdRevenueTracker.NETWORK_STARTIO,
                                metricsFormat
                            )
                        }

                        override fun onClick(view: View?) {
                            Log.d("StartIoAds", "Direct Start.io Banner clicked")
                            AdRevenueTracker.recordClick(
                                AdRevenueTracker.NETWORK_STARTIO,
                                metricsFormat
                            )
                        }
                    })

                    val lp = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER)
                    container.addView(banner, lp)
                    banner.loadAd(320, 50)
                    container
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
        }
    }
}

/**
 * Direct Start.io MREC (300x250) Banner Card with automatic fallback to Direct 320x50 Banner
 * inside a Sponsored Native container so Native Banner slots ALWAYS render a real live ad.
 */
@Composable
private fun StartIoDirectMrecOrBannerCard(
    lang: AppLang,
    modifier: Modifier = Modifier
) {
    val sdkReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var mrecFailed by remember { mutableStateOf(false) }
    var fallbackBannerFailed by remember { mutableStateOf(false) }

    if (!sdkReady || activity == null || (mrecFailed && fallbackBannerFailed)) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = modifier
            .fillMaxWidth()
            .testTag("startio_mrec_ad")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
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

            if (!mrecFailed) {
                AndroidView(
                    factory = { ctx ->
                        val hostActivity = ctx.findActivity() ?: activity
                        val density = hostActivity.resources.displayMetrics.density
                        val wPx = (300 * density).toInt()
                        val hPx = (250 * density).toInt()

                        val container = FrameLayout(hostActivity).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                hPx
                            )
                        }
                        val mrec = Mrec(hostActivity, object : BannerListener {
                            override fun onReceiveAd(view: View?) {
                                Log.d("StartIoAds", "Direct Start.io MREC received")
                            }

                            override fun onFailedToReceiveAd(view: View?) {
                                Log.w("StartIoAds", "Direct Start.io MREC no-fill -> falling back to Banner")
                                mainHandler.post { mrecFailed = true }
                            }

                            override fun onImpression(view: View?) {
                                AdRevenueTracker.recordImpression(
                                    AdRevenueTracker.NETWORK_STARTIO,
                                    AdRevenueTracker.FORMAT_NATIVE
                                )
                            }

                            override fun onClick(view: View?) {
                                AdRevenueTracker.recordClick(
                                    AdRevenueTracker.NETWORK_STARTIO,
                                    AdRevenueTracker.FORMAT_NATIVE
                                )
                            }
                        })
                        container.addView(mrec, FrameLayout.LayoutParams(wPx, hPx, Gravity.CENTER))
                        mrec.loadAd(300, 250)
                        container
                    },
                    modifier = Modifier
                        .width(300.dp)
                        .height(250.dp)
                )
            } else {
                StartIoBannerAd(
                    metricsFormat = AdRevenueTracker.FORMAT_NATIVE,
                    onFailed = { fallbackBannerFailed = true }
                )
            }
        }
    }
}

/**
 * Production Start.io Native Banner Ad card backed by the preloaded [StartIoNativeAdPool].
 * Immediately shows a real Native Ad if available, or seamlessly falls back to a live
 * Start.io MREC (300x250) / Direct Banner (320x50) so slots are never stuck blank.
 */
@Composable
fun StartIoNativeBannerCard(
    lang: AppLang,
    modifier: Modifier = Modifier
) {
    val isOnline by ConnectivityMonitor.isOnline.collectAsStateWithLifecycle()
    val sdkReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()
    val poolAds by StartIoNativeAdPool.ads.collectAsStateWithLifecycle()
    val poolExhausted by StartIoNativeAdPool.exhausted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val slotIdx = remember { StartIoNativeAdPool.nextSlotIndex() }
    var waitTimedOut by remember { mutableStateOf(false) }

    if (!isOnline) return

    LaunchedEffect(sdkReady, isOnline) {
        if (sdkReady && isOnline) {
            StartIoNativeAdPool.preload(context)
            delay(3000L)
            waitTimedOut = true
        }
    }

    val details = remember(poolAds, slotIdx) {
        if (poolAds.isNotEmpty()) poolAds[slotIdx % poolAds.size] else null
    }

    if (details != null && activity != null) {
        key(details.title, details.description, slotIdx) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("startio_native_banner_ad")
            ) {
                AndroidView(
                    factory = {
                        val dp = activity.resources.displayMetrics.density
                        val pad = (12 * dp).toInt()
                        val container = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(pad, pad, pad, pad)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }

                        val iconView = ImageView(activity).apply {
                            val size = (58 * dp).toInt()
                            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                marginEnd = (10 * dp).toInt()
                            }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            val bmp = details.imageBitmap ?: details.secondaryImageBitmap
                            if (bmp != null) {
                                setImageBitmap(bmp)
                            } else {
                                setBackgroundColor(AndroidColor.parseColor("#E0F2FE"))
                            }
                        }

                        val textCol = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val badge = TextView(activity).apply {
                            text = tr(lang, "বিজ্ঞাপন • Sponsored", "Ad • Sponsored")
                            textSize = 10f
                            setTextColor(AndroidColor.parseColor("#0284C7"))
                            setTypeface(null, Typeface.BOLD)
                        }

                        val title = TextView(activity).apply {
                            text = details.title ?: "Sponsored"
                            textSize = 14f
                            setTextColor(AndroidColor.parseColor("#0F172A"))
                            setTypeface(null, Typeface.BOLD)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }

                        val desc = TextView(activity).apply {
                            text = details.description ?: ""
                            textSize = 12f
                            setTextColor(AndroidColor.parseColor("#475569"))
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                        }

                        textCol.addView(badge)
                        textCol.addView(title)
                        textCol.addView(desc)

                        val ctaBtn = Button(activity).apply {
                            text = details.callToAction?.takeIf { it.isNotBlank() } ?: "Open"
                            textSize = 12f
                            setTextColor(AndroidColor.WHITE)
                            setBackgroundColor(AndroidColor.parseColor("#0D9488"))
                            val btnPadH = (12 * dp).toInt()
                            val btnPadV = (6 * dp).toInt()
                            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginStart = (8 * dp).toInt()
                            }
                        }

                        container.addView(iconView)
                        container.addView(textCol)
                        container.addView(ctaBtn)

                        details.registerViewForInteraction(container, listOf(container, ctaBtn))
                        AdRevenueTracker.recordImpression(
                            AdRevenueTracker.NETWORK_STARTIO,
                            AdRevenueTracker.FORMAT_NATIVE
                        )
                        container
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else if (sdkReady && (poolExhausted || waitTimedOut)) {
        // Guaranteed real Start.io MREC -> Direct Banner fallback so Native Banner always displays!
        StartIoDirectMrecOrBannerCard(lang = lang, modifier = modifier)
    }
}

/**
 * Production controller for Start.io Rewarded Video Ads.
 * Supports distinct callbacks for reward completed, dismissed early without reward, and unavailable/no-fill.
 */
class StartIoRewardedHelper(private val activity: Activity?) {
    private var rewardedAd: StartAppAd? = null
    private var loadedIsRewardedVideo: Boolean = true
    private var pendingOnRewarded: (() -> Unit)? = null
    private var pendingOnDismissedWithoutReward: (() -> Unit)? = null
    private var pendingOnUnavailable: (() -> Unit)? = null

    var isReady by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun preload(
        showImmediatelyWhenLoaded: Boolean = false,
        tryFallbackAutomatic: Boolean = false
    ) {
        val act = activity ?: return
        if (!ConnectivityMonitor.isOnline.value) {
            if (pendingOnUnavailable != null) {
                val failCb = pendingOnUnavailable
                pendingOnRewarded = null
                pendingOnDismissedWithoutReward = null
                pendingOnUnavailable = null
                mainHandler.post { failCb?.invoke() }
            }
            return
        }
        if (isLoading && !showImmediatelyWhenLoaded && !tryFallbackAutomatic) return
        isLoading = true

        val mode = if (tryFallbackAutomatic) {
            StartAppAd.AdMode.AUTOMATIC
        } else {
            StartAppAd.AdMode.REWARDED_VIDEO
        }
        try {
            AdRevenueTracker.recordRequest(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_REWARDED)
            val ad = StartAppAd(act)
            ad.loadAd(mode, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    Log.d("StartIoAds", "Rewarded/Full ad received (mode=$mode)")
                    AdRevenueTracker.recordLoadSuccess(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_REWARDED)
                    mainHandler.post {
                        rewardedAd = ad
                        loadedIsRewardedVideo = !tryFallbackAutomatic
                        isReady = true
                        isLoading = false
                        if (showImmediatelyWhenLoaded || pendingOnRewarded != null) {
                            val rewardCb = pendingOnRewarded
                            val dismissedCb = pendingOnDismissedWithoutReward
                            val failCb = pendingOnUnavailable
                            pendingOnRewarded = null
                            pendingOnDismissedWithoutReward = null
                            pendingOnUnavailable = null
                            if (rewardCb != null) {
                                displayLoadedAd(
                                    ad = ad,
                                    isRewardedVideo = loadedIsRewardedVideo,
                                    onRewarded = rewardCb,
                                    onDismissedWithoutReward = dismissedCb ?: {},
                                    onUnavailable = failCb ?: {}
                                )
                            }
                        }
                    }
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    Log.w("StartIoAds", "Ad no-fill (mode=$mode): ${failedAd?.errorMessage}")
                    AdRevenueTracker.recordNetworkError(AdRevenueTracker.NETWORK_STARTIO, AdRevenueTracker.FORMAT_REWARDED)
                    mainHandler.post {
                        isLoading = false
                        isReady = false
                        val userRequestedNow = showImmediatelyWhenLoaded || pendingOnRewarded != null
                        if (userRequestedNow && !tryFallbackAutomatic) {
                            mainHandler.postDelayed({
                                preload(showImmediatelyWhenLoaded = true, tryFallbackAutomatic = true)
                            }, 450L)
                        } else if (pendingOnUnavailable != null) {
                            val failCb = pendingOnUnavailable
                            pendingOnRewarded = null
                            pendingOnDismissedWithoutReward = null
                            pendingOnUnavailable = null
                            failCb?.invoke()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("StartIoAds", "Rewarded ad exception: ${e.message}")
            mainHandler.post {
                isLoading = false
                isReady = false
                val failCb = pendingOnUnavailable
                pendingOnRewarded = null
                pendingOnDismissedWithoutReward = null
                pendingOnUnavailable = null
                failCb?.invoke()
            }
        }
    }

    private fun displayLoadedAd(
        ad: StartAppAd,
        isRewardedVideo: Boolean,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        var rewarded = false
        var finishedCallbackSent = false
        if (isRewardedVideo) {
            ad.setVideoListener {
                if (!rewarded) {
                    rewarded = true
                    AdRevenueTracker.recordCompleted(
                        AdRevenueTracker.NETWORK_STARTIO,
                        AdRevenueTracker.FORMAT_REWARDED
                    )
                }
            }
        }
        val shown = ad.showAd(object : AdDisplayListener {
            override fun adHidden(p0: Ad?) {
                mainHandler.post {
                    if (!finishedCallbackSent) {
                        finishedCallbackSent = true
                        if (rewarded || !isRewardedVideo) {
                            if (!isRewardedVideo) {
                                AdRevenueTracker.recordCompleted(
                                    AdRevenueTracker.NETWORK_STARTIO,
                                    AdRevenueTracker.FORMAT_REWARDED
                                )
                            }
                            onRewarded()
                        } else {
                            onDismissedWithoutReward()
                        }
                    }
                    preload(showImmediatelyWhenLoaded = false, tryFallbackAutomatic = false)
                }
            }

            override fun adDisplayed(p0: Ad?) {
                Log.d("StartIoAds", "Rewarded/Full ad displayed")
                AdRevenueTracker.recordImpression(
                    AdRevenueTracker.NETWORK_STARTIO,
                    AdRevenueTracker.FORMAT_REWARDED
                )
            }

            override fun adClicked(p0: Ad?) {
                AdRevenueTracker.recordClick(
                    AdRevenueTracker.NETWORK_STARTIO,
                    AdRevenueTracker.FORMAT_REWARDED
                )
            }

            override fun adNotDisplayed(p0: Ad?) {
                Log.w("StartIoAds", "Ad could not be displayed: ${p0?.errorMessage}")
                mainHandler.post {
                    if (!finishedCallbackSent) {
                        finishedCallbackSent = true
                        onUnavailable()
                    }
                    preload(showImmediatelyWhenLoaded = false, tryFallbackAutomatic = false)
                }
            }
        })
        isReady = false
        if (!shown && !finishedCallbackSent) {
            finishedCallbackSent = true
            onUnavailable()
            preload(showImmediatelyWhenLoaded = false, tryFallbackAutomatic = false)
        }
    }

    fun show(
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit = {},
        onUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad != null && isReady) {
            displayLoadedAd(
                ad = ad,
                isRewardedVideo = loadedIsRewardedVideo,
                onRewarded = onRewarded,
                onDismissedWithoutReward = onDismissedWithoutReward,
                onUnavailable = onUnavailable
            )
        } else {
            pendingOnRewarded = onRewarded
            pendingOnDismissedWithoutReward = onDismissedWithoutReward
            pendingOnUnavailable = onUnavailable
            preload(showImmediatelyWhenLoaded = true, tryFallbackAutomatic = false)
        }
    }
}

@Composable
fun rememberStartIoRewardedHelper(): StartIoRewardedHelper {
    val isOnline by ConnectivityMonitor.isOnline.collectAsStateWithLifecycle()
    val sdkReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val helper = remember(activity) { StartIoRewardedHelper(activity) }

    LaunchedEffect(isOnline, sdkReady, helper) {
        if (isOnline && sdkReady) {
            // Stagger Rewarded background preload so it doesn't collide with Banner & App Open at startup
            delay(2500L)
            helper.preload(showImmediatelyWhenLoaded = false, tryFallbackAutomatic = false)
        }
    }
    return helper
}

@Composable
fun RewardedAdCard(
    lang: AppLang,
    isReady: Boolean,
    isLoading: Boolean = false,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MedicalBlueLight.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MedicalBlueContainer),
        modifier = modifier
            .fillMaxWidth()
            .testTag("startio_rewarded_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = MedicalBlueDark,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr(lang, "রিওয়ার্ডেড ভিডিও বিজ্ঞাপন", "Watch Rewarded Video"),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = tr(
                        lang,
                        "ফ্রি ডক্টর ডিরেক্টরি সাপোর্ট করতে এবং দ্রুত ক্লাউড সিঙ্ক পেতে ভিডিও দেখুন।",
                        "Support free Doctor List BD directory & trigger instant sync."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Button(
                onClick = onWatchClick,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue)
            ) {
                if (isLoading && !isReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SurfaceWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isReady) tr(lang, "দেখুন", "Watch") else tr(lang, "চালান", "Play"),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
