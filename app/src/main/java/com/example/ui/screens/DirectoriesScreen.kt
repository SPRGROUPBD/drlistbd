package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AdConfigEntity
import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.SpecialtyEntity
import com.example.data.local.SponsorEntity
import com.example.data.model.ChamberSessionType
import com.example.data.model.DayOfWeekBd
import com.example.data.model.DoctorFullProfile
import com.example.data.model.sessionType
import com.example.data.model.startMinutesOfDay
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.AdCampaignItem
import com.example.ui.ads.AdMobRemoteConfig
import com.example.ui.ads.AdPlacements
import com.example.ui.ads.AdTargetScreens
import com.example.ui.ads.AdvertisingRotationEngine
import com.example.ui.ads.FairAdRotationEngine
import com.example.ui.ads.FairNative300x200Card
import com.example.ui.ads.ManagedAdvertiserCampaign
import com.example.ui.ads.ManagedNativeBannerAd
import com.example.ui.ads.NativeAdSlotOrAdMob
import com.example.ui.ads.NativeSponsorAdCard
import com.example.ui.ads.PlacementGlobalSettings
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.PlacementSponsorSlot
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExternalImageAvatar
import com.example.ui.components.StatusPill
import com.example.ui.components.specialtyIcon
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoriesScreen(
    lang: AppLang,
    adConfig: AdConfigEntity,
    sponsors: List<SponsorEntity> = emptyList(),
    rateCards: List<PlacementRateCardEntity> = PlacementRateCardEngine.defaultRateCards(),
    isOnline: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    onSponsorDisplayed: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onSponsorClicked: (String, Boolean) -> Unit = { _, _ -> },
    managedCampaigns: List<ManagedAdvertiserCampaign> = emptyList(),
    nativeBaseHospital: Long = 0L,
    nativeBaseDiagnostic: Long = 0L,
    nativeBaseChamber: Long = 0L,
    onFairAdImpression: (ManagedAdvertiserCampaign, String) -> Unit = { _, _ -> },
    onFairAdClick: (ManagedAdvertiserCampaign, String) -> Unit = { _, _ -> },
    favoriteHospitalIds: Set<String> = emptySet(),
    favoriteDiagnosticIds: Set<String> = emptySet(),
    favoriteChamberIds: Set<String> = emptySet(),
    onToggleHospitalFavorite: (String, String) -> Unit = { _, _ -> },
    onToggleDiagnosticFavorite: (String, String) -> Unit = { _, _ -> },
    onToggleChamberFavorite: (String, String) -> Unit = { _, _ -> },
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    hospitals: List<HospitalEntity>,
    diagnosticCenters: List<DiagnosticCenterEntity>,
    specialties: List<SpecialtyEntity>,
    doctors: List<DoctorFullProfile>,
    onSelectSpecialty: (String) -> Unit,
    onSelectFacility: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    pendingOpenHospitalId: String? = null,
    pendingOpenDiagnosticId: String? = null,
    pendingOpenChamberId: String? = null,
    onConsumePendingFacilityDetail: () -> Unit = {},
    adCampaigns: List<AdCampaignItem> = emptyList(),
    adPlacementSettings: PlacementGlobalSettings = PlacementGlobalSettings(),
    admobConfig: AdMobRemoteConfig = AdMobRemoteConfig(),
    hospitalRotationBase: Long = 0L,
    diagnosticRotationBase: Long = 0L,
    onNativeAdImpression: (AdCampaignItem, String) -> Unit = { _, _ -> },
    onNativeAdClick: (AdCampaignItem, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        AppStrings.hospitals(lang),
        AppStrings.diagnosticCenters(lang),
        AppStrings.specialties(lang),
        AppStrings.chamberSchedule(lang)
    )
    var selectedDay by remember { mutableStateOf(DayOfWeekBd.today()) }
    var selectedSession by remember { mutableStateOf<ChamberSessionType?>(null) }
    var detailHospital by remember { mutableStateOf<HospitalEntity?>(null) }
    var detailDiagnostic by remember { mutableStateOf<DiagnosticCenterEntity?>(null) }
    var detailSpecialty by remember { mutableStateOf<SpecialtyEntity?>(null) }
    var detailChamberTriple by remember { mutableStateOf<Triple<DoctorFullProfile, com.example.data.local.ChamberEntity, List<com.example.data.local.ChamberScheduleEntity>>?>(null) }
    val context = LocalContext.current

    LaunchedEffect(pendingOpenHospitalId, hospitals) {
        val id = pendingOpenHospitalId?.trim().orEmpty()
        if (id.isNotEmpty()) {
            hospitals.firstOrNull { it.id == id }?.let { match ->
                detailHospital = match
                onConsumePendingFacilityDetail()
            }
        }
    }

    LaunchedEffect(pendingOpenDiagnosticId, diagnosticCenters) {
        val id = pendingOpenDiagnosticId?.trim().orEmpty()
        if (id.isNotEmpty()) {
            diagnosticCenters.firstOrNull { it.id == id }?.let { match ->
                detailDiagnostic = match
                onConsumePendingFacilityDetail()
            }
        }
    }

    LaunchedEffect(pendingOpenChamberId, doctors) {
        val id = pendingOpenChamberId?.trim().orEmpty()
        if (id.isNotEmpty()) {
            val found = doctors.firstNotNullOfOrNull { doc ->
                doc.chambers.firstOrNull { it.chamber.id == id }?.let { cw ->
                    Triple(doc, cw.chamber, cw.schedules)
                }
            }
            if (found != null) {
                detailChamberTriple = found
                onConsumePendingFacilityDetail()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.coerceIn(0, 3),
            containerColor = SurfaceWhite,
            contentColor = MedicalBlue,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { onTabChange(idx) },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }

        when (selectedTab) {
            0 -> { // Hospitals
                val list = hospitals
                val hospPlacementId = PlacementRateCardEngine.HOS_01
                val hospFreq = PlacementRateCardEngine.insertionFrequencyForPlacement(rateCards, hospPlacementId, adConfig.nativeSponsorInterval)
                if (list.isEmpty()) {
                    EmptyStateCard(Icons.Rounded.LocalHospital, tr(lang, "হাসপাতাল পাওয়া যায়নি", "No Hospitals Found"), "")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(list, key = { _, it -> it.id }) { idx, h ->
                            FacilityCard(
                                name = tr(lang, h.nameBn, h.nameEn),
                                subtitle = "${h.upazila}, ${h.district}",
                                address = h.address,
                                phone = h.contactNumber,
                                imageUrl = h.imageUrl,
                                isDemo = h.isDemoData,
                                icon = Icons.Rounded.LocalHospital,
                                lang = lang,
                                isFavorite = h.id in favoriteHospitalIds,
                                onToggleFavorite = { onToggleHospitalFavorite(h.id, h.nameEn) },
                                onCall = { safeDialPhone(context, h.contactNumber, lang) },
                                onViewDoctors = { onSelectFacility(h.nameEn.removePrefix("[DEMO] ")) },
                                onCardClick = { detailHospital = h }
                            )
                            if (AdvertisingRotationEngine.shouldInsertNativeAfterCard(idx, list.size)) {
                                val slot = AdvertisingRotationEngine.nativeSlotForCardIndex(idx)
                                NativeAdSlotOrAdMob(
                                    campaigns = adCampaigns,
                                    placementTarget = AdPlacements.HOSPITAL_LIST,
                                    queueKey = AdvertisingRotationEngine.QUEUE_HOSPITAL_NATIVE,
                                    slotIndex = slot,
                                    baseOffset = hospitalRotationBase,
                                    nowMs = nowMs,
                                    placementSettings = adPlacementSettings,
                                    admobConfig = admobConfig,
                                    lang = lang,
                                    onVisibleImpression = onNativeAdImpression,
                                    onClicked = onNativeAdClick
                                )
                            }
                        }
                    }
                }
            }

            1 -> { // Diagnostic Centers
                val list = diagnosticCenters
                val diaPlacementId = PlacementRateCardEngine.DIA_01
                val diaFreq = PlacementRateCardEngine.insertionFrequencyForPlacement(rateCards, diaPlacementId, adConfig.nativeSponsorInterval)
                if (list.isEmpty()) {
                    EmptyStateCard(Icons.Rounded.Biotech, tr(lang, "ডায়াগনস্টিক সেন্টার পাওয়া যায়নি", "No Diagnostic Centers Found"), "")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(list, key = { _, it -> it.id }) { idx, dc ->
                            FacilityCard(
                                name = tr(lang, dc.nameBn, dc.nameEn),
                                subtitle = "${dc.upazila}, ${dc.district}",
                                address = dc.address,
                                phone = dc.contactNumber,
                                imageUrl = dc.imageUrl,
                                isDemo = dc.isDemoData,
                                icon = Icons.Rounded.Biotech,
                                lang = lang,
                                isFavorite = dc.id in favoriteDiagnosticIds,
                                onToggleFavorite = { onToggleDiagnosticFavorite(dc.id, dc.nameEn) },
                                onCall = { safeDialPhone(context, dc.contactNumber, lang) },
                                onViewDoctors = { onSelectFacility(dc.nameEn.removePrefix("[DEMO] ")) },
                                onCardClick = { detailDiagnostic = dc }
                            )
                            if (AdvertisingRotationEngine.shouldInsertNativeAfterCard(idx, list.size)) {
                                val slot = AdvertisingRotationEngine.nativeSlotForCardIndex(idx)
                                NativeAdSlotOrAdMob(
                                    campaigns = adCampaigns,
                                    placementTarget = AdPlacements.DIAGNOSTIC_LIST,
                                    queueKey = AdvertisingRotationEngine.QUEUE_DIAGNOSTIC_NATIVE,
                                    slotIndex = slot,
                                    baseOffset = diagnosticRotationBase,
                                    nowMs = nowMs,
                                    placementSettings = adPlacementSettings,
                                    admobConfig = admobConfig,
                                    lang = lang,
                                    onVisibleImpression = onNativeAdImpression,
                                    onClicked = onNativeAdClick
                                )
                            }
                        }
                    }
                }
            }

            2 -> { // Specialties
                val list = specialties
                val spePlacementId = PlacementRateCardEngine.SPE_01
                val speFreq = PlacementRateCardEngine.insertionFrequencyForPlacement(rateCards, spePlacementId, adConfig.nativeSponsorInterval)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(list, key = { _, it -> it.id }) { idx, sp ->
                        val docCount = doctors.count { it.doctor.mainSpecialtyId == sp.id }
                        Surface(
                            onClick = { detailSpecialty = sp },
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, OutlineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(MedicalBlueLight, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(specialtyIcon(sp.iconKey), null, tint = MedicalBlueDark)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sp.nameBn, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(sp.nameEn, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                StatusPill(
                                    text = tr(lang, "$docCount জন", "$docCount docs"),
                                    bgColor = MedicalTealLight,
                                    textColor = MedicalTealDark
                                )
                            }
                        }
                        if ((idx + 1) % speFreq == 0) {
                            Spacer(Modifier.height(10.dp))
                            PlacementSponsorSlot(
                                placementId = spePlacementId,
                                sponsors = sponsors,
                                rateCards = rateCards,
                                adConfig = adConfig,
                                isOnline = isOnline,
                                nowMs = nowMs,
                                lang = lang,
                                slotIndex = idx / speFreq,
                                onSponsorDisplayed = onSponsorDisplayed,
                                onSponsorClicked = onSponsorClicked
                            )
                        }
                        if (SponsorCampaignSelector.shouldInsertNativeSponsorAfter(idx, adConfig.nativeSponsorInterval)) {
                            Spacer(Modifier.height(10.dp))
                            NativeSponsorAdCard(
                                sponsors = sponsors,
                                adConfig = adConfig,
                                pageKey = SponsorCampaignSelector.PAGE_FACILITIES,
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

            3 -> { // Chamber Schedule by Day & Time Session
                val today = remember { DayOfWeekBd.today() }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DayOfWeekBd.entries) { day ->
                        val isToday = day == today
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = {
                                Text(
                                    text = "${day.shortBn} • ${day.shortEn}${if (isToday) " ★" else ""}",
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Morning / Afternoon / All Session Filter Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedSession == null,
                            onClick = { selectedSession = null },
                            label = { Text(tr(lang, "সব সময় (All)", "All Times")) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedSession == ChamberSessionType.MORNING,
                            onClick = {
                                selectedSession = if (selectedSession == ChamberSessionType.MORNING) null else ChamberSessionType.MORNING
                            },
                            leadingIcon = { Icon(Icons.Rounded.WbSunny, null, Modifier.size(16.dp)) },
                            label = { Text(tr(lang, "সকাল (Morning)", "Morning")) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedSession == ChamberSessionType.AFTERNOON,
                            onClick = {
                                selectedSession = if (selectedSession == ChamberSessionType.AFTERNOON) null else ChamberSessionType.AFTERNOON
                            },
                            leadingIcon = { Icon(Icons.Rounded.LightMode, null, Modifier.size(16.dp)) },
                            label = { Text(tr(lang, "বিকাল (Afternoon)", "Afternoon")) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val matching = remember(doctors, selectedDay, selectedSession) {
                    doctors.flatMap { doc ->
                        doc.chambers.mapNotNull { cw ->
                            val daySlots = cw.schedules
                                .filter { it.dayOfWeek == selectedDay.name }
                                .filter { selectedSession == null || it.sessionType() == selectedSession }
                                .sortedBy { it.startMinutesOfDay() }
                            if (cw.chamber.isActive && daySlots.isNotEmpty()) {
                                Triple(doc, cw.chamber, daySlots)
                            } else null
                        }
                    }
                }
                if (matching.isEmpty()) {
                    EmptyStateCard(
                        Icons.Rounded.Schedule,
                        tr(lang, "কোনো চেম্বার সময়সূচী নেই", "No Schedules Found"),
                        tr(lang, "অন্য বার বা সময় নির্বাচন করে দেখুন।", "Try selecting another weekday or time session.")
                    )
                } else {
                    val chaPlacementId = PlacementRateCardEngine.CHA_01
                    val chaFreq = PlacementRateCardEngine.insertionFrequencyForPlacement(rateCards, chaPlacementId, adConfig.nativeSponsorInterval)
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(matching) { idx, (doc, ch, slots) ->
                            Surface(
                                onClick = { detailChamberTriple = Triple(doc, ch, slots) },
                                shape = RoundedCornerShape(18.dp),
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn), style = MaterialTheme.typography.titleMedium)
                                            Text(doc.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: "", style = MaterialTheme.typography.bodySmall, color = MedicalBlueDark)
                                        }
                                        val isChamberFav = ch.id in favoriteChamberIds
                                        IconButton(
                                            onClick = { onToggleChamberFavorite(ch.id, ch.facilityName) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isChamberFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                contentDescription = tr(lang, "চেম্বার সেভ করুন", "Save Chamber"),
                                                tint = if (isChamberFav) RoseAlert else TextMuted
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val chHeader = if (ch.chamberName.isNotBlank()) "${ch.facilityName} (${ch.chamberName})" else ch.facilityName
                                    Text("$chHeader • ${ch.upazila}, ${ch.district}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Spacer(Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        slots.forEach { slot ->
                                            val session = slot.sessionType()
                                            val (bg, fg) = when (session) {
                                                ChamberSessionType.MORNING -> AmberSoft to Color(0xFF92400E)
                                                ChamberSessionType.AFTERNOON -> MedicalTealLight to MedicalTealDark
                                                ChamberSessionType.EVENING -> MedicalBlueLight to MedicalBlueDark
                                            }
                                            val label = slot.slotLabel.ifBlank { tr(lang, session.bnLabel, session.enLabel) }
                                            StatusPill(
                                                text = "$label: ${slot.openTime} – ${slot.closeTime}",
                                                bgColor = bg,
                                                textColor = fg,
                                                icon = Icons.Rounded.AccessTime
                                            )
                                        }
                                    }
                                    if (ch.serialPhone.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { onDoctorClick(doc.doctor.id) }) {
                                                Text(tr(lang, "প্রোফাইল", "Doctor Profile"))
                                            }
                                            TextButton(onClick = { safeDialPhone(context, ch.serialPhone, lang) }) {
                                                Icon(Icons.Rounded.Call, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(AppStrings.callForSerial(lang))
                                            }
                                        }
                                    }
                                }
                            }
                            val fairNativeChamber = if (FairAdRotationEngine.shouldInsertNativeAfterCard(idx) && adConfig.adsEnabled) {
                                val slot = FairAdRotationEngine.nativeSlotForCardIndex(idx)
                                remember(managedCampaigns, slot, nativeBaseChamber, nowMs / 60_000L) {
                                    FairAdRotationEngine.selectForNativeSlot(
                                        campaigns = managedCampaigns,
                                        screenId = AdTargetScreens.CHAMBER_SCHEDULE,
                                        slotIndex = slot,
                                        baseOffset = nativeBaseChamber,
                                        nowMs = nowMs
                                    )
                                }
                            } else null

                            if (fairNativeChamber != null) {
                                Spacer(Modifier.height(10.dp))
                                FairNative300x200Card(
                                    campaign = fairNativeChamber,
                                    screenId = AdTargetScreens.CHAMBER_SCHEDULE,
                                    slotIndex = FairAdRotationEngine.nativeSlotForCardIndex(idx),
                                    lang = lang,
                                    onVisibleImpression = onFairAdImpression,
                                    onClicked = onFairAdClick
                                )
                            } else {
                                if ((idx + 1) % chaFreq == 0) {
                                    Spacer(Modifier.height(10.dp))
                                    PlacementSponsorSlot(
                                        placementId = chaPlacementId,
                                        sponsors = sponsors,
                                        rateCards = rateCards,
                                        adConfig = adConfig,
                                        isOnline = isOnline,
                                        nowMs = nowMs,
                                        lang = lang,
                                        slotIndex = idx / chaFreq,
                                        onSponsorDisplayed = onSponsorDisplayed,
                                        onSponsorClicked = onSponsorClicked
                                    )
                                }
                                if (SponsorCampaignSelector.shouldInsertNativeSponsorAfter(idx, adConfig.nativeSponsorInterval)) {
                                    Spacer(Modifier.height(10.dp))
                                    NativeSponsorAdCard(
                                        sponsors = sponsors,
                                        adConfig = adConfig,
                                        pageKey = SponsorCampaignSelector.PAGE_FACILITIES,
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
            }
        }
    }

    // Detail Bottom Sheets hosting HOS-02/HOS-04, DIA-02/DIA-04, SPE-02, CHA-02/CHA-03
    detailHospital?.let { h ->
        ModalBottomSheet(onDismissRequest = { detailHospital = null }, containerColor = SurfaceWhite) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(tr(lang, h.nameBn, h.nameEn), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${h.upazila}, ${h.district} • ${h.address}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.HOS_02,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (h.contactNumber.isNotBlank()) {
                        OutlinedButton(onClick = { safeDialPhone(context, h.contactNumber, lang) }, modifier = Modifier.weight(1f)) {
                            Text(tr(lang, "কল করুন", "Call Hospital"))
                        }
                    }
                    Button(
                        onClick = {
                            detailHospital = null
                            onSelectFacility(h.nameEn.removePrefix("[DEMO] "))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr(lang, "সব ডাক্তার দেখুন", "View Doctors"))
                    }
                }
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.HOS_04,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    detailDiagnostic?.let { dc ->
        ModalBottomSheet(onDismissRequest = { detailDiagnostic = null }, containerColor = SurfaceWhite) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(tr(lang, dc.nameBn, dc.nameEn), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${dc.upazila}, ${dc.district} • ${dc.address}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.DIA_02,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (dc.contactNumber.isNotBlank()) {
                        OutlinedButton(onClick = { safeDialPhone(context, dc.contactNumber, lang) }, modifier = Modifier.weight(1f)) {
                            Text(tr(lang, "কল করুন", "Call Center"))
                        }
                    }
                    Button(
                        onClick = {
                            detailDiagnostic = null
                            onSelectFacility(dc.nameEn.removePrefix("[DEMO] "))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr(lang, "সব ডাক্তার দেখুন", "View Doctors"))
                    }
                }
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.DIA_04,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    detailSpecialty?.let { sp ->
        ModalBottomSheet(onDismissRequest = { detailSpecialty = null }, containerColor = SurfaceWhite) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(tr(lang, sp.nameBn, sp.nameEn), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val docCount = doctors.count { it.doctor.mainSpecialtyId == sp.id }
                Text(tr(lang, "$docCount জন তালিকাভুক্ত বিশেষজ্ঞ", "$docCount listed specialist doctor(s)"), style = MaterialTheme.typography.bodyMedium, color = MedicalBlueDark)
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.SPE_02,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked
                )
                Button(
                    onClick = {
                        detailSpecialty = null
                        onSelectSpecialty(sp.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr(lang, "এই বিভাগের ডাক্তার দেখুন", "Browse Specialists in This Category"))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    detailChamberTriple?.let { (doc, ch, slots) ->
        ModalBottomSheet(onDismissRequest = { detailChamberTriple = null }, containerColor = SurfaceWhite) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${ch.facilityName} • ${ch.fullAddress}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.CHA_02,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked
                )
                slots.forEach { slot ->
                    StatusPill(
                        text = "${slot.slotLabel}: ${slot.openTime} – ${slot.closeTime}",
                        bgColor = MedicalTealLight,
                        textColor = MedicalTealDark,
                        icon = Icons.Rounded.AccessTime
                    )
                }
                PlacementSponsorSlot(
                    placementId = PlacementRateCardEngine.CHA_03,
                    sponsors = sponsors,
                    rateCards = rateCards,
                    adConfig = adConfig,
                    isOnline = isOnline,
                    nowMs = nowMs,
                    lang = lang,
                    onSponsorDisplayed = onSponsorDisplayed,
                    onSponsorClicked = onSponsorClicked
                )
                Button(
                    onClick = {
                        detailChamberTriple = null
                        onDoctorClick(doc.doctor.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr(lang, "ডাক্তারের সম্পূর্ণ প্রোফাইল দেখুন", "Open Doctor Full Profile"))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FacilityCard(
    name: String,
    subtitle: String,
    address: String,
    phone: String,
    imageUrl: String,
    isDemo: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    lang: AppLang,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onCall: () -> Unit,
    onViewDoctors: () -> Unit,
    onCardClick: () -> Unit = {}
) {
    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExternalImageAvatar(imageUrl, name, icon, size = 56.dp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                        if (isDemo) StatusPill("DEMO", AmberSoft, Color(0xFF92400E))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = MedicalTeal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MedicalTealDark)
                    }
                    Text(address, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = tr(lang, "প্রিয় তালিকায় যোগ করুন", "Save to Favorites"),
                        tint = if (isFavorite) RoseAlert else TextMuted
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (phone.isNotBlank()) {
                    OutlinedButton(onClick = onCall, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Call, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(lang, "কল করুন", "Call"))
                    }
                }
                Button(
                    onClick = onViewDoctors,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(tr(lang, "ডাক্তার দেখুন", "View Doctors"))
                }
            }
        }
    }
}

private fun safeDialPhone(context: Context, phone: String, lang: AppLang) {
    val clean = phone.trim()
    if (clean.isEmpty()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
    }.onFailure {
        Toast.makeText(
            context,
            tr(lang, "কল করার অ্যাপ পাওয়া যায়নি", "No phone dialer app available"),
            Toast.LENGTH_SHORT
        ).show()
    }
}
