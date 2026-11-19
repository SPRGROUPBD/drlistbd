package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@Composable
fun HospitalDashboardScreen(
    lang: AppLang,
    currentUser: UserEntity,
    hospitals: List<HospitalEntity>,
    doctors: List<DoctorFullProfile>,
    links: List<HospitalDoctorLinkEntity>,
    onSaveHospital: (HospitalEntity) -> Unit,
    onRequestDoctorLink: (hospitalId: String, doctorId: String) -> Unit,
    onAddDoctorChamber: (ChamberEntity) -> Unit,
    onAddSchedule: (String, String, DayOfWeekBd, String, String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val myHospital = hospitals.firstOrNull { it.managerUserId == currentUser.id }
        ?: HospitalEntity(
            id = "hosp_${currentUser.id}",
            managerUserId = currentUser.id,
            nameBn = currentUser.name,
            nameEn = currentUser.name,
            district = currentUser.district.ifBlank { "Dhaka" },
            address = "",
            contactNumber = currentUser.phone,
            status = "PENDING",
            isDemoData = false
        )

    var nameBn by remember(myHospital.id) { mutableStateOf(myHospital.nameBn) }
    var nameEn by remember(myHospital.id) { mutableStateOf(myHospital.nameEn) }
    var district by remember(myHospital.id) { mutableStateOf(myHospital.district) }
    var upazila by remember(myHospital.id) { mutableStateOf(myHospital.upazila) }
    var address by remember(myHospital.id) { mutableStateOf(myHospital.address) }
    var contact by remember(myHospital.id) { mutableStateOf(myHospital.contactNumber) }
    var imageUrl by remember(myHospital.id) { mutableStateOf(myHospital.imageUrl) }

    val myLinks = links.filter { it.hospitalId == myHospital.id }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr(lang, "হাসপাতাল ড্যাশবোর্ড", "Hospital Manager Dashboard"), style = MaterialTheme.typography.titleLarge, color = MedicalBlueDark)
                        StatusPill(myHospital.status, MedicalBlueLight, MedicalBlueDark)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExternalImageAvatar(imageUrl, nameEn, Icons.Rounded.LocalHospital, size = 56.dp)
                        OutlinedTextField(imageUrl, { imageUrl = it }, label = { Text("Hospital Image URL") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(nameBn, { nameBn = it }, label = { Text("Hospital Name (Bangla)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameEn, { nameEn = it }, label = { Text("Hospital Name (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(district, { district = it }, label = { Text("District") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(upazila, { upazila = it }, label = { Text("Upazila") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(contact, { contact = it }, label = { Text("Contact Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onSaveHospital(
                                myHospital.copy(
                                    nameBn = nameBn,
                                    nameEn = nameEn,
                                    district = district,
                                    upazila = upazila,
                                    address = address,
                                    contactNumber = contact,
                                    imageUrl = imageUrl,
                                    status = "PENDING"
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(lang, "তথ্য সংরক্ষণ ও অনুমোদনের অনুরোধ", "Save & Request Admin Approval"))
                    }
                }
            }
        }

        item {
            Text(tr(lang, "ডাক্তার সংযুক্তি অনুরোধ", "Associate Doctors with Hospital"), style = MaterialTheme.typography.titleMedium)
        }

        items(doctors.take(10), key = { it.doctor.id }) { d ->
            val existingLink = myLinks.firstOrNull { it.doctorId == d.doctor.id }
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, border = BorderStroke(1.dp, OutlineSoft)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr(lang, d.doctor.fullNameBn, d.doctor.fullNameEn), style = MaterialTheme.typography.titleSmall)
                        Text(d.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (existingLink == null) {
                        OutlinedButton(onClick = { onRequestDoctorLink(myHospital.id, d.doctor.id) }) {
                            Text(tr(lang, "অনুরোধ", "Link"))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusPill(existingLink.status, MedicalTealLight, MedicalTealDark)
                            if (existingLink.status == "APPROVED") {
                                TextButton(onClick = {
                                    val chId = "ch_${UUID.randomUUID().toString().take(8)}"
                                    onAddDoctorChamber(
                                        ChamberEntity(
                                            id = chId,
                                            doctorId = d.doctor.id,
                                            facilityName = nameEn,
                                            district = district,
                                            upazila = upazila,
                                            fullAddress = address,
                                            serialPhone = contact,
                                            isActive = true
                                        )
                                    )
                                    onAddSchedule(chId, d.doctor.id, DayOfWeekBd.SATURDAY, "05:00 PM", "08:00 PM", "Hospital Slot")
                                }) { Text("+ Chamber") }
                            }
                        }
                    }
                }
            }
        }
    }
}
