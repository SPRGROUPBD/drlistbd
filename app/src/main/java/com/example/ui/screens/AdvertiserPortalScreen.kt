package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * Full-featured Direct Sponsor Advertiser Self-Service Portal inside the Android app:
 * - Only advertisers need accounts (patients browse the Doctor List app freely without login).
 * - Advertiser Registration & Login (Business Name, Owner, Mobile, Optional Email, Address, Password).
 * - Advertiser Dashboard: Total/Active/Pending/Expired Ads, Total Views, Remaining Days, Individual Ad Stats, Payment History.
 * - Submit New Advertisement: Dynamic Package Picker (never hardcoded), Zero-Permission Photo Picker with dimension/size/format check,
 *   Live Ad Preview, Manual bKash/Nagad Payment Details & Screenshot upload.
 * - Renew Expired Advertisement: Pre-fills expired ad, allows new package/creative/URL/contact + fresh payment while preserving history.
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
    var activeTab by remember { mutableIntStateOf(0) } // 0=Dashboard, 1=Create/Renew Ad, 2=Payments
    var renewingAd by remember { mutableStateOf<SponsorEntity?>(null) }

    fun reloadDashboard() {
        if (!session.isLoggedIn) return
        scope.launch {
            isLoading = true
            AdvertiserRemoteService.fetchAdvertiserDashboard(session.id)
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

    val packages = dashboard?.packages?.takeIf { it.isNotEmpty() }
        ?: cachedPackages.filter { it.status.equals("ACTIVE", true) }
    val paySettings = dashboard?.paymentSettings ?: cachedPaymentSettings
    val myAds = dashboard?.myAds.orEmpty()
    val myPayments = dashboard?.myPayments.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .testTag("advertiser_portal_screen")
    ) {
        // Advertiser Account Header Strip
        Surface(
            color = MedicalBlueDark,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                onClick = { activeTab = 0; renewingAd = null },
                text = { Text(tr(lang, "ড্যাশবোর্ড", "Dashboard"), maxLines = 1) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Text(
                        if (renewingAd != null) tr(lang, "রিনিউ করুন", "Renew Ad")
                        else tr(lang, "নতুন বিজ্ঞাপন", "Submit Ad"),
                        maxLines = 1
                    )
                }
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
            0 -> AdvertiserDashboardTab(
                lang = lang,
                myAds = myAds,
                nowMs = nowMs,
                onCreateNew = { renewingAd = null; activeTab = 1 },
                onRenewAd = { expiredAd ->
                    renewingAd = expiredAd
                    activeTab = 1
                }
            )
            1 -> AdvertiserSubmitAdTab(
                lang = lang,
                session = session,
                packages = packages,
                paymentSettings = paySettings,
                isOnline = isOnline,
                renewingAd = renewingAd,
                onSubmitted = {
                    renewingAd = null
                    activeTab = 0
                    reloadDashboard()
                    onShowMessage(
                        tr(
                            lang,
                            "বিজ্ঞাপন ও পেমেন্ট জমা হয়েছে! অ্যাডমিন যাচাই করলে এটি স্বয়ংক্রিয়ভাবে চালু হবে।",
                            "Ad & payment submitted! It will activate automatically once Admin verifies payment."
                        )
                    )
                },
                onError = onShowMessage
            )
            2 -> AdvertiserPaymentsTab(lang = lang, payments = myPayments)
        }
    }
}

