package com.example.ui.ads

import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.data.model.DoctorFullProfile

/**
 * Supported Advertisement Package Types (Section 1).
 */
object AdPackageTypes {
    const val HOME_SLIDER = "HOME_SLIDER"
    const val NATIVE_300x100 = "NATIVE_300x100"
    const val BOTTOM_BANNER_300x50 = "BOTTOM_BANNER_300x50"
    const val APP_OPEN_POPUP_300x200 = "APP_OPEN_POPUP_300x200"

    val ALL: Set<String> = setOf(
        HOME_SLIDER,
        NATIVE_300x100,
        BOTTOM_BANNER_300x50,
        APP_OPEN_POPUP_300x200
    )

    fun normalize(raw: String): String = when (raw.trim().uppercase()) {
        "HOME_SLIDER", "HOMESLIDER", "SLIDER" -> HOME_SLIDER
        "NATIVE_300X100", "NATIVE_300x100", "NATIVE" -> NATIVE_300x100
        "BOTTOM_BANNER_300X50", "BOTTOM_BANNER_300x50", "BANNER_300X50", "BOTTOM_BANNER", "BANNER" -> BOTTOM_BANNER_300x50
        "APP_OPEN_POPUP_300X200", "APP_OPEN_POPUP_300x200", "POPUP_300X200", "POPUP", "APP_OPEN_POPUP" -> APP_OPEN_POPUP_300x200
        else -> raw.trim().uppercase()
    }

    fun isSupported(adType: String): Boolean = normalize(adType) in ALL

    fun defaultPlacement(adType: String): String = when (normalize(adType)) {
        HOME_SLIDER -> AdPlacements.HOME_TOP_SLIDER
        NATIVE_300x100 -> AdPlacements.DOCTOR_LIST
        BOTTOM_BANNER_300x50 -> AdPlacements.BOTTOM_ALL_SCREENS
        APP_OPEN_POPUP_300x200 -> AdPlacements.APP_LAUNCH
        else -> AdPlacements.DOCTOR_LIST
    }

    fun dimensionsDp(adType: String): Pair<Int, Int> = when (normalize(adType)) {
        HOME_SLIDER -> 360 to 180
        NATIVE_300x100 -> 300 to 100
        BOTTOM_BANNER_300x50 -> 300 to 50
        APP_OPEN_POPUP_300x200 -> 300 to 200
        else -> 300 to 100
    }
}

object AdPlacements {
    const val HOME_TOP_SLIDER = "HOME_TOP_SLIDER"
    const val DOCTOR_LIST = "DOCTOR_LIST"
    const val HOSPITAL_LIST = "HOSPITAL_LIST"
    const val DIAGNOSTIC_LIST = "DIAGNOSTIC_LIST"
    const val FAVORITE_LIST = "FAVORITE_LIST"
    const val BOTTOM_ALL_SCREENS = "BOTTOM_ALL_SCREENS"
    const val APP_LAUNCH = "APP_LAUNCH"

    val NATIVE_PLACEMENTS: Set<String> = setOf(
        DOCTOR_LIST,
        HOSPITAL_LIST,
        DIAGNOSTIC_LIST,
        FAVORITE_LIST
    )

    fun normalize(raw: String): String = when (raw.trim().uppercase()) {
        "HOME_TOP_SLIDER", "HOME_SLIDER", "HOME" -> HOME_TOP_SLIDER
        "DOCTOR_LIST", "DOCTORS" -> DOCTOR_LIST
        "HOSPITAL_LIST", "HOSPITALS" -> HOSPITAL_LIST
        "DIAGNOSTIC_LIST", "DIAGNOSTIC_CENTER_LIST", "DIAGNOSTICS" -> DIAGNOSTIC_LIST
        "FAVORITE_LIST", "FAVORITES", "FAVOURITE_LIST" -> FAVORITE_LIST
        "BOTTOM_ALL_SCREENS", "BOTTOM_BANNER", "ALL_SCREENS" -> BOTTOM_ALL_SCREENS
        "APP_LAUNCH", "APP_OPEN" -> APP_LAUNCH
        else -> raw.trim().uppercase()
    }
}

object SliderRecordTypes {
    const val DOCTOR = "doctor"
    const val HOSPITAL = "hospital"
    const val DIAGNOSTIC = "diagnostic"

    val ALL: Set<String> = setOf(DOCTOR, HOSPITAL, DIAGNOSTIC)

    fun normalize(raw: String): String = when (raw.trim().lowercase()) {
        "doctor", "doctors", "doc" -> DOCTOR
        "hospital", "hospitals", "hosp" -> HOSPITAL
        "diagnostic", "diagnostic_center", "diagnostics", "diag" -> DIAGNOSTIC
        else -> raw.trim().lowercase()
    }

