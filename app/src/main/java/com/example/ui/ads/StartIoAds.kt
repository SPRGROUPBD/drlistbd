package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object StartIoConfig {
    const val APP_ID = "210630916"
    private const val TAG = "StartIoAds"

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    fun init(activity: Activity) {
        if (_isInitialized.value) return
        try {
            // Production mode: always serve REAL Start.io live ads for App ID 210630916
            StartAppSDK.setTestAdsEnabled(false)
            StartAppSDK.setUserConsent(activity, "pas", System.currentTimeMillis(), true)

            StartAppSDK.initParams(activity, APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback {
                    StartAppSDK.setTestAdsEnabled(false)
                    Log.d(TAG, "Start.io SDK initialized in LIVE production mode for App ID $APP_ID")
                    _isInitialized.value = true
                }
                .init()

            // Safety fallback so UI ad views still request ads if callback is delayed
            Handler(Looper.getMainLooper()).postDelayed({
                if (!_isInitialized.value) {
                    Log.w(TAG, "Start.io init callback timeout; enabling ad requests anyway")
                    _isInitialized.value = true
                }
            }, 2200L)
        } catch (e: Exception) {
            Log.e(TAG, "Start.io init error: ${e.message}", e)
            _isInitialized.value = true
        }
    }
}

/**
 * Start.io Banner Ad (320x50) properly attached to an Activity-backed FrameLayout,
 * explicitly calling loadAd(320, 50) + showBanner() after SDK initialization.
 */
@Composable
fun StartIoBannerAd(modifier: Modifier = Modifier) {
    val sdkReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    if (!sdkReady || activity == null) return

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
                factory = {
                    val density = activity.resources.displayMetrics.density
                    val widthPx = (320 * density).toInt()
                    val heightPx = (50 * density).toInt()

                    val container = FrameLayout(activity).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            heightPx
                        )
                    }

                    val banner = Banner(activity, object : BannerListener {
                        override fun onReceiveAd(view: View?) {
                            Log.d("StartIoAds", "Banner ad received")
                            view?.visibility = View.VISIBLE
                        }

                        override fun onFailedToReceiveAd(view: View?) {
                            Log.w("StartIoAds", "Banner ad failed to load; SDK will auto-retry")
                        }

                        override fun onImpression(view: View?) {
                            Log.d("StartIoAds", "Banner impression recorded")
                        }

                        override fun onClick(view: View?) {
                            Log.d("StartIoAds", "Banner clicked")
                        }
                    })

                    val bannerLp = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER)
                    container.addView(banner, bannerLp)
                    // Post loadAd so the Banner is attached to the Activity window first
                    container.post {
                        try {
                            banner.loadAd(320, 50)
                            banner.showBanner()
                        } catch (e: Exception) {
                            Log.e("StartIoAds", "Banner loadAd exception: ${e.message}")
                        }
                    }
                    container
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
        }
    }
}

/**
 * Start.io Native Banner Ad card loaded via StartAppNativeAd (using Activity context)
 * once SDK is initialized, with automatic retry & live Banner fallback if native fill is empty.
 */
