package com.example.ui.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.LightSurfaceWhite
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.concurrent.atomic.AtomicBoolean

object AdMobInitializer {
    private const val TAG = "AdMobInitializer"
    private val initStarted = AtomicBoolean(false)
    @Volatile
    private var initCompleted = false
    private val pendingCallbacks = mutableListOf<() -> Unit>()
    private val lock = Any()

    @Volatile
    var syncedRemoteAppId: String = ""
        private set

    fun ensureInitialized(context: Context) {
        runWhenInitialized(context) {}
    }

    fun runWhenInitialized(context: Context, onReady: () -> Unit) {
        var shouldRunNow = false
        var shouldStartInit = false
        synchronized(lock) {
            if (initCompleted) {
                shouldRunNow = true
            } else {
                pendingCallbacks.add(onReady)
                if (initStarted.compareAndSet(false, true)) {
                    shouldStartInit = true
                }
            }
        }
        if (shouldRunNow) {
            onReady()
            return
        }
        if (shouldStartInit) {
            runCatching {
                MobileAds.initialize(context.applicationContext) {
                    Log.d(TAG, "Google Mobile Ads SDK initialized")
                    val callbacks = synchronized(lock) {
                        initCompleted = true
                        val copy = pendingCallbacks.toList()
                        pendingCallbacks.clear()
                        copy
                    }
                    callbacks.forEach { cb -> runCatching { cb() } }
                }
            }.onFailure { err ->
                Log.w(TAG, "MobileAds initialization skipped: ${err.message}")
                val callbacks = synchronized(lock) {
                    initStarted.set(false)
                    val copy = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    copy
                }
                callbacks.forEach { cb -> runCatching { cb() } }
            }
        }
    }

    fun syncRemoteAppId(context: Context, remoteAppId: String) {
        val clean = remoteAppId.trim()
        if (clean != syncedRemoteAppId) {
            syncedRemoteAppId = clean
            Log.d(TAG, "Synced remote AdMob App ID: ${if (clean.isBlank()) "(cleared)" else clean}")
        }
        ensureInitialized(context)
    }
}

/**
 * Lightweight LRU cache for loaded AdMob NativeAds so scrolling inside a LazyColumn
 * preserves already-loaded native ads without blank flicker or repeated network fetches.
 * Automatically purges stale unit IDs when Admin edits Placement IDs dynamically.
 */
private object AdMobNativeAdCache {
    private const val MAX_ENTRIES = 10
    private val lock = Any()
    private val entries = object : LinkedHashMap<String, NativeAd>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NativeAd>?): Boolean {
            if (size > MAX_ENTRIES) {
                runCatching { eldest?.value?.destroy() }
                return true
            }
            return false
        }
    }

    private fun cacheKey(adUnitId: String, slotKey: String): String = "$adUnitId|$slotKey"

    fun get(adUnitId: String, slotKey: String): NativeAd? = synchronized(lock) {
        entries[cacheKey(adUnitId, slotKey)]
    }

    fun put(adUnitId: String, slotKey: String, ad: NativeAd) = synchronized(lock) {
        val k = cacheKey(adUnitId, slotKey)
        val old = entries.put(k, ad)
        if (old != null && old !== ad) {
            runCatching { old.destroy() }
        }
    }
}

/**
 * Lifecycle-safe Google AdMob Banner Ad (320x50 standard banner, responsive centered container).
 * Wrapped in Compose [key] by [adUnitId] so whenever Admin adds, edits, or swaps a Placement ID,
 * the previous AdView is destroyed immediately and the new Placement ID loads live without restart.
 * Collapses cleanly to 0dp if loading fails or network is offline.
 */
@Composable
fun AdMobBannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val cleanId = adUnitId.trim()
    if (cleanId.isBlank()) return

    key(cleanId) {
        val context = LocalContext.current
        val isDark = LocalIsDarkTheme.current
        var loadFailed by remember(cleanId) { mutableStateOf(false) }

        val adView = remember(cleanId) {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = cleanId
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        loadFailed = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("AdMobBanner", "Banner failed ($cleanId): ${error.message}")
                        loadFailed = true
                    }
                }
            }
        }

        LaunchedEffect(cleanId, adView) {
            AdMobInitializer.runWhenInitialized(context) {
                runCatching { adView.loadAd(AdRequest.Builder().build()) }
                    .onFailure { loadFailed = true }
            }
        }

        DisposableEffect(adView) {
            onDispose {
                runCatching { adView.destroy() }
            }
        }

        if (!loadFailed) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF0A121C) else LightSurfaceWhite)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("admob_banner_ad"),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            (adView.parent as? ViewGroup)?.removeView(adView)
                            addView(adView)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }
        }
    }
}

