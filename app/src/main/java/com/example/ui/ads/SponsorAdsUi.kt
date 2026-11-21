package com.example.ui.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.VideoView
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.example.data.local.AdConfigEntity
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.SponsorEntity
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*
import kotlin.math.max
import kotlin.math.min

/**
 * Counts an impression ONLY when at least 50% of the advertisement card/banner is genuinely visible
 * inside the active screen viewport (prevents off-screen LazyColumn pre-composition inflation).
 */
fun Modifier.onAdVisibleOnScreen(
    visibilityKey: String,
    onBecomeVisible: () -> Unit
): Modifier = composed {
    val rootView = LocalView.current.rootView
    var firedForKey by remember(visibilityKey) { mutableStateOf(false) }
    val latestCallback by rememberUpdatedState(onBecomeVisible)

    this.onGloballyPositioned { coords ->
        if (firedForKey || !coords.isAttached) return@onGloballyPositioned
        val rect = coords.boundsInWindow()
        val height = rect.height
        val width = rect.width
        if (height <= 0f || width <= 0f) return@onGloballyPositioned
        val screenH = rootView.height.toFloat().coerceAtLeast(1f)
        val screenW = rootView.width.toFloat().coerceAtLeast(1f)
        val visibleTop = max(0f, rect.top)
        val visibleBottom = min(screenH, rect.bottom)
        val visibleLeft = max(0f, rect.left)
        val visibleRight = min(screenW, rect.right)
        val visibleH = (visibleBottom - visibleTop).coerceAtLeast(0f)
        val visibleW = (visibleRight - visibleLeft).coerceAtLeast(0f)
        if ((visibleH / height) >= 0.5f && (visibleW / width) >= 0.5f) {
            firedForKey = true
            latestCallback()
        }
    }
}

private fun openSponsorWebsite(
    context: Context,
    sponsor: SponsorEntity,
    isOnline: Boolean,
    lang: AppLang,
    onClickTracked: (String) -> Unit
) {
    val url = sponsor.websiteUrl.trim()
    if (!SponsorCampaignSelector.isValidWebsiteUrl(url)) return
    if (!isOnline) {
        Toast.makeText(
            context,
            tr(lang, "ওয়েবসাইট ভিজিট করতে ইন্টারনেট সংযোগ প্রয়োজন।", "Internet connection required to open website."),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    onClickTracked(sponsor.id)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun dialSponsorPhone(
    context: Context,
    sponsor: SponsorEntity,
    onClickTracked: (String) -> Unit
) {
    val phone = sponsor.contactNumber.trim()
    if (phone.isEmpty()) return
    onClickTracked(sponsor.id)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }
}

@Composable
private fun SponsorLogoAvatar(
    sponsor: SponsorEntity,
    isOnline: Boolean,
    sizeDp: Int = 40
) {
    val model = remember(sponsor.localLogoPath, sponsor.logoUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localLogoPath, sponsor.logoUrl, isOnline)
    }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MedicalBlueLight)
            .border(1.dp, MedicalBlueContainer, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = sponsor.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = sponsor.name.trim().take(2).uppercase().ifEmpty { "SP" }
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MedicalBlueDark
            )
        }
    }
}

