package com.example.ui.ads

import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.RateCardSettingsEntity
import com.example.data.local.SponsorEntity
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Screen-Based Advertisement Placement Catalog, Dynamic Ad Size Matrix,
 * Multi-Placement Rate Card Pricing & Bundle Discount Calculator, and
 * Capacity / Rotation Selector.
 */
object PlacementRateCardEngine {
    // Module keys
    const val MOD_HOME = "HOME"
    const val MOD_DOCTOR = "DOCTOR"
    const val MOD_HOSPITAL = "HOSPITAL"
    const val MOD_DIAGNOSTIC = "DIAGNOSTIC"
    const val MOD_SPECIALIST = "SPECIALIST"
    const val MOD_CHAMBER = "CHAMBER"
    const val MOD_OTHER = "OTHER"

    // Canonical Placement IDs (37 standard placements + Future extensibility)
    const val HOME_01 = "HOME-01"
    const val HOME_02 = "HOME-02"
    const val HOME_03 = "HOME-03"
    const val HOME_04 = "HOME-04"
    const val HOME_05 = "HOME-05"
    const val HOME_06 = "HOME-06"
    const val HOME_07 = "HOME-07"

    const val DOC_01 = "DOC-01"
    const val DOC_02 = "DOC-02"
    const val DOC_03 = "DOC-03"
    const val DOC_04 = "DOC-04"
    const val DOC_05 = "DOC-05"

    const val HOS_01 = "HOS-01"
    const val HOS_02 = "HOS-02"
    const val HOS_03 = "HOS-03"
    const val HOS_04 = "HOS-04"

    const val DIA_01 = "DIA-01"
    const val DIA_02 = "DIA-02"
    const val DIA_03 = "DIA-03"
    const val DIA_04 = "DIA-04"

    const val SPE_01 = "SPE-01"
    const val SPE_02 = "SPE-02"
    const val SPE_03 = "SPE-03"

    const val CHA_01 = "CHA-01"
    const val CHA_02 = "CHA-02"
    const val CHA_03 = "CHA-03"
    const val CHA_04 = "CHA-04"

    const val OTH_SEARCH = "OTH-SEARCH"
    const val OTH_CATEGORY = "OTH-CATEGORY"
    const val OTH_DISTRICT = "OTH-DISTRICT"
    const val OTH_UPAZILA = "OTH-UPAZILA"
    const val OTH_FEATURED = "OTH-FEATURED"
    const val OTH_ABOUT = "OTH-ABOUT"
    const val OTH_POPUP = "OTH-POPUP"
    const val OTH_FULLSCREEN = "OTH-FULLSCREEN"
    const val OTH_WEB = "OTH-WEB"
    const val OTH_FUTURE = "OTH-FUTURE"

    // Rotation modes
    const val ROTATION_WEIGHTED = "WEIGHTED_ROTATION"
    const val ROTATION_PRIORITY = "PRIORITY_FIRST"
    const val ROTATION_SEQUENTIAL = "SEQUENTIAL"
    const val ROTATION_RANDOM = "RANDOM"

    data class AdSizeSpec(
        val key: String,
        val displayLabel: String,
        val formatNameEn: String,
        val formatNameBn: String,
        val widthPx: Int,
        val heightPx: Int
    )

    val STANDARD_AD_SIZES: List<AdSizeSpec> = listOf(
        AdSizeSpec("320x50", "320×50", "Small Banner", "স্মল ব্যানার (৩২০×৫০)", 320, 50),
        AdSizeSpec("320x100", "320×100", "Large Banner", "লার্জ ব্যানার (৩২০×১০০)", 320, 100),
        AdSizeSpec("300x200", "300×200", "Sponsor Card", "স্পন্সর কার্ড (৩০০×২০০)", 300, 200),
        AdSizeSpec("300x250", "300×250", "Rectangle", "রেকট্যাঙ্গেল (৩০০×২৫০)", 300, 250),
        AdSizeSpec("336x280", "336×280", "Large Rectangle", "লার্জ রেকট্যাঙ্গেল (৩৩৬×২৮০)", 336, 280),
        AdSizeSpec("300x300", "300×300", "Square Sponsor", "স্কয়ার স্পন্সর (৩০০×৩০০)", 300, 300),
        AdSizeSpec("RESPONSIVE", "Responsive", "Native Sponsor Card", "নেটিভ স্পন্সর কার্ড", 320, 180),
        AdSizeSpec("FULL_WIDTH", "Full Width", "Section Banner", "সেকশন ব্যানার (ফুল উইডথ)", 720, 180),
        AdSizeSpec("CUSTOM", "Admin Defined", "Custom Size", "কাস্টম সাইজ", 300, 200)
    )

