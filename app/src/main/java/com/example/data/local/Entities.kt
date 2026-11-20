package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val role: String, // UserRole.name
    val phone: String = "",
    val district: String = "",
    val isActive: Boolean = true,
    val isHidden: Boolean = false, // true for super admin so regular users never see admin
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "doctors")
data class DoctorEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val fullNameBn: String,
    val fullNameEn: String,
    val photoUrl: String = "",
    val qualification: String = "",
    val bmdcNumber: String = "",
    val bmdcVerified: Boolean = false,
    val verificationStatus: String = "PENDING", // VerificationStatus.name
    val approvalStatus: String = "PENDING", // PENDING, APPROVED, REJECTED
    val verificationDate: Long? = null,
    val verifiedBy: String = "",
    val verificationNotes: String = "",
    val gender: String = "",
    val bioBn: String = "",
    val bioEn: String = "",
    val contactNumber: String = "",
    val mainSpecialtyId: String = "",
    val subSpecialty: String = "",
    val expertise: String = "",
    val currentHospital: String = "",
    val currentMedicalCollege: String = "",
    val currentDepartment: String = "",
    val currentDesignation: String = "",
    val employmentStartDate: String = "",
    val totalExperienceYears: Int = 0,
    val teachingExperienceYears: Int = 0,
    val trainings: String = "",
    val isActive: Boolean = true,
    val isFeatured: Boolean = false,
    val isDemoData: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "education")
data class EducationEntity(
    @PrimaryKey val id: String,
    val doctorId: String,
    val degreeName: String,
    val institution: String,
    val passingYear: String,
    val specialTraining: String = ""
)

@Entity(tableName = "employment_history")
data class EmploymentHistoryEntity(
    @PrimaryKey val id: String,
    val doctorId: String,
    val hospitalName: String,
    val medicalCollege: String = "",
    val designation: String,
    val department: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val description: String = ""
)

@Entity(tableName = "specialties")
data class SpecialtyEntity(
    @PrimaryKey val id: String,
    val nameBn: String,
    val nameEn: String,
    val iconKey: String = "medical"
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val division: String,
    val district: String,
    val upazila: String,
    val area: String = ""
)

@Entity(tableName = "hospitals")
data class HospitalEntity(
    @PrimaryKey val id: String,
    val managerUserId: String = "",
    val nameBn: String,
    val nameEn: String,
    val district: String,
    val upazila: String = "",
    val address: String,
    val contactNumber: String,
    val imageUrl: String = "",
    val status: String = "ACTIVE", // ACTIVE, PENDING, INACTIVE
    val isDemoData: Boolean = false
)

@Entity(tableName = "diagnostic_centers")
data class DiagnosticCenterEntity(
    @PrimaryKey val id: String,
    val nameBn: String,
    val nameEn: String,
    val district: String,
    val upazila: String = "",
    val address: String,
    val contactNumber: String,
    val imageUrl: String = "",
    val status: String = "ACTIVE",
    val isDemoData: Boolean = false
)

@Entity(tableName = "chambers")
data class ChamberEntity(
    @PrimaryKey val id: String,
    val doctorId: String,
    val facilityName: String,
    val facilityType: String = "HOSPITAL", // HOSPITAL, DIAGNOSTIC_CENTER, PRIVATE
    val chamberName: String = "",
    val district: String,
    val upazila: String = "",
    val fullAddress: String,
    val locationArea: String = "",
    val googleMapsUrl: String = "",
    val serialPhone: String = "",
    val instructions: String = "",
    val isActive: Boolean = true
)

@Entity(tableName = "chamber_schedules")
data class ChamberScheduleEntity(
    @PrimaryKey val id: String,
    val chamberId: String,
    val doctorId: String,
    val dayOfWeek: String, // DayOfWeekBd.name
    val openTime: String,
    val closeTime: String,
    val slotLabel: String = ""
)

@Entity(tableName = "appointments")
data class AppointmentRequestEntity(
    @PrimaryKey val id: String,
    val patientUserId: String = "",
    val patientName: String,
    val patientPhone: String,
    val doctorId: String,
    val chamberId: String,
    val preferredDay: String,
    val preferredSlot: String,
    val notes: String = "",
    val status: String = "PENDING", // PENDING, CONFIRMED, REJECTED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val doctorId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "verification_requests")
data class VerificationRequestEntity(
    @PrimaryKey val id: String,
    val doctorId: String,
    val bmdcNumber: String,
    val submittedByUserId: String,
    val status: String = "PENDING", // PENDING, VERIFIED, REJECTED
    val notes: String = "",
    val submittedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val reviewedBy: String = ""
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val doctorId: String,
    val reportedByUserId: String = "",
    val reason: String,
    val details: String,
    val status: String = "OPEN", // OPEN, RESOLVED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val titleBn: String,
    val titleEn: String,
    val messageBn: String,
    val messageEn: String,
    val targetRole: String = "ALL",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SiteSettingsEntity(
    @PrimaryKey val id: String = "global",
    val siteTitle: String = "Doctor Directory Bangladesh",
    val taglineBn: String = "ডাক্তার কোথায় বসেন, কখন বসেন — সব তথ্য এক জায়গায়।",
    val taglineEn: String = "Where doctors sit & when they sit — all verified chamber info in one place.",
    val contactEmail: String = "support@doctordirectorybd.org",
    val contactPhone: String = "+880 1700-000000",
    val allowOnlineBooking: Boolean = true,
    val showDemoData: Boolean = true
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actorUserId: String,
    val actorRole: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "hospital_doctor_links")
data class HospitalDoctorLinkEntity(
    @PrimaryKey val id: String,
    val hospitalId: String,
    val doctorId: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val requestedBy: String = ""
)

@Entity(tableName = "profile_change_requests")
data class ProfileChangeRequestEntity(
    @PrimaryKey val id: String,
    val doctorId: String,
    val requestedByUserId: String,
    val summary: String,
    val newDesignation: String = "",
    val newHospital: String = "",
    val newBioBn: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ad_config")
data class AdConfigEntity(
    @PrimaryKey val id: String = "global",
    val adsEnabled: Boolean = true,
    // UNITY | STARTIO | BOTH_UNITY_PRIMARY | BOTH_STARTIO_PRIMARY
    val activeNetwork: String = "BOTH_UNITY_PRIMARY",
    val testMode: Boolean = false, // false = 100% REAL LIVE REVENUE ADS
    val bannerEnabled: Boolean = true,
    val appOpenEnabled: Boolean = true,
    val nativeBannerEnabled: Boolean = true,
    val rewardedEnabled: Boolean = true,
    val nativeAdInterval: Int = 3,
    val appOpenCooldownSeconds: Int = 120,
    val unityGameId: String = "",
    val unityBannerPlacement: String = "Banner_Android",
    val unityAppOpenPlacement: String = "Interstitial_Android",
    val unityNativeBannerPlacement: String = "Banner_Android",
    val unityRewardedPlacement: String = "Rewarded_Android",
    val startIoAppId: String = "210630916",
    val updatedAt: Long = System.currentTimeMillis()
)
