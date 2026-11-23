package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AdConfigEntity
import com.example.data.local.AppDatabase
import com.example.data.local.PaymentSettingsEntity
import com.example.data.local.PendingAdViewEventEntity
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.RateCardSettingsEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.SponsorEntity
import com.example.data.local.SponsorPackageEntity
import com.example.data.remote.AdvertiserRemoteService
import com.example.ui.ads.AdRevenueTracker
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.ads.SponsorOfflineCacheManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Comprehensive verification suite for the Final Hybrid Ad Configuration:
 * - ONLINE:  Start.io Ads + Unity Ads + Custom Direct Sponsor Ads (ZERO AdMob)
 * - OFFLINE: Custom Direct Sponsor Ads ONLY (Cached Banner, Popup, Native Card, About Us)
 * - Server-authoritative activation/expiry + offline clock rollback protection
 * - Dynamic packages, manual payment validation, salted SHA-256 advertiser password hashing
 * - Persistent Room v7 cache + local image file cache + offline view analytics queue & dedup
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HybridAdSystemTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var cacheDir: File

    // Fixed reference time: 2026-06-15 12:00:00 Asia/Dhaka
    private val refNowMs: Long =
        SponsorCampaignSelector.parseCampaignBounds("2026-06-15", "2026-06-15").first + 12L * 3600L * 1000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cacheDir = File(context.filesDir, "test_sponsor_cache_${System.nanoTime()}").apply { mkdirs() }
        AdRevenueTracker.clearMemoryCooldownsForTesting()
        ConnectivityMonitor.setOnlineForTesting(true)
    }

    @After
    fun tearDown() {
        db.close()
        cacheDir.deleteRecursively()
        AdRevenueTracker.clearMemoryCooldownsForTesting()
        ConnectivityMonitor.setOnlineForTesting(true)
    }

    private fun makeSponsor(
        id: String,
        name: String = "Sponsor $id",
        status: String = SponsorCampaignSelector.STATUS_ACTIVE,
        placementType: String = SponsorCampaignSelector.PLACEMENT_ALL,
        startDate: String = "2026-01-01",
        endDate: String = "2026-12-31",
        activatedAt: Long = 0L,
        expiryAt: Long = Long.MAX_VALUE,
        targetPages: String = "ALL",
        priority: Int = 10,
        frequency: Int = 1,
        isActive: Boolean = true,
        localBannerPath: String = "",
        bannerUrl: String = "https://cdn.example.com/$id.png",
        placementIds: String = "",
        moduleNames: String = "",
        adSizeKey: String = ""
    ): SponsorEntity {
        val (sTs, eTs) = SponsorCampaignSelector.parseCampaignBounds(startDate, endDate)
        return SponsorEntity(
            id = id,
            status = status,
            placementType = placementType,
            name = name,
            logoUrl = "https://cdn.example.com/${id}_logo.png",
            bannerImageUrl = bannerUrl,
            localBannerPath = localBannerPath,
            promoTitle = "Special Offer from $name",
            description = "24/7 Healthcare Discount",
            websiteUrl = "https://example.com/$id",
            contactNumber = "+8801700000000",
            startDate = startDate,
            endDate = endDate,
            activatedAt = activatedAt,
            expiryAt = expiryAt,
            startTimestamp = sTs,
            endTimestamp = eTs,
            targetPages = targetPages,
            displayPriority = priority,
            displayFrequency = frequency,
            isActive = isActive,
            placementIds = placementIds,
            moduleNames = moduleNames,
            adSizeKey = adSizeKey
        )
    }

    // 1. Strictly ZERO third-party ad network SDKs bundled in the APK
    @Test
    fun verifyZeroThirdPartyAdNetworkSdksBundled() {
        val admobLoaded = runCatching { Class.forName("com.google.android.gms.ads.MobileAds") }.isSuccess
        val startIoLoaded = runCatching { Class.forName("com.startapp.sdk.adsbase.StartAppSDK") }.isSuccess
        val unityLoaded = runCatching { Class.forName("com.unity3d.ads.UnityAds") }.isSuccess

        assertFalse("Google AdMob SDK must be absent", admobLoaded)
        assertFalse("Start.io SDK must be absent", startIoLoaded)
        assertFalse("Unity Ads SDK must be absent", unityLoaded)
    }

    // 2. Default AdConfig keeps all network/ad flags disabled safely
    @Test
    fun defaultAdConfigKeepsThirdPartyAndDirectAdsDisabledSafely() {
        val cfg = AdConfigEntity()
        assertFalse(cfg.adsEnabled)
        assertFalse(cfg.bannerEnabled)
        assertFalse(cfg.appOpenEnabled)
        assertFalse(cfg.nativeBannerEnabled)
        assertFalse(cfg.rewardedEnabled)
    }

    // 3. Server-authoritative lifecycle & expiry enforcement
    @Test
    fun lifecycleEnforcesActiveStatusAndServerAuthoritativeWindow() {
        val dayMs = 24L * 3600L * 1000L
        val active = makeSponsor(
            id = "sp_active",
            status = SponsorCampaignSelector.STATUS_ACTIVE,
            activatedAt = refNowMs - 2 * dayMs,
            expiryAt = refNowMs + 5 * dayMs
        )
        val pendingPayment = makeSponsor("sp_pp", status = SponsorCampaignSelector.STATUS_PENDING_PAYMENT)
        val pendingApproval = makeSponsor("sp_pa", status = SponsorCampaignSelector.STATUS_PENDING_APPROVAL)
        val paused = makeSponsor("sp_paused", status = SponsorCampaignSelector.STATUS_PAUSED)
        val rejected = makeSponsor("sp_rej", status = SponsorCampaignSelector.STATUS_REJECTED)
        val expiredByTime = makeSponsor(
            id = "sp_exp",
            status = SponsorCampaignSelector.STATUS_ACTIVE,
            activatedAt = refNowMs - 10 * dayMs,
            expiryAt = refNowMs - 1000L
        )

        assertTrue(SponsorCampaignSelector.isCampaignValid(active, refNowMs))
        assertEquals(5, SponsorCampaignSelector.remainingDays(active, refNowMs))

        assertFalse(SponsorCampaignSelector.isCampaignValid(pendingPayment, refNowMs))
        assertFalse(SponsorCampaignSelector.isCampaignValid(pendingApproval, refNowMs))
        assertFalse(SponsorCampaignSelector.isCampaignValid(paused, refNowMs))
        assertFalse(SponsorCampaignSelector.isCampaignValid(rejected, refNowMs))
        assertFalse(SponsorCampaignSelector.isCampaignValid(expiredByTime, refNowMs))
        assertTrue(SponsorCampaignSelector.isCampaignExpired(expiredByTime, refNowMs))
        assertEquals(0, SponsorCampaignSelector.remainingDays(expiredByTime, refNowMs))
    }

    // 4. Offline clock rollback protection via monotonic elapsedRealtime anchor
    @Test
    fun authoritativeClockBlocksOfflineDeviceClockRollback() {
        val serverSyncMs = refNowMs
        val syncElapsedMs = 100_000L
        val tenMinutesLaterElapsedMs = syncElapsedMs + 600_000L
        // User maliciously sets device clock back by 30 days while offline
        val rolledBackDeviceClockMs = refNowMs - 30L * 24L * 3600L * 1000L

        val authoritativeNow = SessionPreferences.computeAuthoritativeNowMs(
            deviceWallClockMs = rolledBackDeviceClockMs,
            lastServerSyncAtMs = serverSyncMs,
            lastServerSyncElapsedMs = syncElapsedMs,
            currentElapsedMs = tenMinutesLaterElapsedMs
        )
        assertEquals(serverSyncMs + 600_000L, authoritativeNow)
    }

    // 5. All 4 Custom Direct Sponsor formats: Banner, Popup, Native Card, About Us Sponsor Ad
    @Test
    fun allFourDirectSponsorFormatsSelectAndFilterProperly() {
        val banner320 = makeSponsor("sp_b320", placementType = SponsorCampaignSelector.PLACEMENT_BANNER_320x100, priority = 30)
        val banner300 = makeSponsor("sp_b300", placementType = SponsorCampaignSelector.PLACEMENT_BANNER_300x200, priority = 20)
        val popupAd = makeSponsor("sp_pop", placementType = SponsorCampaignSelector.PLACEMENT_POPUP, priority = 40)
        val nativeAd = makeSponsor("sp_nat", placementType = SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, priority = 25)
        val aboutUsAd = makeSponsor("sp_abt", placementType = SponsorCampaignSelector.PLACEMENT_ABOUT_US, priority = 15)
        val all = listOf(banner320, banner300, popupAd, nativeAd, aboutUsAd)

        // Banner selects banner placement
        val pickedBanner = SponsorCampaignSelector.selectBannerSponsor(all, "home", 0, refNowMs)
        assertEquals("sp_b320", pickedBanner?.id)

        // Popup prefers POPUP_AD placement
        val pickedPopup = SponsorCampaignSelector.selectAppOpenPopupSponsor(all, "", refNowMs)
        assertEquals("sp_pop", pickedPopup?.id)

        // Native selects SPONSORED_NATIVE_CARD / 300x200 and rotates without duplicates
        val nat0 = SponsorCampaignSelector.selectNativeSponsorForSlot(all, "find_doctors", 0, refNowMs)
        val nat1 = SponsorCampaignSelector.selectNativeSponsorForSlot(all, "find_doctors", 1, refNowMs)
        assertEquals("sp_nat", nat0?.id)
        assertEquals("sp_b300", nat1?.id)
        assertNotEquals(nat0?.id, nat1?.id)

        // About Us prioritizes featured ad then dedicated ABOUT_US_SPONSOR, capped by maxAds
        val aboutPicked = SponsorCampaignSelector.selectAboutUsSponsors(
            sponsors = all,
            featuredAdId = "sp_b300",
            maxAds = 2,
            nowMs = refNowMs
        )
        assertEquals(2, aboutPicked.size)
        assertEquals("sp_b300", aboutPicked[0].id)
        assertEquals("sp_abt", aboutPicked[1].id)
    }

    // 6. Popup frequency (EVERY_LAUNCH vs ONCE_DAILY) & Native interval (4, 5, 6)
    @Test
    fun popupFrequencyAndNativeIntervalsWorkAccurately() {
        assertTrue(
            SponsorCampaignSelector.shouldShowAppOpenPopup(
                popupEnabled = true,
                frequency = SponsorCampaignSelector.FREQ_EVERY_LAUNCH,
                lastShownDate = "2026-06-15",
                todayDate = "2026-06-15",
                alreadyShownThisLaunch = false
            )
        )
        assertFalse(
            SponsorCampaignSelector.shouldShowAppOpenPopup(
                popupEnabled = true,
                frequency = SponsorCampaignSelector.FREQ_ONCE_DAILY,
                lastShownDate = "2026-06-15",
                todayDate = "2026-06-15",
                alreadyShownThisLaunch = false
            )
        )
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(3, 4))
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(4, 5))
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(5, 6))
        assertFalse(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(2, 5))
    }

    // 7. Image, URL, Mobile, TrxID validation & Salted SHA-256 hashing
    @Test
    fun validatesMediaPaymentsAndSaltedPasswordHash() {
        assertTrue(SponsorCampaignSelector.isValidImageSource("https://cdn.hospitalbd.com/banner.png"))
        assertTrue(SponsorCampaignSelector.isValidImageSource("data:image/webp;base64,UklGRh4="))
        assertFalse(SponsorCampaignSelector.isValidImageSource("javascript:alert(1)"))

        assertTrue(SponsorCampaignSelector.validateImageDimensions(300, 200, 300, 200))
        assertFalse(SponsorCampaignSelector.validateImageDimensions(50, 20, 300, 200))

        assertTrue(SponsorCampaignSelector.isValidBangladeshMobile("01712345678"))
        assertTrue(SponsorCampaignSelector.isValidBangladeshMobile("+8801812345678"))
        assertFalse(SponsorCampaignSelector.isValidBangladeshMobile("12345"))

        assertEquals("TRX9AB88C", SponsorCampaignSelector.normalizeTransactionId("  trx-9ab 88c "))

        val validPng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        )
        assertTrue(SponsorCampaignSelector.isSupportedImageBytes(validPng))
        assertFalse(SponsorCampaignSelector.isSupportedImageBytes("<script>xss</script>".toByteArray()))

        val salt1 = AdvertiserRemoteService.generateSalt()
        val salt2 = AdvertiserRemoteService.generateSalt()
        assertNotEquals(salt1, salt2)
        assertEquals(
            AdvertiserRemoteService.hashPassword("Secret#2026", salt1),
            AdvertiserRemoteService.hashPassword("Secret#2026", salt1)
        )
        assertNotEquals(
            AdvertiserRemoteService.hashPassword("Secret#2026", salt1),
            AdvertiserRemoteService.hashPassword("Secret#2026", salt2)
        )
    }

    // 8. Room v7 persistence, local file offline cache & offline view analytics queue with 30s dedup
    @Test
    fun offlineCacheAndQueuedViewEventsWorkOfflineWithDuplicateProtection() = runBlocking {
        // Persist dynamic package & payment settings in Room
        db.sponsorPackageDao().insertAll(
            listOf(
                SponsorPackageEntity(
                    id = "pkg_300x200_30d",
                    name = "Standard 300x200 Banner",
                    widthPx = 300,
                    heightPx = 200,
                    durationDays = 30,
                    priceBdt = 300,
                    placementType = SponsorCampaignSelector.PLACEMENT_BANNER_300x200,
                    status = "ACTIVE"
                )
            )
        )
        db.paymentSettingsDao().save(
            PaymentSettingsEntity(
                bkashNumber = "01700000001",
                bkashAccountType = "Personal",
                nagadNumber = "01800000002",
                nagadAccountType = "Personal"
            )
        )
        assertEquals(1, db.sponsorPackageDao().getActiveOnce().size)
        assertEquals("01700000001", db.paymentSettingsDao().getOnce()?.bkashNumber)

        // Save validated sponsor image to local cache
        val validPng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        )
        val savedFile = SponsorOfflineCacheManager.saveValidatedImageToCache(
            cacheDir = cacheDir,
            sponsorId = "sp_cached",
            kind = "banner",
            sourceUrl = "https://cdn.example.com/cached.png",
            bytes = validPng
        )
        assertNotNull(savedFile)
        db.sponsorDao().insert(makeSponsor(id = "sp_cached", localBannerPath = savedFile!!.absolutePath))

        // Switch to OFFLINE mode -> cached file resolves locally, uncached HTTPS blocked
        ConnectivityMonitor.setOnlineForTesting(false)
        assertFalse(ConnectivityMonitor.isOnline.value)

        val offlineModel = SponsorOfflineCacheManager.resolveImageModel(
            localPath = savedFile.absolutePath,
            remoteUrl = "https://cdn.example.com/cached.png",
            isOnline = false
        )
        assertTrue(offlineModel is File)
        assertNull(
            SponsorOfflineCacheManager.resolveImageModel(
                localPath = "",
                remoteUrl = "https://cdn.example.com/uncached.png",
                isOnline = false
            )
        )

        // Offline visible impression queues in Room & enforces 30s duplicate protection
        val prefs = SessionPreferences(context)
        val firstCounted = AdRevenueTracker.recordVisibleImpression(
            db = db,
            prefs = prefs,
            adId = "sp_cached",
            packageId = "pkg_300x200_30d",
            slotKey = "banner_home",
            isOnline = false,
            nowMs = refNowMs
        )
        val duplicateBlocked = AdRevenueTracker.recordVisibleImpression(
            db = db,
            prefs = prefs,
            adId = "sp_cached",
            packageId = "pkg_300x200_30d",
            slotKey = "banner_home",
            isOnline = false,
            nowMs = refNowMs + 10_000L
        )
        val afterCooldown = AdRevenueTracker.recordVisibleImpression(
            db = db,
            prefs = prefs,
            adId = "sp_cached",
            packageId = "pkg_300x200_30d",
            slotKey = "banner_home",
            isOnline = false,
            nowMs = refNowMs + 35_000L
        )

        assertTrue(firstCounted)
        assertFalse(duplicateBlocked)
        assertTrue(afterCooldown)

        val queued: List<PendingAdViewEventEntity> = db.pendingAdViewEventDao().getPendingOnce()
        assertEquals(2, queued.size)
        assertTrue(queued.all { it.wasOffline && it.adId == "sp_cached" })
    }

    // 9. All 37 Screen-Based Placements & 9 Standard Ad Sizes catalog completeness
    @Test
    fun placementRateCardCatalogContainsAll37CanonicalPlacementsAnd9Sizes() {
        val cards = PlacementRateCardEngine.defaultRateCards()
        assertEquals(37, cards.size)
        assertEquals(9, PlacementRateCardEngine.STANDARD_AD_SIZES.size)

        val ids = cards.map { it.placementId }.toSet()
        val expectedIds = setOf(
            "HOME-01", "HOME-02", "HOME-03", "HOME-04", "HOME-05", "HOME-06", "HOME-07",
            "DOC-01", "DOC-02", "DOC-03", "DOC-04", "DOC-05",
            "HOS-01", "HOS-02", "HOS-03", "HOS-04",
            "DIA-01", "DIA-02", "DIA-03", "DIA-04",
            "SPE-01", "SPE-02", "SPE-03",
            "CHA-01", "CHA-02", "CHA-03", "CHA-04",
            "OTH-SEARCH", "OTH-CATEGORY", "OTH-DISTRICT", "OTH-UPAZILA",
            "OTH-FEATURED", "OTH-ABOUT", "OTH-POPUP", "OTH-FULLSCREEN", "OTH-WEB", "OTH-FUTURE"
        )
        assertEquals(expectedIds, ids)
    }

    // 10. Multi-Placement Rate Calculation (Exact 500 + 300 + 300 + 300 = 1400, plus bundle discount & size overrides)
    @Test
    fun multiPlacementRateCalculationAndSizeOverridesWorkAccurately() {
        val cards = PlacementRateCardEngine.defaultRateCards()
        val selected = cards.filter {
            it.placementId in setOf("HOME-01", "DOC-01", "HOS-01", "DIA-01")
        }
        assertEquals(4, selected.size)

        // Without bundle discount: 500 + 300 + 300 + 300 = 1400 BDT for 30 days
        val noBundleSettings = RateCardSettingsEntity(bundleEnabled = false)
        val exactQuote = PlacementRateCardEngine.calculateBookingQuote(
            selectedCards = selected,
            adSizeKey = "320x100",
            durationDays = 30,
            settings = noBundleSettings
        )
        assertEquals(1400, exactQuote.subtotalBdt)
        assertEquals(0, exactQuote.discountBdt)
        assertEquals(1400, exactQuote.totalBdt)
        assertEquals(500, exactQuote.breakdownMap["HOME-01"])
        assertEquals(300, exactQuote.breakdownMap["DOC-01"])
        assertEquals(300, exactQuote.breakdownMap["HOS-01"])
        assertEquals(300, exactQuote.breakdownMap["DIA-01"])

        // With 4+ placement 15% bundle discount: 1400 - 210 = 1190 BDT
        val bundleSettings = RateCardSettingsEntity(
            bundleEnabled = true,
            discount2Pct = 5,
            discount3Pct = 10,
            discount4PlusPct = 15
        )
        val discountedQuote = PlacementRateCardEngine.calculateBookingQuote(
            selectedCards = selected,
            adSizeKey = "320x100",
            durationDays = 30,
            settings = bundleSettings
        )
        assertEquals(1400, discountedQuote.subtotalBdt)
        assertEquals(15, discountedQuote.discountPct)
        assertEquals(210, discountedQuote.discountBdt)
        assertEquals(1190, discountedQuote.totalBdt)

        // Same size (300x200) has different rates on DOC-01 vs HOME-02, plus per-size override scaling
        val home01WithOverride = cards.first { it.placementId == "HOME-01" }.copy(
            sizeRateOverridesJson = """{"320x100":750}"""
        )
        assertEquals(750, PlacementRateCardEngine.calculatePlacementRate(home01WithOverride, "320x100", 30))
        // 15d rate for HOME-01 is 320; scaled by 750/500 = 480
        assertEquals(480, PlacementRateCardEngine.calculatePlacementRate(home01WithOverride, "320x100", 15))
    }

    // 11. Placement Selection: Inactive suppression, Max Active Ads cap, Rotation modes & Room v8 DAO persistence
    @Test
    fun placementSelectionEnforcesStatusCapRotationAndRoomPersistence() = runBlocking {
        val baseCards = PlacementRateCardEngine.defaultRateCards()
        db.placementRateCardDao().insertAll(baseCards)
        db.rateCardSettingsDao().save(RateCardSettingsEntity(bundleEnabled = true, discount4PlusPct = 20))

        val loadedCards = db.placementRateCardDao().getAllOnce()
        assertEquals(37, loadedCards.size)
        assertEquals(20, db.rateCardSettingsDao().getOnce()?.discount4PlusPct)

        val sp1 = makeSponsor("sp1", priority = 30, frequency = 1, placementIds = "HOME-01,DOC-01")
        val sp2 = makeSponsor("sp2", priority = 20, frequency = 1, placementIds = "HOME-01")
        val sp3 = makeSponsor("sp3", priority = 10, frequency = 1, placementIds = "HOME-01")
        val sponsors = listOf(sp1, sp2, sp3)

        // Active sequential rotation respects maxActiveAds = 2 (sp3 excluded by priority cap)
        val seqCards = loadedCards.map {
            if (it.placementId == "HOME-01") it.copy(maxActiveAds = 2, rotationMode = PlacementRateCardEngine.ROTATION_SEQUENTIAL) else it
        }
        assertEquals("sp1", PlacementRateCardEngine.selectSponsorForPlacement(sponsors, seqCards, "HOME-01", 0, refNowMs)?.id)
        assertEquals("sp2", PlacementRateCardEngine.selectSponsorForPlacement(sponsors, seqCards, "HOME-01", 1, refNowMs)?.id)
        assertEquals("sp1", PlacementRateCardEngine.selectSponsorForPlacement(sponsors, seqCards, "HOME-01", 2, refNowMs)?.id)

        // Inactive placement suppresses ad rendering completely
        val inactiveCards = loadedCards.map {
            if (it.placementId == "HOME-01") it.copy(status = "INACTIVE") else it
        }
        assertNull(PlacementRateCardEngine.selectSponsorForPlacement(sponsors, inactiveCards, "HOME-01", 0, refNowMs))

        // Offline mode still resolves matching placement from cached sponsors
        ConnectivityMonitor.setOnlineForTesting(false)
        assertEquals("sp1", PlacementRateCardEngine.selectSponsorForPlacement(sponsors, seqCards, "DOC-01", 0, refNowMs)?.id)
    }
}
