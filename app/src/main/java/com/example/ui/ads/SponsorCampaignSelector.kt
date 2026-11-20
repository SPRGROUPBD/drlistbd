package com.example.ui.ads

import com.example.data.local.SponsorEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

/**
 * Pure, deterministic Direct Sponsor campaign selector, server-authoritative expiry enforcer,
 * placement matcher, image/URL validator, and rotation engine.
 * Works identically in Online and Offline modes with zero third-party ad networks.
 */
object SponsorCampaignSelector {
    const val PAGE_ALL = "ALL"
    const val PAGE_HOME = "home"
    const val PAGE_FIND_DOCTORS = "find_doctors"
    const val PAGE_FACILITIES = "facilities"
    const val PAGE_PORTAL = "portal"
    const val PAGE_DOCTOR_PROFILE = "doctor_profile"
    const val PAGE_ABOUT_US = "about_us"
    const val PAGE_ADVERTISER = "advertiser_portal"
    const val PAGE_INFO = "info"

    // Supported Direct Sponsor Placement Types
    const val PLACEMENT_ALL = "ALL"
    const val PLACEMENT_BANNER_300x200 = "BANNER_300x200"
    const val PLACEMENT_BANNER_320x100 = "BANNER_320x100"
    const val PLACEMENT_BANNER_728x90 = "BANNER_728x90"
    const val PLACEMENT_BANNER_CUSTOM = "BANNER_CUSTOM"
    const val PLACEMENT_NATIVE_CARD = "SPONSORED_NATIVE_CARD"
    const val PLACEMENT_POPUP = "POPUP_AD"
    const val PLACEMENT_ABOUT_US = "ABOUT_US_SPONSOR"

    // Ad lifecycle statuses
    const val STATUS_PENDING_PAYMENT = "PENDING_PAYMENT"
    const val STATUS_PENDING_APPROVAL = "PENDING_APPROVAL"
    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_PAUSED = "PAUSED"
    const val STATUS_EXPIRED = "EXPIRED"
    const val STATUS_REJECTED = "REJECTED"

    const val FREQ_EVERY_LAUNCH = "EVERY_LAUNCH"
    const val FREQ_ONCE_DAILY = "ONCE_DAILY"

    const val MAX_IMAGE_BYTES = 3 * 1024 * 1024 // 3 MB strict safety cap
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private val dhakaTz: TimeZone get() = TimeZone.getTimeZone("Asia/Dhaka")

    fun todayDateKey(nowMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = dhakaTz
        return sdf.format(Date(nowMs))
    }

    fun formatDateDisplay(timestampMs: Long): String {
        if (timestampMs <= 0L || timestampMs == Long.MAX_VALUE) return "—"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        sdf.timeZone = dhakaTz
        return sdf.format(Date(timestampMs))
    }

