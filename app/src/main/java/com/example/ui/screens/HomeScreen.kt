package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AdConfigEntity
import com.example.data.local.HospitalEntity
import com.example.data.local.LocationEntity
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.SpecialtyEntity
import com.example.data.local.SponsorEntity
import com.example.data.model.DoctorFullProfile
import com.example.data.remote.FirestoreSliderService
import com.example.data.remote.HomeAutoSliderItem
import com.example.data.remote.SliderQuickFilter
import com.example.data.seed.SeedData
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    lang: AppLang,
    settings: SiteSettingsEntity,
    adConfig: AdConfigEntity,
    sponsors: List<SponsorEntity> = emptyList(),
    rateCards: List<PlacementRateCardEntity> = PlacementRateCardEngine.defaultRateCards(),
    isOnline: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    onSponsorDisplayed: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onSponsorClicked: (String, Boolean) -> Unit = { _, _ -> },
    isSyncing: Boolean,
    specialties: List<SpecialtyEntity>,
    locations: List<LocationEntity>,
    doctors: List<DoctorFullProfile>,
    hospitals: List<HospitalEntity>,
    favoriteIds: Set<String>,
    onRefreshDirectory: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSelectSpecialty: (String) -> Unit,
    onSelectDistrict: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    onNavigateFindDoctors: (availableTodayOnly: Boolean) -> Unit,
    onNavigateFacilitiesTab: (Int) -> Unit,
    autoSliderSlides: List<HomeAutoSliderItem> = FirestoreSliderService.defaultBangladeshDemoSlides(),
    homeSliderAutoSlideMs: Long = 4000L,
    inlineSearchQuery: String = "",
    onSliderItemVisible: (HomeAutoSliderItem) -> Unit = {},
    onSliderViewDetails: (HomeAutoSliderItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Quick-Filter pill selection that filters the Auto Slider in real time
    var activeQuickFilter by remember { mutableStateOf(SliderQuickFilter.ALL) }

    // Space-saving collapsible single-row dropdown state for "বিভাগ অনুযায়ী ৬৪টি জেলা"
    var isDistrictDropdownExpanded by remember { mutableStateOf(false) }
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

    val filteredSliderItems = remember(autoSliderSlides, activeQuickFilter, inlineSearchQuery) {
        FirestoreSliderService.filterSlides(
            slides = autoSliderSlides,
            quickFilter = activeQuickFilter,
            searchQuery = inlineSearchQuery
        )
    }

    val availableTodayDocs = remember(doctors) { doctors.filter { it.isAvailableToday() }.take(5) }
    val recentDocs = remember(doctors) { doctors.sortedByDescending { it.doctor.updatedAt }.take(6) }
    val fallbackFeaturedDoctorSlide = remember(autoSliderSlides) {
        autoSliderSlides.firstOrNull { it.category == "DOCTOR" }
            ?: FirestoreSliderService.defaultBangladeshDemoSlides().first()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
    ) {
        // 1. COMPACT AUTO SLIDER (Placed Directly Beneath the Top App Bar)
        item(key = "home_compact_auto_slider_item") {
            CompactHomeAutoSlider(
                slides = filteredSliderItems,
                lang = lang,
                autoSlideIntervalMs = homeSliderAutoSlideMs,
                onViewDetails = onSliderViewDetails,
                onSlideVisible = onSliderItemVisible,
                onClearFilter = if (activeQuickFilter != SliderQuickFilter.ALL) {
                    { activeQuickFilter = SliderQuickFilter.ALL }
                } else null
            )
        }

        // 2. QUICK-FILTER PILLS ROW (Filters the Auto Slider on tap)
        item(key = "home_quick_filter_pills") {
            Spacer(Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_quick_filter_row")
            ) {
                item {
                    QuickFilterPill(
                        icon = Icons.Rounded.EventAvailable,
                        label = tr(lang, "আজ বসবেন", "Sitting Today"),
                        selected = activeQuickFilter == SliderQuickFilter.TODAY,
                        accentColor = EmeraldAvailable,
                        onClick = {
                            activeQuickFilter = if (activeQuickFilter == SliderQuickFilter.TODAY) {
                                SliderQuickFilter.ALL
                            } else {
                                SliderQuickFilter.TODAY
                            }
                        },
                        modifier = Modifier.testTag("quick_filter_today")
                    )
                }
                item {
                    QuickFilterPill(
                        icon = Icons.Rounded.Schedule,
                        label = tr(lang, "চেম্বার সময়সূচী", "Chamber Schedule"),
                        selected = activeQuickFilter == SliderQuickFilter.SCHEDULE,
                        accentColor = MedicalBlue,
                        onClick = {
                            activeQuickFilter = if (activeQuickFilter == SliderQuickFilter.SCHEDULE) {
                                SliderQuickFilter.ALL
                            } else {
                                SliderQuickFilter.SCHEDULE
                            }
                        },
                        modifier = Modifier.testTag("quick_filter_schedule")
                    )
                }
                item {
                    QuickFilterPill(
                        icon = Icons.Rounded.LocalHospital,
                        label = tr(lang, "হাসপাতাল", "Hospitals"),
                        selected = activeQuickFilter == SliderQuickFilter.HOSPITAL,
                        accentColor = MedicalTeal,
                        onClick = {
                            activeQuickFilter = if (activeQuickFilter == SliderQuickFilter.HOSPITAL) {
                                SliderQuickFilter.ALL
                            } else {
                                SliderQuickFilter.HOSPITAL
                            }
                        },
                        modifier = Modifier.testTag("quick_filter_hospital")
                    )
                }
                item {
                    QuickFilterPill(
                        icon = Icons.Rounded.Biotech,
                        label = tr(lang, "ডায়াগনস্টিক", "Diagnostics"),
                        selected = activeQuickFilter == SliderQuickFilter.DIAGNOSTIC,
                        accentColor = MedicalBlueDark,
                        onClick = {
                            activeQuickFilter = if (activeQuickFilter == SliderQuickFilter.DIAGNOSTIC) {
                                SliderQuickFilter.ALL
                            } else {
                                SliderQuickFilter.DIAGNOSTIC
                            }
                        },
                        modifier = Modifier.testTag("quick_filter_diagnostic")
                    )
                }
                item {
                    QuickFilterPill(
                        icon = Icons.Rounded.Campaign,
                        label = tr(lang, "বিজ্ঞপ্তি", "Notices"),
                        selected = activeQuickFilter == SliderQuickFilter.NOTICE,
                        accentColor = AmberPending,
                        onClick = {
                            activeQuickFilter = if (activeQuickFilter == SliderQuickFilter.NOTICE) {
                                SliderQuickFilter.ALL
                            } else {
                                SliderQuickFilter.NOTICE
                            }
                        },
                        modifier = Modifier.testTag("quick_filter_notice")
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 3. "চিকিৎসা বিশেষজ্ঞতা" (Ultra-Compact Medical Specialties: 96.dp wide, 32x32.dp icon box)
        item(key = "home_compact_specialties") {
            CompactHomeSectionHeader(
                title = AppStrings.specialties(lang),
                actionLabel = AppStrings.allLabel(lang),
                onAction = { onNavigateFacilitiesTab(2) }
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_compact_specialty_row")
            ) {
                items(specialties, key = { it.id }) { spec ->
                    Surface(
                        onClick = { onSelectSpecialty(spec.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, MintBorder),
                        modifier = Modifier
                            .width(96.dp)
                            .testTag("compact_specialty_card_${spec.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SpecialtyIconBadge(
                                specialty = spec,
                                contentDescription = tr(lang, spec.nameBn, spec.nameEn),
                                selected = false,
                                size = 32.dp,
                                iconSize = 18.dp,
                                cornerRadius = 8.dp
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = tr(lang, spec.nameBn, spec.nameEn),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 4. "বিভাগ অনুযায়ী ৬৪টি জেলা" (Space-Saving Collapsible Single-Row Dropdown Bar)
        item(key = "home_64_districts_collapsible") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, MintBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 34.dp)
                            .clickable { isDistrictDropdownExpanded = !isDistrictDropdownExpanded }
                            .testTag("home_district_dropdown_bar"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MedicalTeal,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tr(lang, "বিভাগ অনুযায়ী ৬৪টি জেলা", "64 Districts by Division"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPill(
                                text = tr(lang, "৮ বিভাগ • ৬৪ জেলা", "8 Div • 64 Dist"),
                                bgColor = MintCardSurface,
                                textColor = MedicalTealDark
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(BackgroundIce),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDistrictDropdownExpanded) {
                                        Icons.Rounded.KeyboardArrowUp
                                    } else {
                                        Icons.Rounded.KeyboardArrowDown
                                    },
                                    contentDescription = tr(lang, "জেলা তালিকা", "Toggle Districts"),
                                    tint = MedicalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isDistrictDropdownExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            HorizontalDivider(color = MintBorder, thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))

                            // 8 Division Pills
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.testTag("home_division_row")
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedDivisionEn == null,
                                        onClick = { selectedDivisionEn = null },
                                        modifier = Modifier.height(32.dp),
                                        label = {
                                            Text(
                                                text = tr(lang, "সব বিভাগ (৬৪)", "All (64)"),
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MedicalBlue,
                                            selectedLabelColor = Color.White,
                                            containerColor = MintCardStart
                                        )
                                    )
                                }
                                items(SeedData.bangladeshDivisions, key = { it.nameEn }) { div ->
                                    val label = tr(
                                        lang,
                                        "${div.nameBn.removeSuffix(" বিভাগ")} (${div.districts.size})",
                                        "${div.nameEn} (${div.districts.size})"
                                    )
                                    FilterChip(
                                        selected = selectedDivisionEn == div.nameEn,
                                        onClick = {
                                            selectedDivisionEn = if (selectedDivisionEn == div.nameEn) null else div.nameEn
                                        },
                                        modifier = Modifier.height(32.dp),
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MedicalBlue,
                                            selectedLabelColor = Color.White,
                                            containerColor = MintCardStart
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Matching District Chips — collapses automatically upon selection
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.testTag("home_district_row")
                            ) {
                                items(displayedDistricts, key = { "${it.divisionEn}_${it.nameEn}" }) { dist ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            isDistrictDropdownExpanded = false
                                            onSelectDistrict(dist.nameEn)
                                        },
                                        modifier = Modifier.height(32.dp),
                                        label = {
                                            Text(
                                                text = tr(lang, dist.nameBn, dist.nameEn),
                                                fontSize = 11.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.LocationOn,
                                                contentDescription = null,
                                                tint = MedicalTeal,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = SurfaceWhite
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 5. "আজ বসবেন" (Sitting Today) — Compact Featured Doctor Card with chamber location & green status pill
        item(key = "home_sitting_today_header") {
            CompactHomeSectionHeader(
                title = AppStrings.availableToday(lang),
                actionLabel = AppStrings.allLabel(lang),
                onAction = { onNavigateFindDoctors(true) }
            )
            Spacer(Modifier.height(6.dp))
        }

        if (availableTodayDocs.isNotEmpty()) {
            items(availableTodayDocs, key = { "today_${it.doctor.id}" }) { doc ->
                val primaryChamber = doc.chambers.firstOrNull { it.chamber.isActive }?.chamber
                val chamberLocation = primaryChamber?.let {
                    listOf(it.facilityName, it.upazila.ifBlank { it.locationArea }, it.district)
                        .filter { p -> p.isNotBlank() }
                        .joinToString(", ")
                }?.ifBlank { doc.doctor.currentHospital } ?: doc.doctor.currentHospital

                CompactSittingTodayDoctorCard(
                    name = tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn),
                    subtitle = listOf(
                        doc.specialty?.let { tr(lang, it.nameBn, it.nameEn) }.orEmpty(),
                        doc.doctor.qualification
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    chamberLocation = chamberLocation.ifBlank { tr(lang, "বাংলাদেশ", "Bangladesh") },
                    imageUrl = doc.doctor.photoUrl,
                    statusLabel = AppStrings.availableToday(lang),
                    onClick = { onDoctorClick(doc.doctor.id) },
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .testTag("sitting_today_card_${doc.doctor.id}")
                )
            }
        } else {
            item(key = "sitting_today_featured_demo") {
                CompactSittingTodayDoctorCard(
                    name = fallbackFeaturedDoctorSlide.displayName(lang),
                    subtitle = listOf(
                        fallbackFeaturedDoctorSlide.displaySpecialty(lang),
                        fallbackFeaturedDoctorSlide.displayDesignation(lang)
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    chamberLocation = fallbackFeaturedDoctorSlide.displayAddress(lang),
                    imageUrl = fallbackFeaturedDoctorSlide.imageUrl,
                    statusLabel = AppStrings.availableToday(lang),
                    onClick = { onSliderViewDetails(fallbackFeaturedDoctorSlide) },
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .testTag("sitting_today_featured_card")
                )
            }
        }

        // Specialist Doctors (when live directory has records)
        if (doctors.isNotEmpty()) {
            item(key = "home_specialist_doctors_header") {
                Spacer(Modifier.height(10.dp))
                CompactHomeSectionHeader(
                    title = tr(lang, "বিশেষজ্ঞ ডাক্তারবৃন্দ", "Specialist Doctors"),
                    actionLabel = AppStrings.allLabel(lang),
                    onAction = { onNavigateFindDoctors(false) }
                )
                Spacer(Modifier.height(6.dp))
            }
            items(doctors.take(8), key = { "dir_${it.doctor.id}" }) { doc ->
                DoctorCard(
                    item = doc,
                    lang = lang,
                    isFavorite = doc.doctor.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                    onClick = { onDoctorClick(doc.doctor.id) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }

        // Recently Updated Doctors
        if (recentDocs.isNotEmpty()) {
            item(key = "home_recent_doctors") {
                Spacer(Modifier.height(10.dp))
                CompactHomeSectionHeader(
                    title = AppStrings.recentlyUpdatedDoctors(lang),
                    actionLabel = AppStrings.allLabel(lang),
                    onAction = { onNavigateFindDoctors(false) }
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentDocs, key = { "rec_${it.doctor.id}" }) { doc ->
                        Surface(
                            onClick = { onDoctorClick(doc.doctor.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, MintBorder),
                            modifier = Modifier.width(205.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExternalImageAvatar(
                                    imageUrl = doc.doctor.photoUrl,
                                    contentDescription = tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn),
                                    fallbackIcon = Icons.Rounded.Person,
                                    size = 42.dp,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = doc.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MedicalBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Popular Hospitals
        if (hospitals.isNotEmpty()) {
            item(key = "home_popular_hospitals") {
                Spacer(Modifier.height(12.dp))
                CompactHomeSectionHeader(
                    title = AppStrings.popularHospitals(lang),
                    actionLabel = AppStrings.allLabel(lang),
                    onAction = { onNavigateFacilitiesTab(0) }
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(hospitals.take(6), key = { it.id }) { hosp ->
                        Surface(
                            onClick = { onNavigateFacilitiesTab(0) },
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, MintBorder),
                            modifier = Modifier.width(220.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ExternalImageAvatar(
                                        imageUrl = hosp.imageUrl,
                                        contentDescription = tr(lang, hosp.nameBn, hosp.nameEn),
                                        fallbackIcon = Icons.Rounded.LocalHospital,
                                        size = 40.dp,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tr(lang, hosp.nameBn, hosp.nameEn),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${hosp.upazila}, ${hosp.district}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MedicalTealDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (hosp.address.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = hosp.address,
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
        }
    }
}

@Composable
private fun QuickFilterPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MedicalBlue else SurfaceWhite,
        border = BorderStroke(1.dp, if (selected) MedicalBlue else MintBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else accentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactHomeSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedicalBlue
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MedicalBlue,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CompactSittingTodayDoctorCard(
    name: String,
    subtitle: String,
    chamberLocation: String,
    imageUrl: String,
    statusLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) { FirestoreSliderService.extractInitials(name) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, MintBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (imageUrl.startsWith("http://", true) || imageUrl.startsWith("https://", true)) {
                ExternalImageAvatar(
                    imageUrl = imageUrl,
                    contentDescription = name,
                    fallbackIcon = Icons.Rounded.Person,
                    size = 50.dp,
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(MintCardStart, MintCardSurface))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MedicalBlue
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusPill(
                        text = statusLabel,
                        bgColor = EmeraldSoft,
                        textColor = Color(0xFF065F46),
                        icon = Icons.Rounded.FiberManualRecord
                    )
                }

                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MedicalBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MedicalTeal,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = chamberLocation,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
