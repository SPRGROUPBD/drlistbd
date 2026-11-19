package com.example.data.repository

import android.content.Context
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.seed.SeedData
import kotlinx.coroutines.flow.*
import java.util.UUID

class DirectoryRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    val prefs = SessionPreferences(context)

    suspend fun ensureSeeded() {
        SeedData.populateIfEmpty(db)
    }

    val specialties: Flow<List<SpecialtyEntity>> = db.specialtyDao().observeAll()
    val locations: Flow<List<LocationEntity>> = db.locationDao().observeAll()
    val hospitals: Flow<List<HospitalEntity>> = db.hospitalDao().observeAll()
    val diagnosticCenters: Flow<List<DiagnosticCenterEntity>> = db.diagnosticCenterDao().observeAll()
    val visibleUsers: Flow<List<UserEntity>> = db.userDao().observeVisibleUsers()
    val appointments: Flow<List<AppointmentRequestEntity>> = db.appointmentDao().observeAll()
    val verificationRequests: Flow<List<VerificationRequestEntity>> = db.verificationRequestDao().observeAll()
    val reports: Flow<List<ReportEntity>> = db.reportDao().observeAll()
    val notifications: Flow<List<NotificationEntity>> = db.notificationDao().observeAll()
    val settings: Flow<SiteSettingsEntity> = db.settingsDao().observe().map { it ?: SiteSettingsEntity() }
    val auditLogs: Flow<List<AuditLogEntity>> = db.auditLogDao().observeAll()
    val hospitalDoctorLinks: Flow<List<HospitalDoctorLinkEntity>> = db.hospitalDoctorLinkDao().observeAll()
    val profileChangeRequests: Flow<List<ProfileChangeRequestEntity>> = db.profileChangeRequestDao().observeAll()

    val doctorsFull: Flow<List<DoctorFullProfile>> = combine(
        db.doctorDao().observeAll(),
        db.specialtyDao().observeAll(),
        db.educationDao().observeAll(),
        db.employmentDao().observeAll(),
        combine(db.chamberDao().observeAll(), db.chamberScheduleDao().observeAll()) { chambers, schedules ->
            chambers to schedules
        }
    ) { doctors, specs, edus, emps, chambersPair ->
        val (chambers, schedules) = chambersPair
        val specMap = specs.associateBy { it.id }
        val eduMap = edus.groupBy { it.doctorId }
        val empMap = emps.groupBy { it.doctorId }
        val schedMap = schedules.groupBy { it.chamberId }
        val chamberMap = chambers.groupBy { it.doctorId }

        doctors.map { doc ->
            DoctorFullProfile(
                doctor = doc,
                specialty = specMap[doc.mainSpecialtyId],
                educations = eduMap[doc.id].orEmpty(),
                employmentHistory = empMap[doc.id].orEmpty(),
                chambers = chamberMap[doc.id].orEmpty().map { ch ->
                    ChamberWithSchedules(ch, schedMap[ch.id].orEmpty())
                }
            )
        }
    }

    fun observeFavorites(userId: String?): Flow<Set<String>> {
        val uid = userId?.takeIf { it.isNotBlank() } ?: "guest_local"
        return db.favoriteDao().observeForUser(uid).map { list -> list.map { it.doctorId }.toSet() }
    }

    suspend fun getCurrentUser(userId: String?): UserEntity? =
        userId?.let { db.userDao().getById(it) }

    // --- Validation Helpers ---
    fun isValidExternalImageUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return true
        return (trimmed.startsWith("https://") || trimmed.startsWith("http://")) && trimmed.length > 10
    }

    fun isValidMapsUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return true
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }

    // --- Authentication ---
    suspend fun login(emailOrId: String, password: String): Result<UserEntity> {
        val cleanId = emailOrId.trim()
        if (cleanId.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Email/ID and password are required"))
        }
        val user = db.userDao().getByEmailOrId(cleanId)
            ?: return Result.failure(IllegalArgumentException("Invalid credentials"))
        if (!user.isActive) {
            return Result.failure(IllegalStateException("Account is deactivated"))
        }
        if (user.passwordHash != SeedData.sha256(password)) {
            return Result.failure(IllegalArgumentException("Invalid credentials"))
        }
        prefs.setUserId(user.id)
        logAudit(user.id, user.role, "LOGIN", "USER", user.id, "Signed in")
        return Result.success(user)
    }

    suspend fun resetPassword(email: String, newPassword: String): Result<Unit> {
        val clean = email.trim()
        if (clean.equals("admin", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Cannot reset protected system account"))
        }
        val user = db.userDao().getByEmailOrId(clean)
            ?: return Result.failure(IllegalArgumentException("No account found for $clean"))
        if (newPassword.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters"))
        }
        db.userDao().update(user.copy(passwordHash = SeedData.sha256(newPassword)))
        return Result.success(Unit)
    }

    suspend fun registerUser(
        name: String,
        email: String,
        password: String,
        phone: String,
        district: String,
        role: UserRole
    ): Result<UserEntity> {
        val cleanEmail = email.trim()
        if (name.isBlank() || cleanEmail.isBlank() || password.length < 4) {
            return Result.failure(IllegalArgumentException("Name, email, and 4+ char password required"))
        }
        if (role == UserRole.SUPER_ADMIN || role == UserRole.ADMIN || cleanEmail.equals("admin", true)) {
            return Result.failure(IllegalArgumentException("Unauthorized role registration"))
        }
        if (db.userDao().getByEmailOrId(cleanEmail) != null) {
            return Result.failure(IllegalArgumentException("Email already registered"))
        }
        val user = UserEntity(
            id = "usr_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            email = cleanEmail,
            passwordHash = SeedData.sha256(password),
            role = role.name,
            phone = phone.trim(),
            district = district.trim()
        )
        db.userDao().insert(user)
        prefs.setUserId(user.id)
        logAudit(user.id, user.role, "REGISTER", "USER", user.id, "Registered as ${role.name}")
        return Result.success(user)
    }

    suspend fun registerDoctorWithProfile(
        nameBn: String,
        nameEn: String,
        email: String,
        password: String,
        phone: String,
        bmdcNumber: String,
        specialtyId: String,
        currentHospital: String,
        designation: String
    ): Result<UserEntity> {
        val cleanBmdc = bmdcNumber.trim()
        if (cleanBmdc.isEmpty()) {
            return Result.failure(IllegalArgumentException("BMDC registration number is required"))
        }
        if (db.doctorDao().getByBmdc(cleanBmdc) != null) {
            return Result.failure(IllegalArgumentException("A doctor with BMDC $cleanBmdc already exists"))
        }
        val userRes = registerUser(nameEn.ifBlank { nameBn }, email, password, phone, "", UserRole.DOCTOR)
        if (userRes.isFailure) return userRes
        val user = userRes.getOrThrow()

        // Never auto-verify: starts PENDING
        val doctor = DoctorEntity(
            id = "doc_${UUID.randomUUID().toString().take(8)}",
            userId = user.id,
            fullNameBn = nameBn.trim().ifBlank { nameEn.trim() },
            fullNameEn = nameEn.trim().ifBlank { nameBn.trim() },
            bmdcNumber = cleanBmdc,
            bmdcVerified = false,
            verificationStatus = "PENDING",
            approvalStatus = "PENDING",
            contactNumber = phone.trim(),
            mainSpecialtyId = specialtyId,
            currentHospital = currentHospital.trim(),
            currentDesignation = designation.trim(),
            isActive = true,
            isDemoData = false
        )
        db.doctorDao().insert(doctor)
        db.verificationRequestDao().insert(
            VerificationRequestEntity(
                id = "ver_${UUID.randomUUID().toString().take(8)}",
                doctorId = doctor.id,
                bmdcNumber = cleanBmdc,
                submittedByUserId = user.id,
                status = "PENDING",
                notes = "Initial doctor registration submission"
            )
        )
        return Result.success(user)
    }

    suspend fun logout() {
        prefs.setUserId(null)
    }

    // --- Favorites ---
    suspend fun toggleFavorite(userId: String?, doctorId: String, currentlyFav: Boolean) {
        val uid = userId?.takeIf { it.isNotBlank() } ?: "guest_local"
        if (currentlyFav) {
            db.favoriteDao().remove(uid, doctorId)
        } else {
            db.favoriteDao().insert(FavoriteEntity("fav_${uid}_$doctorId", uid, doctorId))
        }
    }

    // --- Patient Actions ---
    suspend fun submitReport(doctorId: String, userId: String?, reason: String, details: String): Result<Unit> {
        if (reason.isBlank() || details.isBlank()) {
            return Result.failure(IllegalArgumentException("Reason and details required"))
        }
        db.reportDao().insert(
            ReportEntity(
                id = "rep_${UUID.randomUUID().toString().take(8)}",
                doctorId = doctorId,
                reportedByUserId = userId ?: "guest",
                reason = reason.trim(),
                details = details.trim()
            )
        )
        return Result.success(Unit)
    }

    suspend fun requestAppointment(
        patientUserId: String?,
        patientName: String,
        patientPhone: String,
        doctorId: String,
        chamberId: String,
        preferredDay: String,
        preferredSlot: String,
        notes: String
    ): Result<Unit> {
        if (patientName.isBlank() || patientPhone.isBlank()) {
            return Result.failure(IllegalArgumentException("Patient name and phone required"))
        }
        db.appointmentDao().insert(
            AppointmentRequestEntity(
                id = "apt_${UUID.randomUUID().toString().take(8)}",
                patientUserId = patientUserId ?: "guest",
                patientName = patientName.trim(),
                patientPhone = patientPhone.trim(),
                doctorId = doctorId,
                chamberId = chamberId,
                preferredDay = preferredDay,
                preferredSlot = preferredSlot,
                notes = notes.trim(),
                status = "PENDING" // Never auto-confirmed
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateAppointmentStatus(id: String, status: String, actorId: String, actorRole: String) {
        db.appointmentDao().updateStatus(id, status)
        logAudit(actorId, actorRole, "APPOINTMENT_$status", "APPOINTMENT", id, "Status changed to $status")
    }

    // --- Doctor & Admin Doctor CRUD ---
    suspend fun saveDoctorProfile(
        doctor: DoctorEntity,
        actor: UserEntity,
        isAdminAction: Boolean
    ): Result<Unit> {
        if (!isValidExternalImageUrl(doctor.photoUrl)) {
            return Result.failure(IllegalArgumentException("Image URL must start with http:// or https://"))
        }
        if (doctor.bmdcNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("BMDC number is required"))
        }
        val existingBmdc = db.doctorDao().getByBmdc(doctor.bmdcNumber.trim())
        if (existingBmdc != null && existingBmdc.id != doctor.id) {
            return Result.failure(IllegalArgumentException("Duplicate BMDC number: ${doctor.bmdcNumber}"))
        }
        val existing = db.doctorDao().getById(doctor.id)
        // Security: Doctors cannot self-verify or alter verification fields
        val safeDoctor = if (isAdminAction && (actor.role == UserRole.SUPER_ADMIN.name || actor.role == UserRole.ADMIN.name)) {
            doctor.copy(updatedAt = System.currentTimeMillis())
        } else {
            doctor.copy(
                bmdcVerified = existing?.bmdcVerified ?: false,
                verificationStatus = existing?.verificationStatus ?: "PENDING",
                approvalStatus = existing?.approvalStatus ?: "PENDING",
                verificationDate = existing?.verificationDate,
                verifiedBy = existing?.verifiedBy ?: "",
                verificationNotes = existing?.verificationNotes ?: "",
                updatedAt = System.currentTimeMillis()
            )
        }
        db.doctorDao().insert(safeDoctor)
        logAudit(actor.id, actor.role, "SAVE_DOCTOR", "DOCTOR", safeDoctor.id, safeDoctor.fullNameEn)
        return Result.success(Unit)
    }

    suspend fun deleteDoctor(doctorId: String, actor: UserEntity) {
        db.doctorDao().deleteById(doctorId)
        logAudit(actor.id, actor.role, "DELETE_DOCTOR", "DOCTOR", doctorId, "Deleted doctor")
    }

    suspend fun setDoctorApproval(doctorId: String, approvalStatus: String, actor: UserEntity) {
        val doc = db.doctorDao().getById(doctorId) ?: return
        db.doctorDao().update(doc.copy(approvalStatus = approvalStatus, updatedAt = System.currentTimeMillis()))
        logAudit(actor.id, actor.role, "APPROVAL_$approvalStatus", "DOCTOR", doctorId, doc.fullNameEn)
    }

    suspend fun setDoctorActive(doctorId: String, active: Boolean, actor: UserEntity) {
        val doc = db.doctorDao().getById(doctorId) ?: return
        db.doctorDao().update(doc.copy(isActive = active, updatedAt = System.currentTimeMillis()))
        logAudit(actor.id, actor.role, if (active) "ACTIVATE_DOCTOR" else "DEACTIVATE_DOCTOR", "DOCTOR", doctorId, doc.fullNameEn)
    }

    suspend fun verifyDoctorBmdc(
        doctorId: String,
        verified: Boolean,
        notes: String,
        actor: UserEntity
    ) {
        if (actor.role != UserRole.SUPER_ADMIN.name && actor.role != UserRole.ADMIN.name) return
        val doc = db.doctorDao().getById(doctorId) ?: return
        val status = if (verified) "VERIFIED" else "REJECTED"
        db.doctorDao().update(
            doc.copy(
                bmdcVerified = verified,
                verificationStatus = status,
                approvalStatus = if (verified) "APPROVED" else doc.approvalStatus,
                verificationDate = System.currentTimeMillis(),
                verifiedBy = actor.name,
                verificationNotes = notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        logAudit(actor.id, actor.role, "BMDC_$status", "DOCTOR", doctorId, "Notes: $notes")
    }

    suspend fun submitVerificationRequest(doctorId: String, bmdcNumber: String, userId: String, notes: String) {
        db.verificationRequestDao().insert(
            VerificationRequestEntity(
                id = "ver_${UUID.randomUUID().toString().take(8)}",
                doctorId = doctorId,
                bmdcNumber = bmdcNumber,
                submittedByUserId = userId,
                status = "PENDING",
                notes = notes
            )
        )
    }

    suspend fun reviewVerificationRequest(req: VerificationRequestEntity, approved: Boolean, notes: String, actor: UserEntity) {
        val status = if (approved) "VERIFIED" else "REJECTED"
        db.verificationRequestDao().update(
            req.copy(
                status = status,
                notes = notes,
                reviewedAt = System.currentTimeMillis(),
                reviewedBy = actor.name
            )
        )
        verifyDoctorBmdc(req.doctorId, approved, notes, actor)
    }

    suspend fun submitProfileChangeRequest(
        doctorId: String,
        userId: String,
        summary: String,
        newDesignation: String,
        newHospital: String,
        newBioBn: String
    ) {
        db.profileChangeRequestDao().insert(
            ProfileChangeRequestEntity(
                id = "pcr_${UUID.randomUUID().toString().take(8)}",
                doctorId = doctorId,
                requestedByUserId = userId,
                summary = summary,
                newDesignation = newDesignation,
                newHospital = newHospital,
                newBioBn = newBioBn
            )
        )
    }

    suspend fun reviewProfileChangeRequest(req: ProfileChangeRequestEntity, approve: Boolean, actor: UserEntity) {
        db.profileChangeRequestDao().updateStatus(req.id, if (approve) "APPROVED" else "REJECTED")
        if (approve) {
            val doc = db.doctorDao().getById(req.doctorId)
            if (doc != null) {
                db.doctorDao().update(
                    doc.copy(
                        currentDesignation = req.newDesignation.ifBlank { doc.currentDesignation },
                        currentHospital = req.newHospital.ifBlank { doc.currentHospital },
                        bioBn = req.newBioBn.ifBlank { doc.bioBn },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        logAudit(actor.id, actor.role, if (approve) "APPROVE_CHANGE" else "REJECT_CHANGE", "DOCTOR", req.doctorId, req.summary)
    }

    // --- Education, Employment, Chambers, Schedules ---
    suspend fun addEducation(item: EducationEntity) = db.educationDao().insert(item)
    suspend fun deleteEducation(id: String) = db.educationDao().deleteById(id)

    suspend fun addEmployment(item: EmploymentHistoryEntity) = db.employmentDao().insert(item)
    suspend fun deleteEmployment(id: String) = db.employmentDao().deleteById(id)

    suspend fun saveChamber(chamber: ChamberEntity): Result<Unit> {
        if (!isValidMapsUrl(chamber.googleMapsUrl)) {
            return Result.failure(IllegalArgumentException("Google Maps URL must start with http:// or https://"))
        }
        db.chamberDao().insert(chamber)
        return Result.success(Unit)
    }

    suspend fun deleteChamber(id: String) = db.chamberDao().deleteById(id)

    suspend fun addSchedule(schedule: ChamberScheduleEntity) = db.chamberScheduleDao().insert(schedule)
    suspend fun deleteSchedule(id: String) = db.chamberScheduleDao().deleteById(id)

    // --- Hospitals, Diagnostics, Specialties, Locations ---
    suspend fun saveHospital(h: HospitalEntity, actor: UserEntity): Result<Unit> {
        if (!isValidExternalImageUrl(h.imageUrl)) {
            return Result.failure(IllegalArgumentException("Image URL must start with http:// or https://"))
        }
        db.hospitalDao().insert(h)
        logAudit(actor.id, actor.role, "SAVE_HOSPITAL", "HOSPITAL", h.id, h.nameEn)
        return Result.success(Unit)
    }

    suspend fun deleteHospital(id: String, actor: UserEntity) {
        db.hospitalDao().deleteById(id)
        logAudit(actor.id, actor.role, "DELETE_HOSPITAL", "HOSPITAL", id, "Deleted hospital")
    }

    suspend fun saveDiagnosticCenter(dc: DiagnosticCenterEntity, actor: UserEntity): Result<Unit> {
        if (!isValidExternalImageUrl(dc.imageUrl)) {
            return Result.failure(IllegalArgumentException("Image URL must start with http:// or https://"))
        }
        db.diagnosticCenterDao().insert(dc)
        logAudit(actor.id, actor.role, "SAVE_DIAGNOSTIC", "DIAGNOSTIC", dc.id, dc.nameEn)
        return Result.success(Unit)
    }

    suspend fun deleteDiagnosticCenter(id: String, actor: UserEntity) {
        db.diagnosticCenterDao().deleteById(id)
        logAudit(actor.id, actor.role, "DELETE_DIAGNOSTIC", "DIAGNOSTIC", id, "Deleted diagnostic center")
    }

    suspend fun saveSpecialty(sp: SpecialtyEntity, actor: UserEntity) {
        db.specialtyDao().insert(sp)
        logAudit(actor.id, actor.role, "SAVE_SPECIALTY", "SPECIALTY", sp.id, sp.nameEn)
    }

    suspend fun deleteSpecialty(id: String, actor: UserEntity) {
        db.specialtyDao().deleteById(id)
        logAudit(actor.id, actor.role, "DELETE_SPECIALTY", "SPECIALTY", id, "Deleted specialty")
    }

    suspend fun saveLocation(loc: LocationEntity, actor: UserEntity) {
        db.locationDao().insert(loc)
        logAudit(actor.id, actor.role, "SAVE_LOCATION", "LOCATION", loc.id, "${loc.district} - ${loc.upazila}")
    }

    suspend fun deleteLocation(id: String, actor: UserEntity) {
        db.locationDao().deleteById(id)
        logAudit(actor.id, actor.role, "DELETE_LOCATION", "LOCATION", id, "Deleted location")
    }

    // --- Hospital-Doctor Links ---
    suspend fun requestHospitalDoctorLink(hospitalId: String, doctorId: String, userId: String) {
        db.hospitalDoctorLinkDao().insert(
            HospitalDoctorLinkEntity(
                id = "hdl_${UUID.randomUUID().toString().take(8)}",
                hospitalId = hospitalId,
                doctorId = doctorId,
                status = "PENDING",
                requestedBy = userId
            )
        )
    }

    suspend fun updateHospitalDoctorLinkStatus(id: String, status: String, actor: UserEntity) {
        db.hospitalDoctorLinkDao().updateStatus(id, status)
        logAudit(actor.id, actor.role, "LINK_$status", "HOSPITAL_DOCTOR", id, "Status -> $status")
    }

    // --- Admin Misc: Users, Reports, Notifications, Settings, Demo Cleanup, CSV ---
    suspend fun updateUserRole(user: UserEntity, newRole: UserRole, active: Boolean, actor: UserEntity) {
        if (user.isHidden) return
        db.userDao().update(user.copy(role = newRole.name, isActive = active))
        logAudit(actor.id, actor.role, "UPDATE_USER_ROLE", "USER", user.id, "${user.email} -> ${newRole.name}")
    }

    suspend fun resolveReport(reportId: String, actor: UserEntity) {
        db.reportDao().updateStatus(reportId, "RESOLVED")
        logAudit(actor.id, actor.role, "RESOLVE_REPORT", "REPORT", reportId, "Marked resolved")
    }

    suspend fun sendNotification(titleBn: String, titleEn: String, msgBn: String, msgEn: String, targetRole: String, actor: UserEntity) {
        db.notificationDao().insert(
            NotificationEntity(
                id = "ntf_${UUID.randomUUID().toString().take(8)}",
                titleBn = titleBn,
                titleEn = titleEn,
                messageBn = msgBn,
                messageEn = msgEn,
                targetRole = targetRole
            )
        )
        logAudit(actor.id, actor.role, "SEND_NOTIFICATION", "NOTIFICATION", targetRole, titleEn)
    }

    suspend fun markNotificationRead(id: String) = db.notificationDao().markRead(id)

    suspend fun saveSettings(s: SiteSettingsEntity, actor: UserEntity) {
        db.settingsDao().save(s)
        logAudit(actor.id, actor.role, "SAVE_SETTINGS", "SETTINGS", "global", "Updated website settings")
    }

    suspend fun clearAllDemoData(actor: UserEntity) {
        db.doctorDao().deleteDemoDoctors()
        db.hospitalDao().deleteDemoHospitals()
        db.diagnosticCenterDao().deleteDemoCenters()
        logAudit(actor.id, actor.role, "CLEAR_DEMO_DATA", "SYSTEM", "demo", "Removed all DEMO entries")
    }

    suspend fun logAudit(actorId: String, actorRole: String, action: String, targetType: String, targetId: String, details: String) {
        db.auditLogDao().insert(
            AuditLogEntity(
                id = "aud_${UUID.randomUUID().toString().take(8)}",
                actorUserId = actorId,
                actorRole = actorRole,
                action = action,
                targetType = targetType,
                targetId = targetId,
                details = details
            )
        )
    }

    fun buildDoctorsCsv(list: List<DoctorFullProfile>): String {
        val sb = StringBuilder("ID,Name_EN,Name_BN,BMDC,Verified,Approval,Specialty,Hospital,Designation,Phone\n")
        list.forEach { d ->
            sb.append(
                listOf(
                    d.doctor.id,
                    d.doctor.fullNameEn,
                    d.doctor.fullNameBn,
                    d.doctor.bmdcNumber,
                    d.doctor.bmdcVerified.toString(),
                    d.doctor.approvalStatus,
                    d.specialty?.nameEn ?: "",
                    d.doctor.currentHospital,
                    d.doctor.currentDesignation,
                    d.doctor.contactNumber
                ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
            ).append('\n')
        }
        return sb.toString()
    }

    fun buildHospitalsCsv(list: List<HospitalEntity>): String {
        val sb = StringBuilder("ID,Name_EN,Name_BN,District,Upazila,Address,Phone,Status\n")
        list.forEach { h ->
            sb.append(
                listOf(h.id, h.nameEn, h.nameBn, h.district, h.upazila, h.address, h.contactNumber, h.status)
                    .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
            ).append('\n')
        }
        return sb.toString()
    }
}