@Composable
fun StartIoNativeBannerCard(
    lang: AppLang,
    modifier: Modifier = Modifier
) {
    val sdkReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var activeNativeAd by remember { mutableStateOf<StartAppNativeAd?>(null) }
    var adDetails by remember { mutableStateOf<NativeAdDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var retryTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(sdkReady, activity, retryTick) {
        if (!sdkReady || activity == null) return@LaunchedEffect
        isLoading = true
        try {
            val nativeAd = StartAppNativeAd(activity)
            activeNativeAd = nativeAd
            val prefs = NativeAdPreferences()
                .setAdsNumber(1)
                .setAutoBitmapDownload(true)
                .setPrimaryImageSize(2)

            nativeAd.loadAd(prefs, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    val list = nativeAd.nativeAds
                    val first = list?.firstOrNull()
                    Log.d("StartIoAds", "Native ad received: ${first?.title}")
                    adDetails = first
                    isLoading = false
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w("StartIoAds", "Native ad failed: ${ad?.errorMessage}")
                    isLoading = false
                }
            })
        } catch (e: Exception) {
            Log.e("StartIoAds", "Native ad exception: ${e.message}")
            isLoading = false
        }
    }

    // One automatic retry after 4 seconds if initial cold-start native call returned empty
    LaunchedEffect(isLoading, adDetails, sdkReady) {
        if (sdkReady && !isLoading && adDetails == null && retryTick == 0) {
            delay(4000L)
            retryTick = 1
        }
    }

    val details = adDetails
    if (details != null && activity != null) {
        key(details.title, details.description) {
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
                            val size = (54 * dp).toInt()
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
                        container
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else if (!isLoading && sdkReady) {
        // Fallback to a live Start.io Banner inside the native slot if native inventory is empty
        StartIoBannerAd(modifier = modifier)
    }
}

/**
 * Controller for Start.io Rewarded Video Ads (with Activity context, SDK-ready gating,
 * load-on-demand auto-display, and fallback ad mode if rewarded video inventory is empty).
 */
class StartIoRewardedHelper(private val activity: Activity?) {
    private var rewardedAd: StartAppAd? = null
    private var loadedIsRewardedVideo: Boolean = true
    private var pendingOnRewarded: (() -> Unit)? = null
    private var pendingOnUnavailable: (() -> Unit)? = null

    var isReady by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun preload(showImmediatelyWhenLoaded: Boolean = false, tryFallbackInterstitial: Boolean = false) {
        val act = activity ?: return
        if (isLoading && !showImmediatelyWhenLoaded) return
        isLoading = true

        val mode = if (tryFallbackInterstitial) StartAppAd.AdMode.AUTOMATIC else StartAppAd.AdMode.REWARDED_VIDEO
        try {
            val ad = StartAppAd(act)
            ad.loadAd(mode, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    Log.d("StartIoAds", "Rewarded/Full ad received (mode=$mode)")
                    rewardedAd = ad
                    loadedIsRewardedVideo = !tryFallbackInterstitial
                    isReady = true
                    isLoading = false
                    if (showImmediatelyWhenLoaded || pendingOnRewarded != null) {
                        val rewardCb = pendingOnRewarded
                        val failCb = pendingOnUnavailable
                        pendingOnRewarded = null
                        pendingOnUnavailable = null
                        if (rewardCb != null) {
                            displayLoadedAd(ad, isRewardedVideo = loadedIsRewardedVideo, onRewarded = rewardCb, onUnavailable = failCb ?: {})
                        }
                    }
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    Log.w("StartIoAds", "Rewarded ad failed (mode=$mode): ${failedAd?.errorMessage}")
                    isLoading = false
                    isReady = false
                    if (!tryFallbackInterstitial && (showImmediatelyWhenLoaded || pendingOnRewarded != null)) {
                        // Fall back to standard StartAppAd video/interstitial so user still sees an ad
                        preload(showImmediatelyWhenLoaded = true, tryFallbackInterstitial = true)
                    } else if (pendingOnUnavailable != null) {
                        val failCb = pendingOnUnavailable
                        pendingOnRewarded = null
                        pendingOnUnavailable = null
                        failCb?.invoke()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("StartIoAds", "Rewarded ad exception: ${e.message}")
            isLoading = false
            isReady = false
        }
    }

    private fun displayLoadedAd(
        ad: StartAppAd,
        isRewardedVideo: Boolean,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        var rewarded = false
        if (isRewardedVideo) {
            ad.setVideoListener {
                rewarded = true
                onRewarded()
            }
        }
        val shown = ad.showAd(object : AdDisplayListener {
            override fun adHidden(p0: Ad?) {
                if (!isRewardedVideo && !rewarded) {
                    rewarded = true
                    onRewarded()
                }
                preload(showImmediatelyWhenLoaded = false)
            }

            override fun adDisplayed(p0: Ad?) {
                Log.d("StartIoAds", "Rewarded ad displayed")
            }

            override fun adClicked(p0: Ad?) {}

            override fun adNotDisplayed(p0: Ad?) {
                Log.w("StartIoAds", "Rewarded ad could not be displayed: ${p0?.errorMessage}")
                onUnavailable()
                preload(showImmediatelyWhenLoaded = false)
            }
        })
        isReady = false
        if (!shown) {
            onUnavailable()
            preload(showImmediatelyWhenLoaded = false)
        }
    }

    fun show(
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad != null && isReady) {
            displayLoadedAd(ad, isRewardedVideo = loadedIsRewardedVideo, onRewarded = onRewarded, onUnavailable = onUnavailable)
        } else {
            pendingOnRewarded = onRewarded
            pendingOnUnavailable = onUnavailable
            preload(showImmediatelyWhenLoaded = true, tryFallbackInterstitial = false)
        }
    }
}

@Composable
fun rememberStartIoRewardedHelper(): StartIoRewardedHelper {
    val sdkReady by StartIoConfig.isInitialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val helper = remember(activity) { StartIoRewardedHelper(activity) }

    LaunchedEffect(sdkReady, helper) {
        if (sdkReady) {
            helper.preload(showImmediatelyWhenLoaded = false)
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