    /**
     * Parses startDate (00:00:00.000 Asia/Dhaka) and endDate (23:59:59.999 Asia/Dhaka).
     * Blank dates default to open-ended (0L / Long.MAX_VALUE).
     */
    fun parseCampaignBounds(startDate: String, endDate: String): Pair<Long, Long> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = dhakaTz
            isLenient = false
        }
        val startMs = startDate.trim().takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { sdf.parse(raw)?.time }.getOrNull()
        } ?: 0L

        val endMs = endDate.trim().takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching {
                sdf.parse(raw)?.time?.let { it + DAY_MS - 1L }
            }.getOrNull()
        } ?: Long.MAX_VALUE

        return startMs to endMs
    }

    /**
     * Resolves server-authoritative activation & expiry timestamps.
     * Prefers explicit server `activatedAt`/`expiryAt` over date strings.
     */
    fun resolveEffectiveWindow(sponsor: SponsorEntity): Pair<Long, Long> {
        val (parsedStart, parsedEnd) = parseCampaignBounds(sponsor.startDate, sponsor.endDate)
        val start = when {
            sponsor.activatedAt > 0L -> sponsor.activatedAt
            sponsor.startTimestamp > 0L -> sponsor.startTimestamp
            else -> parsedStart
        }
        val end = when {
            sponsor.expiryAt in 1L until Long.MAX_VALUE -> sponsor.expiryAt
            sponsor.endTimestamp in 1L until Long.MAX_VALUE -> sponsor.endTimestamp
            else -> parsedEnd
        }
        return start to end
    }

    /**
     * Returns true ONLY if:
     * 1. Status is ACTIVE (never PENDING_PAYMENT, PENDING_APPROVAL, PAUSED, EXPIRED, REJECTED),
     * 2. isActive flag is true,
     * 3. Server-authoritative timestamp `nowMs` falls within [effectiveStart, effectiveEnd].
     */
    fun isCampaignValid(sponsor: SponsorEntity, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!sponsor.isActive) return false
        if (!sponsor.status.equals(STATUS_ACTIVE, ignoreCase = true)) return false
        if (sponsor.name.isBlank() && sponsor.promoTitle.isBlank()) return false
        val (effectiveStart, effectiveEnd) = resolveEffectiveWindow(sponsor)
        return nowMs in effectiveStart..effectiveEnd
    }

    fun isCampaignExpired(sponsor: SponsorEntity, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (sponsor.status.equals(STATUS_EXPIRED, ignoreCase = true)) return true
        val (_, effectiveEnd) = resolveEffectiveWindow(sponsor)
        return effectiveEnd < Long.MAX_VALUE && nowMs > effectiveEnd
    }

    fun remainingDays(sponsor: SponsorEntity, nowMs: Long = System.currentTimeMillis()): Int {
        val (_, effectiveEnd) = resolveEffectiveWindow(sponsor)
        if (effectiveEnd == Long.MAX_VALUE) return 0
        val diff = effectiveEnd - nowMs
        if (diff <= 0L) return 0
        return ceil(diff.toDouble() / DAY_MS.toDouble()).toInt()
    }

    fun matchesPlacement(sponsor: SponsorEntity, requestedPlacements: Set<String>): Boolean {
        val pt = sponsor.placementType.trim()
        if (pt.isEmpty() || pt.equals(PLACEMENT_ALL, ignoreCase = true)) return true
        return requestedPlacements.any { it.equals(pt, ignoreCase = true) }
    }

    fun matchesPage(sponsor: SponsorEntity, pageKey: String): Boolean {
        val raw = sponsor.targetPages.trim()
        if (raw.isEmpty() || raw.equals(PAGE_ALL, ignoreCase = true)) return true
        val normalizedPage = normalizeRouteToPageKey(pageKey)
        val targets = raw.split(",").map { it.trim().lowercase(Locale.US) }.filter { it.isNotEmpty() }
        return "all" in targets || normalizedPage in targets
    }

    fun normalizeRouteToPageKey(route: String): String {
        val r = route.trim().lowercase(Locale.US)
        return when {
            r.startsWith("doctor_profile") -> PAGE_DOCTOR_PROFILE
            r.startsWith("info") -> PAGE_INFO
            r == "find_doctors" -> PAGE_FIND_DOCTORS
            r == "facilities" -> PAGE_FACILITIES
            r == "portal" -> PAGE_PORTAL
            r == "about_us" -> PAGE_ABOUT_US
            r == "advertiser_portal" -> PAGE_ADVERTISER
            else -> PAGE_HOME
        }
    }

    fun getEligibleSponsors(
        sponsors: List<SponsorEntity>,
        pageKey: String,
        allowedPlacements: Set<String>? = null,
        nowMs: Long = System.currentTimeMillis()
    ): List<SponsorEntity> {
        return sponsors
            .filter { sp ->
                isCampaignValid(sp, nowMs) &&
                    matchesPage(sp, pageKey) &&
                    (allowedPlacements == null || matchesPlacement(sp, allowedPlacements))
            }
            .sortedWith(
                compareByDescending<SponsorEntity> { it.displayPriority }
                    .thenByDescending { it.displayFrequency }
                    .thenByDescending { it.updatedAt }
            )
    }

    private val BANNER_PLACEMENTS = setOf(
        PLACEMENT_BANNER_300x200,
        PLACEMENT_BANNER_320x100,
        PLACEMENT_BANNER_728x90,
        PLACEMENT_BANNER_CUSTOM
    )

    fun selectBannerSponsor(
        sponsors: List<SponsorEntity>,
        pageKey: String,
        rotationTick: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): SponsorEntity? {
        val eligible = getEligibleSponsors(sponsors, pageKey, BANNER_PLACEMENTS, nowMs)
        if (eligible.isEmpty()) return null
        val weightedPool = buildWeightedPool(eligible)
        val idx = ((rotationTick % weightedPool.size) + weightedPool.size) % weightedPool.size
        return weightedPool[idx]
    }

    fun selectAppOpenPopupSponsor(
        sponsors: List<SponsorEntity>,
        preferredSponsorId: String,
        nowMs: Long = System.currentTimeMillis()
    ): SponsorEntity? {
        val validPopup = sponsors
            .filter { isCampaignValid(it, nowMs) && matchesPlacement(it, setOf(PLACEMENT_POPUP)) }
            .sortedWith(
                compareByDescending<SponsorEntity> { it.displayPriority }
                    .thenByDescending { it.updatedAt }
            )
        val pool = validPopup.ifEmpty {
            sponsors.filter { isCampaignValid(it, nowMs) }
                .sortedWith(compareByDescending<SponsorEntity> { it.displayPriority }.thenByDescending { it.updatedAt })
        }
        if (pool.isEmpty()) return null
        val cleanPref = preferredSponsorId.trim()
        if (cleanPref.isNotEmpty() && !cleanPref.equals("AUTO", ignoreCase = true)) {
            sponsors.firstOrNull { it.id == cleanPref && isCampaignValid(it, nowMs) }?.let { return it }
        }
        return pool.first()
    }

    fun shouldShowAppOpenPopup(
        popupEnabled: Boolean,
        frequency: String,
        lastShownDate: String,
        todayDate: String,
        alreadyShownThisLaunch: Boolean
    ): Boolean {
        if (!popupEnabled || alreadyShownThisLaunch) return false
        return when (frequency.uppercase(Locale.US)) {
            FREQ_ONCE_DAILY -> lastShownDate != todayDate
            else -> true
        }
    }

    fun selectNativeSponsorForSlot(
        sponsors: List<SponsorEntity>,
        pageKey: String,
        slotIndex: Int,
        nowMs: Long = System.currentTimeMillis()
    ): SponsorEntity? {
        val preferred = getEligibleSponsors(
            sponsors,
            pageKey,
            setOf(PLACEMENT_NATIVE_CARD, PLACEMENT_BANNER_300x200),
            nowMs
        )
        val eligible = preferred.ifEmpty { getEligibleSponsors(sponsors, pageKey, null, nowMs) }
        if (eligible.isEmpty()) return null
        if (eligible.size == 1) {
            return if (slotIndex == 0) eligible[0] else null
        }
        return eligible[slotIndex % eligible.size]
    }

    /**
     * Selects active sponsors for the Administrator-controlled About Us Sponsor section.
     * Prioritizes featured ad ID, then ABOUT_US_SPONSOR & 300x200/Native ads, capped by maxAds.
     */
    fun selectAboutUsSponsors(
        sponsors: List<SponsorEntity>,
        featuredAdId: String,
        maxAds: Int,
        nowMs: Long = System.currentTimeMillis()
    ): List<SponsorEntity> {
        val cap = maxAds.coerceIn(1, 10)
        val validAbout = getEligibleSponsors(
            sponsors = sponsors,
            pageKey = PAGE_ABOUT_US,
            allowedPlacements = setOf(
                PLACEMENT_ABOUT_US,
                PLACEMENT_BANNER_300x200,
                PLACEMENT_BANNER_320x100,
                PLACEMENT_BANNER_CUSTOM,
                PLACEMENT_NATIVE_CARD
            ),
            nowMs = nowMs
        )
        val ordered = mutableListOf<SponsorEntity>()
        if (featuredAdId.isNotBlank()) {
            sponsors.firstOrNull { it.id == featuredAdId && isCampaignValid(it, nowMs) }?.let {
                ordered.add(it)
            }
        }
        // Dedicated About Us placement first
        validAbout.filter { it.placementType.equals(PLACEMENT_ABOUT_US, true) }.forEach { sp ->
            if (ordered.none { it.id == sp.id }) ordered.add(sp)
        }
        validAbout.forEach { sp ->
            if (ordered.none { it.id == sp.id }) ordered.add(sp)
        }
        return ordered.take(cap)
    }

    fun shouldInsertNativeSponsorAfter(cardIndex: Int, configuredInterval: Int): Boolean {
        val interval = configuredInterval.coerceIn(4, 6)
        return (cardIndex + 1) % interval == 0
    }

    fun normalizeTransactionId(trxId: String): String =
        trxId.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")

    fun isValidBangladeshMobile(phone: String): Boolean {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return (digits.length == 11 && digits.startsWith("01")) ||
            (digits.length == 13 && digits.startsWith("8801"))
    }

    fun isValidImageSource(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty()) return false
        if (clean.startsWith("https://", ignoreCase = true)) return clean.length <= 2048
        if (clean.startsWith("data:image/", ignoreCase = true)) {
            val lower = clean.lowercase(Locale.US)
            val validMime = lower.startsWith("data:image/png;base64,") ||
                lower.startsWith("data:image/jpeg;base64,") ||
                lower.startsWith("data:image/jpg;base64,") ||
                lower.startsWith("data:image/webp;base64,")
            return validMime && clean.length <= (MAX_IMAGE_BYTES * 4 / 3 + 128)
        }
        return false
    }

    fun isValidWebsiteUrl(url: String): Boolean {
        val clean = url.trim()
        return clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true)
    }

    fun validateImageDimensions(actualW: Int, actualH: Int, targetW: Int, targetH: Int): Boolean {
        if (actualW < 100 || actualH < 50) return false
        if (targetW <= 0 || targetH <= 0) return true
        // Accept exact match OR >= 70% of target resolution with aspect ratio within 35% tolerance
        val minW = (targetW * 0.7).toInt().coerceAtLeast(100)
        val minH = (targetH * 0.7).toInt().coerceAtLeast(50)
        if (actualW < minW || actualH < minH) return false
        val targetRatio = targetW.toDouble() / targetH.toDouble()
        val actualRatio = actualW.toDouble() / actualH.toDouble()
        val ratioDiff = kotlin.math.abs(actualRatio - targetRatio) / targetRatio
        return ratioDiff <= 0.45
    }

    fun isSupportedImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12 || bytes.size > MAX_IMAGE_BYTES) return false
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val isWebp = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        val isGif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()
        return isPng || isJpeg || isWebp || isGif
    }

    private fun buildWeightedPool(eligible: List<SponsorEntity>): List<SponsorEntity> {
        val pool = mutableListOf<SponsorEntity>()
        eligible.forEach { sp ->
            val weight = sp.displayFrequency.coerceIn(1, 5)
            repeat(weight) { pool.add(sp) }
        }
        return pool.ifEmpty { eligible }
    }
}
