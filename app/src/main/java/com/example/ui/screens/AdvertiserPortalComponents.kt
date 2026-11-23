package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AdvertiserSession
import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.data.model.DoctorFullProfile
import com.example.data.remote.AdvertisingRemoteService
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.ads.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun AdvertiserOverviewTab(
    lang: AppLang,
    myCampaigns: List<AdCampaignItem>,
    myPayments: List<AdPaymentRecord>,
    activePackages: List<AdPackageItem>,
    nowMs: Long,
    onSelectPackageToCreate: (AdPackageItem) -> Unit,
    onRenewCampaign: (AdCampaignItem) -> Unit
) {
    val activeCount = myCampaigns.count { AdvertisingValidators.isCampaignEligible(it, nowMs) }
    val pendingCount = myCampaigns.count {
        AdvertisingValidators.effectiveCampaignStatus(it, nowMs) == AdStatuses.PENDING
    }
    val expiredCount = myCampaigns.count {
        AdvertisingValidators.effectiveCampaignStatus(it, nowMs) == AdStatuses.EXPIRED
    }
    val totalImpressions = myCampaigns.sumOf { it.impressionsCount }
    val totalClicks = myCampaigns.sumOf { it.clicksCount }
    val verifiedSpend = myPayments.filter { it.status.equals(AdStatuses.VERIFIED, true) }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PortalMetricCard(tr(lang, "মোট ক্যাম্পেইন", "Campaigns"), myCampaigns.size.toString(), MedicalBlue, Modifier.weight(1f))
                    PortalMetricCard(tr(lang, "চলমান", "Active"), activeCount.toString(), EmeraldAvailable, Modifier.weight(1f))
                    PortalMetricCard(tr(lang, "অপেক্ষমান", "Pending"), pendingCount.toString(), Color(0xFFD97706), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PortalMetricCard(tr(lang, "মেয়াদ শেষ", "Expired"), expiredCount.toString(), RoseAlert, Modifier.weight(1f))
                    PortalMetricCard(tr(lang, "ইমপ্রেশন", "Impressions"), totalImpressions.toString(), MedicalTealDark, Modifier.weight(1f))
                    PortalMetricCard(tr(lang, "ক্লিক", "Clicks"), totalClicks.toString(), MedicalBlueDark, Modifier.weight(1f))
                }
                if (verifiedSpend > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MedicalTealLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr(lang, "যাচাইকৃত মোট খরচ: ৳${verifiedSpend.toInt()}", "Total Verified Spend: ৳${verifiedSpend.toInt()}"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MedicalTealDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // Available Active Packages (Section 1 & 11)
        item {
            Text(
                text = tr(lang, "উপলব্ধ বিজ্ঞাপন প্যাকেজ (${activePackages.size})", "Available Active Packages (${activePackages.size})"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (activePackages.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(lang, "বর্তমানে কোনো সক্রিয় প্যাকেজ নেই।", "No active advertisement packages available right now."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(activePackages, key = { it.id }) { pkg ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (pkg.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = pkg.imageUrl,
                                    contentDescription = pkg.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BackgroundIce)
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MedicalBlueLight,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = MedicalBlueDark)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pkg.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    "${adTypeLabel(pkg.adType, lang)} • ${pkg.durationDays} ${tr(lang, "দিন", "days")} (Max ${pkg.maxCampaignDurationDays}d)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MedicalTealDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "৳${pkg.price.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MedicalBlueDark
                            )
                        }
                        if (pkg.description.isNotBlank()) {
                            Text(pkg.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Button(
                            onClick = { onSelectPackageToCreate(pkg) },
                            colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("adv_select_pkg_${pkg.id}")
                        ) {
                            Icon(Icons.Rounded.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "এই প্যাকেজে বিজ্ঞাপন দিন", "Select & Create Ad"))
                        }
                    }
                }
            }
        }

        // My Advertisements
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = tr(lang, "আমার বিজ্ঞাপনসমূহ (${myCampaigns.size})", "My Advertisements (${myCampaigns.size})"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (myCampaigns.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(lang, "আপনি এখনও কোনো বিজ্ঞাপন জমা দেননি।", "You have not submitted any campaigns yet."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(myCampaigns, key = { it.id }) { camp ->
                CampaignStatusCard(
                    lang = lang,
                    campaign = camp,
                    nowMs = nowMs,
                    onRenew = { onRenewCampaign(camp) }
                )
            }
        }
    }
}

@Composable
private fun CampaignStatusCard(
    lang: AppLang,
    campaign: AdCampaignItem,
    nowMs: Long,
    onRenew: () -> Unit
) {
    val effective = AdvertisingValidators.effectiveCampaignStatus(campaign, nowMs)
    val badgeColor = when (effective) {
        AdStatuses.ACTIVE -> EmeraldAvailable
        AdStatuses.EXPIRED, AdStatuses.REJECTED -> RoseAlert
        AdStatuses.PAUSED -> Color(0xFF6366F1)
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
                        text = campaign.title.ifBlank { campaign.targetRecordTitle.ifBlank { campaign.packageName } },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${adTypeLabel(campaign.adType, lang)} • ${campaign.placement} • ৳${campaign.price.toInt()} (${campaign.durationDays}d)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Surface(color = badgeColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = effective,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (campaign.targetRecordId.isNotBlank()) {
                Text(
                    text = tr(
                        lang,
                        "লিংককৃত রেকর্ড (${campaign.targetRecordType}): ${campaign.targetRecordTitle}",
                        "Linked ${campaign.targetRecordType}: ${campaign.targetRecordTitle}"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MedicalBlueDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tr(lang, "পেমেন্ট: ${campaign.paymentStatus}", "Payment: ${campaign.paymentStatus}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (campaign.paymentStatus == AdStatuses.VERIFIED) EmeraldAvailable else Color(0xFFD97706),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = tr(lang, "অনুমোদন: ${campaign.approvalStatus}", "Approval: ${campaign.approvalStatus}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (campaign.approvalStatus == AdStatuses.APPROVED) EmeraldAvailable else Color(0xFFD97706),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tr(lang, "ইমপ্রেশন: ${campaign.impressionsCount} • ক্লিক: ${campaign.clicksCount}", "Impressions: ${campaign.impressionsCount} • Clicks: ${campaign.clicksCount}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MedicalBlueDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (campaign.startDate > 0L && campaign.expiryDate > 0L) {
                Text(
                    text = "Start: ${SponsorCampaignSelector.formatDateDisplay(campaign.startDate)} • Expiry: ${SponsorCampaignSelector.formatDateDisplay(campaign.expiryDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            if (effective == AdStatuses.EXPIRED) {
                Button(
                    onClick = onRenew,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(lang, "পুনরায় পেমেন্ট করে রিনিউ করুন", "Renew Campaign (New Payment Required)"))
                }
            }
        }
    }
}

@Composable
internal fun AdvertiserPaymentsHistoryTab(
    lang: AppLang,
    payments: List<AdPaymentRecord>
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
                        text = tr(lang, "কোনো পেমেন্ট রেকর্ড পাওয়া যায়নি।", "No payment submissions found."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(payments, key = { it.id }) { pay ->
                val color = when (pay.status) {
                    AdStatuses.VERIFIED -> EmeraldAvailable
                    AdStatuses.REJECTED -> RoseAlert
                    else -> Color(0xFFD97706)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${pay.provider.ifBlank { pay.methodName }} • ৳${pay.amount.toInt()}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = pay.status,
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text("TRX ID: ${pay.trxId}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MedicalBlueDark)
                        Text(
                            text = "${pay.packageName} • Sender: ${pay.senderMobile} -> To: ${pay.receivingNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        if (pay.paymentDate.isNotBlank()) {
                            Text("Date: ${pay.paymentDate}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        if (pay.rejectionReason.isNotBlank()) {
                            Text("Reason: ${pay.rejectionReason}", style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdvertiserProfileSettingsTab(
    lang: AppLang,
    session: AdvertiserSession,
    isOnline: Boolean,
    onProfileUpdated: (AdvertiserSession) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var ownerName by remember(session.ownerName) { mutableStateOf(session.ownerName) }
    var businessName by remember(session.businessName) { mutableStateOf(session.businessName) }
    var mobile by remember(session.mobile) { mutableStateOf(session.mobile) }
    var email by remember(session.email) { mutableStateOf(session.email) }
    var address by remember(session.address) { mutableStateOf(session.address) }
    var category by remember(session.category) { mutableStateOf(session.category.ifBlank { "Healthcare" }) }
    var websiteUrl by remember(session.websiteUrl) { mutableStateOf(session.websiteUrl) }
    var saving by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "প্রোফাইল ও অ্যাকাউন্ট সেটিংস", "Account & Profile Settings"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text(tr(lang, "প্রতিষ্ঠান / ব্যবসার নাম *", "Business / Organization Name *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text(tr(lang, "অ্যাডভার্টাইজার নাম *", "Advertiser Name *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text(tr(lang, "মোবাইল নম্বর *", "Contact Mobile *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(tr(lang, "ইমেইল", "Email")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(tr(lang, "ঠিকানা", "Address")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(tr(lang, "ব্যবসার ধরন", "Category")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = websiteUrl,
                        onValueChange = { websiteUrl = it },
                        label = { Text(tr(lang, "ওয়েবসাইট URL", "Website URL")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !saving && isOnline,
                        onClick = {
                            scope.launch {
                                saving = true
                                val res = AdvertisingRemoteService.updateAdvertiserProfile(
                                    advertiserId = session.id,
                                    ownerName = ownerName,
                                    businessName = businessName,
                                    mobile = mobile,
                                    email = email,
                                    address = address,
                                    category = category,
                                    websiteUrl = websiteUrl
                                )
                                saving = false
                                res.onSuccess { updated ->
                                    onProfileUpdated(updated)
                                    onShowMessage(tr(lang, "প্রোফাইল আপডেট হয়েছে।", "Profile updated."))
                                }.onFailure {
                                    onShowMessage(it.message ?: "Update failed")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(tr(lang, "প্রোফাইল সংরক্ষণ করুন", "Save Profile Changes"))
                    }
                }
            }
        }
    }
}

/**
 * Special Home Slider Record Picker (Section 4A):
 * Allows Advertiser to select ONLY an existing Doctor, Hospital, or Diagnostic Center record:
 * Select Category -> Search by Name -> Choose Existing Record -> Preview Card -> Confirm Reference ID.
 */
@Composable
internal fun HomeSliderExistingRecordPicker(
    lang: AppLang,
    doctors: List<DoctorFullProfile>,
    hospitals: List<HospitalEntity>,
    diagnostics: List<DiagnosticCenterEntity>,
    selectedRecordType: String,
    selectedRecordId: String,
    onSelectRecord: (ValidatedRecordTarget) -> Unit
) {
    var recordType by remember(selectedRecordType) {
        mutableStateOf(
            selectedRecordType.takeIf { SliderRecordTypes.isSupported(it) } ?: SliderRecordTypes.DOCTOR
        )
    }
    var searchQuery by remember { mutableStateOf("") }

    val matchingItems = remember(recordType, searchQuery, doctors, hospitals, diagnostics) {
        val q = searchQuery.trim().lowercase()
        when (recordType) {
            SliderRecordTypes.DOCTOR -> doctors.filter { d ->
                q.isEmpty() ||
                    d.doctor.fullNameEn.lowercase().contains(q) ||
                    d.doctor.fullNameBn.lowercase().contains(q) ||
                    d.doctor.currentHospital.lowercase().contains(q)
            }.take(25).map { d ->
                ValidatedRecordTarget(
                    recordType = SliderRecordTypes.DOCTOR,
                    recordId = d.doctor.id,
                    title = d.doctor.fullNameEn.ifBlank { d.doctor.fullNameBn },
                    subtitle = listOfNotNull(
                        d.specialty?.nameEn?.ifBlank { d.specialty.nameBn },
                        d.doctor.qualification,
                        d.doctor.currentHospital
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    photoUrl = d.doctor.photoUrl
                )
            }
            SliderRecordTypes.HOSPITAL -> hospitals.filter { h ->
                q.isEmpty() ||
                    h.nameEn.lowercase().contains(q) ||
                    h.nameBn.lowercase().contains(q) ||
                    h.district.lowercase().contains(q)
            }.take(25).map { h ->
                ValidatedRecordTarget(
                    recordType = SliderRecordTypes.HOSPITAL,
                    recordId = h.id,
                    title = h.nameEn.ifBlank { h.nameBn },
                    subtitle = "${h.address}, ${h.district}",
                    photoUrl = h.imageUrl
                )
            }
            else -> diagnostics.filter { dc ->
                q.isEmpty() ||
                    dc.nameEn.lowercase().contains(q) ||
                    dc.nameBn.lowercase().contains(q) ||
                    dc.district.lowercase().contains(q)
            }.take(25).map { dc ->
                ValidatedRecordTarget(
                    recordType = SliderRecordTypes.DIAGNOSTIC,
                    recordId = dc.id,
                    title = dc.nameEn.ifBlank { dc.nameBn },
                    subtitle = "${dc.address}, ${dc.district}",
                    photoUrl = dc.imageUrl
                )
            }
        }
    }

    val selectedTarget = remember(selectedRecordType, selectedRecordId, doctors, hospitals, diagnostics) {
        AdvertisingValidators.validateAndResolveDirectoryRecord(
            recordType = selectedRecordType,
            recordId = selectedRecordId,
            doctors = doctors,
            hospitals = hospitals,
            diagnostics = diagnostics
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MedicalBlueLight.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = tr(lang, "হোম স্লাইডার: বিদ্যমান রেকর্ড নির্বাচন করুন *", "Home Slider: Select Existing Record *"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MedicalBlueDark
            )
            Text(
                text = tr(
                    lang,
                    "কেবলমাত্র ডাটাবেজে সংরক্ষিত ডাক্তার, হাসপাতাল বা ডায়াগনস্টিক সেন্টার নির্বাচন করা যাবে।",
                    "You can promote an existing saved Doctor, Hospital, or Diagnostic Center directly (no duplicate record is created)."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            // 1. Select Category
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    SliderRecordTypes.DOCTOR to tr(lang, "ডাক্তার", "Doctor"),
                    SliderRecordTypes.HOSPITAL to tr(lang, "হাসপাতাল", "Hospital"),
                    SliderRecordTypes.DIAGNOSTIC to tr(lang, "ডায়াগনস্টিক", "Diagnostic")
                ).forEach { (typeKey, label) ->
                    FilterChip(
                        selected = recordType == typeKey,
                        onClick = { recordType = typeKey },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. Search by Name
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(tr(lang, "নাম দিয়ে খুঁজুন...", "Search existing record by name...")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Matching Existing Records
            if (matchingItems.isEmpty()) {
                Text(
                    text = tr(lang, "কোনো মিল পাওয়া যায়নি।", "No matching saved records found."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    matchingItems.take(6).forEach { item ->
                        val isChosen = item.recordType == selectedRecordType && item.recordId == selectedRecordId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChosen) MedicalTealLight else SurfaceWhite,
                            border = BorderStroke(
                                if (isChosen) 1.5.dp else 1.dp,
                                if (isChosen) MedicalTeal else OutlineSoft
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRecord(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (item.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = item.photoUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BackgroundIce)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isChosen) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MedicalTealDark)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Live Slider Card Preview
            if (selectedTarget != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, EmeraldAvailable),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tr(lang, "নির্বাচিত রেকর্ড প্রিভিউ (ID: ${selectedTarget.recordId})", "Confirmed Record Preview (ID: ${selectedTarget.recordId})"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldAvailable
                        )
                        Text(selectedTarget.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (selectedTarget.subtitle.isNotBlank()) {
                            Text(selectedTarget.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PortalMetricCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun adTypeLabel(adType: String, lang: AppLang): String = when (AdPackageTypes.normalize(adType)) {
    AdPackageTypes.HOME_SLIDER -> tr(lang, "হোম স্লাইডার", "Home Slider")
    AdPackageTypes.NATIVE_300x100 -> tr(lang, "নেটিভ অ্যাড (300×100)", "Native Ad 300×100")
    AdPackageTypes.BOTTOM_BANNER_300x50 -> tr(lang, "বটম ব্যানার (300×50)", "Bottom Banner 300×50")
    AdPackageTypes.APP_OPEN_POPUP_300x200 -> tr(lang, "অ্যাপ ওপেন পপআপ (300×200)", "App Open Popup 300×200")
    else -> adType
}
