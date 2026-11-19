package com.example.ui.ads

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
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
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

object StartIoConfig {
    const val APP_ID = "210630916"
    private const val TAG = "StartIoAds"

    fun init(context: Context) {
        try {
            StartAppSDK.initParams(context.applicationContext, APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback {
                    Log.d(TAG, "Start.io SDK initialized for App ID $APP_ID")
                }
                .init()
            StartAppAd.disableSplash()
        } catch (e: Exception) {
            Log.w(TAG, "Start.io init error: ${e.message}")
        }
    }
}

/**
 * Standard Start.io Banner Ad anchored cleanly above bottom navigation.
 */
@Composable
fun StartIoBannerAd(modifier: Modifier = Modifier) {
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
                .heightIn(min = 52.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    Banner(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Start.io Native Banner Ad card loaded via StartAppNativeAd and bound via registerViewForInteraction.
 */
@Composable
fun StartIoNativeBannerCard(
    lang: AppLang,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var adDetails by remember { mutableStateOf<NativeAdDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val nativeAd = StartAppNativeAd(context)
        val prefs = NativeAdPreferences()
            .setAdsNumber(1)
            .setAutoBitmapDownload(true)
            .setPrimaryImageSize(2)

        nativeAd.loadAd(prefs, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                val list = nativeAd.nativeAds
                adDetails = list?.firstOrNull()
                isLoading = false
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                isLoading = false
            }
        })
        onDispose {}
    }

    val details = adDetails
    if (details != null) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = modifier
                .fillMaxWidth()
                .testTag("startio_native_banner_ad")
        ) {
            AndroidView(
                factory = { ctx ->
                    val dp = ctx.resources.displayMetrics.density
                    val pad = (12 * dp).toInt()
                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(pad, pad, pad, pad)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val iconView = ImageView(ctx).apply {
                        val size = (54 * dp).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            marginEnd = (10 * dp).toInt()
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        details.imageBitmap?.let { setImageBitmap(it) }
                    }

                    val textCol = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val badge = TextView(ctx).apply {
                        text = tr(lang, "বিজ্ঞাপন • Sponsored", "Ad • Sponsored")
                        textSize = 10f
                        setTextColor(AndroidColor.parseColor("#0284C7"))
                        setTypeface(null, Typeface.BOLD)
                    }

                    val title = TextView(ctx).apply {
                        text = details.title ?: "Sponsored"
                        textSize = 14f
                        setTextColor(AndroidColor.parseColor("#0F172A"))
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }

                    val desc = TextView(ctx).apply {
                        text = details.description ?: ""
                        textSize = 12f
                        setTextColor(AndroidColor.parseColor("#475569"))
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }

                    textCol.addView(badge)
                    textCol.addView(title)
                    textCol.addView(desc)

                    val ctaBtn = Button(ctx).apply {
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

                    details.registerViewForInteraction(container)
                    container
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else if (!isLoading) {
        // Fallback standard Start.io inline Banner if native inventory is empty in test/emulator region
        StartIoBannerAd(modifier = modifier)
    }
}

/**
 * Controller for Start.io Rewarded Video Ads.
 */
class StartIoRewardedHelper(private val context: Context) {
    private var rewardedAd: StartAppAd? = null
    var isReady by mutableStateOf(false)
        private set

    fun preload() {
        try {
            val ad = StartAppAd(context)
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(p0: Ad) {
                    isReady = true
                }

                override fun onFailedToReceiveAd(p0: Ad?) {
                    isReady = false
                }
            })
            rewardedAd = ad
        } catch (_: Exception) {
            isReady = false
        }
    }

    fun show(
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad != null && isReady) {
            ad.setVideoListener {
                onRewarded()
            }
            val shown = ad.showAd()
            isReady = false
            preload()
            if (!shown) onUnavailable()
        } else {
            preload()
            onUnavailable()
        }
    }
}

@Composable
fun rememberStartIoRewardedHelper(): StartIoRewardedHelper {
    val context = LocalContext.current
    val helper = remember(context) { StartIoRewardedHelper(context) }
    LaunchedEffect(helper) {
        helper.preload()
    }
    return helper
}

@Composable
fun RewardedAdCard(
    lang: AppLang,
    isReady: Boolean,
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
                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue)
            ) {
                Text(
                    text = if (isReady) tr(lang, "দেখুন", "Watch") else tr(lang, "লোড...", "Load"),
                    maxLines = 1
                )
            }
        }
    }
}
