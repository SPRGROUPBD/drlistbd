package com.example.ui.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*
import kotlinx.coroutines.delay

import android.util.Base64

internal fun decodeAdImageModel(rawUrl: String): Any? {
    val clean = rawUrl.trim()
    if (clean.isBlank()) return null
    if (clean.startsWith("data:image/", ignoreCase = true)) {
        val commaIdx = clean.indexOf(',')
        if (commaIdx > 0 && commaIdx < clean.length - 1) {
            return runCatching {
                Base64.decode(clean.substring(commaIdx + 1), Base64.DEFAULT)
            }.getOrNull()
        }
        return null
    }
    return clean
}

internal fun openAdUrl(context: Context, rawUrl: String, lang: AppLang) {
    val clean = rawUrl.trim()
    if (clean.isEmpty()) return
    val normalized = if (
        clean.startsWith("http://", true) ||
        clean.startsWith("https://", true) ||
        clean.startsWith("tel:", true)
    ) clean else "https://$clean"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }.onFailure {
        Toast.makeText(
            context,
            tr(lang, "লিংক খোলা যায়নি", "Unable to open link"),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Section 4: Top Home Slider Carousel
 * - Combines Admin Custom Sliders and Advertiser Existing Record Sliders (Doctor/Hospital/Diagnostic).
 * - Auto-slides automatically with manual swipe support & pagination indicators.
 * - Clicking an Advertiser slide navigates to the original existing Doctor/Hospital/Diagnostic details page!
 * - Clicking a Custom Slider opens its destination URL.
 */
@Composable
fun HomeSliderCarouselSection(
    slides: List<ResolvedHomeSliderSlide>,
    autoSlideIntervalMs: Long,
    lang: AppLang,
    onSlideVisible: (ResolvedHomeSliderSlide) -> Unit,
    onSlideClick: (ResolvedHomeSliderSlide) -> Unit,
    modifier: Modifier = Modifier
) {
    if (slides.isEmpty()) return
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { slides.size })

    LaunchedEffect(slides.size, autoSlideIntervalMs) {
        if (slides.size > 1) {
            while (true) {
                delay(autoSlideIntervalMs.coerceIn(2000L, 15000L))
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % slides.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    val loggedSlides = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(pagerState.currentPage, slides) {
        val current = slides.getOrNull(pagerState.currentPage)
        if (current != null && !current.isCustomAdminSlider && loggedSlides[current.slideId] != true) {
            loggedSlides[current.slideId] = true
            onSlideVisible(current)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("home_slider_carousel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) { page ->
            val slide = slides[page]
            var imageFailed by remember(slide.slideId, slide.imageUrl) { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.2f)),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        if (!slide.isCustomAdminSlider) {
                            onSlideClick(slide)
                        } else if (slide.destinationUrl.isNotBlank()) {
                            onSlideClick(slide)
                            openAdUrl(context, slide.destinationUrl, lang)
                        }
                    }
                    .testTag("home_slider_card_$page")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val slideModel = remember(slide.imageUrl) { decodeAdImageModel(slide.imageUrl) }
                    if (slideModel != null && !imageFailed) {
                        AsyncImage(
                            model = slideModel,
                            contentDescription = slide.title,
                            contentScale = ContentScale.Crop,
                            onError = { imageFailed = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(listOf(MedicalBlueDark, MedicalTealDark))
                                )
                        ) {
                            val icon = when (slide.targetRecordType) {
                                SliderRecordTypes.DOCTOR -> Icons.Rounded.MedicalServices
                                SliderRecordTypes.HOSPITAL -> Icons.Rounded.LocalHospital
                                SliderRecordTypes.DIAGNOSTIC -> Icons.Rounded.Biotech
                                else -> Icons.Rounded.Campaign
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .size(72.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            )
                        }
                    }

                    // Legible overlay gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.22f),
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.82f)
                                    )
                                )
                            )
                    )

                    // Top Badge Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (slide.isCustomAdminSlider) MedicalTeal else Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = slide.categoryBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                        if (!slide.isCustomAdminSlider) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = tr(lang, "বিস্তারিত দেখুন", "View Details"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MedicalBlueDark,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Bottom Title & Details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val detailLine = slide.subtitle.ifBlank { slide.description }
                        if (detailLine.isNotBlank()) {
                            Text(
                                text = detailLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Pagination Dot Indicators
        if (slides.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(slides.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (selected) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) MedicalBlue else OutlineSoft)
                    )
                }
            }
        }
    }
}

