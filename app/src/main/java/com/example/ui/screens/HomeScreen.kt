package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.seed.SeedData
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.AdCampaignItem
import com.example.ui.ads.AdMobRemoteConfig
import com.example.ui.ads.AdPlacements
import com.example.ui.ads.AdvertisingRotationEngine
import com.example.ui.ads.ManagedNativeBannerAd
import com.example.ui.ads.NativeAdSlotOrAdMob
import com.example.ui.ads.NativeSponsorAdCard
import com.example.ui.ads.PlacementGlobalSettings
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.PlacementSponsorSlot
import com.example.ui.ads.ResolvedHomeSliderSlide
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

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
    selectedHomeDistrict: String? = null,
    onSelectHomeDistrict: (String?) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onRefreshDirectory: () -> Unit,
    onToggleLanguage: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onSearchSubmit: (String) -> Unit,
    onSelectSpecialty: (String) -> Unit,
    onSelectDistrict: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    onNavigateFindDoctors: (availableTodayOnly: Boolean) -> Unit,
    onNavigateFacilitiesTab: (Int) -> Unit,
    onNavigateSavedTab: () -> Unit = {},
    onNavigateAboutTab: () -> Unit = {},
    homeSliderSlides: List<ResolvedHomeSliderSlide> = emptyList(),
    homeSliderAutoSlideMs: Long = 4000L,
    onHomeSliderVisible: (ResolvedHomeSliderSlide) -> Unit = {},
    onHomeSliderClick: (ResolvedHomeSliderSlide) -> Unit = {},
    adCampaigns: List<AdCampaignItem> = emptyList(),
    adPlacementSettings: PlacementGlobalSettings = PlacementGlobalSettings(),
    admobConfig: AdMobRemoteConfig = AdMobRemoteConfig(),
    doctorListRotationBase: Long = 0L,
    hospitalRotationBase: Long = 0L,
    onNativeAdImpression: (AdCampaignItem, String) -> Unit = { _, _ -> },
    onNativeAdClick: (AdCampaignItem, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val isDark = LocalIsDarkTheme.current
    val emeraldGreen = Color(0xFF10B96B)
    val cardBg = if (isDark) Color(0xFF132131) else LightSurfaceWhite
    val cardBorder = if (isDark) Color(0xFF23384F) else Color(0xFFDCE9F5)

    var showDistrictPicker by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(false) }

    val activeDistrictInfo = remember(selectedHomeDistrict) {
        selectedHomeDistrict?.let { sel ->
            SeedData.allDistricts.firstOrNull { it.nameEn.equals(sel, ignoreCase = true) }
        }
    }

    val districtDisplayLabel = remember(activeDistrictInfo, lang) {
        if (activeDistrictInfo != null) {
            tr(lang, activeDistrictInfo.nameBn, activeDistrictInfo.nameEn)
        } else {
            tr(lang, "সারা বাংলাদেশ", "All Bangladesh")
        }
    }

    val locationSubLabel = remember(activeDistrictInfo, lang) {
        if (activeDistrictInfo != null) {
            tr(lang, "${activeDistrictInfo.nameBn}, বাংলাদেশ", "${activeDistrictInfo.nameEn}, Bangladesh")
        } else {
            tr(lang, "৬৪ জেলা, বাংলাদেশ", "64 Districts, Bangladesh")
        }
    }

    fun runLocationAutoDetect() {
        if (isDetectingLocation) return
        scope.launch {
            isDetectingLocation = true
            val detected = DistrictAutoDetector.detectBangladeshDistrict(context)
            isDetectingLocation = false
            if (detected != null) {
                onSelectHomeDistrict(detected.nameEn)
                onShowMessage(
                    tr(
                        lang,
                        "অবস্থান শনাক্ত হয়েছে: ${detected.nameBn}",
                        "Location detected: ${detected.nameEn}, Bangladesh"
                    )
                )
            } else {
                onShowMessage(
                    tr(
                        lang,
                        "স্বয়ংক্রিয়ভাবে জেলা পাওয়া যায়নি — তালিকা থেকে নির্বাচন করুন",
                        "Could not auto-detect district — please select from list"
                    )
                )
                showDistrictPicker = true
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runLocationAutoDetect()
        } else {
            onShowMessage(
                tr(
                    lang,
                    "লোকেশন অনুমতি দেওয়া হয়নি — ম্যানুয়ালি জেলা নির্বাচন করুন",
                    "Location permission denied — select your district manually"
                )
            )
            showDistrictPicker = true
        }
    }

    val availableTodayCount = remember(doctors, selectedHomeDistrict) {
        val scoped = if (selectedHomeDistrict != null) {
            val local = doctors.filter { doc ->
                doc.chambers.any { it.chamber.district.equals(selectedHomeDistrict, ignoreCase = true) }
            }
            if (local.isNotEmpty()) local else doctors
        } else doctors
        scoped.count { it.isAvailableToday() }
    }

    val popularDoctors = remember(doctors, selectedHomeDistrict) {
        doctors.sortedWith(
            compareByDescending<DoctorFullProfile> { doc ->
                selectedHomeDistrict != null && doc.chambers.any {
                    it.chamber.district.equals(selectedHomeDistrict, ignoreCase = true)
                }
            }
                .thenByDescending { it.doctor.isFeatured }
                .thenByDescending { it.isAvailableToday() }
                .thenByDescending { it.doctor.updatedAt }
        ).take(10)
    }

    val displayedHospitals = remember(hospitals, selectedHomeDistrict) {
        if (selectedHomeDistrict == null) hospitals.take(6)
        else {
            val local = hospitals.filter { it.district.equals(selectedHomeDistrict, ignoreCase = true) }
            (local + hospitals.filterNot { it.district.equals(selectedHomeDistrict, ignoreCase = true) }).take(6)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. TOP DISTRICT SELECTOR PILL BAR (matches mockup: Pin + District v | Emerald "Change" pill)
        item(key = "home_district_bar") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable { showDistrictPicker = true }
                    .testTag("home_district_selector_bar"),
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF132232) else Color(0xFFEAF4FB),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = emeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = districtDisplayLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = emeraldGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Surface(
                        onClick = { showDistrictPicker = true },
                        shape = RoundedCornerShape(50),
                        color = emeraldGreen,
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .testTag("home_district_change_btn")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(lang, "পরিবর্তন", "Change"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 2. ADMIN-MANAGED HOME SLIDER — Displays strictly ONLY slides added by Admin
        if (homeSliderSlides.isNotEmpty()) {
            item(key = "home_hero_slider") {
                HomeHeroBannerSlider(
                    slides = homeSliderSlides,
                    autoSlideIntervalMs = homeSliderAutoSlideMs,
                    lang = lang,
                    onSlideVisible = onHomeSliderVisible,
                    onSlideClick = onHomeSliderClick
                )
            }
        }

        // 3. "YOUR LOCATION" CARD WITH WORKING "AUTO DETECT" BUTTON
        item(key = "home_your_location_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable {
                        if (activeDistrictInfo != null) {
                            onSelectDistrict(activeDistrictInfo.nameEn)
                        } else {
                            showDistrictPicker = true
                        }
                    }
                    .testTag("home_your_location_card"),
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = emeraldGreen,
                            modifier = Modifier.size(30.dp)
                        )
                        Column {
                            Text(
                                text = tr(lang, "আপনার অবস্থান", "Your Location"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                text = locationSubLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        onClick = {
                            if (DistrictAutoDetector.hasLocationPermission(context)) {
                                runLocationAutoDetect()
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isDark) emeraldGreen else Color(0xFFCFF7E2),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("home_auto_detect_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isDetectingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = if (isDark) Color.White else Color(0xFF0A663B)
                                )
                            }
                            Text(
                                text = if (isDetectingLocation) {
                                    tr(lang, "খোঁজা হচ্ছে...", "Detecting...")
                                } else {
                                    tr(lang, "অটো শনাক্ত", "Auto Detect")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0A4D2E)
                            )
                        }
                    }
                }
            }
        }

        // 4. QUICK SERVICES (4-TILE ROW: Doctors, Hospitals, Diagnostics, Chambers)
        item(key = "home_quick_services") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeSectionHeading(
                    icon = Icons.Rounded.GridView,
                    title = tr(lang, "দ্রুত সেবা", "Quick Services"),
                    actionLabel = tr(lang, "সব দেখুন", "View All"),
                    onAction = { onNavigateFacilitiesTab(0) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickServiceTile(
                        icon = Icons.Rounded.MedicalServices,
                        label = tr(lang, "ডাক্তার", "Doctors"),
                        iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        onClick = { onNavigateFindDoctors(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_service_doctors")
                    )
                    QuickServiceTile(
                        icon = Icons.Rounded.LocalHospital,
                        label = tr(lang, "হাসপাতাল", "Hospitals"),
                        iconTint = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0284C7),
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        onClick = { onNavigateFacilitiesTab(0) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_service_hospitals")
                    )
                    QuickServiceTile(
                        icon = Icons.Rounded.Biotech,
                        label = tr(lang, "ডায়াগনস্টিক", "Diagnostics"),
                        iconTint = if (isDark) Color(0xFF60A5FA) else Color(0xFF0369A1),
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        onClick = { onNavigateFacilitiesTab(1) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_service_diagnostics")
                    )
                    QuickServiceTile(
                        icon = Icons.Rounded.EventNote,
                        label = tr(lang, "চেম্বার", "Chambers"),
                        iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        onClick = { onNavigateFacilitiesTab(3) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_service_chambers")
                    )
                }
            }
        }

        // 5. TODAY'S CHAMBER SCHEDULE HIGHLIGHT BANNER CARD
        item(key = "home_todays_chamber_banner") {
            val bannerBrush = if (isDark) {
                Brush.horizontalGradient(listOf(Color(0xFF0C3625), Color(0xFF104932)))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFFE7FCEF), Color(0xFFBAF4D3)))
            }
            val bannerBorder = if (isDark) emeraldGreen.copy(alpha = 0.7f) else Color(0xFFA7EBC4)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bannerBrush)
                    .border(1.dp, bannerBorder, RoundedCornerShape(16.dp))
                    .clickable { onNavigateFindDoctors(true) }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
                    .testTag("home_todays_chamber_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(emeraldGreen.copy(alpha = if (isDark) 0.22f else 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = emeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = tr(lang, "আজকের চেম্বার সময়সূচী", "Today's Chamber Schedule"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color.White else Color(0xFF0B253A)
                            )
                            Text(
                                text = if (availableTodayCount > 0) {
                                    tr(
                                        lang,
                                        "আজ আপনার এলাকায় $availableTodayCount জন ডাক্তার বসছেন।",
                                        "$availableTodayCount doctors available today in your area."
                                    )
                                } else {
                                    tr(
                                        lang,
                                        "আপনার এলাকায় আজ যেসব ডাক্তার বসছেন।",
                                        "Doctors available today in your area."
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color(0xFFC8EBD7) else Color(0xFF284B3C),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (isDark) Color.White else emeraldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Preserved HOME-02 / Native Sponsor Slot
        item(key = "home_slot_02") {
            val hasHome02 = remember(sponsors, rateCards, nowMs / 60_000L) {
                adConfig.adsEnabled && PlacementRateCardEngine.selectSponsorForPlacement(
                    sponsors = sponsors,
                    rateCards = rateCards,
                    placementId = PlacementRateCardEngine.HOME_02,
                    nowMs = nowMs
                ) != null
            }
            if (hasHome02) {
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.HOME_02,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            } else if (adConfig.adsEnabled && adConfig.nativeSponsorEnabled) {
                NativeSponsorAdCard(
                    sponsors = sponsors,
                    adConfig = adConfig,
                    pageKey = SponsorCampaignSelector.PAGE_HOME,
                    slotIndex = 0,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked,
                    enforce300x200 = true,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }

        // 6. MEDICAL SPECIALISTS HORIZONTAL STRIP (Colorful icons + Firebase custom iconUrl support)
        item(key = "home_medical_specialists") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeSectionHeading(
                    icon = Icons.Rounded.Star,
                    title = tr(lang, "চিকিৎসা বিশেষজ্ঞতা", "Medical Specialists"),
                    actionLabel = tr(lang, "সব দেখুন", "View All"),
                    onAction = { onNavigateFacilitiesTab(2) }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("home_specialists_row")
                ) {
                    items(specialties, key = { it.id }) { spec ->
                        HomeSpecialtyTile(
                            specialty = spec,
                            lang = lang,
                            isDark = isDark,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            onClick = { onSelectSpecialty(spec.id) }
                        )
                    }
                }

                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.HOME_06,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }

        if (isOnline && adConfig.adsEnabled && adConfig.nativeBannerEnabled) {
            item(key = "home_mid_native") {
                ManagedNativeBannerAd(
                    adConfig = adConfig,
                    isOnline = isOnline,
                    refreshKey = "home_mid",
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }

        // 7. POPULAR DOCTORS SECTION (Matches mockup card style: Photo, Bold Name, Degree, Emerald Hospital/Location Pill, >)
        item(key = "home_popular_doctors_header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeSectionHeading(
                    icon = Icons.Rounded.Groups,
                    title = tr(lang, "জনপ্রিয় ডাক্তারবৃন্দ", "Popular Doctors"),
                    actionLabel = tr(lang, "সব দেখুন", "View All"),
                    onAction = { onNavigateFindDoctors(false) }
                )
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.HOME_03,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }

        if (popularDoctors.isEmpty()) {
            item(key = "home_doctors_empty") {
                EmptyStateCard(
                    icon = Icons.Outlined.MedicalServices,
                    title = tr(lang, "এখনো কোনো ডাক্তার যুক্ত করা হয়নি", "No Doctors Listed Yet"),
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
            itemsIndexed(popularDoctors, key = { _, it -> "popular_${it.doctor.id}" }) { index, doc ->
                PopularDoctorCompactCard(
                    item = doc,
                    lang = lang,
                    isDark = isDark,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    isFavorite = doc.doctor.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                    onClick = { onDoctorClick(doc.doctor.id) },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                // Dynamic Native Banner Ads (300x100) + AdMob Native inside "জনপ্রিয় ডাক্তারবৃন্দ" (Popular Doctors)
                if (AdvertisingRotationEngine.shouldInsertNativeAfterCard(index, popularDoctors.size)) {
                    val nativeSlot = AdvertisingRotationEngine.nativeSlotForCardIndex(index)
                    NativeAdSlotOrAdMob(
                        campaigns = adCampaigns,
                        placementTarget = AdPlacements.DOCTOR_LIST,
                        queueKey = AdvertisingRotationEngine.QUEUE_DOCTOR_NATIVE,
                        slotIndex = nativeSlot,
                        baseOffset = doctorListRotationBase,
                        nowMs = nowMs,
                        placementSettings = adPlacementSettings,
                        admobConfig = admobConfig,
                        lang = lang,
                        onVisibleImpression = onNativeAdImpression,
                        onClicked = onNativeAdClick,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }

        // 8. POPULAR HOSPITALS (Preserved directory section)
        if (displayedHospitals.isNotEmpty()) {
            item(key = "home_popular_hospitals") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeSectionHeading(
                        icon = Icons.Rounded.LocalHospital,
                        title = AppStrings.popularHospitals(lang),
                        actionLabel = tr(lang, "সব দেখুন", "View All"),
                        onAction = { onNavigateFacilitiesTab(0) }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedHospitals, key = { it.id }) { hosp ->
                            Surface(
                                modifier = Modifier
                                    .width(228.dp)
                                    .clickable { onNavigateFacilitiesTab(0) },
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ExternalImageAvatar(
                                        imageUrl = hosp.imageUrl,
                                        contentDescription = tr(lang, hosp.nameBn, hosp.nameEn),
                                        fallbackIcon = Icons.Rounded.LocalHospital,
                                        size = 44.dp,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tr(lang, hosp.nameBn, hosp.nameEn),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = listOf(hosp.upazila, hosp.district).filter { it.isNotBlank() }.joinToString(", "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = emeraldGreen,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    NativeAdSlotOrAdMob(
                        campaigns = adCampaigns,
                        placementTarget = AdPlacements.HOSPITAL_LIST,
                        queueKey = AdvertisingRotationEngine.QUEUE_HOSPITAL_NATIVE,
                        slotIndex = 0,
                        baseOffset = hospitalRotationBase,
                        nowMs = nowMs,
                        placementSettings = adPlacementSettings,
                        admobConfig = admobConfig,
                        lang = lang,
                        onVisibleImpression = onNativeAdImpression,
                        onClicked = onNativeAdClick,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }

        // Preserved bottom sponsor placements (HOME-04, HOME-05, HOME-07)
        if (adConfig.adsEnabled) {
            item(key = "home_bottom_placements") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlacementSponsorSlot(
                        placementId = PlacementRateCardEngine.HOME_04,
                        sponsors = sponsors,
                        rateCards = rateCards,
                        adConfig = adConfig,
                        isOnline = isOnline,
                        nowMs = nowMs,
                        lang = lang,
                        onSponsorDisplayed = onSponsorDisplayed,
                        onSponsorClicked = onSponsorClicked,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    PlacementSponsorSlot(
                        placementId = PlacementRateCardEngine.HOME_05,
                        sponsors = sponsors,
                        rateCards = rateCards,
                        adConfig = adConfig,
                        isOnline = isOnline,
                        nowMs = nowMs,
                        lang = lang,
                        onSponsorDisplayed = onSponsorDisplayed,
                        onSponsorClicked = onSponsorClicked,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    PlacementSponsorSlot(
                        placementId = PlacementRateCardEngine.HOME_07,
                        sponsors = sponsors,
                        rateCards = rateCards,
                        adConfig = adConfig,
                        isOnline = isOnline,
                        nowMs = nowMs,
                        lang = lang,
                        onSponsorDisplayed = onSponsorDisplayed,
                        onSponsorClicked = onSponsorClicked,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }

    if (showDistrictPicker) {
        DistrictPickerBottomSheet(
            lang = lang,
            selectedDistrictEn = selectedHomeDistrict,
            onSelectDistrict = { picked ->
                onSelectHomeDistrict(picked)
            },
            onDismiss = { showDistrictPicker = false }
        )
    }
}

@Composable
private fun HomeSectionHeading(
    icon: ImageVector,
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val emeraldGreen = Color(0xFF10B96B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = emeraldGreen,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = emeraldGreen
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = emeraldGreen,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickServiceTile(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    cardBg: Color,
    cardBorder: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.heightIn(min = 78.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeSpecialtyTile(
    specialty: SpecialtyEntity,
    lang: AppLang,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val label = tr(lang, specialty.nameBn, specialty.nameEn)
    val (tint, softBg) = specialtyVisualColors(specialty.iconKey, isDark)
    val hasCustomIconUrl = specialty.iconUrl.trim().let {
        it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("data:image/", true)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .width(82.dp)
            .testTag("home_specialty_tile_${specialty.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hasCustomIconUrl) {
                SpecialtyIconBadge(
                    specialty = specialty,
                    contentDescription = label,
                    selected = false,
                    size = 44.dp,
                    iconSize = 24.dp,
                    cornerRadius = 22.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(softBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = specialtyIcon(specialty.iconKey),
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun specialtyVisualColors(iconKey: String, isDark: Boolean): Pair<Color, Color> = when (iconKey.lowercase()) {
    "heart" -> Color(0xFFEF4444) to if (isDark) Color(0xFF3A1820) else Color(0xFFFEE2E2)
    "brain" -> Color(0xFF8B5CF6) to if (isDark) Color(0xFF281E42) else Color(0xFFEDE9FE)
    "child" -> Color(0xFFF59E0B) to if (isDark) Color(0xFF3A2B14) else Color(0xFFFEF3C7)
    "bone" -> Color(0xFF14B8A6) to if (isDark) Color(0xFF103438) else Color(0xFFCCFBF1)
    "eye" -> Color(0xFF0284C7) to if (isDark) Color(0xFF102D45) else Color(0xFFE0F2FE)
    "stethoscope" -> Color(0xFFEC4899) to if (isDark) Color(0xFF3B1A2F) else Color(0xFFFCE7F3)
    "female" -> Color(0xFFD946EF) to if (isDark) Color(0xFF371A40) else Color(0xFFFAE8FF)
    "skin" -> Color(0xFF10B981) to if (isDark) Color(0xFF103529) else Color(0xFFD1FAE5)
    "ear" -> Color(0xFF0EA5E9) to if (isDark) Color(0xFF0E2F44) else Color(0xFFE0F2FE)
    "scalpel" -> Color(0xFFF97316) to if (isDark) Color(0xFF3C2314) else Color(0xFFFFEDD5)
    "mind" -> Color(0xFF6366F1) to if (isDark) Color(0xFF21244A) else Color(0xFFE0E7FF)
    else -> Color(0xFF0284C7) to if (isDark) Color(0xFF132D46) else Color(0xFFE0F2FE)
}

@Composable
private fun PopularDoctorCompactCard(
    item: DoctorFullProfile,
    lang: AppLang,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val emeraldGreen = Color(0xFF10B96B)
    val doc = item.doctor
    val primaryChamber = item.chambers.firstOrNull { it.chamber.isActive }?.chamber
    val specName = item.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: doc.subSpecialty

    val hospitalLocationText = remember(doc, primaryChamber, specName) {
        val place = doc.currentHospital.ifBlank { primaryChamber?.facilityName.orEmpty() }
        val dist = primaryChamber?.district.orEmpty()
        when {
            place.isNotBlank() && dist.isNotBlank() -> "$place, $dist"
            place.isNotBlank() -> place
            dist.isNotBlank() -> dist
            else -> specName
        }
    }

    val qualificationLine = remember(doc, specName) {
        when {
            doc.qualification.isNotBlank() && specName.isNotBlank() -> "${doc.qualification} ($specName)"
            doc.qualification.isNotBlank() -> doc.qualification
            else -> specName
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("popular_doctor_card_${doc.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExternalImageAvatar(
                imageUrl = doc.photoUrl,
                contentDescription = tr(lang, doc.fullNameBn, doc.fullNameEn),
                fallbackIcon = Icons.Rounded.Person,
                size = 68.dp,
                shape = RoundedCornerShape(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = tr(lang, doc.fullNameBn, doc.fullNameEn),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) Color.White else Color(0xFF0A2E6E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (qualificationLine.isNotBlank()) {
                    Text(
                        text = qualificationLine,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (hospitalLocationText.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Surface(
                        color = if (isDark) Color(0xFF0D4E33) else Color(0xFFCFF7E2),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF0D8A4E),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = hospitalLocationText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFD5FDE7) else Color(0xFF0A4F2F),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("popular_favorite_btn_${doc.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = AppStrings.saveFavorite(lang),
                        tint = if (isFavorite) RoseAlert else colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = emeraldGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
