package com.example.data.model

import com.example.data.local.ChamberEntity
import com.example.data.local.ChamberScheduleEntity
import com.example.data.local.DoctorEntity
import com.example.data.local.EducationEntity
import com.example.data.local.EmploymentHistoryEntity
import com.example.data.local.SpecialtyEntity
import java.util.Calendar

enum class UserRole(val bnLabel: String, val enLabel: String) {
    SUPER_ADMIN("সুপার অ্যাডমিন", "Super Admin"),
    ADMIN("অ্যাডমিন", "Admin"),
    DOCTOR("ডাক্তার", "Doctor"),
    HOSPITAL_MANAGER("হাসপাতাল ম্যানেজার", "Hospital Manager"),
    PATIENT("রোগী", "Patient")
}

enum class VerificationStatus(val bnLabel: String, val enLabel: String) {
    PENDING("অনুমোদন পেন্ডিং", "Pending"),
    VERIFIED("BMDC ভেরিফায়েড", "Verified"),
    REJECTED("বাতিলকৃত", "Rejected")
}

enum class DayOfWeekBd(val bnLabel: String, val enLabel: String, val shortBn: String, val calendarDay: Int) {
    SATURDAY("শনিবার", "Saturday", "শনি", Calendar.SATURDAY),
    SUNDAY("রবিবার", "Sunday", "রবি", Calendar.SUNDAY),
    MONDAY("সোমবার", "Monday", "সোম", Calendar.MONDAY),
    TUESDAY("মঙ্গলবার", "Tuesday", "মঙ্গল", Calendar.TUESDAY),
    WEDNESDAY("বুধবার", "Wednesday", "বুধ", Calendar.WEDNESDAY),
    THURSDAY("বৃহস্পতিবার", "Thursday", "বৃহঃ", Calendar.THURSDAY),
    FRIDAY("শুক্রবার", "Friday", "শুক্র", Calendar.FRIDAY);

    companion object {
        fun today(): DayOfWeekBd {
            val calDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            return entries.firstOrNull { it.calendarDay == calDay } ?: SATURDAY
        }
    }
}

enum class SortOption(val bnLabel: String, val enLabel: String) {
    VERIFIED_FIRST("ভেরিফায়েড আগে", "Verified First"),
    NAME_AZ("নাম (A-Z)", "Name A-Z"),
    RECENTLY_UPDATED("সম্প্রতি আপডেটেড", "Recently Updated")
}

data class ChamberWithSchedules(
    val chamber: ChamberEntity,
    val schedules: List<ChamberScheduleEntity>
)

data class DoctorFullProfile(
    val doctor: DoctorEntity,
    val specialty: SpecialtyEntity?,
    val educations: List<EducationEntity>,
    val employmentHistory: List<EmploymentHistoryEntity>,
    val chambers: List<ChamberWithSchedules>
) {
    fun isAvailableOn(day: DayOfWeekBd): Boolean =
        chambers.any { c -> c.chamber.isActive && c.schedules.any { it.dayOfWeek == day.name } }

    fun isAvailableToday(): Boolean = isAvailableOn(DayOfWeekBd.today())
}

data class SearchFilterState(
    val query: String = "",
    val specialtyId: String? = null,
    val subSpecialty: String = "",
    val division: String? = null,
    val district: String? = null,
    val upazila: String? = null,
    val facilityQuery: String = "",
    val weekday: DayOfWeekBd? = null,
    val availableTodayOnly: Boolean = false,
    val workplaceQuery: String = "",
    val verifiedOnly: Boolean = false,
    val sortOption: SortOption = SortOption.RECENTLY_UPDATED
)

data class AdminDashboardStats(
    val totalDoctors: Int = 0,
    val verifiedDoctors: Int = 0,
    val pendingDoctors: Int = 0,
    val rejectedDoctors: Int = 0,
    val totalHospitals: Int = 0,
    val totalDiagnosticCenters: Int = 0,
    val totalChambers: Int = 0,
    val totalPatients: Int = 0,
    val profileChangeRequests: Int = 0,
    val openReports: Int = 0
)
