package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AdvertiserSession
import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.data.model.DoctorFullProfile
import com.example.data.remote.AdvertiserPortalDashboard
import com.example.data.remote.AdvertisingRemoteService
import com.example.data.remote.RegisteredInstitutionClaims
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.ads.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Production Advertiser Dashboard (Sections 2, 3, 4A, 8, 9, 11, 12):
 * - Independent Advertiser registration, login, logout, and profile update
 * - Active-only packages created by Admin (Home Slider, Native 300x100, Bottom Banner 300x50, App Open Popup 300x200)
 * - Special Home Slider existing Doctor/Hospital/Diagnostic picker (no duplicate records)
 * - Full Payment submission against Admin active receiving methods with duplicate TRX ID check
 * - Campaign & Payment status tracking, impressions/clicks, and manual renewal
 */
@Composable
fun AdvertiserPortalScreen(
    lang: AppLang,
    session: AdvertiserSession,
    doctors: List<DoctorFullProfile> = emptyList(),
    hospitals: List<HospitalEntity> = emptyList(),
    diagnosticCenters: List<DiagnosticCenterEntity> = emptyList(),
    cachedPackages: List<AdPackageItem> = emptyList(),
    cachedPaymentMethods: List<AdPaymentMethod> = emptyList(),
    isOnline: Boolean,
    nowMs: Long,
    onLoginSuccess: (AdvertiserSession) -> Unit,
    onLogout: () -> Unit,
    onShowMessage: (String) -> Unit,
    onRefreshGlobalAds: () -> Unit = {},
    onDoctorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<AdvertiserPortalDashboard?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Dashboard, 1=Create Ad, 2=Payments, 3=Profile
    var preselectedPackage by remember { mutableStateOf<AdPackageItem?>(null) }
    var renewingCampaign by remember { mutableStateOf<AdCampaignItem?>(null) }

    fun reloadDashboard() {
        if (!session.isLoggedIn) return
        scope.launch {
            isLoading = true
            AdvertisingRemoteService.fetchAdvertiserDashboard(session.id)
                .onSuccess { dashboard = it }
                .onFailure { onShowMessage(it.message ?: "Failed to load dashboard") }
            isLoading = false
        }
    }

    LaunchedEffect(session.id, isOnline) {
        if (session.isLoggedIn && isOnline) {
            reloadDashboard()
        }
    }

    if (!session.isLoggedIn) {
        AdvertiserAuthView(
            lang = lang,
            hospitals = hospitals,
            diagnosticCenters = diagnosticCenters,
            isOnline = isOnline,
            onAuthSuccess = { newSession ->
                onLoginSuccess(newSession)
                onShowMessage(tr(lang, "অ্যাডভার্টাইজার অ্যাকাউন্টে স্বাগতম!", "Welcome to Advertiser Portal!"))
            },
            onError = onShowMessage,
            modifier = modifier
        )
        return
    }

    val activePackages = remember(dashboard?.activePackages, cachedPackages) {
        (dashboard?.activePackages ?: cachedPackages).filter { it.isActive }
    }
    val activeMethods = remember(dashboard?.activePaymentMethods, cachedPaymentMethods) {
        (dashboard?.activePaymentMethods ?: cachedPaymentMethods).filter { it.isActive }
    }
    val myCampaigns = dashboard?.myCampaigns.orEmpty()
    val myPayments = dashboard?.myPayments.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .testTag("advertiser_portal_screen")
    ) {
        Surface(color = MedicalBlueDark, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(MedicalBlueDark, MedicalTealDark)))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.businessName.ifBlank { session.ownerName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.ownerName} • ${session.mobile}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.86f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { reloadDashboard() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            dashboard = null
                            onLogout()
                        },
                        modifier = Modifier.testTag("adv_logout_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                }
            }
        }

        ScrollableTabRow(selectedTabIndex = activeTab, containerColor = SurfaceWhite, edgePadding = 8.dp) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0; renewingCampaign = null },
                text = { Text(tr(lang, "ড্যাশবোর্ড", "Dashboard"), maxLines = 1) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Text(
                        if (renewingCampaign != null) tr(lang, "রিনিউ", "Renew")
                        else tr(lang, "বিজ্ঞাপন তৈরি", "Create Ad"),
                        maxLines = 1
                    )
                }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2; renewingCampaign = null },
                text = { Text(tr(lang, "পেমেন্ট", "Payments"), maxLines = 1) }
            )
            Tab(
                selected = activeTab == 3,
                onClick = { activeTab = 3; renewingCampaign = null },
                text = { Text(tr(lang, "প্রোফাইল", "Settings"), maxLines = 1) }
            )
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MedicalTeal)
        }

        when (activeTab) {
            0 -> AdvertiserOverviewTab(
                lang = lang,
                myCampaigns = myCampaigns,
                myPayments = myPayments,
                activePackages = activePackages,
                doctors = doctors,
                hospitals = hospitals,
                diagnostics = diagnosticCenters,
                nowMs = nowMs,
                onSelectPackageToCreate = { pkg ->
                    renewingCampaign = null
                    preselectedPackage = pkg
                    activeTab = 1
                },
                onRenewCampaign = { camp ->
                    renewingCampaign = camp
                    preselectedPackage = activePackages.firstOrNull { it.id == camp.packageId } ?: activePackages.firstOrNull()
                    activeTab = 1
                },
                onDoctorClick = onDoctorClick
            )
            1 -> CreateCampaignAndPaymentTab(
                lang = lang,
                session = session,
                activePackages = activePackages,
                activePaymentMethods = activeMethods,
                doctors = doctors,
                hospitals = hospitals,
                diagnosticCenters = diagnosticCenters,
                initialPackage = preselectedPackage,
                renewingCampaign = renewingCampaign,
                isOnline = isOnline,
                nowMs = nowMs,
                onSubmitted = {
                    renewingCampaign = null
                    preselectedPackage = null
                    activeTab = 0
                    reloadDashboard()
                    onRefreshGlobalAds()
                    onShowMessage(
                        tr(
                            lang,
                            "বিজ্ঞাপন ও পেমেন্ট জমা হয়েছে! অ্যাডমিন পেমেন্ট যাচাই ও অনুমোদনের পর এটি চালু হবে।",
                            "Ad & payment submitted! It goes live after Admin verifies payment and approves the campaign."
                        )
                    )
                },
                onError = onShowMessage,
                onDoctorClick = onDoctorClick
            )
            2 -> AdvertiserPaymentsHistoryTab(lang = lang, payments = myPayments)
            3 -> AdvertiserProfileSettingsTab(
                lang = lang,
                session = session,
                isOnline = isOnline,
                onProfileUpdated = onLoginSuccess,
                onShowMessage = onShowMessage
            )
        }
    }
}