/**
 * Section 5: Native Advertisement — 300×100 PX (300dp × 100dp)
 * Rendered after every 3 content cards on Doctor List, Hospital List, Diagnostic List, Favorite List.
 * - Collapses cleanly (0dp) if creative fails to load.
 * - Logs real visible impression only after >= 50% visible & creative rendered cleanly.
 */
@Composable
fun NativeAd300x100Card(
    campaign: AdCampaignItem,
    queueKey: String,
    slotIndex: Int,
    lang: AppLang,
    onVisibleImpression: (AdCampaignItem, String) -> Unit,
    onClicked: (AdCampaignItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageModel = remember(campaign.imageUrl) { decodeAdImageModel(campaign.imageUrl) }
    var imageFailed by remember(campaign.id, campaign.imageUrl) { mutableStateOf(false) }
    var impressionLogged by remember(campaign.id, queueKey, slotIndex) { mutableStateOf(false) }
    var isVisible by remember(campaign.id, queueKey, slotIndex) { mutableStateOf(false) }
    val showFallback = imageModel == null || imageFailed

    LaunchedEffect(isVisible, campaign.id, queueKey, slotIndex) {
        if (isVisible && !impressionLogged) {
            impressionLogged = true
            onVisibleImpression(campaign, queueKey)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val h = coords.size.height.toFloat()
                val w = coords.size.width.toFloat()
                if (h > 0f && w > 0f) {
                    val ratio = (bounds.height / h) * (bounds.width / w)
                    isVisible = ratio >= 0.4f
                }
            }
            .testTag("native_ad_300x100_${queueKey}_$slotIndex"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.28f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    onClicked(campaign, queueKey)
                    openAdUrl(context, campaign.destinationUrl, lang)
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!showFallback) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = campaign.title.ifBlank { campaign.businessName },
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.22f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.72f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(MedicalBlueDark, MedicalTealDark))
                            )
                    )
                }
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = tr(lang, "স্পন্সরড", "Sponsored"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = campaign.title.ifBlank { campaign.businessName.ifBlank { tr(lang, "স্পন্সরড বিজ্ঞাপন", "Sponsored Ad") } },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = campaign.businessName.ifBlank { campaign.description }
                        if (sub.isNotBlank()) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (campaign.destinationUrl.isNotBlank()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = tr(lang, "ভিজিট", "Open"),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified Native Ad slot used across Doctor List, Hospital List, Diagnostic Center List,
 * Home Screen "জনপ্রিয় ডাক্তারবৃন্দ" (Popular Doctors), and Favorite List.
 * - Renders an active Admin/Advertiser 300x100 Native Ad when available.
 * - Automatically displays Google AdMob Native Ad (Official Test Native Ad or Admin's live AdMob Native Unit ID)
 *   when enabled and either no custom native campaign is active or alternating slots prevent duplicates.
 * - Completely hides (0dp) if neither an active custom native ad nor AdMob Native is active.
 */
@Composable
fun NativeAdSlotOrAdMob(
    campaigns: List<AdCampaignItem>,
    placementTarget: String,
    queueKey: String,
    slotIndex: Int,
    baseOffset: Long,
    nowMs: Long,
    placementSettings: PlacementGlobalSettings,
    admobConfig: AdMobRemoteConfig,
    lang: AppLang,
    onVisibleImpression: (AdCampaignItem, String) -> Unit,
    onClicked: (AdCampaignItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val normPlacement = remember(placementTarget) { AdPlacements.normalize(placementTarget) }
    val placementEnabled = when (normPlacement) {
        AdPlacements.DOCTOR_LIST -> placementSettings.doctorNativeEnabled
        AdPlacements.HOSPITAL_LIST -> placementSettings.hospitalNativeEnabled
        AdPlacements.DIAGNOSTIC_LIST -> placementSettings.diagnosticNativeEnabled
        AdPlacements.FAVORITE_LIST -> placementSettings.favoriteNativeEnabled
        else -> true
    }

    val customNative = remember(campaigns, placementSettings, normPlacement, slotIndex, baseOffset, nowMs / 60_000L) {
        AdvertisingRotationEngine.selectNativeAdForSlot(
            campaigns = campaigns,
            placementTarget = normPlacement,
            slotIndex = slotIndex,
            baseOffset = baseOffset,
            nowMs = nowMs,
            settings = placementSettings
        )
    }

    val activeCustomCount = remember(campaigns, placementSettings, normPlacement, nowMs / 60_000L) {
        if (!placementSettings.adsEnabled || !placementEnabled) 0
        else campaigns.count {
            AdPackageTypes.normalize(it.adType) == AdPackageTypes.NATIVE_300x100 &&
                AdvertisingValidators.isCampaignEligible(it, nowMs)
        }
    }

    val admobNativeUnitId = remember(admobConfig, normPlacement, slotIndex) {
        admobConfig.nativeUnitIdForSlot(normPlacement, slotIndex)
    }

    // Show Google AdMob Native Ad when no custom native campaign is active, or interleave on alternating 3-card slots
    val preferAdMobForSlot = placementSettings.adsEnabled && !admobNativeUnitId.isNullOrBlank() && placementEnabled && (
        customNative == null || (activeCustomCount >= 1 && slotIndex % 2 == 1)
        )

    when {
        preferAdMobForSlot && !admobNativeUnitId.isNullOrBlank() -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))
                AdMobNativeAdCard(
                    adUnitId = admobNativeUnitId,
                    lang = lang,
                    slotKey = "${queueKey}_$slotIndex"
                )
            }
        }
        customNative != null -> {
            Column(modifier = modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))
                NativeAd300x100Card(
                    campaign = customNative,
                    queueKey = queueKey,
                    slotIndex = slotIndex,
                    lang = lang,
                    onVisibleImpression = onVisibleImpression,
                    onClicked = onClicked
                )
            }
        }
        else -> Unit
    }
}

