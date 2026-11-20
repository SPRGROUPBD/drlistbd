package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.example.ui.ads.ManagedNativeBannerAd
import com.example.ui.ads.NativeSponsorAdCard
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExternalImageAvatar
import com.example.ui.components.StatusPill
import com.example.ui.components.specialtyIcon
import com.example.ui.theme.*

@Composable
fun DirectoriesScreen(
    lang: AppLang,
    adConfig: AdConfigEntity,
    sponsors: List<SponsorEntity> = emptyList(),
    isOnline: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    onSponsorDisplayed: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onSponsorClicked: (String, Boolean) -> Unit = { _, _ -> },
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    hospitals: List<HospitalEntity>,
    diagnosticCenters: List<DiagnosticCenterEntity>,
    specialties: List<SpecialtyEntity>,
    doctors: List<DoctorFullProfile>,
    onSelectSpecialty: (String) -> Unit,
    onSelectFacility: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        AppStrings.hospitals(lang),
        AppStrings.diagnosticCenters(lang),
        AppStrings.specialties(lang),
        AppStrings.chamberSchedule(lang)
    )
    var search by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf(DayOfWeekBd.today()) }
    var selectedSession by remember { mutableStateOf<ChamberSessionType?>(null) }
    val context = LocalContext.current

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

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(tr(lang, "অনুসন্ধান করুন...", "Search directory...")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MedicalBlue) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        when (selectedTab) {
            0 -> { // Hospitals
                val list = hospitals.filter {
                    search.isBlank() || it.nameEn.contains(search, true) || it.nameBn.contains(search, true) || it.district.contains(search, true)
                }
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
                                onCall = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${h.contactNumber}")))
                                },
                                onViewDoctors = { onSelectFacility(h.nameEn.removePrefix("[DEMO] ")) }
                            )
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
                            } else if (isOnline && adConfig.nativeBannerEnabled && idx == 2) {
                                Spacer(Modifier.height(10.dp))
                                ManagedNativeBannerAd(
                                    adConfig = adConfig,
                                    isOnline = isOnline
                                )
                            }
                        }
                    }
                }
            }

            1 -> { // Diagnostic Centers
                val list = diagnosticCenters.filter {
                    search.isBlank() || it.nameEn.contains(search, true) || it.nameBn.contains(search, true) || it.district.contains(search, true)
                }
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
                                onCall = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${dc.contactNumber}")))
                                },
                                onViewDoctors = { onSelectFacility(dc.nameEn.removePrefix("[DEMO] ")) }
                            )
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
                            } else if (isOnline && adConfig.nativeBannerEnabled && idx == 2) {
                                Spacer(Modifier.height(10.dp))
                                ManagedNativeBannerAd(
                                    adConfig = adConfig,
                                    isOnline = isOnline
                                )
                            }
                        }
                    }
                }
            }

            2 -> { // Specialties
                val list = specialties.filter {
                    search.isBlank() || it.nameBn.contains(search, true) || it.nameEn.contains(search, true)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(list, key = { _, it -> it.id }) { idx, sp ->
                        val docCount = doctors.count { it.doctor.mainSpecialtyId == sp.id }
                        Surface(
                            onClick = { onSelectSpecialty(sp.id) },
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
                val matching = remember(doctors, selectedDay, selectedSession, search) {
                    doctors.flatMap { doc ->
                        doc.chambers.mapNotNull { cw ->
                            val daySlots = cw.schedules
                                .filter { it.dayOfWeek == selectedDay.name }
                                .filter { selectedSession == null || it.sessionType() == selectedSession }
                                .sortedBy { it.startMinutesOfDay() }
                            val matchesSearch = search.isBlank() ||
                                doc.doctor.fullNameEn.contains(search, true) ||
                                doc.doctor.fullNameBn.contains(search, true) ||
                                cw.chamber.facilityName.contains(search, true) ||
                                cw.chamber.district.contains(search, true)
                            if (cw.chamber.isActive && daySlots.isNotEmpty() && matchesSearch) {
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
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(matching) { idx, (doc, ch, slots) ->
                            Surface(
                                onClick = { onDoctorClick(doc.doctor.id) },
                                shape = RoundedCornerShape(18.dp),
                                color = SurfaceWhite,
                                border = BorderStroke(1.dp, OutlineSoft),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(tr(lang, doc.doctor.fullNameBn, doc.doctor.fullNameEn), style = MaterialTheme.typography.titleMedium)
                                    Text(doc.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: "", style = MaterialTheme.typography.bodySmall, color = MedicalBlueDark)
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
                                            TextButton(onClick = {
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ch.serialPhone}")))
                                            }) {
                                                Icon(Icons.Rounded.Call, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(AppStrings.callForSerial(lang))
                                            }
                                        }
                                    }
                                }
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
    onCall: () -> Unit,
    onViewDoctors: () -> Unit
) {
    Surface(
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