    fun findSizeSpec(sizeKey: String): AdSizeSpec? =
        STANDARD_AD_SIZES.firstOrNull { it.key.equals(sizeKey.trim(), ignoreCase = true) }

    fun moduleLabelBn(module: String): String = when (module.uppercase()) {
        MOD_HOME -> "হোম স্ক্রিন (Home)"
        MOD_DOCTOR -> "ডাক্তার মডিউল (Doctor)"
        MOD_HOSPITAL -> "হাসপাতাল মডিউল (Hospital)"
        MOD_DIAGNOSTIC -> "ডায়াগনস্টিক মডিউল (Diagnostic)"
        MOD_SPECIALIST -> "চিকিৎসা বিশেষজ্ঞ (Specialist)"
        MOD_CHAMBER -> "চেম্বার সময়সূচি (Chamber)"
        MOD_OTHER -> "অন্যান্য প্লেসমেন্ট (Other)"
        else -> module
    }

    fun moduleLabelEn(module: String): String = when (module.uppercase()) {
        MOD_HOME -> "A. Home Screen"
        MOD_DOCTOR -> "B. Doctor Module"
        MOD_HOSPITAL -> "C. Hospital Module"
        MOD_DIAGNOSTIC -> "D. Diagnostic Center Module"
        MOD_SPECIALIST -> "E. Medical Specialist Module"
        MOD_CHAMBER -> "F. Chamber Schedule Module"
        MOD_OTHER -> "G. Other & Global Placements"
        else -> module
    }

