package com.example.ui.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*

private fun openCampaignUrl(context: Context, rawUrl: String, lang: AppLang) {
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
            tr(lang, "লিংক খোলা সম্ভব হয়নি", "Unable to open link"),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Strict 300x50 Banner Advertisement rendered ONLY in the fixed bottom bar of targeted screens.
 * - Preserves 300x50 design aspect while adapting cleanly to screen width.
 * - Deduplicates visible impressions so recompositions never over-count.
 * - Collapses cleanly (0dp) if creative fails to load.
 */
@Composable
fun FairBanner300x50View(
    campaign: ManagedAdvertiserCampaign,
    screenId: String,
    lang: AppLang,
    onVisibleImpression: (ManagedAdvertiserCampaign, String) -> Unit,
    onClicked: (ManagedAdvertiserCampaign, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageLoaded by remember(campaign.advertisementId, campaign.imageUrl) { mutableStateOf(false) }
    var imageFailed by remember(campaign.advertisementId, campaign.imageUrl) { mutableStateOf(false) }
    var impressionLogged by remember(campaign.advertisementId, screenId) { mutableStateOf(false) }
    var isVisible by remember(campaign.advertisementId, screenId) { mutableStateOf(false) }

    LaunchedEffect(imageLoaded, isVisible, campaign.advertisementId, screenId) {
        if (imageLoaded && isVisible && !impressionLogged) {
            impressionLogged = true
            onVisibleImpression(campaign, screenId)
        }
    }

    if (imageFailed) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .padding(vertical = 3.dp)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val h = coords.size.height.toFloat()
                val w = coords.size.width.toFloat()
                if (h > 0f && w > 0f) {
                    val ratio = (bounds.height / h) * (bounds.width / w)
                    isVisible = ratio >= 0.5f
                }
            }
            .testTag("fair_banner_300x50_${screenId}"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .width(300.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onClicked(campaign, screenId)
                    openCampaignUrl(context, campaign.destinationUrl, lang)
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = campaign.imageUrl,
                    contentDescription = campaign.title.ifBlank { campaign.companyName },
                    contentScale = ContentScale.Crop,
                    onSuccess = { imageLoaded = true },
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    color = Color.Black.copy(alpha = 0.55f),
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
 * Strict 300x200 Native Banner Advertisement inserted after every 3 content cards
 * ONLY on the 4 supported list screens: doctorList, hospitalList, diagnosticList, chamberSchedule.
 * - Never renders broken placeholders if image fails.
 * - Logs visible impression strictly once when rendered & >= 50% visible on screen.
 */
@Composable
fun FairNative300x200Card(
    campaign: ManagedAdvertiserCampaign,
    screenId: String,
    slotIndex: Int,
    lang: AppLang,
    onVisibleImpression: (ManagedAdvertiserCampaign, String) -> Unit,
    onClicked: (ManagedAdvertiserCampaign, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageLoaded by remember(campaign.advertisementId, campaign.imageUrl) { mutableStateOf(false) }
    var imageFailed by remember(campaign.advertisementId, campaign.imageUrl) { mutableStateOf(false) }
    var impressionLogged by remember(campaign.advertisementId, screenId, slotIndex) { mutableStateOf(false) }
    var isVisible by remember(campaign.advertisementId, screenId, slotIndex) { mutableStateOf(false) }

    LaunchedEffect(imageLoaded, isVisible, campaign.advertisementId, screenId, slotIndex) {
        if (imageLoaded && isVisible && !impressionLogged) {
            impressionLogged = true
            onVisibleImpression(campaign, screenId)
        }
    }

    if (imageFailed) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val h = coords.size.height.toFloat()
                val w = coords.size.width.toFloat()
                if (h > 0f && w > 0f) {
                    val ratio = (bounds.height / h) * (bounds.width / w)
                    isVisible = ratio >= 0.5f
                }
            }
            .testTag("fair_native_300x200_${screenId}_slot_$slotIndex"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.25f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .width(300.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    onClicked(campaign, screenId)
                    openCampaignUrl(context, campaign.destinationUrl, lang)
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = campaign.imageUrl,
                    contentDescription = campaign.title.ifBlank { campaign.companyName },
                    contentScale = ContentScale.Crop,
                    onSuccess = { imageLoaded = true },
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
                // Sponsored badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = tr(lang, "স্পন্সরড", "Sponsored"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                // Compact bottom caption strip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (campaign.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = campaign.logoUrl,
                                contentDescription = campaign.companyName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceWhite)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = campaign.title.ifBlank { campaign.companyName },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (campaign.companyName.isNotBlank()) {
                                Text(
                                    text = campaign.companyName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = tr(lang, "বিস্তারিত", "Open"),
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
 * Strict 300x200 App Launch Popup Advertisement.
 * - Shown ONLY on genuine app launch.
 * - Includes clearly visible Close (X) button for immediate non-blocking dismissal.
 * - Counts impression only after image loads cleanly.
 */
@Composable
fun FairAppOpenPopup300x200Dialog(
    campaign: ManagedAdvertiserCampaign,
    lang: AppLang,
    onVisibleImpression: (ManagedAdvertiserCampaign, String) -> Unit,
    onClicked: (ManagedAdvertiserCampaign, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var imageLoaded by remember(campaign.advertisementId) { mutableStateOf(false) }
    var imageFailed by remember(campaign.advertisementId) { mutableStateOf(false) }
    var impressionLogged by remember(campaign.advertisementId) { mutableStateOf(false) }

    LaunchedEffect(imageFailed) {
        if (imageFailed) onDismiss()
    }
    LaunchedEffect(imageLoaded) {
        if (imageLoaded && !impressionLogged) {
            impressionLogged = true
            onVisibleImpression(campaign, AdTargetScreens.APP_LAUNCH)
        }
    }

    if (imageFailed) return

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
                .testTag("fair_popup_300x200_dialog")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (campaign.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = campaign.logoUrl,
                                contentDescription = campaign.companyName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Column {
                            Text(
                                text = campaign.companyName.ifBlank { tr(lang, "স্পন্সর", "Sponsored") },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tr(lang, "স্পন্সরড বিজ্ঞাপন", "Sponsored Advertisement"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MedicalTealDark
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("fair_popup_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = tr(lang, "বন্ধ করুন", "Close"),
                            tint = TextPrimary
                        )
                    }
                }

                // Exact 300x200 dp creative container
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundIce,
                    modifier = Modifier
                        .width(300.dp)
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onClicked(campaign, AdTargetScreens.APP_LAUNCH)
                            openCampaignUrl(context, campaign.destinationUrl, lang)
                            onDismiss()
                        }
                ) {
                    AsyncImage(
                        model = campaign.imageUrl,
                        contentDescription = campaign.title.ifBlank { campaign.companyName },
                        contentScale = ContentScale.Crop,
                        onSuccess = { imageLoaded = true },
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
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
                                onClicked(campaign, AdTargetScreens.APP_LAUNCH)
                                openCampaignUrl(context, campaign.destinationUrl, lang)
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
