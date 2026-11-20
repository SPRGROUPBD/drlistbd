package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SessionPreferences
import com.example.data.local.SponsorEntity
import com.example.ui.ads.ConnectivityMonitor
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
 * Comprehensive verification suite for the Complete Hybrid Advertisement Monetization System:
 * - Zero AdMob verification
 * - Campaign start/expiration scheduling (online & offline)
 * - Target page filtering & route normalization
 * - Direct Sponsor Banner priority & weighted rotation
 * - App Open Sponsor Popup (EVERY_LAUNCH, ONCE_DAILY, ON/OFF, selection, empty fallback)
 * - Native Sponsor Card insertion interval (4, 5, 6) & non-duplicate rotation
 * - Image URL, Website URL, and magic-byte/size validation
 * - Persistent Room + File offline cache survival across offline states
 * - Connectivity online/offline gating & strictly separated offline local event storage
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HybridAdSystemTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var cacheDir: File

    // Fixed reference date: 2026-06-15 12:00:00 Asia/Dhaka
    private val refNowMs: Long = SponsorCampaignSelector.parseCampaignBounds("2026-06-15", "2026-06-15").first + 12L * 3600L * 1000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cacheDir = File(context.filesDir, "test_sponsor_cache_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        cacheDir.deleteRecursively()
        ConnectivityMonitor.setOnlineForTesting(true)
    }

    private fun makeSponsor(
        id: String,
        name: String = "Sponsor $id",
        startDate: String = "2026-01-01",
        endDate: String = "2026-12-31",
        targetPages: String = "ALL",
        priority: Int = 10,
        frequency: Int = 1,
        isActive: Boolean = true,
        localBannerPath: String = "",
        bannerUrl: String = "https://cdn.example.com/$id.png"
    ): SponsorEntity {
        val (sTs, eTs) = SponsorCampaignSelector.parseCampaignBounds(startDate, endDate)
        return SponsorEntity(
            id = id,
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
            startTimestamp = sTs,
            endTimestamp = eTs,
            targetPages = targetPages,
            displayPriority = priority,
            displayFrequency = frequency,
            isActive = isActive
        )
    }

    // 1. Strictly zero AdMob SDK presence
    @Test
    fun verifyNoAdMobSdkOnClasspath() {
        val admobLoaded = runCatching {
            Class.forName("com.google.android.gms.ads.MobileAds")
        }.isSuccess
        assertFalse("Google AdMob SDK must be strictly removed from dependencies", admobLoaded)
    }

    // 2. Campaign scheduling: active vs future vs expired vs inactive
    @Test
    fun campaignScheduleHidesExpiredFutureAndInactiveSponsors() {
        val valid = makeSponsor("sp_valid", startDate = "2026-06-01", endDate = "2026-06-30")
        val expired = makeSponsor("sp_expired", startDate = "2026-01-01", endDate = "2026-06-14")
        val future = makeSponsor("sp_future", startDate = "2026-06-16", endDate = "2026-12-31")
        val disabled = makeSponsor("sp_disabled", isActive = false)

        assertTrue(SponsorCampaignSelector.isCampaignValid(valid, refNowMs))
        assertFalse(SponsorCampaignSelector.isCampaignExpired(valid, refNowMs))

        assertFalse(SponsorCampaignSelector.isCampaignValid(expired, refNowMs))
        assertTrue(SponsorCampaignSelector.isCampaignExpired(expired, refNowMs))

        assertFalse(SponsorCampaignSelector.isCampaignValid(future, refNowMs))
        assertFalse(SponsorCampaignSelector.isCampaignExpired(future, refNowMs))

        assertFalse(SponsorCampaignSelector.isCampaignValid(disabled, refNowMs))
    }

    // 3. Target pages matching & route normalization
    @Test
    fun targetPageMatchingWorksForAllAndSpecificRoutes() {
        val allPages = makeSponsor("sp_all", targetPages = "ALL")
        val homeOnly = makeSponsor("sp_home", targetPages = "home,find_doctors")
        val profileOnly = makeSponsor("sp_prof", targetPages = "doctor_profile")

        assertEquals(SponsorCampaignSelector.PAGE_DOCTOR_PROFILE, SponsorCampaignSelector.normalizeRouteToPageKey("doctor_profile/doc_123"))
        assertEquals(SponsorCampaignSelector.PAGE_INFO, SponsorCampaignSelector.normalizeRouteToPageKey("info/privacy"))
        assertEquals(SponsorCampaignSelector.PAGE_FIND_DOCTORS, SponsorCampaignSelector.normalizeRouteToPageKey("find_doctors"))

        assertTrue(SponsorCampaignSelector.matchesPage(allPages, "facilities"))
        assertTrue(SponsorCampaignSelector.matchesPage(homeOnly, "home"))
        assertTrue(SponsorCampaignSelector.matchesPage(homeOnly, "find_doctors"))
        assertFalse(SponsorCampaignSelector.matchesPage(homeOnly, "portal"))
        assertTrue(SponsorCampaignSelector.matchesPage(profileOnly, "doctor_profile/doc_99"))
        assertFalse(SponsorCampaignSelector.matchesPage(profileOnly, "home"))
    }

    // 4. Direct Sponsor Banner selection respects priority & frequency
    @Test
    fun bannerSelectionRespectsPriorityAndExcludesExpired() {
        val expiredHigh = makeSponsor("sp_exp", priority = 100, endDate = "2026-05-01")
        val low = makeSponsor("sp_low", priority = 5, frequency = 1)
        val high = makeSponsor("sp_high", priority = 50, frequency = 2)

        val picked0 = SponsorCampaignSelector.selectBannerSponsor(
            sponsors = listOf(expiredHigh, low, high),
            pageKey = "home",
            rotationTick = 0,
            nowMs = refNowMs
        )
        assertEquals("sp_high", picked0?.id)

        // Weighted pool has [high, high, low] -> tick 2 selects low
        val picked2 = SponsorCampaignSelector.selectBannerSponsor(
            sponsors = listOf(expiredHigh, low, high),
            pageKey = "home",
            rotationTick = 2,
            nowMs = refNowMs
        )
        assertEquals("sp_low", picked2?.id)
    }

    // 5. App Open Sponsor Popup: EVERY_LAUNCH, ONCE_DAILY, ON/OFF, selection & empty state
    @Test
    fun appOpenSponsorPopupFrequencyAndSelectionRules() {
        val spHigh = makeSponsor("sp_1", priority = 20)
        val spMed = makeSponsor("sp_2", priority = 10)
        val spExpired = makeSponsor("sp_exp", priority = 99, endDate = "2026-01-01")

        // Preferred sponsor selected if valid
        val preferred = SponsorCampaignSelector.selectAppOpenPopupSponsor(
            sponsors = listOf(spHigh, spMed, spExpired),
            preferredSponsorId = "sp_2",
            nowMs = refNowMs
        )
        assertEquals("sp_2", preferred?.id)

        // If preferred sponsor is expired, falls back to highest priority valid sponsor
        val fallback = SponsorCampaignSelector.selectAppOpenPopupSponsor(
            sponsors = listOf(spHigh, spMed, spExpired),
            preferredSponsorId = "sp_exp",
            nowMs = refNowMs
        )
        assertEquals("sp_1", fallback?.id)

        // If NO valid sponsor exists, popup must NOT display (returns null)
        val none = SponsorCampaignSelector.selectAppOpenPopupSponsor(
            sponsors = listOf(spExpired),
            preferredSponsorId = "",
            nowMs = refNowMs
        )
        assertNull(none)

        // Frequency rules: EVERY_LAUNCH vs ONCE_DAILY vs disabled
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
        assertTrue(
            SponsorCampaignSelector.shouldShowAppOpenPopup(
                popupEnabled = true,
                frequency = SponsorCampaignSelector.FREQ_ONCE_DAILY,
                lastShownDate = "2026-06-14",
                todayDate = "2026-06-15",
                alreadyShownThisLaunch = false
            )
        )
        assertFalse(
            SponsorCampaignSelector.shouldShowAppOpenPopup(
                popupEnabled = false,
                frequency = SponsorCampaignSelector.FREQ_EVERY_LAUNCH,
                lastShownDate = "",
                todayDate = "2026-06-15",
                alreadyShownThisLaunch = false
            )
        )
    }

    // 6. Native Sponsor Card interval (4, 5, 6) & non-duplicate rotation
    @Test
    fun nativeSponsorIntervalAndRotationPreventDuplicates() {
        // Default 5 cards: inserted after 0-based index 4, 9, 14
        assertFalse(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(3, 5))
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(4, 5))
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(9, 5))

        // Admin configured 4 cards: inserted after index 3, 7
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(3, 4))
        assertFalse(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(4, 4))
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(7, 4))

        // Admin configured 6 cards: inserted after index 5, 11
        assertFalse(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(4, 6))
        assertTrue(SponsorCampaignSelector.shouldInsertNativeSponsorAfter(5, 6))

        // Rotation across multiple active sponsors prevents consecutive duplicates
        val spA = makeSponsor("sp_a", priority = 30)
        val spB = makeSponsor("sp_b", priority = 20)
        val spC = makeSponsor("sp_c", priority = 10)
        val slot0 = SponsorCampaignSelector.selectNativeSponsorForSlot(listOf(spA, spB, spC), "home", 0, refNowMs)
        val slot1 = SponsorCampaignSelector.selectNativeSponsorForSlot(listOf(spA, spB, spC), "home", 1, refNowMs)
        val slot2 = SponsorCampaignSelector.selectNativeSponsorForSlot(listOf(spA, spB, spC), "home", 2, refNowMs)
        assertEquals("sp_a", slot0?.id)
        assertEquals("sp_b", slot1?.id)
        assertEquals("sp_c", slot2?.id)
        assertNotEquals(slot0?.id, slot1?.id)

        // Single valid sponsor avoids repeating duplicates in same short section
        val onlyFirst = SponsorCampaignSelector.selectNativeSponsorForSlot(listOf(spA), "home", 0, refNowMs)
        val noDuplicateSecond = SponsorCampaignSelector.selectNativeSponsorForSlot(listOf(spA), "home", 1, refNowMs)
        assertEquals("sp_a", onlyFirst?.id)
        assertNull(noDuplicateSecond)
    }

    // 7. Image & Website URL validation + magic byte safety enforcement
    @Test
    fun validatesImageFormatsSizesAndSecureUrls() {
        assertTrue(SponsorCampaignSelector.isValidImageSource("https://cdn.hospitalbd.com/banner.png"))
        assertTrue(SponsorCampaignSelector.isValidImageSource("data:image/png;base64,iVBORw0KGgo="))
        assertFalse(SponsorCampaignSelector.isValidImageSource("http://insecure.example.com/banner.png"))
        assertFalse(SponsorCampaignSelector.isValidImageSource("javascript:alert(1)"))

        assertTrue(SponsorCampaignSelector.isValidWebsiteUrl("https://hospitalbd.com"))
        assertFalse(SponsorCampaignSelector.isValidWebsiteUrl("file:///etc/passwd"))

        // Valid PNG magic header
        val validPng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        )
        assertTrue(SponsorCampaignSelector.isSupportedImageBytes(validPng))

        // Valid JPEG magic header
        val validJpeg = ByteArray(16).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xD8.toByte()
            this[2] = 0xFF.toByte()
        }
        assertTrue(SponsorCampaignSelector.isSupportedImageBytes(validJpeg))

        // Invalid text payload rejected
        val fakeScript = "<script>alert('xss')</script>".toByteArray()
        assertFalse(SponsorCampaignSelector.isSupportedImageBytes(fakeScript))

        // Oversized > 3MB payload rejected
        val oversized = ByteArray(SponsorCampaignSelector.MAX_IMAGE_BYTES + 100).apply {
            this[0] = 0x89.toByte(); this[1] = 0x50; this[2] = 0x4E; this[3] = 0x47
        }
        assertFalse(SponsorCampaignSelector.isSupportedImageBytes(oversized))
    }

    // 8. Persistent Room cache + local file survival in Offline Mode
    @Test
    fun offlineSponsorCacheSurvivesOfflineStateAndResolvesLocalFile() = runBlocking {
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
        assertTrue(savedFile!!.exists())

        val sponsor = makeSponsor(
            id = "sp_cached",
            name = "Dhaka Heart Lab",
            localBannerPath = savedFile.absolutePath
        )
        db.sponsorDao().insert(sponsor)

        // Simulate offline mode: Room still returns cached sponsor and resolves local File directly
        ConnectivityMonitor.setOnlineForTesting(false)
        assertFalse(ConnectivityMonitor.isOnline.value)

        val persisted = db.sponsorDao().getAllOnce()
        assertEquals(1, persisted.size)
        assertEquals("Dhaka Heart Lab", persisted.first().name)

        val offlineModel = SponsorOfflineCacheManager.resolveImageModel(
            localPath = persisted.first().localBannerPath,
            remoteUrl = persisted.first().bannerImageUrl,
            isOnline = false
        )
        assertTrue("Offline resolver must return cached File", offlineModel is File)
        assertEquals(savedFile.absolutePath, (offlineModel as File).absolutePath)

        // Uncached remote HTTPS image must NOT be requested while offline
        val uncachedOffline = SponsorOfflineCacheManager.resolveImageModel(
            localPath = "",
            remoteUrl = "https://cdn.example.com/uncached.png",
            isOnline = false
        )
        assertNull("Offline mode must not attempt remote HTTPS image fetch", uncachedOffline)
    }

    // 9. Strictly separated offline local event counters in SessionPreferences
    @Test
    fun offlineDisplaysAndSessionsAreRecordedLocallyAndConsumedOnReconnect() = runBlocking {
        val prefs = SessionPreferences(context)
        prefs.consumePendingOfflineStats() // clear any prior state

        prefs.recordLocalOfflineSponsorDisplay()
        prefs.recordLocalOfflineSponsorDisplay()
        prefs.recordLocalOfflineSession()

        val (displays, sessions) = prefs.consumePendingOfflineStats()
        assertEquals(2, displays)
        assertEquals(1, sessions)

        // After flush/consume, local queue resets to 0
        val (afterDisplays, afterSessions) = prefs.consumePendingOfflineStats()
        assertEquals(0, afterDisplays)
        assertEquals(0, afterSessions)

        // Popup daily date persists cleanly
        prefs.setLastAppOpenPopupDate("2026-06-15")
        assertEquals("2026-06-15", prefs.getLastAppOpenPopupDateOnce())
    }
}
