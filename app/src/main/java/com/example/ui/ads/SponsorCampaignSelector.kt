package com.example.ui.ads

import com.example.data.local.SponsorEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure, deterministic sponsor campaign selection, scheduling, validation, and rotation engine.
 * Works identically in Online and Offline modes using persisted campaign metadata & expiration bounds.
 */
object SponsorCampaignSelector {
    const val PAGE_ALL = "ALL"
    const val PAGE_HOME = "home"
    const val PAGE_FIND_DOCTORS = "find_doctors"
    const val PAGE_FACILITIES = "facilities"
    const val PAGE_PORTAL = "portal"
    const val PAGE_DOCTOR_PROFILE = "doctor_profile"
    const val PAGE_ABOUT_US = "about_us"
    const val PAGE_INFO = "info"

    const val FREQ_EVERY_LAUNCH = "EVERY_LAUNCH"
    const val FREQ_ONCE_DAILY = "ONCE_DAILY"

    const val MAX_IMAGE_BYTES = 3 * 1024 * 1024 // 3 MB strict safety cap

    private val dhakaTz: TimeZone get() = TimeZone.getTimeZone("Asia/Dhaka")

    fun todayDateKey(nowMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = dhakaTz
        return sdf.format(Date(nowMs))
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
                sdf.parse(raw)?.time?.let { it + (24L * 60L * 60L * 1000L) - 1L }
            }.getOrNull()
        } ?: Long.MAX_VALUE

        return startMs to endMs
    }

    /**
     * Returns true ONLY if sponsor is Active AND current timestamp falls within [startTimestamp, endTimestamp].
     * Automatically hides expired or not-yet-started campaigns in both online and offline modes.
     */
    fun isCampaignValid(sponsor: SponsorEntity, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!sponsor.isActive) return false
        if (sponsor.name.isBlank()) return false
        val effectiveStart = if (sponsor.startTimestamp > 0L) {
            sponsor.startTimestamp
        } else {
            parseCampaignBounds(sponsor.startDate, sponsor.endDate).first
        }
        val effectiveEnd = if (sponsor.endTimestamp > 0L && sponsor.endTimestamp < Long.MAX_VALUE) {
            sponsor.endTimestamp
        } else {
            parseCampaignBounds(sponsor.startDate, sponsor.endDate).second
        }
        return nowMs in effectiveStart..effectiveEnd
    }

    fun isCampaignExpired(sponsor: SponsorEntity, nowMs: Long = System.currentTimeMillis()): Boolean {
        val effectiveEnd = if (sponsor.endTimestamp > 0L && sponsor.endTimestamp < Long.MAX_VALUE) {
            sponsor.endTimestamp
        } else {
            parseCampaignBounds(sponsor.startDate, sponsor.endDate).second
        }
        return effectiveEnd < Long.MAX_VALUE && nowMs > effectiveEnd
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
            else -> PAGE_HOME
        }
    }

    /**
     * Filters valid, non-expired sponsors eligible for [pageKey] sorted by priority DESC.
     */
    fun getEligibleSponsors(
        sponsors: List<SponsorEntity>,
        pageKey: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<SponsorEntity> {
        return sponsors
            .filter { isCampaignValid(it, nowMs) && matchesPage(it, pageKey) }
            .sortedWith(
                compareByDescending<SponsorEntity> { it.displayPriority }
                    .thenByDescending { it.displayFrequency }
                    .thenByDescending { it.updatedAt }
            )
    }

    /**
     * Selects a single sponsor for the bottom/page Sponsor Banner using weighted priority rotation.
     */
    fun selectBannerSponsor(
        sponsors: List<SponsorEntity>,
        pageKey: String,
        rotationTick: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): SponsorEntity? {
        val eligible = getEligibleSponsors(sponsors, pageKey, nowMs)
        if (eligible.isEmpty()) return null
        val weightedPool = buildWeightedPool(eligible)
        val idx = ((rotationTick % weightedPool.size) + weightedPool.size) % weightedPool.size
        return weightedPool[idx]
    }

    /**
     * Selects the Direct Sponsor for the App Open Sponsor Popup.
     * Prefers [preferredSponsorId] if configured and currently valid; otherwise picks the highest-priority
     * active valid sponsor across the app. Returns null if no valid sponsor exists.
     */
    fun selectAppOpenPopupSponsor(
        sponsors: List<SponsorEntity>,
        preferredSponsorId: String,
        nowMs: Long = System.currentTimeMillis()
    ): SponsorEntity? {
        val valid = sponsors
            .filter { isCampaignValid(it, nowMs) }
            .sortedWith(
                compareByDescending<SponsorEntity> { it.displayPriority }
                    .thenByDescending { it.updatedAt }
            )
        if (valid.isEmpty()) return null
        val cleanPref = preferredSponsorId.trim()
        if (cleanPref.isNotEmpty() && !cleanPref.equals("AUTO", ignoreCase = true)) {
            valid.firstOrNull { it.id == cleanPref }?.let { return it }
        }
        return valid.first()
    }

    /**
     * Determines whether App Open Sponsor Popup should display on this launch.
     */
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
            else -> true // EVERY_LAUNCH
        }
    }

    /**
     * Rotates active valid sponsors for Native Sponsor slots between content cards.
     * Guarantees NO consecutive duplicate sponsors in the same feed section whenever >= 2 valid sponsors exist,
     * and prevents repeating the same sponsor inside a short section if only 1 exists by capping duplicates
     * or spacing cleanly.
     */
    fun selectNativeSponsorForSlot(
        sponsors: List<SponsorEntity>,
        pageKey: String,
        slotIndex: Int,
        nowMs: Long = System.currentTimeMillis()
    ): SponsorEntity? {
        val eligible = getEligibleSponsors(sponsors, pageKey, nowMs)
        if (eligible.isEmpty()) return null
        // Distinct rotation order without consecutive duplicates
        if (eligible.size == 1) {
            // In a short section, only show single sponsor once per unique section unless slotIndex == 0
            return if (slotIndex == 0) eligible[0] else null
        }
        return eligible[slotIndex % eligible.size]
    }

    /**
     * Returns true if a Native Sponsor Card should be inserted right after card at [cardIndex] (0-based).
     * Supports Admin-configured intervals of 4, 5 (default), or 6 cards.
     */
    fun shouldInsertNativeSponsorAfter(cardIndex: Int, configuredInterval: Int): Boolean {
        val interval = configuredInterval.coerceIn(4, 6)
        return (cardIndex + 1) % interval == 0
    }

    /**
     * Strict URL validation for sponsor image/website URLs to prevent unsafe schemes.
     */
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

    /**
     * Checks magic bytes of downloaded image bytes to confirm real PNG, JPEG, WebP, or GIF image.
     */
    fun isSupportedImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12 || bytes.size > MAX_IMAGE_BYTES) return false
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        // JPEG: FF D8 FF
        val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        // WEBP: RIFF....WEBP
        val isWebp = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        // GIF: GIF87a / GIF89a
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
