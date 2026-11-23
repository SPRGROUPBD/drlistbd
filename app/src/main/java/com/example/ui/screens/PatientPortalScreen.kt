package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.auth.GoogleUserAccount
import com.example.data.local.AdConfigEntity
import com.example.data.local.ChamberEntity
import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.data.local.SponsorEntity
import com.example.data.model.DoctorFullProfile
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.ads.AdCampaignItem
import com.example.ui.ads.AdPlacements
import com.example.ui.ads.AdvertisingRotationEngine
import com.example.ui.ads.NativeAd300x100Card
import com.example.ui.ads.NativeSponsorAdCard
import com.example.ui.ads.PlacementGlobalSettings
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.components.ContinueWithGoogleButton
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
    visitorAccount: GoogleUserAccount? = null,
    favoriteDoctors: List<DoctorFullProfile>,
    favoriteHospitals: List<HospitalEntity> = emptyList(),
    favoriteDiagnostics: List<DiagnosticCenterEntity> = emptyList(),
    favoriteChambers: List<Pair<DoctorFullProfile, ChamberEntity>> = emptyList(),
    onGoogleSignInSuccess: (GoogleUserAccount) -> Unit = {},
    onGoogleSignInError: (String) -> Unit = {},
    onVisitorSignOut: () -> Unit = {},
    onSyncFavorites: () -> Unit = {},
    onRefreshDirectory: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHospitalFavorite: (String, String) -> Unit = { _, _ -> },
    onToggleDiagnosticFavorite: (String, String) -> Unit = { _, _ -> },
    onToggleChamberFavorite: (String, String) -> Unit = { _, _ -> },
    onSelectFacility: (String) -> Unit = {},
    onDoctorClick: (String) -> Unit,
    adCampaigns: List<AdCampaignItem> = emptyList(),
    adPlacementSettings: PlacementGlobalSettings = PlacementGlobalSettings(),
    favoriteRotationBase: Long = 0L,
    onNativeAdImpression: (AdCampaignItem, String) -> Unit = { _, _ -> },
    onNativeAdClick: (AdCampaignItem, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0=Doctors, 1=Hospitals, 2=Diagnostics, 3=Chambers

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .testTag("favorite_doctors_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Visitor Google Profile Card OR Sign-In CTA
        item {
            if (visitorAccount != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth().testTag("visitor_profile_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (visitorAccount.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = visitorAccount.photoUrl,
                                    contentDescription = visitorAccount.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(MedicalBlueLight)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MedicalBlueLight,
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = MedicalBlueDark)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = visitorAccount.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = visitorAccount.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = EmeraldAvailable, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = tr(lang, "মাল্টি-ডিভাইস সিঙ্ক চালু আছে", "Multi-device cloud sync active"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldAvailable,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    onSyncFavorites()
                                    onRefreshDirectory()
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("sync_directory_btn")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "সিঙ্ক করুন", "Sync Now"))
                            }
                            OutlinedButton(
                                onClick = onVisitorSignOut,
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("visitor_logout_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "লগআউট", "Sign Out"))
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth().testTag("visitor_signin_banner_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MedicalBlueLight,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MedicalBlueDark)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr(lang, "ভিজিটর ড্যাশবোর্ড ও প্রিয় তালিকা", "Visitor Dashboard & Saved Favorites"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = tr(
                                        lang,
                                        "প্রিয় ডাক্তার, হাসপাতাল, ডায়াগনস্টিক ও চেম্বার সব ডিভাইসে সিঙ্ক রাখতে Google দিয়ে সাইন-ইন করুন।",
                                        "Sign in with Google to save doctors, hospitals, diagnostic centers & chambers and sync across all devices."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        ContinueWithGoogleButton(
                            label = tr(lang, "Google দিয়ে চালিয়ে যান", "Continue with Google"),
                            enabled = isOnline,
                            onSuccess = onGoogleSignInSuccess,
                            onError = onGoogleSignInError,
                            testTagName = "visitor_dashboard_google_btn"
                        )
                    }
                }
            }
        }

        // Category Filter Chips (Doctors / Hospitals / Diagnostics / Chambers)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        leadingIcon = { Icon(Icons.Rounded.MedicalServices, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(tr(lang, "ডাক্তার (${favoriteDoctors.size})", "Doctors (${favoriteDoctors.size})")) },
                        modifier = Modifier.testTag("fav_tab_doctors")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        leadingIcon = { Icon(Icons.Rounded.LocalHospital, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(tr(lang, "হাসপাতাল (${favoriteHospitals.size})", "Hospitals (${favoriteHospitals.size})")) },
                        modifier = Modifier.testTag("fav_tab_hospitals")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        leadingIcon = { Icon(Icons.Rounded.Biotech, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(tr(lang, "ডায়াগনস্টিক (${favoriteDiagnostics.size})", "Diagnostics (${favoriteDiagnostics.size})")) },
                        modifier = Modifier.testTag("fav_tab_diagnostics")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedSection == 3,
                        onClick = { selectedSection = 3 },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(tr(lang, "চেম্বার (${favoriteChambers.size})", "Chambers (${favoriteChambers.size})")) },
                        modifier = Modifier.testTag("fav_tab_chambers")
                    )
                }
            }
        }

        when (selectedSection) {
            0 -> {
                if (favoriteDoctors.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.FavoriteBorder,
                            title = tr(lang, "কোনো প্রিয় ডাক্তার সেভ করা নেই", "No Favorite Doctors Yet"),
                            subtitle = tr(
                                lang,
                                "যেকোনো ডাক্তারের কার্ডে হার্ট (♥) আইকনে ট্যাপ করে আপনার প্রিয় তালিকায় সেভ করুন।",
                                "Tap the heart icon on any doctor card to save them to your cloud-synced favorites."
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
                        if (AdvertisingRotationEngine.shouldInsertNativeAfterCard(idx)) {
                            FavoriteListNativeAdSlot(
                                slotIndex = AdvertisingRotationEngine.nativeSlotForCardIndex(idx),
                                campaigns = adCampaigns,
                                placementSettings = adPlacementSettings,
                                baseOffset = favoriteRotationBase,
                                nowMs = nowMs,
                                lang = lang,
                                onVisibleImpression = onNativeAdImpression,
                                onClicked = onNativeAdClick
                            )
                        }
                    }
                }
            }

            1 -> {
                if (favoriteHospitals.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.LocalHospital,
                            title = tr(lang, "কোনো প্রিয় হাসপাতাল সেভ করা নেই", "No Favorite Hospitals Yet"),
                            subtitle = tr(
                                lang,
                                "হাসপাতাল তালিকা থেকে যেকোনো হাসপাতালের হার্ট আইকনে ট্যাপ করে সেভ করুন।",
                                "Tap the heart icon on any hospital in Facilities to save it here."
                            )
                        )
                    }
                } else {
                    itemsIndexed(favoriteHospitals, key = { _, it -> it.id }) { idx, h ->
                        SavedItemCard(
                            title = tr(lang, h.nameBn, h.nameEn),
                            subtitle = "${h.upazila}, ${h.district}",
                            detail = h.address,
                            icon = Icons.Rounded.LocalHospital,
                            actionLabel = tr(lang, "ডাক্তার দেখুন", "View Doctors"),
                            onAction = { onSelectFacility(h.nameEn.removePrefix("[DEMO] ")) },
                            onRemove = { onToggleHospitalFavorite(h.id, h.nameEn) }
                        )
                        if (AdvertisingRotationEngine.shouldInsertNativeAfterCard(idx)) {
                            FavoriteListNativeAdSlot(
                                slotIndex = AdvertisingRotationEngine.nativeSlotForCardIndex(idx),
                                campaigns = adCampaigns,
                                placementSettings = adPlacementSettings,
                                baseOffset = favoriteRotationBase,
                                nowMs = nowMs,
                                lang = lang,
                                onVisibleImpression = onNativeAdImpression,
                                onClicked = onNativeAdClick
                            )
                        }
                    }
                }
            }

            2 -> {
                if (favoriteDiagnostics.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Biotech,
                            title = tr(lang, "কোনো প্রিয় ডায়াগনস্টিক সেভ করা নেই", "No Favorite Diagnostic Centers Yet"),
                            subtitle = tr(
                                lang,
                                "ডায়াগনস্টিক সেন্টারের কার্ডে হার্ট আইকনে ট্যাপ করে সেভ করুন।",
                                "Tap the heart icon on any diagnostic center to save it here."
                            )
                        )
                    }
                } else {
                    itemsIndexed(favoriteDiagnostics, key = { _, it -> it.id }) { idx, dc ->
                        SavedItemCard(
                            title = tr(lang, dc.nameBn, dc.nameEn),
                            subtitle = "${dc.upazila}, ${dc.district}",
                            detail = dc.address,
                            icon = Icons.Rounded.Biotech,
                            actionLabel = tr(lang, "ডাক্তার দেখুন", "View Doctors"),
                            onAction = { onSelectFacility(dc.nameEn.removePrefix("[DEMO] ")) },
                            onRemove = { onToggleDiagnosticFavorite(dc.id, dc.nameEn) }
                        )
                        if (AdvertisingRotationEngine.shouldInsertNativeAfterCard(idx)) {
                            FavoriteListNativeAdSlot(
                                slotIndex = AdvertisingRotationEngine.nativeSlotForCardIndex(idx),
                                campaigns = adCampaigns,
                                placementSettings = adPlacementSettings,
                                baseOffset = favoriteRotationBase,
                                nowMs = nowMs,
                                lang = lang,
                                onVisibleImpression = onNativeAdImpression,
                                onClicked = onNativeAdClick
                            )
                        }
                    }
                }
            }

            3 -> {
                if (favoriteChambers.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Schedule,
                            title = tr(lang, "কোনো প্রিয় চেম্বার সেভ করা নেই", "No Favorite Chambers Yet"),
                            subtitle = tr(
                                lang,
                                "চেম্বার সময়সূচী ট্যাবে হার্ট আইকনে ট্যাপ করে আপনার পছন্দের চেম্বার সেভ করুন।",
                                "Tap the heart icon in Chamber Schedule to bookmark chambers for quick access."
                            )
                        )
                    }
                } else {
                    itemsIndexed(favoriteChambers, key = { _, it -> it.second.id }) { idx, (doc, ch) ->
                        SavedItemCard(
                            title = tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn),
                            subtitle = ch.facilityName,
                            detail = "${ch.upazila}, ${ch.district} • ${ch.fullAddress}",
                            icon = Icons.Rounded.Schedule,
                            actionLabel = tr(lang, "প্রোফাইল", "Open Doctor"),
                            onAction = { onDoctorClick(doc.doctor.id) },
                            onRemove = { onToggleChamberFavorite(ch.id, ch.facilityName) }
                        )
                        if (AdvertisingRotationEngine.shouldInsertNativeAfterCard(idx)) {
                            FavoriteListNativeAdSlot(
                                slotIndex = AdvertisingRotationEngine.nativeSlotForCardIndex(idx),
                                campaigns = adCampaigns,
                                placementSettings = adPlacementSettings,
                                baseOffset = favoriteRotationBase,
                                nowMs = nowMs,
                                lang = lang,
                                onVisibleImpression = onNativeAdImpression,
                                onClicked = onNativeAdClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteListNativeAdSlot(
    slotIndex: Int,
    campaigns: List<AdCampaignItem>,
    placementSettings: PlacementGlobalSettings,
    baseOffset: Long,
    nowMs: Long,
    lang: AppLang,
    onVisibleImpression: (AdCampaignItem, String) -> Unit,
    onClicked: (AdCampaignItem, String) -> Unit
) {
    val nativeAd = remember(campaigns, placementSettings, slotIndex, baseOffset, nowMs / 60_000L) {
        AdvertisingRotationEngine.selectNativeAdForSlot(
            campaigns = campaigns,
            placementTarget = AdPlacements.FAVORITE_LIST,
            slotIndex = slotIndex,
            baseOffset = baseOffset,
            nowMs = nowMs,
            settings = placementSettings
        )
    } ?: return

    Spacer(Modifier.height(10.dp))
    NativeAd300x100Card(
        campaign = nativeAd,
        queueKey = AdvertisingRotationEngine.QUEUE_FAVORITE_NATIVE,
        slotIndex = slotIndex,
        lang = lang,
        onVisibleImpression = onVisibleImpression,
        onClicked = onClicked
    )
}

@Composable
private fun SavedItemCard(
    title: String,
    subtitle: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MedicalBlueLight,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MedicalBlueDark)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MedicalTealDark)
                    if (detail.isNotBlank()) {
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Remove Favorite", tint = RoseAlert)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
