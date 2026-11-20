package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.*
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.ads.SponsorOfflineCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Syncs live directory data managed via index.html Admin Portal from the
 * Firebase Realtime Database REST endpoint directly into the local Room database.
 */
object RtdbSyncService {
    private const val BASE_URL = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"
    private const val TAG = "RtdbSyncService"

    suspend fun pullDirectoryIntoRoom(db: AppDatabase, context: Context? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$BASE_URL/directory.json").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext Result.failure(IllegalStateException("HTTP $code"))
            }
            // Capture authoritative server timestamp from HTTP Date header (prevents offline clock rollback abuse)
            val serverHeaderDateMs = conn.date.takeIf { it > 0L } ?: System.currentTimeMillis()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            if (context != null) {
                SessionPreferences(context.applicationContext).saveServerSyncAnchor(serverHeaderDateMs)
            }

            if (body.isBlank() || body == "null") {
                return@withContext Result.success(0)
            }
            val root = JSONObject(body)
            var syncedDoctors = 0

            // Specialties
            root.optJSONObject("specialties")?.let { obj ->
                val list = parseMap(obj) { id, o ->
                    SpecialtyEntity(
                        id = id,
                        nameBn = o.optString("nameBn", o.optString("nameEn")),
                        nameEn = o.optString("nameEn", o.optString("nameBn")),
                        iconKey = o.optString("iconKey", "medical")
                    )
                }
                if (list.isNotEmpty()) {
                    db.specialtyDao().deleteAll()
                    db.specialtyDao().insertAll(list)
                }
            }

            // Locations (preserve all 64 Bangladesh districts + merge Admin custom locations)
            root.optJSONObject("locations")?.let { obj ->
                val list = parseMap(obj) { id, o ->
                    LocationEntity(
                        id = id,
                        division = o.optString("division", ""),
                        district = o.optString("district", ""),
                        upazila = o.optString("upazila", ""),
                        area = o.optString("area", "")
                    )
                }
                if (list.isNotEmpty()) {
                    db.locationDao().insertAll(list)
                }
            }

            // Doctors
            if (root.has("doctors")) {
                val obj = root.optJSONObject("doctors")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    val fallbackName = o.optString("doctorName", "")
                    val nameBn = o.optString("fullNameBn", "").ifBlank { fallbackName }
                    val nameEn = o.optString("fullNameEn", "").ifBlank { fallbackName.ifBlank { nameBn } }
                    val qual = o.optString("qualification", "")
                        .ifBlank { o.optString("degree", "") }
                        .ifBlank { o.optString("trainings", "") }
                    DoctorEntity(
                        id = id,
                        userId = o.optString("userId", ""),
                        fullNameBn = nameBn,
                        fullNameEn = nameEn,
                        photoUrl = o.optString("photoUrl", ""),
                        qualification = qual,
                        bmdcNumber = o.optString("bmdcNumber", ""),
                        bmdcVerified = o.optBoolean("bmdcVerified", false),
                        verificationStatus = o.optString("verificationStatus", "PENDING"),
                        approvalStatus = o.optString("approvalStatus", "APPROVED"),
                        verificationNotes = o.optString("verificationNotes", ""),
                        gender = o.optString("gender", ""),
                        bioBn = o.optString("bioBn", ""),
                        bioEn = o.optString("bioEn", ""),
                        contactNumber = o.optString("contactNumber", ""),
                        mainSpecialtyId = o.optString("mainSpecialtyId", ""),
                        subSpecialty = o.optString("subSpecialty", ""),
                        expertise = o.optString("expertise", ""),
                        currentHospital = o.optString("currentHospital", ""),
                        currentMedicalCollege = o.optString("currentMedicalCollege", ""),
                        currentDepartment = o.optString("currentDepartment", ""),
                        currentDesignation = o.optString("currentDesignation", ""),
                        employmentStartDate = o.optString("employmentStartDate", ""),
                        totalExperienceYears = o.optInt("totalExperienceYears", 0),
                        teachingExperienceYears = o.optInt("teachingExperienceYears", 0),
                        trainings = o.optString("trainings", ""),
                        isActive = o.optBoolean("isActive", true),
                        isFeatured = o.optBoolean("isFeatured", false),
                        isDemoData = false,
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                } else emptyList()
                db.doctorDao().deleteAll()
                if (list.isNotEmpty()) db.doctorDao().insertAll(list)
                syncedDoctors = list.size
            }

