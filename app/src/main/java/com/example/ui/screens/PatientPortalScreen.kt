package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppointmentRequestEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.UserEntity
import com.example.data.model.DoctorFullProfile
import com.example.data.model.UserRole
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.components.DoctorCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*

@Composable
fun PatientPortalScreen(
    lang: AppLang,
    currentUser: UserEntity?,
    favoriteDoctors: List<DoctorFullProfile>,
    appointments: List<AppointmentRequestEntity>,
    notifications: List<NotificationEntity>,
    onToggleFavorite: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    onGoLogin: () -> Unit,
    onGoRegisterDoctor: () -> Unit,
    onGoRegisterUser: () -> Unit,
    onOpenRoleDashboard: (UserRole) -> Unit,
    onLogout: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val myAppointments = remember(appointments, currentUser) {
        val uid = currentUser?.id ?: "guest"
        appointments.filter { it.patientUserId == uid || (currentUser == null && it.patientUserId == "guest") }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Account / Portal Header Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (currentUser == null) {
                        Text(
                            tr(lang, "আপনার অ্যাকাউন্ট ও পোর্টাল", "Account & Portals"),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr(
                                lang,
                                "সেভ করা ডাক্তার ও সিরিয়াল অনুরোধ সিঙ্ক রাখতে লগইন বা নিবন্ধন করুন।",
                                "Sign in or register as a Patient, Doctor, or Hospital Manager."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onGoLogin,
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("portal_login_btn")
                            ) {
                                Text(AppStrings.login(lang))
                            }
                            OutlinedButton(
                                onClick = onGoRegisterDoctor,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(AppStrings.registerDoctor(lang), maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onGoRegisterUser, modifier = Modifier.fillMaxWidth()) {
                            Text(tr(lang, "রোগী বা হাসপাতাল ম্যানেজার নিবন্ধন", "Register as Patient or Hospital Manager"))
                        }
                    } else {
                        val roleEnum = UserRole.entries.firstOrNull { it.name == currentUser.role } ?: UserRole.PATIENT
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentUser.name, style = MaterialTheme.typography.titleLarge)
                                Text(currentUser.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                StatusPill(
                                    text = tr(lang, roleEnum.bnLabel, roleEnum.enLabel),
                                    bgColor = MedicalBlueLight,
                                    textColor = MedicalBlueDark
                                )
                            }
                            IconButton(onClick = onLogout) {
                                Icon(Icons.Rounded.Logout, contentDescription = AppStrings.logout(lang), tint = RoseAlert)
                            }
                        }

                        if (roleEnum != UserRole.PATIENT) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onOpenRoleDashboard(roleEnum) },
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_role_dashboard_btn")
                            ) {
                                Icon(Icons.Rounded.Dashboard, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (roleEnum) {
                                        UserRole.DOCTOR -> tr(lang, "ডাক্তার ড্যাশবোর্ড খুলুন", "Open Doctor Dashboard")
                                        UserRole.HOSPITAL_MANAGER -> tr(lang, "হাসপাতাল ড্যাশবোর্ড খুলুন", "Open Hospital Dashboard")
                                        else -> tr(lang, "অ্যাডমিন প্যানেল খুলুন", "Open Admin Panel")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sub-Tabs: Favorites | Appointments | Notifications
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("${AppStrings.saved(lang)} (${favoriteDoctors.size})") }
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text(tr(lang, "সিরিয়াল (${myAppointments.size})", "Requests (${myAppointments.size})")) }
                )
                FilterChip(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    label = { Text(tr(lang, "নোটিশ (${notifications.size})", "Alerts (${notifications.size})")) }
                )
            }
        }

        when (activeTab) {
            0 -> {
                if (favoriteDoctors.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.FavoriteBorder,
                            title = tr(lang, "কোনো ডাক্তার সেভ করা নেই", "No Saved Doctors"),
                            subtitle = tr(lang, "ডাক্তারের কার্ডে হার্ট আইকনে ট্যাপ করে সেভ করুন।", "Tap the heart icon on any doctor card to save.")
                        )
                    }
                } else {
                    items(favoriteDoctors, key = { it.doctor.id }) { doc ->
                        DoctorCard(
                            item = doc,
                            lang = lang,
                            isFavorite = true,
                            onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                            onClick = { onDoctorClick(doc.doctor.id) }
                        )
                    }
                }
            }

            1 -> {
                if (myAppointments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.EventNote,
                            title = tr(lang, "কোনো অ্যাপয়েন্টমেন্ট অনুরোধ নেই", "No Appointment Requests"),
                            subtitle = tr(lang, "ডাক্তারের প্রোফাইল থেকে সিরিয়ালের অনুরোধ পাঠাতে পারেন।", "You can request a serial from a doctor's chamber card.")
                        )
                    }
                } else {
                    items(myAppointments, key = { it.id }) { apt ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
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
                                    Text(apt.patientName, style = MaterialTheme.typography.titleMedium)
                                    val (bg, fg) = when (apt.status) {
                                        "CONFIRMED" -> EmeraldSoft to Color(0xFF065F46)
                                        "REJECTED" -> RoseSoft to RoseAlert
                                        else -> AmberSoft to Color(0xFF92400E)
                                    }
                                    StatusPill(apt.status, bg, fg)
                                }
                                Text("${apt.preferredDay} • ${apt.preferredSlot}", style = MaterialTheme.typography.bodySmall, color = MedicalBlueDark)
                                Text(apt.patientPhone, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                if (apt.notes.isNotBlank()) Text(apt.notes, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
            }

            else -> {
                items(notifications, key = { it.id }) { n ->
                    Surface(
                        onClick = { onMarkNotificationRead(n.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (n.isRead) SurfaceWhite else MedicalBlueLight.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, OutlineSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(tr(lang, n.titleBn, n.titleEn), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(tr(lang, n.messageBn, n.messageEn), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
