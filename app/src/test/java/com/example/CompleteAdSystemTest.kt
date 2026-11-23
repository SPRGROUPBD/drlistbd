package com.example

import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.DoctorEntity
import com.example.data.local.HospitalEntity
import com.example.data.local.SpecialtyEntity
import com.example.data.model.DoctorFullProfile
import com.example.ui.ads.AdCampaignItem
import com.example.ui.ads.AdPackageItem
import com.example.ui.ads.AdPackageTypes
import com.example.ui.ads.AdPlacements
import com.example.ui.ads.AdStatuses
import com.example.ui.ads.AdminCustomSliderItem
import com.example.ui.ads.AdvertiserHomeSliderItem
import com.example.ui.ads.AdvertisingRotationEngine
import com.example.ui.ads.AdvertisingValidators
import com.example.ui.ads.HomeSliderSettings
import com.example.ui.ads.PlacementGlobalSettings
import com.example.ui.ads.SliderRecordTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompleteAdSystemTest {

    private val nowMs = 1_750_000_000_000L
    private val futureMs = nowMs + 7L * 24L * 60L * 60L * 1000L
    private val pastMs = nowMs - 1000L

    private val sampleDoctor = DoctorFullProfile(
        doctor = DoctorEntity(
            id = "doc_1",
            fullNameBn = "ডাঃ রহমান",
            fullNameEn = "Dr. Rahman",
            photoUrl = "https://example.com/doc1.jpg",
            qualification = "MBBS, FCPS",
            mainSpecialtyId = "spec_1",
            currentHospital = "Dhaka Medical College",
            isActive = true
        ),
        specialty = SpecialtyEntity(id = "spec_1", nameBn = "মেডিসিন", nameEn = "Medicine"),
        educations = emptyList(),
        employmentHistory = emptyList(),
        chambers = emptyList()
    )

    private val sampleHospital = HospitalEntity(
        id = "hosp_1",
        nameBn = "স্কয়ার হাসপাতাল",
        nameEn = "Square Hospital",
        district = "Dhaka",
        address = "Panthapath, Dhaka",
        contactNumber = "01700000001",
        imageUrl = "https://example.com/hosp1.jpg"
    )

    private val sampleDiagnostic = DiagnosticCenterEntity(
        id = "diag_1",
        nameBn = "পপুলার ডায়াগনস্টিক",
        nameEn = "Popular Diagnostic",
        district = "Dhaka",
        address = "Dhanmondi, Dhaka",
        contactNumber = "01700000002",
        imageUrl = "https://example.com/diag1.jpg"
    )

    @Before
    fun setUp() {
        AdvertisingRotationEngine.resetForTesting()
    }

    @Test
    fun `package eligibility and supported types work strictly`() {
        val activePkg = AdPackageItem(id = "p1", name = "Slider", status = AdStatuses.ACTIVE, adType = AdPackageTypes.HOME_SLIDER)
        val inactivePkg = AdPackageItem(id = "p2", name = "Native", status = AdStatuses.INACTIVE, adType = AdPackageTypes.NATIVE_300x100)
        val pausedPkg = AdPackageItem(id = "p3", name = "Banner", status = AdStatuses.PAUSED, adType = AdPackageTypes.BOTTOM_BANNER_300x50)

        assertTrue(activePkg.isActive)
        assertFalse(inactivePkg.isActive)
        assertFalse(pausedPkg.isActive)

        assertTrue(AdPackageTypes.isSupported(AdPackageTypes.HOME_SLIDER))
        assertTrue(AdPackageTypes.isSupported(AdPackageTypes.NATIVE_300x100))
        assertTrue(AdPackageTypes.isSupported(AdPackageTypes.BOTTOM_BANNER_300x50))
        assertTrue(AdPackageTypes.isSupported(AdPackageTypes.APP_OPEN_POPUP_300x200))
        assertFalse(AdPackageTypes.isSupported("FULLSCREEN_INTERSTITIAL"))
    }

    @Test
    fun `trx id normalization strips spaces and enforces length bounds`() {
        assertEquals("TRX-9A8B7C", AdvertisingValidators.normalizeTrxId("  trx- 9a8b 7c  "))
        assertTrue(AdvertisingValidators.isValidTrxId("bk12345678"))
        assertFalse(AdvertisingValidators.isValidTrxId("ab"))
        assertFalse(AdvertisingValidators.isValidTrxId("A".repeat(41)))
    }

    @Test
    fun `separated payment verification and campaign approval gate eligibility`() {
        val base = AdCampaignItem(
            id = "c1",
            advertiserId = "adv_1",
            imageUrl = "https://example.com/ad.jpg",
            adType = AdPackageTypes.NATIVE_300x100,
            placement = AdPlacements.DOCTOR_LIST,
            paymentStatus = AdStatuses.PENDING,
            approvalStatus = AdStatuses.PENDING,
            campaignStatus = AdStatuses.PENDING,
            startDate = nowMs - 10_000L,
            expiryDate = futureMs
        )

        // Payment verified alone is not enough
        val paidOnly = base.copy(paymentStatus = AdStatuses.VERIFIED)
        assertEquals(AdStatuses.PENDING, AdvertisingValidators.effectiveCampaignStatus(paidOnly, nowMs))
        assertFalse(AdvertisingValidators.isCampaignEligible(paidOnly, nowMs))

        // Approval alone without payment verification is not enough
        val approvedOnly = base.copy(approvalStatus = AdStatuses.APPROVED, campaignStatus = AdStatuses.ACTIVE)
        assertEquals(AdStatuses.PENDING, AdvertisingValidators.effectiveCampaignStatus(approvedOnly, nowMs))
        assertFalse(AdvertisingValidators.isCampaignEligible(approvedOnly, nowMs))

        // Verified + Approved + Active within window IS eligible
        val live = base.copy(
            paymentStatus = AdStatuses.VERIFIED,
            approvalStatus = AdStatuses.APPROVED,
            campaignStatus = AdStatuses.ACTIVE
        )
        assertEquals(AdStatuses.ACTIVE, AdvertisingValidators.effectiveCampaignStatus(live, nowMs))
        assertTrue(AdvertisingValidators.isCampaignEligible(live, nowMs))
    }

    @Test
    fun `expired campaign stops automatically and never auto reactivates`() {
        val expiredActive = AdCampaignItem(
            id = "c_exp",
            advertiserId = "adv_1",
            imageUrl = "https://example.com/ad.jpg",
            adType = AdPackageTypes.BOTTOM_BANNER_300x50,
            placement = AdPlacements.BOTTOM_ALL_SCREENS,
            paymentStatus = AdStatuses.VERIFIED,
            approvalStatus = AdStatuses.APPROVED,
            campaignStatus = AdStatuses.ACTIVE,
            startDate = pastMs - 86_400_000L,
            expiryDate = pastMs
        )
        assertEquals(AdStatuses.EXPIRED, AdvertisingValidators.effectiveCampaignStatus(expiredActive, nowMs))
        assertFalse(AdvertisingValidators.isCampaignEligible(expiredActive, nowMs))

        val expiredPaused = expiredActive.copy(campaignStatus = AdStatuses.PAUSED)
        assertEquals(AdStatuses.EXPIRED, AdvertisingValidators.effectiveCampaignStatus(expiredPaused, nowMs))
        assertFalse(AdvertisingValidators.isCampaignEligible(expiredPaused, nowMs))
    }

    @Test
    fun `home slider resolves only existing directory records and applies before after toggle and rotation`() {
        val validDocTarget = AdvertisingValidators.validateAndResolveDirectoryRecord(
            recordType = SliderRecordTypes.DOCTOR,
            recordId = "doc_1",
            doctors = listOf(sampleDoctor),
            hospitals = listOf(sampleHospital),
            diagnostics = listOf(sampleDiagnostic)
        )
        assertNotNull(validDocTarget)
        assertEquals("Dr. Rahman", validDocTarget?.title)

        val missingTarget = AdvertisingValidators.validateAndResolveDirectoryRecord(
            recordType = SliderRecordTypes.DOCTOR,
            recordId = "non_existent_doc",
            doctors = listOf(sampleDoctor),
            hospitals = listOf(sampleHospital),
            diagnostics = listOf(sampleDiagnostic)
        )
        assertNull(missingTarget)

        val advDoc = AdCampaignItem(
            id = "s_adv1",
            advertiserId = "adv_1",
            adType = AdPackageTypes.HOME_SLIDER,
            placement = AdPlacements.HOME_TOP_SLIDER,
            targetRecordType = SliderRecordTypes.DOCTOR,
            targetRecordId = "doc_1",
            paymentStatus = AdStatuses.VERIFIED,
            approvalStatus = AdStatuses.APPROVED,
            campaignStatus = AdStatuses.ACTIVE,
            startDate = nowMs - 1000L,
            expiryDate = futureMs,
            createdAt = 100L
        )
        val advHosp = advDoc.copy(
            id = "s_adv2",
            advertiserId = "adv_2",
            targetRecordType = SliderRecordTypes.HOSPITAL,
            targetRecordId = "hosp_1",
            createdAt = 200L
        )
        val advInvalid = advDoc.copy(
            id = "s_invalid",
            targetRecordType = SliderRecordTypes.DIAGNOSTIC,
            targetRecordId = "fake_diag_id",
            createdAt = 300L
        )

        val custom1 = AdminCustomSliderItem(
            id = "cs_2",
            title = "Admin Banner 2",
            imageUrl = "https://example.com/cs2.jpg",
            order = 2,
            status = AdStatuses.ACTIVE
        )
        val custom2 = AdminCustomSliderItem(
            id = "cs_1",
            title = "Admin Banner 1",
            imageUrl = "https://example.com/cs1.jpg",
            order = 1,
            status = AdStatuses.ACTIVE
        )
        val customPaused = AdminCustomSliderItem(
            id = "cs_paused",
            title = "Paused Banner",
            imageUrl = "https://example.com/csp.jpg",
            order = 0,
            status = AdStatuses.PAUSED
        )

        val slidesBefore = AdvertisingRotationEngine.buildHomeSliderSlides(
            campaigns = listOf(advDoc, advHosp, advInvalid),
            advertiserSliderIndex = listOf(
                AdvertiserHomeSliderItem(campaignId = "s_adv1", advertiserId = "adv_1", targetRecordType = "doctor", targetRecordId = "doc_1", priority = 1),
                AdvertiserHomeSliderItem(campaignId = "s_adv2", advertiserId = "adv_2", targetRecordType = "hospital", targetRecordId = "hosp_1", priority = 2)
            ),
            customSliders = listOf(custom1, custom2, customPaused),
            sliderSettings = HomeSliderSettings(enabled = true, customSliderPosition = "BEFORE"),
            placementSettings = PlacementGlobalSettings(),
            doctors = listOf(sampleDoctor),
            hospitals = listOf(sampleHospital),
            diagnostics = listOf(sampleDiagnostic),
            baseOffset = 0L,
            nowMs = nowMs
        )
        // Expected: cs_1, cs_2, adv_slide_s_adv1, adv_slide_s_adv2 (invalid & paused excluded)
        assertEquals(listOf("custom_slide_cs_1", "custom_slide_cs_2", "adv_slide_s_adv1", "adv_slide_s_adv2"), slidesBefore.map { it.slideId })

        val slidesAfterRotated = AdvertisingRotationEngine.buildHomeSliderSlides(
            campaigns = listOf(advDoc, advHosp, advInvalid),
            advertiserSliderIndex = emptyList(),
            customSliders = listOf(custom1, custom2),
            sliderSettings = HomeSliderSettings(enabled = true, customSliderPosition = "AFTER"),
            placementSettings = PlacementGlobalSettings(),
            doctors = listOf(sampleDoctor),
            hospitals = listOf(sampleHospital),
            diagnostics = listOf(sampleDiagnostic),
            baseOffset = 1L,
            nowMs = nowMs
        )
        // Advertiser slides rotated by 1, then custom slides AFTER
        assertEquals(listOf("adv_slide_s_adv2", "adv_slide_s_adv1", "custom_slide_cs_1", "custom_slide_cs_2"), slidesAfterRotated.map { it.slideId })
    }

    @Test
    fun `native 300x100 inserts after every 3 cards across 4 independent queues`() {
        val insertedIndices = (0..11).filter { AdvertisingRotationEngine.shouldInsertNativeAfterCard(it) }
        assertEquals(listOf(2, 5, 8, 11), insertedIndices)
        assertEquals(0, AdvertisingRotationEngine.nativeSlotForCardIndex(2))
        assertEquals(1, AdvertisingRotationEngine.nativeSlotForCardIndex(5))
        assertEquals(2, AdvertisingRotationEngine.nativeSlotForCardIndex(8))

        val docAd1 = AdCampaignItem(
            id = "doc_ad_1", advertiserId = "a1", imageUrl = "https://example.com/d1.jpg",
            adType = AdPackageTypes.NATIVE_300x100, placement = AdPlacements.DOCTOR_LIST,
            paymentStatus = AdStatuses.VERIFIED, approvalStatus = AdStatuses.APPROVED, campaignStatus = AdStatuses.ACTIVE,
            startDate = nowMs - 1000L, expiryDate = futureMs, createdAt = 10L
        )
        val docAd2 = docAd1.copy(id = "doc_ad_2", advertiserId = "a2", createdAt = 20L)
        val hospAd1 = docAd1.copy(id = "hosp_ad_1", placement = AdPlacements.HOSPITAL_LIST)
        val diagAd1 = docAd1.copy(id = "diag_ad_1", placement = AdPlacements.DIAGNOSTIC_LIST)
        val favAd1 = docAd1.copy(id = "fav_ad_1", placement = AdPlacements.FAVORITE_LIST)

        val all = listOf(docAd1, docAd2, hospAd1, diagAd1, favAd1)

        // Independent queues
        assertEquals("doc_ad_1", AdvertisingRotationEngine.selectNativeAdForSlot(all, AdPlacements.DOCTOR_LIST, 0, 0L, nowMs)?.id)
        assertEquals("doc_ad_2", AdvertisingRotationEngine.selectNativeAdForSlot(all, AdPlacements.DOCTOR_LIST, 1, 0L, nowMs)?.id)
        assertEquals("hosp_ad_1", AdvertisingRotationEngine.selectNativeAdForSlot(all, AdPlacements.HOSPITAL_LIST, 0, 0L, nowMs)?.id)
        assertEquals("diag_ad_1", AdvertisingRotationEngine.selectNativeAdForSlot(all, AdPlacements.DIAGNOSTIC_LIST, 0, 0L, nowMs)?.id)
        assertEquals("fav_ad_1", AdvertisingRotationEngine.selectNativeAdForSlot(all, AdPlacements.FAVORITE_LIST, 0, 0L, nowMs)?.id)

        // Placement disable flag respects isolation
        val disabledHosp = PlacementGlobalSettings(hospitalNativeEnabled = false)
        assertNull(AdvertisingRotationEngine.selectNativeAdForSlot(all, AdPlacements.HOSPITAL_LIST, 0, 0L, nowMs, disabledHosp))
        assertNotNull(AdvertisingRotationEngine.selectNativeAdForSlot(all, AdPlacements.DOCTOR_LIST, 0, 0L, nowMs, disabledHosp))
    }

    @Test
    fun `bottom banner prioritizes earliest activated and switches automatically on pause or expiry`() {
        val earliest = AdCampaignItem(
            id = "ban_1", advertiserId = "a1", imageUrl = "https://example.com/b1.jpg",
            adType = AdPackageTypes.BOTTOM_BANNER_300x50, placement = AdPlacements.BOTTOM_ALL_SCREENS,
            paymentStatus = AdStatuses.VERIFIED, approvalStatus = AdStatuses.APPROVED, campaignStatus = AdStatuses.ACTIVE,
            startDate = nowMs - 10_000L, expiryDate = futureMs, activatedAt = 1000L
        )
        val second = earliest.copy(id = "ban_2", advertiserId = "a2", activatedAt = 2000L)

        assertEquals("ban_1", AdvertisingRotationEngine.selectBottomBanner(listOf(second, earliest), nowMs)?.id)

        // Pause earliest -> automatically switches to second
        val earliestPaused = earliest.copy(campaignStatus = AdStatuses.PAUSED)
        assertEquals("ban_2", AdvertisingRotationEngine.selectBottomBanner(listOf(second, earliestPaused), nowMs)?.id)

        // Expire earliest -> automatically switches to second
        val earliestExpired = earliest.copy(expiryDate = pastMs)
        assertEquals("ban_2", AdvertisingRotationEngine.selectBottomBanner(listOf(second, earliestExpired), nowMs)?.id)
    }

    @Test
    fun `app open popup rotates round robin across eligible popup campaigns`() {
        val p1 = AdCampaignItem(
            id = "pop_1", advertiserId = "a1", imageUrl = "https://example.com/p1.jpg",
            adType = AdPackageTypes.APP_OPEN_POPUP_300x200, placement = AdPlacements.APP_LAUNCH,
            paymentStatus = AdStatuses.VERIFIED, approvalStatus = AdStatuses.APPROVED, campaignStatus = AdStatuses.ACTIVE,
            startDate = nowMs - 1000L, expiryDate = futureMs, createdAt = 100L
        )
        val p2 = p1.copy(id = "pop_2", advertiserId = "a2", createdAt = 200L)
        val p3 = p1.copy(id = "pop_3", advertiserId = "a3", createdAt = 300L)
        val list = listOf(p3, p1, p2)

        assertEquals("pop_1", AdvertisingRotationEngine.selectNextAppOpenPopup(list, nowMs)?.first?.id)
        assertEquals("pop_2", AdvertisingRotationEngine.selectNextAppOpenPopup(list, nowMs)?.first?.id)
        assertEquals("pop_3", AdvertisingRotationEngine.selectNextAppOpenPopup(list, nowMs)?.first?.id)
        assertEquals("pop_1", AdvertisingRotationEngine.selectNextAppOpenPopup(list, nowMs)?.first?.id)
    }
}
