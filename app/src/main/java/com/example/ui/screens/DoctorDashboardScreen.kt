package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.data.model.DayOfWeekBd
import com.example.data.model.DoctorFullProfile
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.components.ExternalImageAvatar
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDashboardScreen(
    lang: AppLang,
    currentUser: UserEntity,
    doctorProfile: DoctorFullProfile?,
    specialties: List<SpecialtyEntity>,
    appointments: List<AppointmentRequestEntity>,
    onSaveProfile: (DoctorEntity) -> Unit,
    onAddEducation: (String, String, String, String, String) -> Unit,
    onDeleteEducation: (String) -> Unit,
    onAddEmployment: (String, String, String, String, String, String, String, String) -> Unit,
    onDeleteEmployment: (String) -> Unit,
    onSaveChamber: (ChamberEntity) -> Unit,
    onDeleteChamber: (String) -> Unit,
    onAddSchedule: (String, String, DayOfWeekBd, String, String, String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onSubmitVerification: (String, String, String) -> Unit,
    onSubmitChangeRequest: (String, String, String, String, String) -> Unit,
    onUpdateAppointment: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        tr(lang, "প্রোফাইল", "Profile"),
        tr(lang, "যোগ্যতা ও চাকরি", "Edu & Jobs"),
        tr(lang, "চেম্বার ও সময়", "Chambers"),
        tr(lang, "সিরিয়াল", "Bookings")
    )

    val doc = doctorProfile?.doctor ?: DoctorEntity(
        id = "doc_${currentUser.id}",
        userId = currentUser.id,
        fullNameBn = currentUser.name,
        fullNameEn = currentUser.name,
        bmdcNumber = "",
        contactNumber = currentUser.phone,
        mainSpecialtyId = specialties.firstOrNull()?.id ?: ""
    )

    val docAppointments = remember(appointments, doc.id) {
        appointments.filter { it.doctorId == doc.id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
    ) {
        // Status Banner (Cannot self-verify)
        Surface(
            color = SurfaceWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr(lang, "ডাক্তার ড্যাশবোর্ড", "Doctor Dashboard"),
                        style = MaterialTheme.typography.titleLarge,
                        color = MedicalBlueDark
                    )
                    StatusPill(
                        text = if (doc.bmdcVerified) tr(lang, "BMDC ভেরিফায়েড", "BMDC Verified") else doc.approvalStatus,
                        bgColor = if (doc.bmdcVerified) EmeraldSoft else AmberSoft,
                        textColor = if (doc.bmdcVerified) MedicalTealDark else Color(0xFF92400E)
                    )
                }
                Spacer(Modifier.height(8.dp))
                ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = SurfaceWhite, edgePadding = 0.dp) {
                    tabs.forEachIndexed { idx, t ->
                        Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(t) })
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> DoctorProfileEditTab(
                lang = lang,
                doc = doc,
                specialties = specialties,
                onSave = onSaveProfile,
                onSubmitVerification = onSubmitVerification,
                onSubmitChangeRequest = onSubmitChangeRequest
            )

            1 -> DoctorEduJobsTab(
                lang = lang,
                doctorId = doc.id,
                educations = doctorProfile?.educations.orEmpty(),
                employments = doctorProfile?.employmentHistory.orEmpty(),
                onAddEducation = onAddEducation,
                onDeleteEducation = onDeleteEducation,
                onAddEmployment = onAddEmployment,
                onDeleteEmployment = onDeleteEmployment
            )

            2 -> DoctorChambersTab(
                lang = lang,
                doctorId = doc.id,
                chambers = doctorProfile?.chambers.orEmpty(),
                onSaveChamber = onSaveChamber,
                onDeleteChamber = onDeleteChamber,
                onAddSchedule = onAddSchedule,
                onDeleteSchedule = onDeleteSchedule
            )

            3 -> DoctorBookingsTab(
                lang = lang,
                appointments = docAppointments,
                onUpdateStatus = onUpdateAppointment
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoctorProfileEditTab(
    lang: AppLang,
    doc: DoctorEntity,
    specialties: List<SpecialtyEntity>,
    onSave: (DoctorEntity) -> Unit,
    onSubmitVerification: (String, String, String) -> Unit,
    onSubmitChangeRequest: (String, String, String, String, String) -> Unit
) {
    var nameBn by remember(doc.id) { mutableStateOf(doc.fullNameBn) }
    var nameEn by remember(doc.id) { mutableStateOf(doc.fullNameEn) }
    var photoUrl by remember(doc.id) { mutableStateOf(doc.photoUrl) }
    var bmdc by remember(doc.id) { mutableStateOf(doc.bmdcNumber) }
    var gender by remember(doc.id) { mutableStateOf(doc.gender) }
    var bioBn by remember(doc.id) { mutableStateOf(doc.bioBn) }
    var bioEn by remember(doc.id) { mutableStateOf(doc.bioEn) }
    var phone by remember(doc.id) { mutableStateOf(doc.contactNumber) }
    var specId by remember(doc.id) { mutableStateOf(doc.mainSpecialtyId) }
    var subSpec by remember(doc.id) { mutableStateOf(doc.subSpecialty) }
    var expertise by remember(doc.id) { mutableStateOf(doc.expertise) }
    var hospital by remember(doc.id) { mutableStateOf(doc.currentHospital) }
    var college by remember(doc.id) { mutableStateOf(doc.currentMedicalCollege) }
    var dept by remember(doc.id) { mutableStateOf(doc.currentDepartment) }
    var designation by remember(doc.id) { mutableStateOf(doc.currentDesignation) }
    var expYears by remember(doc.id) { mutableStateOf(doc.totalExperienceYears.toString()) }
    var teachYears by remember(doc.id) { mutableStateOf(doc.teachingExperienceYears.toString()) }
    var trainings by remember(doc.id) { mutableStateOf(doc.trainings) }
    var specMenu by remember { mutableStateOf(false) }

    var showChangeModal by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExternalImageAvatar(photoUrl, nameEn, Icons.Rounded.Person, size = 64.dp)
                        OutlinedTextField(
                            value = photoUrl,
                            onValueChange = { photoUrl = it },
                            label = { Text(tr(lang, "ছবির এক্সটার্নাল URL", "External Photo URL")) },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(nameBn, { nameBn = it }, label = { Text("Name (Bangla)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameEn, { nameEn = it }, label = { Text("Name (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(bmdc, { bmdc = it }, label = { Text("BMDC Number *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    ExposedDropdownMenuBox(expanded = specMenu, onExpandedChange = { specMenu = it }) {
                        val sp = specialties.firstOrNull { it.id == specId }
                        OutlinedTextField(
                            value = sp?.let { tr(lang, it.nameBn, it.nameEn) } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr(lang, "মূল বিশেষজ্ঞতা", "Main Specialty")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = specMenu, onDismissRequest = { specMenu = false }) {
                            specialties.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(tr(lang, s.nameBn, s.nameEn)) },
                                    onClick = { specId = s.id; specMenu = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(subSpec, { subSpec = it }, label = { Text("Sub-specialty") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(expertise, { expertise = it }, label = { Text("Treatment Expertise") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(hospital, { hospital = it }, label = { Text("Current Hospital") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(college, { college = it }, label = { Text("Medical College") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dept, { dept = it }, label = { Text("Department") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(designation, { designation = it }, label = { Text("Designation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(expYears, { expYears = it.filter(Char::isDigit) }, label = { Text("Exp (Yrs)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(teachYears, { teachYears = it.filter(Char::isDigit) }, label = { Text("Teaching (Yrs)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(trainings, { trainings = it }, label = { Text("Trainings & Certifications") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Contact Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(gender, { gender = it }, label = { Text("Gender (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(bioBn, { bioBn = it }, label = { Text("Bio (Bangla)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(bioEn, { bioEn = it }, label = { Text("Bio (English)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            onSave(
                                doc.copy(
                                    fullNameBn = nameBn,
                                    fullNameEn = nameEn,
                                    photoUrl = photoUrl,
                                    bmdcNumber = bmdc,
                                    gender = gender,
                                    bioBn = bioBn,
                                    bioEn = bioEn,
                                    contactNumber = phone,
                                    mainSpecialtyId = specId,
                                    subSpecialty = subSpec,
                                    expertise = expertise,
                                    currentHospital = hospital,
                                    currentMedicalCollege = college,
                                    currentDepartment = dept,
                                    currentDesignation = designation,
                                    totalExperienceYears = expYears.toIntOrNull() ?: 0,
                                    teachingExperienceYears = teachYears.toIntOrNull() ?: 0,
                                    trainings = trainings
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(lang, "প্রোফাইল সংরক্ষণ করুন", "Save Doctor Profile"))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onSubmitVerification(doc.id, bmdc, "Doctor requested BMDC verification") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(tr(lang, "BMDC যাচাই অনুরোধ", "Request BMDC Verify"), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showChangeModal = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(tr(lang, "পরিবর্তন অনুরোধ", "Request Review"), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showChangeModal) {
        var summary by remember { mutableStateOf("") }
        var newDesig by remember { mutableStateOf(designation) }
        var newHosp by remember { mutableStateOf(hospital) }
        AlertDialog(
            onDismissRequest = { showChangeModal = false },
            title = { Text(tr(lang, "প্রোফাইল পরিবর্তনের অনুরোধ", "Request Profile Review")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(summary, { summary = it }, label = { Text("Change summary") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newDesig, { newDesig = it }, label = { Text("Updated Designation") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newHosp, { newHosp = it }, label = { Text("Updated Hospital") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSubmitChangeRequest(doc.id, summary, newDesig, newHosp, bioBn)
                    showChangeModal = false
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showChangeModal = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DoctorEduJobsTab(
    lang: AppLang,
    doctorId: String,
    educations: List<EducationEntity>,
    employments: List<EmploymentHistoryEntity>,
    onAddEducation: (String, String, String, String, String) -> Unit,
    onDeleteEducation: (String) -> Unit,
    onAddEmployment: (String, String, String, String, String, String, String, String) -> Unit,
    onDeleteEmployment: (String) -> Unit
) {
    var degree by remember { mutableStateOf("") }
    var inst by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var training by remember { mutableStateOf("") }

    var empHosp by remember { mutableStateOf("") }
    var empDesig by remember { mutableStateOf("") }
    var empDept by remember { mutableStateOf("") }
    var empStart by remember { mutableStateOf("") }
    var empEnd by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(lang, "শিক্ষাগত যোগ্যতা যুক্ত করুন", "Add Qualification"), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(degree, { degree = it }, label = { Text("Degree (e.g. MBBS, FCPS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(inst, { inst = it }, label = { Text("Institution") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(year, { year = it }, label = { Text("Year") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(training, { training = it }, label = { Text("Special Training") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (degree.isNotBlank()) {
                                onAddEducation(doctorId, degree, inst, year, training)
                                degree = ""; inst = ""; year = ""; training = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Qualification") }
                }
            }
        }
        items(educations, key = { it.id }) { edu ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${edu.degreeName} (${edu.passingYear})", fontWeight = FontWeight.SemiBold)
                        Text(edu.institution, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { onDeleteEducation(edu.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert)
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(lang, "পূর্ববর্তী কর্মস্থল যুক্ত করুন", "Add Previous Employment"), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(empHosp, { empHosp = it }, label = { Text("Hospital / College") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(empDesig, { empDesig = it }, label = { Text("Designation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(empDept, { empDept = it }, label = { Text("Department") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(empStart, { empStart = it }, label = { Text("Start Date") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(empEnd, { empEnd = it }, label = { Text("End Date") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (empHosp.isNotBlank()) {
                                onAddEmployment(doctorId, empHosp, "", empDesig, empDept, empStart, empEnd, "")
                                empHosp = ""; empDesig = ""; empDept = ""; empStart = ""; empEnd = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Employment History") }
                }
            }
        }
        items(employments, key = { it.id }) { emp ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${emp.designation} • ${emp.hospitalName}", fontWeight = FontWeight.SemiBold)
                        Text("${emp.department} (${emp.startDate} - ${emp.endDate})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { onDeleteEmployment(emp.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoctorChambersTab(
    lang: AppLang,
    doctorId: String,
    chambers: List<com.example.data.model.ChamberWithSchedules>,
    onSaveChamber: (ChamberEntity) -> Unit,
    onDeleteChamber: (String) -> Unit,
    onAddSchedule: (String, String, DayOfWeekBd, String, String, String) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    var facName by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("Dhaka") }
    var upazila by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var mapsUrl by remember { mutableStateOf("") }
    var serialPhone by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    var slotChamberId by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(lang, "নতুন চেম্বার যুক্ত করুন", "Add Chamber Location (No Fees)"), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(facName, { facName = it }, label = { Text("Hospital / Diagnostic Center *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(district, { district = it }, label = { Text("District *") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(upazila, { upazila = it }, label = { Text("Upazila / Area") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(address, { address = it }, label = { Text("Full Address *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(serialPhone, { serialPhone = it }, label = { Text("Serial Booking Phone *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(mapsUrl, { mapsUrl = it }, label = { Text("Google Maps URL (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(instructions, { instructions = it }, label = { Text("Appointment Instructions") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (facName.isNotBlank() && address.isNotBlank()) {
                                onSaveChamber(
                                    ChamberEntity(
                                        id = "ch_${UUID.randomUUID().toString().take(8)}",
                                        doctorId = doctorId,
                                        facilityName = facName,
                                        district = district,
                                        upazila = upazila,
                                        fullAddress = address,
                                        googleMapsUrl = mapsUrl,
                                        serialPhone = serialPhone,
                                        instructions = instructions,
                                        isActive = true
                                    )
                                )
                                facName = ""; address = ""; serialPhone = ""; mapsUrl = ""; instructions = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr(lang, "চেম্বার যোগ করুন", "Save Chamber")) }
                }
            }
        }

        items(chambers, key = { it.chamber.id }) { cw ->
            val ch = cw.chamber
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ch.facilityName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = ch.isActive,
                            onCheckedChange = { onSaveChamber(ch.copy(isActive = it)) }
                        )
                        IconButton(onClick = { onDeleteChamber(ch.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert)
                        }
                    }
                    Text("${ch.fullAddress} (${ch.district})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Serial: ${ch.serialPhone}", style = MaterialTheme.typography.bodySmall, color = MedicalBlueDark)

                    cw.schedules.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${s.dayOfWeek}: ${s.openTime} - ${s.closeTime}", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { onDeleteSchedule(s.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, null, tint = RoseAlert, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    OutlinedButton(onClick = { slotChamberId = ch.id }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(lang, "সময়সূচী স্লট যোগ করুন", "Add Time Slot"))
                    }
                }
            }
        }
    }

    slotChamberId?.let { chId ->
        var day by remember { mutableStateOf(DayOfWeekBd.SATURDAY) }
        var openTime by remember { mutableStateOf("05:00 PM") }
        var closeTime by remember { mutableStateOf("08:30 PM") }
        AlertDialog(
            onDismissRequest = { slotChamberId = null },
            title = { Text("Add Weekly Slot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeekBd.entries.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { d ->
                                FilterChip(
                                    selected = day == d,
                                    onClick = { day = d },
                                    label = { Text(d.shortBn) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(openTime, { openTime = it }, label = { Text("Opening Time") }, singleLine = true)
                    OutlinedTextField(closeTime, { closeTime = it }, label = { Text("Closing Time") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddSchedule(chId, doctorId, day, openTime, closeTime, "Slot")
                    slotChamberId = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { slotChamberId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DoctorBookingsTab(
    lang: AppLang,
    appointments: List<AppointmentRequestEntity>,
    onUpdateStatus: (String, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (appointments.isEmpty()) {
            item {
                Text(
                    tr(lang, "কোনো সিরিয়াল অনুরোধ নেই।", "No pending serial requests."),
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(appointments, key = { it.id }) { apt ->
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(apt.patientName, style = MaterialTheme.typography.titleMedium)
                            StatusPill(apt.status, MedicalBlueLight, MedicalBlueDark)
                        }
                        Text("${apt.patientPhone} • ${apt.preferredDay} (${apt.preferredSlot})", style = MaterialTheme.typography.bodySmall)
                        if (apt.status == "PENDING") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onUpdateStatus(apt.id, "CONFIRMED") },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAvailable),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Confirm") }
                                OutlinedButton(
                                    onClick = { onUpdateStatus(apt.id, "REJECTED") },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }
}