/**
 * Lifecycle-safe Google AdMob Native Ad rendered inside an authentic [NativeAdView]
 * styled to match the app's 300x100 Material 3 Native Ad card in both Light and Dark themes.
 * Wrapped in Compose [key] by [adUnitId] + [slotKey] so Admin Placement ID edits reload immediately.
 * Caches loaded ads across LazyColumn scrolls and collapses cleanly to 0dp only if load fails.
 */
@Composable
fun AdMobNativeAdCard(
    adUnitId: String,
    lang: AppLang,
    slotKey: String = "native",
    modifier: Modifier = Modifier
) {
    val cleanId = adUnitId.trim()
    if (cleanId.isBlank()) return

    key(cleanId, slotKey) {
        val context = LocalContext.current
        val isDark = LocalIsDarkTheme.current
        val surfaceArgb = SurfaceWhite.toArgb()
        val primaryTextArgb = TextPrimary.toArgb()
        val secondaryTextArgb = TextSecondary.toArgb()
        val accentArgb = MedicalBlue.toArgb()
        val badgeBgArgb = if (isDark) 0xFF10B96B.toInt() else MedicalTeal.toArgb()
        val adBadgeText = tr(lang, "বিজ্ঞাপন", "Ad")
        val defaultCtaText = tr(lang, "বিস্তারিত", "Open")

        var loadedNativeAd by remember(cleanId, slotKey) {
            mutableStateOf(AdMobNativeAdCache.get(cleanId, slotKey))
        }
        var loadFailed by remember(cleanId, slotKey) { mutableStateOf(false) }
        var retryAttempt by remember(cleanId, slotKey) { mutableIntStateOf(0) }

        LaunchedEffect(cleanId, slotKey, retryAttempt) {
            val cached = AdMobNativeAdCache.get(cleanId, slotKey)
            if (cached != null) {
                loadedNativeAd = cached
                loadFailed = false
                return@LaunchedEffect
            }
            if (retryAttempt > 0) {
                kotlinx.coroutines.delay(1800L)
            }
            AdMobInitializer.runWhenInitialized(context) {
                runCatching {
                    val loader = AdLoader.Builder(context.applicationContext, cleanId)
                        .forNativeAd { nativeAd ->
                            AdMobNativeAdCache.put(cleanId, slotKey, nativeAd)
                            loadedNativeAd = nativeAd
                            loadFailed = false
                        }
                        .withAdListener(object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.w("AdMobNative", "Native failed ($cleanId, attempt=$retryAttempt): code=${error.code} ${error.message}")
                                if (retryAttempt == 0 && (error.code == AdRequest.ERROR_CODE_INTERNAL_ERROR || error.code == AdRequest.ERROR_CODE_NETWORK_ERROR)) {
                                    retryAttempt = 1
                                } else {
                                    loadFailed = true
                                }
                            }
                        })
                        .withNativeAdOptions(
                            NativeAdOptions.Builder()
                                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                                .build()
                        )
                        .build()

                    loader.loadAd(AdRequest.Builder().build())
                }.onFailure { err ->
                    Log.w("AdMobNative", "AdLoader exception ($cleanId): ${err.message}")
                    loadFailed = true
                }
            }
        }

        val nativeAd = loadedNativeAd
        if (!loadFailed && nativeAd != null) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("admob_native_ad_$slotKey"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .height(104.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            buildCompactNativeAdView(ctx)
                        },
                        update = { nativeAdView ->
                            bindCompactNativeAdView(
                                nativeAdView = nativeAdView,
                                nativeAd = nativeAd,
                                surfaceArgb = surfaceArgb,
                                primaryTextArgb = primaryTextArgb,
                                secondaryTextArgb = secondaryTextArgb,
                                accentArgb = accentArgb,
                                badgeBgArgb = badgeBgArgb,
                                adBadgeText = adBadgeText,
                                defaultCtaText = defaultCtaText
                            )
                        }
                    )
                }
            }
        } else if (!loadFailed) {
            // Subtle compact loading placeholder while AdMob Native request is actively in flight
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("admob_native_loading_$slotKey"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .height(104.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MedicalTeal
                        )
                        androidx.compose.material3.Text(
                            text = tr(lang, "বিজ্ঞাপন লোড হচ্ছে...", "Loading Sponsored Ad..."),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun dpToPx(context: Context, dp: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

private fun buildCompactNativeAdView(context: Context): NativeAdView {
    val adView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val rootRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = dpToPx(context, 12)
        val padV = dpToPx(context, 10)
        setPadding(padH, padV, padH, padV)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val iconView = ImageView(context).apply {
        val sizePx = dpToPx(context, 52)
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            marginEnd = dpToPx(context, 10)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val textCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpToPx(context, 8)
        }
    }

    val badgeRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val adBadge = TextView(context).apply {
        tag = "ad_badge"
        textSize = 10f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(android.graphics.Color.WHITE)
        setPadding(dpToPx(context, 6), dpToPx(context, 1), dpToPx(context, 6), dpToPx(context, 1))
    }

    val advertiserView = TextView(context).apply {
        textSize = 11f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dpToPx(context, 6), 0, 0, 0)
    }

    badgeRow.addView(adBadge)
    badgeRow.addView(advertiserView)

    val headlineView = TextView(context).apply {
        textSize = 13.5f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 2), 0, 0)
    }

    val bodyView = TextView(context).apply {
        textSize = 11.5f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 2), 0, 0)
    }

    textCol.addView(badgeRow)
    textCol.addView(headlineView)
    textCol.addView(bodyView)

    val ctaBtn = Button(context).apply {
        isAllCaps = false
        textSize = 11.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(android.graphics.Color.WHITE)
        minHeight = dpToPx(context, 36)
        minimumHeight = dpToPx(context, 36)
        minWidth = dpToPx(context, 72)
        minimumWidth = dpToPx(context, 72)
        setPadding(dpToPx(context, 10), dpToPx(context, 4), dpToPx(context, 10), dpToPx(context, 4))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    rootRow.addView(iconView)
    rootRow.addView(textCol)
    rootRow.addView(ctaBtn)
    adView.addView(rootRow)

    adView.iconView = iconView
    adView.headlineView = headlineView
    adView.bodyView = bodyView
    adView.advertiserView = advertiserView
    adView.callToActionView = ctaBtn
    return adView
}

