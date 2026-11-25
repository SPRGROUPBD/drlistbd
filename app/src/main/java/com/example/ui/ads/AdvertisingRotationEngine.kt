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
     * Native Insertion Rule:
     * Display 300x100 ad after every 3 content cards (index 2, 5, 8, ...),
     * and if the section currently has only 1-2 items, display 1 native ad after the last item.
     */
    fun shouldInsertNativeAfterCard(contentCardIndex: Int, totalCount: Int = Int.MAX_VALUE): Boolean {
        if (contentCardIndex >= 2 && (contentCardIndex + 1) % 3 == 0) return true
        return totalCount in 1..2 && contentCardIndex == totalCount - 1
    }

    fun nativeSlotForCardIndex(contentCardIndex: Int): Int =
        if (contentCardIndex < 2) 0 else ((contentCardIndex + 1) / 3) - 1

    /**
     * Independent Round-Robin selection for Native 300x100 slots on:
     * Doctor List, Hospital List, Diagnostic Center List, Favorite List.
     * - Prioritizes campaigns matching the exact placement (or ALL/blank placement).
     * - If none match the specific placement, falls back to any eligible active NATIVE_300x100 campaign
     *   so native ads remain reliably visible in Popular Doctors, Hospitals, and Diagnostics.
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

        val allActiveNative = campaigns
            .filter { campaign ->
                AdPackageTypes.normalize(campaign.adType) == AdPackageTypes.NATIVE_300x100 &&
                    AdvertisingValidators.isCampaignEligible(campaign, nowMs)
            }
            .distinctBy { it.id }
            .sortedWith(compareBy<AdCampaignItem> { it.createdAt }.thenBy { it.advertiserId }.thenBy { it.id })

        if (allActiveNative.isEmpty()) return null

        val exactOrAll = allActiveNative.filter { campaign ->
            val p = AdPlacements.normalize(campaign.placement)
            p == normPlacement || p == AdPlacements.ALL_NATIVE_LISTS || p.isBlank()
        }
        val pool = if (exactOrAll.isNotEmpty()) exactOrAll else allActiveNative
        val idx = Math.floorMod(baseOffset.coerceAtLeast(0L) + slotIndex.coerceAtLeast(0).toLong(), pool.size.toLong()).toInt()
        return pool[idx]
    }

    /**
     * Collects ALL active bottom banners for the 10-second rotating bottom banner bar:
     * 1. Admin Promotional Bottom Banners (/advertising/bottomBanners) ordered by `order ASC, createdAt ASC`
     * 2. Eligible Advertiser Bottom Banner campaigns (`BOTTOM_BANNER_300x50`)
     * 3. Google AdMob Banner (when enabled in /advertising/admob — official test ad or Admin's live Banner Unit ID)
     */
    fun selectActiveBottomBanners(
        adminBanners: List<AdminBottomBannerItem>,
        campaigns: List<AdCampaignItem>,
        admobConfig: AdMobRemoteConfig = AdMobRemoteConfig(),
        nowMs: Long,
        settings: PlacementGlobalSettings = PlacementGlobalSettings()
    ): List<BottomBannerDisplayItem> {
        if (!settings.adsEnabled || !settings.bottomBannerEnabled) return emptyList()

        val result = mutableListOf<BottomBannerDisplayItem>()

        adminBanners
            .filter { AdvertisingValidators.isAdminBottomBannerEligible(it, nowMs) }
            .sortedWith(compareBy<AdminBottomBannerItem> { it.order }.thenBy { it.createdAt }.thenBy { it.id })
            .forEach { b ->
                result.add(
                    BottomBannerDisplayItem(
                        id = "admin_bb_${b.id}",
                        title = b.title,
                        subtitle = "",
                        imageUrl = b.imageUrl,
                        destinationUrl = b.destinationUrl,
                        isAdminPromo = true
                    )
                )
            }

        campaigns
            .filter { campaign ->
                val isBannerType = AdPackageTypes.normalize(campaign.adType) == AdPackageTypes.BOTTOM_BANNER_300x50
                val isBottomPlacement = AdPlacements.normalize(campaign.placement) == AdPlacements.BOTTOM_ALL_SCREENS
                (isBannerType || isBottomPlacement) &&
                    AdvertisingValidators.isCampaignEligible(campaign, nowMs)
            }
            .sortedWith(
                compareBy<AdCampaignItem> { it.priorityOverride }
                    .thenBy { if (it.activatedAt > 0L) it.activatedAt else Long.MAX_VALUE }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            .forEach { camp ->
                result.add(
                    BottomBannerDisplayItem(
                        id = "camp_bb_${camp.id}",
                        title = camp.title.ifBlank { camp.businessName },
                        subtitle = camp.businessName,
                        imageUrl = camp.imageUrl,
                        destinationUrl = camp.destinationUrl,
                        isAdminPromo = false,
                        sourceCampaign = camp
                    )
                )
            }

        admobConfig.activeBannerUnitIds().forEachIndexed { idx, unitId ->
            result.add(
                BottomBannerDisplayItem(
                    id = "admob_banner_${idx}_$unitId",
                    title = "AdMob Banner",
                    isAdMobBanner = true,
                    admobUnitId = unitId
                )
            )
        }

        return result
    }

    /**
     * Legacy single-campaign bottom banner selector retained for backwards compatibility.
     */
    fun selectBottomBanner(
        campaigns: List<AdCampaignItem>,
        nowMs: Long,
        settings: PlacementGlobalSettings = PlacementGlobalSettings()
    ): AdCampaignItem? {
        if (!settings.adsEnabled || !settings.bottomBannerEnabled) return null

        return campaigns
            .filter { campaign ->
                val isBannerType = AdPackageTypes.normalize(campaign.adType) == AdPackageTypes.BOTTOM_BANNER_300x50
                val isBottomPlacement = AdPlacements.normalize(campaign.placement) == AdPlacements.BOTTOM_ALL_SCREENS
                (isBannerType || isBottomPlacement) &&
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
     *   (supports both single target and multi-selected promotedTargets: multiple Doctors + Advertiser's Institution).
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
        val resolvedAdvSlides = eligibleAdvertiserRaw.flatMap { campaign ->
            val idxEntry = indexByCampaign[campaign.id]
            val targetRefs: List<PromotedTargetRef> = if (campaign.promotedTargets.isNotEmpty()) {
                campaign.promotedTargets
            } else {
                val recType = idxEntry?.targetRecordType?.takeIf { it.isNotBlank() } ?: campaign.targetRecordType
                val recId = idxEntry?.targetRecordId?.takeIf { it.isNotBlank() } ?: campaign.targetRecordId
                if (recType.isNotBlank() && recId.isNotBlank()) {
                    listOf(
                        PromotedTargetRef(
                            recordType = recType,
                            recordId = recId,
                            title = campaign.targetRecordTitle,
                            subtitle = campaign.targetRecordSubtitle,
                            photoUrl = campaign.targetRecordPhotoUrl
                        )
                    )
                } else emptyList()
            }

            val linkedSlides = targetRefs.mapIndexedNotNull { targetIdx, ref ->
                val target = AdvertisingValidators.validateAndResolveDirectoryRecord(
                    recordType = ref.recordType,
                    recordId = ref.recordId,
                    doctors = doctors,
                    hospitals = hospitals,
                    diagnostics = diagnostics
                ) ?: return@mapIndexedNotNull null

                val categoryBadge = when (target.recordType) {
                    SliderRecordTypes.DOCTOR -> "Sponsored Doctor"
                    SliderRecordTypes.HOSPITAL -> "Sponsored Hospital"
                    SliderRecordTypes.DIAGNOSTIC -> "Sponsored Diagnostic"
                    else -> "Sponsored"
                }

                ResolvedHomeSliderSlide(
                    slideId = "adv_slide_${campaign.id}_${target.recordType}_${target.recordId}_$targetIdx",
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

            if (linkedSlides.isNotEmpty()) {
                linkedSlides
            } else if (campaign.imageUrl.isNotBlank() || campaign.title.isNotBlank()) {
                listOf(
                    ResolvedHomeSliderSlide(
                        slideId = "adv_standalone_${campaign.id}",
                        isCustomAdminSlider = false,
                        campaignId = campaign.id,
                        advertiserId = campaign.advertiserId,
                        title = campaign.title.ifBlank { campaign.businessName },
                        subtitle = campaign.businessName,
                        description = campaign.description,
                        imageUrl = campaign.imageUrl,
                        categoryBadge = "Sponsored",
                        destinationUrl = campaign.destinationUrl
                    )
                )
            } else {
                emptyList()
            }
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