/**
 * 10-Second Auto-Rotating Bottom Banner Bar:
 * - Supports Admin Promotional Bottom Banners, Advertiser Bottom Banners, and Google AdMob Banner Ads.
 * - If 0 active banners: hidden completely (0dp).
 * - If 1 active banner: displays continuously without unnecessary timer/rotation.
 * - If >1 active banners: automatically rotates every 10,000 ms (10s) with smooth fade+slide transition,
 *   pausing when backgrounded and cleaning up cleanly on dispose to prevent memory leaks.
 */
@Composable
fun RotatingBottomBannerBar(
    banners: List<BottomBannerDisplayItem>,
    lang: AppLang,
    onVisibleImpression: (BottomBannerDisplayItem) -> Unit,
    onClicked: (BottomBannerDisplayItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) return

    var currentIndex by remember(banners.map { it.id }) { mutableIntStateOf(0) }
    var isForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isForeground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(banners.size, isForeground) {
        if (banners.size > 1 && isForeground) {
            while (true) {
                delay(10_000L)
                if (isForeground && banners.size > 1) {
                    currentIndex = (currentIndex + 1) % banners.size
                }
            }
        }
    }

    val safeIndex = currentIndex.coerceIn(0, banners.lastIndex)
    val activeItem = banners[safeIndex]

    if (banners.size == 1) {
        SingleBottomBannerItem(
            item = activeItem,
            lang = lang,
            onVisibleImpression = onVisibleImpression,
            onClicked = onClicked,
            modifier = modifier
        )
    } else {
        AnimatedContent(
            targetState = activeItem,
            transitionSpec = {
                (fadeIn(animationSpec = tween(350)) + slideInVertically(animationSpec = tween(350)) { it / 3 }) togetherWith
                    (fadeOut(animationSpec = tween(250)) + slideOutVertically(animationSpec = tween(250)) { -it / 3 })
            },
            label = "bottom_banner_rotation",
            modifier = modifier.fillMaxWidth()
        ) { item ->
            SingleBottomBannerItem(
                item = item,
                lang = lang,
                onVisibleImpression = onVisibleImpression,
                onClicked = onClicked
            )
        }
    }
}

