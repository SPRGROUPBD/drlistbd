package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.DirectoryRepository
import com.example.i18n.AppLang
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repo = DirectoryRepository(application)

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _searchState = MutableStateFlow(SearchFilterState())
    val searchState: StateFlow<SearchFilterState> = _searchState.asStateFlow()

    private val _visiblePageCount = MutableStateFlow(10)
    val visiblePageCount: StateFlow<Int> = _visiblePageCount.asStateFlow()

    val currentLang: StateFlow<AppLang> = repo.prefs.currentLang
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLang.BN)

    val currentUser: StateFlow<UserEntity?> = repo.prefs.currentUserId
        .map { uid -> repo.getCurrentUser(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<SiteSettingsEntity> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SiteSettingsEntity())

    val specialties: StateFlow<List<SpecialtyEntity>> = repo.specialties
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val locations: StateFlow<List<LocationEntity>> = repo.locations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allHospitals: StateFlow<List<HospitalEntity>> = repo.hospitals
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allDiagnosticCenters: StateFlow<List<DiagnosticCenterEntity>> = repo.diagnosticCenters
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allDoctorsFull: StateFlow<List<DoctorFullProfile>> = repo.doctorsFull
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleHospitals: StateFlow<List<HospitalEntity>> = combine(allHospitals, settings) { list, st ->
        list.filter { st.showDemoData || !it.isDemoData }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleDiagnosticCenters: StateFlow<List<DiagnosticCenterEntity>> = combine(allDiagnosticCenters, settings) { list, st ->
        list.filter { st.showDemoData || !it.isDemoData }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publicDoctors: StateFlow<List<DoctorFullProfile>> = combine(allDoctorsFull, settings) { list, st ->
        list.filter { d ->
            d.doctor.isActive &&
                d.doctor.approvalStatus == "APPROVED" &&
                (st.showDemoData || !d.doctor.isDemoData)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val debouncedSearch: Flow<SearchFilterState> = _searchState.debounce(180)

    val filteredDoctors: StateFlow<List<DoctorFullProfile>> = combine(
        publicDoctors,
        debouncedSearch,
        locations
    ) { docs, filter, locs ->
        val q = filter.query.trim().lowercase()
        val subQ = filter.subSpecialty.trim().lowercase()
        val facQ = filter.facilityQuery.trim().lowercase()
        val workQ = filter.workplaceQuery.trim().lowercase()
        val divDistricts = filter.division?.let { div ->
            locs.filter { it.division.equals(div, true) }.map { it.district.lowercase() }.toSet()
        }

        val filtered = docs.filter { item ->
            val d = item.doctor
            val matchesQuery = q.isEmpty() ||
                d.fullNameBn.lowercase().contains(q) ||
                d.fullNameEn.lowercase().contains(q) ||
                d.subSpecialty.lowercase().contains(q) ||
                d.currentHospital.lowercase().contains(q) ||
                (item.specialty?.nameBn?.lowercase()?.contains(q) == true) ||
                (item.specialty?.nameEn?.lowercase()?.contains(q) == true) ||
                item.chambers.any {
                    it.chamber.facilityName.lowercase().contains(q) ||
                        it.chamber.district.lowercase().contains(q) ||
                        it.chamber.upazila.lowercase().contains(q)
                }

            val matchesSpec = filter.specialtyId == null || d.mainSpecialtyId == filter.specialtyId
            val matchesSub = subQ.isEmpty() || d.subSpecialty.lowercase().contains(subQ) || d.expertise.lowercase().contains(subQ)
            val matchesDiv = divDistricts == null || item.chambers.any { it.chamber.district.lowercase() in divDistricts }
            val matchesDistrict = filter.district == null || item.chambers.any { it.chamber.district.equals(filter.district, true) }
            val matchesUpazila = filter.upazila == null || item.chambers.any { it.chamber.upazila.equals(filter.upazila, true) }
            val matchesFacility = facQ.isEmpty() || item.chambers.any { it.chamber.facilityName.lowercase().contains(facQ) }
            val matchesDay = filter.weekday == null || item.isAvailableOn(filter.weekday)
            val matchesToday = !filter.availableTodayOnly || item.isAvailableToday()
            val matchesWork = workQ.isEmpty() || d.currentHospital.lowercase().contains(workQ) || d.currentMedicalCollege.lowercase().contains(workQ)
            val matchesVerified = !filter.verifiedOnly || d.bmdcVerified

            matchesQuery && matchesSpec && matchesSub && matchesDiv && matchesDistrict &&
                matchesUpazila && matchesFacility && matchesDay && matchesToday && matchesWork && matchesVerified
        }

        when (filter.sortOption) {
            SortOption.NAME_AZ -> filtered.sortedBy { it.doctor.fullNameEn.lowercase() }
            SortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.doctor.updatedAt }
            SortOption.VERIFIED_FIRST -> filtered.sortedWith(
                compareByDescending<DoctorFullProfile> { it.doctor.bmdcVerified }
                    .thenByDescending { it.doctor.updatedAt }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDoctorIds: StateFlow<Set<String>> = repo.prefs.currentUserId
        .flatMapLatest { uid -> repo.observeFavorites(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val appointments: StateFlow<List<AppointmentRequestEntity>> = repo.appointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val verificationRequests: StateFlow<List<VerificationRequestEntity>> = repo.verificationRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reports: StateFlow<List<ReportEntity>> = repo.reports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repo.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleUsers: StateFlow<List<UserEntity>> = repo.visibleUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repo.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hospitalDoctorLinks: StateFlow<List<HospitalDoctorLinkEntity>> = repo.hospitalDoctorLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profileChangeRequests: StateFlow<List<ProfileChangeRequestEntity>> = repo.profileChangeRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminStats: StateFlow<AdminDashboardStats> = combine(
        allDoctorsFull,
        allHospitals,
        allDiagnosticCenters,
        visibleUsers,
        combine(profileChangeRequests, reports) { pcr, rep -> pcr to rep }
    ) { docs, hosps, diags, users, pair ->
        val (pcr, rep) = pair
        AdminDashboardStats(
            totalDoctors = docs.size,
            verifiedDoctors = docs.count { it.doctor.bmdcVerified },
            pendingDoctors = docs.count { it.doctor.approvalStatus == "PENDING" },
            rejectedDoctors = docs.count { it.doctor.approvalStatus == "REJECTED" },
            totalHospitals = hosps.size,
            totalDiagnosticCenters = diags.size,
            totalChambers = docs.sumOf { it.chambers.size },
            totalPatients = users.count { it.role == UserRole.PATIENT.name },
            profileChangeRequests = pcr.count { it.status == "PENDING" },
            openReports = rep.count { it.status == "OPEN" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminDashboardStats())

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
    fun showMessage(msg: String) { _snackbar.value = msg }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (currentLang.value == AppLang.BN) AppLang.EN else AppLang.BN
            repo.prefs.setLanguage(next)
        }
    }

    fun updateSearch(transform: (SearchFilterState) -> SearchFilterState) {
        _searchState.update(transform)
        _visiblePageCount.value = 10
    }

    fun resetFilters() {
        _searchState.value = SearchFilterState()
        _visiblePageCount.value = 10
    }

    fun loadMoreDoctors() {
        _visiblePageCount.update { it + 10 }
    }

    // --- Auth ---
    fun login(emailOrId: String, password: String, onSuccess: (UserEntity) -> Unit) {
        viewModelScope.launch {
            repo.login(emailOrId, password)
                .onSuccess { user ->
                    showMessage(if (currentLang.value == AppLang.BN) "স্বাগতম, ${user.name}" else "Welcome, ${user.name}")
                    onSuccess(user)
                }
                .onFailure { showMessage(it.message ?: "Login failed") }
        }
    }

    fun resetPassword(email: String, newPass: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.resetPassword(email, newPass)
                .onSuccess {
                    showMessage(if (currentLang.value == AppLang.BN) "পাসওয়ার্ড আপডেট হয়েছে" else "Password updated")
                    onDone()
                }
                .onFailure { showMessage(it.message ?: "Reset failed") }
        }
    }

    fun registerUser(name: String, email: String, pass: String, phone: String, district: String, role: UserRole, onSuccess: (UserEntity) -> Unit) {
        viewModelScope.launch {
            repo.registerUser(name, email, pass, phone, district, role)
                .onSuccess {
                    showMessage(if (currentLang.value == AppLang.BN) "নিবন্ধন সফল হয়েছে" else "Registration successful")
                    onSuccess(it)
                }
                .onFailure { showMessage(it.message ?: "Registration failed") }
        }
    }

    fun registerDoctor(
        nameBn: String, nameEn: String, email: String, pass: String, phone: String,
        bmdc: String, specialtyId: String, hospital: String, designation: String,
        onSuccess: (UserEntity) -> Unit
    ) {
        viewModelScope.launch {
            repo.registerDoctorWithProfile(nameBn, nameEn, email, pass, phone, bmdc, specialtyId, hospital, designation)
                .onSuccess {
                    showMessage(if (currentLang.value == AppLang.BN) "ডাক্তার নিবন্ধন সম্পন্ন! অ্যাডমিন অনুমোদনের অপেক্ষায়।" else "Doctor registered! Pending admin approval.")
                    onSuccess(it)
                }
                .onFailure { showMessage(it.message ?: "Registration failed") }
        }
    }

    fun logout(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.logout()
            showMessage(if (currentLang.value == AppLang.BN) "লগআউট সম্পন্ন" else "Logged out")
            onDone()
        }
    }

    // --- Patient Actions ---
    fun toggleFavorite(doctorId: String) {
        viewModelScope.launch {
            val isFav = doctorId in favoriteDoctorIds.value
            repo.toggleFavorite(currentUser.value?.id, doctorId, isFav)
        }
    }

    fun submitReport(doctorId: String, reason: String, details: String) {
        viewModelScope.launch {
            repo.submitReport(doctorId, currentUser.value?.id, reason, details)
                .onSuccess { showMessage(if (currentLang.value == AppLang.BN) "রিপোর্ট পাঠানো হয়েছে" else "Report submitted") }
                .onFailure { showMessage(it.message ?: "Failed") }
        }
    }

    fun requestAppointment(
        patientName: String, patientPhone: String, doctorId: String,
        chamberId: String, day: String, slot: String, notes: String
    ) {
        viewModelScope.launch {
            repo.requestAppointment(currentUser.value?.id, patientName, patientPhone, doctorId, chamberId, day, slot, notes)
                .onSuccess {
                    showMessage(
                        if (currentLang.value == AppLang.BN)
                            "অ্যাপয়েন্টমেন্ট অনুরোধ পাঠানো হয়েছে (চেম্বারের নিশ্চিতকরণের অপেক্ষায়)।"
                        else "Appointment request sent (Pending chamber confirmation)."
                    )
                }
                .onFailure { showMessage(it.message ?: "Failed") }
        }
    }

    fun updateAppointmentStatus(id: String, status: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.updateAppointmentStatus(id, status, actor.id, actor.role)
            showMessage("Appointment $status")
        }
    }

    // --- Doctor / Admin Profile & Chamber CRUD ---
    fun saveDoctor(doctor: DoctorEntity, isAdminAction: Boolean, onDone: () -> Unit = {}) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.saveDoctorProfile(doctor, actor, isAdminAction)
                .onSuccess {
                    showMessage(if (currentLang.value == AppLang.BN) "তথ্য সংরক্ষিত হয়েছে" else "Doctor profile saved")
                    onDone()
                }
                .onFailure { showMessage(it.message ?: "Failed to save") }
        }
    }

    fun deleteDoctor(doctorId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.deleteDoctor(doctorId, actor); showMessage("Doctor deleted") }
    }

    fun setDoctorApproval(doctorId: String, status: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.setDoctorApproval(doctorId, status, actor); showMessage("Doctor $status") }
    }

    fun setDoctorActive(doctorId: String, active: Boolean) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.setDoctorActive(doctorId, active, actor) }
    }

    fun verifyDoctorBmdc(doctorId: String, verified: Boolean, notes: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.verifyDoctorBmdc(doctorId, verified, notes, actor)
            showMessage(if (verified) "BMDC Verified" else "BMDC Rejected")
        }
    }

    fun submitVerificationRequest(doctorId: String, bmdc: String, notes: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.submitVerificationRequest(doctorId, bmdc, actor.id, notes)
            showMessage(if (currentLang.value == AppLang.BN) "ভেরিফিকেশন অনুরোধ পাঠানো হয়েছে" else "Verification request submitted")
        }
    }

    fun reviewVerificationRequest(req: VerificationRequestEntity, approved: Boolean, notes: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.reviewVerificationRequest(req, approved, notes, actor) }
    }

    fun submitProfileChangeRequest(doctorId: String, summary: String, newDesig: String, newHosp: String, newBio: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.submitProfileChangeRequest(doctorId, actor.id, summary, newDesig, newHosp, newBio)
            showMessage(if (currentLang.value == AppLang.BN) "পরিবর্তন অনুরোধ জমা হয়েছে" else "Change request submitted")
        }
    }

    fun reviewProfileChangeRequest(req: ProfileChangeRequestEntity, approve: Boolean) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.reviewProfileChangeRequest(req, approve, actor) }
    }

    fun addEducation(doctorId: String, degree: String, institution: String, year: String, training: String) {
        viewModelScope.launch {
            repo.addEducation(EducationEntity("edu_${UUID.randomUUID().toString().take(8)}", doctorId, degree, institution, year, training))
            showMessage("Qualification added")
        }
    }

    fun deleteEducation(id: String) = viewModelScope.launch { repo.deleteEducation(id) }

    fun addEmployment(doctorId: String, hospital: String, college: String, desig: String, dept: String, start: String, end: String, desc: String) {
        viewModelScope.launch {
            repo.addEmployment(EmploymentHistoryEntity("emp_${UUID.randomUUID().toString().take(8)}", doctorId, hospital, college, desig, dept, start, end, desc))
            showMessage("Employment history added")
        }
    }

    fun deleteEmployment(id: String) = viewModelScope.launch { repo.deleteEmployment(id) }

    fun saveChamber(chamber: ChamberEntity) {
        viewModelScope.launch {
            repo.saveChamber(chamber)
                .onSuccess { showMessage(if (currentLang.value == AppLang.BN) "চেম্বার সংরক্ষিত" else "Chamber saved") }
                .onFailure { showMessage(it.message ?: "Invalid chamber") }
        }
    }

    fun deleteChamber(id: String) = viewModelScope.launch { repo.deleteChamber(id) }

    fun addSchedule(chamberId: String, doctorId: String, day: DayOfWeekBd, open: String, close: String, label: String) {
        viewModelScope.launch {
            repo.addSchedule(ChamberScheduleEntity("sch_${UUID.randomUUID().toString().take(8)}", chamberId, doctorId, day.name, open, close, label))
            showMessage("Schedule slot added")
        }
    }

    fun deleteSchedule(id: String) = viewModelScope.launch { repo.deleteSchedule(id) }

    // --- Hospitals, Diagnostics, Specialties, Locations ---
    fun saveHospital(h: HospitalEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.saveHospital(h, actor)
                .onSuccess { showMessage("Hospital saved") }
                .onFailure { showMessage(it.message ?: "Error") }
        }
    }

    fun deleteHospital(id: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.deleteHospital(id, actor) }
    }

    fun saveDiagnosticCenter(dc: DiagnosticCenterEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.saveDiagnosticCenter(dc, actor)
                .onSuccess { showMessage("Diagnostic center saved") }
                .onFailure { showMessage(it.message ?: "Error") }
        }
    }

    fun deleteDiagnosticCenter(id: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.deleteDiagnosticCenter(id, actor) }
    }

    fun saveSpecialty(sp: SpecialtyEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.saveSpecialty(sp, actor); showMessage("Specialty saved") }
    }

    fun deleteSpecialty(id: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.deleteSpecialty(id, actor) }
    }

    fun saveLocation(loc: LocationEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.saveLocation(loc, actor); showMessage("Location saved") }
    }

    fun deleteLocation(id: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.deleteLocation(id, actor) }
    }

    fun requestHospitalDoctorLink(hospitalId: String, doctorId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repo.requestHospitalDoctorLink(hospitalId, doctorId, actor.id)
            showMessage("Doctor association request sent")
        }
    }

    fun updateHospitalDoctorLinkStatus(id: String, status: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.updateHospitalDoctorLinkStatus(id, status, actor) }
    }

    fun updateUserRole(user: UserEntity, newRole: UserRole, active: Boolean) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.updateUserRole(user, newRole, active, actor); showMessage("User updated") }
    }

    fun resolveReport(reportId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.resolveReport(reportId, actor); showMessage("Report resolved") }
    }

    fun sendNotification(titleBn: String, titleEn: String, msgBn: String, msgEn: String, targetRole: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.sendNotification(titleBn, titleEn, msgBn, msgEn, targetRole, actor); showMessage("Notification sent") }
    }

    fun markNotificationRead(id: String) = viewModelScope.launch { repo.markNotificationRead(id) }

    fun saveSettings(s: SiteSettingsEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.saveSettings(s, actor); showMessage("Settings saved") }
    }

    fun clearAllDemoData() {
        val actor = currentUser.value ?: return
        viewModelScope.launch { repo.clearAllDemoData(actor); showMessage("All DEMO entries removed") }
    }
}
