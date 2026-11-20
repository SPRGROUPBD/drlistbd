package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AdConfigEntity
import com.example.data.local.HospitalEntity
import com.example.data.local.LocationEntity
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.SpecialtyEntity
import com.example.data.model.DoctorFullProfile
import com.example.data.seed.SeedData
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.ManagedNativeBannerAd
import com.example.ui.ads.RewardedAdCard
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    lang: AppLang,
    settings: SiteSettingsEntity,
    adConfig: AdConfigEntity,
    isSyncing: Boolean,
    specialties: List<SpecialtyEntity>,
    locations: List<LocationEntity>,
    doctors: List<DoctorFullProfile>,
    hospitals: List<HospitalEntity>,
    favoriteIds: Set<String>,
    isRewardedReady: Boolean,
    isRewardedLoading: Boolean = false,
    onWatchRewardedAd: () -> Unit,
    onRefreshDirectory: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSelectSpecialty: (String) -> Unit,
    onSelectDistrict: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    onNavigateFindDoctors: (availableTodayOnly: Boolean) -> Unit,
    onNavigateFacilitiesTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var quickQuery by remember { mutableStateOf("") }
    // null = All 64 districts; otherwise selected division nameEn from SeedData.bangladeshDivisions
    var selectedDivisionEn by remember { mutableStateOf<String?>(null) }

    val displayedDistricts = remember(selectedDivisionEn) {
        if (selectedDivisionEn == null) {
            SeedData.allDistricts
        } else {
            SeedData.bangladeshDivisions
                .firstOrNull { it.nameEn == selectedDivisionEn }
                ?.districts ?: SeedData.allDistricts
        }
    }
    val availableTodayDocs = remember(doctors) { doctors.filter { it.isAvailableToday() }.take(5) }
    val recentDocs = remember(doctors) { doctors.sortedByDescending { it.doctor.updatedAt }.take(6) }
    val nativeInterval = adConfig.nativeAdInterval.coerceAtLeast(2)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Banner + Search
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceWhite)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_healthcare_1794967702193),
                    contentDescription = "Doctor List BD banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.94f),
                                    Color.White.copy(alpha = 0.72f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = "BANGLADESH DOCTOR DIRECTORY",
                            bgColor = MedicalBlueLight,
                            textColor = MedicalBlueDark,
                            icon = Icons.Rounded.VerifiedUser
                        )
                        IconButton(
                            onClick = onRefreshDirectory,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_refresh_btn")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Sync", tint = MedicalBlueDark)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = settings.siteTitle.ifBlank { "Doctor List BD" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr(lang, settings.taglineBn, settings.taglineEn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = quickQuery,
                        onValueChange = { quickQuery = it },
                        placeholder = {
                            Text(
                                AppStrings.searchHint(lang),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MedicalBlue) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onSearchSubmit(quickQuery) },
                                modifier = Modifier.testTag("home_search_submit")
                            ) {
                                Icon(Icons.Rounded.ArrowForward, contentDescription = "Search", tint = MedicalBlue)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = MedicalBlue,
                            unfocusedBorderColor = OutlineSoft
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(quickQuery) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_search_input")
                    )
                }
            }
        }

        // Quick Action Directory Shortcuts
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    QuickNavChip(
                        icon = Icons.Rounded.EventAvailable,
                        label = AppStrings.availableToday(lang),
                        tint = EmeraldAvailable,
                        onClick = { onNavigateFindDoctors(true) }
                    )
                }
                item {
                    QuickNavChip(
                        icon = Icons.Rounded.Schedule,
                        label = AppStrings.chamberSchedule(lang),
                        tint = MedicalBlue,
                        onClick = { onNavigateFacilitiesTab(3) }
                    )
                }
                item {
                    QuickNavChip(
                        icon = Icons.Rounded.LocalHospital,
                        label = AppStrings.hospitals(lang),
                        tint = MedicalTeal,
                        onClick = { onNavigateFacilitiesTab(0) }
                    )
                }
                item {
                    QuickNavChip(
                        icon = Icons.Rounded.Biotech,
                        label = AppStrings.diagnosticCenters(lang),
                        tint = MedicalBlueDark,
                        onClick = { onNavigateFacilitiesTab(1) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Managed Native Banner Ad #1 (Unity Ads / Start.io)
        item {
            ManagedNativeBannerAd(
                adConfig = adConfig,
                lang = lang,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        // Specialties Strip
        item {
            SectionHeader(
                title = AppStrings.specialties(lang),
                actionLabel = AppStrings.allLabel(lang),
                onAction = { onNavigateFacilitiesTab(2) }
            )
            SpecialtyStrip(
                specialties = specialties,
                selectedId = null,
                lang = lang,
                onSelect = { id -> if (id != null) onSelectSpecialty(id) }
            )
            Spacer(Modifier.height(12.dp))
        }

        // বাংলাদেশের ৮টি বিভাগ অনুযায়ী ৬৪টি জেলার তালিকা (8 Divisions & 64 Districts Browser)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(lang, "বিভাগ অনুযায়ী ৬৪টি জেলা", "64 Districts by Division"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                StatusPill(
                    text = tr(lang, "৮ বিভাগ • ৬৪ জেলা", "8 Div • 64 Dist"),
                    bgColor = MedicalTealLight,
                    textColor = MedicalTealDark
                )
            }
            Spacer(Modifier.height(6.dp))
            // Division Filter Strip (All 8 Divisions of Bangladesh)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_division_row")
            ) {
                item {
                    FilterChip(
                        selected = selectedDivisionEn == null,
                        onClick = { selectedDivisionEn = null },
                        label = {
                            Text(tr(lang, "সব বিভাগ (৬৪)", "All (64)"))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedicalBlue,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceWhite
                        )
                    )
                }
                items(SeedData.bangladeshDivisions, key = { it.nameEn }) { div ->
                    val shortDivLabel = tr(
                        lang,
                        "${div.nameBn.removeSuffix(" বিভাগ")} (${div.districts.size})",
                        "${div.nameEn} (${div.districts.size})"
                    )
                    FilterChip(
                        selected = selectedDivisionEn == div.nameEn,
                        onClick = {
                            selectedDivisionEn = if (selectedDivisionEn == div.nameEn) null else div.nameEn
                        },
                        label = { Text(shortDivLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedicalBlue,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceWhite
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Matching Districts Strip (All 64 Districts or Selected Division's Districts)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_district_row")
            ) {
                items(displayedDistricts, key = { "${it.divisionEn}_${it.nameEn}" }) { dist ->
                    FilterChip(
                        selected = false,
                        onClick = { onSelectDistrict(dist.nameEn) },
                        label = {
                            Text(tr(lang, dist.nameBn, dist.nameEn))
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MedicalTeal,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceWhite)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (doctors.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.MedicalServices,
                    title = tr(lang, "এখনো কোনো ডাক্তার যুক্ত করা হয়নি", "No Doctors Listed Yet"),
                    subtitle = tr(
                        lang,
                        "অ্যাডমিন প্যানেল থেকে ডাক্তার ও চেম্বার যুক্ত করার সাথে সাথে এখানে প্রদর্শিত হবে।",
                        "Doctors and chamber schedules published by the administrator will appear here automatically."
                    ),
                    actionText = tr(lang, "তথ্য রিফ্রেশ করুন", "Refresh Directory"),
                    onAction = onRefreshDirectory
                )
            }
        } else {
            // Available Today
            if (availableTodayDocs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = AppStrings.availableToday(lang),
                        actionLabel = AppStrings.allLabel(lang),
                        onAction = { onNavigateFindDoctors(true) }
                    )
                }
                itemsIndexed(availableTodayDocs, key = { _, it -> "today_${it.doctor.id}" }) { index, doc ->
                    DoctorCard(
                        item = doc,
                        lang = lang,
                        isFavorite = doc.doctor.id in favoriteIds,
                        onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                        onClick = { onDoctorClick(doc.doctor.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    if ((index + 1) % nativeInterval == 0) {
                        ManagedNativeBannerAd(
                            adConfig = adConfig,
                            lang = lang,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Directory Doctors List with periodic Native Banner Ads
            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader(
                    title = tr(lang, "বিশেষজ্ঞ ডাক্তারবৃন্দ", "Specialist Doctors"),
                    actionLabel = AppStrings.allLabel(lang),
                    onAction = { onNavigateFindDoctors(false) }
                )
            }
            val featuredSlice = doctors.take(6)
            itemsIndexed(featuredSlice, key = { _, it -> "dir_${it.doctor.id}" }) { index, doc ->
                DoctorCard(
                    item = doc,
                    lang = lang,
                    isFavorite = doc.doctor.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                    onClick = { onDoctorClick(doc.doctor.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                if ((index + 1) % nativeInterval == 0) {
                    ManagedNativeBannerAd(
                        adConfig = adConfig,
                        lang = lang,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Recently Updated Doctors
            if (recentDocs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader(title = AppStrings.recentlyUpdatedDoctors(lang))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentDocs, key = { "rec_${it.doctor.id}" }) { doc ->
                            Surface(
                                onClick = { onDoctorClick(doc.doctor.id) },
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceWhite,
                                border = BorderStroke(1.dp, OutlineSoft),
                                modifier = Modifier.width(210.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ExternalImageAvatar(
                                        imageUrl = doc.doctor.photoUrl,
                                        contentDescription = tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn),
                                        fallbackIcon = Icons.Rounded.Person,
                                        size = 44.dp,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            doc.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MedicalBlueDark,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Popular Hospitals
        if (hospitals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader(
                    title = AppStrings.popularHospitals(lang),
                    actionLabel = AppStrings.allLabel(lang),
                    onAction = { onNavigateFacilitiesTab(0) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(hospitals.take(6), key = { it.id }) { hosp ->
                        Surface(
                            modifier = Modifier
                                .width(230.dp)
                                .clickable { onNavigateFacilitiesTab(0) },
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, OutlineSoft)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ExternalImageAvatar(
                                        imageUrl = hosp.imageUrl,
                                        contentDescription = tr(lang, hosp.nameBn, hosp.nameEn),
                                        fallbackIcon = Icons.Rounded.LocalHospital,
                                        size = 44.dp,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            tr(lang, hosp.nameBn, hosp.nameEn),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${hosp.upazila}, ${hosp.district}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MedicalTealDark
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    hosp.address,
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

        // Bottom Periodic Native Banner Ad
        item {
            Spacer(Modifier.height(10.dp))
            ManagedNativeBannerAd(
                adConfig = adConfig,
                lang = lang,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Rewarded Video Ad Card (shown when Rewarded format is enabled by Admin)
        if (adConfig.adsEnabled && adConfig.rewardedEnabled) {
            item {
                Spacer(Modifier.height(12.dp))
                RewardedAdCard(
                    lang = lang,
                    isReady = isRewardedReady,
                    isLoading = isRewardedLoading,
                    onWatchClick = onWatchRewardedAd,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun QuickNavChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}
