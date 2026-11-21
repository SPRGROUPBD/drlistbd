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
import com.example.data.local.ScreenAdPlacementEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.SponsorEntity
import com.example.data.local.SponsorPackageEntity
import com.example.data.remote.AdvertiserRemoteService
import com.example.ui.ads.AdRevenueTracker
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.HybridNetworkAdController
import com.example.ui.ads.OnlineAdFormat
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.ads.SponsorOfflineCacheManager
import com.example.ui.ads.StartIoAdManager
import com.example.ui.ads.UnityAdsManager
import com.example.ui.ads.WortiseAdsManager
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
import org.robolectric.Robolectric
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
        HybridNetworkAdController.resetSessionCapsForTesting()
        ConnectivityMonitor.setOnlineForTesting(true)
    }

    @After
    fun tearDown() {
        db.close()
        cacheDir.deleteRecursively()
        AdRevenueTracker.clearMemoryCooldownsForTesting()
        HybridNetworkAdController.resetSessionCapsForTesting()
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

    // 1. All 3 official Ad Network SDKs (Unity Ads + Start.io + Wortise) are present and AdMob is never scheduled
    @Test
    fun verifyZeroAdMobAndStartIoPlusUnityPresentForOnlineMode() {
        val startIoLoaded = runCatching { Class.forName("com.startapp.sdk.adsbase.StartAppSDK") }.isSuccess
        val unityLoaded = runCatching { Class.forName("com.unity3d.ads.UnityAds") }.isSuccess
        val wortiseLoaded = runCatching { Class.forName("com.wortise.ads.WortiseSdk") }.isSuccess

        assertTrue("Start.io SDK must be present for Online Multi-Network Ads", startIoLoaded)
        assertTrue("Unity Ads SDK must be present for Online Multi-Network Ads", unityLoaded)
        assertTrue("Wortise SDK must be present for Online Multi-Network Ads", wortiseLoaded)

        // Ensure AdMob is never accepted or returned by HybridNetworkAdController
        val configWithInjectedAdmob = AdConfigEntity(
            adsEnabled = true,
            networkPriorityOrder = "ADMOB,UNITY,STARTIO,WORTISE",
            unityGameId = "5789123",
            startIoAppId = "210630916",
            wortiseAppId = "wortise-test-app-id",
            wortiseBannerAdUnitId = "wortise-banner-1"
        )
        val ordered = HybridNetworkAdController.getOrderedNetworksForOnline(
            config = configWithInjectedAdmob,
            format = OnlineAdFormat.BANNER,
            preferredNetworkOverride = "ADMOB"
        )
        assertFalse("ADMOB must never be scheduled by HybridNetworkAdController", ordered.contains("ADMOB"))
        assertEquals(listOf("UNITY", "STARTIO", "WORTISE"), ordered)
    }

    // 2. Hybrid Online Network ordering & failover rules
    @Test
    fun hybridNetworkControllerResolvesExactOnlineFailoverOrder() {
        val bothUnityFirst = AdConfigEntity(
            adsEnabled = true,
            activeNetwork = "BOTH_UNITY_PRIMARY",
            unityGameId = "5789123",
            startIoAppId = "210630916"
        )
        assertEquals(listOf("UNITY", "STARTIO"), HybridNetworkAdController.getOrderedNetworksForOnline(bothUnityFirst))

        val bothStartIoFirst = bothUnityFirst.copy(activeNetwork = "BOTH_STARTIO_PRIMARY")
        assertEquals(listOf("STARTIO", "UNITY"), HybridNetworkAdController.getOrderedNetworksForOnline(bothStartIoFirst))

        val unityMissingGameId = bothUnityFirst.copy(unityGameId = "")
        assertEquals(listOf("STARTIO"), HybridNetworkAdController.getOrderedNetworksForOnline(unityMissingGameId))

        val sponsorsOnly = bothUnityFirst.copy(activeNetwork = "SPONSORS_ONLY")
        assertTrue(HybridNetworkAdController.getOrderedNetworksForOnline(sponsorsOnly).isEmpty())
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

    // 12. Fixed Package Pricing Formula: Total Package Price = Fixed Package Rate (NEVER multiplied by days)
    @Test
    fun fixedPackagePricingNeverMultipliesByDurationAndComputesExpiryCorrectly() {
        // 4 Default Demo Packages: Banner All Screen 200, Native Banner 300, Popup App Open 180, Reward Video 600 (all 30d)
        assertEquals(200, SponsorCampaignSelector.calculateFixedPackageTotal(200, 30))
        assertEquals(300, SponsorCampaignSelector.calculateFixedPackageTotal(300, 30))
        assertEquals(180, SponsorCampaignSelector.calculateFixedPackageTotal(180, 30))
        assertEquals(600, SponsorCampaignSelector.calculateFixedPackageTotal(600, 30))

        // Changing duration to 7, 15, 60, or 90 days must NEVER multiply the fixed rate
        assertEquals(200, SponsorCampaignSelector.calculateFixedPackageTotal(200, 7))
        assertEquals(200, SponsorCampaignSelector.calculateFixedPackageTotal(200, 60))
        assertEquals(600, SponsorCampaignSelector.calculateFixedPackageTotal(600, 90))
        assertEquals(0, SponsorCampaignSelector.calculateFixedPackageTotal(-50, 30))

        // Duration only determines expiry timestamp
        val dayMs = 24L * 3600L * 1000L
        assertEquals(refNowMs + 30L * dayMs, SponsorCampaignSelector.computeExpiryTimestamp(refNowMs, 30))
        assertEquals(refNowMs + 15L * dayMs, SponsorCampaignSelector.computeExpiryTimestamp(refNowMs, 15))
    }

    // 13. All 7 Campaign Statuses enforced both online and offline + expired asset purge
    @Test
    fun allSevenCampaignStatusesEnforcedOnlineAndOffline() = runBlocking {
        val dayMs = 24L * 3600L * 1000L
        val pending = makeSponsor("st_pending", status = SponsorCampaignSelector.STATUS_PENDING)
        val paymentPending = makeSponsor("st_pay_pending", status = SponsorCampaignSelector.STATUS_PAYMENT_PENDING)
        val active = makeSponsor(
            id = "st_active",
            status = SponsorCampaignSelector.STATUS_ACTIVE,
            activatedAt = refNowMs - dayMs,
            expiryAt = refNowMs + 29L * dayMs
        )
        val paused = makeSponsor("st_paused", status = SponsorCampaignSelector.STATUS_PAUSED)
        val expired = makeSponsor(
            id = "st_expired",
            status = SponsorCampaignSelector.STATUS_EXPIRED,
            activatedAt = refNowMs - 40L * dayMs,
            expiryAt = refNowMs - 10L * dayMs
        )
        val rejected = makeSponsor("st_rejected", status = SponsorCampaignSelector.STATUS_REJECTED)
        val cancelled = makeSponsor("st_cancelled", status = SponsorCampaignSelector.STATUS_CANCELLED)

        val all = listOf(pending, paymentPending, active, paused, expired, rejected, cancelled)

        // Online: ONLY ACTIVE within window is valid
        ConnectivityMonitor.setOnlineForTesting(true)
        val validOnline = all.filter { SponsorCampaignSelector.isCampaignValid(it, refNowMs) }
        assertEquals(listOf("st_active"), validOnline.map { it.id })

        // Offline: Expired/Cancelled/Rejected/Pending/Paused must NEVER appear offline either
        ConnectivityMonitor.setOnlineForTesting(false)
        val validOffline = all.filter { SponsorCampaignSelector.isCampaignValid(it, refNowMs) }
        assertEquals(listOf("st_active"), validOffline.map { it.id })

        // Expired & cancelled sponsors are purged from Room & local cache during sync
        val expiredFile = File(cacheDir, "expired_banner.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val expiredDb = expired.copy(localBannerPath = expiredFile.absolutePath)
        db.sponsorDao().insert(expiredDb)
        db.sponsorDao().insert(cancelled)
        db.sponsorDao().insert(active.copy(activatedAt = System.currentTimeMillis() - dayMs, expiryAt = System.currentTimeMillis() + 10L * dayMs))

        SponsorOfflineCacheManager.syncSponsorAssets(context, db)
        assertFalse("Expired cached banner file must be deleted", expiredFile.exists())
        val remainingIds = db.sponsorDao().getAllOnce().map { it.id }
        assertFalse(remainingIds.contains("st_expired"))
        assertFalse(remainingIds.contains("st_cancelled"))
        assertTrue(remainingIds.contains("st_active"))
    }

    // 14. Banner 300x50, Reward Video Doctor Click & Package Placement Scopes
    @Test
    fun banner300x50AndRewardVideoDoctorClickSelectCorrectly() {
        val banner300x50 = makeSponsor(
            id = "sp_b300x50",
            placementType = SponsorCampaignSelector.PLACEMENT_BANNER_300x50,
            targetPages = "HOME",
            priority = 25
        )
        val rewardVideo = makeSponsor(
            id = "sp_rv_doc",
            placementType = SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO,
            targetPages = "DOCTOR_DETAILS",
            priority = 30
        ).copy(
            mediaType = "VIDEO",
            videoUrl = "https://cdn.example.com/sponsor-promo.mp4",
            adType = "REWARD_VIDEO",
            adPlacement = "DOCTOR_DETAILS"
        )

        val pickedBannerHome = SponsorCampaignSelector.selectBannerSponsor(
            sponsors = listOf(banner300x50, rewardVideo),
            pageKey = "home",
            rotationTick = 0,
            nowMs = refNowMs
        )
        assertEquals("sp_b300x50", pickedBannerHome?.id)

        // Banner restricted to HOME must not show on about_us
        val pickedBannerAbout = SponsorCampaignSelector.selectBannerSponsor(
            sponsors = listOf(banner300x50),
            pageKey = "about_us",
            rotationTick = 0,
            nowMs = refNowMs
        )
        assertNull(pickedBannerAbout)

        // Reward Video – Doctor Click selects active video sponsor cleanly
        val pickedReward = SponsorCampaignSelector.selectRewardVideoSponsor(
            sponsors = listOf(banner300x50, rewardVideo),
            rotationTick = 0,
            nowMs = refNowMs
        )
        assertEquals("sp_rv_doc", pickedReward?.id)
    }

    // 15. Video source & magic-byte validation (MP4 / WebM / HTTPS only)
    @Test
    fun videoValidationAcceptsMp4WebmAndHttpsAndRejectsUnsafeInputs() {
        assertTrue(SponsorCampaignSelector.isValidVideoSource("https://cdn.hospitalbd.com/ad.mp4"))
        assertTrue(SponsorCampaignSelector.isValidVideoSource("data:video/mp4;base64,AAAAIGZ0eXA="))
        assertTrue(SponsorCampaignSelector.isValidVideoSource("data:video/webm;base64,GkXfo59C"))
        assertFalse(SponsorCampaignSelector.isValidVideoSource("javascript:alert(1)"))
        assertFalse(SponsorCampaignSelector.isValidVideoSource("http://insecure.example.com/ad.mp4"))
        assertFalse(SponsorCampaignSelector.isValidVideoSource(""))

        // Valid MP4 ftyp header bytes
        val validMp4 = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            0x66, 0x74, 0x79, 0x70, // 'ftyp'
            0x69, 0x73, 0x6F, 0x6D,
            0x00, 0x00, 0x02, 0x00
        )
        // Valid WebM EBML header bytes
        val validWebm = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(),
            0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x1F,
            0x42, 0x86.toByte(), 0x81.toByte(), 0x01
        )
        assertTrue(SponsorCampaignSelector.isSupportedVideoBytes(validMp4))
        assertTrue(SponsorCampaignSelector.isSupportedVideoBytes(validWebm))
        assertFalse(SponsorCampaignSelector.isSupportedVideoBytes("<script>alert(1)</script>".toByteArray()))
    }

    // 16. Room v10 persists Admin Packages (new fields), Sponsors (contact/video/placement) & Rocket Payment Settings
    @Test
    fun roomV10PersistsPackagesWithNewFieldsSponsorsAndRocketPaymentSettings() = runBlocking {
        val demoPackages = listOf(
            SponsorPackageEntity(
                id = "pkg_banner_all",
                name = "Banner All Screen",
                adType = "BANNER",
                sizeLabel = "300×50 px",
                adPlacement = "ALL",
                packageImageUrl = "https://cdn.example.com/pkg1.png",
                widthPx = 300,
                heightPx = 50,
                durationDays = 30,
                priceBdt = 200,
                placementType = SponsorCampaignSelector.PLACEMENT_BANNER_300x50,
                description = "All-screen fixed banner",
                status = "ACTIVE"
            ),
            SponsorPackageEntity(
                id = "pkg_reward_video",
                name = "Reward Video – Doctor Click",
                adType = "REWARD_VIDEO",
                sizeLabel = "Video",
                adPlacement = "DOCTOR_DETAILS",
                packageImageUrl = "",
                widthPx = 1280,
                heightPx = 720,
                durationDays = 30,
                priceBdt = 600,
                placementType = SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO,
                description = "Reward video on doctor click",
                status = "ACTIVE"
            )
        )
        db.sponsorPackageDao().insertAll(demoPackages)

        val storedPackages = db.sponsorPackageDao().getActiveOnce()
        assertEquals(2, storedPackages.size)
        val bannerPkg = storedPackages.first { it.id == "pkg_banner_all" }
        assertEquals("300×50 px", bannerPkg.sizeLabel)
        assertEquals("ALL", bannerPkg.adPlacement)
        assertEquals(200, SponsorCampaignSelector.calculateFixedPackageTotal(bannerPkg.priceBdt, bannerPkg.durationDays))

        db.paymentSettingsDao().save(
            PaymentSettingsEntity(
                bkashNumber = "01711111111",
                bkashAccountType = "Personal",
                nagadNumber = "01822222222",
                nagadAccountType = "Merchant",
                rocketNumber = "01933333333",
                rocketAccountType = "Personal",
                instructionsEn = "Send fixed package rate via bKash, Nagad, or Rocket"
            )
        )
        val payCfg = db.paymentSettingsDao().getOnce()
        assertNotNull(payCfg)
        assertEquals("01933333333", payCfg?.rocketNumber)
        assertEquals("Personal", payCfg?.rocketAccountType)

        val videoSponsor = makeSponsor(
            id = "sp_v10",
            placementType = SponsorCampaignSelector.PLACEMENT_REWARD_VIDEO
        ).copy(
            contactPersonName = "Dr. Karim",
            mediaType = "VIDEO",
            videoUrl = "https://cdn.example.com/promo.mp4",
            adType = "REWARD_VIDEO",
            adPlacement = "DOCTOR_DETAILS"
        )
        db.sponsorDao().insert(videoSponsor)
        val loadedSponsor = db.sponsorDao().getById("sp_v10")
        assertNotNull(loadedSponsor)
        assertEquals("Dr. Karim", loadedSponsor?.contactPersonName)
        assertEquals("VIDEO", loadedSponsor?.mediaType)
        assertEquals("https://cdn.example.com/promo.mp4", loadedSponsor?.videoUrl)
        assertEquals("DOCTOR_DETAILS", loadedSponsor?.adPlacement)
    }

    // 17. 3-Network Priority Order (Unity -> Start.io -> Wortise), Custom Reordering & Individual Network Switches
    @Test
    fun multiNetworkPriorityOrderAndIndividualSwitchesWorkCorrectly() {
        val allThree = AdConfigEntity(
            adsEnabled = true,
            activeNetwork = "BOTH_UNITY_PRIMARY",
            networkPriorityOrder = "UNITY,STARTIO,WORTISE",
            unityEnabled = true,
            startIoEnabled = true,
            wortiseEnabled = true,
            unityGameId = "5789123",
            startIoAppId = "210630916",
            wortiseAppId = "w-app-123",
            wortiseBannerAdUnitId = "w-banner-1",
            wortiseNativeAdUnitId = "w-native-1",
            wortiseInterstitialAdUnitId = "w-inter-1",
            wortiseRewardedAdUnitId = "w-rew-1",
            wortiseAppOpenAdUnitId = "w-open-1"
        )

        // Default priority: Unity -> Start.io -> Wortise
        assertEquals(
            listOf("UNITY", "STARTIO", "WORTISE"),
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree, OnlineAdFormat.BANNER)
        )

        // Admin reorders priority remotely without app update: Wortise -> Unity -> Start.io
        val wortiseFirst = allThree.copy(networkPriorityOrder = "WORTISE,UNITY,STARTIO")
        assertEquals(
            listOf("WORTISE", "UNITY", "STARTIO"),
            HybridNetworkAdController.getOrderedNetworksForOnline(wortiseFirst, OnlineAdFormat.BANNER)
        )

        // Disabling individual networks removes only that network while keeping fallback intact
        assertEquals(
            listOf("STARTIO", "WORTISE"),
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree.copy(unityEnabled = false), OnlineAdFormat.BANNER)
        )
        assertEquals(
            listOf("UNITY", "WORTISE"),
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree.copy(startIoEnabled = false), OnlineAdFormat.BANNER)
        )
        assertEquals(
            listOf("UNITY", "STARTIO"),
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree.copy(wortiseEnabled = false), OnlineAdFormat.BANNER)
        )

        // Legacy activeNetwork single-network filters remain backward compatible
        assertEquals(
            listOf("WORTISE"),
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree.copy(activeNetwork = "WORTISE"), OnlineAdFormat.REWARDED)
        )
        assertEquals(
            listOf("UNITY"),
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree.copy(activeNetwork = "UNITY"), OnlineAdFormat.REWARDED)
        )
        assertEquals(
            listOf("STARTIO"),
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree.copy(activeNetwork = "STARTIO"), OnlineAdFormat.REWARDED)
        )
        assertTrue(
            HybridNetworkAdController.getOrderedNetworksForOnline(allThree.copy(activeNetwork = "SPONSORS_ONLY"), OnlineAdFormat.BANNER).isEmpty()
        )
    }

    // 18. Format-Specific Wortise Eligibility & Smart Screen Placement Override Resolution
    @Test
    fun formatEligibilityAndSmartScreenPlacementsResolveCorrectly() {
        val partialWortise = AdConfigEntity(
            adsEnabled = true,
            networkPriorityOrder = "UNITY,STARTIO,WORTISE",
            unityGameId = "5789123",
            startIoAppId = "210630916",
            wortiseAppId = "w-app-123",
            wortiseBannerAdUnitId = "", // Banner unit intentionally empty
            wortiseRewardedAdUnitId = "w-rew-1",
            wortiseAppOpenAdUnitId = "w-open-1"
        )

        // Wortise skipped for BANNER when Banner AdUnit ID is empty, but included for REWARDED and APP_OPEN
        assertFalse(WortiseAdsManager.hasFormatUnit(partialWortise, OnlineAdFormat.BANNER))
        assertTrue(WortiseAdsManager.hasFormatUnit(partialWortise, OnlineAdFormat.REWARDED))
        assertTrue(WortiseAdsManager.hasFormatUnit(partialWortise, OnlineAdFormat.APP_OPEN))
        assertEquals(
            listOf("UNITY", "STARTIO"),
            HybridNetworkAdController.getOrderedNetworksForOnline(partialWortise, OnlineAdFormat.BANNER)
        )
        assertEquals(
            listOf("UNITY", "STARTIO", "WORTISE"),
            HybridNetworkAdController.getOrderedNetworksForOnline(partialWortise, OnlineAdFormat.REWARDED)
        )

        // Preferred network override promotes WORTISE first while keeping UNITY & STARTIO as fallbacks
        assertEquals(
            listOf("WORTISE", "UNITY", "STARTIO"),
            HybridNetworkAdController.getOrderedNetworksForOnline(
                config = partialWortise,
                format = OnlineAdFormat.REWARDED,
                preferredNetworkOverride = "WORTISE"
            )
        )

        // Screen placement normalization across all 8 canonical screens
        assertEquals("HOME", HybridNetworkAdController.normalizeScreenKey("home"))
        assertEquals("DOCTOR_LIST", HybridNetworkAdController.normalizeScreenKey("find_doctors"))
        assertEquals("DOCTOR_DETAILS", HybridNetworkAdController.normalizeScreenKey("doctor_profile"))
        assertEquals("HOSPITAL_LIST", HybridNetworkAdController.normalizeScreenKey("facilities"))
        assertEquals("DIAGNOSTIC_CENTER", HybridNetworkAdController.normalizeScreenKey("diagnostic_center"))
        assertEquals("MEDICAL_SPECIALIST", HybridNetworkAdController.normalizeScreenKey("specialties"))
        assertEquals("CHAMBER_SCHEDULE", HybridNetworkAdController.normalizeScreenKey("chamber_schedule"))
        assertEquals("OTHER_SCREENS", HybridNetworkAdController.normalizeScreenKey("about_us"))

        val placements = listOf(
            ScreenAdPlacementEntity(placementKey = "HOME", screenName = "Home Screen", enabled = true, preferredNetwork = "WORTISE", adFormat = "BANNER", refreshIntervalSeconds = 45),
            ScreenAdPlacementEntity(placementKey = "DOCTOR_DETAILS", screenName = "Doctor Details", enabled = false, preferredNetwork = "UNITY", adFormat = "NATIVE"),
            ScreenAdPlacementEntity(placementKey = "OTHER_SCREENS", screenName = "Other Screens", enabled = true, preferredNetwork = "AUTO", adFormat = "BANNER")
        )
        val homePlacement = HybridNetworkAdController.resolveScreenPlacement("home", placements)
        assertNotNull(homePlacement)
        assertEquals("WORTISE", homePlacement?.preferredNetwork)
        assertEquals(45, homePlacement?.refreshIntervalSeconds)

        val detailsPlacement = HybridNetworkAdController.resolveScreenPlacement("doctor_profile", placements)
        assertNotNull(detailsPlacement)
        assertFalse(detailsPlacement!!.enabled)

        val fallbackOther = HybridNetworkAdController.resolveScreenPlacement("unknown_screen", placements)
        assertEquals("OTHER_SCREENS", fallbackOther?.placementKey)
    }

    // 19. Offline Mode Strictly Suppresses Unity Ads, Start.io, AND Wortise while keeping Direct Sponsor Ads
    @Test
    fun offlineModeSuppressesAllThreeOnlineNetworksWhileServingCachedSponsors() {
        val fullConfig = AdConfigEntity(
            adsEnabled = true,
            networkPriorityOrder = "UNITY,STARTIO,WORTISE",
            unityGameId = "5789123",
            startIoAppId = "210630916",
            wortiseAppId = "w-app-123",
            wortiseBannerAdUnitId = "w-banner-1",
            wortiseInterstitialAdUnitId = "w-inter-1",
            wortiseRewardedAdUnitId = "w-rew-1",
            wortiseAppOpenAdUnitId = "w-open-1"
        )
        ConnectivityMonitor.setOnlineForTesting(false)
        assertFalse(ConnectivityMonitor.isOnline.value)

        // Calling online sync/preload while offline must remain safe no-ops without crashing or initializing
        HybridNetworkAdController.syncOnlineSdks(context, fullConfig, isOnline = false)
        HybridNetworkAdController.preloadRewardedForOnline(context, fullConfig, isOnline = false)
        assertFalse(WortiseAdsManager.isRewardedReady.value)
        assertFalse(StartIoAdManager.isRewardedReady.value)
        assertFalse(UnityAdsManager.isRewardedLoaded.value)

        // Cached Direct Sponsor ads still select cleanly offline
        val cachedSponsor = makeSponsor(id = "sp_offline_ok", placementType = SponsorCampaignSelector.PLACEMENT_BANNER_300x50)
        val selected = SponsorCampaignSelector.selectBannerSponsor(listOf(cachedSponsor), "home", 0, refNowMs)
        assertEquals("sp_offline_ok", selected?.id)
    }

    // 20. Frequency Caps & Fullscreen Cooldown Prevent Repeated Rapid Advertisements
    @Test
    fun crossNetworkFrequencyCapsAndCooldownBlockRapidFullScreenAds() {
        val cappedConfig = AdConfigEntity(
            adsEnabled = true,
            appOpenEnabled = true,
            interstitialEnabled = true,
            appOpenCooldownSeconds = 120,
            maxInterstitialsPerSession = 2,
            maxRewardedPerSession = 3
        )
        val t0 = 1_000_000L
        assertTrue(HybridNetworkAdController.canShowFullScreenAd(cappedConfig, t0))
        HybridNetworkAdController.recordFullScreenShown(t0)

        // Within 120s cooldown -> blocked
        assertFalse(HybridNetworkAdController.canShowFullScreenAd(cappedConfig, t0 + 30_000L))
        assertFalse(HybridNetworkAdController.canShowFullScreenAd(cappedConfig, t0 + 119_000L))

        // After 120s cooldown -> allowed (2nd show)
        assertTrue(HybridNetworkAdController.canShowFullScreenAd(cappedConfig, t0 + 121_000L))
        HybridNetworkAdController.recordFullScreenShown(t0 + 121_000L)

        // Session cap reached (maxInterstitialsPerSession = 2) -> blocked even after cooldown
        assertFalse(HybridNetworkAdController.canShowFullScreenAd(cappedConfig, t0 + 500_000L))
    }

    // 21. Room v11 Persists Full Multi-Network AdConfig (including Wortise IDs & Caps) and All 8 Screen Placements
    @Test
    fun roomV11PersistsWortiseAdConfigAndAllEightScreenAdPlacements() = runBlocking {
        val savedCfg = AdConfigEntity(
            id = "global",
            adsEnabled = true,
            activeNetwork = "MULTI_CUSTOM",
            networkPriorityOrder = "WORTISE,UNITY,STARTIO",
            unityEnabled = true,
            startIoEnabled = true,
            wortiseEnabled = true,
            interstitialEnabled = true,
            adRequestTimeoutSeconds = 12,
            adRetryCooldownSeconds = 45,
            bannerRefreshSeconds = 75,
            maxInterstitialsPerSession = 3,
            maxRewardedPerSession = 8,
            unityGameId = "5789123",
            startIoAppId = "210630916",
            wortiseAppId = "wortise-prod-app",
            wortiseBannerAdUnitId = "wortise-prod-banner",
            wortiseNativeAdUnitId = "wortise-prod-native",
            wortiseInterstitialAdUnitId = "wortise-prod-inter",
            wortiseRewardedAdUnitId = "wortise-prod-rewarded",
            wortiseAppOpenAdUnitId = "wortise-prod-appopen"
        )
        db.adConfigDao().save(savedCfg)

        val loadedCfg = db.adConfigDao().getOnce()
        assertNotNull(loadedCfg)
        assertEquals("WORTISE,UNITY,STARTIO", loadedCfg?.networkPriorityOrder)
        assertEquals("wortise-prod-app", loadedCfg?.wortiseAppId)
        assertEquals("wortise-prod-banner", loadedCfg?.wortiseBannerAdUnitId)
        assertEquals("wortise-prod-native", loadedCfg?.wortiseNativeAdUnitId)
        assertEquals("wortise-prod-inter", loadedCfg?.wortiseInterstitialAdUnitId)
        assertEquals("wortise-prod-rewarded", loadedCfg?.wortiseRewardedAdUnitId)
        assertEquals("wortise-prod-appopen", loadedCfg?.wortiseAppOpenAdUnitId)
        assertEquals(12, loadedCfg?.adRequestTimeoutSeconds)
        assertEquals(45, loadedCfg?.adRetryCooldownSeconds)
        assertEquals(75, loadedCfg?.bannerRefreshSeconds)
        assertEquals(3, loadedCfg?.maxInterstitialsPerSession)
        assertEquals(8, loadedCfg?.maxRewardedPerSession)

        val eightScreens = listOf(
            ScreenAdPlacementEntity("HOME", "Home Screen", true, "UNITY", "BANNER", 10, 5, 60),
            ScreenAdPlacementEntity("DOCTOR_LIST", "Doctor List", true, "STARTIO", "NATIVE", 9, 5, 60),
            ScreenAdPlacementEntity("DOCTOR_DETAILS", "Doctor Details", true, "WORTISE", "REWARDED", 8, 4, 60),
            ScreenAdPlacementEntity("HOSPITAL_LIST", "Hospital List", true, "AUTO", "BANNER", 7, 5, 60),
            ScreenAdPlacementEntity("DIAGNOSTIC_CENTER", "Diagnostic Center", true, "AUTO", "BANNER", 6, 5, 60),
            ScreenAdPlacementEntity("MEDICAL_SPECIALIST", "Medical Specialist", true, "AUTO", "NATIVE", 5, 5, 60),
            ScreenAdPlacementEntity("CHAMBER_SCHEDULE", "Chamber Schedule", true, "AUTO", "BANNER", 4, 5, 60),
            ScreenAdPlacementEntity("OTHER_SCREENS", "Other Content Screens", false, "AUTO", "BANNER", 3, 3, 90)
        )
        db.screenAdPlacementDao().deleteAll()
        db.screenAdPlacementDao().insertAll(eightScreens)

        val storedScreens = db.screenAdPlacementDao().getAllOnce()
        assertEquals(8, storedScreens.size)
        assertEquals("HOME", storedScreens.first().placementKey)
        assertEquals("WORTISE", storedScreens.first { it.placementKey == "DOCTOR_DETAILS" }.preferredNetwork)
        assertFalse(storedScreens.first { it.placementKey == "OTHER_SCREENS" }.enabled)
    }

    // 22. Revenue Tracker Normalizes Unity, Start.io & Wortise Networks + All 5 Ad Formats (Rejecting AdMob)
    @Test
    fun revenueTrackerNormalizesThreeOfficialNetworksAndFiveFormats() {
        assertEquals("unity", AdRevenueTracker.normalizeNetworkKey("UNITY"))
        assertEquals("unity", AdRevenueTracker.normalizeNetworkKey("unity_ads"))
        assertEquals("startio", AdRevenueTracker.normalizeNetworkKey("Start.io"))
        assertEquals("wortise", AdRevenueTracker.normalizeNetworkKey("WORTISE"))
        assertEquals("wortise", AdRevenueTracker.normalizeNetworkKey("wortise_ads"))
        assertNull(AdRevenueTracker.normalizeNetworkKey("admob"))

        assertEquals("banner", AdRevenueTracker.normalizeFormatKey("BANNER"))
        assertEquals("native", AdRevenueTracker.normalizeFormatKey("NATIVE"))
        assertEquals("native", AdRevenueTracker.normalizeFormatKey("mrec"))
        assertEquals("interstitial", AdRevenueTracker.normalizeFormatKey("INTERSTITIAL"))
        assertEquals("app_open", AdRevenueTracker.normalizeFormatKey("APP_OPEN"))
        assertEquals("rewarded", AdRevenueTracker.normalizeFormatKey("REWARDED_VIDEO"))
    }

    // 23. Startup Regression Guard: MobileAdsInitProvider (INTEGRATION_MANAGER=wortise), Wortise InitializationProvider & MainActivity launch cleanly
    @Test
    fun startupContentProvidersAndMainActivityLaunchWithoutCrash() {
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "${context.packageName}.mobileadsinitprovider"
            packageName = context.packageName
        }
        val mobileAdsProvider = com.google.android.gms.ads.MobileAdsInitProvider()
        mobileAdsProvider.attachInfo(context, providerInfo)

        val wortiseProviderInfo = android.content.pm.ProviderInfo().apply {
            authority = "${context.packageName}.wortise-startup"
            packageName = context.packageName
        }
        val wortiseProvider = com.wortise.ads.InitializationProvider()
        wortiseProvider.attachInfo(context, wortiseProviderInfo)

        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    // 24. Release Build Strict Live Mode: Test Mode Impossible in Release, Demo IDs Blocked, Test Impressions Excluded from Revenue
    @Test
    fun releaseBuildStrictlyDisablesTestModeBlocksDemoIdsAndExcludesTestRevenue() {
        val remoteConfigWithAccidentalTestMode = AdConfigEntity(
            adsEnabled = true,
            testMode = true,
            unityGameId = "5789123",
            startIoAppId = "210630916",
            wortiseAppId = "3f844795-ebd1-4a52-b934-587b7d56571d"
        )

        // In Release builds (isDebugBuild = false), test mode MUST ALWAYS be false regardless of remote config
        assertFalse(
            "Release builds must NEVER enable test mode even if remote config.testMode=true",
            HybridNetworkAdController.isTestModeActive(remoteConfigWithAccidentalTestMode, isDebugBuild = false)
        )
        // In Debug builds (isDebugBuild = true), test mode stays enabled to protect accounts
        assertTrue(
            HybridNetworkAdController.isTestModeActive(remoteConfigWithAccidentalTestMode, isDebugBuild = true)
        )

        // Release builds strictly reject blank, sample, placeholder, and known SDK demo credentials
        assertFalse(HybridNetworkAdController.isValidProductionCredential("", isDebugBuild = false))
        assertFalse(HybridNetworkAdController.isValidProductionCredential("demo", isDebugBuild = false))
        assertFalse(HybridNetworkAdController.isValidProductionCredential("test_banner_id", isDebugBuild = false))
        assertFalse(HybridNetworkAdController.isValidProductionCredential("00000000-0000-0000-0000-000000000000", isDebugBuild = false))
        assertFalse(HybridNetworkAdController.isValidProductionCredential("ca-app-pub-3940256099942544/6300978111", isDebugBuild = false))
        assertFalse(HybridNetworkAdController.isValidProductionCredential("14851", isDebugBuild = false))
        assertFalse(HybridNetworkAdController.isValidProductionCredential("123456", isDebugBuild = false))

        // Real production IDs pass cleanly in Release builds
        assertTrue(HybridNetworkAdController.isValidProductionCredential("210630916", isDebugBuild = false))
        assertTrue(HybridNetworkAdController.isValidProductionCredential("5789123", isDebugBuild = false))
        assertTrue(HybridNetworkAdController.isValidProductionCredential("3f844795-ebd1-4a52-b934-587b7d56571d", isDebugBuild = false))

        // Test impressions are strictly excluded when live production tracking is off
        AdRevenueTracker.setProductionTrackingMode(false)
        assertFalse(AdRevenueTracker.isLiveProductionTrackingEnabled())
        AdRevenueTracker.setProductionTrackingMode(true)
        assertTrue(AdRevenueTracker.isLiveProductionTrackingEnabled())
        // Restore debug-safe default
        AdRevenueTracker.setProductionTrackingMode(false)
    }
}
