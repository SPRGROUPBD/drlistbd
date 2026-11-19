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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.data.model.AdminDashboardStats
import com.example.data.model.DoctorFullProfile
import com.example.data.model.UserRole
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AdminMenu(val label: String, val icon: ImageVector) {
    DASHBOARD("1. Dashboard", Icons.Rounded.Dashboard),
    DOCTORS("2. Doctor Management", Icons.Rounded.MedicalServices),
    PENDING("3. Pending Approvals", Icons.Rounded.HourglassTop),
    VERIFIED("4. Verified Doctors", Icons.Rounded.Verified),
    HOSPITALS("5. Hospital Management", Icons.Rounded.LocalHospital),
    DIAGNOSTICS("6. Diagnostic Center", Icons.Rounded.Biotech),
    SPECIALTIES("7. Specialty Management", Icons.Rounded.Category),
    CHAMBERS("8. Chamber Management", Icons.Rounded.MeetingRoom),
    SCHEDULES("9. Chamber Schedule", Icons.Rounded.Schedule),
    BMDC_VERIFY("10. BMDC Verification", Icons.Rounded.FactCheck),
    EMP_HISTORY("11. Doctor Employment History", Icons.Rounded.History),
    PATIENTS("12. Patient Management", Icons.Rounded.People),
    REPORTS("13. Reports", Icons.Rounded.Flag),
    NOTIFICATIONS("14. Notifications", Icons.Rounded.NotificationsActive),
    USER_ROLES("15. User Roles & Requests", Icons.Rounded.ManageAccounts),
    AUDIT_LOGS("16. Audit Logs", Icons.Rounded.ReceiptLong),
    SETTINGS("17. Website Settings & CSV", Icons.Rounded.Settings),
    LOGOUT("18. Logout", Icons.Rounded.Logout)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    lang: AppLang,
    currentUser: UserEntity,
    stats: AdminDashboardStats,
    doctors: List<DoctorFullProfile>,
    hospitals: List<HospitalEntity>,
    diagnosticCenters: List<DiagnosticCenterEntity>,
    specialties: List<SpecialtyEntity>,
    locations: List<LocationEntity>,
    users: List<UserEntity>,
    verificationRequests: List<VerificationRequestEntity>,
    profileChangeRequests: List<ProfileChangeRequestEntity>,
    hospitalDoctorLinks: List<HospitalDoctorLinkEntity>,
    reports: List<ReportEntity>,
    notifications: List<NotificationEntity>,
    auditLogs: List<AuditLogEntity>,
    settings: SiteSettingsEntity,
    onSaveDoctor: (DoctorEntity) -> Unit,
    onDeleteDoctor: (String) -> Unit,
    onSetDoctorApproval: (String, String) -> Unit,
    onSetDoctorActive: (String, Boolean) -> Unit,
    onVerifyBmdc: (String, Boolean, String) -> Unit,
    onReviewVerificationReq: (VerificationRequestEntity, Boolean, String) -> Unit,
    onReviewProfileChangeReq: (ProfileChangeRequestEntity, Boolean) -> Unit,
    onUpdateLinkStatus: (String, String) -> Unit,
    onSaveHospital: (HospitalEntity) -> Unit,
    onDeleteHospital: (String) -> Unit,
    onSaveDiagnostic: (DiagnosticCenterEntity) -> Unit,
    onDeleteDiagnostic: (String) -> Unit,
    onSaveSpecialty: (SpecialtyEntity) -> Unit,
    onDeleteSpecialty: (String) -> Unit,
    onSaveLocation: (LocationEntity) -> Unit,
    onDeleteLocation: (String) -> Unit,
    onUpdateUserRole: (UserEntity, UserRole, Boolean) -> Unit,
    onResolveReport: (String) -> Unit,
    onSendNotification: (String, String, String, String, String) -> Unit,
    onSaveSettings: (SiteSettingsEntity) -> Unit,
    onClearDemoData: () -> Unit,
    onExportDoctorsCsv: () -> Unit,
    onExportHospitalsCsv: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var activeMenu by remember { mutableStateOf(AdminMenu.DASHBOARD) }
    var editingDoctor by remember { mutableStateOf<DoctorEntity?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceWhite,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Super Admin Panel", style = MaterialTheme.typography.titleLarge, color = MedicalBlueDark)
                    Text("Doctor Directory Bangladesh", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                HorizontalDivider(color = SurfaceMuted)
                LazyColumn(contentPadding = PaddingValues(8.dp)) {
                    items(AdminMenu.entries) { item ->
                        NavigationDrawerItem(
                            selected = activeMenu == item,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (item == AdminMenu.LOGOUT) onLogout() else activeMenu = item
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label, style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.testTag("admin_menu_${item.name}")
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundIce)
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite),
                navigationIcon = {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.testTag("admin_drawer_toggle")
                    ) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Admin Menu")
                    }
                },
                title = { Text(activeMenu.label, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    TextButton(onClick = onBack) { Text(tr(lang, "সাইট", "Public")) }
                }
            )

            when (activeMenu) {
                AdminMenu.DASHBOARD -> AdminDashboardOverview(stats, onNavigate = { activeMenu = it })
                AdminMenu.DOCTORS -> AdminDoctorsList(
                    doctors = doctors,
                    onAdd = {
                        editingDoctor = DoctorEntity(
                            id = "doc_${UUID.randomUUID().toString().take(8)}",
                            fullNameBn = "",
                            fullNameEn = "",
                            bmdcNumber = "",
                            mainSpecialtyId = specialties.firstOrNull()?.id ?: "",
                            approvalStatus = "APPROVED"
                        )
                    },
                    onEdit = { editingDoctor = it },
                    onApprove = { onSetDoctorApproval(it, "APPROVED") },
                    onReject = { onSetDoctorApproval(it, "REJECTED") },
                    onVerify = { id, verified -> onVerifyBmdc(id, verified, "Verified by ${currentUser.name}") },
                    onToggleActive = onSetDoctorActive,
                    onDelete = onDeleteDoctor
                )
                AdminMenu.PENDING -> AdminDoctorsList(
                    doctors = doctors.filter { it.doctor.approvalStatus == "PENDING" },
                    onAdd = null,
                    onEdit = { editingDoctor = it },
                    onApprove = { onSetDoctorApproval(it, "APPROVED") },
                    onReject = { onSetDoctorApproval(it, "REJECTED") },
                    onVerify = { id, verified -> onVerifyBmdc(id, verified, "Verified by Admin") },
                    onToggleActive = onSetDoctorActive,
                    onDelete = onDeleteDoctor
                )
                AdminMenu.VERIFIED -> AdminDoctorsList(
                    doctors = doctors.filter { it.doctor.bmdcVerified },
                    onAdd = null,
                    onEdit = { editingDoctor = it },
                    onApprove = { onSetDoctorApproval(it, "APPROVED") },
                    onReject = { onSetDoctorApproval(it, "REJECTED") },
                    onVerify = { id, verified -> onVerifyBmdc(id, verified, "Status updated by Admin") },
                    onToggleActive = onSetDoctorActive,
                    onDelete = onDeleteDoctor
                )
                AdminMenu.BMDC_VERIFY -> AdminBmdcVerificationScreen(doctors, verificationRequests, onVerifyBmdc, onReviewVerificationReq)
                AdminMenu.CHAMBERS -> AdminChambersList(doctors)
                AdminMenu.SCHEDULES -> AdminSchedulesList(doctors)
                AdminMenu.EMP_HISTORY -> AdminEmploymentHistoryList(doctors)
                AdminMenu.HOSPITALS -> AdminHospitalsModule(hospitals, onSaveHospital, onDeleteHospital)
                AdminMenu.DIAGNOSTICS -> AdminDiagnosticsModule(diagnosticCenters, onSaveDiagnostic, onDeleteDiagnostic)
                AdminMenu.SPECIALTIES -> AdminSpecialtiesModule(specialties, onSaveSpecialty, onDeleteSpecialty)
                AdminMenu.PATIENTS -> AdminUsersModule(users.filter { it.role == UserRole.PATIENT.name }, onUpdateUserRole)
                AdminMenu.REPORTS -> AdminReportsModule(reports, onResolveReport)
                AdminMenu.NOTIFICATIONS -> AdminNotificationsModule(notifications, onSendNotification)
                AdminMenu.USER_ROLES -> AdminRolesRequestsLocationsModule(
                    users = users,
                    locations = locations,
                    profileChangeRequests = profileChangeRequests,
                    hospitalDoctorLinks = hospitalDoctorLinks,
                    onUpdateUserRole = onUpdateUserRole,
                    onSaveLocation = onSaveLocation,
                    onDeleteLocation = onDeleteLocation,
                    onReviewChangeReq = onReviewProfileChangeReq,
                    onUpdateLinkStatus = onUpdateLinkStatus
                )
                AdminMenu.AUDIT_LOGS -> AdminAuditLogsModule(auditLogs)
                AdminMenu.SETTINGS -> AdminSettingsModule(
                    settings = settings,
                    onSaveSettings = onSaveSettings,
                    onClearDemoData = onClearDemoData,
                    onExportDoctorsCsv = onExportDoctorsCsv,
                    onExportHospitalsCsv = onExportHospitalsCsv
                )
                AdminMenu.LOGOUT -> {}
            }
        }
    }

    editingDoctor?.let { doc ->
        AdminDoctorEditorDialog(
            doc = doc,
            specialties = specialties,
            onDismiss = { editingDoctor = null },
            onSave = {
                onSaveDoctor(it)
                editingDoctor = null
            }
        )
    }
}