    fun isSupported(raw: String): Boolean = normalize(raw) in ALL
}

object AdStatuses {
    const val PENDING = "PENDING"
    const val VERIFIED = "VERIFIED"
    const val APPROVED = "APPROVED"
    const val ACTIVE = "ACTIVE"
    const val PAUSED = "PAUSED"
    const val INACTIVE = "INACTIVE"
    const val REJECTED = "REJECTED"
    const val EXPIRED = "EXPIRED"
}

data class AdPackageItem(
    val id: String,
    val name: String,
    val description: String = "",
    val price: Double = 0.0,
    val durationDays: Int = 30,
    val adType: String = AdPackageTypes.NATIVE_300x100,
    val placement: String = AdPlacements.DOCTOR_LIST,
    val maxCampaignDurationDays: Int = 365,
    val imageUrl: String = "",
    val status: String = AdStatuses.ACTIVE, // ACTIVE | INACTIVE | PAUSED
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isActive: Boolean get() = status.equals(AdStatuses.ACTIVE, ignoreCase = true)
}

data class AdPaymentMethod(
    val id: String,
    val methodName: String,
    val provider: String,
    val receivingNumber: String,
    val accountType: String = "Personal",
    val instructions: String = "",
    val status: String = AdStatuses.ACTIVE
) {
    val isActive: Boolean get() = status.equals(AdStatuses.ACTIVE, ignoreCase = true)
}

data class AdAdvertiserAccount(
    val id: String,
    val uid: String = "",
    val advertiserName: String,
    val businessName: String,
    val mobile: String,
    val email: String = "",
    val address: String = "",
    val category: String = "Healthcare",
    val photoUrl: String = "",
    val websiteUrl: String = "",
    val institutionId: String = "",
    val institutionType: String = "",
    val status: String = AdStatuses.ACTIVE,
    val authProvider: String = "PASSWORD",
    val createdAt: Long = 0L
) {
    val isActive: Boolean get() = status.equals(AdStatuses.ACTIVE, ignoreCase = true)
}

data class PromotedTargetRef(
    val recordType: String, // doctor | hospital | diagnostic
    val recordId: String,
    val title: String = "",
    val subtitle: String = "",
    val photoUrl: String = ""
)

