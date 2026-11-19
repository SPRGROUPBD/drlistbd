package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.data.model.UserRole
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import java.util.UUID

@Composable
fun AdminHospitalsModule(
    hospitals: List<HospitalEntity>,
    onSave: (HospitalEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var nameBn by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("Dhaka") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Hospital", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(nameBn, { nameBn = it }, label = { Text("Name (Bangla)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameEn, { nameEn = it }, label = { Text("Name (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(district, { district = it }, label = { Text("District") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Contact Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(imageUrl, { imageUrl = it }, label = { Text("Image URL (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (nameEn.isNotBlank()) {
                            onSave(HospitalEntity("hosp_${UUID.randomUUID().toString().take(8)}", "", nameBn.ifBlank { nameEn }, nameEn, district, "", address, phone, imageUrl, "ACTIVE", false))
                            nameBn = ""; nameEn = ""; address = ""; phone = ""; imageUrl = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save Hospital") }
                }
            }
        }
        items(hospitals, key = { it.id }) { h ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(h.nameEn, style = MaterialTheme.typography.titleSmall)
                        Text("${h.district} • ${h.contactNumber} (${h.status})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (h.status != "ACTIVE") {
                        TextButton(onClick = { onSave(h.copy(status = "ACTIVE")) }) { Text("Activate") }
                    }
                    IconButton(onClick = { onDelete(h.id) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert) }
                }
            }
        }
    }
}

@Composable
fun AdminDiagnosticsModule(
    centers: List<DiagnosticCenterEntity>,
    onSave: (DiagnosticCenterEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var nameBn by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("Dhaka") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Diagnostic Center", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(nameBn, { nameBn = it }, label = { Text("Name (Bangla)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameEn, { nameEn = it }, label = { Text("Name (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(district, { district = it }, label = { Text("District") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Contact Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(imageUrl, { imageUrl = it }, label = { Text("Image URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (nameEn.isNotBlank()) {
                            onSave(DiagnosticCenterEntity("diag_${UUID.randomUUID().toString().take(8)}", nameBn.ifBlank { nameEn }, nameEn, district, "", address, phone, imageUrl, "ACTIVE", false))
                            nameBn = ""; nameEn = ""; address = ""; phone = ""; imageUrl = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save Diagnostic Center") }
                }
            }
        }
        items(centers, key = { it.id }) { dc ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dc.nameEn, style = MaterialTheme.typography.titleSmall)
                        Text("${dc.district} • ${dc.contactNumber}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { onDelete(dc.id) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert) }
                }
            }
        }
    }
}

@Composable
fun AdminSpecialtiesModule(
    specialties: List<SpecialtyEntity>,
    onSave: (SpecialtyEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var nameBn by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf("medical") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Medical Specialty", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(nameBn, { nameBn = it }, label = { Text("Bangla Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameEn, { nameEn = it }, label = { Text("English Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(iconKey, { iconKey = it }, label = { Text("Icon Key (heart, brain, eye...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (nameEn.isNotBlank()) {
                            onSave(SpecialtyEntity("sp_${UUID.randomUUID().toString().take(6)}", nameBn.ifBlank { nameEn }, nameEn, iconKey))
                            nameBn = ""; nameEn = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save Specialty") }
                }
            }
        }
        items(specialties, key = { it.id }) { sp ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sp.nameBn, style = MaterialTheme.typography.titleSmall)
                        Text("${sp.nameEn} (${sp.iconKey})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { onDelete(sp.id) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert) }
                }
            }
        }
    }
}

@Composable
fun AdminUsersModule(
    users: List<UserEntity>,
    onUpdateUserRole: (UserEntity, UserRole, Boolean) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(users, key = { it.id }) { u ->
            val currentRole = UserRole.entries.firstOrNull { it.name == u.role } ?: UserRole.PATIENT
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.titleSmall)
                        Text("${u.email} • ${currentRole.enLabel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(checked = u.isActive, onCheckedChange = { onUpdateUserRole(u, currentRole, it) })
                }
            }
        }
    }
}

