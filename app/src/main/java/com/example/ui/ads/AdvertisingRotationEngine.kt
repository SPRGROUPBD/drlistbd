package com.example.ui.ads

import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.data.model.DoctorFullProfile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Complete Advertisement Rotation Engine supporting the 7 independent queues:
 * 1. home_slider         (Home Slider Advertiser Round-Robin + Admin Custom Slider order)
 * 2. doctor_native       (300x100 Native Ad after every 3 Doctor cards)
 * 3. hospital_native     (300x100 Native Ad after every 3 Hospital cards)
 * 4. diagnostic_native   (300x100 Native Ad after every 3 Diagnostic Center cards)
 * 5. favorite_native     (300x100 Native Ad after every 3 Favorite cards)
 * 6. bottom_banner       (300x50 Bottom Banner — earliest activated eligible first, 1 at a time)
 * 7. app_open_popup      (300x200 App Open Popup — once per app session, round-robin)
 */
object AdvertisingRotationEngine {
    const val QUEUE_HOME_SLIDER = "home_slider"
    const val QUEUE_DOCTOR_NATIVE = "doctor_native"
    const val QUEUE_HOSPITAL_NATIVE = "hospital_native"
    const val QUEUE_DIAGNOSTIC_NATIVE = "diagnostic_native"
    const val QUEUE_FAVORITE_NATIVE = "favorite_native"
    const val QUEUE_BOTTOM_BANNER = "bottom_banner"
    const val QUEUE_APP_OPEN_POPUP = "app_open_popup"

    val ALL_QUEUES: Set<String> = setOf(
        QUEUE_HOME_SLIDER,
        QUEUE_DOCTOR_NATIVE,
        QUEUE_HOSPITAL_NATIVE,
        QUEUE_DIAGNOSTIC_NATIVE,
        QUEUE_FAVORITE_NATIVE,
        QUEUE_BOTTOM_BANNER,
        QUEUE_APP_OPEN_POPUP
    )

    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun seedCounters(persisted: Map<String, Long>) {
        persisted.forEach { (queueKey, value) ->
            if (queueKey in ALL_QUEUES && value >= 0L) {
                counters.compute(queueKey) { _, existing ->
                    if (existing == null) AtomicLong(value)
                    else {
                        existing.updateAndGet { cur -> maxOf(cur, value) }
                        existing
                    }
                }
            }
        }
    }

    fun currentCounter(queueKey: String): Long =
        counters[queueKey]?.get() ?: 0L

    fun advanceCounter(queueKey: String): Long =
        counters.computeIfAbsent(queueKey) { AtomicLong(0L) }.incrementAndGet()

    fun snapshotCounters(): Map<String, Long> =
        counters.entries.associate { it.key to it.value.get() }

    fun nativeQueueForPlacement(placement: String): String = when (AdPlacements.normalize(placement)) {
        AdPlacements.DOCTOR_LIST -> QUEUE_DOCTOR_NATIVE
        AdPlacements.HOSPITAL_LIST -> QUEUE_HOSPITAL_NATIVE
        AdPlacements.DIAGNOSTIC_LIST -> QUEUE_DIAGNOSTIC_NATIVE
        AdPlacements.FAVORITE_LIST -> QUEUE_FAVORITE_NATIVE
        else -> QUEUE_DOCTOR_NATIVE
    }

    /**
     * Exact Native Insertion Rule (Section 5):
     * Display 300x100 ad after every 3 content cards -> index 2, 5, 8, ...
     */
    fun shouldInsertNativeAfterCard(contentCardIndex: Int): Boolean =
        contentCardIndex >= 2 && (contentCardIndex + 1) % 3 == 0

    fun nativeSlotForCardIndex(contentCardIndex: Int): Int =
        ((contentCardIndex + 1) / 3) - 1

