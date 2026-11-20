package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AdConfigEntity
import com.example.data.local.AppointmentRequestEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.SponsorEntity
import com.example.data.model.DoctorFullProfile
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.ManagedNativeBannerAd
import com.example.ui.ads.NativeSponsorAdCard
import com.example.ui.ads.RewardedAdCard
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.components.DoctorCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*

@Composable
fun PatientPortalScreen(
    lang: AppLang,
    adConfig: AdConfigEntity,
    sponsors: List<SponsorEntity> = emptyList(),
    isOnline: Boolean = true,
    onSponsorDisplayed: (String, Boolean) -> Unit = { _, _ -> },
    onSponsorClicked: (String, Boolean) -> Unit = { _, _ -> },
    isSyncing: Boolean,
    favoriteDoctors: List<DoctorFullProfile>,
    notifications: List<NotificationEntity>,
    isRewardedReady: Boolean,
    isRewardedLoading: Boolean = false,
    onWatchRewardedAd: () -> Unit,
    onRefreshDirectory: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onNavigateInfo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Directory Sync & Offline Storage Card (Zero Login Required)
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tr(lang, "আমার সেভড তালিকা", "My Saved Directory"),
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                tr(
                                    lang,
                                    "কোনো লগইন ছাড়াই আপনার প্রিয় ডাক্তার ও নোটিশ সংরক্ষিত থাকে।",
                                    "Favorite doctors & alerts are saved locally on your device — no login required."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRefreshDirectory,
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sync_directory_btn")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(tr(lang, "সর্বশেষ ডিরেক্টরি আপডেট করুন", "Sync Latest Directory"))
                    }
                }
            }
        }

        // Rewarded Ad Support Card (Online Only)
        if (isOnline && adConfig.adsEnabled && adConfig.rewardedEnabled) {
            item {
                RewardedAdCard(
                    lang = lang,
                    isReady = isRewardedReady,
                    isLoading = isRewardedLoading,
                    onWatchClick = onWatchRewardedAd
                )
            }
        }

        // Sub-Tabs: Favorites | Notifications
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("${AppStrings.saved(lang)} (${favoriteDoctors.size})") }
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text(tr(lang, "নোটিশ (${notifications.size})", "Alerts (${notifications.size})")) }
                )
            }
        }

        when (activeTab) {
            0 -> {
                if (favoriteDoctors.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.FavoriteBorder,
                            title = tr(lang, "কোনো ডাক্তার সেভ করা নেই", "No Saved Doctors"),
                            subtitle = tr(lang, "ডাক্তারের কার্ডে হার্ট আইকনে ট্যাপ করে সেভ করুন।", "Tap the heart icon on any doctor card to save.")
                        )
                    }
                } else {
                    itemsIndexed(favoriteDoctors, key = { _, it -> it.doctor.id }) { idx, doc ->
                        DoctorCard(
                            item = doc,
                            lang = lang,
                            isFavorite = true,
                            onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                            onClick = { onDoctorClick(doc.doctor.id) }
                        )
                        if (SponsorCampaignSelector.shouldInsertNativeSponsorAfter(idx, adConfig.nativeSponsorInterval)) {
                            Spacer(Modifier.height(10.dp))
                            NativeSponsorAdCard(
                                sponsors = sponsors,
                                adConfig = adConfig,
                                pageKey = SponsorCampaignSelector.PAGE_PORTAL,
                                slotIndex = idx / adConfig.nativeSponsorInterval.coerceIn(4, 6),
                                isOnline = isOnline,
                                lang = lang,
                                onSponsorDisplayed = onSponsorDisplayed,
                                onSponsorClicked = onSponsorClicked
                            )
                        }
                    }
                }
            }

            else -> {
                if (notifications.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.NotificationsNone,
                            title = tr(lang, "কোনো নোটিশ নেই", "No Notifications"),
                            subtitle = tr(lang, "নতুন কোনো আপডেট বা ঘোষণা এলে এখানে দেখা যাবে।", "Directory notices and updates will appear here.")
                        )
                    }
                } else {
                    items(notifications, key = { it.id }) { n ->
                        Surface(
                            onClick = { onMarkNotificationRead(n.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (n.isRead) SurfaceWhite else MedicalBlueLight.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, OutlineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(tr(lang, n.titleBn, n.titleEn), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(tr(lang, n.messageBn, n.messageEn), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Native Sponsor Card (Online + Offline Cached) & Network Native Banner (Online Only)
        item {
            NativeSponsorAdCard(
                sponsors = sponsors,
                adConfig = adConfig,
                pageKey = SponsorCampaignSelector.PAGE_PORTAL,
                slotIndex = 0,
                isOnline = isOnline,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked
            )
            Spacer(Modifier.height(8.dp))
            ManagedNativeBannerAd(adConfig = adConfig, lang = lang)
        }

        // Information Links
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { onNavigateInfo("about") }) { Text(AppStrings.aboutUs(lang)) }
                        TextButton(onClick = { onNavigateInfo("contact") }) { Text(AppStrings.contactUs(lang)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { onNavigateInfo("privacy") }) { Text(AppStrings.privacyPolicy(lang)) }
                        TextButton(onClick = { onNavigateInfo("terms") }) { Text(AppStrings.termsConditions(lang)) }
                    }
                }
            }
        }
    }
}
