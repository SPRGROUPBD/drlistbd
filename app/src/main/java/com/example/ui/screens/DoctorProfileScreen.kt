package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SiteSettingsEntity
import com.example.data.model.ChamberWithSchedules
import com.example.data.model.DayOfWeekBd
import com.example.data.model.DoctorFullProfile
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

                        Spacer(Modifier.height(10.dp))
                        Text(tr(lang, "সাপ্তাহিক সময়সূচী:", "Weekly Schedule:"), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))

                        DayOfWeekBd.entries.forEach { day ->
                            val slots = cw.schedules.filter { it.dayOfWeek == day.name }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    tr(lang, day.bnLabel, day.enLabel),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (slots.isNotEmpty()) TextPrimary else TextMuted
                                )
                                Text(
                                    text = if (slots.isEmpty()) tr(lang, "বন্ধ", "Closed")
                                    else slots.joinToString(" | ") { "${it.openTime} - ${it.closeTime}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (slots.isNotEmpty()) MedicalBlueDark else TextMuted
                                )
                            }
                        }

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
        var patientName by remember { mutableStateOf("") }
        var patientPhone by remember { mutableStateOf("") }
        var selectedDay by remember { mutableStateOf(cw.schedules.firstOrNull()?.dayOfWeek ?: DayOfWeekBd.SATURDAY.name) }
        var selectedSlot by remember {
            mutableStateOf(
                cw.schedules.firstOrNull()?.let { "${it.openTime} - ${it.closeTime}" } ?: ""
            )
        }
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
                    OutlinedTextField(
                        value = "$selectedDay ($selectedSlot)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(tr(lang, "সময়সূচী", "Slot")) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    onRequestAppointment(cw.chamber.id, patientName, patientPhone, selectedDay, selectedSlot, notes)
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
