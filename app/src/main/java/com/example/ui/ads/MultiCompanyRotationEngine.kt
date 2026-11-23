package com.example.ui.ads

import com.example.data.local.SponsorEntity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Multi-Company Fair Ad Rotation Engine.
 *
 * Guarantees:
 * - Separate independent rotation sequences for BANNER, POPUP, and NATIVE placements.
 * - Fair round-robin across active companies:
 *   - 3 companies: Company 1 -> Company 2 -> Company 3 -> Repeat
 *   - 2 companies: Company 1 -> Company 2 -> Repeat
 *   - 1 company:   Company 1 consistently
 * - Avoids consecutive duplicate companies whenever >= 2 active companies exist.
 * - Preserves rotation continuity across pagination and doctor list refreshes without unnecessary resets.
 */
object MultiCompanyRotationEngine {
    enum class Channel {
        BANNER,
        POPUP,
        NATIVE
    }

    private val cursors = ConcurrentHashMap<Channel, AtomicInteger>().apply {
        Channel.entries.forEach { put(it, AtomicInteger(0)) }
    }
    private val lastCompanyByChannel = ConcurrentHashMap<Channel, String>()
    private val companyAdCounters = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Resolves a canonical company key for any SponsorEntity (backward compatible with legacy records).
     */
    fun resolveCompanyKey(sponsor: SponsorEntity): String {
        val raw = sponsor.companyId.trim()
            .ifBlank { sponsor.advertiserId.trim() }
            .ifBlank { sponsor.name.trim() }
            .ifBlank { sponsor.id }
        return raw.lowercase(Locale.US)
    }

    /**
     * Groups eligible sponsors by company and orders companies deterministically by
     * rotationOrder ASC (explicit > 0 first, then priority DESC, then companyKey ASC).
     */
    fun buildOrderedCompanies(eligibleSponsors: List<SponsorEntity>): List<Pair<String, List<SponsorEntity>>> {
        if (eligibleSponsors.isEmpty()) return emptyList()
        val grouped = eligibleSponsors.groupBy { resolveCompanyKey(it) }
        return grouped.entries
            .map { (key, ads) ->
                val sortedAds = ads.sortedWith(
                    compareBy<SponsorEntity> { if (it.rotationOrder > 0) it.rotationOrder else Int.MAX_VALUE }
                        .thenByDescending { it.displayPriority }
                        .thenByDescending { it.updatedAt }
                        .thenBy { it.id }
                )
                key to sortedAds
            }
            .sortedWith(
                compareBy<Pair<String, List<SponsorEntity>>> { (_, ads) ->
                    ads.minOfOrNull { if (it.rotationOrder > 0) it.rotationOrder else Int.MAX_VALUE } ?: Int.MAX_VALUE
                }
                    .thenByDescending { (_, ads) -> ads.maxOfOrNull { it.displayPriority } ?: 0 }
                    .thenBy { (key, _) -> key }
            )
    }

    /**
     * Selects the next fair company's ad for sequential placements (BANNER / POPUP),
     * advancing the channel cursor without consecutive duplicate companies when >= 2 companies exist.
     */
    fun selectNextForChannel(
        channel: Channel,
        eligibleSponsors: List<SponsorEntity>
    ): SponsorEntity? {
        val companies = buildOrderedCompanies(eligibleSponsors)
        if (companies.isEmpty()) return null
        if (companies.size == 1) {
            val (companyKey, ads) = companies[0]
            lastCompanyByChannel[channel] = companyKey
            return pickAdFromCompany(channel, companyKey, ads, 0)
        }

        val cursor = cursors.getOrPut(channel) { AtomicInteger(0) }
        var idx = Math.floorMod(cursor.getAndIncrement(), companies.size)
        val prevCompany = lastCompanyByChannel[channel]
        if (prevCompany != null && companies[idx].first == prevCompany) {
            idx = (idx + 1) % companies.size
            cursor.set(idx + 1)
        }
        val (companyKey, ads) = companies[idx]
        lastCompanyByChannel[channel] = companyKey
        return pickAdFromCompany(channel, companyKey, ads, idx)
    }

    /**
     * Selects a deterministic, fair-rotated company ad for a specific indexed list slot
     * (e.g. Native Ad slot 0 after Doctor 3, slot 1 after Doctor 6, slot 2 after Doctor 9).
     *
     * Guarantees:
     * - 3 companies -> C1, C2, C3, C1, C2, C3...
     * - 2 companies -> C1, C2, C1, C2...
     * - 1 company   -> C1, C1...
     * - Adjacent slots NEVER show the same company when >= 2 companies are active.
     * - Pagination and list refreshes keep consistent rotation without jitter or duplicate resets.
     */
    fun selectForSlot(
        channel: Channel,
        eligibleSponsors: List<SponsorEntity>,
        slotIndex: Int,
        sequenceOffset: Int = 0
    ): SponsorEntity? {
        val companies = buildOrderedCompanies(eligibleSponsors)
        if (companies.isEmpty()) return null
        val safeSlot = slotIndex.coerceAtLeast(0)
        val companyIdx = Math.floorMod(sequenceOffset + safeSlot, companies.size)
        val (companyKey, ads) = companies[companyIdx]
        val cycleForCompany = safeSlot / companies.size
        return ads[Math.floorMod(cycleForCompany, ads.size)]
    }

    private fun pickAdFromCompany(
        channel: Channel,
        companyKey: String,
        ads: List<SponsorEntity>,
        fallbackOffset: Int
    ): SponsorEntity {
        if (ads.size == 1) return ads[0]
        val counter = companyAdCounters.getOrPut("${channel.name}:$companyKey") { AtomicInteger(fallbackOffset) }
        val adIdx = Math.floorMod(counter.getAndIncrement(), ads.size)
        return ads[adIdx]
    }

    /**
     * Advances the persistent session offset for a channel (e.g. when user explicitly pulls-to-refresh)
     * without resetting to 0.
     */
    fun advanceOffset(channel: Channel): Int =
        cursors.getOrPut(channel) { AtomicInteger(0) }.incrementAndGet()

    fun currentOffset(channel: Channel): Int =
        cursors.getOrPut(channel) { AtomicInteger(0) }.get()

    internal fun resetForTesting() {
        Channel.entries.forEach { cursors[it]?.set(0) }
        lastCompanyByChannel.clear()
        companyAdCounters.clear()
    }
}
