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
    RECENTLY_UPDATED("সম্প্রতি আপডেটেড", "Recently Updated"),
    NAME_AZ("নাম (A-Z)", "Name A-Z")
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
    val facilityAltQuery: String = "",
    val weekday: DayOfWeekBd? = null,
    val availableTodayOnly: Boolean = false,
    val workplaceQuery: String = "",
    val sortOption: SortOption = SortOption.RECENTLY_UPDATED
)

/**
 * Matches doctors to a Hospital or Diagnostic Center reliably across:
 * - English & Bengali facility names
 * - Chambers (`facilityName`, `chamberName`, `fullAddress`)
 * - Current hospital / medical college & employment history
 * - Bidirectional substring & core-name matching (e.g. "Ibn Sina Diagnostic" vs "Ibn Sina Diagnostic & Consultation Center")
 */
object FacilityDoctorMatcher {
    private val genericWords = setOf(
        "hospital", "diagnostic", "center", "centre", "clinic", "medical", "college",
        "limited", "ltd", "pvt", "private", "general", "specialized", "specialised",
        "consultation", "complex", "care", "health", "healthcare", "bangladesh", "bd",
        "and", "the", "of", "at", "&", "-",
        "হাসপাতাল", "ডায়াগনস্টিক", "ডায়াগনস্টিক", "সেন্টার", "ক্লিনিক", "মেডিকেল", "কলেজ", "লিমিটেড", "এন্ড", "ও"
    )

    fun normalize(raw: String): String =
        raw.removePrefix("[DEMO] ")
            .removePrefix("[demo] ")
            .trim()
            .lowercase()
            .replace(Regex("[.,&/()\\-–—]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun significantTokens(normalized: String): List<String> =
        normalized.split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in genericWords }

    private fun stringsMatchFacility(candidateRaw: String, targetRaw: String): Boolean {
        val candidate = normalize(candidateRaw)
        val target = normalize(targetRaw)
        if (candidate.isEmpty() || target.isEmpty()) return false
        if (candidate == target) return true
        if (candidate.contains(target)) return true
        if (candidate.length >= 4 && target.contains(candidate)) return true

        val targetTokens = significantTokens(target)
        val candTokens = significantTokens(candidate)
        if (targetTokens.isEmpty() || candTokens.isEmpty()) return false

        // All significant tokens of the shorter name appear inside the longer name
        val targetSet = targetTokens.toSet()
        val candSet = candTokens.toSet()
        val intersection = targetSet.intersect(candSet)
        if (intersection.isEmpty()) return false

        if (targetSet.all { t -> candSet.any { c -> c == t || (t.length >= 4 && c.contains(t)) || (c.length >= 4 && t.contains(c)) } }) {
            return true
        }
        if (candSet.size >= 2 && candSet.all { c -> targetSet.any { t -> t == c || (c.length >= 4 && t.contains(c)) } }) {
            return true
        }
        return intersection.size >= 2
    }

    fun matchesFacility(
        profile: DoctorFullProfile,
        primaryName: String,
        altName: String = ""
    ): Boolean {
        val targets = listOf(primaryName, altName)
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        if (targets.isEmpty()) return true

        val candidates = buildList {
            add(profile.doctor.currentHospital)
            add(profile.doctor.currentMedicalCollege)
            profile.chambers.forEach { c ->
                add(c.chamber.facilityName)
                add(c.chamber.chamberName)
                add(c.chamber.fullAddress)
            }
            profile.employmentHistory.forEach { emp ->
                add(emp.hospitalName)
                add(emp.medicalCollege)
            }
        }.filter { it.isNotBlank() }

        return targets.any { target ->
            candidates.any { candidate -> stringsMatchFacility(candidate, target) }
        }
    }
}

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
