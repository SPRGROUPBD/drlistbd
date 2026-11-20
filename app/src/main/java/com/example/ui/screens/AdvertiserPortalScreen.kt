package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.RateCardSettingsEntity
import com.example.data.local.SponsorEntity
import com.example.data.local.SponsorPackageEntity
import com.example.data.remote.AdvertiserDashboardData
import com.example.data.remote.AdvertiserPaymentRecord
import com.example.data.remote.AdvertiserRemoteService
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.ads.SponsorPlacementPreviewCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Full-featured Direct Sponsor Advertiser Self-Service Portal inside the Android app:
 * - Only advertisers need accounts (patients browse the Doctor List app freely without login).
 * - Advertiser Registration & Login (Business Name, Owner, Mobile, Optional Email, Address, Password).
 * - Screen-Based Multi-Placement Booking & Dynamic Rate Card Engine (37 Placements, 9 Ad Formats,
 *   1/7/15/30/Custom durations, Bundle Discounts, Visual Placement Preview BEFORE booking).
 * - Legacy Package Support & Expired Advertisement Renewal with preserved statistics.
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
    modifier: Modifier = Modifier,
    cachedRateCards: List<PlacementRateCardEntity> = PlacementRateCardEngine.defaultRateCards(),
    cachedRateCardSettings: RateCardSettingsEntity = RateCardSettingsEntity(),
    cachedSponsors: List<SponsorEntity> = emptyList()
) {
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<AdvertiserDashboardData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Dashboard, 1=Create/Book Ad, 2=Payments
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
    val rateCards = remember(dashboard?.rateCards, cachedRateCards) {
        PlacementRateCardEngine.mergeWithDefaults(dashboard?.rateCards ?: cachedRateCards)
    }
    val rateCardSettings = dashboard?.rateCardSettings ?: cachedRateCardSettings
    val activePlacementCounts = remember(dashboard?.activePlacementCounts, cachedSponsors, rateCards, nowMs) {
        dashboard?.activePlacementCounts ?: rateCards.associate { c ->
            c.placementId to PlacementRateCardEngine.countActiveForPlacement(cachedSponsors, c.placementId, nowMs)
        }
    }
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
                        else tr(lang, "বিজ্ঞাপন বুকিং", "Book Ad"),
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
                rateCards = rateCards,
                rateCardSettings = rateCardSettings,
                activePlacementCounts = activePlacementCounts,
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
                            "Booking & payment submitted! It will activate automatically once Admin verifies payment."
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
                                    "Only businesses & institutions need accounts to book placements"
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("adv_new_booking_btn")
            ) {
                Icon(Icons.Rounded.AddCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr(lang, "নতুন প্লেসমেন্ট ও রেট কার্ড বুকিং", "Book New Screen Placement Ad"))
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
                            "এখনও কোনো বিজ্ঞাপন জমা দেওয়া হয়নি। উপরে ক্লিক করে প্লেসমেন্ট নির্বাচন করুন।",
                            "No advertisements yet. Tap above to pick screen placements & submit your first sponsor ad."
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
                val pids = remember(ad.placementIds) {
                    ad.placementIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
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
                                    text = "${ad.packageName.ifBlank { ad.placementType }} (${ad.widthPx}×${ad.heightPx}px • ${ad.durationDays}d • ৳${ad.priceBdt})",
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

                        if (pids.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pids.forEach { pid ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MedicalBlueLight
                                    ) {
                                        Text(
                                            text = pid,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MedicalBlueDark,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (ad.adSizeKey.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = MedicalTealLight) {
                                        Text(
                                            text = ad.adSizeKey,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MedicalTealDark,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (ad.bundleDiscountBdt > 0) {
                            Text(
                                text = tr(
                                    lang,
                                    "বান্ডেল ছাড়: -৳${ad.bundleDiscountBdt} (মোট পূর্বমূল্য ৳${ad.subtotalBdt})",
                                    "Bundle discount: -৳${ad.bundleDiscountBdt} (Subtotal ৳${ad.subtotalBdt})"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAvailable
                            )
                        }

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
    rateCards: List<PlacementRateCardEntity>,
    rateCardSettings: RateCardSettingsEntity,
    activePlacementCounts: Map<String, Int>,
    packages: List<SponsorPackageEntity>,
    paymentSettings: PaymentSettingsEntity,
    isOnline: Boolean,
    renewingAd: SponsorEntity?,
    onSubmitted: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bookingMode by remember(renewingAd) {
        val preferLegacy = renewingAd != null && renewingAd.placementIds.isBlank() && packages.isNotEmpty()
        mutableStateOf(if (preferLegacy) "LEGACY" else "PLACEMENT")
    }

    // Placement Booking State (9-step flow)
    val availableModules = remember(rateCards) {
        listOf("ALL") + rateCards.map { it.moduleName.uppercase() }.distinct()
    }
    var selectedModule by remember { mutableStateOf("ALL") }
    var selectedPlacementIds by remember(renewingAd, rateCards) {
        val fromRenew = renewingAd?.placementIds
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val initial = if (fromRenew.isNotEmpty()) fromRenew
        else listOfNotNull(rateCards.firstOrNull { it.status.equals("ACTIVE", true) }?.placementId)
        mutableStateOf(initial)
    }
    val selectedCards = remember(rateCards, selectedPlacementIds) {
        val pickSet = selectedPlacementIds.map { it.uppercase() }.toSet()
        rateCards.filter { it.placementId.uppercase() in pickSet && it.status.equals("ACTIVE", true) }
    }

    val allowedSizeSpecs = remember(selectedCards) {
        if (selectedCards.isEmpty()) PlacementRateCardEngine.STANDARD_AD_SIZES
        else {
            val sets = selectedCards.map { card ->
                PlacementRateCardEngine.allowedSizesForCard(card).map { it.key.uppercase() }.toSet()
            }
            val intersected = sets.reduce { acc, s -> acc.intersect(s) }
            val unionSet = sets.reduce { acc, s -> acc.union(s) }
            val finalKeys = if (intersected.isNotEmpty()) intersected else unionSet
            PlacementRateCardEngine.STANDARD_AD_SIZES.filter { it.key.uppercase() in finalKeys }
        }
    }

    var selectedSizeKey by remember(renewingAd) {
        mutableStateOf(renewingAd?.adSizeKey?.ifBlank { "300x200" } ?: "300x200")
    }
    LaunchedEffect(allowedSizeSpecs) {
        if (allowedSizeSpecs.isNotEmpty() && allowedSizeSpecs.none { it.key.equals(selectedSizeKey, true) }) {
            selectedSizeKey = allowedSizeSpecs.first().key
        }
    }
    var customWidthStr by remember(renewingAd) { mutableStateOf((renewingAd?.widthPx ?: 300).toString()) }
    var customHeightStr by remember(renewingAd) { mutableStateOf((renewingAd?.heightPx ?: 200).toString()) }

    var durationPreset by remember(renewingAd) {
        val d = renewingAd?.durationDays ?: 30
        mutableIntStateOf(if (d in listOf(1, 7, 15, 30)) d else -1)
    }
    var customDaysStr by remember(renewingAd) {
        val d = renewingAd?.durationDays ?: 10
        mutableStateOf(if (d in listOf(1, 7, 15, 30)) "10" else d.toString())
    }
    val effectiveDurationDays = remember(durationPreset, customDaysStr, rateCardSettings) {
        if (durationPreset > 0) durationPreset
        else if (rateCardSettings.allowCustomDuration) (customDaysStr.toIntOrNull() ?: 1).coerceIn(1, 3650)
        else 30
    }

    val quote = remember(selectedCards, selectedSizeKey, effectiveDurationDays, rateCardSettings) {
        PlacementRateCardEngine.calculateBookingQuote(
            selectedCards = selectedCards,
            adSizeKey = selectedSizeKey,
            durationDays = effectiveDurationDays,
            settings = rateCardSettings
        )
    }

    // Legacy Package Selection
    var selectedPkg by remember(packages, renewingAd) {
        mutableStateOf(packages.firstOrNull { it.id == renewingAd?.packageId } ?: packages.firstOrNull())
    }

    // Creative & Org fields
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
    var amountPaid by remember { mutableStateOf("") }
    var screenshotSource by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val expectedDue = if (bookingMode == "PLACEMENT") quote.totalBdt else (selectedPkg?.priceBdt ?: 0)
    LaunchedEffect(expectedDue) {
        if (expectedDue > 0) amountPaid = expectedDue.toString()
    }

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
            if (bookingMode == "LEGACY") {
                val pkg = selectedPkg
                if (pkg != null && !SponsorCampaignSelector.validateImageDimensions(opts.outWidth, opts.outHeight, pkg.widthPx, pkg.heightPx)) {
                    onError(
                        tr(
                            lang,
                            "ইমেজের মাপ (${opts.outWidth}×${opts.outHeight}) নির্বাচিত প্যাকেজের (${pkg.widthPx}×${pkg.heightPx}) সাথে সামঞ্জস্যপূর্ণ নয়।",
                            "Image dimensions (${opts.outWidth}×${opts.outHeight}px) do not match package (${pkg.widthPx}×${pkg.heightPx}px)."
                        )
                    )
                    return@runCatching
                }
            } else if (opts.outWidth < 120 || opts.outHeight < 40) {
                onError(tr(lang, "ইমেজটি খুবই ছোট (সর্বনিম্ন 120×40px প্রয়োজন)।", "Image resolution is too small (min 120×40px)."))
                return@runCatching
            }
            val mime = when {
                bytes[0] == 0x89.toByte() -> "image/png"
                bytes[0] == 'R'.code.toByte() -> "image/webp"
                else -> "image/jpeg"
            }
            imageSource = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            imageMetaLabel = "${opts.outWidth}×${opts.outHeight}px • ${bytes.size / 1024} KB"
        }.onFailure { onError(it.message ?: "Image error") }
    }

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

        if (packages.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = bookingMode == "PLACEMENT",
                        onClick = { bookingMode = "PLACEMENT" },
                        label = { Text(tr(lang, "স্ক্রিন প্লেসমেন্ট ও রেট কার্ড", "Placement & Rate Card")) },
                        modifier = Modifier.weight(1f).testTag("mode_placement_chip")
                    )
                    FilterChip(
                        selected = bookingMode == "LEGACY",
                        onClick = { bookingMode = "LEGACY" },
                        label = { Text(tr(lang, "প্যাকেজ বুকিং", "Legacy Packages")) },
                        modifier = Modifier.weight(1f).testTag("mode_legacy_chip")
                    )
                }
            }
        }

        if (bookingMode == "PLACEMENT") {
            // STEP 1 & 2: Module Filter & Multi-Placement Selector
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = tr(lang, "১ ও ২. মডিউল ও প্লেসমেন্ট নির্বাচন করুন (একাধিক বাছাই করা যাবে)", "1 & 2. Select Module & Screen Placement(s)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModules.forEach { mod ->
                                FilterChip(
                                    selected = selectedModule == mod,
                                    onClick = { selectedModule = mod },
                                    label = {
                                        Text(
                                            text = if (mod == "ALL") tr(lang, "সব মডিউল", "All Modules")
                                            else tr(lang, PlacementRateCardEngine.moduleLabelBn(mod), PlacementRateCardEngine.moduleLabelEn(mod)),
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }

                        val visibleCards = remember(rateCards, selectedModule) {
                            if (selectedModule == "ALL") rateCards
                            else rateCards.filter { it.moduleName.equals(selectedModule, true) }
                        }

                        visibleCards.forEach { card ->
                            val isActiveCard = card.status.equals("ACTIVE", true)
                            val used = activePlacementCounts[card.placementId] ?: 0
                            val maxSlots = card.maxActiveAds.coerceAtLeast(1)
                            val freeSlots = (maxSlots - used).coerceAtLeast(0)
                            val picked = selectedPlacementIds.any { it.equals(card.placementId, true) }
                            val cardRate = PlacementRateCardEngine.calculatePlacementRate(card, selectedSizeKey, effectiveDurationDays)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    !isActiveCard -> Color(0xFFF1F5F9)
                                    picked -> MedicalBlueLight.copy(alpha = 0.6f)
                                    else -> BackgroundIce
                                },
                                border = BorderStroke(if (picked) 2.dp else 1.dp, if (picked) MedicalBlue else OutlineSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isActiveCard) {
                                        val norm = card.placementId.uppercase()
                                        selectedPlacementIds = if (picked) {
                                            selectedPlacementIds.filterNot { it.equals(norm, true) }
                                        } else {
                                            selectedPlacementIds + norm
                                        }
                                    }
                                    .testTag("booking_placement_${card.placementId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = picked,
                                        enabled = isActiveCard,
                                        onCheckedChange = null
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = MedicalBlueDark) {
                                                Text(
                                                    text = card.placementId,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = tr(lang, card.placementNameBn, card.placementNameEn),
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActiveCard) TextPrimary else TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (isActiveCard) {
                                                tr(
                                                    lang,
                                                    "${card.moduleName} • স্লট: $used/$maxSlots (${freeSlots}টি খালি)",
                                                    "${card.moduleName} • Slots: $used/$maxSlots ($freeSlots free)"
                                                )
                                            } else {
                                                tr(lang, "অ্যাডমিন কর্তৃক নিষ্ক্রিয়", "Inactive / Closed")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (freeSlots == 0 && isActiveCard) Color(0xFFD97706) else TextSecondary
                                        )
                                    }
                                    Text(
                                        text = "৳$cardRate",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MedicalTealDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3 & 4: Ad Size & Duration Selector
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = tr(lang, "৩. বিজ্ঞাপনের সাইজ নির্বাচন করুন", "3. Select Allowed Advertisement Size"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allowedSizeSpecs.forEach { spec ->
                                val picked = selectedSizeKey.equals(spec.key, true)
                                FilterChip(
                                    selected = picked,
                                    onClick = { selectedSizeKey = spec.key },
                                    label = {
                                        Text("${spec.displayLabel} • ${tr(lang, spec.formatNameBn, spec.formatNameEn)}")
                                    },
                                    modifier = Modifier.testTag("size_chip_${spec.key}")
                                )
                            }
                        }

                        if (selectedSizeKey.equals("CUSTOM", true)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = customWidthStr,
                                    onValueChange = { customWidthStr = it.filter { ch -> ch.isDigit() } },
                                    label = { Text(tr(lang, "প্রস্থ (px)", "Custom Width (px)")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customHeightStr,
                                    onValueChange = { customHeightStr = it.filter { ch -> ch.isDigit() } },
                                    label = { Text(tr(lang, "উচ্চতা (px)", "Custom Height (px)")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = OutlineSoft)

                        Text(
                            text = tr(lang, "৪. বিজ্ঞাপনের মেয়াদ নির্বাচন করুন", "4. Select Duration"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                1 to tr(lang, "১ দিন", "1 Day"),
                                7 to tr(lang, "৭ দিন", "7 Days"),
                                15 to tr(lang, "১৫ দিন", "15 Days"),
                                30 to tr(lang, "৩০ দিন", "30 Days")
                            ).forEach { (d, lbl) ->
                                FilterChip(
                                    selected = durationPreset == d,
                                    onClick = { durationPreset = d },
                                    label = { Text(lbl) },
                                    modifier = Modifier.testTag("duration_chip_$d")
                                )
                            }
                            if (rateCardSettings.allowCustomDuration) {
                                FilterChip(
                                    selected = durationPreset == -1,
                                    onClick = { durationPreset = -1 },
                                    label = { Text(tr(lang, "কাস্টম দিন", "Custom Days")) }
                                )
                            }
                        }
                        if (durationPreset == -1 && rateCardSettings.allowCustomDuration) {
                            OutlinedTextField(
                                value = customDaysStr,
                                onValueChange = { customDaysStr = it.filter { ch -> ch.isDigit() } },
                                label = { Text(tr(lang, "মোট কত দিন চলবে *", "Number of Days *")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // STEP 5: Live Auto Price Breakdown + Bundle Discount
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.5.dp, MedicalTeal.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("rate_quote_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr(lang, "৫. স্বয়ংক্রিয় মূল্য হিসাব (${effectiveDurationDays} দিন • $selectedSizeKey)", "5. Automatic Price Calculation (${effectiveDurationDays}d • $selectedSizeKey)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedicalBlueDark
                        )
                        if (quote.lines.isEmpty()) {
                            Text(
                                text = tr(lang, "অন্তত ১টি প্লেসমেন্ট নির্বাচন করুন।", "Select at least one placement above."),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD97706)
                            )
                        } else {
                            quote.lines.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${tr(lang, line.placementNameBn, line.placementNameEn)} (${line.placementId})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "৳${line.priceBdt}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                            HorizontalDivider(color = OutlineSoft)
                            if (quote.discountBdt > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tr(lang, "মোট উপমূল্য", "Subtotal"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text("৳${quote.subtotalBdt}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        tr(lang, "মাল্টি-প্লেসমেন্ট ছাড় (${quote.discountPct}%)", "Multi-Placement Discount (${quote.discountPct}%)"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldAvailable
                                    )
                                    Text("-৳${quote.discountBdt}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = EmeraldAvailable)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = tr(lang, "সর্বমোট প্রদেয় মূল্য", "Total Payable Amount"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "৳${quote.totalBdt}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MedicalTealDark
                                )
                            }
                        }
                    }
                }
            }

            // STEP 6: Interactive Visual Screen Placement Preview BEFORE Booking
            item {
                SponsorPlacementPreviewCard(
                    lang = lang,
                    selectedCards = selectedCards,
                    adSizeKey = selectedSizeKey,
                    customW = customWidthStr.toIntOrNull() ?: 300,
                    customH = customHeightStr.toIntOrNull() ?: 200,
                    businessName = businessName,
                    title = title,
                    description = description,
                    imageSource = imageSource,
                    modifier = Modifier.testTag("placement_visual_preview")
                )
            }
        } else {
            // LEGACY DYNAMIC PACKAGE SELECTOR
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
                        packages.forEach { pkg ->
                            val picked = selectedPkg?.id == pkg.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (picked) MedicalBlueLight.copy(alpha = 0.6f) else BackgroundIce,
                                border = BorderStroke(if (picked) 2.dp else 1.dp, if (picked) MedicalBlue else OutlineSoft),
                                modifier = Modifier.fillMaxWidth().clickable { selectedPkg = pkg }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pkg.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            text = "${pkg.widthPx}×${pkg.heightPx}px • ${pkg.durationDays} ${tr(lang, "দিন", "days")} • ${pkg.placementType}",
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

        // STEP 7: Creative, Business & Contact Info
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "৬ ও ৭. প্রতিষ্ঠান, ছবি ও বিজ্ঞাপনের তথ্য", "6 & 7. Business Details, Image & URL"),
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
                        label = { Text(tr(lang, "প্রতিষ্ঠানের নাম *", "Organization / Business Name *")) },
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
                        label = { Text(tr(lang, "ওয়েবসাইট / ফেসবুক লিংক (https://...)", "Website or Social URL")) },
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
                }
            }
        }

        // STEP 8 & 9: Manual bKash / Nagad Payment & Submit Request
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "৮ ও ৯. পেমেন্ট তথ্য ও বুকিং সাবমিট", "8 & 9. Payment Verification & Submit"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

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

                    val canSubmit = !submitting && isOnline &&
                        ((bookingMode == "PLACEMENT" && selectedCards.isNotEmpty()) ||
                            (bookingMode == "LEGACY" && selectedPkg != null))

                    Button(
                        enabled = canSubmit,
                        onClick = {
                            scope.launch {
                                submitting = true
                                val res = if (bookingMode == "PLACEMENT") {
                                    AdvertiserRemoteService.submitPlacementBookingWithPayment(
                                        advertiser = session,
                                        selectedCards = selectedCards,
                                        adSizeKey = selectedSizeKey,
                                        customWidthPx = customWidthStr.toIntOrNull() ?: 300,
                                        customHeightPx = customHeightStr.toIntOrNull() ?: 200,
                                        durationDays = effectiveDurationDays,
                                        rateCardSettings = rateCardSettings,
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
                                } else {
                                    val pkg = selectedPkg ?: return@launch
                                    AdvertiserRemoteService.submitAdvertisementWithPayment(
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
                                }
                                submitting = false
                                res.onSuccess { onSubmitted() }
                                    .onFailure { onError(it.message ?: "Submission failed") }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("submit_booking_btn")
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (renewingAd != null) tr(lang, "রিনিউ ও পেমেন্ট জমা দিন", "Submit Renewal & Payment")
                                else tr(lang, "বুকিং রিকোয়েস্ট জমা দিন (৳$expectedDue)", "Submit Booking Request (৳$expectedDue)")
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
                        if (pay.placementIds.isNotBlank()) {
                            Text(
                                text = tr(lang, "প্লেসমেন্ট: ${pay.placementIds} (${pay.adSizeKey})", "Placements: ${pay.placementIds} (${pay.adSizeKey})"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MedicalBlueDark
                            )
                        }
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