    /**
     * Independent Round-Robin selection for Native 300x100 slots on:
     * Doctor List, Hospital List, Diagnostic Center List, Favorite List.
     * - Strictly 1 ad per slot
     * - Independent rotation per placement
     * - Verified + Approved + Active + Non-Expired only
     */
    fun selectNativeAdForSlot(
        campaigns: List<AdCampaignItem>,
        placementTarget: String,
        slotIndex: Int,
        baseOffset: Long,
        nowMs: Long,
        settings: PlacementGlobalSettings = PlacementGlobalSettings()
    ): AdCampaignItem? {
        if (!settings.adsEnabled) return null
        val normPlacement = AdPlacements.normalize(placementTarget)
        val enabledForPlacement = when (normPlacement) {
            AdPlacements.DOCTOR_LIST -> settings.doctorNativeEnabled
            AdPlacements.HOSPITAL_LIST -> settings.hospitalNativeEnabled
            AdPlacements.DIAGNOSTIC_LIST -> settings.diagnosticNativeEnabled
            AdPlacements.FAVORITE_LIST -> settings.favoriteNativeEnabled
            else -> false
        }
        if (!enabledForPlacement) return null

        val eligible = campaigns
            .filter { campaign ->
                AdPackageTypes.normalize(campaign.adType) == AdPackageTypes.NATIVE_300x100 &&
                    AdPlacements.normalize(campaign.placement) == normPlacement &&
                    AdvertisingValidators.isCampaignEligible(campaign, nowMs)
            }
            .sortedWith(compareBy<AdCampaignItem> { it.createdAt }.thenBy { it.advertiserId }.thenBy { it.id })

        if (eligible.isEmpty()) return null
        val idx = Math.floorMod(baseOffset.coerceAtLeast(0L) + slotIndex.coerceAtLeast(0).toLong(), eligible.size.toLong()).toInt()
        return eligible[idx]
    }

