package com.example.data.remote

import android.util.Log
import com.example.auth.GoogleAuthManager
import com.example.data.local.AdvertiserSession
import com.example.ui.ads.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class RegisteredInstitutionClaims(
    val institutionIds: Set<String> = emptySet(),
    val normalizedBusinessNames: Set<String> = emptySet()
)

data class AdvertiserPortalDashboard(
    val account: AdAdvertiserAccount? = null,
    val activePackages: List<AdPackageItem> = emptyList(),
    val activePaymentMethods: List<AdPaymentMethod> = emptyList(),
    val myCampaigns: List<AdCampaignItem> = emptyList(),
    val myPayments: List<AdPaymentRecord> = emptyList()
)

/**
 * Production Firebase Realtime Database REST Service for:
 * - /advertising/packages
 * - /advertising/advertisers
 * - /advertising/campaigns
 * - /advertising/payments
 * - /advertising/trxIndex
 * - /advertising/paymentMethods
 * - /advertising/placements
 * - /advertising/rotation
 * - /advertising/impressions
 * - /advertising/clicks
 * - /homeSlider/advertiserCampaigns
 * - /homeSlider/adminCustomSliders
 * - /homeSlider/settings
 */
object AdvertisingRemoteService {
    private const val TAG = "AdvertisingRemote"
    private const val RTDB_ROOT = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"

    private suspend fun authedUrl(path: String, extraQuery: String = ""): String {
        val clean = path.trimStart('/')
        val token = GoogleAuthManager.getIdToken(false)
        val params = mutableListOf<String>()
        if (!token.isNullOrBlank()) {
            params.add("auth=${URLEncoder.encode(token, "UTF-8")}")
        }
        if (extraQuery.isNotBlank()) {
            params.add(extraQuery)
        }
        val queryStr = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "$RTDB_ROOT/$clean.json$queryStr"
    }

