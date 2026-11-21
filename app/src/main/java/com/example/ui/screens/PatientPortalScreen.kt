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
import com.example.data.local.NotificationEntity
import com.example.data.local.SponsorEntity
import com.example.data.model.DoctorFullProfile
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.NativeSponsorAdCard
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.components.DoctorCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.*

@Composable
fun PatientPortalScreen(
    lang: AppLang,
    adConfig: AdConfigEntity,
    sponsors: List<SponsorEntity> = emptyList(),
    isOnline: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    onSponsorDisplayed: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onSponsorClicked: (String, Boolean) -> Unit = { _, _ -> },
    isSyncing: Boolean,
    favoriteDoctors: List<DoctorFullProfile>,
    onRefreshDirectory: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .testTag("favorite_doctors_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Favorite Doctors Header
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MedicalBlueLight,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = MedicalBlueDark,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = tr(lang, "প্রিয় ডাক্তার তালিকা", "Favorite Doctors"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = tr(
                                    lang,
                                    "${favoriteDoctors.size} জন ডাক্তার সেভ করা আছে",
                                    "${favoriteDoctors.size} saved doctor(s)"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onRefreshDirectory,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("sync_directory_btn")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = tr(lang, "আপডেট করুন", "Refresh")
                            )
                        }
                    }
                }
            }
        }

        // Favorite Doctors List ONLY
        if (favoriteDoctors.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = tr(lang, "কোনো প্রিয় ডাক্তার সেভ করা নেই", "No Favorite Doctors Yet"),
                    subtitle = tr(
                        lang,
                        "যেকোনো ডাক্তারের কার্ডে হার্ট (♥) আইকনে ট্যাপ করে আপনার প্রিয় তালিকায় সেভ করুন।",
                        "Tap the heart icon on any doctor card to save them to your favorites."
                    )
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
                        nowMs = nowMs,
                        lang = lang,
                        onSponsorDisplayed = onSponsorDisplayed,
                        onSponsorClicked = onSponsorClicked
                    )
                }
            }
        }
    }
}
