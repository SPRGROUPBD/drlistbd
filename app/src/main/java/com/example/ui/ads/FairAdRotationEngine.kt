package com.example.ui.ads

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Allowed strict placement types in the Dynamic Package & Advanced Advertiser Management System.
 * Strictly ONLY these three placement types are permitted:
 * 1. banner_300x50  -> 300x50 Fixed Bottom Banner
 * 2. native_300x200 -> 300x200 Native Banner (after every 3 content cards on 4 supported list screens)
 * 3. popup_300x200  -> 300x200 App Launch Popup ONLY
 */
object StrictPlacementTypes {
    const val BANNER_300x50 = "banner_300x50"
    const val NATIVE_300x200 = "native_300x200"
    const val POPUP_300x200 = "popup_300x200"

    val ALLOWED_SET: Set<String> = setOf(BANNER_300x50, NATIVE_300x200, POPUP_300x200)

    fun isAllowed(placementType: String): Boolean = placementType.trim() in ALLOWED_SET

    fun defaultDimensions(placementType: String): Pair<Int, Int> = when (placementType.trim()) {
        BANNER_300x50 -> 300 to 50
        NATIVE_300x200 -> 300 to 200
        POPUP_300x200 -> 300 to 200
        else -> 0 to 0
    }
}

object AdTargetScreens {
    const val HOME = "home"
    const val DOCTOR_LIST = "doctorList"
    const val DOCTOR_DETAILS = "doctorDetails"
    const val HOSPITAL_LIST = "hospitalList"
    const val HOSPITAL_DETAILS = "hospitalDetails"
    const val DIAGNOSTIC_LIST = "diagnosticList"
    const val DIAGNOSTIC_DETAILS = "diagnosticDetails"
    const val CHAMBER_SCHEDULE = "chamberSchedule"
    const val OTHER_SCREENS = "otherScreens"
    const val APP_LAUNCH = "appLaunch"

    /** ONLY these 4 screens support 300x200 Native Banner insertion after every 3 content cards. */
    val NATIVE_SUPPORTED_SCREENS: Set<String> = setOf(
        DOCTOR_LIST,
        HOSPITAL_LIST,
        DIAGNOSTIC_LIST,
        CHAMBER_SCHEDULE
    )

    val BANNER_SUPPORTED_SCREENS: Set<String> = setOf(
        HOME,
        DOCTOR_LIST,
        DOCTOR_DETAILS,
        HOSPITAL_LIST,
        HOSPITAL_DETAILS,
        DIAGNOSTIC_LIST,
        DIAGNOSTIC_DETAILS,
        CHAMBER_SCHEDULE,
        OTHER_SCREENS
    )
}

data class DynamicAdPackage(
    val packageId: String,
    val packageName: String,
    val placementType: String,
    val width: Int,
    val height: Int,
    val durationDays: Int,
    val price: Double,
    val description: String = "",
    val status: String = "active", // active | inactive
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isActive: Boolean get() = status.equals("active", ignoreCase = true)
}

data class DynamicPurchaseSnapshot(
    val purchaseId: String = "",
    val packageId: String = "",
    val packageName: String = "",
    val placementType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val price: Double = 0.0,
    val durationDays: Int = 0,
    val startDate: Long = 0L,
    val endDate: Long = 0L
)

data class AdScreenTargeting(
    val bannerAllScreens: Boolean = false,
    val bannerScreens: Set<String> = emptySet(),
    val nativeScreens: Set<String> = emptySet(),
    val popupAppLaunch: Boolean = false
) {
    fun allowsBannerOn(screenId: String): Boolean {
        if (screenId !in AdTargetScreens.BANNER_SUPPORTED_SCREENS) return false
        return bannerAllScreens || screenId in bannerScreens
    }

    fun allowsNativeOn(screenId: String): Boolean {
        if (screenId !in AdTargetScreens.NATIVE_SUPPORTED_SCREENS) return false
        return screenId in nativeScreens
    }

    fun allowsPopupOnLaunch(): Boolean = popupAppLaunch
}

data class ManagedAdvertiserCampaign(
    val advertiserId: String,
    val advertisementId: String,
    val advertiserName: String,
    val companyName: String,
    val logoUrl: String = "",
    val imageUrl: String = "",
    val title: String = "",
    val description: String = "",
    val destinationUrl: String = "",
    val placementType: String = "",
    val width: Int = 300,
    val height: Int = 50,
    val advertiserStatus: String = "Pending", // Pending | Active | Inactive | Expired | Rejected
    val adStatus: String = "Pending",         // Pending | Active | Inactive | Expired | Rejected
    val paymentStatus: String = "Unpaid",     // Unpaid | Partial | Paid
    val packageId: String = "",
    val packageValid: Boolean = true,
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val createdAt: Long = 0L,
    val targeting: AdScreenTargeting = AdScreenTargeting()
)