    suspend fun fetchPublicSnapshot(): Result<AdvertisingSystemSnapshot> = withContext(Dispatchers.IO) {
        try {
            val pkgObj = getJsonObject(authedUrl("advertising/packages"))
            val methodObj = getJsonObject(authedUrl("advertising/paymentMethods"))
            val campObj = getJsonObject(authedUrl("advertising/campaigns"))
            val placementsObj = getJsonObject(authedUrl("advertising/placements"))
            val rotationObj = getJsonObject(authedUrl("advertising/rotation"))
            val advSlidersObj = getJsonObject(authedUrl("homeSlider/advertiserCampaigns"))
            val customSlidersObj = getJsonObject(authedUrl("homeSlider/adminCustomSliders"))
            val sliderSettingsObj = getJsonObject(authedUrl("homeSlider/settings"))

            val packages = parsePackages(pkgObj)
            val paymentMethods = parsePaymentMethods(methodObj)
            val campaigns = parseCampaigns(campObj)
            val advertiserSliders = parseAdvertiserHomeSliders(advSlidersObj)
            val customSliders = parseAdminCustomSliders(customSlidersObj)
            val sliderSettings = parseHomeSliderSettings(sliderSettingsObj)
            val placementSettings = parsePlacementSettings(placementsObj)
            val rotation = parseRotationCounters(rotationObj)

            AdvertisingRotationEngine.seedCounters(rotation)

            Result.success(
                AdvertisingSystemSnapshot(
                    packages = packages,
                    paymentMethods = paymentMethods,
                    campaigns = campaigns,
                    advertiserHomeSliders = advertiserSliders,
                    adminCustomSliders = customSliders,
                    homeSliderSettings = sliderSettings,
                    placementSettings = placementSettings,
                    rotationCounters = rotation,
                    syncedFromServer = true
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchPublicSnapshot failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun normalizeInstitutionKey(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")

    suspend fun fetchRegisteredInstitutionClaims(): Result<RegisteredInstitutionClaims> = withContext(Dispatchers.IO) {
        try {
            val allObj = getJsonObject(authedUrl("advertising/advertisers"))
            val ids = mutableSetOf<String>()
            val names = mutableSetOf<String>()
            if (allObj != null) {
                val keys = allObj.keys()
                while (keys.hasNext()) {
                    val o = allObj.optJSONObject(keys.next()) ?: continue
                    val instId = o.optString("institutionId", "").trim()
                    if (instId.isNotBlank()) ids.add(instId)
                    val bizNorm = normalizeInstitutionKey(o.optString("businessName", ""))
                    if (bizNorm.isNotBlank()) names.add(bizNorm)
                }
            }
            Result.success(RegisteredInstitutionClaims(institutionIds = ids, normalizedBusinessNames = names))
        } catch (e: Exception) {
            Log.w(TAG, "fetchRegisteredInstitutionClaims failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registerAdvertiser(
        advertiserName: String,
        businessName: String,
        mobile: String,
        email: String,
        address: String,
        category: String,
        websiteUrl: String,
        password: String,
        institutionId: String = "",
        institutionType: String = ""
    ): Result<AdvertiserSession> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = advertiserName.trim()
            val cleanBiz = businessName.trim()
            val cleanMobile = normalizePhone(mobile)
            val cleanEmail = email.trim().lowercase()
            val cleanAddress = address.trim()
            val cleanCategory = category.trim().ifBlank { "Healthcare" }
            val cleanWeb = websiteUrl.trim()
            val cleanInstId = institutionId.trim()
            val cleanInstType = institutionType.trim().uppercase()
            val normBiz = normalizeInstitutionKey(cleanBiz)

            if (cleanOwner.length < 2) return@withContext Result.failure(IllegalArgumentException("Advertiser Name is required."))
            if (cleanBiz.length < 2) return@withContext Result.failure(IllegalArgumentException("Please select your Institution / Organization."))
            if (cleanMobile.length < 10) return@withContext Result.failure(IllegalArgumentException("Enter a valid mobile number (01XXXXXXXXX)."))
            if (password.length < 6) return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
            if (cleanWeb.isNotBlank() && !AdvertisingValidators.isValidDestinationUrl(cleanWeb)) {
                return@withContext Result.failure(IllegalArgumentException("Website URL must start with https:// or http://"))
            }

            val existingAll = getJsonObject(authedUrl("advertising/advertisers"))
            if (existingAll != null) {
                val keys = existingAll.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = existingAll.optJSONObject(k) ?: continue
                    val exMobile = normalizePhone(o.optString("mobile", ""))
                    val exEmail = o.optString("email", "").trim().lowercase()
                    val exInstId = o.optString("institutionId", "").trim()
                    val exBizNorm = normalizeInstitutionKey(o.optString("businessName", ""))
                    if (exMobile.isNotBlank() && exMobile == cleanMobile) {
                        return@withContext Result.failure(IllegalStateException("An advertiser account with this mobile number already exists."))
                    }
                    if (cleanEmail.isNotBlank() && exEmail == cleanEmail) {
                        return@withContext Result.failure(IllegalStateException("An advertiser account with this email already exists."))
                    }
                    if ((cleanInstId.isNotBlank() && exInstId == cleanInstId) || (normBiz.isNotBlank() && exBizNorm == normBiz)) {
                        return@withContext Result.failure(IllegalStateException("This institution is already registered by another advertiser account."))
                    }
                }
            }

            val advId = "adv_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val salt = generateSaltHex()
            val hash = sha256Hex("$salt:$password")
            val now = System.currentTimeMillis()

            val payload = JSONObject().apply {
                put("id", advId)
                put("uid", advId)
                put("advertiserName", cleanOwner.take(120))
                put("businessName", cleanBiz.take(160))
                put("mobile", cleanMobile.take(32))
                put("email", cleanEmail.take(160))
                put("address", cleanAddress.take(250))
                put("category", cleanCategory.take(80))
                put("photoUrl", "")
                put("websiteUrl", cleanWeb.take(300))
                put("institutionId", cleanInstId)
                put("institutionType", cleanInstType)
                put("salt", salt)
                put("passwordHash", hash)
                put("authProvider", "PASSWORD")
                put("status", AdStatuses.ACTIVE)
                put("createdAt", now)
                put("updatedAt", now)
            }

            val saved = putJson(authedUrl("advertising/advertisers/$advId"), payload.toString())
            if (!saved) {
                return@withContext Result.failure(IllegalStateException("Failed to create advertiser account. Check connection."))
            }

            Result.success(
                AdvertiserSession(
                    id = advId,
                    businessName = cleanBiz,
                    ownerName = cleanOwner,
                    mobile = cleanMobile,
                    email = cleanEmail,
                    address = cleanAddress,
                    category = cleanCategory,
                    photoUrl = "",
                    websiteUrl = cleanWeb,
                    authProvider = "PASSWORD",
                    institutionId = cleanInstId,
                    institutionType = cleanInstType
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "registerAdvertiser failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loginAdvertiser(
        mobileOrEmail: String,
        password: String
    ): Result<AdvertiserSession> = withContext(Dispatchers.IO) {
        try {
            val loginInput = mobileOrEmail.trim()
            if (loginInput.isBlank() || password.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Mobile/Email and Password are required."))
            }
            val normMobile = normalizePhone(loginInput)
            val normEmail = loginInput.lowercase()

            val allObj = getJsonObject(authedUrl("advertising/advertisers"))
                ?: return@withContext Result.failure(IllegalStateException("No advertiser accounts found."))

            val keys = allObj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val o = allObj.optJSONObject(id) ?: continue
                val m = normalizePhone(o.optString("mobile", ""))
                val em = o.optString("email", "").trim().lowercase()
                if ((normMobile.isNotBlank() && m == normMobile) || (em.isNotBlank() && em == normEmail)) {
                    val salt = o.optString("salt", "")
                    val expectedHash = o.optString("passwordHash", "")
                    if (salt.isBlank() || expectedHash.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Password not configured for this account. Please contact Admin."))
                    }
                    val actualHash = sha256Hex("$salt:$password")
                    if (actualHash != expectedHash) {
                        return@withContext Result.failure(IllegalArgumentException("Incorrect password."))
                    }
                    val status = o.optString("status", AdStatuses.ACTIVE).uppercase()
                    if (status == "SUSPENDED" || status == AdStatuses.INACTIVE) {
                        return@withContext Result.failure(IllegalStateException("This advertiser account is currently suspended/inactive."))
                    }
                    return@withContext Result.success(
                        AdvertiserSession(
                            id = id,
                            businessName = o.optString("businessName", ""),
                            ownerName = o.optString("advertiserName", ""),
                            mobile = o.optString("mobile", ""),
                            email = o.optString("email", ""),
                            address = o.optString("address", ""),
                            category = o.optString("category", "Healthcare"),
                            photoUrl = o.optString("photoUrl", ""),
                            websiteUrl = o.optString("websiteUrl", ""),
                            authProvider = o.optString("authProvider", "PASSWORD"),
                            institutionId = o.optString("institutionId", ""),
                            institutionType = o.optString("institutionType", "")
                        )
                    )
                }
            }
            Result.failure(IllegalArgumentException("No advertiser account found with that mobile or email."))
        } catch (e: Exception) {
            Log.w(TAG, "loginAdvertiser failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun ensureGoogleAdvertiserAccount(
        uid: String,
        ownerName: String,
        email: String,
        businessName: String,
        mobile: String,
        address: String,
        category: String,
        websiteUrl: String,
        photoUrl: String
    ): Result<AdvertiserSession> = withContext(Dispatchers.IO) {
        try {
            val cleanBiz = businessName.trim()
            val cleanOwner = ownerName.trim()
            val cleanMobile = normalizePhone(mobile)
            if (cleanBiz.length < 2 || cleanOwner.length < 2 || cleanMobile.length < 10) {
                return@withContext Result.failure(IllegalArgumentException("Business name, owner name, and valid mobile are required."))
            }
            val now = System.currentTimeMillis()
            val existing = getJsonObject(authedUrl("advertising/advertisers/$uid"))
            val createdAt = existing?.optLong("createdAt", 0L)?.takeIf { it > 0L } ?: now
            val status = existing?.optString("status", AdStatuses.ACTIVE)?.ifBlank { AdStatuses.ACTIVE } ?: AdStatuses.ACTIVE

            val payload = JSONObject().apply {
                put("id", uid)
                put("uid", uid)
                put("advertiserName", cleanOwner.take(120))
                put("businessName", cleanBiz.take(160))
                put("mobile", cleanMobile.take(32))
                put("email", email.trim().take(160))
                put("address", address.trim().take(250))
                put("category", category.trim().ifBlank { "Healthcare" }.take(80))
                put("photoUrl", photoUrl.take(500))
                put("websiteUrl", websiteUrl.trim().take(300))
                put("authProvider", "GOOGLE")
                put("status", status)
                put("createdAt", createdAt)
                put("updatedAt", now)
            }
            val ok = putJson(authedUrl("advertising/advertisers/$uid"), payload.toString())
            if (!ok) return@withContext Result.failure(IllegalStateException("Could not save advertiser profile."))
            Result.success(
                AdvertiserSession(
                    id = uid,
                    businessName = cleanBiz,
                    ownerName = cleanOwner,
                    mobile = cleanMobile,
                    email = email.trim(),
                    address = address.trim(),
                    category = category.trim().ifBlank { "Healthcare" },
                    photoUrl = photoUrl,
                    websiteUrl = websiteUrl.trim(),
                    authProvider = "GOOGLE"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGoogleAdvertiserIfExists(uid: String): AdvertiserSession? = withContext(Dispatchers.IO) {
        val o = getJsonObject(authedUrl("advertising/advertisers/$uid")) ?: return@withContext null
        val biz = o.optString("businessName", "").trim()
        if (biz.isBlank()) return@withContext null
        AdvertiserSession(
            id = uid,
            businessName = biz,
            ownerName = o.optString("advertiserName", ""),
            mobile = o.optString("mobile", ""),
            email = o.optString("email", ""),
            address = o.optString("address", ""),
            category = o.optString("category", "Healthcare"),
            photoUrl = o.optString("photoUrl", ""),
            websiteUrl = o.optString("websiteUrl", ""),
            authProvider = o.optString("authProvider", "GOOGLE")
        )
    }

    suspend fun updateAdvertiserProfile(
        advertiserId: String,
        ownerName: String,
        businessName: String,
        mobile: String,
        email: String,
        address: String,
        category: String,
        websiteUrl: String
    ): Result<AdvertiserSession> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = ownerName.trim()
            val cleanBiz = businessName.trim()
            val cleanMobile = normalizePhone(mobile)
            if (cleanOwner.length < 2 || cleanBiz.length < 2 || cleanMobile.length < 10) {
                return@withContext Result.failure(IllegalArgumentException("Name, Business Name, and valid mobile are required."))
            }
            val existing = getJsonObject(authedUrl("advertising/advertisers/$advertiserId")) ?: JSONObject()
            val now = System.currentTimeMillis()
            val payload = JSONObject(existing.toString()).apply {
                put("id", advertiserId)
                put("uid", advertiserId)
                put("advertiserName", cleanOwner.take(120))
                put("businessName", cleanBiz.take(160))
                put("mobile", cleanMobile.take(32))
                put("email", email.trim().lowercase().take(160))
                put("address", address.trim().take(250))
                put("category", category.trim().ifBlank { "Healthcare" }.take(80))
                put("websiteUrl", websiteUrl.trim().take(300))
                put("updatedAt", now)
                if (!has("createdAt")) put("createdAt", now)
                if (!has("status")) put("status", AdStatuses.ACTIVE)
            }
            val ok = putJson(authedUrl("advertising/advertisers/$advertiserId"), payload.toString())
            if (!ok) return@withContext Result.failure(IllegalStateException("Failed to update profile."))
            Result.success(
                AdvertiserSession(
                    id = advertiserId,
                    businessName = cleanBiz,
                    ownerName = cleanOwner,
                    mobile = cleanMobile,
                    email = email.trim().lowercase(),
                    address = address.trim(),
                    category = category.trim().ifBlank { "Healthcare" },
                    photoUrl = payload.optString("photoUrl", ""),
                    websiteUrl = websiteUrl.trim(),
                    authProvider = payload.optString("authProvider", "PASSWORD"),
                    institutionId = payload.optString("institutionId", ""),
                    institutionType = payload.optString("institutionType", "")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches ONLY the current logged-in advertiser's own campaigns, payments,
     * plus Active Admin packages & Active payment methods. Strict private isolation!
     */
    suspend fun fetchAdvertiserDashboard(advertiserId: String): Result<AdvertiserPortalDashboard> = withContext(Dispatchers.IO) {
        try {
            if (advertiserId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Not logged in"))

            val pkgObj = getJsonObject(authedUrl("advertising/packages"))
            val methodObj = getJsonObject(authedUrl("advertising/paymentMethods"))
            val advObj = getJsonObject(authedUrl("advertising/advertisers/$advertiserId"))
            val campObj = getJsonObject(authedUrl("advertising/campaigns"))
            val payObj = getJsonObject(authedUrl("advertising/payments"))

            val activePackages = parsePackages(pkgObj).filter { it.isActive }
            val activeMethods = parsePaymentMethods(methodObj).filter { it.isActive }
            // Strict private filter by advertiserId
            val myCampaigns = parseCampaigns(campObj)
                .filter { it.advertiserId == advertiserId }
                .sortedByDescending { it.createdAt }
            val myPayments = parsePayments(payObj)
                .filter { it.advertiserId == advertiserId }
                .sortedByDescending { it.createdAt }

            val account = advObj?.let {
                AdAdvertiserAccount(
                    id = advertiserId,
                    uid = it.optString("uid", advertiserId),
                    advertiserName = it.optString("advertiserName", ""),
                    businessName = it.optString("businessName", ""),
                    mobile = it.optString("mobile", ""),
                    email = it.optString("email", ""),
                    address = it.optString("address", ""),
                    category = it.optString("category", "Healthcare"),
                    photoUrl = it.optString("photoUrl", ""),
                    websiteUrl = it.optString("websiteUrl", ""),
                    institutionId = it.optString("institutionId", ""),
                    institutionType = it.optString("institutionType", ""),
                    status = it.optString("status", AdStatuses.ACTIVE),
                    authProvider = it.optString("authProvider", "PASSWORD"),
                    createdAt = it.optLong("createdAt", 0L)
                )
            }

            Result.success(
                AdvertiserPortalDashboard(
                    account = account,
                    activePackages = activePackages,
                    activePaymentMethods = activeMethods,
                    myCampaigns = myCampaigns,
                    myPayments = myPayments
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchAdvertiserDashboard failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Submits a new Advertisement Campaign + Payment atomically:
     * - Re-fetches the selected package from RTDB and rejects if not ACTIVE.
     * - Locks immutable price, durationDays, adType, and placement from the server package.
     * - For HOME_SLIDER, validates targetRecordType & targetRecordId reference an existing record.
     * - Checks duplicate normalized TRX ID before creating the payment & campaign.
     */
    suspend fun submitCampaignWithPayment(
        session: AdvertiserSession,
        packageId: String,
        selectedPlacement: String,
        campaignDurationDays: Int,
        title: String,
        description: String,
        imageUrl: String,
        destinationUrl: String,
        // Existing record reference(s) for HOME_SLIDER (supports multi-select doctors + institution)
        targetRecordType: String = "",
        targetRecordId: String = "",
        targetRecordTitle: String = "",
        targetRecordSubtitle: String = "",
        targetRecordPhotoUrl: String = "",
        promotedTargets: List<PromotedTargetRef> = emptyList(),
        // Payment fields
        paymentMethod: AdPaymentMethod,
        senderMobile: String,
        trxId: String,
        amountPaid: Double,
        paymentDate: String,
        screenshotUrl: String = "",
        renewalOfCampaignId: String = ""
    ): Result<AdCampaignItem> = withContext(Dispatchers.IO) {
        try {
            if (!session.isLoggedIn) {
                return@withContext Result.failure(IllegalStateException("Advertiser login is required."))
            }

            // 1. Validate live package directly from Firebase RTDB (Section 1 & 3 immutability)
            val pkgJson = getJsonObject(authedUrl("advertising/packages/$packageId"))
                ?: return@withContext Result.failure(IllegalArgumentException("Selected package no longer exists."))
            val pkg = parseSinglePackage(packageId, pkgJson)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid package configuration."))
            if (!pkg.isActive) {
                return@withContext Result.failure(IllegalStateException("This package is inactive. Select an Active package."))
            }
            if (!AdPackageTypes.isSupported(pkg.adType)) {
                return@withContext Result.failure(IllegalArgumentException("Unsupported package advertisement type."))
            }

            val normType = AdPackageTypes.normalize(pkg.adType)
            val effectivePlacement = if (normType == AdPackageTypes.NATIVE_300x100 && selectedPlacement in AdPlacements.NATIVE_PLACEMENTS) {
                selectedPlacement
            } else {
                pkg.placement.ifBlank { AdPackageTypes.defaultPlacement(normType) }
            }
            val maxDays = pkg.maxCampaignDurationDays.coerceAtLeast(pkg.durationDays)
            val lockedDuration = campaignDurationDays.coerceIn(1, maxDays).takeIf { it > 0 } ?: pkg.durationDays

            // Normalize multi-select targets for HOME_SLIDER
            val cleanedTargets: List<PromotedTargetRef> = promotedTargets
                .map {
                    it.copy(
                        recordType = SliderRecordTypes.normalize(it.recordType),
                        recordId = it.recordId.trim()
                    )
                }
                .filter { SliderRecordTypes.isSupported(it.recordType) && it.recordId.isNotBlank() }
                .distinctBy { "${it.recordType}:${it.recordId}" }
                .ifEmpty {
                    val normSingle = SliderRecordTypes.normalize(targetRecordType)
                    if (SliderRecordTypes.isSupported(normSingle) && targetRecordId.isNotBlank()) {
                        listOf(
                            PromotedTargetRef(
                                recordType = normSingle,
                                recordId = targetRecordId.trim(),
                                title = targetRecordTitle.trim(),
                                subtitle = targetRecordSubtitle.trim(),
                                photoUrl = targetRecordPhotoUrl.trim()
                            )
                        )
                    } else emptyList()
                }
            val primaryTarget = cleanedTargets.firstOrNull()

            // 2. Validate content
            if (normType == AdPackageTypes.HOME_SLIDER) {
                if (cleanedTargets.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Please select at least one Doctor or your Institution to promote in the Home Slider."))
                }
            } else {
                if (title.trim().length < 2) {
                    return@withContext Result.failure(IllegalArgumentException("Advertisement Title is required."))
                }
                if (!AdvertisingValidators.isValidUrlOrDataUri(imageUrl)) {
                    return@withContext Result.failure(IllegalArgumentException("Please select or enter a valid Ad Image."))
                }
            }

            if (destinationUrl.isNotBlank() && !AdvertisingValidators.isValidDestinationUrl(destinationUrl)) {
                return@withContext Result.failure(IllegalArgumentException("Destination URL must start with https://, http://, or tel:"))
            }

            // 3. Validate payment & check duplicate TRX ID (Section 8)
            val cleanSender = normalizePhone(senderMobile)
            if (cleanSender.length < 10) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid sender mobile number."))
            }
            val normTrx = AdvertisingValidators.normalizeTrxId(trxId)
            if (!AdvertisingValidators.isValidTrxId(trxId)) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid Transaction ID (TRX ID)."))
            }
            if (amountPaid <= 0.0) {
                return@withContext Result.failure(IllegalArgumentException("Payment amount must be greater than 0."))
            }

            val existingTrx = getJsonObject(authedUrl("advertising/trxIndex/$normTrx"))
            if (existingTrx != null) {
                return@withContext Result.failure(IllegalStateException("Duplicate TRX ID detected. This transaction has already been submitted."))
            }
            val existingPayments = getJsonObject(authedUrl("advertising/payments"))
            if (existingPayments != null) {
                val pKeys = existingPayments.keys()
                while (pKeys.hasNext()) {
                    val pObj = existingPayments.optJSONObject(pKeys.next()) ?: continue
                    val prevNorm = AdvertisingValidators.normalizeTrxId(pObj.optString("trxId", ""))
                    if (prevNorm == normTrx) {
                        return@withContext Result.failure(IllegalStateException("Duplicate TRX ID detected."))
                    }
                }
            }

            val now = System.currentTimeMillis()
            val campaignId = "camp_${now}_${UUID.randomUUID().toString().take(6)}"
            val paymentId = "pay_${now}_${UUID.randomUUID().toString().take(6)}"

            val multiSummaryTitle = when {
                title.trim().isNotBlank() -> title.trim()
                cleanedTargets.size > 1 -> "${session.businessName.ifBlank { primaryTarget?.title.orEmpty() }} (${cleanedTargets.size} items)"
                else -> primaryTarget?.title?.ifBlank { session.businessName } ?: session.businessName
            }
            val resolvedImg = imageUrl.trim().ifBlank { primaryTarget?.photoUrl.orEmpty() }
            val targetsJsonArray = JSONArray().apply {
                cleanedTargets.forEach { t ->
                    put(JSONObject().apply {
                        put("recordType", t.recordType)
                        put("recordId", t.recordId)
                        put("title", t.title.take(160))
                        put("subtitle", t.subtitle.take(240))
                        put("photoUrl", t.photoUrl.take(500))
                    })
                }
            }

            // 4. Write Campaign (Strictly PENDING until Admin verifies Payment AND approves Ad separately)
            val campaignJson = JSONObject().apply {
                put("id", campaignId)
                put("advertiserId", session.id)
                put("advertiserName", session.ownerName)
                put("businessName", session.businessName)
                put("contactMobile", session.mobile)
                put("title", multiSummaryTitle.take(160))
                put("description", description.trim().take(500))
                put("imageUrl", resolvedImg)
                put("destinationUrl", destinationUrl.trim().take(300))
                put("packageId", pkg.id)
                put("packageName", pkg.name)
                put("adType", normType)
                put("placement", effectivePlacement)
                put("price", pkg.price) // Server-locked immutable price
                put("durationDays", lockedDuration)
                put("targetRecordType", primaryTarget?.recordType.orEmpty())
                put("targetRecordId", primaryTarget?.recordId.orEmpty())
                put("targetRecordTitle", (primaryTarget?.title ?: "").take(160))
                put("targetRecordSubtitle", (primaryTarget?.subtitle ?: "").take(240))
                put("targetRecordPhotoUrl", (primaryTarget?.photoUrl ?: "").take(500))
                if (cleanedTargets.isNotEmpty()) {
                    put("promotedTargets", targetsJsonArray)
                }
                put("paymentStatus", AdStatuses.PENDING)
                put("approvalStatus", AdStatuses.PENDING)
                put("campaignStatus", AdStatuses.PENDING)
                put("startDate", 0L)
                put("expiryDate", 0L)
                put("activatedAt", 0L)
                put("renewalOfCampaignId", renewalOfCampaignId)
                put("impressionsCount", 0L)
                put("clicksCount", 0L)
                put("createdAt", now)
                put("updatedAt", now)
            }

            val okCamp = putJson(authedUrl("advertising/campaigns/$campaignId"), campaignJson.toString())
            if (!okCamp) {
                return@withContext Result.failure(IllegalStateException("Failed to submit advertisement campaign."))
            }

            // 5. Mirror reference into /homeSlider/advertiserCampaigns if HOME_SLIDER
            if (normType == AdPackageTypes.HOME_SLIDER) {
                val sliderJson = JSONObject().apply {
                    put("campaignId", campaignId)
                    put("advertiserId", session.id)
                    put("targetRecordType", primaryTarget?.recordType.orEmpty())
                    put("targetRecordId", primaryTarget?.recordId.orEmpty())
                    put("promotedTargets", targetsJsonArray)
                    put("approvalStatus", AdStatuses.PENDING)
                    put("paymentStatus", AdStatuses.PENDING)
                    put("campaignStatus", AdStatuses.PENDING)
                    put("paused", false)
                    put("startDate", 0L)
                    put("expiryDate", 0L)
                    put("priority", 100)
                    put("createdAt", now)
                }
                putJson(authedUrl("homeSlider/advertiserCampaigns/$campaignId"), sliderJson.toString())
            }

            // 6. Write Payment + TRX Index
            val paymentJson = JSONObject().apply {
                put("id", paymentId)
                put("campaignId", campaignId)
                put("advertiserId", session.id)
                put("advertiserName", session.ownerName)
                put("businessName", session.businessName)
                put("packageId", pkg.id)
                put("packageName", pkg.name)
                put("methodId", paymentMethod.id)
                put("methodName", paymentMethod.methodName)
                put("provider", paymentMethod.provider)
                put("senderMobile", cleanSender)
                put("receivingNumber", paymentMethod.receivingNumber)
                put("trxId", trxId.trim())
                put("normalizedTrxId", normTrx)
                put("amount", amountPaid)
                put("expectedAmount", pkg.price)
                put("paymentDate", paymentDate.trim().ifBlank { SponsorCampaignSelector.todayDateKey(now) })
                put("screenshotUrl", screenshotUrl.trim())
                put("status", AdStatuses.PENDING)
                put("createdAt", now)
                put("updatedAt", now)
            }
            val trxJson = JSONObject().apply {
                put("paymentId", paymentId)
                put("campaignId", campaignId)
                put("advertiserId", session.id)
                put("createdAt", now)
            }
            putJson(authedUrl("advertising/trxIndex/$normTrx"), trxJson.toString())
            val okPay = putJson(authedUrl("advertising/payments/$paymentId"), paymentJson.toString())
            if (!okPay) {
                return@withContext Result.failure(IllegalStateException("Campaign created, but payment submission failed. Contact Admin."))
            }

            Result.success(
                parseCampaign(campaignId, campaignJson)
            )
        } catch (e: Exception) {
            Log.w(TAG, "submitCampaignWithPayment failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Atomically advances a rotation queue in `/advertising/rotation/{queueKey}` using ETag conditional PUT.
     */
    suspend fun atomicAdvanceRotationQueue(
        queueKey: String,
        lastCampaignId: String,
        fallbackLocalValue: Long
    ): Long = withContext(Dispatchers.IO) {
        if (queueKey !in AdvertisingRotationEngine.ALL_QUEUES) return@withContext fallbackLocalValue
        val url = authedUrl("advertising/rotation/$queueKey")
        repeat(3) {
            var getConn: HttpURLConnection? = null
            var putConn: HttpURLConnection? = null
            try {
                getConn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("X-Firebase-ETag", "true")
                }
                val etag = getConn.getHeaderField("ETag")
                val body = if (getConn.responseCode in 200..299) {
                    getConn.inputStream.bufferedReader().use { it.readText() }.trim()
                } else "{}"
                val remoteIndex = runCatching {
                    if (body.startsWith("{")) JSONObject(body).optLong("index", 0L)
                    else body.toLongOrNull() ?: 0L
                }.getOrDefault(0L)
                val nextIndex = maxOf(remoteIndex + 1L, fallbackLocalValue)
                val payload = JSONObject().apply {
                    put("index", nextIndex)
                    put("lastCampaignId", lastCampaignId)
                    put("updatedAt", System.currentTimeMillis())
                }.toString()
                putConn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    if (!etag.isNullOrBlank()) setRequestProperty("if-match", etag)
                }
                OutputStreamWriter(putConn.outputStream, Charsets.UTF_8).use { it.write(payload) }
                val code = putConn.responseCode
                if (code in 200..299) return@withContext nextIndex
                if (code != 412) return@withContext fallbackLocalValue
            } catch (_: Exception) {
                return@withContext fallbackLocalValue
            } finally {
                getConn?.disconnect()
                putConn?.disconnect()
            }
        }
        fallbackLocalValue
    }

    suspend fun logImpression(campaign: AdCampaignItem, queueKey: String) = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val eventId = "imp_${now}_${UUID.randomUUID().toString().take(6)}"
            val eventJson = JSONObject().apply {
                put("id", eventId)
                put("campaignId", campaign.id)
                put("advertiserId", campaign.advertiserId)
                put("adType", campaign.adType)
                put("placement", campaign.placement)
                put("queueKey", queueKey)
                put("timestamp", now)
            }
            putJson(authedUrl("advertising/impressions/$eventId"), eventJson.toString())
            atomicIncrementField(authedUrl("advertising/campaigns/${campaign.id}/impressionsCount"))
        }
    }

    suspend fun logClick(campaign: AdCampaignItem, queueKey: String) = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val eventId = "clk_${now}_${UUID.randomUUID().toString().take(6)}"
            val eventJson = JSONObject().apply {
                put("id", eventId)
                put("campaignId", campaign.id)
                put("advertiserId", campaign.advertiserId)
                put("adType", campaign.adType)
                put("placement", campaign.placement)
                put("queueKey", queueKey)
                put("timestamp", now)
            }
            putJson(authedUrl("advertising/clicks/$eventId"), eventJson.toString())
            atomicIncrementField(authedUrl("advertising/campaigns/${campaign.id}/clicksCount"))
        }
    }

    private fun atomicIncrementField(url: String) {
        repeat(3) {
            var getConn: HttpURLConnection? = null
            var putConn: HttpURLConnection? = null
            try {
                getConn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("X-Firebase-ETag", "true")
                }
                val etag = getConn.getHeaderField("ETag")
                val raw = if (getConn.responseCode in 200..299) {
                    getConn.inputStream.bufferedReader().use { it.readText() }.trim()
                } else "0"
                val cur = raw.toLongOrNull() ?: 0L
                val next = cur + 1L
                putConn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    if (!etag.isNullOrBlank()) setRequestProperty("if-match", etag)
                }
                OutputStreamWriter(putConn.outputStream, Charsets.UTF_8).use { it.write(next.toString()) }
                val code = putConn.responseCode
                if (code in 200..299 || code != 412) return
            } catch (_: Exception) {
                return
            } finally {
                getConn?.disconnect()
                putConn?.disconnect()
            }
        }
    }

    // --- JSON Parsers ---
    private fun parsePackages(root: JSONObject?): List<AdPackageItem> {
        if (root == null) return emptyList()
        val list = mutableListOf<AdPackageItem>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val o = root.optJSONObject(id) ?: continue
            parseSinglePackage(id, o)?.let(list::add)
        }
        return list.sortedBy { it.createdAt }
    }

    private fun parseSinglePackage(id: String, o: JSONObject): AdPackageItem? {
        val name = o.optString("name", o.optString("packageName", "")).trim()
        val adType = AdPackageTypes.normalize(o.optString("adType", ""))
        if (name.isBlank() || !AdPackageTypes.isSupported(adType)) return null
        val duration = o.optInt("durationDays", 30).coerceAtLeast(1)
        return AdPackageItem(
            id = id,
            name = name,
            description = o.optString("description", "").trim(),
            price = o.optDouble("price", 0.0),
            durationDays = duration,
            adType = adType,
            placement = AdPlacements.normalize(o.optString("placement", AdPackageTypes.defaultPlacement(adType))),
            maxCampaignDurationDays = o.optInt("maxCampaignDurationDays", duration).coerceAtLeast(duration),
            imageUrl = o.optString("imageUrl", "").trim(),
            status = o.optString("status", AdStatuses.ACTIVE).trim().uppercase(),
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }

    private fun parsePaymentMethods(root: JSONObject?): List<AdPaymentMethod> {
        if (root == null) return emptyList()
        val list = mutableListOf<AdPaymentMethod>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val o = root.optJSONObject(id) ?: continue
            list.add(
                AdPaymentMethod(
                    id = id,
                    methodName = o.optString("methodName", o.optString("provider", "")).trim(),
                    provider = o.optString("provider", "").trim(),
                    receivingNumber = o.optString("receivingNumber", "").trim(),
                    accountType = o.optString("accountType", "Personal").trim(),
                    instructions = o.optString("instructions", "").trim(),
                    status = o.optString("status", AdStatuses.ACTIVE).trim().uppercase()
                )
            )
        }
        return list
    }

    private fun parseCampaigns(root: JSONObject?): List<AdCampaignItem> {
        if (root == null) return emptyList()
        val list = mutableListOf<AdCampaignItem>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val o = root.optJSONObject(id) ?: continue
            list.add(parseCampaign(id, o))
        }
        return list
    }

    private fun parseCampaign(id: String, o: JSONObject): AdCampaignItem {
        val parsedTargets = mutableListOf<PromotedTargetRef>()
        val targetsArr = o.optJSONArray("promotedTargets")
        if (targetsArr != null) {
            for (i in 0 until targetsArr.length()) {
                val item = targetsArr.optJSONObject(i) ?: continue
                val rType = SliderRecordTypes.normalize(item.optString("recordType", ""))
                val rId = item.optString("recordId", "").trim()
                if (SliderRecordTypes.isSupported(rType) && rId.isNotBlank()) {
                    parsedTargets.add(
                        PromotedTargetRef(
                            recordType = rType,
                            recordId = rId,
                            title = item.optString("title", "").trim(),
                            subtitle = item.optString("subtitle", "").trim(),
                            photoUrl = item.optString("photoUrl", "").trim()
                        )
                    )
                }
            }
        }
        val rawImageUrl = o.optString("imageUrl", "").trim()
            .ifBlank { o.optString("bannerUrl", "").trim() }
            .ifBlank { o.optString("creativeUrl", "").trim() }
        return AdCampaignItem(
            id = id,
            advertiserId = o.optString("advertiserId", "").trim(),
            advertiserName = o.optString("advertiserName", "").trim(),
            businessName = o.optString("businessName", "").trim(),
            contactMobile = o.optString("contactMobile", "").trim(),
            title = o.optString("title", "").trim(),
            description = o.optString("description", "").trim(),
            imageUrl = rawImageUrl,
            destinationUrl = o.optString("destinationUrl", "").trim(),
            packageId = o.optString("packageId", "").trim(),
            packageName = o.optString("packageName", "").trim(),
            adType = AdPackageTypes.normalize(o.optString("adType", "")),
            placement = AdPlacements.normalize(o.optString("placement", "")),
            price = o.optDouble("price", 0.0),
            durationDays = o.optInt("durationDays", 0),
            targetRecordType = SliderRecordTypes.normalize(o.optString("targetRecordType", "")),
            targetRecordId = o.optString("targetRecordId", "").trim(),
            targetRecordTitle = o.optString("targetRecordTitle", "").trim(),
            targetRecordSubtitle = o.optString("targetRecordSubtitle", "").trim(),
            targetRecordPhotoUrl = o.optString("targetRecordPhotoUrl", "").trim(),
            promotedTargets = parsedTargets,
            paymentStatus = o.optString("paymentStatus", AdStatuses.PENDING).trim().uppercase(),
            approvalStatus = o.optString("approvalStatus", AdStatuses.PENDING).trim().uppercase(),
            campaignStatus = o.optString("campaignStatus", AdStatuses.PENDING).trim().uppercase(),
            startDate = o.optLong("startDate", 0L),
            expiryDate = o.optLong("expiryDate", 0L),
            activatedAt = o.optLong("activatedAt", 0L),
            priorityOverride = o.optInt("priorityOverride", Int.MAX_VALUE),
            renewalOfCampaignId = o.optString("renewalOfCampaignId", "").trim(),
            impressionsCount = o.optLong("impressionsCount", 0L),
            clicksCount = o.optLong("clicksCount", 0L),
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }

    private fun parsePayments(root: JSONObject?): List<AdPaymentRecord> {
        if (root == null) return emptyList()
        val list = mutableListOf<AdPaymentRecord>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val o = root.optJSONObject(id) ?: continue
            list.add(
                AdPaymentRecord(
                    id = id,
                    campaignId = o.optString("campaignId", "").trim(),
                    advertiserId = o.optString("advertiserId", "").trim(),
                    advertiserName = o.optString("advertiserName", "").trim(),
                    businessName = o.optString("businessName", "").trim(),
                    packageId = o.optString("packageId", "").trim(),
                    packageName = o.optString("packageName", "").trim(),
                    methodId = o.optString("methodId", "").trim(),
                    methodName = o.optString("methodName", "").trim(),
                    provider = o.optString("provider", "").trim(),
                    senderMobile = o.optString("senderMobile", "").trim(),
                    receivingNumber = o.optString("receivingNumber", "").trim(),
                    trxId = o.optString("trxId", "").trim(),
                    normalizedTrxId = o.optString("normalizedTrxId", "").trim(),
                    amount = o.optDouble("amount", 0.0),
                    expectedAmount = o.optDouble("expectedAmount", 0.0),
                    paymentDate = o.optString("paymentDate", "").trim(),
                    screenshotUrl = o.optString("screenshotUrl", "").trim(),
                    status = o.optString("status", AdStatuses.PENDING).trim().uppercase(),
                    rejectionReason = o.optString("rejectionReason", "").trim(),
                    verifiedBy = o.optString("verifiedBy", "").trim(),
                    verifiedAt = o.optLong("verifiedAt", 0L),
                    createdAt = o.optLong("createdAt", 0L)
                )
            )
        }
        return list
    }

    private fun parseAdvertiserHomeSliders(root: JSONObject?): List<AdvertiserHomeSliderItem> {
        if (root == null) return emptyList()
        val list = mutableListOf<AdvertiserHomeSliderItem>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val o = root.optJSONObject(id) ?: continue
            list.add(
                AdvertiserHomeSliderItem(
                    campaignId = o.optString("campaignId", id).trim(),
                    advertiserId = o.optString("advertiserId", "").trim(),
                    targetRecordType = SliderRecordTypes.normalize(o.optString("targetRecordType", "")),
                    targetRecordId = o.optString("targetRecordId", "").trim(),
                    approvalStatus = o.optString("approvalStatus", AdStatuses.PENDING).trim().uppercase(),
                    paymentStatus = o.optString("paymentStatus", AdStatuses.PENDING).trim().uppercase(),
                    campaignStatus = o.optString("campaignStatus", AdStatuses.PENDING).trim().uppercase(),
                    paused = o.optBoolean("paused", false),
                    startDate = o.optLong("startDate", 0L),
                    expiryDate = o.optLong("expiryDate", 0L),
                    priority = o.optInt("priority", 100),
                    createdAt = o.optLong("createdAt", 0L)
                )
            )
        }
        return list
    }

    private fun parseAdminCustomSliders(root: JSONObject?): List<AdminCustomSliderItem> {
        if (root == null) return emptyList()
        val list = mutableListOf<AdminCustomSliderItem>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val o = root.optJSONObject(id) ?: continue
            list.add(
                AdminCustomSliderItem(
                    id = id,
                    title = o.optString("title", "").trim(),
                    description = o.optString("description", "").trim(),
                    imageUrl = o.optString("imageUrl", "").trim(),
                    destinationUrl = o.optString("destinationUrl", "").trim(),
                    order = o.optInt("order", 1),
                    startDate = o.optLong("startDate", 0L),
                    expiryDate = o.optLong("expiryDate", 0L),
                    status = o.optString("status", AdStatuses.ACTIVE).trim().uppercase(),
                    createdAt = o.optLong("createdAt", 0L)
                )
            )
        }
        return list
    }

    private fun parseHomeSliderSettings(obj: JSONObject?): HomeSliderSettings {
        if (obj == null) return HomeSliderSettings()
        return HomeSliderSettings(
            enabled = obj.optBoolean("enabled", true),
            customSliderPosition = obj.optString("customSliderPosition", "BEFORE").uppercase(),
            autoSlideIntervalMs = obj.optLong("autoSlideIntervalMs", 4500L).coerceIn(2000L, 15000L)
        )
    }

    private fun parsePlacementSettings(obj: JSONObject?): PlacementGlobalSettings {
        if (obj == null) return PlacementGlobalSettings()
        return PlacementGlobalSettings(
            adsEnabled = obj.optBoolean("adsEnabled", true),
            homeSliderEnabled = obj.optBoolean("homeSliderEnabled", true),
            doctorNativeEnabled = obj.optBoolean("doctorNativeEnabled", true),
            hospitalNativeEnabled = obj.optBoolean("hospitalNativeEnabled", true),
            diagnosticNativeEnabled = obj.optBoolean("diagnosticNativeEnabled", true),
            favoriteNativeEnabled = obj.optBoolean("favoriteNativeEnabled", true),
            bottomBannerEnabled = obj.optBoolean("bottomBannerEnabled", true),
            appOpenPopupEnabled = obj.optBoolean("appOpenPopupEnabled", true),
            popupOncePerSession = obj.optBoolean("popupOncePerSession", true),
            nativeInterval = obj.optInt("nativeInterval", 3).coerceAtLeast(3)
        )
    }

    private fun parseRotationCounters(obj: JSONObject?): Map<String, Long> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, Long>()
        for (queue in AdvertisingRotationEngine.ALL_QUEUES) {
            val raw = obj.opt(queue)
            val v = when (raw) {
                is Number -> raw.toLong()
                is JSONObject -> raw.optLong("index", 0L)
                else -> 0L
            }
            if (v >= 0L) out[queue] = v
        }
        return out
    }

    private fun normalizePhone(raw: String): String =
        raw.replace(Regex("[^0-9+]"), "").trim()

    private fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun getJsonObject(urlString: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            if (body.isEmpty() || body == "null") null else JSONObject(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun putJson(urlString: String, jsonBody: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