private fun bindCompactNativeAdView(
    nativeAdView: NativeAdView,
    nativeAd: NativeAd,
    surfaceArgb: Int,
    primaryTextArgb: Int,
    secondaryTextArgb: Int,
    accentArgb: Int,
    badgeBgArgb: Int,
    adBadgeText: String,
    defaultCtaText: String
) {
    nativeAdView.setBackgroundColor(surfaceArgb)

    val badgeView = nativeAdView.findViewWithTag<TextView>("ad_badge")
    badgeView?.apply {
        text = adBadgeText
        background = GradientDrawable().apply {
            setColor(badgeBgArgb)
            cornerRadius = dpToPx(context, 6).toFloat()
        }
    }

    (nativeAdView.headlineView as? TextView)?.apply {
        text = nativeAd.headline.orEmpty()
        setTextColor(primaryTextArgb)
    }

    (nativeAdView.bodyView as? TextView)?.apply {
        val b = nativeAd.body.orEmpty()
        text = b
        setTextColor(secondaryTextArgb)
        visibility = if (b.isBlank()) View.GONE else View.VISIBLE
    }

    (nativeAdView.advertiserView as? TextView)?.apply {
        val adv = nativeAd.advertiser.orEmpty()
        text = adv
        setTextColor(secondaryTextArgb)
        visibility = if (adv.isBlank()) View.GONE else View.VISIBLE
    }

    (nativeAdView.iconView as? ImageView)?.apply {
        val drawable = nativeAd.icon?.drawable ?: nativeAd.images.firstOrNull()?.drawable
        if (drawable != null) {
            setImageDrawable(drawable)
            visibility = View.VISIBLE
        } else {
            visibility = View.GONE
        }
    }

    (nativeAdView.callToActionView as? Button)?.apply {
        text = nativeAd.callToAction?.takeIf { it.isNotBlank() } ?: defaultCtaText
        background = GradientDrawable().apply {
            setColor(accentArgb)
            cornerRadius = dpToPx(context, 10).toFloat()
        }
        visibility = View.VISIBLE
    }

    nativeAdView.setNativeAd(nativeAd)
}
