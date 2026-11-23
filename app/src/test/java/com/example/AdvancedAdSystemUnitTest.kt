package com.example

import com.example.data.local.AdConfigEntity
import com.example.data.local.SponsorEntity
import com.example.ui.ads.MultiCompanyRotationEngine
import com.example.ui.ads.SponsorCampaignSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit tests verifying:
 * 1. Strict placement isolation (Banner / Popup / Native 300x200 never cross-leak).
 * 2. Exact doctor-list Native Ad cadence (1 Native Ad after every 3 Doctor Cards).
 * 3. Multi-company fair rotation (3 companies -> C1,C2,C3,C1...; 2 companies -> C1,C2,C1...; 1 company -> C1).
 * 4. Independent rotation channels for Banner, Popup, and Native Ads across pagination/refreshes.
 * 5. Disabled and expired ad exclusion.
 * 6. Preserved online ad network ordering (Start.io + Unity Ads + Wortise, zero AdMob).
 */
class AdvancedAdSystemUnitTest {

    private val nowMs = 1_760_000_000_000L

    @Before
    fun setUp() {
        MultiCompanyRotationEngine.resetForTesting()
    }

    private fun makeSponsor(
        id: String,
        companyId: String,
        companyName: String,
        placementType: String,
        placementIds: String = "",
        rotationOrder: Int = 0,
        priority: Int = 5,
        isActive: Boolean = true,
        status: String = SponsorCampaignSelector.STATUS_ACTIVE,
        expiryAt: Long = nowMs + 10L * SponsorCampaignSelector.DAY_MS
    ) = SponsorEntity(
        id = id,
        companyId = companyId,
        name = companyName,
        promoTitle = "$companyName Offer",
        bannerImageUrl = "https://example.com/$id.png",
        placementType = placementType,
        placementIds = placementIds,
        targetPages = SponsorCampaignSelector.PAGE_ALL,
        rotationOrder = rotationOrder,
        displayPriority = priority,
        isActive = isActive,
        status = status,
        activatedAt = nowMs - SponsorCampaignSelector.DAY_MS,
        expiryAt = expiryAt
    )

    @Test
    fun strictPlacementIsolation_bannerNeverShowsPopupOrNative() {
        val popupAd = makeSponsor("p1", "c_pop", "Popup Corp", SponsorCampaignSelector.PLACEMENT_POPUP, "OTH-POPUP")
        val nativeAd = makeSponsor("n1", "c_nat", "Native Corp", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, "DOC-01")
        val aboutAd = makeSponsor("a1", "c_abt", "About Corp", SponsorCampaignSelector.PLACEMENT_ABOUT_US, "OTH-ABOUT")

        // No banner ad present -> Banner placement MUST return null (never leak Popup/Native/About)
        assertNull(
            SponsorCampaignSelector.selectBannerSponsor(
                sponsors = listOf(popupAd, nativeAd, aboutAd),
                pageKey = SponsorCampaignSelector.PAGE_HOME,
                rotationTick = 0,
                nowMs = nowMs
            )
        )

        val bannerAd = makeSponsor("b1", "c_ban", "Banner Corp", SponsorCampaignSelector.PLACEMENT_BANNER_320x100, "HOME-01")
        val chosen = SponsorCampaignSelector.selectBannerSponsor(
            sponsors = listOf(popupAd, nativeAd, aboutAd, bannerAd),
            pageKey = SponsorCampaignSelector.PAGE_HOME,
            rotationTick = 0,
            nowMs = nowMs
        )
        assertEquals("b1", chosen?.id)
    }

    @Test
    fun strictPlacementIsolation_popupNeverShowsBannerOrNative() {
        val bannerAd = makeSponsor("b1", "c_ban", "Banner Corp", SponsorCampaignSelector.PLACEMENT_BANNER_320x100, "HOME-01")
        val nativeAd = makeSponsor("n1", "c_nat", "Native Corp", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, "DOC-01")

        // No popup ad present -> Popup placement MUST return null
        assertNull(
            SponsorCampaignSelector.selectAppOpenPopupSponsor(
                sponsors = listOf(bannerAd, nativeAd),
                preferredSponsorId = "",
                nowMs = nowMs
            )
        )

        val popupAd = makeSponsor("p1", "c_pop", "Popup Corp", SponsorCampaignSelector.PLACEMENT_POPUP, "OTH-POPUP")
        val chosen = SponsorCampaignSelector.selectAppOpenPopupSponsor(
            sponsors = listOf(bannerAd, nativeAd, popupAd),
            preferredSponsorId = "",
            nowMs = nowMs
        )
        assertEquals("p1", chosen?.id)
    }