@Composable
private fun AdminDashboardOverview(
    stats: AdminDashboardStats,
    onNavigate: (AdminMenu) -> Unit
) {
    val cards = listOf(
        Triple("Total Doctors", stats.totalDoctors, AdminMenu.DOCTORS),
        Triple("Verified Doctors", stats.verifiedDoctors, AdminMenu.VERIFIED),
        Triple("Pending Approvals", stats.pendingDoctors, AdminMenu.PENDING),
        Triple("Rejected Doctors", stats.rejectedDoctors, AdminMenu.DOCTORS),
        Triple("Total Hospitals", stats.totalHospitals, AdminMenu.HOSPITALS),
        Triple("Diagnostic Centers", stats.totalDiagnosticCenters, AdminMenu.DIAGNOSTICS),
        Triple("Total Chambers", stats.totalChambers, AdminMenu.CHAMBERS),
        Triple("Total Patients", stats.totalPatients, AdminMenu.PATIENTS),
        Triple("Profile Requests", stats.profileChangeRequests, AdminMenu.USER_ROLES),
        Triple("Open Reports", stats.openReports, AdminMenu.REPORTS)
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cards.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, count, target) ->
                    Surface(
                        onClick = { onNavigate(target) },
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, OutlineSoft),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = MedicalBlueDark, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminDoctorsList(
    doctors: List<DoctorFullProfile>,
    onAdd: (() -> Unit)?,
    onEdit: (DoctorEntity) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onVerify: (String, Boolean) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onAdd != null) {
            item {
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add New Doctor")
                }
            }
        }
        items(doctors, key = { it.doctor.id }) { item ->
            val d = item.doctor
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.fullNameEn, style = MaterialTheme.typography.titleMedium)
                            Text("${d.fullNameBn} • BMDC: ${d.bmdcNumber}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(checked = d.isActive, onCheckedChange = { onToggleActive(d.id, it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(d.approvalStatus, MedicalBlueLight, MedicalBlueDark)
                        StatusPill(
                            if (d.bmdcVerified) "VERIFIED" else "UNVERIFIED",
                            if (d.bmdcVerified) EmeraldSoft else AmberSoft,
                            if (d.bmdcVerified) MedicalTealDark else Color(0xFF92400E)
                        )
                        if (d.isDemoData) StatusPill("DEMO", AmberSoft, Color(0xFF92400E))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onEdit(d) }, modifier = Modifier.weight(1f)) { Text("Edit") }
                        Button(onClick = { onApprove(d.id) }, colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal), modifier = Modifier.weight(1f)) { Text("Approve") }
                        OutlinedButton(onClick = { onReject(d.id) }, modifier = Modifier.weight(1f)) { Text("Reject") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onVerify(d.id, !d.bmdcVerified) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (d.bmdcVerified) TextMuted else EmeraldAvailable),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (d.bmdcVerified) "Revoke Verify" else "Verify BMDC")
                        }
                        IconButton(onClick = { onDelete(d.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBmdcVerificationScreen(
    doctors: List<DoctorFullProfile>,
    requests: List<VerificationRequestEntity>,
    onVerifyBmdc: (String, Boolean, String) -> Unit,
    onReviewReq: (VerificationRequestEntity, Boolean, String) -> Unit
) {
    var notes by remember { mutableStateOf("BMDC verified via Admin Review") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(notes, { notes = it }, label = { Text("Verification Notes") }, modifier = Modifier.fillMaxWidth())
        }
        items(requests, key = { it.id }) { req ->
            val doc = doctors.firstOrNull { it.doctor.id == req.doctorId }
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(doc?.doctor?.fullNameEn ?: req.doctorId, style = MaterialTheme.typography.titleMedium)
                    Text("BMDC: ${req.bmdcNumber} • Status: ${req.status}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onReviewReq(req, true, notes) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldAvailable), modifier = Modifier.weight(1f)) { Text("Verify") }
                        OutlinedButton(onClick = { onReviewReq(req, false, notes) }, modifier = Modifier.weight(1f)) { Text("Reject") }
                    }
                }
            }
        }
        item { Text("All Doctors Direct Verification", style = MaterialTheme.typography.titleSmall) }
        items(doctors.filter { !it.doctor.bmdcVerified }, key = { "dv_${it.doctor.id}" }) { d ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.doctor.fullNameEn, fontWeight = FontWeight.SemiBold)
                        Text("BMDC: ${d.doctor.bmdcNumber}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onVerifyBmdc(d.doctor.id, true, notes) }) { Text("Verify") }
                }
            }
        }
    }
}