@Composable
private fun AdvertiserAuthView(
    lang: AppLang,
    isOnline: Boolean,
    onAuthSuccess: (AdvertiserSession) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Login / Register Fields
    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce),
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
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MedicalBlueLight,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Storefront, contentDescription = null, tint = MedicalBlueDark)
                            }
                        }
                        Column {
                            Text(
                                text = tr(lang, "অ্যাডভার্টাইজার অ্যাকাউন্ট", "Advertiser Account"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = tr(
                                    lang,
                                    "শুধুমাত্র বিজ্ঞাপনদাতাদের জন্য (রোগীদের কোনো অ্যাকাউন্ট লাগে না)",
                                    "Only businesses & institutions need accounts to manage ads"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isRegister,
                            onClick = { isRegister = false },
                            label = { Text(tr(lang, "লগইন", "Sign In")) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isRegister,
                            onClick = { isRegister = true },
                            label = { Text(tr(lang, "নতুন অ্যাকাউন্ট", "Register Business")) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isOnline) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr(
                                    lang,
                                    "লগইন বা রেজিস্ট্রেশনের জন্য ইন্টারনেট সংযোগ প্রয়োজন।",
                                    "Internet connection is required to sign in or register."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (isRegister) {
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text(tr(lang, "প্রতিষ্ঠান / ব্যবসার নাম *", "Business / Institution Name *")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("adv_reg_business")
                        )
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text(tr(lang, "মালিক / প্রতিনিধির নাম *", "Owner Name *")) },
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
                            keyboardType = if (isRegister) KeyboardType.Phone else KeyboardType.Text
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("adv_mobile_input")
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(tr(lang, "পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) *", "Password (min 6 chars) *")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("adv_password_input")
                    )

                    Button(
                        enabled = !busy && isOnline,
                        onClick = {
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
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("adv_auth_submit_btn")
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (isRegister) tr(lang, "অ্যাকাউন্ট তৈরি করুন", "Create Advertiser Account")
                                else tr(lang, "লগইন করুন", "Sign In")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvertiserDashboardTab(
    lang: AppLang,
    myAds: List<SponsorEntity>,
    nowMs: Long,
    onCreateNew: () -> Unit,
    onRenewAd: (SponsorEntity) -> Unit
) {
    val activeAds = myAds.count { SponsorCampaignSelector.isCampaignValid(it, nowMs) }
    val pendingAds = myAds.count {
        it.status.equals(SponsorCampaignSelector.STATUS_PENDING_APPROVAL, true) ||
            it.status.equals(SponsorCampaignSelector.STATUS_PENDING_PAYMENT, true)
    }
    val expiredAds = myAds.count { SponsorCampaignSelector.isCampaignExpired(it, nowMs) }
    val totalViews = myAds.sumOf { it.totalViews }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // KPI Summary Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AdvStatCard(tr(lang, "মোট বিজ্ঞাপন", "Total Ads"), myAds.size.toString(), MedicalBlue, Modifier.weight(1f))
                    AdvStatCard(tr(lang, "চলমান", "Active"), activeAds.toString(), EmeraldAvailable, Modifier.weight(1f))
                    AdvStatCard(tr(lang, "অপেক্ষমান", "Pending"), pendingAds.toString(), Color(0xFFD97706), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AdvStatCard(tr(lang, "মেয়াদোত্তীর্ণ", "Expired"), expiredAds.toString(), Color(0xFFDC2626), Modifier.weight(1f))
                    AdvStatCard(tr(lang, "মোট ভিউ", "Total Views"), totalViews.toString(), MedicalTealDark, Modifier.weight(1f))
                }
            }
        }

        item {
            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.AddCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr(lang, "নতুন বিজ্ঞাপন সাবমিট করুন", "Submit New Advertisement"))
            }
        }

        if (myAds.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(
                            lang,
                            "এখনও কোনো বিজ্ঞাপন জমা দেওয়া হয়নি। উপরে ক্লিক করে আপনার প্রথম প্যাকেজ নির্বাচন করুন।",
                            "No advertisements yet. Tap above to pick a package and submit your first sponsor ad."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(myAds, key = { it.id }) { ad ->
                val isExpired = SponsorCampaignSelector.isCampaignExpired(ad, nowMs)
                val isActive = SponsorCampaignSelector.isCampaignValid(ad, nowMs)
                val remDays = SponsorCampaignSelector.remainingDays(ad, nowMs)
                val statusLabel = when {
                    isActive -> tr(lang, "সক্রিয়", "ACTIVE")
                    isExpired -> tr(lang, "মেয়াদোত্তীর্ণ", "EXPIRED")
                    ad.status.equals("PAUSED", true) -> tr(lang, "বিরতি", "PAUSED")
                    ad.status.equals("REJECTED", true) -> tr(lang, "প্রত্যাখ্যাত", "REJECTED")
                    else -> tr(lang, "যাচাইয়ের অপেক্ষায়", "PENDING VERIFICATION")
                }
                val statusColor = when {
                    isActive -> EmeraldAvailable
                    isExpired || ad.status.equals("REJECTED", true) -> Color(0xFFDC2626)
                    else -> Color(0xFFD97706)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
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
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${ad.packageName.ifBlank { ad.placementType }} (${ad.widthPx}x${ad.heightPx}px • ${ad.durationDays}d • ৳${ad.priceBdt})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Individual Ad Statistics & Dates
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tr(lang, "মোট ভিউ: ${ad.totalViews} (ইউনিক: ${ad.uniqueViews})", "Views: ${ad.totalViews} (Unique: ${ad.uniqueViews})"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MedicalBlueDark
                            )
                            Text(
                                text = if (isActive) tr(lang, "বাকি: $remDays দিন", "$remDays days left") else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MedicalTealDark
                            )
                        }

                        if (ad.activatedAt > 0L) {
                            Text(
                                text = tr(
                                    lang,
                                    "শুরু: ${SponsorCampaignSelector.formatDateDisplay(ad.activatedAt)} • মেয়াদ শেষ: ${SponsorCampaignSelector.formatDateDisplay(ad.expiryAt)}",
                                    "Start: ${SponsorCampaignSelector.formatDateDisplay(ad.activatedAt)} • Expiry: ${SponsorCampaignSelector.formatDateDisplay(ad.expiryAt)}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        if (ad.renewalOfAdId.isNotBlank()) {
                            Text(
                                text = tr(lang, "রিনিউ করা বিজ্ঞাপন (পূর্ববর্তী ID: ${ad.renewalOfAdId})", "Renewed from previous ad: ${ad.renewalOfAdId}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MedicalBlue
                            )
                        }

                        if (isExpired) {
                            Button(
                                onClick = { onRenewAd(ad) },
                                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "বিজ্ঞাপন রিনিউ করুন", "Renew Advertisement"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvertiserSubmitAdTab(
    lang: AppLang,
    session: AdvertiserSession,
    packages: List<SponsorPackageEntity>,
    paymentSettings: PaymentSettingsEntity,
    isOnline: Boolean,
    renewingAd: SponsorEntity?,
    onSubmitted: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPkg by remember(packages, renewingAd) {
        mutableStateOf(
            packages.firstOrNull { it.id == renewingAd?.packageId } ?: packages.firstOrNull()
        )
    }
    var title by remember(renewingAd) { mutableStateOf(renewingAd?.promoTitle.orEmpty()) }
    var businessName by remember(renewingAd, session) {
        mutableStateOf(renewingAd?.name?.ifBlank { session.businessName } ?: session.businessName)
    }
    var description by remember(renewingAd) { mutableStateOf(renewingAd?.description.orEmpty()) }
    var websiteUrl by remember(renewingAd) { mutableStateOf(renewingAd?.websiteUrl.orEmpty()) }
    var contactNumber by remember(renewingAd, session) {
        mutableStateOf(renewingAd?.contactNumber?.ifBlank { session.mobile } ?: session.mobile)
    }
    var imageSource by remember(renewingAd) { mutableStateOf(renewingAd?.bannerImageUrl.orEmpty()) }
    var imageMetaLabel by remember { mutableStateOf("") }

    // Manual Payment Form
    var paymentMethod by remember { mutableStateOf("BKASH") }
    var senderMobile by remember { mutableStateOf(session.mobile) }
    var trxId by remember { mutableStateOf("") }
    var amountPaid by remember(selectedPkg) { mutableStateOf(selectedPkg?.priceBdt?.toString().orEmpty()) }
    var screenshotSource by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    // Zero-Permission Android Photo Picker for Ad Image
    val adImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not read image")
            if (bytes.size > SponsorCampaignSelector.MAX_IMAGE_BYTES) {
                onError(tr(lang, "ইমেজের সাইজ সর্বোচ্চ ৩ এমবি হতে হবে।", "Image file must be 3 MB or smaller."))
                return@runCatching
            }
            if (!SponsorCampaignSelector.isSupportedImageBytes(bytes)) {
                onError(tr(lang, "শুধুমাত্র PNG, JPEG বা WebP ইমেজ সমর্থিত।", "Only PNG, JPEG, or WebP images are supported."))
                return@runCatching
            }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val pkg = selectedPkg
            if (pkg != null && !SponsorCampaignSelector.validateImageDimensions(opts.outWidth, opts.outHeight, pkg.widthPx, pkg.heightPx)) {
                onError(
                    tr(
                        lang,
                        "ইমেজের মাপ (${opts.outWidth}x${opts.outHeight}) নির্বাচিত প্যাকেজের (${pkg.widthPx}x${pkg.heightPx}) সাথে সামঞ্জস্যপূর্ণ নয়।",
                        "Image dimensions (${opts.outWidth}x${opts.outHeight}px) do not match package (${pkg.widthPx}x${pkg.heightPx}px)."
                    )
                )
                return@runCatching
            }
            val mime = when {
                bytes[0] == 0x89.toByte() -> "image/png"
                bytes[0] == 'R'.code.toByte() -> "image/webp"
                else -> "image/jpeg"
            }
            imageSource = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            imageMetaLabel = "${opts.outWidth}x${opts.outHeight}px • ${bytes.size / 1024} KB"
        }.onFailure { onError(it.message ?: "Image error") }
    }

    // Zero-Permission Photo Picker for Optional Payment Screenshot
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            if (bytes.size > SponsorCampaignSelector.MAX_IMAGE_BYTES || !SponsorCampaignSelector.isSupportedImageBytes(bytes)) {
                onError(tr(lang, "সঠিক পেমেন্ট স্ক্রিনশট নির্বাচন করুন (সর্বোচ্চ ৩ এমবি)।", "Select a valid screenshot under 3 MB."))
                return@runCatching
            }
            val mime = if (bytes[0] == 0x89.toByte()) "image/png" else "image/jpeg"
            screenshotSource = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (renewingAd != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MedicalBlueLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(
                            lang,
                            "রিনিউ মোড: পূর্ববর্তী বিজ্ঞাপন (${renewingAd.promoTitle}) এর ইতিহাস ও পরিসংখ্যান সংরক্ষিত থাকবে।",
                            "Renewal Mode: Previous ad history & stats (${renewingAd.totalViews} views) will be preserved."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MedicalBlueDark,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 1. Dynamic Package Selector (Never hardcoded!)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "১. বিজ্ঞাপন প্যাকেজ নির্বাচন করুন", "1. Select Advertising Package"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (packages.isEmpty()) {
                        Text(
                            text = tr(lang, "অ্যাডমিন এখনও কোনো সক্রিয় প্যাকেজ যুক্ত করেননি।", "No active packages available yet."),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        packages.forEach { pkg ->
                            val picked = selectedPkg?.id == pkg.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (picked) MedicalBlueLight.copy(alpha = 0.6f) else BackgroundIce,
                                border = BorderStroke(if (picked) 2.dp else 1.dp, if (picked) MedicalBlue else OutlineSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPkg = pkg
                                        amountPaid = pkg.priceBdt.toString()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pkg.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            text = "${pkg.widthPx}x${pkg.heightPx}px • ${pkg.durationDays} ${tr(lang, "দিন", "days")} • ${pkg.placementType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Text(
                                        text = "৳${pkg.priceBdt}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MedicalTealDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Advertisement Details & Image Upload + Live Preview
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "২. বিজ্ঞাপনের তথ্য ও ইমেজ", "2. Advertisement Creative & Details"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                adImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "ইমেজ আপলোড", "Upload Ad Image"))
                        }
                    }
                    if (imageMetaLabel.isNotBlank()) {
                        Text(imageMetaLabel, style = MaterialTheme.typography.labelSmall, color = EmeraldAvailable)
                    }
                    OutlinedTextField(
                        value = if (imageSource.startsWith("data:")) "" else imageSource,
                        onValueChange = { imageSource = it },
                        label = { Text(tr(lang, "অথবা ইমেজ URL (https://...)", "Or HTTPS Image URL")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(tr(lang, "বিজ্ঞাপনের শিরোনাম *", "Advertisement Title *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text(tr(lang, "প্রতিষ্ঠানের নাম *", "Business Name *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(tr(lang, "বিজ্ঞাপনের বিবরণ", "Advertisement Description")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = websiteUrl,
                        onValueChange = { websiteUrl = it },
                        label = { Text(tr(lang, "ওয়েবসাইট / ফেসবুক লিংক (https://...)", "Website or Social Media URL")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactNumber,
                        onValueChange = { contactNumber = it },
                        label = { Text(tr(lang, "যোগাযোগের নম্বর", "Contact Number")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live Advertisement Preview
                    if (title.isNotBlank() || imageSource.isNotBlank()) {
                        Text(
                            text = tr(lang, "লাইভ প্রিভিউ", "Live Preview"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedicalBlueDark
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BackgroundIce,
                            border = BorderStroke(1.dp, MedicalTeal),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(businessName.ifBlank { "Business Name" }, fontWeight = FontWeight.Bold)
                                    Text("Sponsored", style = MaterialTheme.typography.labelSmall, color = MedicalTealDark, fontWeight = FontWeight.Bold)
                                }
                                if (imageSource.isNotBlank()) {
                                    AsyncImage(
                                        model = imageSource,
                                        contentDescription = "Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (description.isNotBlank()) {
                                    Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Manual bKash / Nagad Payment Instructions & Submission
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "৩. ম্যানুয়াল পেমেন্ট (বিকাশ / নগদ)", "3. Manual Payment Verification"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Admin configured bKash & Nagad numbers
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFDF2F8),
                        border = BorderStroke(1.dp, Color(0xFFFBCFE8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (paymentSettings.bkashNumber.isNotBlank()) {
                                Text(
                                    text = "bKash (${paymentSettings.bkashAccountType}): ${paymentSettings.bkashNumber}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9D174D)
                                )
                            }
                            if (paymentSettings.nagadNumber.isNotBlank()) {
                                Text(
                                    text = "Nagad (${paymentSettings.nagadAccountType}): ${paymentSettings.nagadNumber}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9A3412)
                                )
                            }
                            val inst = tr(lang, paymentSettings.instructionsBn, paymentSettings.instructionsEn)
                            if (inst.isNotBlank()) {
                                Text(inst, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = paymentMethod == "BKASH",
                            onClick = { paymentMethod = "BKASH" },
                            label = { Text("bKash") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentMethod == "NAGAD",
                            onClick = { paymentMethod = "NAGAD" },
                            label = { Text("Nagad") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = senderMobile,
                        onValueChange = { senderMobile = it },
                        label = { Text(tr(lang, "সেন্ডার মোবাইল নম্বর *", "Sender Mobile Number *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = trxId,
                        onValueChange = { trxId = it },
                        label = { Text(tr(lang, "ট্রানজেকশন আইডি (TrxID) *", "Transaction ID (TrxID) *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountPaid,
                        onValueChange = { amountPaid = it.filter { ch -> ch.isDigit() } },
                        label = { Text(tr(lang, "প্রদত্ত টাকা (BDT) *", "Amount Paid (BDT) *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = {
                            screenshotPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Rounded.ReceiptLong, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (screenshotSource.isNotBlank()) tr(lang, "পেমেন্ট স্ক্রিনশট যুক্ত হয়েছে ✓", "Payment Screenshot Attached ✓")
                            else tr(lang, "পেমেন্ট স্ক্রিনশট (ঐচ্ছিক)", "Attach Payment Screenshot (Optional)")
                        )
                    }

                    Button(
                        enabled = !submitting && isOnline && selectedPkg != null,
                        onClick = {
                            val pkg = selectedPkg ?: return@Button
                            scope.launch {
                                submitting = true
                                val res = AdvertiserRemoteService.submitAdvertisementWithPayment(
                                    advertiser = session,
                                    pkg = pkg,
                                    title = title,
                                    businessName = businessName,
                                    description = description,
                                    imageSource = imageSource,
                                    logoSource = "",
                                    websiteUrl = websiteUrl,
                                    contactNumber = contactNumber,
                                    paymentMethod = paymentMethod,
                                    senderMobile = senderMobile,
                                    transactionId = trxId,
                                    amountPaidBdt = amountPaid.toIntOrNull() ?: 0,
                                    screenshotSource = screenshotSource,
                                    renewalOfAdId = renewingAd?.id.orEmpty()
                                )
                                submitting = false
                                res.onSuccess { onSubmitted() }
                                    .onFailure { onError(it.message ?: "Submission failed") }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (renewingAd != null) tr(lang, "রিনিউ ও পেমেন্ট জমা দিন", "Submit Renewal & Payment")
                                else tr(lang, "যাচাইয়ের জন্য জমা দিন", "Submit for Payment Verification")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvertiserPaymentsTab(
    lang: AppLang,
    payments: List<AdvertiserPaymentRecord>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (payments.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(lang, "কোনো পেমেন্ট ইতিহাস পাওয়া যায়নি।", "No payment history found."),
                        color = TextSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(payments, key = { it.id }) { pay ->
                val color = when (pay.status.uppercase()) {
                    "VERIFIED" -> EmeraldAvailable
                    "REJECTED" -> Color(0xFFDC2626)
                    else -> Color(0xFFD97706)
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${pay.paymentMethod} • ৳${pay.amountBdt}", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(pay.status, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Text("TrxID: ${pay.transactionId} • ${pay.senderMobile}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            "${pay.packageName} • ${SponsorCampaignSelector.formatDateDisplay(pay.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        if (pay.adminNote.isNotBlank()) {
                            Text("Admin: ${pay.adminNote}", style = MaterialTheme.typography.labelSmall, color = MedicalBlueDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvStatCard(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        }
    }
}
