package com.example.data.remote

import android.util.Log
import com.example.data.local.AdvertiserSession
import com.example.data.local.PaymentSettingsEntity
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.RateCardSettingsEntity
import com.example.data.local.SponsorEntity
import com.example.data.local.SponsorPackageEntity
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.SponsorCampaignSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class AdvertiserPaymentRecord(
    val id: String,
    val adId: String,
    val advertiserId: String,
    val packageId: String,
    val packageName: String,
    val isRenewal: Boolean,
    val paymentMethod: String,
    val senderMobile: String,
    val transactionId: String,
    val amountBdt: Int,
    val screenshotUrl: String,
    val status: String, // PENDING | VERIFIED | REJECTED
    val adminNote: String,
    val createdAt: Long,
    val verifiedAt: Long,
    val placementIds: String = "",
    val moduleNames: String = "",
    val adSizeKey: String = "",
    val placementBreakdownJson: String = "",
    val subtotalBdt: Int = 0,
    val bundleDiscountBdt: Int = 0
)

data class AdvertiserDashboardData(
    val myAds: List<SponsorEntity>,
    val myPayments: List<AdvertiserPaymentRecord>,
    val packages: List<SponsorPackageEntity>,
    val paymentSettings: PaymentSettingsEntity,
    val rateCards: List<PlacementRateCardEntity> = PlacementRateCardEngine.defaultRateCards(),
    val rateCardSettings: RateCardSettingsEntity = RateCardSettingsEntity(),
    val activePlacementCounts: Map<String, Int> = emptyMap()
)

/**
 * Production REST service for the Direct Sponsor Advertiser Account System,
 * Advertisement Submission, Manual bKash/Nagad Payment Verification Queue,
 * Advertiser Dashboard, and Expired Advertisement Renewal.
 */