/**
 * Production Equal & Independent Fair Ad Rotation Engine.
 *
 * Guarantees:
 * - Strict separation of placement types (banner_300x50, native_300x200, popup_300x200).
 * - Independent rotation counters per `(placementType, screenId)` key.
 * - Equal sequential round-robin (`C1 -> C2 -> C3 -> C1 -> C2 -> C3`) with stable deterministic ordering
 *   (`createdAt ASC`, then `advertiserId ASC`) — NO random or weighted priority bias.
 * - Thread-safe atomic counter progression with external persistence sync (RTDB + local DataStore)
 *   so rotation survives app restarts and concurrent callers never skip or repeat unfairly.
 */
object FairAdRotationEngine {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun counterKey(placementType: String, screenId: String): String =
        "${placementType.trim()}:${screenId.trim()}"

    fun seedCounters(persisted: Map<String, Long>) {
        persisted.forEach { (key, value) ->
            if (value >= 0L) {
                counters.compute(key) { _, existing ->
                    if (existing == null) AtomicLong(value)
                    else {
                        existing.updateAndGet { cur -> maxOf(cur, value) }
                        existing
                    }
                }
            }
        }
    }

    fun currentCounter(placementType: String, screenId: String): Long =
        counters[counterKey(placementType, screenId)]?.get() ?: 0L

    fun snapshotCounters(): Map<String, Long> =
        counters.entries.associate { it.key to it.value.get() }

    /**
     * Complete 9-point display validation gate before an advertisement is allowed to participate in rotation.
     */
    fun isCampaignEligibleForDisplay(
        campaign: ManagedAdvertiserCampaign,
        requestedPlacement: String,
        screenId: String,
        nowMs: Long
    ): Boolean {
        if (!StrictPlacementTypes.isAllowed(requestedPlacement)) return false
        if (!campaign.placementType.equals(requestedPlacement, ignoreCase = false)) return false
        if (!campaign.advertiserStatus.equals("Active", ignoreCase = true)) return false
        if (!campaign.adStatus.equals("Active", ignoreCase = true)) return false
        if (!campaign.paymentStatus.equals("Paid", ignoreCase = true)) return false
        if (!campaign.packageValid) return false
        if (campaign.startDate > 0L && nowMs < campaign.startDate) return false
        if (campaign.endDate > 0L && nowMs > campaign.endDate) return false
        if (campaign.imageUrl.isBlank()) return false
        if (campaign.destinationUrl.isNotBlank() && !isValidDestinationUrl(campaign.destinationUrl)) return false

        return when (requestedPlacement) {
            StrictPlacementTypes.BANNER_300x50 -> campaign.targeting.allowsBannerOn(screenId)
            StrictPlacementTypes.NATIVE_300x200 -> campaign.targeting.allowsNativeOn(screenId)
            StrictPlacementTypes.POPUP_300x200 ->
                screenId == AdTargetScreens.APP_LAUNCH && campaign.targeting.allowsPopupOnLaunch()
            else -> false
        }
    }