@Composable
fun AdminReportsModule(
    reports: List<ReportEntity>,
    onResolve: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(reports, key = { it.id }) { r ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.reason, style = MaterialTheme.typography.titleSmall)
                        StatusPill(r.status, if (r.status == "OPEN") RoseSoft else EmeraldSoft, if (r.status == "OPEN") RoseAlert else MedicalTealDark)
                    }
                    Text(r.details, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (r.status == "OPEN") {
                        TextButton(onClick = { onResolve(r.id) }) { Text("Mark Resolved") }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminNotificationsModule(
    notifications: List<NotificationEntity>,
    onSend: (String, String, String, String, String) -> Unit
) {
    var titleBn by remember { mutableStateOf("") }
    var titleEn by remember { mutableStateOf("") }
    var msgBn by remember { mutableStateOf("") }
    var msgEn by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Broadcast Notification", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(titleBn, { titleBn = it }, label = { Text("Title (Bangla)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(titleEn, { titleEn = it }, label = { Text("Title (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(msgBn, { msgBn = it }, label = { Text("Message (Bangla)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(msgEn, { msgEn = it }, label = { Text("Message (English)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (titleEn.isNotBlank() || titleBn.isNotBlank()) {
                            onSend(titleBn.ifBlank { titleEn }, titleEn.ifBlank { titleBn }, msgBn.ifBlank { msgEn }, msgEn.ifBlank { msgBn }, "ALL")
                            titleBn = ""; titleEn = ""; msgBn = ""; msgEn = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Send Notification") }
                }
            }
        }
        items(notifications, key = { it.id }) { n ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(n.titleEn, style = MaterialTheme.typography.titleSmall)
                    Text(n.messageEn, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun AdminRolesRequestsLocationsModule(
    users: List<UserEntity>,
    locations: List<LocationEntity>,
    profileChangeRequests: List<ProfileChangeRequestEntity>,
    hospitalDoctorLinks: List<HospitalDoctorLinkEntity>,
    onUpdateUserRole: (UserEntity, UserRole, Boolean) -> Unit,
    onSaveLocation: (LocationEntity) -> Unit,
    onDeleteLocation: (String) -> Unit,
    onReviewChangeReq: (ProfileChangeRequestEntity, Boolean) -> Unit,
    onUpdateLinkStatus: (String, String) -> Unit
) {
    var div by remember { mutableStateOf("") }
    var dist by remember { mutableStateOf("") }
    var upz by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Profile Change Requests", style = MaterialTheme.typography.titleMedium) }
        items(profileChangeRequests, key = { it.id }) { req ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${req.summary} (${req.status})", style = MaterialTheme.typography.titleSmall)
                    if (req.status == "PENDING") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onReviewChangeReq(req, true) }, modifier = Modifier.weight(1f)) { Text("Approve") }
                            OutlinedButton(onClick = { onReviewChangeReq(req, false) }, modifier = Modifier.weight(1f)) { Text("Reject") }
                        }
                    }
                }
            }
        }

        item { Text("Hospital-Doctor Association Requests", style = MaterialTheme.typography.titleMedium) }
        items(hospitalDoctorLinks, key = { it.id }) { l ->
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${l.hospitalId} <-> ${l.doctorId} (${l.status})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (l.status == "PENDING") {
                        TextButton(onClick = { onUpdateLinkStatus(l.id, "APPROVED") }) { Text("Approve") }
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Location Management (Bangladesh)", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(div, { div = it }, label = { Text("Division") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dist, { dist = it }, label = { Text("District") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(upz, { upz = it }, label = { Text("Upazila / Area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (div.isNotBlank() && dist.isNotBlank()) {
                            onSaveLocation(LocationEntity("loc_${UUID.randomUUID().toString().take(6)}", div, dist, upz, upz))
                            div = ""; dist = ""; upz = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Add Location") }
                }
            }
        }
        items(locations, key = { it.id }) { loc ->
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${loc.division} > ${loc.district} > ${loc.upazila}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { onDeleteLocation(loc.id) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = RoseAlert) }
                }
            }
        }

        item { Text("All User Accounts & Role Assignment", style = MaterialTheme.typography.titleMedium) }
        items(users, key = { "role_${it.id}" }) { u ->
            val currentRole = UserRole.entries.firstOrNull { it.name == u.role } ?: UserRole.PATIENT
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${u.name} (${u.email})", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(UserRole.PATIENT, UserRole.DOCTOR, UserRole.HOSPITAL_MANAGER, UserRole.ADMIN).forEach { r ->
                            FilterChip(
                                selected = currentRole == r,
                                onClick = { onUpdateUserRole(u, r, u.isActive) },
                                label = { Text(r.name.take(6)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogsModule(logs: List<AuditLogEntity>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(logs, key = { it.id }) { log ->
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${log.action} • ${log.targetType} (${log.actorRole})", style = MaterialTheme.typography.titleSmall, color = MedicalBlueDark)
                    Text(log.details, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun AdminSettingsModule(
    settings: SiteSettingsEntity,
    onSaveSettings: (SiteSettingsEntity) -> Unit,
    onClearDemoData: () -> Unit,
    onExportDoctorsCsv: () -> Unit,
    onExportHospitalsCsv: () -> Unit
) {
    var title by remember(settings.siteTitle) { mutableStateOf(settings.siteTitle) }
    var taglineBn by remember(settings.taglineBn) { mutableStateOf(settings.taglineBn) }
    var email by remember(settings.contactEmail) { mutableStateOf(settings.contactEmail) }
    var phone by remember(settings.contactPhone) { mutableStateOf(settings.contactPhone) }
    var allowBooking by remember(settings.allowOnlineBooking) { mutableStateOf(settings.allowOnlineBooking) }
    var showDemo by remember(settings.showDemoData) { mutableStateOf(settings.showDemoData) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Website Settings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(title, { title = it }, label = { Text("Site Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(taglineBn, { taglineBn = it }, label = { Text("Tagline (Bangla)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text("Support Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Support Phone") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow Online Appointment Requests")
                        Switch(checked = allowBooking, onCheckedChange = { allowBooking = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Show DEMO Sample Entries")
                        Switch(checked = showDemo, onCheckedChange = { showDemo = it })
                    }
                    Button(
                        onClick = {
                            onSaveSettings(settings.copy(siteTitle = title, taglineBn = taglineBn, contactEmail = email, contactPhone = phone, allowOnlineBooking = allowBooking, showDemoData = showDemo))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Settings") }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data Export & Demo Cleanup", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onExportDoctorsCsv, modifier = Modifier.fillMaxWidth()) { Text("Export Doctors List (CSV)") }
                    OutlinedButton(onClick = onExportHospitalsCsv, modifier = Modifier.fillMaxWidth()) { Text("Export Hospitals List (CSV)") }
                    Button(
                        onClick = onClearDemoData,
                        colors = ButtonDefaults.buttonColors(containerColor = RoseAlert),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete All DEMO Data") }
                }
            }
        }
    }
}
