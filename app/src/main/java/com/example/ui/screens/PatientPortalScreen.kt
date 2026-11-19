package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.local.AppointmentRequestEntity
import com.example.data.local.NotificationEntity
import com.example.data.model.DoctorFullProfile
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.RewardedAdCard
import com.example.ui.ads.StartIoNativeBannerCard
import com.example.ui.components.DoctorCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*

@Composable
fun PatientPortalScreen(
    lang: AppLang,
    isSyncing: Boolean,
    favoriteDoctors: List<DoctorFullProfile>,
    appointments: List<AppointmentRequestEntity>,
    notifications: List<NotificationEntity>,
    isRewardedReady: Boolean,
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
                                    "কোনো লগইন ছাড়াই আপনার প্রিয় ডাক্তার ও সিরিয়াল অনুরোধ সংরক্ষিত থাকে।",
                                    "Favorites & serial requests are saved locally on your device — no login required."
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

        // Start.io Rewarded Ad Support Card
        item {
            RewardedAdCard(
                lang = lang,
                isReady = isRewardedReady,
                onWatchClick = onWatchRewardedAd
            )
        }

        // Sub-Tabs: Favorites | Appointments | Notifications
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
                    label = { Text(tr(lang, "সিরিয়াল (${appointments.size})", "Requests (${appointments.size})")) }
                )
                FilterChip(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
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
                    items(favoriteDoctors, key = { it.doctor.id }) { doc ->
                        DoctorCard(
                            item = doc,
                            lang = lang,
                            isFavorite = true,
                            onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                            onClick = { onDoctorClick(doc.doctor.id) }
                        )
                    }
                }
            }

            1 -> {
                if (appointments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.EventNote,
                            title = tr(lang, "কোনো অ্যাপয়েন্টমেন্ট অনুরোধ নেই", "No Appointment Requests"),
                            subtitle = tr(lang, "ডাক্তারের প্রোফাইল থেকে সিরিয়ালের অনুরোধ পাঠাতে পারেন।", "You can request a serial from a doctor's chamber card.")
                        )
                    }
                } else {
                    items(appointments, key = { it.id }) { apt ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, OutlineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(apt.patientName, style = MaterialTheme.typography.titleMedium)
                                    val (bg, fg) = when (apt.status) {
                                        "CONFIRMED" -> EmeraldSoft to Color(0xFF065F46)
                                        "REJECTED" -> RoseSoft to RoseAlert
                                        else -> AmberSoft to Color(0xFF92400E)
                                    }
                                    StatusPill(apt.status, bg, fg)
                                }
                                Text("${apt.preferredDay} • ${apt.preferredSlot}", style = MaterialTheme.typography.bodySmall, color = MedicalBlueDark)
                                Text(apt.patientPhone, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                if (apt.notes.isNotBlank()) Text(apt.notes, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
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

        // Start.io Native Banner Ad
        item {
            StartIoNativeBannerCard(lang = lang)
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