            // Education
            if (root.has("education")) {
                val obj = root.optJSONObject("education")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    EducationEntity(
                        id = id,
                        doctorId = o.optString("doctorId", ""),
                        degreeName = o.optString("degreeName", ""),
                        institution = o.optString("institution", ""),
                        passingYear = o.optString("passingYear", ""),
                        specialTraining = o.optString("specialTraining", "")
                    )
                } else emptyList()
                db.educationDao().deleteAll()
                if (list.isNotEmpty()) db.educationDao().insertAll(list)
            }

            // Employment History
            if (root.has("employment_history")) {
                val obj = root.optJSONObject("employment_history")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    EmploymentHistoryEntity(
                        id = id,
                        doctorId = o.optString("doctorId", ""),
                        hospitalName = o.optString("hospitalName", ""),
                        medicalCollege = o.optString("medicalCollege", ""),
                        designation = o.optString("designation", ""),
                        department = o.optString("department", ""),
                        startDate = o.optString("startDate", ""),
                        endDate = o.optString("endDate", ""),
                        description = o.optString("description", "")
                    )
                } else emptyList()
                db.employmentDao().deleteAll()
                if (list.isNotEmpty()) db.employmentDao().insertAll(list)
            }

            // Chambers
            if (root.has("chambers")) {
                val obj = root.optJSONObject("chambers")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    ChamberEntity(
                        id = id,
                        doctorId = o.optString("doctorId", ""),
                        facilityName = o.optString("facilityName", ""),
                        facilityType = o.optString("facilityType", "HOSPITAL"),
                        chamberName = o.optString("chamberName", ""),
                        district = o.optString("district", ""),
                        upazila = o.optString("upazila", ""),
                        fullAddress = o.optString("fullAddress", ""),
                        locationArea = o.optString("locationArea", ""),
                        googleMapsUrl = o.optString("googleMapsUrl", ""),
                        serialPhone = o.optString("serialPhone", ""),
                        instructions = o.optString("instructions", ""),
                        isActive = o.optBoolean("isActive", true)
                    )
                } else emptyList()
                db.chamberDao().deleteAll()
                if (list.isNotEmpty()) db.chamberDao().insertAll(list)
            }

            // Chamber Schedules
            if (root.has("chamber_schedules")) {
                val obj = root.optJSONObject("chamber_schedules")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    ChamberScheduleEntity(
                        id = id,
                        chamberId = o.optString("chamberId", ""),
                        doctorId = o.optString("doctorId", ""),
                        dayOfWeek = o.optString("dayOfWeek", "SATURDAY"),
                        openTime = o.optString("openTime", ""),
                        closeTime = o.optString("closeTime", ""),
                        slotLabel = o.optString("slotLabel", "")
                    )
                } else emptyList()
                db.chamberScheduleDao().deleteAll()
                if (list.isNotEmpty()) db.chamberScheduleDao().insertAll(list)
            }

            // Hospitals
            if (root.has("hospitals")) {
                val obj = root.optJSONObject("hospitals")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    HospitalEntity(
                        id = id,
                        managerUserId = o.optString("managerUserId", ""),
                        nameBn = o.optString("nameBn", ""),
                        nameEn = o.optString("nameEn", ""),
                        district = o.optString("district", ""),
                        upazila = o.optString("upazila", ""),
                        address = o.optString("address", ""),
                        contactNumber = o.optString("contactNumber", ""),
                        imageUrl = o.optString("imageUrl", ""),
                        status = o.optString("status", "ACTIVE"),
                        isDemoData = false
                    )
                } else emptyList()
                db.hospitalDao().deleteAll()
                if (list.isNotEmpty()) db.hospitalDao().insertAll(list)
            }

            // Diagnostic Centers
            if (root.has("diagnostic_centers")) {
                val obj = root.optJSONObject("diagnostic_centers")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    DiagnosticCenterEntity(
                        id = id,
                        nameBn = o.optString("nameBn", ""),
                        nameEn = o.optString("nameEn", ""),
                        district = o.optString("district", ""),
                        upazila = o.optString("upazila", ""),
                        address = o.optString("address", ""),
                        contactNumber = o.optString("contactNumber", ""),
                        imageUrl = o.optString("imageUrl", ""),
                        status = o.optString("status", "ACTIVE"),
                        isDemoData = false
                    )
                } else emptyList()
                db.diagnosticCenterDao().deleteAll()
                if (list.isNotEmpty()) db.diagnosticCenterDao().insertAll(list)
            }

            // Notifications
            if (root.has("notifications")) {
                val obj = root.optJSONObject("notifications")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    NotificationEntity(
                        id = id,
                        titleBn = o.optString("titleBn", ""),
                        titleEn = o.optString("titleEn", ""),
                        messageBn = o.optString("messageBn", ""),
                        messageEn = o.optString("messageEn", ""),
                        targetRole = o.optString("targetRole", "ALL"),
                        isRead = false,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                } else emptyList()
                db.notificationDao().deleteAll()
                if (list.isNotEmpty()) db.notificationDao().insertAll(list)
            }

            // Site Settings
            root.optJSONObject("settings")?.let { s ->
                db.settingsDao().save(
                    SiteSettingsEntity(
                        id = "global",
                        siteTitle = s.optString("siteTitle", "Doctor List BD"),
                        taglineBn = s.optString("taglineBn", "ডাক্তার কোথায় বসেন, কখন বসেন — সব তথ্য এক জায়গায়।"),
                        taglineEn = s.optString("taglineEn", "Where doctors sit & when they sit — all verified chamber info in one place."),
                        contactEmail = s.optString("contactEmail", "support@doctorlistbd.org"),
                        contactPhone = s.optString("contactPhone", "+880 1700-000000"),
                        allowOnlineBooking = s.optBoolean("allowOnlineBooking", true),
                        showDemoData = false
                    )
                )
            }

            // Hybrid Ad Configuration: ONLINE (Start.io + Unity Ads + Direct Sponsors) | OFFLINE (Direct Sponsors ONLY)
            val adCfgObj = root.optJSONObject("ad_config")
            val aboutCfgObj = root.optJSONObject("about_us_ad_settings")
            if (adCfgObj != null || aboutCfgObj != null) {
                db.adConfigDao().save(
                    AdConfigEntity(
                        id = "global",
                        adsEnabled = adCfgObj?.optBoolean("adsEnabled", true) ?: true,
                        activeNetwork = adCfgObj?.optString("activeNetwork", "BOTH_UNITY_PRIMARY")
                            ?.ifBlank { "BOTH_UNITY_PRIMARY" } ?: "BOTH_UNITY_PRIMARY",
                        testMode = adCfgObj?.optBoolean("testMode", false) ?: false,
                        bannerEnabled = adCfgObj?.optBoolean("bannerEnabled", true) ?: true,
                        appOpenEnabled = adCfgObj?.optBoolean("appOpenEnabled", true) ?: true,
                        nativeBannerEnabled = adCfgObj?.optBoolean("nativeBannerEnabled", true) ?: true,
                        rewardedEnabled = adCfgObj?.optBoolean("rewardedEnabled", true) ?: true,
                        nativeAdInterval = (adCfgObj?.optInt("nativeAdInterval", 5) ?: 5).coerceIn(3, 12),
                        appOpenCooldownSeconds = (adCfgObj?.optInt("appOpenCooldownSeconds", 120) ?: 120).coerceIn(15, 3600),
                        unityGameId = cleanOptString(adCfgObj, "unityGameId", ""),
                        unityBannerPlacement = cleanOptString(adCfgObj, "unityBannerPlacement", "Banner_Android")
                            .ifBlank { "Banner_Android" },
                        unityAppOpenPlacement = cleanOptString(adCfgObj, "unityAppOpenPlacement", "Interstitial_Android")
                            .ifBlank { "Interstitial_Android" },
                        unityNativeBannerPlacement = cleanOptString(adCfgObj, "unityNativeBannerPlacement", "Banner_Android")
                            .ifBlank { "Banner_Android" },
                        unityRewardedPlacement = cleanOptString(adCfgObj, "unityRewardedPlacement", "Rewarded_Android")
                            .ifBlank { "Rewarded_Android" },
                        startIoAppId = cleanOptString(adCfgObj, "startIoAppId", "210630916")
                            .ifBlank { "210630916" },
                        sponsorBannerEnabled = adCfgObj?.optBoolean("sponsorBannerEnabled", true) ?: true,
                        appOpenPopupEnabled = adCfgObj?.optBoolean("appOpenPopupEnabled", true) ?: true,
                        appOpenPopupFrequency = adCfgObj?.optString("appOpenPopupFrequency", SponsorCampaignSelector.FREQ_EVERY_LAUNCH)
                            ?.ifBlank { SponsorCampaignSelector.FREQ_EVERY_LAUNCH }
                            ?: SponsorCampaignSelector.FREQ_EVERY_LAUNCH,
                        appOpenPopupSponsorId = adCfgObj?.optString("appOpenPopupSponsorId", "")?.trim().orEmpty(),
                        nativeSponsorEnabled = adCfgObj?.optBoolean("nativeSponsorEnabled", true) ?: true,
                        nativeSponsorInterval = (adCfgObj?.optInt("nativeSponsorInterval", 5) ?: 5).coerceIn(4, 6),
                        aboutUsSponsorEnabled = aboutCfgObj?.optBoolean("enabled", true)
                            ?: adCfgObj?.optBoolean("aboutUsSponsorEnabled", true) ?: true,
                        aboutUsSponsorTitleBn = aboutCfgObj?.optString("sectionTitleBn", "")
                            ?.ifBlank { "আমাদের স্পন্সর পার্টনার" } ?: "আমাদের স্পন্সর পার্টনার",
                        aboutUsSponsorTitleEn = aboutCfgObj?.optString("sectionTitleEn", "")
                            ?.ifBlank { "Our Sponsor Partners" } ?: "Our Sponsor Partners",
                        aboutUsSponsorMaxAds = (aboutCfgObj?.optInt("maxAds", 3) ?: 3).coerceIn(1, 10),
                        aboutUsSponsorLayout = aboutCfgObj?.optString("layoutStyle", "CARDS")
                            ?.ifBlank { "CARDS" } ?: "CARDS",
                        aboutUsFeaturedAdId = aboutCfgObj?.optString("featuredAdId", "")?.trim().orEmpty(),
                        updatedAt = maxOf(
                            adCfgObj?.optLong("updatedAt", 0L) ?: 0L,
                            aboutCfgObj?.optLong("updatedAt", 0L) ?: 0L,
                            serverHeaderDateMs
                        )
                    )
                )
            }

            // Dynamic Advertising Packages (Never hardcoded!)
            if (root.has("ad_packages")) {
                val obj = root.optJSONObject("ad_packages")
                val pkgList = if (obj != null) parseMap(obj) { id, o ->
                    SponsorPackageEntity(
                        id = id,
                        name = o.optString("name", "").trim(),
                        widthPx = o.optInt("widthPx", 300).coerceAtLeast(100),
                        heightPx = o.optInt("heightPx", 200).coerceAtLeast(50),
                        durationDays = o.optInt("durationDays", 30).coerceAtLeast(1),
                        priceBdt = o.optInt("priceBdt", 0).coerceAtLeast(0),
                        placementType = o.optString("placementType", SponsorCampaignSelector.PLACEMENT_BANNER_300x200)
                            .ifBlank { SponsorCampaignSelector.PLACEMENT_BANNER_300x200 },
                        description = o.optString("description", "").trim(),
                        status = o.optString("status", "ACTIVE").ifBlank { "ACTIVE" },
                        updatedAt = o.optLong("updatedAt", serverHeaderDateMs)
                    )
                } else emptyList()
                db.sponsorPackageDao().deleteAll()
                if (pkgList.isNotEmpty()) db.sponsorPackageDao().insertAll(pkgList)
            }

            // Placement Rate Cards & Global Bundle Discount Settings
            if (root.has("placement_rate_cards")) {
                val obj = root.optJSONObject("placement_rate_cards")
                val cardList = if (obj != null) parseMap(obj) { id, o ->
                    PlacementRateCardEntity(
                        placementId = o.optString("placementId", id).ifBlank { id },
                        placementNameEn = o.optString("placementNameEn", o.optString("name", id)).trim(),
                        placementNameBn = o.optString("placementNameBn", "").trim(),
                        moduleName = o.optString("moduleName", "OTHER").ifBlank { "OTHER" }.uppercase(),
                        allowedSizesCsv = o.optString("allowedSizesCsv", "320x50,320x100,300x200,300x250,RESPONSIVE,FULL_WIDTH"),
                        sizeRateOverridesJson = o.optJSONObject("sizeRateOverrides")?.toString()
                            ?: o.optString("sizeRateOverridesJson", ""),
                        customWidthPx = o.optInt("customWidthPx", 300).coerceAtLeast(50),
                        customHeightPx = o.optInt("customHeightPx", 200).coerceAtLeast(30),
                        dailyRateBdt = o.optInt("dailyRateBdt", 30).coerceAtLeast(0),
                        sevenDaysRateBdt = o.optInt("sevenDaysRateBdt", 150).coerceAtLeast(0),
                        fifteenDaysRateBdt = o.optInt("fifteenDaysRateBdt", 220).coerceAtLeast(0),
                        thirtyDaysRateBdt = o.optInt("thirtyDaysRateBdt", 300).coerceAtLeast(0),
                        customDailyRateBdt = o.optInt("customDailyRateBdt", 20).coerceAtLeast(0),
                        maxActiveAds = o.optInt("maxActiveAds", 3).coerceAtLeast(1),
                        rotationMode = o.optString("rotationMode", "WEIGHTED_ROTATION").ifBlank { "WEIGHTED_ROTATION" },
                        cardInsertionFrequency = o.optInt("cardInsertionFrequency", 5).coerceIn(2, 20),
                        status = o.optString("status", "ACTIVE").ifBlank { "ACTIVE" }.uppercase(),
                        sortOrder = o.optInt("sortOrder", 0),
                        updatedAt = o.optLong("updatedAt", serverHeaderDateMs)
                    )
                } else emptyList()
                db.placementRateCardDao().deleteAll()
                if (cardList.isNotEmpty()) db.placementRateCardDao().insertAll(cardList)
            }

            root.optJSONObject("rate_card_settings")?.let { rcs ->
                db.rateCardSettingsDao().save(
                    RateCardSettingsEntity(
                        id = "global",
                        bundleEnabled = rcs.optBoolean("bundleEnabled", true),
                        discount2Pct = rcs.optInt("discount2Pct", 5).coerceIn(0, 80),
                        discount3Pct = rcs.optInt("discount3Pct", 10).coerceIn(0, 80),
                        discount4PlusPct = rcs.optInt("discount4PlusPct", 15).coerceIn(0, 80),
                        allowCustomDuration = rcs.optBoolean("allowCustomDuration", true),
                        updatedAt = rcs.optLong("updatedAt", serverHeaderDateMs)
                    )
                )
            }

            // Manual Payment Settings (bKash & Nagad configured by Admin)
            root.optJSONObject("payment_settings")?.let { p ->
                db.paymentSettingsDao().save(
                    PaymentSettingsEntity(
                        id = "global",
                        bkashNumber = p.optString("bkashNumber", "").trim(),
                        bkashAccountType = p.optString("bkashAccountType", "Personal").ifBlank { "Personal" },
                        nagadNumber = p.optString("nagadNumber", "").trim(),
                        nagadAccountType = p.optString("nagadAccountType", "Personal").ifBlank { "Personal" },
                        instructionsBn = p.optString("instructionsBn", "").trim(),
                        instructionsEn = p.optString("instructionsEn", "").trim(),
                        updatedAt = p.optLong("updatedAt", serverHeaderDateMs)
                    )
                )
            }

            // Direct Sponsor Advertisements (Server-authoritative status, activatedAt & expiryAt; removes expired after verified update)
            if (root.has("sponsors")) {
                val obj = root.optJSONObject("sponsors")
                val existingMap = db.sponsorDao().getAllOnce().associateBy { it.id }
                val list = if (obj != null) parseMap(obj) { id, o ->
                    val prev = existingMap[id]
                    val startDate = o.optString("startDate", "").trim()
                    val endDate = o.optString("endDate", "").trim()
                    val (startTs, endTs) = SponsorCampaignSelector.parseCampaignBounds(startDate, endDate)
                    val activatedAt = o.optLong("activatedAt", 0L).let { if (it > 0L) it else startTs }
                    val rawExpiryAt = o.optLong("expiryAt", 0L)
                    val expiryAt = if (rawExpiryAt > 0L) rawExpiryAt else endTs
                    val rawStatus = o.optString("status", if (o.optBoolean("isActive", true)) "ACTIVE" else "PAUSED")
                    val effectiveStatus = if (rawStatus.equals("ACTIVE", true) && expiryAt < Long.MAX_VALUE && serverHeaderDateMs > expiryAt) {
                        SponsorCampaignSelector.STATUS_EXPIRED
                    } else {
                        rawStatus
                    }
                    val logoUrl = o.optString("logoUrl", "").trim()
                    val bannerUrl = o.optString("bannerImageUrl", "").trim()
                    SponsorEntity(
                        id = id,
                        advertiserId = o.optString("advertiserId", "").trim(),
                        submittedBy = o.optString("submittedBy", "ADMIN").ifBlank { "ADMIN" },
                        packageId = o.optString("packageId", "").trim(),
                        packageName = o.optString("packageName", "").trim(),
                        widthPx = o.optInt("widthPx", 320).coerceAtLeast(100),
                        heightPx = o.optInt("heightPx", 50).coerceAtLeast(40),
                        durationDays = o.optInt("durationDays", 30).coerceAtLeast(1),
                        priceBdt = o.optInt("priceBdt", 0).coerceAtLeast(0),
                        placementType = o.optString("placementType", SponsorCampaignSelector.PLACEMENT_ALL)
                            .ifBlank { SponsorCampaignSelector.PLACEMENT_ALL },
                        status = effectiveStatus,
                        name = o.optString("name", o.optString("sponsorName", "")).trim(),
                        logoUrl = logoUrl,
                        bannerImageUrl = bannerUrl,
                        localLogoPath = if (prev != null && prev.logoUrl == logoUrl) prev.localLogoPath else "",
                        localBannerPath = if (prev != null && prev.bannerImageUrl == bannerUrl) prev.localBannerPath else "",
                        promoTitle = o.optString("promoTitle", o.optString("title", "")).trim(),
                        description = o.optString("description", "").trim(),
                        websiteUrl = o.optString("websiteUrl", "").trim(),
                        contactNumber = o.optString("contactNumber", "").trim(),
                        startDate = startDate,
                        endDate = endDate,
                        activatedAt = activatedAt,
                        expiryAt = expiryAt,
                        startTimestamp = startTs,
                        endTimestamp = endTs,
                        targetPages = o.optString("targetPages", SponsorCampaignSelector.PAGE_ALL).ifBlank { SponsorCampaignSelector.PAGE_ALL },
                        displayPriority = o.optInt("displayPriority", 10).coerceIn(1, 100),
                        displayFrequency = o.optInt("displayFrequency", 1).coerceIn(1, 10),
                        isActive = o.optBoolean("isActive", true) && effectiveStatus.equals("ACTIVE", true),
                        renewalOfAdId = o.optString("renewalOfAdId", "").trim(),
                        totalViews = o.optInt("totalViews", 0),
                        uniqueViews = o.optInt("uniqueViews", 0),
                        cachedAt = prev?.cachedAt ?: 0L,
                        updatedAt = o.optLong("updatedAt", serverHeaderDateMs),
                        placementIds = o.optString("placementIds", "").trim(),
                        moduleNames = o.optString("moduleNames", "").trim(),
                        adSizeKey = o.optString("adSizeKey", "").trim(),
                        placementBreakdownJson = o.optJSONObject("placementBreakdown")?.toString()
                            ?: o.optString("placementBreakdownJson", ""),
                        subtotalBdt = o.optInt("subtotalBdt", 0),
                        bundleDiscountBdt = o.optInt("bundleDiscountBdt", 0)
                    )
                }.filter { sp ->
                    // Verified server update: keep only active/non-expired ads in public offline cache
                    sp.status.equals(SponsorCampaignSelector.STATUS_ACTIVE, true) &&
                        !SponsorCampaignSelector.isCampaignExpired(sp, serverHeaderDateMs)
                } else emptyList()

                db.sponsorDao().deleteAll()
                if (list.isNotEmpty()) {
                    db.sponsorDao().insertAll(list)
                }
                if (context != null) {
                    SponsorOfflineCacheManager.syncSponsorAssets(context, db)
                }
            }

            Result.success(syncedDoctors)
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync skipped/failed (using local Room cache): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun pushReport(report: ReportEntity) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", report.id)
                put("doctorId", report.doctorId)
                put("reason", report.reason)
                put("details", report.details)
                put("status", report.status)
                put("createdAt", report.createdAt)
            }
            putJson("$BASE_URL/directory/reports/${report.id}.json", json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Push report offline: ${e.message}")
        }
    }

    suspend fun pushAppointment(apt: AppointmentRequestEntity) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", apt.id)
                put("patientName", apt.patientName)
                put("patientPhone", apt.patientPhone)
                put("doctorId", apt.doctorId)
                put("chamberId", apt.chamberId)
                put("preferredDay", apt.preferredDay)
                put("preferredSlot", apt.preferredSlot)
                put("notes", apt.notes)
                put("status", apt.status)
                put("createdAt", apt.createdAt)
            }
            putJson("$BASE_URL/directory/appointments/${apt.id}.json", json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Push appointment offline: ${e.message}")
        }
    }

    private fun putJson(url: String, payload: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 6000
            readTimeout = 6000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(payload) }
        conn.responseCode
        conn.disconnect()
    }

    private fun cleanOptString(obj: JSONObject?, key: String, fallback: String = ""): String {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return fallback
        val v = obj.optString(key, fallback).trim()
        return if (v.equals("null", ignoreCase = true)) fallback else v
    }

    private fun <T> parseMap(obj: JSONObject, mapper: (String, JSONObject) -> T): List<T> {
        val out = mutableListOf<T>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = obj.optJSONObject(key)
            if (item != null) out.add(mapper(key, item))
        }
        return out
    }
}