@Composable
private fun SingleBottomBannerItem(
    item: BottomBannerDisplayItem,
    lang: AppLang,
    onVisibleImpression: (BottomBannerDisplayItem) -> Unit,
    onClicked: (BottomBannerDisplayItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (item.isAdMobBanner) {
        AdMobBannerAdView(
            adUnitId = item.admobUnitId,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val imageModel = remember(item.imageUrl) { decodeAdImageModel(item.imageUrl) }
    var imageFailed by remember(item.id, item.imageUrl) { mutableStateOf(false) }
    var impressionLogged by remember(item.id) { mutableStateOf(false) }
    var isVisible by remember(item.id) { mutableStateOf(false) }
    val showFallback = imageModel == null || imageFailed
    val isDark = LocalIsDarkTheme.current

    LaunchedEffect(isVisible, item.id) {
        if (isVisible && !impressionLogged) {
            impressionLogged = true
            onVisibleImpression(item)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isDark) Color(0xFF0A121C) else LightSurfaceWhite)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val h = coords.size.height.toFloat()
                val w = coords.size.width.toFloat()
                if (h > 0f && w > 0f) {
                    val ratio = (bounds.height / h) * (bounds.width / w)
                    isVisible = ratio >= 0.4f
                }
            }
            .testTag("bottom_banner_300x50"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.25f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    onClicked(item)
                    if (item.destinationUrl.isNotBlank()) {
                        openAdUrl(context, item.destinationUrl, lang)
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!showFallback) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(MedicalBlueDark, MedicalTealDark)))
                            .padding(start = 48.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title.ifBlank { tr(lang, "বিজ্ঞাপন", "Sponsored Banner") },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.subtitle.isNotBlank() && item.subtitle != item.title) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (item.destinationUrl.isNotBlank()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (item.isAdminPromo) tr(lang, "অফার", "Promo") else tr(lang, "বিজ্ঞাপন", "Ad"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Section 6: Bottom Banner Advertisement — 300×50 PX (responsive width up to 360dp × 52dp)
 * Only ONE banner visible at the bottom of applicable screens.
 * Supports HTTP/HTTPS URLs, Base64 gallery uploads, and graceful branded fallback.
 */
@Composable
fun BottomBanner300x50Bar(
    campaign: AdCampaignItem,
    lang: AppLang,
    onVisibleImpression: (AdCampaignItem, String) -> Unit,
    onClicked: (AdCampaignItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageModel = remember(campaign.imageUrl) { decodeAdImageModel(campaign.imageUrl) }
    var imageFailed by remember(campaign.id, campaign.imageUrl) { mutableStateOf(false) }
    var impressionLogged by remember(campaign.id) { mutableStateOf(false) }
    var isVisible by remember(campaign.id) { mutableStateOf(false) }
    val showFallback = imageModel == null || imageFailed
    val isDark = LocalIsDarkTheme.current

    LaunchedEffect(isVisible, campaign.id) {
        if (isVisible && !impressionLogged) {
            impressionLogged = true
            onVisibleImpression(campaign, AdvertisingRotationEngine.QUEUE_BOTTOM_BANNER)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isDark) Color(0xFF0A121C) else LightSurfaceWhite)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val h = coords.size.height.toFloat()
                val w = coords.size.width.toFloat()
                if (h > 0f && w > 0f) {
                    val ratio = (bounds.height / h) * (bounds.width / w)
                    isVisible = ratio >= 0.4f
                }
            }
            .testTag("bottom_banner_300x50"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.25f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    onClicked(campaign, AdvertisingRotationEngine.QUEUE_BOTTOM_BANNER)
                    openAdUrl(context, campaign.destinationUrl, lang)
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!showFallback) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = campaign.title.ifBlank { campaign.businessName },
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(MedicalBlueDark, MedicalTealDark)))
                            .padding(start = 42.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = campaign.title.ifBlank { campaign.businessName.ifBlank { tr(lang, "স্পন্সরড বিজ্ঞাপন", "Sponsored Banner") } },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (campaign.businessName.isNotBlank() && campaign.businessName != campaign.title) {
                                Text(
                                    text = campaign.businessName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (campaign.destinationUrl.isNotBlank()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = tr(lang, "বিজ্ঞাপন", "Ad"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Section 7: App Open Popup Advertisement — 300×200 PX (300dp × 200dp)
 * Displayed ONLY when the app opens (once per session), never repeatedly during navigation.
 * Includes visible Close button.
 */
@Composable
fun AppOpenPopup300x200Dialog(
    campaign: AdCampaignItem,
    lang: AppLang,
    onVisibleImpression: (AdCampaignItem, String) -> Unit,
    onClicked: (AdCampaignItem, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val popupModel = remember(campaign.imageUrl) { decodeAdImageModel(campaign.imageUrl) }
    var imageFailed by remember(campaign.id, campaign.imageUrl) { mutableStateOf(false) }
    var impressionLogged by remember(campaign.id) { mutableStateOf(false) }
    val showFallback = popupModel == null || imageFailed

    LaunchedEffect(campaign.id) {
        if (!impressionLogged) {
            impressionLogged = true
            onVisibleImpression(campaign, AdvertisingRotationEngine.QUEUE_APP_OPEN_POPUP)
        }
    }

    if (showFallback && campaign.title.isBlank() && campaign.description.isBlank()) {
        LaunchedEffect(campaign.id) { onDismiss() }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            tonalElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 336.dp)
                .testTag("app_open_popup_300x200")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = campaign.businessName.ifBlank { tr(lang, "স্পন্সরড বিজ্ঞাপন", "Sponsored Advertisement") },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tr(lang, "স্পন্সরড", "Sponsored"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MedicalTealDark
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("app_open_popup_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = tr(lang, "বন্ধ করুন", "Close"),
                            tint = TextPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundIce,
                    modifier = Modifier
                        .width(300.dp)
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onClicked(campaign, AdvertisingRotationEngine.QUEUE_APP_OPEN_POPUP)
                            openAdUrl(context, campaign.destinationUrl, lang)
                            onDismiss()
                        }
                ) {
                    if (!showFallback) {
                        AsyncImage(
                            model = popupModel,
                            contentDescription = campaign.title.ifBlank { campaign.businessName },
                            contentScale = ContentScale.Crop,
                            onError = { imageFailed = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(MedicalBlueDark, MedicalTealDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Campaign,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                if (campaign.title.isNotBlank()) {
                    Text(
                        text = campaign.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (campaign.description.isNotBlank()) {
                    Text(
                        text = campaign.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(tr(lang, "বন্ধ করুন", "Close"))
                    }
                    if (campaign.destinationUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                onClicked(campaign, AdvertisingRotationEngine.QUEUE_APP_OPEN_POPUP)
                                openAdUrl(context, campaign.destinationUrl, lang)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text(tr(lang, "ভিজিট করুন", "Visit Now"))
                        }
                    }
                }
            }
        }
    }
}