data class AdCampaignItem(
    val id: String,
    val advertiserId: String,
    val advertiserName: String = "",
    val businessName: String = "",
    val contactMobile: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val destinationUrl: String = "",
    val packageId: String = "",
    val packageName: String = "",
    val adType: String = "",
    val placement: String = "",
    val price: Double = 0.0,
    val durationDays: Int = 0,
    // Existing record reference(s) for HOME_SLIDER (supports multi-select doctors + institution)
    val targetRecordType: String = "", // doctor | hospital | diagnostic
    val targetRecordId: String = "",
    val targetRecordTitle: String = "",
    val targetRecordSubtitle: String = "",
    val targetRecordPhotoUrl: String = "",
    val promotedTargets: List<PromotedTargetRef> = emptyList(),
    // Separated Verification & Approval Lifecycle
    val paymentStatus: String = AdStatuses.PENDING,  // PENDING | VERIFIED | REJECTED
    val approvalStatus: String = AdStatuses.PENDING, // PENDING | APPROVED | REJECTED
    val campaignStatus: String = AdStatuses.PENDING, // PENDING | ACTIVE | PAUSED | EXPIRED | REJECTED
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val activatedAt: Long = 0L,
    val priorityOverride: Int = Int.MAX_VALUE,
    val renewalOfCampaignId: String = "",
    val impressionsCount: Long = 0L,
    val clicksCount: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class AdPaymentRecord(
    val id: String,
    val campaignId: String,
    val advertiserId: String,
    val advertiserName: String = "",
    val businessName: String = "",
    val packageId: String = "",
    val packageName: String = "",
    val methodId: String = "",
    val methodName: String = "",
    val provider: String = "",
    val senderMobile: String = "",
    val receivingNumber: String = "",
    val trxId: String = "",
    val normalizedTrxId: String = "",
    val amount: Double = 0.0,
    val expectedAmount: Double = 0.0,
    val paymentDate: String = "",
    val screenshotUrl: String = "",
    val status: String = AdStatuses.PENDING, // PENDING | VERIFIED | REJECTED
    val rejectionReason: String = "",
    val verifiedBy: String = "",
    val verifiedAt: Long = 0L,
    val createdAt: Long = 0L
)

data class AdvertiserHomeSliderItem(
    val campaignId: String,
    val advertiserId: String,
    val targetRecordType: String, // doctor | hospital | diagnostic
    val targetRecordId: String,
    val approvalStatus: String = AdStatuses.PENDING,
    val paymentStatus: String = AdStatuses.PENDING,
    val campaignStatus: String = AdStatuses.PENDING,
    val paused: Boolean = false,
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val priority: Int = 100,
    val createdAt: Long = 0L
)

data class AdminCustomSliderItem(
    val id: String,
    val title: String,
    val description: String = "",
    val imageUrl: String,
    val destinationUrl: String = "",
    val order: Int = 1,
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val status: String = AdStatuses.ACTIVE, // ACTIVE | INACTIVE | PAUSED
    val createdAt: Long = 0L
)

data class HomeSliderSettings(
    val enabled: Boolean = true,
    val customSliderPosition: String = "BEFORE", // BEFORE | AFTER
    val autoSlideIntervalMs: Long = 4500L
)

data class PlacementGlobalSettings(
    val adsEnabled: Boolean = true,
    val homeSliderEnabled: Boolean = true,
    val doctorNativeEnabled: Boolean = true,
    val hospitalNativeEnabled: Boolean = true,
    val diagnosticNativeEnabled: Boolean = true,
    val favoriteNativeEnabled: Boolean = true,
    val bottomBannerEnabled: Boolean = true,
    val appOpenPopupEnabled: Boolean = true,
    val popupOncePerSession: Boolean = true,
    val nativeInterval: Int = 3
)

/**
 * Resolved slide item rendered in the Home Slider Carousel.
 * Either references an authentic existing Doctor/Hospital/Diagnostic record (Advertiser Slider)
 * or renders an Admin Custom Slider banner.
 */
data class ResolvedHomeSliderSlide(
    val slideId: String,
    val isCustomAdminSlider: Boolean,
    val campaignId: String = "",
    val advertiserId: String = "",
    val title: String,
    val subtitle: String,
    val description: String,
    val imageUrl: String,
    val categoryBadge: String,
    val targetRecordType: String = "", // doctor | hospital | diagnostic
    val targetRecordId: String = "",
    val destinationUrl: String = ""
)

data class AdvertisingSystemSnapshot(
    val packages: List<AdPackageItem> = emptyList(),
    val paymentMethods: List<AdPaymentMethod> = emptyList(),
    val campaigns: List<AdCampaignItem> = emptyList(),
    val advertiserHomeSliders: List<AdvertiserHomeSliderItem> = emptyList(),
    val adminCustomSliders: List<AdminCustomSliderItem> = emptyList(),
    val homeSliderSettings: HomeSliderSettings = HomeSliderSettings(),
    val placementSettings: PlacementGlobalSettings = PlacementGlobalSettings(),
    val rotationCounters: Map<String, Long> = emptyMap(),
    val syncedFromServer: Boolean = false
)

data class ValidatedRecordTarget(
    val recordType: String,
    val recordId: String,
    val title: String,
    val subtitle: String,
    val photoUrl: String
)

object AdvertisingValidators {
    fun normalizeTrxId(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "")

    fun isValidTrxId(raw: String): Boolean {
        val norm = normalizeTrxId(raw)
        return norm.length in 4..40
    }

    fun isValidUrlOrDataUri(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("https://") || u.startsWith("http://") || u.startsWith("data:image/")
    }

    fun isValidDestinationUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("https://") || u.startsWith("http://") || u.startsWith("tel:")
    }

    fun isExpired(expiryDate: Long, nowMs: Long): Boolean =
        expiryDate in 1L until nowMs

    fun effectiveCampaignStatus(campaign: AdCampaignItem, nowMs: Long): String {
        val base = campaign.campaignStatus.trim().uppercase()
        if (base == AdStatuses.REJECTED || base == AdStatuses.EXPIRED) return base
        if (campaign.approvalStatus.equals(AdStatuses.REJECTED, true) ||
            campaign.paymentStatus.equals(AdStatuses.REJECTED, true)
        ) {
            return AdStatuses.REJECTED
        }
        if (isExpired(campaign.expiryDate, nowMs)) return AdStatuses.EXPIRED
        if (base == AdStatuses.PAUSED) return AdStatuses.PAUSED
        if (campaign.paymentStatus.equals(AdStatuses.VERIFIED, true) &&
            campaign.approvalStatus.equals(AdStatuses.APPROVED, true) &&
            base == AdStatuses.ACTIVE
        ) {
            return AdStatuses.ACTIVE
        }
        return AdStatuses.PENDING
    }

    /**
     * Strict 7-point campaign eligibility check (Section 9, 12, 13):
     * - Payment MUST be VERIFIED
     * - Campaign approval MUST be APPROVED
     * - Campaign status MUST be ACTIVE (not PAUSED, REJECTED, EXPIRED, PENDING)
     * - Current time must be within [startDate, expiryDate]
     * - Never auto-reactivates expired campaigns
     */
    fun isCampaignEligible(campaign: AdCampaignItem, nowMs: Long): Boolean {
        if (!AdPackageTypes.isSupported(campaign.adType)) return false
        if (!campaign.paymentStatus.equals(AdStatuses.VERIFIED, true)) return false
        if (!campaign.approvalStatus.equals(AdStatuses.APPROVED, true)) return false
        if (!campaign.campaignStatus.equals(AdStatuses.ACTIVE, true)) return false
        if (campaign.startDate > 0L && nowMs < campaign.startDate) return false
        if (campaign.expiryDate > 0L && nowMs > campaign.expiryDate) return false

        return if (AdPackageTypes.normalize(campaign.adType) == AdPackageTypes.HOME_SLIDER) {
            campaign.promotedTargets.any {
                SliderRecordTypes.isSupported(it.recordType) && it.recordId.isNotBlank()
            } || (SliderRecordTypes.isSupported(campaign.targetRecordType) && campaign.targetRecordId.isNotBlank())
        } else {
            campaign.imageUrl.isNotBlank() || campaign.title.isNotBlank() || campaign.businessName.isNotBlank()
        }
    }

    fun isCustomSliderEligible(slider: AdminCustomSliderItem, nowMs: Long): Boolean {
        if (!slider.status.equals(AdStatuses.ACTIVE, true)) return false
        if (slider.imageUrl.isBlank()) return false
        if (slider.startDate > 0L && nowMs < slider.startDate) return false
        if (slider.expiryDate > 0L && nowMs > slider.expiryDate) return false
        return true
    }

    /**
     * Section 4A strict lookup: Verifies that a Home Slider campaign references a REAL existing
     * Doctor, Hospital, or Diagnostic Center record WITHOUT duplicating or editing it.
     */
    fun validateAndResolveDirectoryRecord(
        recordType: String,
        recordId: String,
        doctors: List<DoctorFullProfile>,
        hospitals: List<HospitalEntity>,
        diagnostics: List<DiagnosticCenterEntity>
    ): ValidatedRecordTarget? {
        val cleanType = SliderRecordTypes.normalize(recordType)
        val cleanId = recordId.trim()
        if (cleanId.isBlank() || cleanType !in SliderRecordTypes.ALL) return null

        return when (cleanType) {
            SliderRecordTypes.DOCTOR -> {
                val doc = doctors.firstOrNull { it.doctor.id == cleanId } ?: return null
                val title = doc.doctor.fullNameEn.ifBlank { doc.doctor.fullNameBn }
                val spec = doc.specialty?.nameEn?.ifBlank { doc.specialty.nameBn } ?: doc.doctor.subSpecialty
                val place = doc.doctor.currentHospital.ifBlank {
                    doc.chambers.firstOrNull()?.chamber?.facilityName.orEmpty()
                }
                ValidatedRecordTarget(
                    recordType = SliderRecordTypes.DOCTOR,
                    recordId = cleanId,
                    title = title,
                    subtitle = listOf(spec, doc.doctor.qualification, place).filter { it.isNotBlank() }.joinToString(" • "),
                    photoUrl = doc.doctor.photoUrl
                )
            }
            SliderRecordTypes.HOSPITAL -> {
                val hosp = hospitals.firstOrNull { it.id == cleanId } ?: return null
                val title = hosp.nameEn.ifBlank { hosp.nameBn }
                ValidatedRecordTarget(
                    recordType = SliderRecordTypes.HOSPITAL,
                    recordId = cleanId,
                    title = title,
                    subtitle = listOf(hosp.address, hosp.district).filter { it.isNotBlank() }.joinToString(", "),
                    photoUrl = hosp.imageUrl
                )
            }
            SliderRecordTypes.DIAGNOSTIC -> {
                val diag = diagnostics.firstOrNull { it.id == cleanId } ?: return null
                val title = diag.nameEn.ifBlank { diag.nameBn }
                ValidatedRecordTarget(
                    recordType = SliderRecordTypes.DIAGNOSTIC,
                    recordId = cleanId,
                    title = title,
                    subtitle = listOf(diag.address, diag.district).filter { it.isNotBlank() }.joinToString(", "),
                    photoUrl = diag.imageUrl
                )
            }
            else -> null
        }
    }
}
