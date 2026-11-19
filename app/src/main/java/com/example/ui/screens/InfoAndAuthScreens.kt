package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.SpecialtyEntity
import com.example.data.local.UserEntity
import com.example.data.model.UserRole
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.theme.*

@Composable
fun StaticInfoScreen(
    pageKey: String,
    lang: AppLang,
    settings: SiteSettingsEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val (title, body) = when (pageKey) {
        "about" -> AppStrings.aboutUs(lang) to tr(
            lang,
            "Doctor Directory Bangladesh বাংলাদেশের রোগীদের জন্য ডাক্তারদের সঠিক চেম্বার অবস্থান, সাপ্তাহিক সময়সূচী, শিক্ষাগত যোগ্যতা ও কর্মস্থলের তথ্য এক জায়গায় পৌঁছে দেওয়ার একটি নির্ভরযোগ্য প্ল্যাটফর্ম।\n\nআমাদের লক্ষ্য হলো রোগী যেন অযথা ভোগান্তি ছাড়াই সঠিক বিশেষজ্ঞ ডাক্তারের চেম্বার ও সিরিয়াল নম্বর খুঁজে পান।",
            "Doctor Directory Bangladesh is a dedicated healthcare directory designed to help patients across Bangladesh discover accurate doctor chamber locations, weekly schedules, qualifications, and current workplaces in one place."
        )
        "contact" -> AppStrings.contactUs(lang) to tr(
            lang,
            "সহায়তা বা তথ্য সংশোধনের জন্য যোগাযোগ করুন:\n\nইমেইল: ${settings.contactEmail}\nফোন: ${settings.contactPhone}\n\nযেকোনো ভুল চেম্বার তথ্য সরাসরি ডাক্তারের প্রোফাইলের 'ভুল তথ্য রিপোর্ট' বাটনের মাধ্যমে জানাতে পারেন।",
            "Contact Support:\n\nEmail: ${settings.contactEmail}\nPhone: ${settings.contactPhone}\n\nYou can also flag outdated chamber info directly using the Report button on any doctor profile."
        )
        "privacy" -> AppStrings.privacyPolicy(lang) to tr(
            lang,
            "গোপনীয়তা নীতি:\n\n১. আমরা শুধুমাত্র ডিরেক্টরি পরিচালনা, অ্যাকাউন্ট নিরাপত্তা এবং সিরিয়াল অনুরোধ সমন্বয়ের জন্য প্রয়োজনীয় তথ্য সংরক্ষণ করি।\n২. রোগী ও ডাক্তারের ব্যক্তিগত যোগাযোগের তথ্য অননুমোদিত তৃতীয় পক্ষের সাথে শেয়ার করা হয় না।\n৩. কোনো মেডিকেল পেমেন্ট বা কনসালটেশন ফি তথ্য এই প্ল্যাটফর্মে সংগ্রহ করা হয় না।",
            "Privacy Policy:\n\n1. We collect only essential account, chamber, and serial request details needed to operate the directory.\n2. Personal contact records are protected by role-based access rules.\n3. No consultation fees or financial payment data are collected on this platform."
        )
        else -> AppStrings.termsConditions(lang) to tr(
            lang,
            "শর্তাবলী:\n\n১. শুধুমাত্র নিবন্ধিত ও অনুমোদিত চিকিৎসকগণ চেম্বার তথ্য প্রকাশ করতে পারবেন।\n২. BMDC ভেরিফিকেশন ব্যাজ কেবল অ্যাডমিন যাচাইয়ের পর প্রদান করা হয়।\n৩. চেম্বারে যাওয়ার আগে প্রদর্শিত সিরিয়াল নম্বরে ফোন করে সময়সূচী নিশ্চিত করার পরামর্শ দেওয়া হলো।",
            "Terms & Conditions:\n\n1. Doctors must submit authentic BMDC registration credentials.\n2. BMDC verification status is granted solely after administrator review.\n3. Patients are advised to call the chamber serial number before visiting."
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MedicalBlueDark, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
        }
    }
}