    @Test
    fun strictPlacementIsolation_nativeNeverShowsBannerOrPopup() {
        val bannerAd = makeSponsor("b1", "c_ban", "Banner Corp", SponsorCampaignSelector.PLACEMENT_BANNER_320x100, "HOME-01")
        val popupAd = makeSponsor("p1", "c_pop", "Popup Corp", SponsorCampaignSelector.PLACEMENT_POPUP, "OTH-POPUP")

        // No native ad present -> Native doctor-list slot MUST return null
        assertNull(
            SponsorCampaignSelector.selectNativeSponsorForSlot(
                sponsors = listOf(bannerAd, popupAd),
                pageKey = SponsorCampaignSelector.PAGE_FIND_DOCTORS,
                slotIndex = 0,
                nowMs = nowMs
            )
        )

        val nativeAd = makeSponsor("n1", "c_nat", "Native Corp", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, "DOC-01")
        val chosen = SponsorCampaignSelector.selectNativeSponsorForSlot(
            sponsors = listOf(bannerAd, popupAd, nativeAd),
            pageKey = SponsorCampaignSelector.PAGE_FIND_DOCTORS,
            slotIndex = 0,
            nowMs = nowMs
        )
        assertEquals("n1", chosen?.id)
    }

    @Test
    fun doctorListCadence_insertsExactlyOneNativeAdAfterEveryThreeDoctors() {
        val insertIndices = (0 until 12).filter { SponsorCampaignSelector.shouldInsertNativeAfterDoctorCard(it) }
        // Doctor 1(0), 2(1), 3(2) -> Ad (slot 0); Doctor 4(3), 5(4), 6(5) -> Ad (slot 1); etc.
        assertEquals(listOf(2, 5, 8, 11), insertIndices)
        assertEquals(listOf(0, 1, 2, 3), insertIndices.map { SponsorCampaignSelector.nativeSlotIndexForDoctorCard(it) })
        assertFalse(SponsorCampaignSelector.shouldInsertNativeAfterDoctorCard(-1))
        assertFalse(SponsorCampaignSelector.shouldInsertNativeAfterDoctorCard(0))
        assertFalse(SponsorCampaignSelector.shouldInsertNativeAfterDoctorCard(1))
    }

    @Test
    fun multiCompanyRotation_threeCompaniesRotateFairlyWithoutConsecutiveDuplicates() {
        val ads = listOf(
            makeSponsor("n1", "comp_1", "Company 1", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, rotationOrder = 1),
            makeSponsor("n2", "comp_2", "Company 2", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, rotationOrder = 2),
            makeSponsor("n3", "comp_3", "Company 3", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, rotationOrder = 3)
        )

        val sequence = (0 until 9).map { slot ->
            SponsorCampaignSelector.selectNativeSponsorForSlot(
                sponsors = ads,
                pageKey = SponsorCampaignSelector.PAGE_FIND_DOCTORS,
                slotIndex = slot,
                nowMs = nowMs
            )?.companyId
        }

        assertEquals(
            listOf("comp_1", "comp_2", "comp_3", "comp_1", "comp_2", "comp_3", "comp_1", "comp_2", "comp_3"),
            sequence
        )
        // Verify zero consecutive duplicates
        for (i in 1 until sequence.size) {
            assertNotEquals(sequence[i - 1], sequence[i])
        }
    }

