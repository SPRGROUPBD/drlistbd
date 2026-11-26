package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.AdConfigEntity
import com.example.data.local.ChamberScheduleEntity
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.SponsorEntity
import com.example.data.model.ChamberSessionType
import com.example.data.model.ChamberWithSchedules
import com.example.data.model.DayOfWeekBd
import com.example.data.model.DoctorFullProfile
import com.example.data.model.sessionType
import com.example.data.model.startMinutesOfDay
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.ManagedNativeBannerAd
import com.example.ui.ads.NativeSponsorAdCard
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.PlacementSponsorSlot
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.components.ExternalImageAvatar
import com.example.ui.components.StatusPill
import com.example.ui.theme.*

@Composable
fun DoctorProfileScreen(
    profile: DoctorFullProfile?,
    lang: AppLang,
    adConfig: AdConfigEntity,
    sponsors: List<SponsorEntity> = emptyList(),
    rateCards: List<PlacementRateCardEntity> = PlacementRateCardEngine.defaultRateCards(),
    isOnline: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    onSponsorDisplayed: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onSponsorClicked: (String, Boolean) -> Unit = { _, _ -> },
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    isLoadingProfile: Boolean = false,
    onGoToDoctorList: () -> Unit = onBack,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    if (profile == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundIce)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingProfile) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MedicalBlue)
                        Text(
                            text = tr(lang, "ডাক্তারের সর্বশেষ তথ্য লোড হচ্ছে...", "Loading latest doctor profile..."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doctor_profile_unavailable_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(RoseSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonOff,
                                contentDescription = tr(lang, "ডাক্তার পাওয়া যায়নি", "Doctor unavailable"),
                                tint = RoseAlert,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = tr(lang, "ডাক্তারের প্রোফাইল পাওয়া যায়নি", "Doctor Profile Unavailable"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = tr(
                                lang,
                                "এই ডাক্তারের প্রোফাইলটি বর্তমানে মুছে ফেলা হয়েছে অথবা সক্রিয় নেই। অনুগ্রহ করে ডাক্তার তালিকা থেকে অন্য বিশেষজ্ঞ ডাক্তার খুঁজুন।",
                                "This doctor profile may have been removed or is currently unavailable. Please browse the doctor directory."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onGoToDoctorList,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("back_to_doctor_list_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MedicalServices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tr(lang, "ডাক্তার তালিকায় ফিরে যান", "Back to Doctor List"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val doc = profile.doctor
    val specName = profile.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: doc.subSpecialty
    val displayName = tr(lang, doc.fullNameBn.ifBlank { doc.fullNameEn }, doc.fullNameEn.ifBlank { doc.fullNameBn })
    val secondaryName = if (doc.fullNameBn.isNotBlank() && doc.fullNameEn.isNotBlank() && doc.fullNameBn != doc.fullNameEn) {
        tr(lang, doc.fullNameEn, doc.fullNameBn)
    } else ""
    val activeChambers = remember(profile.chambers) { profile.chambers.filter { it.chamber.isActive } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Profile Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        ExternalImageAvatar(
                            imageUrl = doc.photoUrl,
                            contentDescription = displayName,
                            fallbackIcon = Icons.Rounded.Person,
                            size = 86.dp,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            if (secondaryName.isNotBlank()) {
                                Text(secondaryName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            if (doc.qualification.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = doc.qualification,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MedicalTealDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (specName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    specName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MedicalBlueDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (doc.currentDesignation.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    doc.currentDesignation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    // Action Row: Save & Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_save_btn")
                        ) {
                            Icon(
                                if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) RoseAlert else MedicalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isFavorite) AppStrings.saved(lang) else AppStrings.saveFavorite(lang), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                val shareText = buildString {
                                    append(displayName)
                                    if (doc.qualification.isNotBlank()) append("\n${doc.qualification}")
                                    if (specName.isNotBlank()) append("\n$specName")
                                    if (doc.currentDesignation.isNotBlank()) append("\n${doc.currentDesignation}")
                                    if (doc.currentHospital.isNotBlank()) append("\n${doc.currentHospital}")
                                    append("\nShared via Doctor List BD")
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(sendIntent, AppStrings.shareProfile(lang)))
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        tr(lang, "শেয়ার করার অ্যাপ পাওয়া যায়নি", "No sharing app available"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(AppStrings.shareProfile(lang), maxLines = 1)
                        }
                    }
                }
            }
        }

        // Doctor Profile Summary
        item {
            ProfileSectionCard(title = tr(lang, "ডাক্তারের পরিচিতি", "Doctor Details"), icon = Icons.Rounded.MedicalServices) {
                if (doc.qualification.isNotBlank()) InfoRow(tr(lang, "ডিগ্রি / যোগ্যতা", "Degree / Qualification"), doc.qualification)
                if (specName.isNotBlank()) InfoRow(tr(lang, "বিশেষজ্ঞতা", "Specialty"), specName)
                if (doc.subSpecialty.isNotBlank()) InfoRow(tr(lang, "সাব-স্পেশালিটি", "Sub-Specialty"), doc.subSpecialty)
                if (doc.currentDesignation.isNotBlank()) InfoRow(tr(lang, "পদবি", "Designation"), doc.currentDesignation)
                if (doc.currentHospital.isNotBlank()) InfoRow(tr(lang, "প্রতিষ্ঠান / হাসপাতাল", "Institution / Hospital"), doc.currentHospital)
            }
        }

        // DOC-02: Doctor Details Page Placement + Direct Sponsor Native Card
        item {
            PlacementSponsorSlot(
                placementId = PlacementRateCardEngine.DOC_02,
                sponsors = sponsors,
                rateCards = rateCards,
                adConfig = adConfig,
                isOnline = isOnline,
                nowMs = nowMs,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked
            )
            NativeSponsorAdCard(
                sponsors = sponsors,
                adConfig = adConfig,
                pageKey = SponsorCampaignSelector.PAGE_DOCTOR_PROFILE,
                slotIndex = 0,
                isOnline = isOnline,
                nowMs = nowMs,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked
            )
            if (isOnline && adConfig.nativeBannerEnabled) {
                Spacer(Modifier.height(8.dp))
                ManagedNativeBannerAd(
                    adConfig = adConfig,
                    isOnline = isOnline,
                    refreshKey = "doc_profile_${doc.id}"
                )
            }
        }

        // Active Chambers & Weekly Schedules + CHA-03: Doctor Schedule Details
        item {
            Text(
                text = tr(lang, "চেম্বার ও সাপ্তাহিক সময়সূচী", "Chambers & Weekly Schedules"),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            PlacementSponsorSlot(
                placementId = PlacementRateCardEngine.CHA_03,
                sponsors = sponsors,
                rateCards = rateCards,
                adConfig = adConfig,
                isOnline = isOnline,
                nowMs = nowMs,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (activeChambers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        tr(lang, "বর্তমানে কোনো সক্রিয় চেম্বার নেই।", "No active chamber locations available."),
                        modifier = Modifier.padding(16.dp),
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(activeChambers, key = { it.chamber.id }) { cw ->
                val ch = cw.chamber
                Surface(
                    shape = RoundedCornerShape(20.dp),
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
                                    ch.facilityName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MedicalBlueDark,
                                    fontWeight = FontWeight.Bold
                                )
                                if (ch.chamberName.isNotBlank()) {
                                    Text(
                                        ch.chamberName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MedicalTealDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            StatusPill(
                                text = tr(lang, "সক্রিয়", "Active"),
                                bgColor = EmeraldSoft,
                                textColor = Color(0xFF065F46)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val locationPart = listOf(ch.upazila, ch.district).filter { it.isNotBlank() }.joinToString(", ")
                        Text(
                            text = if (locationPart.isNotBlank()) "${ch.fullAddress} ($locationPart)" else ch.fullAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(12.dp))
                        ChamberWeeklyCalendar(
                            schedules = cw.schedules,
                            lang = lang
                        )

                        if (ch.serialPhone.isNotBlank() || ch.googleMapsUrl.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (ch.serialPhone.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ch.serialPhone.trim()}"))
                                                context.startActivity(intent)
                                            }.onFailure {
                                                Toast.makeText(
                                                    context,
                                                    tr(lang, "কল করার অ্যাপ পাওয়া যায়নি", "No phone dialer app available"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Call, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(AppStrings.callForSerial(lang), maxLines = 1)
                                    }
                                }
                                if (ch.googleMapsUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            val rawUrl = ch.googleMapsUrl.trim()
                                            val normalizedUrl = if (
                                                rawUrl.startsWith("http://", ignoreCase = true) ||
                                                rawUrl.startsWith("https://", ignoreCase = true) ||
                                                rawUrl.startsWith("geo:", ignoreCase = true)
                                            ) rawUrl else "https://$rawUrl"
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
                                                context.startActivity(intent)
                                            }.onFailure {
                                                Toast.makeText(
                                                    context,
                                                    tr(lang, "ম্যাপ খোলার অ্যাপ পাওয়া যায়নি", "Unable to open map link"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Map, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(AppStrings.openMaps(lang), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // DOC-05: Doctor Details Related Section + DOC-04: Doctor Profile Bottom
        item {
            PlacementSponsorSlot(
                placementId = PlacementRateCardEngine.DOC_05,
                sponsors = sponsors,
                rateCards = rateCards,
                adConfig = adConfig,
                isOnline = isOnline,
                nowMs = nowMs,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked
            )
            Spacer(Modifier.height(8.dp))
            PlacementSponsorSlot(
                placementId = PlacementRateCardEngine.DOC_04,
                sponsors = sponsors,
                rateCards = rateCards,
                adConfig = adConfig,
                isOnline = isOnline,
                nowMs = nowMs,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ChamberWeeklyCalendar(
    schedules: List<ChamberScheduleEntity>,
    lang: AppLang
) {
    val today = remember { DayOfWeekBd.today() }
    val activeDays = remember(schedules) { schedules.map { it.dayOfWeek }.toSet() }
    var focusedDay by remember(schedules) {
        mutableStateOf(
            if (today.name in activeDays) today
            else DayOfWeekBd.entries.firstOrNull { it.name in activeDays } ?: today
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundIce, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header row with active days badge & Available Today pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MedicalBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tr(lang, "সাপ্তাহিক ক্যালেন্ডার", "Weekly Calendar"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            if (today.name in activeDays) {
                StatusPill(
                    text = tr(lang, "আজ খোলা", "Open Today"),
                    bgColor = EmeraldSoft,
                    textColor = Color(0xFF065F46),
                    icon = Icons.Rounded.CheckCircle
                )
            } else {
                StatusPill(
                    text = tr(lang, "${activeDays.size}/৭ দিন", "${activeDays.size}/7 days"),
                    bgColor = MedicalBlueLight,
                    textColor = MedicalBlueDark
                )
            }
        }

        // Interactive 7-Day Calendar Strip (Sat → Fri)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DayOfWeekBd.entries.forEach { day ->
                val isOpen = day.name in activeDays
                val isToday = day == today
                val isSelected = day == focusedDay
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isSelected && isOpen -> MedicalBlue
                        isSelected -> TextSecondary
                        isOpen -> SurfaceWhite
                        else -> SurfaceMuted.copy(alpha = 0.65f)
                    },
                    border = when {
                        isToday && !isSelected -> BorderStroke(1.5.dp, MedicalTeal)
                        isOpen && !isSelected -> BorderStroke(1.dp, MedicalBlueContainer)
                        else -> null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { focusedDay = day }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.shortBn,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isSelected -> Color.White
                                isOpen -> TextPrimary
                                else -> TextMuted
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Text(
                            text = day.shortEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isSelected -> Color.White.copy(alpha = 0.85f)
                                isOpen -> MedicalBlueDark
                                else -> TextMuted
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = when {
                                        isSelected && isOpen -> Color.White
                                        isOpen -> EmeraldAvailable
                                        else -> Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Full 7-Day Bilingual Schedule List with Morning / Afternoon AM-PM Pills
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeekBd.entries.forEach { day ->
                val daySlots = schedules
                    .filter { it.dayOfWeek == day.name }
                    .sortedBy { it.startMinutesOfDay() }
                val isFocused = day == focusedDay
                val isToday = day == today

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFocused) SurfaceWhite else SurfaceWhite.copy(alpha = 0.65f),
                    border = if (isFocused) BorderStroke(1.dp, MedicalBlue) else BorderStroke(0.5.dp, OutlineSoft),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { focusedDay = day }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(0.38f)) {
                            Text(
                                text = "${day.bnLabel}${if (isToday) " •" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (daySlots.isNotEmpty() || isToday) FontWeight.Bold else FontWeight.Medium,
                                color = if (daySlots.isNotEmpty()) TextPrimary else TextMuted
                            )
                            Text(
                                text = day.enLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) MedicalTealDark else TextMuted
                            )
                        }
                        if (daySlots.isEmpty()) {
                            Text(
                                text = tr(lang, "বন্ধ (Closed)", "Closed"),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        } else {
                            Column(
                                modifier = Modifier.weight(0.62f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                daySlots.forEach { slot ->
                                    val session = slot.sessionType()
                                    val (bg, fg, icon) = when (session) {
                                        ChamberSessionType.MORNING -> Triple(AmberSoft, Color(0xFF92400E), Icons.Rounded.WbSunny)
                                        ChamberSessionType.AFTERNOON -> Triple(MedicalTealLight, MedicalTealDark, Icons.Rounded.LightMode)
                                        ChamberSessionType.EVENING -> Triple(MedicalBlueLight, MedicalBlueDark, Icons.Rounded.NightsStay)
                                    }
                                    val sessionLabel = slot.slotLabel.ifBlank { tr(lang, session.bnLabel, session.enLabel) }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = bg
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
                                            Text(
                                                text = "$sessionLabel: ${slot.openTime} – ${slot.closeTime}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = fg,
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
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MedicalBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}
