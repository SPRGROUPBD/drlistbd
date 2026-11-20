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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.AdConfigEntity
import com.example.data.local.SponsorEntity
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*

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
    isOnline: Boolean,
    onClickTracked: (String) -> Unit
) {
    val phone = sponsor.contactNumber.trim()
    if (phone.isEmpty()) return
    if (isOnline) {
        onClickTracked(sponsor.id)
    }
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

/**
 * Responsive Direct Sponsor Banner component displayed on every app page.
 * Works seamlessly in both Online Mode and Offline Mode (using persisted local cached images & metadata).
 */
@Composable
fun SponsorBannerView(
    sponsors: List<SponsorEntity>,
    adConfig: AdConfigEntity,
    pageKey: String,
    isOnline: Boolean,
    lang: AppLang,
    onSponsorDisplayed: (sponsorId: String, isOnline: Boolean) -> Unit,
    onSponsorClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled || !adConfig.sponsorBannerEnabled) return

    val sponsor = remember(sponsors, pageKey) {
        SponsorCampaignSelector.selectBannerSponsor(
            sponsors = sponsors,
            pageKey = pageKey,
            rotationTick = pageKey.hashCode()
        )
    } ?: return

    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }

    LaunchedEffect(sponsor.id, pageKey, isOnline) {
        onSponsorDisplayed(sponsor.id, isOnline)
    }

    Surface(
        color = SurfaceWhite,
        border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.35f)),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
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
            // Promotional banner thumbnail or sponsor logo
            if (bannerModel != null) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MedicalBlueLight)
                ) {
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Small sponsor logo badge overlay
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
                    Box(
                        modifier = Modifier
                            .background(MedicalTeal, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = tr(lang, "স্পন্সরড", "Sponsored"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = sponsor.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isOnline) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = "Offline cached sponsor",
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = sponsor.promoTitle.ifBlank { sponsor.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action buttons (Visit Sponsor + optional Call Sponsor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sponsor.contactNumber.isNotBlank()) {
                    FilledTonalIconButton(
                        onClick = {
                            dialSponsorPhone(context, sponsor, isOnline) { id ->
                                onSponsorClicked(id, isOnline)
                            }
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
 * Direct Sponsor App Open Popup displayed when the app launches.
 * - Includes Sponsor Image, Logo, Sponsor Name, Promotional Text, CTA, and clearly visible Close (X) button.
 * - Never forces clicks or blocks navigation; works offline from local cache; hidden if no valid sponsor exists.
 */
@Composable
fun AppOpenSponsorPopupDialog(
    sponsor: SponsorEntity,
    isOnline: Boolean,
    lang: AppLang,
    onDisplayed: (sponsorId: String, isOnline: Boolean) -> Unit,
    onClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }

    LaunchedEffect(sponsor.id, isOnline) {
        onDisplayed(sponsor.id, isOnline)
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
                .testTag("app_open_sponsor_popup")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Hero Banner Area with clearly visible Close (X) button & Sponsored label
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(MedicalBlueDark, MedicalTealDark)
                            )
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

                    // Sponsored badge top-left
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tr(lang, "স্পন্সরড বিজ্ঞাপন", "Sponsored"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (!isOnline) {
                                Text(
                                    text = "• Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFBAE6FD)
                                )
                            }
                        }
                    }

                    // Clearly visible Close (X) button (48dp touch target) top-right
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

                // Sponsor Identity & Promotional Copy
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
                                    dialSponsorPhone(context, sponsor, isOnline) { id ->
                                        onClicked(id, isOnline)
                                    }
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

                    // Always provide non-blocking continue button
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
 * Reusable Native Sponsor Advertisement Card displayed between content cards (every 4, 5, or 6 cards).
 * - Visually distinct with clear "Sponsored" badge.
 * - Rotates active sponsors by priority & schedule, preventing duplicate sponsors in short sections.
 * - Works seamlessly offline using cached images & metadata.
 */
@Composable
fun NativeSponsorAdCard(
    sponsors: List<SponsorEntity>,
    adConfig: AdConfigEntity,
    pageKey: String,
    slotIndex: Int,
    isOnline: Boolean,
    lang: AppLang,
    onSponsorDisplayed: (sponsorId: String, isOnline: Boolean) -> Unit,
    onSponsorClicked: (sponsorId: String, isOnline: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled || !adConfig.nativeSponsorEnabled) return

    val sponsor = remember(sponsors, pageKey, slotIndex) {
        SponsorCampaignSelector.selectNativeSponsorForSlot(
            sponsors = sponsors,
            pageKey = pageKey,
            slotIndex = slotIndex
        )
    } ?: return

    val context = LocalContext.current
    val bannerModel = remember(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline) {
        SponsorOfflineCacheManager.resolveImageModel(sponsor.localBannerPath, sponsor.bannerImageUrl, isOnline)
    }

    LaunchedEffect(sponsor.id, pageKey, slotIndex, isOnline) {
        onSponsorDisplayed(sponsor.id, isOnline)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.2.dp, MedicalTeal.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("native_sponsor_ad_card_${slotIndex}")
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
            // Header row: Logo + Sponsor Name + Clear Sponsored Badge
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
                Surface(
                    color = MedicalTealLight,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = tr(lang, "স্পন্সরড", "Sponsored"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MedicalTealDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Promotional Banner Image (Cached locally for offline)
            if (bannerModel != null) {
                AsyncImage(
                    model = bannerModel,
                    contentDescription = sponsor.promoTitle.ifBlank { sponsor.name },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            // Description + CTA Row
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
                                dialSponsorPhone(context, sponsor, isOnline) { id ->
                                    onSponsorClicked(id, isOnline)
                                }
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
