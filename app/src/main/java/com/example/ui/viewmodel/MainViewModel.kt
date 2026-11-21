package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.analytics.AppInstallationTracker
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.DirectoryRepository
import com.example.i18n.AppLang
import com.example.ui.ads.AdRevenueTracker
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.SponsorCampaignSelector
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repo = DirectoryRepository(application)

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _searchState = MutableStateFlow(SearchFilterState())
    val searchState: StateFlow<SearchFilterState> = _searchState.asStateFlow()

    private val _visiblePageCount = MutableStateFlow(10)
    val visiblePageCount: StateFlow<Int> = _visiblePageCount.asStateFlow()

    val currentLang: StateFlow<AppLang> = repo.prefs.currentLang
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLang.BN)

    val settings: StateFlow<SiteSettingsEntity> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SiteSettingsEntity(siteTitle = "Doctor List BD", showDemoData = false))

    val adConfig: StateFlow<AdConfigEntity> = repo.adConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, AdConfigEntity())

    val sponsors: StateFlow<List<SponsorEntity>> = repo.sponsors
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sponsorPackages: StateFlow<List<SponsorPackageEntity>> = repo.sponsorPackages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val placementRateCards: StateFlow<List<PlacementRateCardEntity>> = repo.placementRateCards
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rateCardSettings: StateFlow<RateCardSettingsEntity> = repo.rateCardSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, RateCardSettingsEntity())

    val paymentSettings: StateFlow<PaymentSettingsEntity> = repo.paymentSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, PaymentSettingsEntity())

    val screenAdPlacements: StateFlow<List<ScreenAdPlacementEntity>> = repo.screenAdPlacements
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lastServerSyncAtMs: StateFlow<Long> = repo.prefs.lastServerSyncAtMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val pendingViewEventsCount: StateFlow<Int> = repo.pendingViewEventsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val advertiserSession: StateFlow<AdvertiserSession> = repo.prefs.advertiserSession
        .stateIn(viewModelScope, SharingStarted.Eagerly, AdvertiserSession())

    private val _authoritativeNowMs = MutableStateFlow(System.currentTimeMillis())
    val authoritativeNowMs: StateFlow<Long> = _authoritativeNowMs.asStateFlow()

    val isOnline: StateFlow<Boolean> = ConnectivityMonitor.isOnline

    val lastAppOpenPopupDate: StateFlow<String> = repo.prefs.lastAppOpenPopupDate
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val specialties: StateFlow<List<SpecialtyEntity>> = repo.specialties
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val locations: StateFlow<List<LocationEntity>> = repo.locations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hospitals: StateFlow<List<HospitalEntity>> = repo.hospitals
        .map { list -> list.filter { !it.isDemoData && it.status == "ACTIVE" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diagnosticCenters: StateFlow<List<DiagnosticCenterEntity>> = repo.diagnosticCenters
        .map { list -> list.filter { !it.isDemoData && it.status == "ACTIVE" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publicDoctors: StateFlow<List<DoctorFullProfile>> = repo.doctorsFull
        .map { list ->
            list.filter { d ->
                d.doctor.isActive &&
                    d.doctor.approvalStatus == "APPROVED" &&
                    !d.doctor.isDemoData
            }.map { d ->
                // Only Active Chambers are visible in the patient app
                d.copy(chambers = d.chambers.filter { it.chamber.isActive })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                d.qualification.lowercase().contains(q) ||
                d.subSpecialty.lowercase().contains(q) ||
                d.currentDesignation.lowercase().contains(q) ||
                d.currentHospital.lowercase().contains(q) ||
                (item.specialty?.nameBn?.lowercase()?.contains(q) == true) ||
                (item.specialty?.nameEn?.lowercase()?.contains(q) == true) ||
                item.chambers.any {
                    it.chamber.facilityName.lowercase().contains(q) ||
                        it.chamber.chamberName.lowercase().contains(q) ||
                        it.chamber.district.lowercase().contains(q) ||
                        it.chamber.upazila.lowercase().contains(q)
                }

            val matchesSpec = filter.specialtyId == null || d.mainSpecialtyId == filter.specialtyId
            val matchesSub = subQ.isEmpty() || d.subSpecialty.lowercase().contains(subQ) || d.expertise.lowercase().contains(subQ)
            val matchesDiv = divDistricts == null || item.chambers.any { it.chamber.district.lowercase() in divDistricts }
            val matchesDistrict = filter.district == null || item.chambers.any { it.chamber.district.equals(filter.district, true) }
            val matchesUpazila = filter.upazila == null || item.chambers.any { it.chamber.upazila.equals(filter.upazila, true) }
            val matchesFacility = (filter.facilityQuery.isBlank() && filter.facilityAltQuery.isBlank()) ||
                FacilityDoctorMatcher.matchesFacility(item, filter.facilityQuery, filter.facilityAltQuery)
            val matchesDay = filter.weekday == null || item.isAvailableOn(filter.weekday)
            val matchesToday = !filter.availableTodayOnly || item.isAvailableToday()
            val matchesWork = workQ.isEmpty() || d.currentHospital.lowercase().contains(workQ) || d.currentMedicalCollege.lowercase().contains(workQ)

            matchesQuery && matchesSpec && matchesSub && matchesDiv && matchesDistrict &&
                matchesUpazila && matchesFacility && matchesDay && matchesToday && matchesWork
        }

        when (filter.sortOption) {
            SortOption.NAME_AZ -> filtered.sortedBy { it.doctor.fullNameEn.lowercase() }
            SortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.doctor.updatedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDoctorIds: StateFlow<Set<String>> = repo.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val appointments: StateFlow<List<AppointmentRequestEntity>> = repo.appointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repo.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        ConnectivityMonitor.start(application) {
            // Automatically re-sync Direct Sponsors, packages, settings & flush queued offline view & installation events
            viewModelScope.launch {
                _isSyncing.value = true
                repo.syncFromAdminCloud()
                _authoritativeNowMs.value = repo.prefs.getAuthoritativeNowMs()
                AdRevenueTracker.flushQueuedViewEvents(repo.db)
                AdRevenueTracker.flushOfflineLocalCounters(repo.prefs)
                AppInstallationTracker.syncPendingEvents(application, repo.db)
                _isSyncing.value = false
            }
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _authoritativeNowMs.value = repo.prefs.getAuthoritativeNowMs()
            AppInstallationTracker.trackAppLaunch(application, repo.db)
            repo.initializeAndSync()
            _authoritativeNowMs.value = repo.prefs.getAuthoritativeNowMs()
            AppInstallationTracker.syncPendingEvents(application, repo.db, _authoritativeNowMs.value)
            if (ConnectivityMonitor.isOnline.value) {
                AdRevenueTracker.trackOnlineSession()
                AdRevenueTracker.flushQueuedViewEvents(repo.db)
                AdRevenueTracker.flushOfflineLocalCounters(repo.prefs)
            } else {
                repo.prefs.recordLocalOfflineSession()
            }
            _isSyncing.value = false
        }
    }

    fun recordForegroundActivity() {
        viewModelScope.launch {
            val now = repo.prefs.getAuthoritativeNowMs()
            AppInstallationTracker.recordForegroundActivity(getApplication(), repo.db, now)
        }
    }

    fun markAppOpenPopupShown() {
        viewModelScope.launch {
            val now = repo.prefs.getAuthoritativeNowMs()
            _authoritativeNowMs.value = now
            repo.prefs.setLastAppOpenPopupDate(SponsorCampaignSelector.todayDateKey(now))
        }
    }

    fun recordSponsorDisplay(sponsorId: String, onlineNow: Boolean, slotKey: String = "banner") {
        viewModelScope.launch {
            val now = repo.prefs.getAuthoritativeNowMs()
            _authoritativeNowMs.value = now
            val sp = sponsors.value.firstOrNull { it.id == sponsorId }
            // Never record impressions for expired ads
            if (sp != null && !SponsorCampaignSelector.isCampaignValid(sp, now)) return@launch
            if (!onlineNow) {
                repo.prefs.recordLocalOfflineSponsorDisplay()
            }
            AdRevenueTracker.recordVisibleImpression(
                db = repo.db,
                prefs = repo.prefs,
                adId = sponsorId,
                packageId = sp?.packageId.orEmpty(),
                slotKey = slotKey,
                isOnline = onlineNow,
                nowMs = now
            )
        }
    }

    fun recordSponsorClick(sponsorId: String, onlineNow: Boolean) {
        viewModelScope.launch {
            val now = repo.prefs.getAuthoritativeNowMs()
            val sp = sponsors.value.firstOrNull { it.id == sponsorId }
            AdRevenueTracker.recordSponsorClick(
                db = repo.db,
                prefs = repo.prefs,
                adId = sponsorId,
                packageId = sp?.packageId.orEmpty(),
                isOnline = onlineNow,
                nowMs = now
            )
        }
    }

    fun saveAdvertiserSession(session: AdvertiserSession) {
        viewModelScope.launch { repo.prefs.saveAdvertiserSession(session) }
    }

    fun logoutAdvertiser() {
        viewModelScope.launch { repo.prefs.clearAdvertiserSession() }
    }

    fun refreshDirectory() {
        viewModelScope.launch {
            _isSyncing.value = true
            ConnectivityMonitor.refreshNow(getApplication())
            repo.syncFromAdminCloud()
                .onSuccess { count ->
                    showMessage(
                        if (currentLang.value == AppLang.BN) "ডিরেক্টরি আপডেট হয়েছে ($count জন ডাক্তার)"
                        else "Directory synced ($count doctors)"
                    )
                }
                .onFailure {
                    showMessage(
                        if (currentLang.value == AppLang.BN) "লোকাল ডাটাবেজ থেকে দেখানো হচ্ছে"
                        else "Showing cached local Room database"
                    )
                }
            _isSyncing.value = false
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

    fun selectFacility(nameEn: String, nameBn: String = "") {
        val cleanEn = nameEn.removePrefix("[DEMO] ").trim()
        val cleanBn = nameBn.removePrefix("[DEMO] ").trim()
        _searchState.value = SearchFilterState(
            facilityQuery = cleanEn.ifBlank { cleanBn },
            facilityAltQuery = if (cleanEn.isNotBlank() && !cleanBn.equals(cleanEn, ignoreCase = true)) cleanBn else ""
        )
        _visiblePageCount.value = 10
    }

    fun resetFilters() {
        _searchState.value = SearchFilterState()
        _visiblePageCount.value = 10
    }

    fun loadMoreDoctors() {
        _visiblePageCount.update { it + 10 }
    }

    fun toggleFavorite(doctorId: String) {
        viewModelScope.launch {
            val isFav = doctorId in favoriteDoctorIds.value
            repo.toggleFavorite(doctorId, isFav)
        }
    }

    fun submitReport(doctorId: String, reason: String, details: String) {
        viewModelScope.launch {
            repo.submitReport(doctorId, reason, details)
                .onSuccess { showMessage(if (currentLang.value == AppLang.BN) "রিপোর্ট পাঠানো হয়েছে" else "Report submitted") }
                .onFailure { showMessage(it.message ?: "Failed") }
        }
    }

    fun requestAppointment(
        patientName: String, patientPhone: String, doctorId: String,
        chamberId: String, day: String, slot: String, notes: String
    ) {
        viewModelScope.launch {
            repo.requestAppointment(patientName, patientPhone, doctorId, chamberId, day, slot, notes)
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

    fun markNotificationRead(id: String) = viewModelScope.launch { repo.markNotificationRead(id) }
}
