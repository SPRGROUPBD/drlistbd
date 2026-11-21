package com.example.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Send
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
import com.example.data.local.PaymentSettingsEntity
import com.example.data.local.SponsorEntity
import com.example.data.local.SponsorPackageEntity
import com.example.data.remote.AdvertiserDashboardData
import com.example.data.remote.AdvertiserPaymentRecord
import com.example.data.remote.AdvertiserRemoteService
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Direct Sponsor Advertisement Booking & Advertiser Portal.
 * - Shows ONLY Admin-added ACTIVE advertisement packages (Screen Placement Rate Card removed).
 * - Strictly enforces Fixed Package Pricing: Total Package Price = Fixed Package Rate (never multiplied by days).
 * - Supports bKash, Nagad & Rocket manual payments, image/video upload via Android Photo Picker,
 *   and full 7-state campaign status tracking.
 */
@Composable
fun AdvertiserPortalScreen(
    lang: AppLang,
    session: AdvertiserSession,
    cachedPackages: List<SponsorPackageEntity>,
    cachedPaymentSettings: PaymentSettingsEntity,
    isOnline: Boolean,
    nowMs: Long,
    onLoginSuccess: (AdvertiserSession) -> Unit,
    onLogout: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<AdvertiserDashboardData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Packages/Book, 1=My Campaigns, 2=Payments
    var selectedPackage by remember { mutableStateOf<SponsorPackageEntity?>(null) }
    var renewingAd by remember { mutableStateOf<SponsorEntity?>(null) }

    fun reloadDashboard() {
        if (!session.isLoggedIn) return
        scope.launch {
            isLoading = true
            AdvertiserRemoteService.fetchAdvertiserDashboard(session.id)
                .onSuccess { dashboard = it }
                .onFailure { onShowMessage(it.message ?: "Failed to load sponsor dashboard") }
            isLoading = false
        }
    }

    LaunchedEffect(session.id, isOnline) {
        if (session.isLoggedIn && isOnline) {
            reloadDashboard()
        }
    }

    // ONLY Admin-added ACTIVE packages are visible and purchasable
    val activePackages = remember(dashboard?.packages, cachedPackages) {
        val source = dashboard?.packages ?: cachedPackages
        source.filter { it.status.equals("ACTIVE", ignoreCase = true) }
            .sortedWith(compareBy<SponsorPackageEntity> { it.priceBdt }.thenBy { it.name })
    }
    val paySettings = dashboard?.paymentSettings ?: cachedPaymentSettings
    val myAds = dashboard?.myAds.orEmpty()
    val myPayments = dashboard?.myPayments.orEmpty()

    if (!session.isLoggedIn) {
        AdvertiserAuthAndBrowseView(
            lang = lang,
            isOnline = isOnline,
            activePackages = activePackages,
            onAuthSuccess = { newSession ->
                onLoginSuccess(newSession)
                onShowMessage(tr(lang, "স্পন্সর পোর্টালে স্বাগতম!", "Welcome to the Sponsor Portal!"))
            },
            onSelectPackageBeforeLogin = { pkg ->
                selectedPackage = pkg
                onShowMessage(
                    tr(
                        lang,
                        "‘${pkg.name}’ বুক করতে অনুগ্রহ করে লগইন বা রেজিস্ট্রেশন করুন।",
                        "Please sign in or register to book '${pkg.name}'."
                    )
                )
            },
            onError = onShowMessage,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .testTag("advertiser_portal_screen")
    ) {
        // Account Header Strip
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
                        text = session.businessName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.ownerName} • ${session.mobile}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { reloadDashboard() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            dashboard = null
                            selectedPackage = null
                            renewingAd = null
                            onLogout()
                        },
                        modifier = Modifier.testTag("adv_logout_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                }
            }
        }

        // Section Tabs
        TabRow(selectedTabIndex = activeTab, containerColor = SurfaceWhite) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = {
                    Text(
                        if (renewingAd != null) tr(lang, "রিনিউ", "Renew")
                        else tr(lang, "প্যাকেজ ও বুকিং", "Packages"),
                        maxLines = 1
                    )
                }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1; renewingAd = null },
                text = { Text(tr(lang, "আমার বিজ্ঞাপন", "My Campaigns"), maxLines = 1) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2; renewingAd = null },
                text = { Text(tr(lang, "পেমেন্ট", "Payments"), maxLines = 1) }
            )
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MedicalTeal)
        }

        when (activeTab) {
            0 -> {
                val chosenPkg = selectedPackage
                if (chosenPkg == null) {
                    SponsorPackageCatalogTab(
                        lang = lang,
                        packages = activePackages,
                        renewingAd = renewingAd,
                        onClearRenew = { renewingAd = null },
                        onSelectPackage = { pkg -> selectedPackage = pkg }
                    )
                } else {
                    SponsorBookingFormTab(
                        lang = lang,
                        session = session,
                        selectedPackage = chosenPkg,
                        paymentSettings = paySettings,
                        isOnline = isOnline,
                        renewingAd = renewingAd,
                        onChangePackage = { selectedPackage = null },
                        onSubmitted = {
                            selectedPackage = null
                            renewingAd = null
                            activeTab = 1
                            reloadDashboard()
                            onShowMessage(
                                tr(
                                    lang,
                                    "বিজ্ঞাপন আবেদন ও পেমেন্ট জমা হয়েছে! অ্যাডমিন পেমেন্ট যাচাই করলে ক্যাম্পেইন চালু হবে।",
                                    "Sponsor request & payment submitted! Campaign activates once Admin verifies payment."
                                )
                            )
                        },
                        onError = onShowMessage
                    )
                }
            }
            1 -> SponsorCampaignStatusTab(
                lang = lang,
                myAds = myAds,
                nowMs = nowMs,
                onBrowsePackages = { renewingAd = null; selectedPackage = null; activeTab = 0 },
                onRenewAd = { expiredAd ->
                    renewingAd = expiredAd
                    selectedPackage = activePackages.firstOrNull { it.id == expiredAd.packageId }
                    activeTab = 0
                }
            )
            2 -> SponsorPaymentsTab(lang = lang, payments = myPayments)
        }
    }
}