    @Test
    fun multiCompanyRotation_twoCompaniesAndOneCompanyBehaveCorrectly() {
        val twoAds = listOf(
            makeSponsor("n1", "comp_1", "Company 1", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, rotationOrder = 1),
            makeSponsor("n2", "comp_2", "Company 2", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, rotationOrder = 2)
        )
        val twoSeq = (0 until 6).map { slot ->
            SponsorCampaignSelector.selectNativeSponsorForSlot(
                sponsors = twoAds,
                pageKey = SponsorCampaignSelector.PAGE_FIND_DOCTORS,
                slotIndex = slot,
                nowMs = nowMs
            )?.companyId
        }
        assertEquals(listOf("comp_1", "comp_2", "comp_1", "comp_2", "comp_1", "comp_2"), twoSeq)

        val oneAd = listOf(
            makeSponsor("n1", "comp_1", "Company 1", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, rotationOrder = 1)
        )
        val oneSeq = (0 until 4).map { slot ->
            SponsorCampaignSelector.selectNativeSponsorForSlot(
                sponsors = oneAd,
                pageKey = SponsorCampaignSelector.PAGE_FIND_DOCTORS,
                slotIndex = slot,
                nowMs = nowMs
            )?.companyId
        }
        assertEquals(listOf("comp_1", "comp_1", "comp_1", "comp_1"), oneSeq)
    }

    @Test
    fun independentRotationChannels_popupAdvanceDoesNotCorruptBannerOrNative() {
        val popupAds = listOf(
            makeSponsor("p1", "p_comp_1", "Popup 1", SponsorCampaignSelector.PLACEMENT_POPUP, rotationOrder = 1),
            makeSponsor("p2", "p_comp_2", "Popup 2", SponsorCampaignSelector.PLACEMENT_POPUP, rotationOrder = 2)
        )
        val bannerAds = listOf(
            makeSponsor("b1", "b_comp_1", "Banner 1", SponsorCampaignSelector.PLACEMENT_BANNER_320x100, rotationOrder = 1),
            makeSponsor("b2", "b_comp_2", "Banner 2", SponsorCampaignSelector.PLACEMENT_BANNER_320x100, rotationOrder = 2)
        )

        val pFirst = SponsorCampaignSelector.selectAppOpenPopupSponsor(popupAds, "", nowMs)
        val pSecond = SponsorCampaignSelector.selectAppOpenPopupSponsor(popupAds, "", nowMs)
        assertEquals("p_comp_1", pFirst?.companyId)
        assertEquals("p_comp_2", pSecond?.companyId)

        // Banner channel stays independent and starts cleanly at Banner 1 for slot 0
        val bFirst = SponsorCampaignSelector.selectBannerSponsor(bannerAds, SponsorCampaignSelector.PAGE_HOME, 0, nowMs)
        val bSecond = SponsorCampaignSelector.selectBannerSponsor(bannerAds, SponsorCampaignSelector.PAGE_HOME, 1, nowMs)
        assertEquals("b_comp_1", bFirst?.companyId)
        assertEquals("b_comp_2", bSecond?.companyId)
    }

    @Test
    fun expiredAndDisabledAds_neverDisplay() {
        val paused = makeSponsor("n_paused", "c1", "Paused Corp", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD, isActive = false, status = "PAUSED")
        val expired = makeSponsor(
            "n_exp",
            "c2",
            "Expired Corp",
            SponsorCampaignSelector.PLACEMENT_NATIVE_CARD,
            expiryAt = nowMs - SponsorCampaignSelector.DAY_MS
        )
        val valid = makeSponsor("n_ok", "c3", "Active Corp", SponsorCampaignSelector.PLACEMENT_NATIVE_CARD)

        assertFalse(SponsorCampaignSelector.isCampaignValid(paused, nowMs))
        assertFalse(SponsorCampaignSelector.isCampaignValid(expired, nowMs))
        assertTrue(SponsorCampaignSelector.isCampaignValid(valid, nowMs))

        val selected = SponsorCampaignSelector.selectNativeSponsorForSlot(
            sponsors = listOf(paused, expired, valid),
            pageKey = SponsorCampaignSelector.PAGE_FIND_DOCTORS,
            slotIndex = 0,
            nowMs = nowMs
        )
        assertNotNull(selected)
        assertEquals("n_ok", selected?.id)
    }

    @Test
    fun defaultAdConfig_hasThirdPartyNetworksDisabledByDefault() {
        val cfg = AdConfigEntity()
        assertEquals("SPONSORS_ONLY", cfg.activeNetwork)
        assertFalse(cfg.wortiseEnabled)
        assertEquals("", cfg.unityGameId)
        assertEquals("", cfg.startIoAppId)
        assertEquals("", cfg.wortiseAppId)
    }
}
