package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ChamberEntity
import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.DoctorEntity
import com.example.data.local.HospitalEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.SpecialtyEntity
import com.example.data.model.ChamberWithSchedules
import com.example.data.model.DoctorFullProfile
import com.example.data.model.GlobalSearchEngine
import com.example.data.model.GlobalSearchType
import com.example.i18n.AppLang
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalTopAppBarTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun globalSearchRequiresMinimumTwoCharactersAndSearchesAllFourCategories() {
        val doctor = DoctorFullProfile(
            doctor = DoctorEntity(
                id = "doc_101",
                fullNameBn = "ডা. রাশেদ করিম",
                fullNameEn = "Dr. Rashed Karim",
                qualification = "MBBS, FCPS (Medicine)",
                currentHospital = "Dhaka Medical College Hospital"
            ),
            specialty = SpecialtyEntity("sp_med", "মেডিসিন", "Medicine", "stethoscope"),
            educations = emptyList(),
            employmentHistory = emptyList(),
            chambers = listOf(
                ChamberWithSchedules(
                    chamber = ChamberEntity(
                        id = "ch_101",
                        doctorId = "doc_101",
                        facilityName = "Ibn Sina Consultation Chamber",
                        chamberName = "Room 405",
                        district = "Dhaka",
                        upazila = "Dhanmondi",
                        fullAddress = "Road 9/A, Dhanmondi, Dhaka",
                        isActive = true
                    ),
                    schedules = emptyList()
                ),
                ChamberWithSchedules(
                    chamber = ChamberEntity(
                        id = "ch_inactive",
                        doctorId = "doc_101",
                        facilityName = "Ibn Sina Old Closed Chamber",
                        chamberName = "Room 101",
                        district = "Dhaka",
                        fullAddress = "Old Road, Dhaka",
                        isActive = false
                    ),
                    schedules = emptyList()
                )
            )
        )
        val hospital = HospitalEntity(
            id = "hosp_101",
            nameBn = "ইবনে সিনা হাসপাতাল",
            nameEn = "Ibn Sina Hospital",
            district = "Dhaka",
            upazila = "Dhanmondi",
            address = "Dhanmondi, Dhaka",
            contactNumber = "01700000001"
        )
        val diagnostic = DiagnosticCenterEntity(
            id = "diag_101",
            nameBn = "ইবনে সিনা ডায়াগনস্টিক সেন্টার",
            nameEn = "Ibn Sina Diagnostic Center",
            district = "Dhaka",
            upazila = "Dhanmondi",
            address = "Satmasjid Road, Dhaka",
            contactNumber = "01700000002"
        )

        // < 2 characters must NOT return suggestions
        assertTrue(
            GlobalSearchEngine.search("", listOf(doctor), listOf(hospital), listOf(diagnostic), AppLang.EN).isEmpty()
        )
        assertTrue(
            GlobalSearchEngine.search("I", listOf(doctor), listOf(hospital), listOf(diagnostic), AppLang.EN).isEmpty()
        )
        assertTrue(
            GlobalSearchEngine.search("  a ", listOf(doctor), listOf(hospital), listOf(diagnostic), AppLang.EN).isEmpty()
        )

        // >= 2 characters matches Hospital, Diagnostic, Chamber (and excludes inactive chamber)
        val ibnResults = GlobalSearchEngine.search(
            query = "Ibn",
            doctors = listOf(doctor),
            hospitals = listOf(hospital),
            diagnostics = listOf(diagnostic),
            lang = AppLang.EN
        )
        assertTrue(ibnResults.any { it.type == GlobalSearchType.HOSPITAL && it.id == "hosp_101" })
        assertTrue(ibnResults.any { it.type == GlobalSearchType.DIAGNOSTIC && it.id == "diag_101" })
        assertTrue(ibnResults.any { it.type == GlobalSearchType.CHAMBER && it.id == "ch_101" })
        assertTrue(ibnResults.none { it.id == "ch_inactive" })

        // Matches Doctor by English or Bengali name
        val docEn = GlobalSearchEngine.search("Ra", listOf(doctor), listOf(hospital), listOf(diagnostic), AppLang.EN)
        assertTrue(docEn.any { it.type == GlobalSearchType.DOCTOR && it.id == "doc_101" })

        val docBn = GlobalSearchEngine.search("রাশেদ", listOf(doctor), listOf(hospital), listOf(diagnostic), AppLang.BN)
        assertTrue(docBn.any { it.type == GlobalSearchType.DOCTOR && it.id == "doc_101" })
    }

    @Test
    fun notificationDaoTracksReadIdsAndSupportsMarkAllRead() = runBlocking {
        val list = listOf(
            NotificationEntity("n1", "শিরোনাম ১", "Title 1", "বার্তা ১", "Message 1", isRead = false),
            NotificationEntity("n2", "শিরোনাম ২", "Title 2", "বার্তা ২", "Message 2", isRead = false)
        )
        db.notificationDao().insertAll(list)
        assertEquals(0, db.notificationDao().getReadIds().size)

        db.notificationDao().markRead("n1")
        assertEquals(listOf("n1"), db.notificationDao().getReadIds())

        db.notificationDao().markAllRead()
        val all = db.notificationDao().observeAll().first()
        assertTrue(all.all { it.isRead })
        assertEquals(2, db.notificationDao().getReadIds().size)
    }
}
