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
import com.example.data.model.FacilityDoctorMatcher
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
                                    "${adTypeLabel(pkg.adType, lang)} • ${pkg.durationDays} ${tr(lang, "দিন", "days")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MedicalTealDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = tr(
                                        lang,
                                        "প্লেসমেন্ট: ${AdPlacements.displayLabel(pkg.placement, true)}",
                                        "Placement: ${AdPlacements.displayLabel(pkg.placement, false)}"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MedicalBlueDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

            if (campaign.promotedTargets.size > 1) {
                val names = campaign.promotedTargets.joinToString(", ") { it.title.ifBlank { it.recordId } }
                Text(
                    text = tr(
                        lang,
                        "নির্বাচিত স্লাইডার আইটেম (${campaign.promotedTargets.size}টি): $names",
                        "Promoted Slider Items (${campaign.promotedTargets.size}): $names"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MedicalBlueDark,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (campaign.targetRecordId.isNotBlank()) {
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
 * Home Slider Multi-Select Promotion Picker:
 * - Automatically finds the advertiser's own saved institution (Hospital / Diagnostic Center)
 *   and allows checking it to promote alongside its doctors.
 * - Automatically lists all saved Doctors belonging to the advertiser's institution with multi-select checkboxes.
 * - Includes an optional expandable "Browse All Directory Records" section so advertisers can also pick any saved doctor/facility.
 */
@Composable
internal fun HomeSliderExistingRecordPicker(
    lang: AppLang,
    session: AdvertiserSession,
    doctors: List<DoctorFullProfile>,
    hospitals: List<HospitalEntity>,
    diagnostics: List<DiagnosticCenterEntity>,
    selectedTargets: List<PromotedTargetRef>,
    onSelectionChange: (List<PromotedTargetRef>) -> Unit
) {
    val selectedKeys = remember(selectedTargets) {
        selectedTargets.map { "${SliderRecordTypes.normalize(it.recordType)}:${it.recordId}" }.toSet()
    }

    fun toggleTarget(ref: PromotedTargetRef) {
        val key = "${SliderRecordTypes.normalize(ref.recordType)}:${ref.recordId}"
        val next = if (key in selectedKeys) {
            selectedTargets.filterNot { "${SliderRecordTypes.normalize(it.recordType)}:${it.recordId}" == key }
        } else {
            selectedTargets + ref.copy(recordType = SliderRecordTypes.normalize(ref.recordType))
        }
        onSelectionChange(next)
    }

    // 1. Resolve Advertiser's own saved institution (Hospital or Diagnostic Center)
    val ownHospital = remember(session.institutionId, session.businessName, hospitals) {
        val instId = session.institutionId.trim()
        val bizNorm = session.businessName.trim().lowercase()
        hospitals.firstOrNull { instId.isNotBlank() && it.id == instId }
            ?: hospitals.firstOrNull { h ->
                bizNorm.isNotBlank() && (
                    h.nameEn.removePrefix("[DEMO] ").trim().equals(bizNorm, ignoreCase = true) ||
                        h.nameBn.removePrefix("[DEMO] ").trim().equals(bizNorm, ignoreCase = true) ||
                        h.nameEn.lowercase().contains(bizNorm) ||
                        bizNorm.contains(h.nameEn.removePrefix("[DEMO] ").trim().lowercase()).and(h.nameEn.length >= 4)
                    )
            }
    }
    val ownDiagnostic = remember(session.institutionId, session.businessName, diagnostics, ownHospital) {
        if (ownHospital != null) null
        else {
            val instId = session.institutionId.trim()
            val bizNorm = session.businessName.trim().lowercase()
            diagnostics.firstOrNull { instId.isNotBlank() && it.id == instId }
                ?: diagnostics.firstOrNull { d ->
                    bizNorm.isNotBlank() && (
                        d.nameEn.removePrefix("[DEMO] ").trim().equals(bizNorm, ignoreCase = true) ||
                            d.nameBn.removePrefix("[DEMO] ").trim().equals(bizNorm, ignoreCase = true) ||
                            d.nameEn.lowercase().contains(bizNorm) ||
                            bizNorm.contains(d.nameEn.removePrefix("[DEMO] ").trim().lowercase()).and(d.nameEn.length >= 4)
                        )
                }
        }
    }

    val ownInstitutionTarget = remember(ownHospital, ownDiagnostic) {
        when {
            ownHospital != null -> PromotedTargetRef(
                recordType = SliderRecordTypes.HOSPITAL,
                recordId = ownHospital.id,
                title = ownHospital.nameEn.ifBlank { ownHospital.nameBn },
                subtitle = listOf(ownHospital.address, ownHospital.district).filter { it.isNotBlank() }.joinToString(", "),
                photoUrl = ownHospital.imageUrl
            )
            ownDiagnostic != null -> PromotedTargetRef(
                recordType = SliderRecordTypes.DIAGNOSTIC,
                recordId = ownDiagnostic.id,
                title = ownDiagnostic.nameEn.ifBlank { ownDiagnostic.nameBn },
                subtitle = listOf(ownDiagnostic.address, ownDiagnostic.district).filter { it.isNotBlank() }.joinToString(", "),
                photoUrl = ownDiagnostic.imageUrl
            )
            else -> null
        }
    }

    // 2. Automatically filter all saved Doctors belonging to this advertiser's institution
    val institutionDoctors = remember(doctors, ownHospital, ownDiagnostic, session.businessName) {
        val primaryName = ownHospital?.nameEn?.removePrefix("[DEMO] ")
            ?: ownDiagnostic?.nameEn?.removePrefix("[DEMO] ")
            ?: session.businessName
        val altName = ownHospital?.nameBn?.removePrefix("[DEMO] ")
            ?: ownDiagnostic?.nameBn?.removePrefix("[DEMO] ")
            ?: ""
        if (primaryName.isBlank() && altName.isBlank()) emptyList()
        else doctors.filter { doc ->
            FacilityDoctorMatcher.matchesFacility(doc, primaryName, altName)
        }.map { d ->
            PromotedTargetRef(
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
    }

    var showOtherDirectory by remember { mutableStateOf(institutionDoctors.isEmpty() && ownInstitutionTarget == null) }
    var recordType by remember { mutableStateOf(SliderRecordTypes.DOCTOR) }
    var searchQuery by remember { mutableStateOf("") }

    val otherDirectoryItems = remember(recordType, searchQuery, doctors, hospitals, diagnostics) {
        val q = searchQuery.trim().lowercase()
        when (recordType) {
            SliderRecordTypes.DOCTOR -> doctors.filter { d ->
                q.isEmpty() ||
                    d.doctor.fullNameEn.lowercase().contains(q) ||
                    d.doctor.fullNameBn.lowercase().contains(q) ||
                    d.doctor.currentHospital.lowercase().contains(q) ||
                    d.chambers.any { it.chamber.facilityName.lowercase().contains(q) }
            }.take(20).map { d ->
                PromotedTargetRef(
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
            }.take(20).map { h ->
                PromotedTargetRef(
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
            }.take(20).map { dc ->
                PromotedTargetRef(
                    recordType = SliderRecordTypes.DIAGNOSTIC,
                    recordId = dc.id,
                    title = dc.nameEn.ifBlank { dc.nameBn },
                    subtitle = "${dc.address}, ${dc.district}",
                    photoUrl = dc.imageUrl
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MedicalBlueLight.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MedicalBlue.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = tr(
                    lang,
                    "হোম স্লাইডার: প্রতিষ্ঠান ও ডাক্তার নির্বাচন করুন (একাধিক) *",
                    "Home Slider: Select Your Institution & Doctors (Multi-Select) *"
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MedicalBlueDark
            )
            Text(
                text = tr(
                    lang,
                    "আপনার প্রতিষ্ঠানের সংরক্ষিত ডাক্তারবৃন্দ থেকে একাধিক ডাক্তার এবং আপনার প্রতিষ্ঠান একসাথে সিলেক্ট করে পেমেন্ট জমা দিন।",
                    "Select one or more saved doctors from your institution alongside your institution itself, then submit payment once."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            // Section A: Advertiser's Own Saved Institution
            if (ownInstitutionTarget != null) {
                val isInstChosen = "${ownInstitutionTarget.recordType}:${ownInstitutionTarget.recordId}" in selectedKeys
                Text(
                    text = tr(lang, "আপনার প্রতিষ্ঠান (স্লাইডারে যুক্ত করুন)", "Your Saved Institution (Add to Slider)"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MedicalTealDark
                )
                MultiSelectTargetRow(
                    target = ownInstitutionTarget,
                    badge = if (ownInstitutionTarget.recordType == SliderRecordTypes.HOSPITAL) tr(lang, "হাসপাতাল", "Hospital") else tr(lang, "ডায়াগনস্টিক", "Diagnostic"),
                    checked = isInstChosen,
                    onToggle = { toggleTarget(ownInstitutionTarget) }
                )
            }

            // Section B: Saved Doctors of This Advertiser's Institution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(
                        lang,
                        "আপনার প্রতিষ্ঠানের ডাক্তারবৃন্দ (${institutionDoctors.size})",
                        "Your Institution's Doctors (${institutionDoctors.size})"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MedicalBlueDark
                )
                if (institutionDoctors.isNotEmpty()) {
                    val allDocsSelected = institutionDoctors.all { "${it.recordType}:${it.recordId}" in selectedKeys }
                    TextButton(
                        onClick = {
                            if (allDocsSelected) {
                                val docIds = institutionDoctors.map { "${it.recordType}:${it.recordId}" }.toSet()
                                onSelectionChange(selectedTargets.filterNot { "${SliderRecordTypes.normalize(it.recordType)}:${it.recordId}" in docIds })
                            } else {
                                val existingKeys = selectedKeys
                                val toAdd = institutionDoctors.filterNot { "${it.recordType}:${it.recordId}" in existingKeys }
                                onSelectionChange(selectedTargets + toAdd)
                            }
                        }
                    ) {
                        Text(
                            text = if (allDocsSelected) tr(lang, "সব বাদ দিন", "Unselect All") else tr(lang, "সব ডাক্তার বাছুন", "Select All Doctors"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (institutionDoctors.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tr(
                            lang,
                            "আপনার প্রতিষ্ঠানের নামে এখনও কোনো ডাক্তার যুক্ত পাওয়া যায়নি। নিচ থেকে ডিরেক্টরির যেকোনো ডাক্তার বা প্রতিষ্ঠান সিলেক্ট করতে পারেন।",
                            "No doctors matched your institution name yet. Use the directory browser below to select saved doctors or facilities."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    institutionDoctors.forEach { docTarget ->
                        val isChosen = "${docTarget.recordType}:${docTarget.recordId}" in selectedKeys
                        MultiSelectTargetRow(
                            target = docTarget,
                            badge = tr(lang, "ডাক্তার", "Doctor"),
                            checked = isChosen,
                            onToggle = { toggleTarget(docTarget) }
                        )
                    }
                }
            }

            // Section C: Toggleable Full Directory Search (Doctors / Hospitals / Diagnostics)
            OutlinedButton(
                onClick = { showOtherDirectory = !showOtherDirectory },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (showOtherDirectory) Icons.Rounded.ExpandLess else Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (showOtherDirectory) {
                        tr(lang, "অন্যান্য ডিরেক্টরি তালিকা লুকান", "Hide Full Directory Browser")
                    } else {
                        tr(lang, "আরও ডাক্তার বা প্রতিষ্ঠান খুঁজুন", "Browse More Doctors / Facilities")
                    }
                )
            }

            if (showOtherDirectory) {
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(tr(lang, "নাম দিয়ে খুঁজুন...", "Search existing record by name...")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (otherDirectoryItems.isEmpty()) {
                    Text(
                        text = tr(lang, "কোনো মিল পাওয়া যায়নি।", "No matching saved records found."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        otherDirectoryItems.take(8).forEach { item ->
                            val isChosen = "${item.recordType}:${item.recordId}" in selectedKeys
                            val badgeText = when (item.recordType) {
                                SliderRecordTypes.DOCTOR -> tr(lang, "ডাক্তার", "Doctor")
                                SliderRecordTypes.HOSPITAL -> tr(lang, "হাসপাতাল", "Hospital")
                                else -> tr(lang, "ডায়াগনস্টিক", "Diagnostic")
                            }
                            MultiSelectTargetRow(
                                target = item,
                                badge = badgeText,
                                checked = isChosen,
                                onToggle = { toggleTarget(item) }
                            )
                        }
                    }
                }
            }

            // Section D: Live Summary of All Selected Items
            if (selectedTargets.isNotEmpty()) {
                val docCount = selectedTargets.count { it.recordType == SliderRecordTypes.DOCTOR }
                val facCount = selectedTargets.size - docCount
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, EmeraldAvailable),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(
                                    lang,
                                    "নির্বাচিত (${selectedTargets.size}টি): $docCount ডাক্তার, $facCount প্রতিষ্ঠান",
                                    "Selected (${selectedTargets.size}): $docCount Doctor(s), $facCount Institution(s)"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAvailable
                            )
                            TextButton(onClick = { onSelectionChange(emptyList()) }) {
                                Text(tr(lang, "মুছুন", "Clear"), style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                            }
                        }
                        selectedTargets.forEach { sel ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• ${sel.title.ifBlank { sel.recordId }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { toggleTarget(sel) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = RoseAlert, modifier = Modifier.size(16.dp))
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
private fun MultiSelectTargetRow(
    target: PromotedTargetRef,
    badge: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MedicalTealLight else SurfaceWhite,
        border = BorderStroke(
            if (checked) 1.5.dp else 1.dp,
            if (checked) MedicalTeal else OutlineSoft
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
            if (target.photoUrl.isNotBlank()) {
                val imgModel = remember(target.photoUrl) { decodeAdImageModel(target.photoUrl) }
                AsyncImage(
                    model = imgModel ?: target.photoUrl,
                    contentDescription = target.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundIce)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (target.subtitle.isNotBlank()) {
                    Text(
                        text = target.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (checked) MedicalTeal.copy(alpha = 0.2f) else MedicalBlueLight
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (checked) MedicalTealDark else MedicalBlueDark,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
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