@Composable
private fun SponsoredBadgePill(lang: AppLang, isOnline: Boolean, darkBg: Boolean = false) {
    Surface(
        color = if (darkBg) Color.Black.copy(alpha = 0.68f) else MedicalTealLight,
        shape = RoundedCornerShape(6.dp),
        border = if (darkBg) null else BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tr(lang, "স্পন্সরড", "Sponsored"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (darkBg) Color.White else MedicalTealDark
            )
            if (!isOnline) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = "Offline cached sponsor",
                    tint = if (darkBg) Color(0xFFBAE6FD) else TextMuted,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * Responsive Direct Sponsor Banner displayed across app pages.
 * Supports 320x100, 300x200, 728x90, and custom-size packages, with server-authoritative expiry check
 * and real viewport-visibility impression counting.
 */
@Composable
fun SponsorBannerView(
    sponsors: List<SponsorEntity>,
    adConfig: AdConfigEntity,
    pageKey: String,
    isOnline: Boolean,
    nowMs: Long,
    lang: AppLang,
    onSponsorDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onSponsorClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled || !adConfig.sponsorBannerEnabled) return

    val sponsor = remember(sponsors, pageKey, nowMs / 60_000L) {
        SponsorCampaignSelector.selectBannerSponsor(
            sponsors = sponsors,
            pageKey = pageKey,
            rotationTick = pageKey.hashCode(),
            nowMs = nowMs
        )
    } ?: return

    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }

    Surface(
        color = SurfaceWhite,
        border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.35f)),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .onAdVisibleOnScreen("banner_${sponsor.id}_$pageKey") {
                onSponsorDisplayed(sponsor.id, isOnline, "banner_$pageKey")
            }
            .testTag("direct_sponsor_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MedicalTealLight.copy(alpha = 0.45f),
                            SurfaceWhite,
                            MedicalBlueLight.copy(alpha = 0.35f)
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bannerModel != null) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MedicalBlueLight)
                ) {
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(SurfaceWhite)
                    ) {
                        SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = 18)
                    }
                }
            } else {
                SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = 44)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SponsoredBadgePill(lang = lang, isOnline = isOnline)
                    Text(
                        text = sponsor.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sponsor.promoTitle.ifBlank { sponsor.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sponsor.contactNumber.isNotBlank()) {
                    FilledTonalIconButton(
                        onClick = {
                            dialSponsorPhone(context, sponsor) { id -> onSponsorClicked(id, isOnline) }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MedicalTealLight,
                            contentColor = MedicalTealDark
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("sponsor_banner_call_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = tr(lang, "স্পন্সর কল করুন", "Call Sponsor"),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (SponsorCampaignSelector.isValidWebsiteUrl(sponsor.websiteUrl)) {
                    Button(
                        onClick = {
                            openSponsorWebsite(context, sponsor, isOnline, lang) { id ->
                                onSponsorClicked(id, isOnline)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("sponsor_banner_visit_btn")
                    ) {
                        Text(
                            text = tr(lang, "ভিজিট", "Visit"),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Direct Sponsor Popup Advertisement displayed when the application launches.
 * Dismissible immediately via visible Close (X) button or Continue button.
 */
@Composable
fun AppOpenSponsorPopupDialog(
    sponsor: SponsorEntity,
    isOnline: Boolean,
    lang: AppLang,
    onDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .onAdVisibleOnScreen("popup_${sponsor.id}") {
                    onDisplayed(sponsor.id, isOnline, "popup")
                }
                .testTag("app_open_sponsor_popup")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.linearGradient(listOf(MedicalBlueDark, MedicalTealDark))
                        )
                ) {
                    if (bannerModel != null) {
                        AsyncImage(
                            model = bannerModel,
                            contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.25f)
                                        )
                                    )
                                )
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Campaign,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = sponsor.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        SponsoredBadgePill(lang = lang, isOnline = isOnline, darkBg = true)
                    }

                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.72f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(48.dp)
                            .testTag("app_open_sponsor_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = tr(lang, "বন্ধ করুন", "Close advertisement"),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = 48)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sponsor.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (sponsor.promoTitle.isNotBlank()) {
                                Text(
                                    text = sponsor.promoTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MedicalBlueDark,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (sponsor.description.isNotBlank()) {
                        Text(
                            text = sponsor.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (SponsorCampaignSelector.isValidWebsiteUrl(sponsor.websiteUrl)) {
                            Button(
                                onClick = {
                                    openSponsorWebsite(context, sponsor, isOnline, lang) { id ->
                                        onClicked(id, isOnline)
                                    }
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("app_open_sponsor_cta_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "ভিজিট স্পন্সর", "Visit Sponsor"))
                            }
                        }
                        if (sponsor.contactNumber.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    dialSponsorPhone(context, sponsor) { id -> onClicked(id, isOnline) }
                                },
                                border = BorderStroke(1.dp, MedicalTeal),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("app_open_sponsor_call_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = null,
                                    tint = MedicalTealDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "কল", "Call"), color = MedicalTealDark)
                            }
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("app_open_sponsor_continue_btn")
                    ) {
                        Text(
                            text = tr(lang, "অ্যাপে প্রবেশ করুন", "Continue to App"),
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Sponsored Native Card displayed between content cards (4/5/6 interval).
 * Respects configured package aspect ratio (e.g., 300x200, 320x100, 728x90, or custom) and counts
 * impressions ONLY when genuinely visible on screen.
 */
@Composable
fun NativeSponsorAdCard(
    sponsors: List<SponsorEntity>,
    adConfig: AdConfigEntity,
    pageKey: String,
    slotIndex: Int,
    isOnline: Boolean,
    nowMs: Long,
    lang: AppLang,
    onSponsorDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onSponsorClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled || !adConfig.nativeSponsorEnabled) return

    val sponsor = remember(sponsors, pageKey, slotIndex, nowMs / 60_000L) {
        SponsorCampaignSelector.selectNativeSponsorForSlot(
            sponsors = sponsors,
            pageKey = pageKey,
            slotIndex = slotIndex,
            nowMs = nowMs
        )
    } ?: return

    SponsorCardRenderer(
        sponsor = sponsor,
        slotKey = "native_${pageKey}_$slotIndex",
        isOnline = isOnline,
        lang = lang,
        onSponsorDisplayed = onSponsorDisplayed,
        onSponsorClicked = onSponsorClicked,
        modifier = modifier.testTag("native_sponsor_ad_card_$slotIndex")
    )
}

/**
 * Shared responsive Sponsor Advertisement Card supporting 300x200, 320x100, 728x90,
 * Custom-size banners, Sponsored Native Cards, and About Us Sponsor placements.
 */
@Composable
fun SponsorCardRenderer(
    sponsor: SponsorEntity,
    slotKey: String,
    isOnline: Boolean,
    lang: AppLang,
    onSponsorDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onSponsorClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }

    // Dynamic responsive height respecting package dimensions (300x50 -> strip, 300x200 -> tall, 320x100 / 728x90 -> wide)
    val imageHeightDp = remember(sponsor.widthPx, sponsor.heightPx, sponsor.placementType) {
        when {
            sponsor.placementType.equals(SponsorCampaignSelector.PLACEMENT_BANNER_300x50, ignoreCase = true) -> 68.dp
            sponsor.placementType.equals(SponsorCampaignSelector.PLACEMENT_BANNER_728x90, ignoreCase = true) -> 88.dp
            sponsor.placementType.equals(SponsorCampaignSelector.PLACEMENT_BANNER_320x100, ignoreCase = true) -> 110.dp
            sponsor.placementType.equals(SponsorCampaignSelector.PLACEMENT_BANNER_300x200, ignoreCase = true) -> 196.dp
            else -> {
                val ratio = if (sponsor.widthPx > 0 && sponsor.heightPx > 0) {
                    sponsor.heightPx.toFloat() / sponsor.widthPx.toFloat()
                } else 0.55f
                (300f * ratio).coerceIn(68f, 220f).dp
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.2.dp, MedicalTeal.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .onAdVisibleOnScreen("${slotKey}_${sponsor.id}") {
                onSponsorDisplayed(sponsor.id, isOnline, slotKey)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MedicalTealLight.copy(alpha = 0.35f),
                            SurfaceWhite
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sponsor.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sponsor.promoTitle.ifBlank { tr(lang, "অনুমোদিত স্পন্সর পার্টনার", "Featured Sponsor Partner") },
                        style = MaterialTheme.typography.bodySmall,
                        color = MedicalBlueDark,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SponsoredBadgePill(lang = lang, isOnline = isOnline)
            }

            if (bannerModel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeightDp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MedicalBlueLight.copy(alpha = 0.4f))
                ) {
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sponsor.description.isNotBlank()) {
                    Text(
                        text = sponsor.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (SponsorCampaignSelector.isValidWebsiteUrl(sponsor.websiteUrl)) {
                        Button(
                            onClick = {
                                openSponsorWebsite(context, sponsor, isOnline, lang) { id ->
                                    onSponsorClicked(id, isOnline)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "ভিজিট স্পন্সর", "Visit Sponsor"), maxLines = 1)
                        }
                    }
                    if (sponsor.contactNumber.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                dialSponsorPhone(context, sponsor) { id -> onSponsorClicked(id, isOnline) }
                            },
                            border = BorderStroke(1.dp, MedicalTeal),
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = null,
                                tint = MedicalTealDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = tr(lang, "কল করুন", "Call"),
                                color = MedicalTealDark,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Administrator-controlled Sponsor Advertisement Section for the About Us page,
 * paired with a direct CTA for businesses & institutions to purchase packages or manage their ads.
 */
@Composable
fun AboutUsSponsorSection(
    sponsors: List<SponsorEntity>,
    adConfig: AdConfigEntity,
    isOnline: Boolean,
    nowMs: Long,
    lang: AppLang,
    onSponsorDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onSponsorClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    onOpenAdvertiserPortal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAboutSponsors = remember(sponsors, adConfig, nowMs / 60_000L) {
        if (!adConfig.adsEnabled || !adConfig.aboutUsSponsorEnabled) emptyList()
        else SponsorCampaignSelector.selectAboutUsSponsors(
            sponsors = sponsors,
            featuredAdId = adConfig.aboutUsFeaturedAdId,
            maxAds = adConfig.aboutUsSponsorMaxAds,
            nowMs = nowMs
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("about_us_sponsor_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (adConfig.adsEnabled && adConfig.aboutUsSponsorEnabled && activeAboutSponsors.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = MedicalTealDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tr(
                            lang,
                            adConfig.aboutUsSponsorTitleBn.ifBlank { "আমাদের স্পন্সর পার্টনার" },
                            adConfig.aboutUsSponsorTitleEn.ifBlank { "Our Sponsor Partners" }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                SponsoredBadgePill(lang = lang, isOnline = isOnline)
            }

            activeAboutSponsors.forEachIndexed { idx, sp ->
                SponsorCardRenderer(
                    sponsor = sp,
                    slotKey = "about_us_$idx",
                    isOnline = isOnline,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked,
                    modifier = Modifier.testTag("about_us_sponsor_card_$idx")
                )
            }
        }

        // Direct Sponsor Portal CTA Card for Businesses & Healthcare Institutions
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.2.dp, MedicalBlue.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(MedicalBlueLight.copy(alpha = 0.5f), SurfaceWhite)
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MedicalBlue,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(lang, "আপনার প্রতিষ্ঠানের বিজ্ঞাপন দিন", "Advertise Your Business Here"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = tr(
                                lang,
                                "হাসপাতাল, ডায়াগনস্টিক বা ফার্মেসি — সরাসরি ফিক্সড প্যাকেজ কিনে বিজ্ঞাপন প্রকাশ করুন (বিকাশ/নগদ/রকেট)।",
                                "Hospitals, clinics & businesses can book direct sponsor packages via bKash, Nagad, or Rocket."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Button(
                    onClick = onOpenAdvertiserPortal,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("open_advertiser_portal_btn")
                ) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(lang, "অ্যাডভার্টাইজার পোর্টাল ওপেন করুন", "Open Advertiser Portal"))
                }
            }
        }
    }
}

/**
 * Screen-Based Direct Sponsor Placement Slot.
 * Only renders when:
 * - Master adsEnabled is ON
 * - The canonical Placement ID's Rate Card is ACTIVE
 * - An unexpired, active sponsor explicitly targets this Placement ID (within maxActiveAds cap)
 * Automatically formats container dimensions for 320x50, 320x100, 300x200, 300x250, 336x280,
 * 300x300, RESPONSIVE, FULL_WIDTH, or Admin-Defined CUSTOM sizes.
 */
@Composable
fun PlacementSponsorSlot(
    placementId: String,
    sponsors: List<SponsorEntity>,
    rateCards: List<PlacementRateCardEntity>,
    adConfig: AdConfigEntity,
    isOnline: Boolean,
    nowMs: Long,
    lang: AppLang,
    slotIndex: Int = 0,
    onSponsorDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onSponsorClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled) return

    val sponsor = remember(sponsors, rateCards, placementId, slotIndex, nowMs / 60_000L) {
        PlacementRateCardEngine.selectSponsorForPlacement(
            sponsors = sponsors,
            rateCards = rateCards,
            placementId = placementId,
            rotationTick = placementId.hashCode() + slotIndex,
            nowMs = nowMs
        )
    } ?: return

    val context = LocalContext.current
    val sizeKey = sponsor.adSizeKey.trim().uppercase()
    val isCompactStrip = sizeKey == "320X50" || (sponsor.heightPx in 1..75 && sizeKey.isEmpty())
    val isLargeBanner = sizeKey == "320X100" || (sponsor.heightPx in 76..125 && sponsor.widthPx >= 300 && sizeKey.isEmpty())
    val slotKey = "placement_${placementId}_$slotIndex"

    if (isCompactStrip || isLargeBanner) {
        val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
            SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
        }
        val stripHeight = if (isCompactStrip) 56.dp else 94.dp
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.4f)),
            tonalElevation = 1.dp,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = stripHeight)
                .onAdVisibleOnScreen("${slotKey}_${sponsor.id}") {
                    onSponsorDisplayed(sponsor.id, isOnline, slotKey)
                }
                .testTag("placement_slot_$placementId")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(MedicalTealLight.copy(alpha = 0.4f), SurfaceWhite, MedicalBlueLight.copy(alpha = 0.35f))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = if (isCompactStrip) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bannerModel != null) {
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = if (isCompactStrip) 64.dp else 88.dp, height = if (isCompactStrip) 42.dp else 68.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = if (isCompactStrip) 40 else 52)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        SponsoredBadgePill(lang = lang, isOnline = isOnline)
                        Text(
                            text = sponsor.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sponsor.promoTitle.ifBlank { sponsor.description },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = if (isCompactStrip) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (sponsor.contactNumber.isNotBlank()) {
                    FilledTonalIconButton(
                        onClick = { dialSponsorPhone(context, sponsor) { id -> onSponsorClicked(id, isOnline) } },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MedicalTealLight),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = "Call Sponsor", tint = MedicalTealDark, modifier = Modifier.size(18.dp))
                    }
                }
                if (SponsorCampaignSelector.isValidWebsiteUrl(sponsor.websiteUrl)) {
                    Button(
                        onClick = { openSponsorWebsite(context, sponsor, isOnline, lang) { id -> onSponsorClicked(id, isOnline) } },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text(tr(lang, "দেখুন", "Visit"), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    } else {
        val dynamicHeightDp = remember(sizeKey, sponsor.widthPx, sponsor.heightPx) {
            when (sizeKey) {
                "300X200" -> 185.dp
                "300X250" -> 215.dp
                "336X280" -> 235.dp
                "300X300" -> 240.dp
                "FULL_WIDTH" -> 160.dp
                "RESPONSIVE" -> 180.dp
                else -> {
                    val ratio = if (sponsor.widthPx > 0 && sponsor.heightPx > 0) {
                        sponsor.heightPx.toFloat() / sponsor.widthPx.toFloat()
                    } else 0.58f
                    (300f * ratio).coerceIn(110f, 250f).dp
                }
            }
        }
        val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
            SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
        }
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = BorderStroke(1.2.dp, MedicalTeal.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = modifier
                .fillMaxWidth()
                .onAdVisibleOnScreen("${slotKey}_${sponsor.id}") {
                    onSponsorDisplayed(sponsor.id, isOnline, slotKey)
                }
                .testTag("placement_slot_$placementId")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(MedicalTealLight.copy(alpha = 0.35f), SurfaceWhite)))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = 40)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sponsor.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sponsor.promoTitle.ifBlank { tr(lang, "অনুমোদিত স্পন্সর", "Featured Sponsor") }, style = MaterialTheme.typography.bodySmall, color = MedicalBlueDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SponsoredBadgePill(lang = lang, isOnline = isOnline)
                }
                if (bannerModel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dynamicHeightDp)
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MedicalBlueLight.copy(alpha = 0.4f))
                    ) {
                        AsyncImage(
                            model = bannerModel,
                            contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sponsor.description.isNotBlank()) {
                        Text(sponsor.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (SponsorCampaignSelector.isValidWebsiteUrl(sponsor.websiteUrl)) {
                            Button(
                                onClick = { openSponsorWebsite(context, sponsor, isOnline, lang) { id -> onSponsorClicked(id, isOnline) } },
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "ভিজিট স্পন্সর", "Visit Sponsor"), maxLines = 1)
                            }
                        }
                        if (sponsor.contactNumber.isNotBlank()) {
                            OutlinedButton(
                                onClick = { dialSponsorPhone(context, sponsor) { id -> onSponsorClicked(id, isOnline) } },
                                border = BorderStroke(1.dp, MedicalTeal),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, tint = MedicalTealDark, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(tr(lang, "কল করুন", "Call"), color = MedicalTealDark, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-Screen Sponsor Advertisement Dialog for Placement `OTH-FULLSCREEN`.
 */
@Composable
fun FullScreenSponsorDialog(
    sponsor: SponsorEntity,
    isOnline: Boolean,
    lang: AppLang,
    onDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier
                .fillMaxSize()
                .onAdVisibleOnScreen("fullscreen_${sponsor.id}") {
                    onDisplayed(sponsor.id, isOnline, "placement_OTH-FULLSCREEN")
                }
                .testTag("full_screen_sponsor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SponsoredBadgePill(lang = lang, isOnline = isOnline, darkBg = true)
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(48.dp).testTag("fullscreen_sponsor_close_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = tr(lang, "বন্ধ করুন", "Close"))
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bannerModel != null) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 380.dp)
                        ) {
                            AsyncImage(
                                model = bannerModel,
                                contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = 52)
                        Column {
                            Text(sponsor.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            if (sponsor.promoTitle.isNotBlank()) {
                                Text(sponsor.promoTitle, style = MaterialTheme.typography.titleSmall, color = Color(0xFF7DD3FC))
                            }
                        }
                    }
                    if (sponsor.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = sponsor.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (SponsorCampaignSelector.isValidWebsiteUrl(sponsor.websiteUrl)) {
                            Button(
                                onClick = {
                                    openSponsorWebsite(context, sponsor, isOnline, lang) { id -> onClicked(id, isOnline) }
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                                modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                            ) {
                                Text(tr(lang, "ভিজিট স্পন্সর", "Visit Sponsor"), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (sponsor.contactNumber.isNotBlank()) {
                            OutlinedButton(
                                onClick = { dialSponsorPhone(context, sponsor) { id -> onClicked(id, isOnline) } },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "কল করুন", "Call"), color = Color.White)
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Text(tr(lang, "অ্যাপে ফিরে যান", "Continue to App"), color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

/**
 * Direct Sponsor Reward Video Dialog for `Reward Video – Doctor Click` packages.
 * Plays sponsor video when online with a valid HTTPS video URL, and falls back gracefully
 * to cached sponsor visual preview offline so active Direct Sponsor campaigns work in both modes.
 */
@Composable
fun DirectSponsorRewardVideoDialog(
    sponsor: SponsorEntity,
    isOnline: Boolean,
    lang: AppLang,
    onDisplayed: (sponsorId: String, isOnline: Boolean, slotKey: String) -> Unit,
    onClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }
    val canStreamVideo = remember(sponsor.videoUrl, isOnline) {
        isOnline && sponsor.videoUrl.trim().startsWith("https://", ignoreCase = true)
    }
    var secondsLeft by remember(sponsor.id) { mutableIntStateOf(5) }
    var videoError by remember(sponsor.id) { mutableStateOf(false) }

    LaunchedEffect(sponsor.id) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    Dialog(
        onDismissRequest = { if (secondsLeft <= 0) onCompleted() else onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = Color(0xFF0B1120),
            modifier = Modifier
                .fillMaxSize()
                .onAdVisibleOnScreen("reward_video_${sponsor.id}") {
                    onDisplayed(sponsor.id, isOnline, "reward_video_doctor_click")
                }
                .testTag("direct_sponsor_reward_video_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SponsoredBadgePill(lang = lang, isOnline = isOnline, darkBg = true)
                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (secondsLeft > 0) {
                                    tr(lang, "ডাক্তারের তথ্য ${secondsLeft} সেকেন্ডে...", "Doctor details in ${secondsLeft}s...")
                                } else {
                                    tr(lang, "প্রস্তুত", "Ready")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { if (secondsLeft <= 0) onCompleted() else onDismiss() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reward_sponsor_close_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = tr(lang, "বন্ধ করুন", "Close"))
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 210.dp, max = 320.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (canStreamVideo && !videoError) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(Uri.parse(sponsor.videoUrl.trim()))
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = false
                                                start()
                                            }
                                            setOnCompletionListener {
                                                secondsLeft = 0
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                videoError = true
                                                true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (bannerModel != null) {
                                AsyncImage(
                                    model = bannerModel,
                                    contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Text(
                                        text = sponsor.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SponsorLogoAvatar(sponsor = sponsor, isOnline = isOnline, sizeDp = 48)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sponsor.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (sponsor.promoTitle.isNotBlank()) {
                                Text(
                                    text = sponsor.promoTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF7DD3FC),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (sponsor.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = sponsor.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (SponsorCampaignSelector.isValidWebsiteUrl(sponsor.websiteUrl)) {
                            OutlinedButton(
                                onClick = {
                                    openSponsorWebsite(context, sponsor, isOnline, lang) { id ->
                                        onClicked(id, isOnline)
                                    }
                                },
                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(tr(lang, "ভিজিট স্পন্সর", "Visit Sponsor"), color = Color(0xFF7DD3FC))
                            }
                        }
                        if (sponsor.contactNumber.isNotBlank()) {
                            OutlinedButton(
                                onClick = { dialSponsorPhone(context, sponsor) { id -> onClicked(id, isOnline) } },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "কল", "Call"), color = Color.White)
                            }
                        }
                    }

                    Button(
                        onClick = onCompleted,
                        enabled = secondsLeft <= 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MedicalTeal,
                            disabledContainerColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("reward_sponsor_continue_btn")
                    ) {
                        Text(
                            text = if (secondsLeft > 0) {
                                tr(lang, "অপেক্ষা করুন (${secondsLeft} সে.)", "Please wait (${secondsLeft}s)")
                            } else {
                                tr(lang, "ডাক্তারের বিস্তারিত দেখুন", "View Doctor Profile")
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Visual Placement Mockup shown to Advertisers BEFORE booking & payment,
 * demonstrating exact screen position, selected Ad Size ratio, and live creative preview.
 */
@Composable
fun SponsorPlacementPreviewCard(
    lang: AppLang,
    selectedCards: List<PlacementRateCardEntity>,
    adSizeKey: String,
    customW: Int,
    customH: Int,
    businessName: String,
    title: String,
    description: String,
    imageSource: String,
    modifier: Modifier = Modifier
) {
    val primary = selectedCards.firstOrNull()
    val sizeSpec = PlacementRateCardEngine.findSizeSpec(adSizeKey)
    val wPx = if (adSizeKey.equals("CUSTOM", true)) customW else (sizeSpec?.widthPx ?: 300)
    val hPx = if (adSizeKey.equals("CUSTOM", true)) customH else (sizeSpec?.heightPx ?: 200)
    val previewHeight = when (adSizeKey.uppercase()) {
        "320X50" -> 48.dp
        "320X100" -> 76.dp
        "300X200", "RESPONSIVE" -> 124.dp
        "300X250" -> 144.dp
        "336X280" -> 156.dp
        "300X300" -> 165.dp
        "FULL_WIDTH" -> 112.dp
        else -> (140f * (hPx.toFloat() / wPx.coerceAtLeast(1).toFloat())).coerceIn(48f, 170f).dp
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BackgroundIce,
        border = BorderStroke(1.dp, MedicalBlueContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = primary?.let { "${it.placementId} • ${tr(lang, it.placementNameBn, it.placementNameEn)}" }
                        ?: tr(lang, "প্লেসমেন্ট প্রিভিউ", "Placement Preview"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MedicalBlueDark,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(shape = RoundedCornerShape(6.dp), color = MedicalTealLight) {
                    Text(
                        text = "${wPx}×${hPx}px",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MedicalTealDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Mini App Wireframe Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite, RoundedCornerShape(12.dp))
                    .border(1.dp, OutlineSoft, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Visual mini-screen position bar
                Column(
                    modifier = Modifier
                        .width(46.dp)
                        .height(74.dp)
                        .background(BackgroundIce, RoundedCornerShape(8.dp))
                        .border(1.dp, OutlineSoft, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val pid = primary?.placementId.orEmpty()
                    val isTop = pid.endsWith("-01") || pid == PlacementRateCardEngine.HOME_01
                    val isBottom = pid == PlacementRateCardEngine.HOME_07 || pid.endsWith("-04")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(if (isTop) MedicalTeal else Color(0xFFE2E8F0), RoundedCornerShape(3.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(if (!isTop && !isBottom) MedicalTeal else Color(0xFFE2E8F0), RoundedCornerShape(3.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(if (isBottom) MedicalTeal else Color(0xFFE2E8F0), RoundedCornerShape(3.dp))
                    )
                }

                // Live ad creative preview at chosen ratio
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = businessName.ifBlank { tr(lang, "প্রতিষ্ঠানের নাম", "Your Business") },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = tr(lang, "স্পন্সরড", "Sponsored"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MedicalTealDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MedicalBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageSource.isNotBlank()) {
                            AsyncImage(
                                model = imageSource,
                                contentDescription = "Ad creative preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = title.ifBlank { "${sizeSpec?.formatNameEn ?: adSizeKey} (${wPx}×${hPx})" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MedicalBlueDark,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                        }
                    }
                    if (title.isNotBlank() || description.isNotBlank()) {
                        Text(
                            text = title.ifBlank { description },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