@Composable
fun LoginScreen(
    lang: AppLang,
    onLogin: (emailOrId: String, password: String) -> Unit,
    onResetPassword: (email: String, newPassword: String, onDone: () -> Unit) -> Unit,
    onGoRegisterDoctor: () -> Unit,
    onGoRegisterUser: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    AppStrings.login(lang),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MedicalBlueDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    tr(
                        lang,
                        "রোগী, ডাক্তার অথবা হাসপাতাল ম্যানেজার অ্যাকাউন্টে সাইন ইন করুন।",
                        "Sign in to your Patient, Doctor, or Hospital Manager account."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(tr(lang, "ইমেইল / ইউজার আইডি", "Email / User ID")) },
                    leadingIcon = { Icon(Icons.Rounded.Email, null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(tr(lang, "পাসওয়ার্ড", "Password")) },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showResetDialog = true }) {
                        Text(tr(lang, "পাসওয়ার্ড ভুলে গেছেন?", "Reset Password"))
                    }
                }

                Button(
                    onClick = { onLogin(email, password) },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button")
                ) {
                    Text(AppStrings.login(lang))
                }

                HorizontalDivider(color = SurfaceMuted)

                OutlinedButton(
                    onClick = onGoRegisterDoctor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(AppStrings.registerDoctor(lang))
                }
                OutlinedButton(
                    onClick = onGoRegisterUser,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr(lang, "রোগী / হাসপাতাল নিবন্ধন", "Patient / Hospital Registration"))
                }
            }
        }
    }

    if (showResetDialog) {
        var resetEmail by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(tr(lang, "পাসওয়ার্ড রিসেট", "Reset Password")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(tr(lang, "নিবন্ধিত ইমেইল", "Registered Email")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text(tr(lang, "নতুন পাসওয়ার্ড", "New Password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onResetPassword(resetEmail, newPass) { showResetDialog = false } }) {
                    Text(tr(lang, "আপডেট", "Update"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(tr(lang, "বাতিল", "Cancel")) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorRegistrationScreen(
    lang: AppLang,
    specialties: List<SpecialtyEntity>,
    onRegisterDoctor: (
        nameBn: String, nameEn: String, email: String, password: String,
        phone: String, bmdc: String, specialtyId: String, hospital: String, designation: String
    ) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var nameBn by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var bmdc by remember { mutableStateOf("") }
    var selectedSpecId by remember { mutableStateOf(specialties.firstOrNull()?.id ?: "") }
    var hospital by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var specExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(AppStrings.registerDoctor(lang), style = MaterialTheme.typography.headlineSmall, color = MedicalBlueDark)
                Text(
                    tr(
                        lang,
                        "নিবন্ধনের পর অ্যাডমিন আপনার BMDC নম্বর যাচাই করে প্রোফাইল অনুমোদন করবেন।",
                        "After registration, an administrator reviews and verifies your BMDC registration."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(nameBn, { nameBn = it }, label = { Text(tr(lang, "পুরো নাম (বাংলা)", "Full Name (Bangla)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text(tr(lang, "পুরো নাম (ইংরেজি)", "Full Name (English)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bmdc, { bmdc = it }, label = { Text("BMDC Registration Number *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                ExposedDropdownMenuBox(expanded = specExpanded, onExpandedChange = { specExpanded = it }) {
                    val currentSpec = specialties.firstOrNull { it.id == selectedSpecId }
                    OutlinedTextField(
                        value = currentSpec?.let { tr(lang, it.nameBn, it.nameEn) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(tr(lang, "বিশেষজ্ঞতা", "Medical Specialty")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = specExpanded, onDismissRequest = { specExpanded = false }) {
                        specialties.forEach { sp ->
                            DropdownMenuItem(
                                text = { Text(tr(lang, sp.nameBn, sp.nameEn)) },
                                onClick = {
                                    selectedSpecId = sp.id
                                    specExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(hospital, { hospital = it }, label = { Text(tr(lang, "বর্তমান হাসপাতাল", "Current Hospital")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(designation, { designation = it }, label = { Text(tr(lang, "পদবি", "Current Designation")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text(tr(lang, "ফোন নম্বর", "Phone Number")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text(tr(lang, "ইমেইল *", "Email *")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it },
                    label = { Text(tr(lang, "পাসওয়ার্ড (কমপক্ষে ৪ অক্ষর) *", "Password (4+ chars) *")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onRegisterDoctor(nameBn, nameEn, email, password, phone, bmdc, selectedSpecId, hospital, designation)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(tr(lang, "নিবন্ধন জমা দিন", "Submit Doctor Registration"))
                }
            }
        }
    }
}

@Composable
fun UserRegistrationScreen(
    lang: AppLang,
    onRegister: (name: String, email: String, password: String, phone: String, district: String, role: UserRole) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var role by remember { mutableStateOf(UserRole.PATIENT) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("Dhaka") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr(lang, "অ্যাকাউন্ট নিবন্ধন", "Account Registration"), style = MaterialTheme.typography.headlineSmall, color = MedicalBlueDark)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == UserRole.PATIENT,
                        onClick = { role = UserRole.PATIENT },
                        label = { Text(tr(lang, "রোগী", "Patient")) }
                    )
                    FilterChip(
                        selected = role == UserRole.HOSPITAL_MANAGER,
                        onClick = { role = UserRole.HOSPITAL_MANAGER },
                        label = { Text(tr(lang, "হাসপাতাল ম্যানেজার", "Hospital Manager")) }
                    )
                }

                OutlinedTextField(name, { name = it }, label = { Text(tr(lang, "নাম *", "Name *")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text(tr(lang, "ইমেইল *", "Email *")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text(tr(lang, "মোবাইল নম্বর", "Phone")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(district, { district = it }, label = { Text(tr(lang, "জেলা", "District")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it },
                    label = { Text(tr(lang, "পাসওয়ার্ড *", "Password *")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onRegister(name, email, password, phone, district, role) },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(tr(lang, "নিবন্ধন করুন", "Register"))
                }
            }
        }
    }
}