@Composable
private fun AdvertiserAuthAndBrowseView(
    lang: AppLang,
    isOnline: Boolean,
    activePackages: List<SponsorPackageEntity>,
    onAuthSuccess: (AdvertiserSession) -> Unit,
    onSelectPackageBeforeLogin: (SponsorPackageEntity) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var viewTab by remember { mutableIntStateOf(0) } // 0=Packages, 1=Login/Register
    var isRegister by remember { mutableStateOf(false) }
    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .testTag("advertiser_auth_screen")
    ) {
        TabRow(selectedTabIndex = viewTab, containerColor = SurfaceWhite) {
            Tab(
                selected = viewTab == 0,
                onClick = { viewTab = 0 },
                text = { Text(tr(lang, "স্পন্সর প্যাকেজসমূহ", "Sponsor Packages"), maxLines = 1) }
            )
            Tab(
                selected = viewTab == 1,
                onClick = { viewTab = 1 },
                text = { Text(tr(lang, "লগইন / রেজিস্টার", "Login / Register"), maxLines = 1) }
            )
        }

        if (viewTab == 0) {
            SponsorPackageCatalogTab(
                lang = lang,
                packages = activePackages,
                renewingAd = null,
                onClearRenew = {},
                onSelectPackage = { pkg ->
                    onSelectPackageBeforeLogin(pkg)
                    viewTab = 1
                }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, MedicalBlueContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(MedicalBlueLight.copy(alpha = 0.55f), SurfaceWhite)))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MedicalBlue,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = Color.White)
                                    }
                                }
                                Column {
                                    Text(
                                        text = tr(lang, "ডাইরেক্ট স্পন্সর বিজ্ঞাপন পোর্টাল", "Direct Sponsor Advertisement Portal"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = tr(
                                            lang,
                                            "অ্যাডমিন অনুমোদিত ফিক্সড প্যাকেজ কিনুন — বিকাশ, নগদ ও রকেট পেমেন্ট।",
                                            "Book Admin-published fixed packages via bKash, Nagad, or Rocket."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isRegister,
                            onClick = { isRegister = false },
                            label = { Text(tr(lang, "লগইন", "Login")) },
                            modifier = Modifier.weight(1f).testTag("adv_mode_login")
                        )
                        FilterChip(
                            selected = isRegister,
                            onClick = { isRegister = true },
                            label = { Text(tr(lang, "নতুন অ্যাকাউন্ট", "Register")) },
                            modifier = Modifier.weight(1f).testTag("adv_mode_register")
                        )
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, OutlineSoft)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isRegister) {
                                OutlinedTextField(
                                    value = businessName,
                                    onValueChange = { businessName = it },
                                    label = { Text(tr(lang, "প্রতিষ্ঠান / ব্যবসার নাম *", "Institution / Business Name *")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("adv_reg_business")
                                )
                                OutlinedTextField(
                                    value = ownerName,
                                    onValueChange = { ownerName = it },
                                    label = { Text(tr(lang, "যোগাযোগকারীর নাম *", "Contact Person / Owner Name *")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("adv_reg_owner")
                                )
                            }
                            OutlinedTextField(
                                value = mobile,
                                onValueChange = { mobile = it },
                                label = {
                                    Text(
                                        if (isRegister) tr(lang, "মোবাইল নম্বর (01XXXXXXXXX) *", "Mobile Number (01XXXXXXXXX) *")
                                        else tr(lang, "মোবাইল নম্বর বা ইমেইল *", "Mobile Number or Email *")
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (isRegister) KeyboardType.Phone else KeyboardType.Email
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("adv_auth_mobile")
                            )
                            if (isRegister) {
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text(tr(lang, "ইমেইল (ঐচ্ছিক)", "Email (Optional)")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { address = it },
                                    label = { Text(tr(lang, "ঠিকানা *", "Address *")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("adv_reg_address")
                                )
                            }
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(tr(lang, "পাসওয়ার্ড *", "Password *")) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("adv_auth_password")
                            )

                            if (!isOnline) {
                                Surface(shape = RoundedCornerShape(10.dp), color = AmberSoft) {
                                    Text(
                                        text = tr(lang, "লগইন ও বিজ্ঞাপন জমার জন্য ইন্টারনেট সংযোগ প্রয়োজন।", "Internet connection required to sign in or submit requests."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (!isOnline) {
                                        onError(tr(lang, "ইন্টারনেট সংযোগ চালু করুন।", "Please connect to the internet."))
                                        return@Button
                                    }
                                    scope.launch {
                                        busy = true
                                        val res = if (isRegister) {
                                            AdvertiserRemoteService.registerAdvertiser(
                                                businessName = businessName,
                                                ownerName = ownerName,
                                                mobile = mobile,
                                                email = email,
                                                address = address,
                                                password = password
                                            )
                                        } else {
                                            AdvertiserRemoteService.loginAdvertiser(mobile, password)
                                        }
                                        busy = false
                                        res.onSuccess(onAuthSuccess)
                                            .onFailure { onError(it.message ?: "Authentication failed") }
                                    }
                                },
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("adv_auth_submit")
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        if (isRegister) tr(lang, "অ্যাকাউন্ট তৈরি করুন", "Create Sponsor Account")
                                        else tr(lang, "লগইন করুন", "Sign In"),
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun SponsorPackageCatalogTab(
    lang: AppLang,
    packages: List<SponsorPackageEntity>,
    renewingAd: SponsorEntity?,
    onClearRenew: () -> Unit,
    onSelectPackage: (SponsorPackageEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("sponsor_package_catalog")
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, MedicalTeal.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(MedicalTealLight.copy(alpha = 0.45f), SurfaceWhite)))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr(lang, "স্পন্সর বিজ্ঞাপন প্যাকেজসমূহ", "Direct Sponsor Advertisement Packages"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = tr(
                            lang,
                            "প্রতিটি প্যাকেজের মূল্য সম্পূর্ণ ফিক্সড — কোনো অতিরিক্ত চার্জ বা দিনের গুণিতক নেই।",
                            "Fixed Package Price covers the entire campaign duration — no extra fees or per-day multipliers."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (renewingAd != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MedicalBlueLight,
                    border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(lang, "ক্যাম্পেইন রিনিউ: ${renewingAd.promoTitle.ifBlank { renewingAd.name }}", "Renewing: ${renewingAd.promoTitle.ifBlank { renewingAd.name }}"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MedicalBlueDark
                            )
                            Text(
                                text = tr(lang, "নিচের যেকোনো সক্রিয় প্যাকেজ নির্বাচন করুন।", "Select an active package below to renew."),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        TextButton(onClick = onClearRenew) {
                            Text(tr(lang, "বাতিল", "Cancel"))
                        }
                    }
                }
            }
        }

        if (packages.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, OutlineSoft)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MedicalBlue,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = tr(lang, "এই মুহূর্তে কোনো প্যাকেজ প্রকাশিত নেই", "No Active Sponsor Packages Available"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = tr(
                                lang,
                                "অ্যাডমিন প্যানেল থেকে নতুন স্পন্সর প্যাকেজ যুক্ত ও সক্রিয় করলে এখানে দেখতে পাবেন।",
                                "Only advertisement packages added and activated by the Admin appear here for booking."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(packages, key = { it.id }) { pkg ->
                SponsorPackageCard(
                    lang = lang,
                    pkg = pkg,
                    onSelect = { onSelectPackage(pkg) }
                )
            }
        }
    }
}

@Composable
private fun SponsorPackageCard(
    lang: AppLang,
    pkg: SponsorPackageEntity,
    onSelect: () -> Unit
) {
    val fixedTotal = SponsorCampaignSelector.calculateFixedPackageTotal(pkg.priceBdt, pkg.durationDays)
    val sizeText = pkg.sizeLabel.ifBlank {
        when {
            pkg.adType.equals("REWARD_VIDEO", true) || pkg.placementType.equals(SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO, true) -> "Video"
            pkg.adType.equals("POPUP", true) || pkg.placementType.equals(SponsorCampaignSelector.PLACEMENT_POPUP, true) -> "Responsive"
            pkg.widthPx > 0 && pkg.heightPx > 0 -> "${pkg.widthPx}×${pkg.heightPx} px"
            else -> "Responsive"
        }
    }
    val placementLabel = formatPlacementDisplay(lang, pkg.adPlacement, pkg.placementType)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.2.dp, MedicalBlueContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sponsor_package_card_${pkg.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Optional Admin-uploaded package image
            if (pkg.packageImageUrl.isNotBlank() && SponsorCampaignSelector.isValidImageSource(pkg.packageImageUrl)) {
                AsyncImage(
                    model = pkg.packageImageUrl,
                    contentDescription = pkg.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .background(MedicalBlueLight)
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = pkg.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MedicalTealLight) {
                                Text(
                                    text = formatAdTypeBadge(lang, pkg.adType, pkg.placementType),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MedicalTealDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = MedicalBlueLight) {
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MedicalBlueDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Fixed Package Price Pill
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MedicalBlueDark
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "৳$fixedTotal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = tr(lang, "ফিক্সড মূল্য", "Fixed Price"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                    }
                }

                // Advertisement Size Visual Preview
                AdSizeVisualPreview(lang = lang, pkg = pkg, sizeText = sizeText)

                // Placement + Duration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = MedicalTealDark, modifier = Modifier.size(16.dp))
                        Text(
                            text = placementLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = BackgroundIce) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MedicalBlueDark, modifier = Modifier.size(14.dp))
                            Text(
                                text = tr(lang, "${pkg.durationDays} দিন", "${pkg.durationDays} Days"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedicalBlueDark
                            )
                        }
                    }
                }

                if (pkg.description.isNotBlank()) {
                    Text(
                        text = pkg.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("select_package_${pkg.id}")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tr(lang, "প্যাকেজ নির্বাচন করুন (৳$fixedTotal)", "Select Package (৳$fixedTotal)"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AdSizeVisualPreview(
    lang: AppLang,
    pkg: SponsorPackageEntity,
    sizeText: String
) {
    val isVideo = pkg.adType.equals("REWARD_VIDEO", true) || pkg.placementType.equals(SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO, true)
    val isPopup = pkg.adType.equals("POPUP", true) || pkg.placementType.equals(SponsorCampaignSelector.PLACEMENT_POPUP, true)
    val isStrip = pkg.placementType.equals(SponsorCampaignSelector.PLACEMENT_BANNER_300x50, true) || (pkg.heightPx in 1..75)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BackgroundIce,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val boxW = if (isPopup) 52.dp else 72.dp
            val boxH = when {
                isStrip -> 24.dp
                isPopup -> 56.dp
                isVideo -> 46.dp
                else -> 46.dp
            }
            Box(
                modifier = Modifier
                    .size(width = boxW, height = boxH)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(MedicalBlue.copy(alpha = 0.22f), MedicalTeal.copy(alpha = 0.28f))))
                    .border(1.2.dp, MedicalBlue.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isVideo -> Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = MedicalBlueDark, modifier = Modifier.size(22.dp))
                    isPopup -> Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = MedicalBlueDark, modifier = Modifier.size(18.dp))
                    else -> Text(
                        text = if (isStrip) "AD" else "CARD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MedicalBlueDark
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr(lang, "বিজ্ঞাপনের সাইজ প্রিভিউ: $sizeText", "Ad Size Preview: $sizeText"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when {
                        isVideo -> tr(lang, "ডাক্তার প্রোফাইল ক্লিকে রিওয়ার্ড ভিডিও প্রদর্শিত হবে", "Plays when users tap to view Doctor details")
                        isPopup -> tr(lang, "অ্যাপ ওপেন করার সময় রেসপন্সিভ পপআপ প্রদর্শিত হবে", "Responsive popup shown when opening the app")
                        isStrip -> tr(lang, "সব স্ক্রিনে কম্প্যাক্ট ব্যানার স্ট্রিপ হিসেবে প্রদর্শিত হবে", "Compact banner strip shown across app screens")
                        else -> tr(lang, "সব স্ক্রিনে নেটিভ ব্যানার কার্ড হিসেবে প্রদর্শিত হবে", "Native banner card shown seamlessly inside app screens")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SponsorBookingFormTab(
    lang: AppLang,
    session: AdvertiserSession,
    selectedPackage: SponsorPackageEntity,
    paymentSettings: PaymentSettingsEntity,
    isOnline: Boolean,
    renewingAd: SponsorEntity?,
    onChangePackage: () -> Unit,
    onSubmitted: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isVideoPackage = selectedPackage.adType.equals("REWARD_VIDEO", true) ||
        selectedPackage.placementType.equals(SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO, true)
    val fixedTotal = SponsorCampaignSelector.calculateFixedPackageTotal(selectedPackage.priceBdt, selectedPackage.durationDays)

    var businessName by remember(renewingAd?.id) { mutableStateOf(renewingAd?.name?.ifBlank { session.businessName } ?: session.businessName) }
    var contactPersonName by remember(renewingAd?.id) { mutableStateOf(renewingAd?.contactPersonName?.ifBlank { session.ownerName } ?: session.ownerName) }
    var contactNumber by remember(renewingAd?.id) { mutableStateOf(renewingAd?.contactNumber?.ifBlank { session.mobile } ?: session.mobile) }
    var title by remember(renewingAd?.id) { mutableStateOf(renewingAd?.promoTitle.orEmpty()) }
    var description by remember(renewingAd?.id) { mutableStateOf(renewingAd?.description.orEmpty()) }
    var websiteUrl by remember(renewingAd?.id) { mutableStateOf(renewingAd?.websiteUrl.orEmpty()) }
    var imageSource by remember(renewingAd?.id) { mutableStateOf(renewingAd?.bannerImageUrl.orEmpty()) }
    var videoSource by remember(renewingAd?.id) { mutableStateOf(renewingAd?.videoUrl.orEmpty()) }
    var logoSource by remember(renewingAd?.id) { mutableStateOf(renewingAd?.logoUrl.orEmpty()) }

    val availablePlacements = remember(selectedPackage.id) {
        listOf(
            "ALL" to tr(lang, "সব স্ক্রিন (All Screens)", "All Screens"),
            "HOME" to tr(lang, "হোম স্ক্রিন (Home)", "Home Screen"),
            "DOCTOR_LIST" to tr(lang, "ডাক্তার তালিকা", "Doctor List"),
            "DOCTOR_DETAILS" to tr(lang, "ডাক্তার প্রোফাইল", "Doctor Details"),
            "HOSPITAL_LIST" to tr(lang, "হাসপাতাল তালিকা", "Hospital List"),
            "DIAGNOSTIC_LIST" to tr(lang, "ডায়াগনস্টিক সেন্টার", "Diagnostic Center List"),
            "SPECIALIST" to tr(lang, "চিকিৎসা বিশেষজ্ঞ", "Medical Specialist"),
            "CHAMBER_SCHEDULE" to tr(lang, "চেম্বার সময়সূচি", "Chamber Schedule"),
            "ABOUT_US" to tr(lang, "আমাদের সম্পর্কে (About Us)", "About Us")
        )
    }
    val canChoosePlacement = remember(selectedPackage) {
        val fixedTypes = setOf(SponsorCampaignSelector.PLACEMENT_POPUP, SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO)
        selectedPackage.placementType.uppercase() !in fixedTypes &&
            !selectedPackage.adPlacement.equals("APP_OPEN", true) &&
            !selectedPackage.adPlacement.equals("DOCTOR_CLICK", true)
    }
    var selectedPlacement by remember(selectedPackage.id) {
        mutableStateOf(selectedPackage.adPlacement.ifBlank { "ALL" })
    }

    var paymentMethod by remember { mutableStateOf("BKASH") } // BKASH | NAGAD | ROCKET
    var senderMobile by remember { mutableStateOf(session.mobile) }
    var trxId by remember { mutableStateOf("") }
    var paidAmount by remember(selectedPackage.id) { mutableStateOf(fixedTotal.toString()) }
    var screenshotSource by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for Advertisement Image or Video (Google Play Policy compliant)
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            if (mime.startsWith("video/")) {
                if (!SponsorCampaignSelector.isSupportedVideoBytes(bytes)) {
                    onError(tr(lang, "সর্বোচ্চ ১৫MB MP4 বা WebM ভিডিও আপলোড করুন।", "Upload a valid MP4/WebM video (max 15 MB)."))
                    return@runCatching
                }
                val cleanMime = if (mime.contains("webm")) "video/webm" else "video/mp4"
                videoSource = "data:$cleanMime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                if (!SponsorCampaignSelector.isSupportedImageBytes(bytes)) {
                    onError(tr(lang, "সর্বোচ্চ ৩MB PNG/JPEG/WebP ছবি আপলোড করুন।", "Upload a valid PNG/JPEG/WebP image (max 3 MB)."))
                    return@runCatching
                }
                val cleanMime = when {
                    mime.contains("png") -> "image/png"
                    mime.contains("webp") -> "image/webp"
                    else -> "image/jpeg"
                }
                imageSource = "data:$cleanMime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.onFailure {
            onError(tr(lang, "ফাইল লোড করা যায়নি।", "Could not read selected file."))
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            if (!SponsorCampaignSelector.isSupportedImageBytes(bytes)) {
                onError(tr(lang, "সঠিক পেমেন্ট স্ক্রিনশট ছবি নির্বাচন করুন (সর্বোচ্চ ৩MB)।", "Select a valid receipt screenshot (max 3 MB)."))
                return@runCatching
            }
            screenshotSource = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("sponsor_booking_form")
    ) {
        // Selected Package Header Banner
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MedicalBlueDark)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = selectedPackage.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${selectedPackage.sizeLabel.ifBlank { "${selectedPackage.widthPx}×${selectedPackage.heightPx} px" }} • ${selectedPackage.durationDays} ${tr(lang, "দিন", "Days")} • ${tr(lang, "ফিক্সড রেট", "Fixed Rate")}: ৳$fixedTotal",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFBAE6FD)
                        )
                    }
                    FilledTonalButton(
                        onClick = onChangePackage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("change_package_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(lang, "পরিবর্তন", "Change"), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Section 1: Business & Contact Info
        item {
            BookingSectionCard(title = tr(lang, "১. প্রতিষ্ঠান ও যোগাযোগের তথ্য", "1. Business & Contact Information")) {
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text(tr(lang, "প্রতিষ্ঠান / ব্যবসার নাম *", "Institution / Business Name *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_business_input")
                )
                OutlinedTextField(
                    value = contactPersonName,
                    onValueChange = { contactPersonName = it },
                    label = { Text(tr(lang, "যোগাযোগকারীর নাম *", "Contact Person Name *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_contact_person_input")
                )
                OutlinedTextField(
                    value = contactNumber,
                    onValueChange = { contactNumber = it },
                    label = { Text(tr(lang, "মোবাইল নম্বর *", "Mobile Number *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_mobile_input")
                )
            }
        }

        // Section 2: Advertisement Creative & Details
        item {
            BookingSectionCard(title = tr(lang, "২. বিজ্ঞাপনের তথ্য ও মিডিয়া", "2. Advertisement Details & Media")) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr(lang, "বিজ্ঞাপনের শিরোনাম *", "Advertisement Title *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(tr(lang, "বিজ্ঞাপনের বিবরণ", "Advertisement Description")) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("adv_desc_input")
                )
                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it },
                    label = { Text(tr(lang, "ওয়েবসাইট বা ল্যান্ডিং পেজ URL", "Website or Landing Page URL")) },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_website_input")
                )

                // Upload Advertisement Image or Video (Android Photo Picker)
                OutlinedButton(
                    onClick = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(
                                if (isVideoPackage) ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                else ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    border = BorderStroke(1.2.dp, MedicalBlue),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("adv_upload_media_btn")
                ) {
                    Icon(
                        imageVector = if (isVideoPackage) Icons.Rounded.VideoLibrary else Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MedicalBlue
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isVideoPackage) {
                            tr(lang, "বিজ্ঞাপনের ভিডিও বা ছবি আপলোড করুন", "Upload Advertisement Video or Image")
                        } else {
                            tr(lang, "বিজ্ঞাপনের ছবি আপলোড করুন (PNG/JPG/WebP)", "Upload Advertisement Image (PNG/JPG/WebP)")
                        },
                        color = MedicalBlueDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isVideoPackage) {
                    OutlinedTextField(
                        value = if (videoSource.startsWith("data:")) "" else videoSource,
                        onValueChange = { videoSource = it },
                        label = { Text(tr(lang, "অথবা ভিডিও URL (https://...mp4)", "Or Video HTTPS URL (https://...mp4)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("adv_video_url_input")
                    )
                    if (videoSource.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(10.dp), color = EmeraldSoft) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldAvailable)
                                Text(
                                    text = tr(lang, "ভিডিও মিডিয়া যুক্ত হয়েছে", "Video creative attached"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldAvailable
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = if (imageSource.startsWith("data:")) "" else imageSource,
                    onValueChange = { imageSource = it },
                    label = {
                        Text(
                            if (isVideoPackage) tr(lang, "থাম্বনেইল / প্রিভিউ ছবির URL (ঐচ্ছিক)", "Thumbnail / Preview Image URL (Optional)")
                            else tr(lang, "অথবা ছবির HTTPS URL", "Or Image HTTPS URL")
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_image_url_input")
                )

                if (imageSource.isNotBlank()) {
                    AsyncImage(
                        model = imageSource,
                        contentDescription = "Selected advertisement image preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MedicalBlueLight)
                    )
                }

                // Placement selector where available
                if (canChoosePlacement) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr(lang, "বিজ্ঞাপন প্লেসমেন্ট নির্বাচন করুন", "Select Advertisement Placement"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availablePlacements.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedPlacement.equals(key, ignoreCase = true),
                                onClick = { selectedPlacement = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Fixed Package Price Review
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MedicalTealLight.copy(alpha = 0.55f)),
                border = BorderStroke(1.2.dp, MedicalTeal.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr(lang, "৩. ফিক্সড প্যাকেজ মূল্য পর্যালোচনা", "3. Fixed Package Price Summary"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MedicalTealDark
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr(lang, "প্যাকেজ", "Package"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(selectedPackage.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr(lang, "ক্যাম্পেইনের মেয়াদ", "Campaign Duration"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(tr(lang, "${selectedPackage.durationDays} দিন", "${selectedPackage.durationDays} Days"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    HorizontalDivider(color = MedicalTeal.copy(alpha = 0.25f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tr(lang, "মোট প্রদেয় মূল্য (ফিক্সড)", "Total Package Price (Fixed)"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "৳$fixedTotal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MedicalBlueDark
                        )
                    }
                    Text(
                        text = tr(
                            lang,
                            "মোট মূল্য = ফিক্সড প্যাকেজ রেট (দিনের সংখ্যা দিয়ে গুণ করা হয় না)।",
                            "Total Price = Fixed Package Rate (duration controls campaign expiry only)."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Section 4: Payment Instructions & Payment Details (bKash / Nagad / Rocket)
        item {
            BookingSectionCard(title = tr(lang, "৪. পেমেন্ট নির্দেশনা ও তথ্য (বিকাশ / নগদ / রকেট)", "4. Payment Instructions & Details")) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BackgroundIce,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (paymentSettings.bkashNumber.isNotBlank()) {
                            PaymentAccountLine("bKash (${paymentSettings.bkashAccountType})", paymentSettings.bkashNumber, Color(0xFFE2136E))
                        }
                        if (paymentSettings.nagadNumber.isNotBlank()) {
                            PaymentAccountLine("Nagad (${paymentSettings.nagadAccountType})", paymentSettings.nagadNumber, Color(0xFFF6921E))
                        }
                        if (paymentSettings.rocketNumber.isNotBlank()) {
                            PaymentAccountLine("Rocket (${paymentSettings.rocketAccountType})", paymentSettings.rocketNumber, Color(0xFF8C3494))
                        }
                        if (paymentSettings.bkashNumber.isBlank() && paymentSettings.nagadNumber.isBlank() && paymentSettings.rocketNumber.isBlank()) {
                            Text(
                                text = tr(lang, "অ্যাডমিন এখনো পেমেন্ট নম্বর যুক্ত করেননি।", "Payment numbers will appear here once configured by Admin."),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        val instr = tr(lang, paymentSettings.instructionsBn, paymentSettings.instructionsEn)
                        if (instr.isNotBlank()) {
                            HorizontalDivider(color = OutlineSoft)
                            Text(text = instr, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                // Payment Method Selector: bKash / Nagad / Rocket
                Text(
                    text = tr(lang, "পেমেন্ট মেথড নির্বাচন করুন *", "Select Payment Method *"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("BKASH" to "bKash", "NAGAD" to "Nagad", "ROCKET" to "Rocket").forEach { (key, label) ->
                        FilterChip(
                            selected = paymentMethod == key,
                            onClick = { paymentMethod = key },
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f).testTag("pay_method_${key.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = senderMobile,
                    onValueChange = { senderMobile = it },
                    label = { Text(tr(lang, "প্রেরকের মোবাইল নম্বর *", "Sender Number *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_sender_mobile_input")
                )
                OutlinedTextField(
                    value = trxId,
                    onValueChange = { trxId = it },
                    label = { Text(tr(lang, "ট্রানজেকশন আইডি (Transaction ID) *", "Transaction ID (TrxID) *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_trx_input")
                )
                OutlinedTextField(
                    value = paidAmount,
                    onValueChange = { paidAmount = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr(lang, "পেমেন্টের পরিমাণ (টাকা) *", "Payment Amount (BDT) *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("adv_paid_amount_input")
                )

                OutlinedButton(
                    onClick = {
                        screenshotPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (screenshotSource.isBlank()) tr(lang, "পেমেন্ট স্ক্রিনশট যুক্ত করুন (ঐচ্ছিক)", "Attach Payment Screenshot (Optional)")
                        else tr(lang, "পেমেন্ট স্ক্রিনশট যুক্ত হয়েছে ✓", "Payment Screenshot Attached ✓")
                    )
                }

                Button(
                    onClick = {
                        if (!isOnline) {
                            onError(tr(lang, "আবেদন জমা দিতে ইন্টারনেট সংযোগ প্রয়োজন।", "Internet connection is required to submit."))
                            return@Button
                        }
                        val amountInt = paidAmount.toIntOrNull() ?: 0
                        scope.launch {
                            submitting = true
                            val result = AdvertiserRemoteService.submitAdvertisementWithPayment(
                                advertiser = session,
                                pkg = selectedPackage,
                                title = title,
                                businessName = businessName,
                                contactPersonName = contactPersonName,
                                description = description,
                                imageSource = imageSource,
                                logoSource = logoSource,
                                mediaType = if (videoSource.isNotBlank()) "VIDEO" else "IMAGE",
                                videoSource = videoSource,
                                selectedPlacement = if (canChoosePlacement) selectedPlacement else selectedPackage.adPlacement,
                                websiteUrl = websiteUrl,
                                contactNumber = contactNumber,
                                paymentMethod = paymentMethod,
                                senderMobile = senderMobile,
                                transactionId = trxId,
                                amountPaidBdt = amountInt,
                                screenshotSource = screenshotSource,
                                renewalOfAdId = renewingAd?.id.orEmpty()
                            )
                            submitting = false
                            result.onSuccess { onSubmitted() }
                                .onFailure { onError(it.message ?: "Submission failed") }
                        }
                    },
                    enabled = !submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTealDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("adv_submit_request_btn")
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tr(lang, "বিজ্ঞাপন আবেদন জমা দিন (৳$fixedTotal)", "Submit Advertisement Request (৳$fixedTotal)"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentAccountLine(label: String, number: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
        Text(text = number, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
    }
}

@Composable
private fun BookingSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, OutlineSoft)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MedicalBlueDark
            )
            content()
        }
    }
}

@Composable
private fun SponsorCampaignStatusTab(
    lang: AppLang,
    myAds: List<SponsorEntity>,
    nowMs: Long,
    onBrowsePackages: () -> Unit,
    onRenewAd: (SponsorEntity) -> Unit
) {
    val activeCount = myAds.count { SponsorCampaignSelector.isCampaignValid(it, nowMs) }
    val pendingCount = myAds.count {
        it.status.uppercase() in setOf("PENDING", "PAYMENT_PENDING", "PENDING_PAYMENT", "PENDING_APPROVAL")
    }
    val expiredCount = myAds.count { SponsorCampaignSelector.isCampaignExpired(it, nowMs) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("sponsor_campaigns_tab")
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusMetricTile(tr(lang, "সক্রিয়", "Active"), "$activeCount", EmeraldAvailable, Modifier.weight(1f))
                StatusMetricTile(tr(lang, "অপেক্ষমাণ", "Pending"), "$pendingCount", AmberPending, Modifier.weight(1f))
                StatusMetricTile(tr(lang, "মেয়াদোত্তীর্ণ", "Expired"), "$expiredCount", RoseAlert, Modifier.weight(1f))
            }
        }

        if (myAds.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, OutlineSoft)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = MedicalBlue, modifier = Modifier.size(44.dp))
                        Text(
                            text = tr(lang, "এখনো কোনো বিজ্ঞাপন আবেদন নেই", "No Sponsor Campaigns Yet"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Button(onClick = onBrowsePackages, colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue)) {
                            Text(tr(lang, "প্যাকেজ দেখুন ও বুক করুন", "Browse Sponsor Packages"))
                        }
                    }
                }
            }
        } else {
            items(myAds, key = { it.id }) { ad ->
                val isExpired = SponsorCampaignSelector.isCampaignExpired(ad, nowMs)
                val normalizedStatus = if (isExpired && ad.status.equals("ACTIVE", true)) "EXPIRED" else ad.status.uppercase()
                val (badgeBg, badgeFg, badgeText) = statusStyle(lang, normalizedStatus)
                val remaining = SponsorCampaignSelector.remainingDays(ad, nowMs)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, OutlineSoft)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ad.promoTitle.ifBlank { ad.name },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ad.packageName.ifBlank { ad.placementType }} • ৳${ad.priceBdt} (${ad.durationDays}d)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (ad.activatedAt > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${tr(lang, "শুরু", "Start")}: ${SponsorCampaignSelector.formatDateDisplay(ad.activatedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${tr(lang, "মেয়াদ শেষ", "Expires")}: ${SponsorCampaignSelector.formatDateDisplay(ad.expiryAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (normalizedStatus == "ACTIVE" && remaining > 0) {
                            Text(
                                text = tr(lang, "বাকি আছে $remaining দিন", "$remaining days remaining"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAvailable
                            )
                        }

                        if (normalizedStatus == "EXPIRED") {
                            OutlinedButton(
                                onClick = { onRenewAd(ad) },
                                border = BorderStroke(1.dp, MedicalBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "পুনরায় বুক / রিনিউ করুন", "Renew Campaign"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorPaymentsTab(
    lang: AppLang,
    payments: List<AdvertiserPaymentRecord>
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag("sponsor_payments_tab")
    ) {
        if (payments.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, OutlineSoft)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "এখনো কোনো পেমেন্ট রেকর্ড নেই।", "No payment submissions yet."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(payments, key = { it.id }) { pay ->
                val (bg, fg, label) = when (pay.status.uppercase()) {
                    "VERIFIED" -> Triple(EmeraldSoft, EmeraldAvailable, tr(lang, "যাচাইকৃত", "Verified"))
                    "REJECTED" -> Triple(RoseSoft, RoseAlert, tr(lang, "বাতিল", "Rejected"))
                    else -> Triple(AmberSoft, AmberPending, tr(lang, "যাচাই অপেক্ষমাণ", "Pending Review"))
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, OutlineSoft)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pay.packageName.ifBlank { "Sponsor Package" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(shape = RoundedCornerShape(8.dp), color = bg) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = fg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${pay.paymentMethod} • TrxID: ${pay.transactionId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "৳${pay.amountBdt}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MedicalBlueDark
                            )
                        }
                        Text(
                            text = "${tr(lang, "প্রেরক", "Sender")}: ${pay.senderMobile} • ${SponsorCampaignSelector.formatDateDisplay(pay.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        if (pay.adminNote.isNotBlank()) {
                            Text(
                                text = "${tr(lang, "অ্যাডমিন নোট", "Admin Note")}: ${pay.adminNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MedicalBlueDark
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

private fun statusStyle(lang: AppLang, status: String): Triple<Color, Color, String> = when (status.uppercase()) {
    "ACTIVE" -> Triple(EmeraldSoft, EmeraldAvailable, tr(lang, "সক্রিয়", "Active"))
    "PAUSED" -> Triple(MedicalBlueLight, MedicalBlueDark, tr(lang, "বিরতি", "Paused"))
    "EXPIRED" -> Triple(RoseSoft, RoseAlert, tr(lang, "মেয়াদোত্তীর্ণ", "Expired"))
    "REJECTED" -> Triple(RoseSoft, RoseAlert, tr(lang, "প্রত্যাখ্যাত", "Rejected"))
    "CANCELLED" -> Triple(OutlineSoft, TextSecondary, tr(lang, "বাতিল", "Cancelled"))
    "PAYMENT_PENDING", "PENDING_PAYMENT" -> Triple(AmberSoft, AmberPending, tr(lang, "পেমেন্ট যাচাই", "Payment Pending"))
    else -> Triple(AmberSoft, AmberPending, tr(lang, "অপেক্ষমাণ", "Pending"))
}

private fun formatAdTypeBadge(lang: AppLang, adType: String, placementType: String): String {
    val norm = adType.ifBlank { placementType }.uppercase()
    return when {
        "REWARD" in norm || "VIDEO" in norm -> tr(lang, "রিওয়ার্ড ভিডিও", "Reward Video")
        "POPUP" in norm -> tr(lang, "পপআপ অ্যাড", "Popup Ad")
        "NATIVE" in norm || norm == SponsorCampaignSelector.PLACEMENT_BANNER_300x200 -> tr(lang, "নেটিভ ব্যানার", "Native Banner")
        else -> tr(lang, "ব্যানার অ্যাড", "Banner Ad")
    }
}

private fun formatPlacementDisplay(lang: AppLang, adPlacement: String, placementType: String): String {
    val p = adPlacement.ifBlank { placementType }.uppercase()
    return when (p) {
        "ALL", "ALL_SCREENS" -> tr(lang, "সব স্ক্রিন (All Screens)", "All Screens")
        "APP_OPEN", "POPUP_AD" -> tr(lang, "অ্যাপ ওপেন পপআপ", "App Open Popup")
        "DOCTOR_CLICK", "REWARD_VIDEO_DOCTOR_CLICK" -> tr(lang, "ডাক্তার ক্লিক (রিওয়ার্ড)", "Doctor Click (Reward)")
        "HOME", "HOME_SCREEN" -> tr(lang, "হোম স্ক্রিন", "Home Screen")
        "DOCTOR_LIST" -> tr(lang, "ডাক্তার তালিকা", "Doctor List")
        "DOCTOR_DETAILS" -> tr(lang, "ডাক্তার প্রোফাইল", "Doctor Details")
        "HOSPITAL_LIST" -> tr(lang, "হাসপাতাল তালিকা", "Hospital List")
        "DIAGNOSTIC_LIST" -> tr(lang, "ডায়াগনস্টিক সেন্টার", "Diagnostic Center List")
        "SPECIALIST" -> tr(lang, "চিকিৎসা বিশেষজ্ঞ", "Medical Specialist")
        "CHAMBER_SCHEDULE" -> tr(lang, "চেম্বার সময়সূচি", "Chamber Schedule")
        "ABOUT_US", "ABOUT_US_SPONSOR" -> tr(lang, "আমাদের সম্পর্কে", "About Us")
        else -> p.replace('_', ' ')
    }
}