    fun isValidDestinationUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("https://") || u.startsWith("http://") || u.startsWith("tel:")
    }

    /**
     * Returns stably ordered eligible campaigns (`createdAt ASC`, `advertiserId ASC`) for equal round-robin fairness.
     */
    fun getEligibleOrderedCampaigns(
        campaigns: List<ManagedAdvertiserCampaign>,
        requestedPlacement: String,
        screenId: String,
        nowMs: Long
    ): List<ManagedAdvertiserCampaign> {
        return campaigns
            .filter { isCampaignEligibleForDisplay(it, requestedPlacement, screenId, nowMs) }
            .sortedWith(compareBy<ManagedAdvertiserCampaign> { it.createdAt }.thenBy { it.advertiserId }.thenBy { it.advertisementId })
    }

    /**
     * Atomically advances the counter for `(requestedPlacement, screenId)` and selects the next fair advertiser.
     * Returns `Pair(selectedCampaign, newCounterValue)` or `null` if no advertiser is eligible.
     */
    fun selectNextFair(
        campaigns: List<ManagedAdvertiserCampaign>,
        requestedPlacement: String,
        screenId: String,
        nowMs: Long
    ): Pair<ManagedAdvertiserCampaign, Long>? {
        val eligible = getEligibleOrderedCampaigns(campaigns, requestedPlacement, screenId, nowMs)
        if (eligible.isEmpty()) return null
        val key = counterKey(requestedPlacement, screenId)
        val atomic = counters.computeIfAbsent(key) { AtomicLong(0L) }
        val index = atomic.getAndIncrement()
        val selected = eligible[Math.floorMod(index, eligible.size.toLong()).toInt()]
        return selected to (index + 1L)
    }

    /**
     * Deterministic slot selection for Native Banner lists (Doctor / Hospital / Diagnostic / Chamber):
     * Uses the screen's persisted base counter + slot index so scrolling/rebuilds NEVER duplicate or jitter,
     * while across list loads/sessions every active advertiser rotates strictly `C1 -> C2 -> C3 -> C1...`.
     */
    fun selectForNativeSlot(
        campaigns: List<ManagedAdvertiserCampaign>,
        screenId: String,
        slotIndex: Int,
        baseOffset: Long,
        nowMs: Long
    ): ManagedAdvertiserCampaign? {
        if (screenId !in AdTargetScreens.NATIVE_SUPPORTED_SCREENS) return null
        val eligible = getEligibleOrderedCampaigns(campaigns, StrictPlacementTypes.NATIVE_300x200, screenId, nowMs)
        if (eligible.isEmpty()) return null
        val safeIdx = (baseOffset.coerceAtLeast(0L) + slotIndex.coerceAtLeast(0).toLong())
        return eligible[Math.floorMod(safeIdx, eligible.size.toLong()).toInt()]
    }

    /**
     * Exact native insertion rule:
     * Card 0, 1, 2 -> insert after index 2 (after 3rd content card, slot 0)
     * Card 3, 4, 5 -> insert after index 5 (after 6th content card, slot 1)
     * Never before the 3rd content card. Counts ONLY content cards, never ad cards.
     */
    fun shouldInsertNativeAfterCard(contentCardIndex: Int): Boolean =
        contentCardIndex >= 2 && (contentCardIndex + 1) % 3 == 0

    fun nativeSlotForCardIndex(contentCardIndex: Int): Int =
        ((contentCardIndex + 1) / 3) - 1

    /**
     * Validates Dynamic Package creation/update inputs (rejecting removed placement types and invalid dimensions/prices).
     */
    fun validatePackageInput(
        packageName: String,
        placementType: String,
        width: Int,
        height: Int,
        durationDays: Int,
        price: Double
    ): Result<Unit> {
        if (packageName.trim().length < 2) return Result.failure(IllegalArgumentException("Package Name is required."))
        if (!StrictPlacementTypes.isAllowed(placementType)) {
            return Result.failure(IllegalArgumentException("Invalid placement type. Only 300x50 Banner, 300x200 Native Banner, and 300x200 Popup Banner are allowed."))
        }
        if (width !in 50..2000 || height !in 30..2000) {
            return Result.failure(IllegalArgumentException("Width and Height must be valid positive pixel dimensions."))
        }
        if (durationDays !in 1..3650) {
            return Result.failure(IllegalArgumentException("Duration (Days) must be between 1 and 3650."))
        }
        if (price <= 0.0 || price.isNaN() || price.isInfinite()) {
            return Result.failure(IllegalArgumentException("Price (BDT) must be greater than 0."))
        }
        return Result.success(Unit)
    }

    /**
     * Creates an immutable purchase snapshot from an ACTIVE dynamic package.
     * Inactive packages are strictly rejected from new purchases.
     */
    fun createPurchaseSnapshot(
        purchaseId: String,
        pkg: DynamicAdPackage,
        startDateMs: Long
    ): Result<DynamicPurchaseSnapshot> {
        if (!pkg.isActive) {
            return Result.failure(IllegalStateException("Inactive packages cannot be purchased."))
        }
        if (!StrictPlacementTypes.isAllowed(pkg.placementType)) {
            return Result.failure(IllegalArgumentException("Package placement type is not allowed."))
        }
        val endDateMs = startDateMs + pkg.durationDays.toLong() * 86_400_000L
        return Result.success(
            DynamicPurchaseSnapshot(
                purchaseId = purchaseId,
                packageId = pkg.packageId,
                packageName = pkg.packageName,
                placementType = pkg.placementType,
                width = pkg.width,
                height = pkg.height,
                price = pkg.price,
                durationDays = pkg.durationDays,
                startDate = startDateMs,
                endDate = endDateMs
            )
        )
    }

    internal fun resetForTesting() {
        counters.clear()
    }
}