    /**
     * Bottom Banner 300x50 selection (Section 6):
     * - Only ONE banner visible at a time
     * - Only Bottom Banner package eligible
     * - Prioritizes earliest activated eligible campaign (`activatedAt ASC`),
     *   respecting optional manual admin `priorityOverride` if set.
     * - Automatically switches to next eligible when earliest expires or is paused.
     */
    fun selectBottomBanner(
        campaigns: List<AdCampaignItem>,
        nowMs: Long,
        settings: PlacementGlobalSettings = PlacementGlobalSettings()
    ): AdCampaignItem? {
        if (!settings.adsEnabled || !settings.bottomBannerEnabled) return null

        return campaigns
            .filter { campaign ->
                AdPackageTypes.normalize(campaign.adType) == AdPackageTypes.BOTTOM_BANNER_300x50 &&
                    AdvertisingValidators.isCampaignEligible(campaign, nowMs)
            }
            .sortedWith(
                compareBy<AdCampaignItem> { it.priorityOverride }
                    .thenBy { if (it.activatedAt > 0L) it.activatedAt else Long.MAX_VALUE }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            .firstOrNull()
    }

    /**
     * App Open Popup 300x200 selection (Section 7):
     * - Popup package ONLY
     * - Independent round-robin rotation across eligible advertiser popups
     */
    fun selectNextAppOpenPopup(
        campaigns: List<AdCampaignItem>,
        nowMs: Long,
        settings: PlacementGlobalSettings = PlacementGlobalSettings(),
        overrideCounter: Long? = null
    ): Pair<AdCampaignItem, Long>? {
        if (!settings.adsEnabled || !settings.appOpenPopupEnabled) return null

        val eligible = campaigns
            .filter { campaign ->
                AdPackageTypes.normalize(campaign.adType) == AdPackageTypes.APP_OPEN_POPUP_300x200 &&
                    AdvertisingValidators.isCampaignEligible(campaign, nowMs)
            }
            .sortedWith(compareBy<AdCampaignItem> { it.createdAt }.thenBy { it.advertiserId }.thenBy { it.id })

        if (eligible.isEmpty()) return null
        val atomic = counters.computeIfAbsent(QUEUE_APP_OPEN_POPUP) { AtomicLong(0L) }
        val index = overrideCounter ?: atomic.getAndIncrement()
        val chosen = eligible[Math.floorMod(index, eligible.size.toLong()).toInt()]
        return chosen to (index + 1L)
    }

    /**
     * Builds Home Slider Slides combining:
     * - Section 4A: Approved Advertiser Campaigns linked ONLY to existing Doctor / Hospital / Diagnostic records
     *   (rotates starting order cleanly using `QUEUE_HOME_SLIDER` round-robin counter).
     * - Section 4B: Admin Custom Sliders sorted by `order ASC`.
     * - Section 4C: Respects Admin toggle `settings.customSliderPosition` (BEFORE or AFTER advertiser sliders).
     */
    fun buildHomeSliderSlides(
        campaigns: List<AdCampaignItem>,
        advertiserSliderIndex: List<AdvertiserHomeSliderItem>,
        customSliders: List<AdminCustomSliderItem>,
        sliderSettings: HomeSliderSettings,
        placementSettings: PlacementGlobalSettings,
        doctors: List<DoctorFullProfile>,
        hospitals: List<HospitalEntity>,
        diagnostics: List<DiagnosticCenterEntity>,
        baseOffset: Long,
        nowMs: Long
    ): List<ResolvedHomeSliderSlide> {
        if (!placementSettings.adsEnabled || !placementSettings.homeSliderEnabled || !sliderSettings.enabled) {
            return emptyList()
        }

        val indexByCampaign = advertiserSliderIndex.associateBy { it.campaignId }

        val eligibleAdvertiserRaw = campaigns
            .filter { campaign ->
                if (AdPackageTypes.normalize(campaign.adType) != AdPackageTypes.HOME_SLIDER) return@filter false
                if (!AdvertisingValidators.isCampaignEligible(campaign, nowMs)) return@filter false
                val idxEntry = indexByCampaign[campaign.id]
                if (idxEntry != null && idxEntry.paused) return@filter false
                true
            }
            .sortedWith(
                compareBy<AdCampaignItem> { indexByCampaign[it.id]?.priority ?: 100 }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )

        // Resolve authentic existing Doctor / Hospital / Diagnostic details WITHOUT creating/editing any record
        val resolvedAdvSlides = eligibleAdvertiserRaw.mapNotNull { campaign ->
            val idxEntry = indexByCampaign[campaign.id]
            val recType = idxEntry?.targetRecordType?.takeIf { it.isNotBlank() } ?: campaign.targetRecordType
            val recId = idxEntry?.targetRecordId?.takeIf { it.isNotBlank() } ?: campaign.targetRecordId
            val target = AdvertisingValidators.validateAndResolveDirectoryRecord(
                recordType = recType,
                recordId = recId,
                doctors = doctors,
                hospitals = hospitals,
                diagnostics = diagnostics
            ) ?: return@mapNotNull null

            val categoryBadge = when (target.recordType) {
                SliderRecordTypes.DOCTOR -> "Sponsored Doctor"
                SliderRecordTypes.HOSPITAL -> "Sponsored Hospital"
                SliderRecordTypes.DIAGNOSTIC -> "Sponsored Diagnostic"
                else -> "Sponsored"
            }

            ResolvedHomeSliderSlide(
                slideId = "adv_slide_${campaign.id}",
                isCustomAdminSlider = false,
                campaignId = campaign.id,
                advertiserId = campaign.advertiserId,
                title = target.title,
                subtitle = target.subtitle,
                description = campaign.description.ifBlank { target.subtitle },
                imageUrl = target.photoUrl.ifBlank { campaign.imageUrl },
                categoryBadge = categoryBadge,
                targetRecordType = target.recordType,
                targetRecordId = target.recordId,
                destinationUrl = campaign.destinationUrl
            )
        }

        // Rotate advertiser slider starting position fairly (`Adv1 -> Adv2 -> Adv3`)
        val rotatedAdvSlides = if (resolvedAdvSlides.size > 1) {
            val shift = Math.floorMod(baseOffset.coerceAtLeast(0L), resolvedAdvSlides.size.toLong()).toInt()
            resolvedAdvSlides.drop(shift) + resolvedAdvSlides.take(shift)
        } else {
            resolvedAdvSlides
        }

        val resolvedCustomSlides = customSliders
            .filter { AdvertisingValidators.isCustomSliderEligible(it, nowMs) }
            .sortedWith(compareBy<AdminCustomSliderItem> { it.order }.thenBy { it.createdAt }.thenBy { it.id })
            .map { cs ->
                ResolvedHomeSliderSlide(
                    slideId = "custom_slide_${cs.id}",
                    isCustomAdminSlider = true,
                    campaignId = cs.id,
                    title = cs.title,
                    subtitle = "",
                    description = cs.description,
                    imageUrl = cs.imageUrl,
                    categoryBadge = "Featured",
                    destinationUrl = cs.destinationUrl
                )
            }

        return if (sliderSettings.customSliderPosition.equals("AFTER", ignoreCase = true)) {
            rotatedAdvSlides + resolvedCustomSlides
        } else {
            resolvedCustomSlides + rotatedAdvSlides
        }
    }

    internal fun resetForTesting() {
        counters.clear()
    }
}