private data class SavedInstitutionOption(
    val id: String,
    val type: String, // HOSPITAL | DIAGNOSTIC
    val nameEn: String,
    val nameBn: String,
    val district: String,
    val address: String
) {
    val displayTitle: String get() = nameEn.ifBlank { nameBn }
    val normalizedKey: String get() = displayTitle.trim().lowercase().replace(Regex("\\s+"), " ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvertiserAuthView(
    lang: AppLang,
    hospitals: List<HospitalEntity>,
    diagnosticCenters: List<DiagnosticCenterEntity>,
    isOnline: Boolean,
    onAuthSuccess: (AdvertiserSession) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var businessName by remember { mutableStateOf("") }
    var selectedInstitutionId by remember { mutableStateOf("") }
    var selectedInstitutionType by remember { mutableStateOf("") }
    var useCustomInstitution by remember { mutableStateOf(false) }
    var institutionDropdownExpanded by remember { mutableStateOf(false) }
    var claimedInstitutions by remember { mutableStateOf(RegisteredInstitutionClaims()) }

    var ownerName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Healthcare") }
    var websiteUrl by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val savedInstitutions = remember(hospitals, diagnosticCenters) {
        val list = mutableListOf<SavedInstitutionOption>()
        hospitals.forEach { h ->
            list.add(
                SavedInstitutionOption(
                    id = h.id,
                    type = "HOSPITAL",
                    nameEn = h.nameEn.removePrefix("[DEMO] ").trim(),
                    nameBn = h.nameBn.removePrefix("[DEMO] ").trim(),
                    district = h.district,
                    address = h.address
                )
            )
        }
        diagnosticCenters.forEach { d ->
            list.add(
                SavedInstitutionOption(
                    id = d.id,
                    type = "DIAGNOSTIC",
                    nameEn = d.nameEn.removePrefix("[DEMO] ").trim(),
                    nameBn = d.nameBn.removePrefix("[DEMO] ").trim(),
                    district = d.district,
                    address = d.address
                )
            )
        }
        list.sortedBy { it.displayTitle.lowercase() }
    }

    LaunchedEffect(isRegister, isOnline) {
        if (isRegister && isOnline) {
            AdvertisingRemoteService.fetchRegisteredInstitutionClaims()
                .onSuccess { claimedInstitutions = it }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(BackgroundIce),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MedicalBlueLight, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Storefront, contentDescription = null, tint = MedicalBlueDark)
                            }
                        }
                        Column {
                            Text(
                                text = tr(lang, "অ্যাডভার্টাইজার অ্যাকাউন্ট", "Advertiser Portal"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = tr(lang, "লগইন বা নতুন অ্যাকাউন্ট তৈরি করে বিজ্ঞাপন পরিচালনা করুন", "Sign in or register to submit & track advertisements"),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isRegister,
                            onClick = { isRegister = false },
                            label = { Text(tr(lang, "লগইন", "Login")) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isRegister,
                            onClick = { isRegister = true },
                            label = { Text(tr(lang, "রেজিস্ট্রেশন", "Register")) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isRegister) {
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text(tr(lang, "পূর্ণ নাম *", "Advertiser Name *")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Saved Institution Dropdown (prevents duplicate claims on the same institution)
                        ExposedDropdownMenuBox(
                            expanded = institutionDropdownExpanded,
                            onExpandedChange = { institutionDropdownExpanded = !institutionDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedLabel = when {
                                useCustomInstitution -> tr(lang, "অন্যান্য / নতুন প্রতিষ্ঠান", "Other / Custom Business")
                                selectedInstitutionId.isNotBlank() -> businessName
                                else -> ""
                            }
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(tr(lang, "সংরক্ষিত প্রতিষ্ঠান নির্বাচন করুন *", "Select Saved Institution *")) },
                                placeholder = { Text(tr(lang, "হাসপাতাল বা ডায়াগনস্টিক বাছুন...", "Choose saved Hospital or Diagnostic...")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = institutionDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("adv_saved_institution_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = institutionDropdownExpanded,
                                onDismissRequest = { institutionDropdownExpanded = false }
                            ) {
                                savedInstitutions.forEach { inst ->
                                    val isClaimed = inst.id in claimedInstitutions.institutionIds ||
                                        inst.normalizedKey in claimedInstitutions.normalizedBusinessNames ||
                                        inst.nameBn.trim().lowercase() in claimedInstitutions.normalizedBusinessNames
                                    val typeTag = if (inst.type == "HOSPITAL") {
                                        tr(lang, "হাসপাতাল", "Hospital")
                                    } else {
                                        tr(lang, "ডায়াগনস্টিক", "Diagnostic")
                                    }
                                    DropdownMenuItem(
                                        enabled = !isClaimed,
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = tr(lang, inst.nameBn.ifBlank { inst.nameEn }, inst.displayTitle),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isClaimed) TextMuted else TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isClaimed) {
                                                        tr(lang, "$typeTag • ইতিমধ্যে নিবন্ধিত (অন্য অ্যাকাউন্টে যুক্ত)", "$typeTag • Already Registered")
                                                    } else {
                                                        "$typeTag • ${inst.district}"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isClaimed) RoseAlert else MedicalTealDark
                                                )
                                            }
                                        },
                                        onClick = {
                                            useCustomInstitution = false
                                            selectedInstitutionId = inst.id
                                            selectedInstitutionType = inst.type
                                            businessName = inst.displayTitle
                                            category = if (inst.type == "HOSPITAL") "Hospital" else "Diagnostic Center"
                                            if (address.isBlank() && inst.address.isNotBlank()) {
                                                address = listOf(inst.address, inst.district).filter { it.isNotBlank() }.joinToString(", ")
                                            }
                                            institutionDropdownExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "+ অন্যান্য / নতুন প্রতিষ্ঠানের নাম লিখুন", "+ Other / Custom Institution"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MedicalBlueDark
                                        )
                                    },
                                    onClick = {
                                        useCustomInstitution = true
                                        selectedInstitutionId = ""
                                        selectedInstitutionType = "CUSTOM"
                                        businessName = ""
                                        institutionDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        if (useCustomInstitution) {
                            OutlinedTextField(
                                value = businessName,
                                onValueChange = { businessName = it },
                                label = { Text(tr(lang, "ব্যবসা / প্রতিষ্ঠানের নাম *", "Business / Organization Name *")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (selectedInstitutionId.isNotBlank()) {
                            Text(
                                text = tr(
                                    lang,
                                    "নির্বাচিত প্রতিষ্ঠান: $businessName (একটি প্রতিষ্ঠান কেবল একটি অ্যাকাউন্টেই যুক্ত থাকবে)",
                                    "Linked Institution: $businessName (exclusively reserved for your account)"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAvailable,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text(if (isRegister) tr(lang, "মোবাইল নম্বর *", "Mobile Number *") else tr(lang, "মোবাইল নম্বর বা ইমেইল *", "Mobile or Email *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (isRegister) KeyboardType.Phone else KeyboardType.Text),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isRegister) {
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(tr(lang, "ইমেইল (ঐচ্ছিক)", "Email (Optional)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(tr(lang, "ঠিকানা *", "Address *")) }, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(tr(lang, "পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) *", "Password (min 6 chars) *")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = !busy && isOnline,
                        onClick = {
                            scope.launch {
                                busy = true
                                val res = if (isRegister) {
                                    AdvertisingRemoteService.registerAdvertiser(
                                        advertiserName = ownerName,
                                        businessName = businessName,
                                        mobile = mobile,
                                        email = email,
                                        address = address,
                                        category = category,
                                        websiteUrl = websiteUrl,
                                        password = password,
                                        institutionId = selectedInstitutionId,
                                        institutionType = selectedInstitutionType
                                    )
                                } else {
                                    AdvertisingRemoteService.loginAdvertiser(mobile, password)
                                }
                                busy = false
                                res.onSuccess(onAuthSuccess).onFailure { onError(it.message ?: "Authentication failed") }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("adv_auth_submit_btn")
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (isRegister) tr(lang, "অ্যাকাউন্ট তৈরি করুন", "Create Account") else tr(lang, "লগইন করুন", "Sign In"))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCampaignAndPaymentTab(
    lang: AppLang,
    session: AdvertiserSession,
    activePackages: List<AdPackageItem>,
    activePaymentMethods: List<AdPaymentMethod>,
    doctors: List<DoctorFullProfile>,
    hospitals: List<HospitalEntity>,
    diagnosticCenters: List<DiagnosticCenterEntity>,
    initialPackage: AdPackageItem?,
    renewingCampaign: AdCampaignItem?,
    isOnline: Boolean,
    nowMs: Long,
    onSubmitted: () -> Unit,
    onError: (String) -> Unit,
    onDoctorClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPkg by remember(initialPackage, activePackages) {
        mutableStateOf(initialPackage ?: activePackages.firstOrNull())
    }
    val pkg = selectedPkg
    var selectedPlacement by remember(pkg?.id) {
        mutableStateOf(pkg?.placement ?: AdPlacements.DOCTOR_LIST)
    }
    var durationInput by remember(pkg?.id) {
        mutableStateOf((pkg?.durationDays ?: 30).toString())
    }
    var title by remember(renewingCampaign?.id) { mutableStateOf(renewingCampaign?.title.orEmpty()) }
    var description by remember(renewingCampaign?.id) { mutableStateOf(renewingCampaign?.description.orEmpty()) }
    var imageUrl by remember(renewingCampaign?.id) { mutableStateOf(renewingCampaign?.imageUrl.orEmpty()) }
    var destinationUrl by remember(renewingCampaign?.id) {
        mutableStateOf(renewingCampaign?.destinationUrl ?: session.websiteUrl)
    }

    // Special Home Slider multi-select targets (institution doctors + advertiser's own institution)
    var selectedTargets by remember(renewingCampaign?.id) {
        val initial = when {
            renewingCampaign != null && renewingCampaign.promotedTargets.isNotEmpty() -> renewingCampaign.promotedTargets
            renewingCampaign != null && renewingCampaign.targetRecordId.isNotBlank() -> listOf(
                PromotedTargetRef(
                    recordType = renewingCampaign.targetRecordType,
                    recordId = renewingCampaign.targetRecordId,
                    title = renewingCampaign.targetRecordTitle,
                    subtitle = renewingCampaign.targetRecordSubtitle,
                    photoUrl = renewingCampaign.targetRecordPhotoUrl
                )
            )
            else -> emptyList()
        }
        mutableStateOf(initial)
    }

    // Payment details
    var selectedMethod by remember(activePaymentMethods) { mutableStateOf(activePaymentMethods.firstOrNull()) }
    var senderMobile by remember(session.mobile) { mutableStateOf(session.mobile) }
    var trxId by remember { mutableStateOf("") }
    var amountPaid by remember(pkg?.id) { mutableStateOf(pkg?.price?.toInt()?.toString().orEmpty()) }
    var paymentDate by remember { mutableStateOf(SponsorCampaignSelector.todayDateKey(nowMs)) }
    var screenshotUrl by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            encodeUriToDataUrl(context, uri)?.let { dataUri ->
                imageUrl = dataUri
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activePackages.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(lang, "অ্যাডমিন এখনও কোনো সক্রিয় প্যাকেজ প্রকাশ করেননি।", "No active packages available from Admin yet."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
            return@LazyColumn
        }

        // Step 1: Select Active Package (immutable pricing/duration/type)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tr(lang, "১. সক্রিয় প্যাকেজ নির্বাচন করুন", "1. Select Active Package"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    activePackages.forEach { itemPkg ->
                        val chosen = itemPkg.id == selectedPkg?.id
                        val placementText = AdPlacements.displayLabel(itemPkg.placement, lang == AppLang.BN)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (chosen) MedicalBlueLight else BackgroundIce,
                            border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) MedicalBlue else OutlineSoft),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPkg = itemPkg
                                    selectedPlacement = itemPkg.placement
                                    durationInput = itemPkg.durationDays.toString()
                                    amountPaid = itemPkg.price.toInt().toString()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(itemPkg.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            text = "${adTypeLabel(itemPkg.adType, lang)} • ${itemPkg.durationDays} ${tr(lang, "দিন", "Days")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MedicalTealDark
                                        )
                                    }
                                    Text("৳${itemPkg.price.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MedicalBlueDark)
                                }
                                Text(
                                    text = tr(lang, "প্লেসমেন্ট: $placementText", "Placement: $placementText"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MedicalBlueDark
                                )
                                if (itemPkg.description.isNotBlank()) {
                                    Text(
                                        text = itemPkg.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    pkg?.let { activeSel ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MedicalTealLight.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = tr(lang, "নির্বাচিত প্যাকেজ সারসংক্ষেপ", "Selected Package Summary"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MedicalTealDark
                                )
                                Text(
                                    text = "${activeSel.name} • ${adTypeLabel(activeSel.adType, lang)} • ৳${activeSel.price.toInt()} / ${activeSel.durationDays} ${tr(lang, "দিন", "days")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = tr(
                                        lang,
                                        "প্লেসমেন্ট: ${AdPlacements.displayLabel(selectedPlacement, true)}",
                                        "Placement: ${AdPlacements.displayLabel(selectedPlacement, false)}"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pkg != null) {
            // Step 2: Advertisement Content
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = tr(lang, "২. বিজ্ঞাপনের তথ্য দিন", "2. Advertisement Details"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        if (AdPackageTypes.normalize(pkg.adType) == AdPackageTypes.NATIVE_300x100) {
                            Text(tr(lang, "নেটিভ প্লেসমেন্ট নির্বাচন করুন:", "Native Placement:"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    AdPlacements.ALL_NATIVE_LISTS to tr(lang, "সব নেটিভ তালিকা (ডাক্তার, হাসপাতাল, ডায়াগনস্টিক ও জনপ্রিয় ডাক্তার)", "All Native Lists (Doctors, Hospitals, Diagnostics & Popular Doctors)"),
                                    AdPlacements.DOCTOR_LIST to tr(lang, "ডাক্তার তালিকা (Doctor List)", "Doctor List"),
                                    AdPlacements.HOSPITAL_LIST to tr(lang, "হাসপাতাল তালিকা (Hospital List)", "Hospital List"),
                                    AdPlacements.DIAGNOSTIC_LIST to tr(lang, "ডায়াগনস্টিক তালিকা (Diagnostic List)", "Diagnostic Center List"),
                                    AdPlacements.FAVORITE_LIST to tr(lang, "প্রিয় তালিকা (Favorite List)", "Favorite List")
                                ).forEach { (plKey, plLabel) ->
                                    FilterChip(
                                        selected = selectedPlacement == plKey,
                                        onClick = { selectedPlacement = plKey },
                                        label = { Text(plLabel) }
                                    )
                                }
                            }
                        }

                        if (AdPackageTypes.normalize(pkg.adType) == AdPackageTypes.HOME_SLIDER) {
                            HomeSliderExistingRecordPicker(
                                lang = lang,
                                session = session,
                                doctors = doctors,
                                hospitals = hospitals,
                                diagnostics = diagnosticCenters,
                                selectedTargets = selectedTargets,
                                onSelectionChange = { updated ->
                                    selectedTargets = updated
                                    val first = updated.firstOrNull()
                                    if (first != null && title.isBlank()) title = first.title
                                    if (first != null && imageUrl.isBlank()) imageUrl = first.photoUrl
                                },
                                onViewDoctor = onDoctorClick
                            )
                        } else {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text(tr(lang, "বিজ্ঞাপনের শিরোনাম *", "Ad Title *")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text(tr(lang, "বিবরণ", "Description")) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = imageUrl,
                                onValueChange = { imageUrl = it },
                                label = { Text(tr(lang, "ছবির URL অথবা গ্যালারি থেকে আপলোড *", "Ad Image URL or Gallery Upload *")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "ফোন থেকে ছবি আপলোড করুন", "Upload Ad Image from Gallery"))
                            }
                            if (imageUrl.isNotBlank()) {
                                val previewModel = remember(imageUrl) { decodeAdImageModel(imageUrl) }
                                AsyncImage(
                                    model = previewModel ?: imageUrl,
                                    contentDescription = "Ad preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BackgroundIce)
                                )
                            }
                            OutlinedTextField(
                                value = destinationUrl,
                                onValueChange = { destinationUrl = it },
                                label = { Text(tr(lang, "ডেস্টিনেশন URL (https://...)", "Destination URL (https://...)")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = durationInput,
                            onValueChange = { v -> durationInput = v.filter { it.isDigit() } },
                            label = {
                                Text(
                                    tr(
                                        lang,
                                        "ক্যাম্পেইনের মেয়াদ (সর্বোচ্চ ${pkg.maxCampaignDurationDays} দিন)",
                                        "Campaign Duration (Max ${pkg.maxCampaignDurationDays} days)"
                                    )
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Step 3: Payment Submission
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = tr(lang, "৩. পেমেন্ট জমা দিন (প্যাকেজ মূল্য: ৳${pkg.price.toInt()})", "3. Submit Payment (Price: ৳${pkg.price.toInt()})"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        if (activePaymentMethods.isEmpty()) {
                            Text(
                                text = tr(lang, "অ্যাডমিন এখনও কোনো পেমেন্ট নম্বর যুক্ত করেননি।", "No active Admin payment methods configured yet."),
                                style = MaterialTheme.typography.bodySmall,
                                color = RoseAlert
                            )
                        } else {
                            activePaymentMethods.forEach { method ->
                                val chosen = method.id == selectedMethod?.id
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (chosen) MedicalTealLight else BackgroundIce,
                                    border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) MedicalTeal else OutlineSoft),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedMethod = method }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${method.provider} — ${method.methodName} (${method.accountType})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("Receiving Number: ${method.receivingNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MedicalTealDark)
                                        if (method.instructions.isNotBlank()) {
                                            Text(method.instructions, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = senderMobile,
                            onValueChange = { senderMobile = it },
                            label = { Text(tr(lang, "যে নম্বর থেকে টাকা পাঠিয়েছেন *", "Sender Mobile Number *")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = trxId,
                            onValueChange = { trxId = it },
                            label = { Text(tr(lang, "ট্রানজেকশন আইডি (TRX ID) *", "Transaction ID (TRX ID) *")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = amountPaid,
                                onValueChange = { amountPaid = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text(tr(lang, "টাকার পরিমাণ *", "Amount *")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = paymentDate,
                                onValueChange = { paymentDate = it },
                                label = { Text(tr(lang, "তারিখ", "Payment Date")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = screenshotUrl,
                            onValueChange = { screenshotUrl = it },
                            label = { Text(tr(lang, "পেমেন্ট স্ক্রিনশট URL (ঐচ্ছিক)", "Screenshot URL (Optional)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            enabled = !submitting && isOnline && selectedMethod != null,
                            onClick = {
                                val method = selectedMethod ?: return@Button
                                val amt = amountPaid.toDoubleOrNull() ?: 0.0
                                val dur = durationInput.toIntOrNull() ?: pkg.durationDays
                                scope.launch {
                                    submitting = true
                                    val primaryTarget = selectedTargets.firstOrNull()
                                    val res = AdvertisingRemoteService.submitCampaignWithPayment(
                                        session = session,
                                        packageId = pkg.id,
                                        selectedPlacement = selectedPlacement,
                                        campaignDurationDays = dur,
                                        title = title,
                                        description = description,
                                        imageUrl = imageUrl,
                                        destinationUrl = destinationUrl,
                                        targetRecordType = primaryTarget?.recordType.orEmpty(),
                                        targetRecordId = primaryTarget?.recordId.orEmpty(),
                                        targetRecordTitle = primaryTarget?.title.orEmpty(),
                                        targetRecordSubtitle = primaryTarget?.subtitle.orEmpty(),
                                        targetRecordPhotoUrl = primaryTarget?.photoUrl.orEmpty(),
                                        promotedTargets = selectedTargets,
                                        paymentMethod = method,
                                        senderMobile = senderMobile,
                                        trxId = trxId,
                                        amountPaid = amt,
                                        paymentDate = paymentDate,
                                        screenshotUrl = screenshotUrl,
                                        renewalOfCampaignId = renewingCampaign?.id.orEmpty()
                                    )
                                    submitting = false
                                    res.onSuccess { onSubmitted() }
                                        .onFailure { onError(it.message ?: "Submission failed") }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("adv_submit_campaign_btn")
                        ) {
                            if (submitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text(tr(lang, "বিজ্ঞাপন ও পেমেন্ট জমা দিন", "Submit Advertisement & Payment"))
                        }
                    }
                }
            }
        }
    }
}

private fun encodeUriToDataUrl(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val original = BitmapFactory.decodeStream(stream) ?: return@runCatching null
            val maxDim = 900f
            val scale = minOf(maxDim / original.width, maxDim / original.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else original
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        }
    }.getOrNull()
}
