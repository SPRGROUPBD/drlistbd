package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.example.data.local.ChamberScheduleEntity
import com.example.data.local.SiteSettingsEntity
import com.example.data.model.ChamberSessionType
import com.example.data.model.ChamberWithSchedules
import com.example.data.model.DayOfWeekBd
import com.example.data.model.DoctorFullProfile
import com.example.data.model.sessionType
import com.example.data.model.startMinutesOfDay
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.components.ExternalImageAvatar
import com.example.ui.components.StatusPill
import com.example.ui.theme.*

@Composable
fun DoctorProfileScreen(
    profile: DoctorFullProfile?,
    lang: AppLang,
    settings: SiteSettingsEntity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSubmitReport: (reason: String, details: String) -> Unit,
    onRequestAppointment: (chamberId: String, name: String, phone: String, day: String, slot: String, notes: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var showReportDialog by remember { mutableStateOf(false) }
    var bookingChamber by remember { mutableStateOf<ChamberWithSchedules?>(null) }

    if (profile == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr(lang, "ডাক্তারের তথ্য পাওয়া যায়নি", "Doctor profile not found"))
        }
        return
    }

    val doc = profile.doctor
    val specName = profile.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: doc.subSpecialty

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
                            contentDescription = tr(lang, doc.fullNameBn, doc.fullNameEn),
                            fallbackIcon = Icons.Rounded.Person,
                            size = 86.dp,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (doc.bmdcVerified) {
                                    StatusPill(
                                        text = AppStrings.bmdcVerified(lang),
                                        bgColor = EmeraldSoft,
                                        textColor = MedicalTealDark,
                                        icon = Icons.Rounded.Verified
                                    )
                                } else {
                                    StatusPill(
                                        text = AppStrings.unverified(lang),
                                        bgColor = SurfaceMuted,
                                        textColor = TextSecondary
                                    )
                                }
                                if (doc.isDemoData) {
                                    StatusPill("DEMO", AmberSoft, Color(0xFF92400E))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(doc.fullNameBn, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Text(doc.fullNameEn, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                specName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MedicalBlueDark,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "BMDC Reg: ${doc.bmdcNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }

                    if (doc.bioBn.isNotBlank() || doc.bioEn.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = tr(lang, doc.bioBn.ifBlank { doc.bioEn }, doc.bioEn.ifBlank { doc.bioBn }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    // Action Row: Save, Share, Report
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
                                val shareText = "${doc.fullNameBn} (${doc.fullNameEn})\n$specName\nBMDC: ${doc.bmdcNumber}\n${doc.currentHospital}\nShared via Doctor Directory Bangladesh"
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, AppStrings.shareProfile(lang)))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(AppStrings.shareProfile(lang), maxLines = 1)
                        }
                        IconButton(
                            onClick = { showReportDialog = true },
                            modifier = Modifier.testTag("profile_report_btn")
                        ) {
                            Icon(Icons.Outlined.Flag, contentDescription = AppStrings.reportIncorrect(lang), tint = RoseAlert)
                        }
                    }
                }
            }
        }

        // Specialization & Experience
        item {
            ProfileSectionCard(title = tr(lang, "বিশেষজ্ঞতা ও অভিজ্ঞতা", "Specialization & Experience"), icon = Icons.Rounded.MedicalServices) {
                InfoRow(tr(lang, "মূল বিশেষজ্ঞতা", "Main Specialty"), specName)
                if (doc.subSpecialty.isNotBlank()) InfoRow(tr(lang, "সাব-স্পেশালিটি", "Sub-specialty"), doc.subSpecialty)
                if (doc.expertise.isNotBlank()) InfoRow(tr(lang, "চিকিৎসা দক্ষতা", "Treatment Expertise"), doc.expertise)
                if (doc.totalExperienceYears > 0) InfoRow(tr(lang, "মোট অভিজ্ঞতা", "Total Experience"), "${doc.totalExperienceYears} years")
                if (doc.teachingExperienceYears > 0) InfoRow(tr(lang, "শিক্ষকতা অভিজ্ঞতা", "Teaching Experience"), "${doc.teachingExperienceYears} years")
                if (doc.trainings.isNotBlank()) InfoRow(tr(lang, "প্রশিক্ষণ ও সনদ", "Trainings & Certifications"), doc.trainings)
                if (doc.gender.isNotBlank()) InfoRow(tr(lang, "লিঙ্গ", "Gender"), doc.gender)
                if (doc.contactNumber.isNotBlank()) InfoRow(tr(lang, "যোগাযোগ", "Contact"), doc.contactNumber)
            }
        }

        // Current Employment
        item {
            ProfileSectionCard(title = tr(lang, "বর্তমান কর্মস্থল", "Current Employment"), icon = Icons.Rounded.WorkOutline) {
                if (doc.currentHospital.isNotBlank()) InfoRow(tr(lang, "হাসপাতাল", "Hospital"), doc.currentHospital)
                if (doc.currentMedicalCollege.isNotBlank()) InfoRow(tr(lang, "মেডিকেল কলেজ", "Medical College"), doc.currentMedicalCollege)
                if (doc.currentDepartment.isNotBlank()) InfoRow(tr(lang, "বিভাগ", "Department"), doc.currentDepartment)
                if (doc.currentDesignation.isNotBlank()) InfoRow(tr(lang, "পদবি", "Designation"), doc.currentDesignation)
                if (doc.employmentStartDate.isNotBlank()) InfoRow(tr(lang, "যোগদানের তারিখ", "Start Date"), doc.employmentStartDate)
            }
        }

        // Education & Qualifications
        if (profile.educations.isNotEmpty()) {
            item {
                ProfileSectionCard(title = tr(lang, "শিক্ষাগত যোগ্যতা", "Education & Qualifications"), icon = Icons.Rounded.School) {
                    profile.educations.forEach { edu ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${edu.degreeName} (${edu.passingYear})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(edu.institution, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            if (edu.specialTraining.isNotBlank()) {
                                Text(edu.specialTraining, style = MaterialTheme.typography.bodySmall, color = MedicalTealDark)
                            }
                            HorizontalDivider(Modifier.padding(top = 6.dp), color = SurfaceMuted)
                        }
                    }
                }
            }
        }

        // Previous Employment History
        if (profile.employmentHistory.isNotEmpty()) {
            item {
                ProfileSectionCard(title = tr(lang, "পূর্ববর্তী কর্মস্থলের ইতিহাস", "Previous Employment History"), icon = Icons.Rounded.History) {
                    profile.employmentHistory.forEach { emp ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${emp.designation} • ${emp.hospitalName}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            if (emp.medicalCollege.isNotBlank()) Text(emp.medicalCollege, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("${emp.department} (${emp.startDate} - ${emp.endDate})", style = MaterialTheme.typography.labelMedium, color = MedicalBlueDark)
                            if (emp.description.isNotBlank()) Text(emp.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            HorizontalDivider(Modifier.padding(top = 6.dp), color = SurfaceMuted)
                        }
                    }
                }
            }
        }

        // Chambers & Weekly Schedules (Separated by Location — NO FEE INFO)
        item {
            Text(
                text = tr(lang, "চেম্বার ও সাপ্তাহিক সময়সূচী", "Chambers & Weekly Schedules"),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (profile.chambers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        tr(lang, "এখনো কোনো চেম্বার যুক্ত করা হয়নি।", "No chamber locations added yet."),
                        modifier = Modifier.padding(16.dp),
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(profile.chambers, key = { it.chamber.id }) { cw ->
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
                            Text(
                                ch.facilityName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MedicalBlueDark,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(
                                text = if (ch.isActive) tr(lang, "সক্রিয়", "Active") else tr(lang, "সাময়িক বন্ধ", "Temp Closed"),
                                bgColor = if (ch.isActive) EmeraldSoft else RoseSoft,
                                textColor = if (ch.isActive) Color(0xFF065F46) else RoseAlert
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${ch.fullAddress} (${ch.upazila}, ${ch.district})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (ch.instructions.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(ch.instructions, style = MaterialTheme.typography.bodySmall, color = MedicalTealDark)
                        }

                        Spacer(Modifier.height(12.dp))
                        ChamberWeeklyCalendar(
                            schedules = cw.schedules,
                            lang = lang
                        )

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (ch.serialPhone.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ch.serialPhone}"))
                                        context.startActivity(intent)
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
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ch.googleMapsUrl))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Map, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(AppStrings.openMaps(lang), maxLines = 1)
                                }
                            }
                        }
                        if (settings.allowOnlineBooking && ch.isActive && cw.schedules.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { bookingChamber = cw },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.EventNote, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(AppStrings.requestAppointment(lang))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("Wrong Chamber Time") }
        var details by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(AppStrings.reportIncorrect(lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(tr(lang, "কারণ", "Reason")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text(tr(lang, "বিস্তারিত", "Details")) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSubmitReport(reason, details)
                    showReportDialog = false
                }) { Text(tr(lang, "জমা দিন", "Submit")) }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text(tr(lang, "বাতিল", "Cancel")) }
            }
        )
    }

    bookingChamber?.let { cw ->
        val orderedSchedules = remember(cw.schedules) {
            cw.schedules.sortedWith(
                compareBy<ChamberScheduleEntity> { s ->
                    DayOfWeekBd.entries.indexOfFirst { it.name == s.dayOfWeek }.let { if (it < 0) 99 else it }
                }.thenBy { it.startMinutesOfDay() }
            )
        }
        var patientName by remember { mutableStateOf("") }
        var patientPhone by remember { mutableStateOf("") }
        var selectedSchedule by remember { mutableStateOf(orderedSchedules.firstOrNull()) }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { bookingChamber = null },
            title = { Text(AppStrings.requestAppointment(lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            lang,
                            "চেম্বার: ${cw.chamber.facilityName}\nনোট: অনুরোধটি চেম্বার কর্তৃপক্ষ যাচাই করার পর নিশ্চিত হবে।",
                            "Chamber: ${cw.chamber.facilityName}\nNote: Requests require confirmation by the authorized chamber."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = patientName,
                        onValueChange = { patientName = it },
                        label = { Text(tr(lang, "রোগীর নাম", "Patient Name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = patientPhone,
                        onValueChange = { patientPhone = it },
                        label = { Text(tr(lang, "মোবাইল নম্বর", "Phone Number")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (orderedSchedules.isNotEmpty()) {
                        Text(
                            tr(lang, "পছন্দের বার ও সময় বেছে নিন:", "Choose Preferred Day & Slot:"),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(orderedSchedules, key = { it.id }) { slot ->
                                val dayObj = DayOfWeekBd.entries.firstOrNull { it.name == slot.dayOfWeek }
                                val dayText = dayObj?.let { tr(lang, it.shortBn, it.shortEn) } ?: slot.dayOfWeek
                                FilterChip(
                                    selected = selectedSchedule?.id == slot.id,
                                    onClick = { selectedSchedule = slot },
                                    label = { Text("$dayText • ${slot.openTime}–${slot.closeTime}") }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(lang, "নোট (ঐচ্ছিক)", "Notes (optional)")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val chosenDay = selectedSchedule?.dayOfWeek ?: DayOfWeekBd.SATURDAY.name
                    val chosenSlot = selectedSchedule?.let { "${it.openTime} - ${it.closeTime}" } ?: ""
                    onRequestAppointment(cw.chamber.id, patientName, patientPhone, chosenDay, chosenSlot, notes)
                    bookingChamber = null
                }) { Text(tr(lang, "অনুরোধ পাঠান", "Send Request")) }
            },
            dismissButton = {
                TextButton(onClick = { bookingChamber = null }) { Text(tr(lang, "বাতিল", "Cancel")) }
            }
        )
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