@Composable
private fun AdminChambersList(doctors: List<DoctorFullProfile>) {
    val allChambers = doctors.flatMap { d -> d.chambers.map { d.doctor to it } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(allChambers, key = { it.second.chamber.id }) { (doc, cw) ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${cw.chamber.facilityName} (${doc.fullNameEn})", style = MaterialTheme.typography.titleSmall)
                    Text("${cw.chamber.fullAddress} • ${cw.chamber.district}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("Serial: ${cw.chamber.serialPhone}", style = MaterialTheme.typography.bodySmall, color = MedicalBlueDark)
                }
            }
        }
    }
}

@Composable
private fun AdminSchedulesList(doctors: List<DoctorFullProfile>) {
    val allSlots = doctors.flatMap { d ->
        d.chambers.flatMap { cw -> cw.schedules.map { Triple(d.doctor, cw.chamber, it) } }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(allSlots, key = { it.third.id }) { (doc, ch, s) ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${doc.fullNameEn} @ ${ch.facilityName}", style = MaterialTheme.typography.titleSmall)
                    Text("${s.dayOfWeek}: ${s.openTime} - ${s.closeTime}", style = MaterialTheme.typography.bodySmall, color = MedicalTealDark)
                }
            }
        }
    }
}

@Composable
private fun AdminEmploymentHistoryList(doctors: List<DoctorFullProfile>) {
    val allEmps = doctors.flatMap { d -> d.employmentHistory.map { d.doctor to it } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(allEmps, key = { it.second.id }) { (doc, emp) ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${doc.fullNameEn} • ${emp.designation}", style = MaterialTheme.typography.titleSmall)
                    Text("${emp.hospitalName} (${emp.startDate} - ${emp.endDate})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun AdminDoctorEditorDialog(
    doc: DoctorEntity,
    specialties: List<SpecialtyEntity>,
    onDismiss: () -> Unit,
    onSave: (DoctorEntity) -> Unit
) {
    var nameBn by remember { mutableStateOf(doc.fullNameBn) }
    var nameEn by remember { mutableStateOf(doc.fullNameEn) }
    var bmdc by remember { mutableStateOf(doc.bmdcNumber) }
    var specId by remember { mutableStateOf(doc.mainSpecialtyId.ifBlank { specialties.firstOrNull()?.id ?: "" }) }
    var hospital by remember { mutableStateOf(doc.currentHospital) }
    var designation by remember { mutableStateOf(doc.currentDesignation) }
    var phone by remember { mutableStateOf(doc.contactNumber) }
    var photoUrl by remember { mutableStateOf(doc.photoUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Doctor Editor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nameBn, { nameBn = it }, label = { Text("Name (Bangla)") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("Name (English)") }, singleLine = true)
                OutlinedTextField(bmdc, { bmdc = it }, label = { Text("BMDC Number *") }, singleLine = true)
                OutlinedTextField(hospital, { hospital = it }, label = { Text("Hospital") }, singleLine = true)
                OutlinedTextField(designation, { designation = it }, label = { Text("Designation") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(photoUrl, { photoUrl = it }, label = { Text("Photo URL") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    doc.copy(
                        fullNameBn = nameBn,
                        fullNameEn = nameEn,
                        bmdcNumber = bmdc,
                        mainSpecialtyId = specId,
                        currentHospital = hospital,
                        currentDesignation = designation,
                        contactNumber = phone,
                        photoUrl = photoUrl,
                        isDemoData = false
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