object AdvertiserRemoteService {
    private const val TAG = "AdvertiserRemote"
    private const val BASE_URL = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app/directory"

    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun registerAdvertiser(
        businessName: String,
        ownerName: String,
        mobile: String,
        email: String,
        address: String,
        password: String
    ): Result<AdvertiserSession> = withContext(Dispatchers.IO) {
        try {
            val cleanBiz = businessName.trim()
            val cleanOwner = ownerName.trim()
            val cleanMobile = mobile.replace(Regex("[^0-9+]"), "").trim()
            val cleanEmail = email.trim().lowercase()
            val cleanAddress = address.trim()

            if (cleanBiz.length < 2) return@withContext Result.failure(IllegalArgumentException("Business / Institution Name is required"))
            if (cleanOwner.length < 2) return@withContext Result.failure(IllegalArgumentException("Owner Name is required"))
            if (!SponsorCampaignSelector.isValidBangladeshMobile(cleanMobile)) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid Bangladesh mobile number (01XXXXXXXXX)"))
            }
            if (cleanAddress.length < 3) return@withContext Result.failure(IllegalArgumentException("Address is required"))
            if (password.length < 6) return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))

            // Check mobile uniqueness
            val existingObj = getJsonObject("$BASE_URL/advertisers.json")
            val normMobile = normalizeMobile(cleanMobile)
            if (existingObj != null) {
                val keys = existingObj.keys()
                while (keys.hasNext()) {
                    val item = existingObj.optJSONObject(keys.next()) ?: continue
                    if (normalizeMobile(item.optString("mobile", "")) == normMobile) {
                        return@withContext Result.failure(IllegalStateException("An advertiser account with this mobile number already exists."))
                    }
                }
            }

            val id = "adv_${UUID.randomUUID().toString().take(10)}"
            val salt = generateSalt()
            val passwordHash = hashPassword(password, salt)
            val now = System.currentTimeMillis()
            val payload = JSONObject().apply {
                put("id", id)
                put("businessName", cleanBiz)
                put("ownerName", cleanOwner)
                put("mobile", cleanMobile)
                put("email", cleanEmail)
                put("address", cleanAddress)
                put("salt", salt)
                put("passwordHash", passwordHash)
                put("status", "ACTIVE")
                put("createdAt", now)
                put("updatedAt", now)
            }

            if (!putJson("$BASE_URL/advertisers/$id.json", payload.toString())) {
                return@withContext Result.failure(IllegalStateException("Could not save advertiser account. Check internet connection."))
            }

            Result.success(
                AdvertiserSession(
                    id = id,
                    businessName = cleanBiz,
                    ownerName = cleanOwner,
                    mobile = cleanMobile,
                    email = cleanEmail,
                    address = cleanAddress
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Register advertiser error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loginAdvertiser(mobileOrEmail: String, password: String): Result<AdvertiserSession> =
        withContext(Dispatchers.IO) {
            try {
                val identifier = mobileOrEmail.trim()
                if (identifier.isBlank() || password.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Mobile/Email and password are required"))
                }
                val existingObj = getJsonObject("$BASE_URL/advertisers.json")
                    ?: return@withContext Result.failure(IllegalStateException("Account not found"))

                val normInputMobile = normalizeMobile(identifier)
                val lowerInput = identifier.lowercase()
                val keys = existingObj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = existingObj.optJSONObject(id) ?: continue
                    val itemMobile = normalizeMobile(item.optString("mobile", ""))
                    val itemEmail = item.optString("email", "").trim().lowercase()
                    if ((normInputMobile.isNotEmpty() && itemMobile == normInputMobile) ||
                        (itemEmail.isNotEmpty() && itemEmail == lowerInput)
                    ) {
                        val status = item.optString("status", "ACTIVE")
                        if (!status.equals("ACTIVE", true)) {
                            return@withContext Result.failure(IllegalStateException("This advertiser account is suspended by admin."))
                        }
                        val salt = item.optString("salt", "")
                        val expectedHash = item.optString("passwordHash", "")
                        val computed = hashPassword(password, salt)
                        if (computed != expectedHash) {
                            return@withContext Result.failure(IllegalArgumentException("Incorrect password"))
                        }
                        return@withContext Result.success(
                            AdvertiserSession(
                                id = id,
                                businessName = item.optString("businessName", ""),
                                ownerName = item.optString("ownerName", ""),
                                mobile = item.optString("mobile", ""),
                                email = item.optString("email", ""),
                                address = item.optString("address", "")
                            )
                        )
                    }
                }
                Result.failure(IllegalArgumentException("Advertiser account not found"))
            } catch (e: Exception) {
                Log.w(TAG, "Login advertiser error: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Fetches ONLY the authenticated advertiser's own ads and payments (strict ownership isolation).
     */
    suspend fun fetchAdvertiserDashboard(advertiserId: String): Result<AdvertiserDashboardData> =
        withContext(Dispatchers.IO) {
            try {
                if (advertiserId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Not authenticated"))
                }
                val root = getJsonObject("$BASE_URL.json") ?: JSONObject()

                // 1. Dynamic Active Packages
                val packages = mutableListOf<SponsorPackageEntity>()
                root.optJSONObject("ad_packages")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val o = obj.optJSONObject(id) ?: continue
                        packages.add(
                            SponsorPackageEntity(
                                id = id,
                                name = o.optString("name", ""),
                                widthPx = o.optInt("widthPx", 300),
                                heightPx = o.optInt("heightPx", 200),
                                durationDays = o.optInt("durationDays", 30),
                                priceBdt = o.optInt("priceBdt", 0),
                                placementType = o.optString("placementType", SponsorCampaignSelector.PLACEMENT_BANNER_300x200),
                                description = o.optString("description", ""),
                                status = o.optString("status", "ACTIVE"),
                                updatedAt = o.optLong("updatedAt", 0L)
                            )
                        )
                    }
                }

                // 2. bKash & Nagad Payment Settings
                val payCfgObj = root.optJSONObject("payment_settings")
                val paymentSettings = PaymentSettingsEntity(
                    id = "global",
                    bkashNumber = payCfgObj?.optString("bkashNumber", "")?.trim().orEmpty(),
                    bkashAccountType = payCfgObj?.optString("bkashAccountType", "Personal")?.ifBlank { "Personal" } ?: "Personal",
                    nagadNumber = payCfgObj?.optString("nagadNumber", "")?.trim().orEmpty(),
                    nagadAccountType = payCfgObj?.optString("nagadAccountType", "Personal")?.ifBlank { "Personal" } ?: "Personal",
                    instructionsBn = payCfgObj?.optString("instructionsBn", "").orEmpty(),
                    instructionsEn = payCfgObj?.optString("instructionsEn", "").orEmpty(),
                    updatedAt = payCfgObj?.optLong("updatedAt", 0L) ?: 0L
                )

                // 3. Only this advertiser's own ads + compute active slot counts across all sponsors
                val myAds = mutableListOf<SponsorEntity>()
                val allSponsorsForCounts = mutableListOf<SponsorEntity>()
                root.optJSONObject("sponsors")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val o = obj.optJSONObject(id) ?: continue
                        val startDate = o.optString("startDate", "")
                        val endDate = o.optString("endDate", "")
                        val (sTs, eTs) = SponsorCampaignSelector.parseCampaignBounds(startDate, endDate)
                        val entity = SponsorEntity(
                            id = id,
                            advertiserId = o.optString("advertiserId", ""),
                            submittedBy = o.optString("submittedBy", "ADVERTISER"),
                            packageId = o.optString("packageId", ""),
                            packageName = o.optString("packageName", ""),
                            widthPx = o.optInt("widthPx", 300),
                            heightPx = o.optInt("heightPx", 200),
                            durationDays = o.optInt("durationDays", 30),
                            priceBdt = o.optInt("priceBdt", 0),
                            placementType = o.optString("placementType", SponsorCampaignSelector.PLACEMENT_BANNER_300x200),
                            placementIds = o.optString("placementIds", ""),
                            moduleNames = o.optString("moduleNames", ""),
                            adSizeKey = o.optString("adSizeKey", ""),
                            placementBreakdownJson = o.optJSONObject("placementBreakdown")?.toString()
                                ?: o.optString("placementBreakdownJson", ""),
                            subtotalBdt = o.optInt("subtotalBdt", o.optInt("priceBdt", 0)),
                            bundleDiscountBdt = o.optInt("bundleDiscountBdt", 0),
                            status = o.optString("status", SponsorCampaignSelector.STATUS_PENDING_APPROVAL),
                            name = o.optString("name", o.optString("sponsorName", "")),
                            logoUrl = o.optString("logoUrl", ""),
                            bannerImageUrl = o.optString("bannerImageUrl", ""),
                            promoTitle = o.optString("promoTitle", o.optString("title", "")),
                            description = o.optString("description", ""),
                            websiteUrl = o.optString("websiteUrl", ""),
                            contactNumber = o.optString("contactNumber", ""),
                            startDate = startDate,
                            endDate = endDate,
                            activatedAt = o.optLong("activatedAt", 0L),
                            expiryAt = o.optLong("expiryAt", Long.MAX_VALUE),
                            startTimestamp = sTs,
                            endTimestamp = eTs,
                            targetPages = o.optString("targetPages", "ALL"),
                            displayPriority = o.optInt("displayPriority", 10),
                            displayFrequency = o.optInt("displayFrequency", 1),
                            isActive = o.optBoolean("isActive", false),
                            renewalOfAdId = o.optString("renewalOfAdId", ""),
                            totalViews = o.optInt("totalViews", 0),
                            uniqueViews = o.optInt("uniqueViews", 0),
                            updatedAt = o.optLong("updatedAt", 0L)
                        )
                        allSponsorsForCounts.add(entity)
                        if (entity.advertiserId == advertiserId) {
                            myAds.add(entity)
                        }
                    }
                }

                // 4. Rate Cards & Settings
                val cloudRateCards = mutableListOf<PlacementRateCardEntity>()
                root.optJSONObject("placement_rate_cards")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val pid = keys.next()
                        val o = obj.optJSONObject(pid) ?: continue
                        val resolvedId = o.optString("placementId", pid).ifBlank { pid }
                        cloudRateCards.add(
                            PlacementRateCardEntity(
                                placementId = resolvedId,
                                placementNameEn = o.optString("placementNameEn", resolvedId),
                                placementNameBn = o.optString("placementNameBn", resolvedId),
                                moduleName = o.optString("moduleName", PlacementRateCardEngine.MOD_OTHER),
                                allowedSizesCsv = o.optString("allowedSizesCsv", "300x200,320x100,RESPONSIVE"),
                                sizeRateOverridesJson = o.optJSONObject("sizeRateOverrides")?.toString()
                                    ?: o.optString("sizeRateOverridesJson", "{}"),
                                dailyRateBdt = o.optInt("dailyRateBdt", 30),
                                sevenDaysRateBdt = o.optInt("sevenDaysRateBdt", 120),
                                fifteenDaysRateBdt = o.optInt("fifteenDaysRateBdt", 200),
                                thirtyDaysRateBdt = o.optInt("thirtyDaysRateBdt", 300),
                                customDailyRateBdt = o.optInt("customDailyRateBdt", 15),
                                maxActiveAds = o.optInt("maxActiveAds", 3).coerceAtLeast(1),
                                rotationMode = o.optString("rotationMode", PlacementRateCardEngine.ROTATION_WEIGHTED),
                                cardInsertionFrequency = o.optInt("cardInsertionFrequency", 5).coerceIn(2, 20),
                                status = o.optString("status", "ACTIVE"),
                                sortOrder = o.optInt("sortOrder", 99),
                                updatedAt = o.optLong("updatedAt", 0L)
                            )
                        )
                    }
                }
                val mergedRateCards = PlacementRateCardEngine.mergeWithDefaults(cloudRateCards)

                val rcCfgObj = root.optJSONObject("rate_card_settings")
                val rateCardSettings = RateCardSettingsEntity(
                    id = "global",
                    bundleEnabled = rcCfgObj?.optBoolean("bundleEnabled", true) ?: true,
                    discount2Pct = rcCfgObj?.optInt("discount2Pct", 5) ?: 5,
                    discount3Pct = rcCfgObj?.optInt("discount3Pct", 10) ?: 10,
                    discount4PlusPct = rcCfgObj?.optInt("discount4PlusPct", 15) ?: 15,
                    allowCustomDuration = rcCfgObj?.optBoolean("allowCustomDuration", true) ?: true,
                    updatedAt = rcCfgObj?.optLong("updatedAt", 0L) ?: 0L
                )

                val nowMs = System.currentTimeMillis()
                val activePlacementCounts = mergedRateCards.associate { card ->
                    card.placementId to PlacementRateCardEngine.countActiveForPlacement(
                        sponsors = allSponsorsForCounts,
                        placementId = card.placementId,
                        nowMs = nowMs
                    )
                }

                // 5. Only this advertiser's own payments
                val myPayments = mutableListOf<AdvertiserPaymentRecord>()
                root.optJSONObject("ad_payments")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val o = obj.optJSONObject(id) ?: continue
                        if (o.optString("advertiserId", "") != advertiserId) continue
                        myPayments.add(
                            AdvertiserPaymentRecord(
                                id = id,
                                adId = o.optString("adId", ""),
                                advertiserId = advertiserId,
                                packageId = o.optString("packageId", ""),
                                packageName = o.optString("packageName", ""),
                                isRenewal = o.optBoolean("isRenewal", false),
                                paymentMethod = o.optString("paymentMethod", "BKASH"),
                                senderMobile = o.optString("senderMobile", ""),
                                transactionId = o.optString("transactionId", ""),
                                amountBdt = o.optInt("amountBdt", 0),
                                screenshotUrl = o.optString("screenshotUrl", ""),
                                status = o.optString("status", "PENDING"),
                                adminNote = o.optString("adminNote", ""),
                                createdAt = o.optLong("createdAt", 0L),
                                verifiedAt = o.optLong("verifiedAt", 0L),
                                placementIds = o.optString("placementIds", ""),
                                moduleNames = o.optString("moduleNames", ""),
                                adSizeKey = o.optString("adSizeKey", ""),
                                placementBreakdownJson = o.optJSONObject("placementBreakdown")?.toString()
                                    ?: o.optString("placementBreakdownJson", ""),
                                subtotalBdt = o.optInt("subtotalBdt", o.optInt("amountBdt", 0)),
                                bundleDiscountBdt = o.optInt("bundleDiscountBdt", 0)
                            )
                        )
                    }
                }

                Result.success(
                    AdvertiserDashboardData(
                        myAds = myAds.sortedByDescending { it.updatedAt },
                        myPayments = myPayments.sortedByDescending { it.createdAt },
                        packages = packages.filter { it.status.equals("ACTIVE", true) }.sortedBy { it.priceBdt },
                        paymentSettings = paymentSettings,
                        rateCards = mergedRateCards,
                        rateCardSettings = rateCardSettings,
                        activePlacementCounts = activePlacementCounts
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Fetch advertiser dashboard error: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Submits a new Direct Sponsor Advertisement OR Renewal Request with manual bKash/Nagad payment.
     * - Strictly prevents duplicate Transaction IDs across all non-rejected payments.
     * - Never activates the advertisement automatically (status = PENDING_APPROVAL, isActive = false, activatedAt = 0).
     * - For renewals, links `renewalOfAdId` so previous ad history & statistics remain preserved.
     */
    suspend fun submitAdvertisementWithPayment(
        advertiser: AdvertiserSession,
        pkg: SponsorPackageEntity,
        title: String,
        businessName: String,
        description: String,
        imageSource: String,
        logoSource: String,
        websiteUrl: String,
        contactNumber: String,
        paymentMethod: String,
        senderMobile: String,
        transactionId: String,
        amountPaidBdt: Int,
        screenshotSource: String,
        renewalOfAdId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!advertiser.isLoggedIn) return@withContext Result.failure(IllegalStateException("Please log in as an advertiser first."))
            if (title.trim().length < 3) return@withContext Result.failure(IllegalArgumentException("Advertisement title is required"))
            if (businessName.trim().length < 2) return@withContext Result.failure(IllegalArgumentException("Business name is required"))
            if (!SponsorCampaignSelector.isValidImageSource(imageSource)) {
                return@withContext Result.failure(IllegalArgumentException("Upload a valid PNG/JPEG/WebP image or HTTPS image URL"))
            }
            if (websiteUrl.isNotBlank() && !SponsorCampaignSelector.isValidWebsiteUrl(websiteUrl)) {
                return@withContext Result.failure(IllegalArgumentException("Website / Social URL must start with https:// or http://"))
            }
            if (!SponsorCampaignSelector.isValidBangladeshMobile(senderMobile)) {
                return@withContext Result.failure(IllegalArgumentException("Enter valid bKash/Nagad sender mobile number"))
            }
            val normTrx = SponsorCampaignSelector.normalizeTransactionId(transactionId)
            if (normTrx.length < 5) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid Transaction ID (TrxID)"))
            }
            if (amountPaidBdt < pkg.priceBdt) {
                return@withContext Result.failure(IllegalArgumentException("Paid amount (৳$amountPaidBdt) must be at least package price (৳${pkg.priceBdt})"))
            }
            if (screenshotSource.isNotBlank() && !SponsorCampaignSelector.isValidImageSource(screenshotSource)) {
                return@withContext Result.failure(IllegalArgumentException("Payment screenshot must be a valid image"))
            }

            // Strict Duplicate TrxID Prevention across all existing non-rejected payments
            val existingPayments = getJsonObject("$BASE_URL/ad_payments.json")
            if (existingPayments != null) {
                val keys = existingPayments.keys()
                while (keys.hasNext()) {
                    val p = existingPayments.optJSONObject(keys.next()) ?: continue
                    val pStatus = p.optString("status", "PENDING")
                    if (pStatus.equals("REJECTED", true)) continue
                    val existingNorm = SponsorCampaignSelector.normalizeTransactionId(
                        p.optString("transactionIdNormalized", p.optString("transactionId", ""))
                    )
                    if (existingNorm.isNotEmpty() && existingNorm == normTrx) {
                        return@withContext Result.failure(IllegalStateException("Duplicate Transaction ID ($transactionId) is already used!"))
                    }
                }
            }

            val now = System.currentTimeMillis()
            val adId = "sp_${UUID.randomUUID().toString().take(10)}"
            val payId = "pay_${UUID.randomUUID().toString().take(10)}"
            val isRenewal = renewalOfAdId.isNotBlank()

            // Advertisement created as PENDING_APPROVAL (NEVER active before Admin verifies payment!)
            val adPayload = JSONObject().apply {
                put("id", adId)
                put("advertiserId", advertiser.id)
                put("submittedBy", "ADVERTISER")
                put("packageId", pkg.id)
                put("packageName", pkg.name)
                put("widthPx", pkg.widthPx)
                put("heightPx", pkg.heightPx)
                put("durationDays", pkg.durationDays)
                put("priceBdt", pkg.priceBdt)
                put("placementType", pkg.placementType)
                put("status", SponsorCampaignSelector.STATUS_PENDING_APPROVAL)
                put("isActive", false) // Strictly inactive until Admin approves payment
                put("name", businessName.trim())
                put("sponsorName", businessName.trim())
                put("promoTitle", title.trim())
                put("title", title.trim())
                put("description", description.trim())
                put("logoUrl", logoSource.trim())
                put("bannerImageUrl", imageSource.trim())
                put("websiteUrl", websiteUrl.trim())
                put("contactNumber", contactNumber.trim())
                put("targetPages", if (pkg.placementType == SponsorCampaignSelector.PLACEMENT_ABOUT_US) "about_us" else "ALL")
                put("displayPriority", 15)
                put("displayFrequency", 1)
                put("activatedAt", 0L)
                put("expiryAt", 0L)
                put("startDate", "")
                put("endDate", "")
                put("renewalOfAdId", renewalOfAdId.trim())
                put("totalViews", 0)
                put("uniqueViews", 0)
                put("createdAt", now)
                put("updatedAt", now)
            }

            val payPayload = JSONObject().apply {
                put("id", payId)
                put("adId", adId)
                put("advertiserId", advertiser.id)
                put("advertiserBusiness", advertiser.businessName)
                put("packageId", pkg.id)
                put("packageName", pkg.name)
                put("isRenewal", isRenewal)
                put("renewalOfAdId", renewalOfAdId.trim())
                put("paymentMethod", paymentMethod.uppercase())
                put("senderMobile", senderMobile.trim())
                put("transactionId", transactionId.trim())
                put("transactionIdNormalized", normTrx)
                put("amountBdt", amountPaidBdt)
                put("screenshotUrl", screenshotSource.trim())
                put("status", "PENDING")
                put("adminNote", "")
                put("verifiedBy", "")
                put("verifiedAt", 0L)
                put("createdAt", now)
            }

            val adSaved = putJson("$BASE_URL/sponsors/$adId.json", adPayload.toString())
            val paySaved = putJson("$BASE_URL/ad_payments/$payId.json", payPayload.toString())
            if (!adSaved || !paySaved) {
                return@withContext Result.failure(IllegalStateException("Failed to submit advertisement. Check internet connection."))
            }

            Result.success(adId)
        } catch (e: Exception) {
            Log.w(TAG, "Submit advertisement error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Screen-Based Placement & Dynamic Rate Card Booking submission (Single or Multiple Placements).
     * Calculates pricing breakdown & optional multi-placement bundle discount via PlacementRateCardEngine,
     * validates unique TrxID & full payment, and stores PENDING_APPROVAL until Admin verifies payment.
     */
    suspend fun submitPlacementBookingWithPayment(
        advertiser: AdvertiserSession,
        selectedCards: List<PlacementRateCardEntity>,
        adSizeKey: String,
        customWidthPx: Int,
        customHeightPx: Int,
        durationDays: Int,
        rateCardSettings: RateCardSettingsEntity,
        title: String,
        businessName: String,
        description: String,
        imageSource: String,
        logoSource: String,
        websiteUrl: String,
        contactNumber: String,
        paymentMethod: String,
        senderMobile: String,
        transactionId: String,
        amountPaidBdt: Int,
        screenshotSource: String,
        renewalOfAdId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!advertiser.isLoggedIn) return@withContext Result.failure(IllegalStateException("Please log in as an advertiser first."))
            val activeCards = selectedCards.filter { it.status.equals("ACTIVE", ignoreCase = true) }
            if (activeCards.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Select at least one active advertisement placement."))
            }
            val cleanDays = durationDays.coerceAtLeast(1)
            if (!rateCardSettings.allowCustomDuration && cleanDays !in listOf(1, 7, 15, 30)) {
                return@withContext Result.failure(IllegalArgumentException("Select 1, 7, 15, or 30 days duration."))
            }
            if (title.trim().length < 3) return@withContext Result.failure(IllegalArgumentException("Advertisement title is required"))
            if (businessName.trim().length < 2) return@withContext Result.failure(IllegalArgumentException("Business name is required"))
            if (!SponsorCampaignSelector.isValidImageSource(imageSource)) {
                return@withContext Result.failure(IllegalArgumentException("Upload a valid PNG/JPEG/WebP image or HTTPS image URL"))
            }
            if (websiteUrl.isNotBlank() && !SponsorCampaignSelector.isValidWebsiteUrl(websiteUrl)) {
                return@withContext Result.failure(IllegalArgumentException("Website / Social URL must start with https:// or http://"))
            }
            if (!SponsorCampaignSelector.isValidBangladeshMobile(senderMobile)) {
                return@withContext Result.failure(IllegalArgumentException("Enter valid bKash/Nagad sender mobile number"))
            }
            val normTrx = SponsorCampaignSelector.normalizeTransactionId(transactionId)
            if (normTrx.length < 5) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid Transaction ID (TrxID)"))
            }
            if (screenshotSource.isNotBlank() && !SponsorCampaignSelector.isValidImageSource(screenshotSource)) {
                return@withContext Result.failure(IllegalArgumentException("Payment screenshot must be a valid image"))
            }

            val quote = PlacementRateCardEngine.calculateBookingQuote(
                selectedCards = activeCards,
                adSizeKey = adSizeKey,
                durationDays = cleanDays,
                settings = rateCardSettings
            )
            if (amountPaidBdt < quote.totalBdt) {
                return@withContext Result.failure(
                    IllegalArgumentException("Paid amount (৳$amountPaidBdt) must be at least calculated total (৳${quote.totalBdt})")
                )
            }

            // Duplicate TrxID Check
            val existingPayments = getJsonObject("$BASE_URL/ad_payments.json")
            if (existingPayments != null) {
                val keys = existingPayments.keys()
                while (keys.hasNext()) {
                    val p = existingPayments.optJSONObject(keys.next()) ?: continue
                    val pStatus = p.optString("status", "PENDING")
                    if (pStatus.equals("REJECTED", true)) continue
                    val existingNorm = SponsorCampaignSelector.normalizeTransactionId(
                        p.optString("transactionIdNormalized", p.optString("transactionId", ""))
                    )
                    if (existingNorm.isNotEmpty() && existingNorm == normTrx) {
                        return@withContext Result.failure(IllegalStateException("Duplicate Transaction ID ($transactionId) is already used!"))
                    }
                }
            }

            val spec = PlacementRateCardEngine.findSizeSpec(adSizeKey)
            val widthPx = if (adSizeKey.equals("CUSTOM", true)) customWidthPx.coerceIn(120, 2000) else (spec?.widthPx ?: 300)
            val heightPx = if (adSizeKey.equals("CUSTOM", true)) customHeightPx.coerceIn(40, 2000) else (spec?.heightPx ?: 200)
            val placementIdsCsv = activeCards.joinToString(",") { it.placementId }
            val moduleNamesCsv = activeCards.map { it.moduleName }.distinct().joinToString(",")
            val summaryTitle = activeCards.joinToString(" + ") { it.placementNameEn } + " ($adSizeKey • ${cleanDays}d)"
            val legacyPlacementType = when {
                activeCards.any { it.placementId == PlacementRateCardEngine.OTH_POPUP } -> SponsorCampaignSelector.PLACEMENT_POPUP
                activeCards.any { it.placementId == PlacementRateCardEngine.OTH_ABOUT } -> SponsorCampaignSelector.PLACEMENT_ABOUT_US
                adSizeKey.equals("RESPONSIVE", true) -> SponsorCampaignSelector.PLACEMENT_NATIVE_CARD
                adSizeKey.equals("320x100", true) -> SponsorCampaignSelector.PLACEMENT_BANNER_320x100
                adSizeKey.equals("CUSTOM", true) -> SponsorCampaignSelector.PLACEMENT_BANNER_CUSTOM
                else -> SponsorCampaignSelector.PLACEMENT_BANNER_300x200
            }

            val breakdownJsonObj = JSONObject().apply {
                quote.breakdownMap.forEach { (pid, amt) -> put(pid, amt) }
            }

            val now = System.currentTimeMillis()
            val adId = "sp_${UUID.randomUUID().toString().take(10)}"
            val payId = "pay_${UUID.randomUUID().toString().take(10)}"
            val isRenewal = renewalOfAdId.isNotBlank()

            val adPayload = JSONObject().apply {
                put("id", adId)
                put("advertiserId", advertiser.id)
                put("submittedBy", "ADVERTISER")
                put("packageId", "placement_${placementIdsCsv.take(40)}")
                put("packageName", summaryTitle)
                put("placementIds", placementIdsCsv)
                put("moduleNames", moduleNamesCsv)
                put("adSizeKey", adSizeKey)
                put("placementBreakdown", breakdownJsonObj)
                put("subtotalBdt", quote.subtotalBdt)
                put("bundleDiscountBdt", quote.discountBdt)
                put("widthPx", widthPx)
                put("heightPx", heightPx)
                put("durationDays", cleanDays)
                put("priceBdt", quote.totalBdt)
                put("placementType", legacyPlacementType)
                put("status", SponsorCampaignSelector.STATUS_PENDING_APPROVAL)
                put("isActive", false)
                put("name", businessName.trim())
                put("sponsorName", businessName.trim())
                put("promoTitle", title.trim())
                put("title", title.trim())
                put("description", description.trim())
                put("logoUrl", logoSource.trim())
                put("bannerImageUrl", imageSource.trim())
                put("websiteUrl", websiteUrl.trim())
                put("contactNumber", contactNumber.trim())
                put("targetPages", "ALL")
                put("displayPriority", 15)
                put("displayFrequency", 1)
                put("activatedAt", 0L)
                put("expiryAt", 0L)
                put("startDate", "")
                put("endDate", "")
                put("renewalOfAdId", renewalOfAdId.trim())
                put("totalViews", 0)
                put("uniqueViews", 0)
                put("createdAt", now)
                put("updatedAt", now)
            }

            val payPayload = JSONObject().apply {
                put("id", payId)
                put("adId", adId)
                put("advertiserId", advertiser.id)
                put("advertiserBusiness", advertiser.businessName)
                put("packageId", "placement_booking")
                put("packageName", summaryTitle)
                put("placementIds", placementIdsCsv)
                put("moduleNames", moduleNamesCsv)
                put("adSizeKey", adSizeKey)
                put("placementBreakdown", breakdownJsonObj)
                put("subtotalBdt", quote.subtotalBdt)
                put("bundleDiscountBdt", quote.discountBdt)
                put("isRenewal", isRenewal)
                put("renewalOfAdId", renewalOfAdId.trim())
                put("paymentMethod", paymentMethod.uppercase())
                put("senderMobile", senderMobile.trim())
                put("transactionId", transactionId.trim())
                put("transactionIdNormalized", normTrx)
                put("amountBdt", amountPaidBdt)
                put("screenshotUrl", screenshotSource.trim())
                put("status", "PENDING")
                put("adminNote", "")
                put("verifiedBy", "")
                put("verifiedAt", 0L)
                put("createdAt", now)
            }

            val adSaved = putJson("$BASE_URL/sponsors/$adId.json", adPayload.toString())
            val paySaved = putJson("$BASE_URL/ad_payments/$payId.json", payPayload.toString())
            if (!adSaved || !paySaved) {
                return@withContext Result.failure(IllegalStateException("Failed to submit booking. Check internet connection."))
            }

            Result.success(adId)
        } catch (e: Exception) {
            Log.w(TAG, "Submit placement booking error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun normalizeMobile(phone: String): String {
        val d = phone.replace(Regex("[^0-9]"), "")
        return if (d.startsWith("880") && d.length == 13) d.substring(2) else d
    }

    private fun getJsonObject(urlStr: String): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }
        val body = conn.inputStream.bufferedReader().use { it.readText().trim() }
        conn.disconnect()
        if (body.isEmpty() || body == "null") return null
        return JSONObject(body)
    }

    private fun putJson(urlStr: String, payload: String): Boolean {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }
}