    fun defaultRateCards(): List<PlacementRateCardEntity> = listOf(
        // A. Home Screen Placement
        card(HOME_01, "Home Top Banner", "হোম টপ ব্যানার", MOD_HOME, "320x50,320x100,FULL_WIDTH,RESPONSIVE", 50, 200, 320, 500, 25, 3, 1),
        card(HOME_02, "Between Home Sections", "Home Section-এর মাঝে", MOD_HOME, "320x100,300x200,300x250,RESPONSIVE,FULL_WIDTH", 40, 170, 270, 420, 20, 3, 2),
        card(HOME_03, "Popular Doctor Section", "Popular Doctor Section", MOD_HOME, "300x200,300x250,RESPONSIVE,FULL_WIDTH", 40, 160, 250, 400, 20, 3, 3),
        card(HOME_04, "Hospital Section", "Hospital Section", MOD_HOME, "300x200,320x100,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 4),
        card(HOME_05, "Diagnostic Section", "Diagnostic Section", MOD_HOME, "300x200,320x100,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 5),
        card(HOME_06, "Medical Specialist Section", "Medical Specialist Section", MOD_HOME, "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 6),
        card(HOME_07, "Home Bottom Banner", "হোম বটম ব্যানার", MOD_HOME, "320x50,320x100,FULL_WIDTH,RESPONSIVE", 40, 160, 250, 400, 20, 3, 7),

        // B. Doctor Module
        card(DOC_01, "Between Doctor List", "Doctor List-এর মাঝে", MOD_DOCTOR, "300x200,300x250,RESPONSIVE,320x100", 30, 120, 190, 300, 15, 4, 10),
        card(DOC_02, "Doctor Details Page", "Doctor Details Page", MOD_DOCTOR, "300x200,300x250,336x280,RESPONSIVE,FULL_WIDTH", 45, 180, 290, 450, 22, 3, 11),
        card(DOC_03, "Between Doctor Search Results", "Doctor Search Results-এর মাঝে", MOD_DOCTOR, "320x100,300x200,RESPONSIVE", 35, 140, 220, 350, 18, 3, 12),
        card(DOC_04, "Doctor Profile Bottom", "Doctor Profile Bottom", MOD_DOCTOR, "320x50,320x100,300x200,FULL_WIDTH", 30, 120, 190, 300, 15, 3, 13),
        card(DOC_05, "Doctor Details Related Section", "Doctor Details Related Section", MOD_DOCTOR, "300x200,300x300,RESPONSIVE", 30, 120, 190, 300, 15, 3, 14),

        // C. Hospital Module
        card(HOS_01, "Between Hospital List", "Hospital List-এর মাঝে", MOD_HOSPITAL, "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 4, 20),
        card(HOS_02, "Hospital Details Page", "Hospital Details Page", MOD_HOSPITAL, "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 21),
        card(HOS_03, "Between Hospital Search Results", "Hospital Search Results-এর মাঝে", MOD_HOSPITAL, "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 22),
        card(HOS_04, "Hospital Details Bottom", "Hospital Details Bottom", MOD_HOSPITAL, "320x50,320x100,FULL_WIDTH", 25, 100, 160, 260, 14, 3, 23),

        // D. Diagnostic Center Module
        card(DIA_01, "Between Diagnostic List", "Diagnostic List-এর মাঝে", MOD_DIAGNOSTIC, "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 4, 30),
        card(DIA_02, "Diagnostic Details Page", "Diagnostic Details Page", MOD_DIAGNOSTIC, "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 31),
        card(DIA_03, "Between Diagnostic Search Results", "Diagnostic Search Results-এর মাঝে", MOD_DIAGNOSTIC, "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 32),
        card(DIA_04, "Diagnostic Details Bottom", "Diagnostic Details Bottom", MOD_DIAGNOSTIC, "320x50,320x100,FULL_WIDTH", 25, 100, 160, 260, 14, 3, 33),

        // E. Medical Specialist Module
        card(SPE_01, "Between Specialist List", "Specialist List-এর মাঝে", MOD_SPECIALIST, "300x200,320x100,RESPONSIVE", 30, 120, 190, 300, 15, 3, 40),
        card(SPE_02, "Specialist Details Page", "Specialist Details Page", MOD_SPECIALIST, "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 220, 350, 18, 3, 41),
        card(SPE_03, "Between Specialist Search Results", "Specialist Search Results-এর মাঝে", MOD_SPECIALIST, "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 42),

        // F. Chamber Schedule Module
        card(CHA_01, "Between Chamber Schedule List", "Chamber Schedule List-এর মাঝে", MOD_CHAMBER, "300x200,320x100,RESPONSIVE", 30, 120, 190, 300, 15, 3, 50),
        card(CHA_02, "Chamber Details Page", "Chamber Details Page", MOD_CHAMBER, "300x200,300x250,RESPONSIVE,FULL_WIDTH", 35, 140, 220, 350, 18, 3, 51),
        card(CHA_03, "Doctor Schedule Details", "Doctor Schedule Details", MOD_CHAMBER, "320x100,300x200,RESPONSIVE", 35, 140, 220, 350, 18, 3, 52),
        card(CHA_04, "Between Chamber Search Results", "Chamber Search Results-এর মাঝে", MOD_CHAMBER, "320x100,300x200,RESPONSIVE", 30, 120, 190, 300, 15, 3, 53),

        // G. Other & Global Placements
        card(OTH_SEARCH, "Search Result Page", "সার্চ রেজাল্ট পেজ", MOD_OTHER, "320x50,320x100,300x200,RESPONSIVE,FULL_WIDTH", 35, 140, 230, 360, 18, 3, 60),
        card(OTH_CATEGORY, "Category Page", "ক্যাটাগরি পেজ", MOD_OTHER, "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 3, 61),
        card(OTH_DISTRICT, "District-wise Listing", "জেলাভিত্তিক তালিকা", MOD_OTHER, "300x200,320x100,RESPONSIVE,FULL_WIDTH", 35, 140, 220, 350, 18, 3, 62),
        card(OTH_UPAZILA, "Upazila-wise Listing", "উপজেলাভিত্তিক তালিকা", MOD_OTHER, "300x200,320x100,RESPONSIVE,FULL_WIDTH", 30, 120, 190, 300, 15, 3, 63),
        card(OTH_FEATURED, "Featured Section", "ফিচার্ড সেকশন", MOD_OTHER, "300x200,300x250,336x280,300x300,RESPONSIVE,FULL_WIDTH", 50, 200, 320, 500, 25, 3, 64),
        card(OTH_ABOUT, "App About Us Page", "অ্যাপ About Us পেজ", MOD_OTHER, "300x200,320x100,300x250,RESPONSIVE", 40, 160, 250, 400, 20, 3, 65),
        card(OTH_POPUP, "Popup Advertisement", "পপআপ বিজ্ঞাপন", MOD_OTHER, "300x200,300x250,336x280,300x300,CUSTOM", 80, 350, 600, 1000, 55, 2, 66),
        card(OTH_FULLSCREEN, "Full Screen Advertisement", "ফুল-স্ক্রিন বিজ্ঞাপন", MOD_OTHER, "300x250,336x280,300x300,FULL_WIDTH,CUSTOM", 100, 450, 800, 1400, 75, 2, 67),
        card(OTH_WEB, "Website Page Banner", "ওয়েবসাইট পেজ ব্যানার", MOD_OTHER, "320x50,320x100,FULL_WIDTH,336x280,CUSTOM", 40, 160, 250, 400, 20, 3, 68),
        card(OTH_FUTURE, "Future Modules", "ভবিষ্যৎ মডিউলসমূহ", MOD_OTHER, "320x50,320x100,300x200,300x250,336x280,300x300,RESPONSIVE,FULL_WIDTH,CUSTOM", 30, 120, 190, 300, 15, 3, 69)
    )

    private fun card(
        id: String,
        nameEn: String,
        nameBn: String,
        module: String,
        allowedSizes: String,
        d1: Int,
        d7: Int,
        d15: Int,
        d30: Int,
        customDaily: Int,
        maxAds: Int,
        order: Int
    ) = PlacementRateCardEntity(
        placementId = id,
        placementNameEn = nameEn,
        placementNameBn = nameBn,
        moduleName = module,
        allowedSizesCsv = allowedSizes,
        dailyRateBdt = d1,
        sevenDaysRateBdt = d7,
        fifteenDaysRateBdt = d15,
        thirtyDaysRateBdt = d30,
        customDailyRateBdt = customDaily,
        maxActiveAds = maxAds,
        rotationMode = ROTATION_WEIGHTED,
        cardInsertionFrequency = 5,
        status = "ACTIVE",
        sortOrder = order
    )

    /**
     * Merges Admin RTDB rate cards with built-in catalog defaults so Admin customizations
     * (rates, allowed sizes, rotation, active/inactive status, or new custom placements)
     * always override defaults seamlessly.
     */
    fun mergeWithDefaults(cloudCards: List<PlacementRateCardEntity>): List<PlacementRateCardEntity> {
        if (cloudCards.isEmpty()) return defaultRateCards()
        val cloudMap = cloudCards.associateBy { it.placementId.uppercase() }
        val merged = defaultRateCards().map { def ->
            cloudMap[def.placementId.uppercase()] ?: def
        }.toMutableList()
        val knownIds = merged.map { it.placementId.uppercase() }.toSet()
        cloudCards.forEach { custom ->
            if (custom.placementId.uppercase() !in knownIds) {
                merged.add(custom)
            }
        }
        return merged.sortedWith(compareBy<PlacementRateCardEntity> { it.sortOrder }.thenBy { it.placementId })
    }

    fun allowedSizesForCard(card: PlacementRateCardEntity): List<AdSizeSpec> {
        val keys = card.allowedSizesCsv.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (keys.isEmpty()) return STANDARD_AD_SIZES
        return STANDARD_AD_SIZES.filter { it.key.uppercase() in keys }
    }

    fun parseSizeOverrides(jsonStr: String): Map<String, Int> {
        if (jsonStr.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optInt(k, 0)
                if (v > 0) map[k.trim().uppercase()] = v
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun calculatePlacementRate(
        card: PlacementRateCardEntity,
        adSizeKey: String,
        durationDays: Int
    ): Int {
        val days = durationDays.coerceAtLeast(1)
        val baseRate = when (days) {
            1 -> card.dailyRateBdt
            7 -> card.sevenDaysRateBdt
            15 -> card.fifteenDaysRateBdt
            30 -> card.thirtyDaysRateBdt
            else -> {
                if (card.customDailyRateBdt > 0) days * card.customDailyRateBdt
                else ceil(card.thirtyDaysRateBdt.toDouble() / 30.0 * days).toInt()
            }
        }.coerceAtLeast(0)

        val overrides = parseSizeOverrides(card.sizeRateOverridesJson)
        val normSize = adSizeKey.trim().uppercase()
        val size30dOverride = overrides[normSize]
        if (size30dOverride != null && size30dOverride > 0) {
            if (days == 30) return size30dOverride
            return if (card.thirtyDaysRateBdt > 0) {
                (baseRate.toDouble() * (size30dOverride.toDouble() / card.thirtyDaysRateBdt.toDouble())).roundToInt()
                    .coerceAtLeast(1)
            } else {
                ceil(size30dOverride.toDouble() / 30.0 * days).toInt().coerceAtLeast(1)
            }
        }
        return baseRate
    }

    data class PlacementPriceLine(
        val placementId: String,
        val placementNameEn: String,
        val placementNameBn: String,
        val moduleName: String,
        val priceBdt: Int
    )

    data class BookingRateQuote(
        val lines: List<PlacementPriceLine>,
        val subtotalBdt: Int,
        val discountPct: Int,
        val discountBdt: Int,
        val totalBdt: Int,
        val breakdownMap: Map<String, Int>
    )

    fun calculateBookingQuote(
        selectedCards: List<PlacementRateCardEntity>,
        adSizeKey: String,
        durationDays: Int,
        settings: RateCardSettingsEntity
    ): BookingRateQuote {
        val lines = selectedCards.map { card ->
            val price = calculatePlacementRate(card, adSizeKey, durationDays)
            PlacementPriceLine(
                placementId = card.placementId,
                placementNameEn = card.placementNameEn,
                placementNameBn = card.placementNameBn,
                moduleName = card.moduleName,
                priceBdt = price
            )
        }
        val subtotal = lines.sumOf { it.priceBdt }
        val count = lines.size
        val discountPct = if (!settings.bundleEnabled || count < 2) {
            0
        } else {
            when (count) {
                2 -> settings.discount2Pct.coerceIn(0, 80)
                3 -> settings.discount3Pct.coerceIn(0, 80)
                else -> settings.discount4PlusPct.coerceIn(0, 80)
            }
        }
        val discountBdt = (subtotal * discountPct) / 100
        val totalBdt = (subtotal - discountBdt).coerceAtLeast(0)
        val breakdownMap = lines.associate { line ->
            val net = if (subtotal > 0 && discountBdt > 0) {
                (line.priceBdt.toDouble() * totalBdt / subtotal.toDouble()).roundToInt()
            } else {
                line.priceBdt
            }
            line.placementId to net
        }
        return BookingRateQuote(
            lines = lines,
            subtotalBdt = subtotal,
            discountPct = discountPct,
            discountBdt = discountBdt,
            totalBdt = totalBdt,
            breakdownMap = breakdownMap
        )
    }

    fun parsePlacementIds(sponsor: SponsorEntity): Set<String> =
        sponsor.placementIds.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun matchesCanonicalPlacement(sponsor: SponsorEntity, placementId: String): Boolean {
        val pid = placementId.uppercase()
        val explicitIds = parsePlacementIds(sponsor)
        if (explicitIds.isNotEmpty()) {
            return pid in explicitIds || "*" in explicitIds || "ALL" in explicitIds
        }
        val pType = sponsor.placementType.trim().uppercase()
        if (pType == SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO) return false
        if (pid == OTH_POPUP) return pType == SponsorCampaignSelector.PLACEMENT_POPUP
        if (pid == OTH_ABOUT) return pType == SponsorCampaignSelector.PLACEMENT_ABOUT_US

        // Match package-level placement scopes (HOME, DOCTOR_LIST, DOCTOR_DETAILS, HOSPITAL_LIST, DIAGNOSTIC_LIST, SPECIALIST, CHAMBER_SCHEDULE)
        val scopeRaw = sponsor.adPlacement.ifBlank { sponsor.targetPages }.trim().uppercase()
        if (scopeRaw.isEmpty() || scopeRaw == "ALL" || scopeRaw == "ALL_SCREENS") {
            return false // All-screen banners render in dedicated SponsorBannerView/Native slots without duplicating into every module slot
        }
        val tokens = scopeRaw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        return when {
            pid.startsWith("HOME-") -> "HOME" in tokens || "HOME_SCREEN" in tokens
            pid == DOC_01 || pid == DOC_03 -> "DOCTOR_LIST" in tokens || "FIND_DOCTORS" in tokens
            pid == DOC_02 || pid == DOC_04 || pid == DOC_05 -> "DOCTOR_DETAILS" in tokens || "DOCTOR_PROFILE" in tokens
            pid.startsWith("HOS-") -> "HOSPITAL_LIST" in tokens || "HOSPITAL" in tokens || "FACILITIES" in tokens
            pid.startsWith("DIA-") -> "DIAGNOSTIC_LIST" in tokens || "DIAGNOSTIC_CENTER_LIST" in tokens || "DIAGNOSTIC" in tokens || "FACILITIES" in tokens
            pid.startsWith("SPE-") -> "SPECIALIST" in tokens || "MEDICAL_SPECIALIST" in tokens
            pid.startsWith("CHA-") -> "CHAMBER_SCHEDULE" in tokens || "CHAMBER" in tokens
            else -> false
        }
    }

    fun countActiveForPlacement(
        sponsors: List<SponsorEntity>,
        placementId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int = sponsors.count { sp ->
        SponsorCampaignSelector.isCampaignValid(sp, nowMs) && matchesCanonicalPlacement(sp, placementId)
    }

    /**
     * Selects active sponsor(s) for a specific canonical Placement ID, respecting:
     * - Rate Card ACTIVE status (if INACTIVE, returns null immediately)
     * - Server-authoritative expiry check
     * - Maximum Active Ads cap (`maxActiveAds`)
     * - Rotation strategy (`WEIGHTED_ROTATION`, `PRIORITY_FIRST`, `SEQUENTIAL`, `RANDOM`)
     */
    fun selectSponsorForPlacement(
        sponsors: List<SponsorEntity>,
        rateCards: List<PlacementRateCardEntity>,
        placementId: String,
        rotationTick: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): SponsorEntity? {
        val card = mergeWithDefaults(rateCards).firstOrNull {
            it.placementId.equals(placementId, ignoreCase = true)
        }
        if (card != null && !card.status.equals("ACTIVE", ignoreCase = true)) {
            return null
        }
        val maxAds = (card?.maxActiveAds ?: 3).coerceAtLeast(1)
        val rotationMode = (card?.rotationMode ?: ROTATION_WEIGHTED).uppercase()

        val eligible = sponsors
            .filter { sp ->
                SponsorCampaignSelector.isCampaignValid(sp, nowMs) &&
                    matchesCanonicalPlacement(sp, placementId)
            }
            .sortedWith(
                compareByDescending<SponsorEntity> { it.displayPriority }
                    .thenByDescending { it.displayFrequency }
                    .thenByDescending { it.updatedAt }
            )
            .take(maxAds)

        if (eligible.isEmpty()) return null

        return when (rotationMode) {
            ROTATION_PRIORITY -> eligible.first()
            ROTATION_SEQUENTIAL -> {
                val idx = ((rotationTick % eligible.size) + eligible.size) % eligible.size
                eligible[idx]
            }
            ROTATION_RANDOM -> {
                val seed = (nowMs / 60_000L + placementId.hashCode() + rotationTick).toInt()
                val idx = ((seed % eligible.size) + eligible.size) % eligible.size
                eligible[idx]
            }
            else -> {
                val pool = mutableListOf<SponsorEntity>()
                eligible.forEach { sp ->
                    val weight = sp.displayFrequency.coerceIn(1, 5)
                    repeat(weight) { pool.add(sp) }
                }
                val idx = ((rotationTick % pool.size) + pool.size) % pool.size
                pool[idx]
            }
        }
    }

    fun insertionFrequencyForPlacement(
        rateCards: List<PlacementRateCardEntity>,
        placementId: String,
        fallback: Int = 5
    ): Int {
        val card = mergeWithDefaults(rateCards).firstOrNull {
            it.placementId.equals(placementId, ignoreCase = true)
        }
        return (card?.cardInsertionFrequency ?: fallback).coerceIn(2, 20)
    }
}
