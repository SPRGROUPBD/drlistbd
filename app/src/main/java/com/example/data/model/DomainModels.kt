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

enum class DayOfWeekBd(
    val bnLabel: String,
    val enLabel: String,
    val shortBn: String,
    val shortEn: String,
    val calendarDay: Int
) {
    SATURDAY("শনিবার", "Saturday", "শনি", "Sat", Calendar.SATURDAY),
    SUNDAY("রবিবার", "Sunday", "রবি", "Sun", Calendar.SUNDAY),
    MONDAY("সোমবার", "Monday", "সোম", "Mon", Calendar.MONDAY),
    TUESDAY("মঙ্গলবার", "Tuesday", "মঙ্গল", "Tue", Calendar.TUESDAY),
    WEDNESDAY("বুধবার", "Wednesday", "বুধ", "Wed", Calendar.WEDNESDAY),
    THURSDAY("বৃহস্পতিবার", "Thursday", "বৃহঃ", "Thu", Calendar.THURSDAY),
    FRIDAY("শুক্রবার", "Friday", "শুক্র", "Fri", Calendar.FRIDAY);

    companion object {
        fun today(): DayOfWeekBd {
            val calDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            return entries.firstOrNull { it.calendarDay == calDay } ?: SATURDAY
        }
    }
}

enum class ChamberSessionType(val bnLabel: String, val enLabel: String) {
    MORNING("সকাল", "Morning"),
    AFTERNOON("বিকাল", "Afternoon"),
    EVENING("সন্ধ্যা", "Evening")
}

fun ChamberScheduleEntity.startMinutesOfDay(): Int {
    val cleaned = openTime.trim().uppercase()
    val match = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)?$""").find(cleaned) ?: return 0
    var hour = match.groupValues[1].toIntOrNull() ?: 0
    val min = match.groupValues[2].toIntOrNull() ?: 0
    val meridiem = match.groupValues[3]
    if (meridiem == "PM" && hour < 12) hour += 12
    if (meridiem == "AM" && hour == 12) hour = 0
    return hour * 60 + min
}

fun ChamberScheduleEntity.sessionType(): ChamberSessionType {
    val label = slotLabel.lowercase()
    if (label.contains("morning") || label.contains("সকাল")) return ChamberSessionType.MORNING
    if (label.contains("afternoon") || label.contains("বিকাল")) return ChamberSessionType.AFTERNOON
    if (label.contains("evening") || label.contains("night") || label.contains("সন্ধ্যা")) return ChamberSessionType.EVENING
    val mins = startMinutesOfDay()
    return when {
        mins < 13 * 60 -> ChamberSessionType.MORNING
        mins < 18 * 60 -> ChamberSessionType.AFTERNOON
        else -> ChamberSessionType.EVENING
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
