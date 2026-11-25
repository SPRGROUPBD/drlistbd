package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.AdvertisingRemoteService
import com.example.data.remote.FavoriteCategory
import com.example.data.repository.DirectoryRepository
import com.example.i18n.AppLang
import com.example.ui.ads.AdCampaignItem
import com.example.ui.ads.AdvertisingRotationEngine
import com.example.ui.ads.AdvertisingSystemSnapshot
import com.example.ui.ads.BottomBannerDisplayItem
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.DynamicAdPackage
import com.example.ui.ads.FairAdRotationEngine
import com.example.ui.ads.ManagedAdvertiserCampaign
import com.example.ui.ads.ResolvedHomeSliderSlide
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.ads.StrictPlacementTypes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repo = DirectoryRepository(application)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w("MainViewModel", "Handled background coroutine error: ${throwable.message}")
        _isSyncing.value = false
    }

    private fun <T> Flow<T>.safeStateIn(started: SharingStarted, initial: T): StateFlow<T> =
        this.catch { err ->
            Log.w("MainViewModel", "Flow error handled gracefully: ${err.message}")
            emit(initial)
        }.stateIn(viewModelScope, started, initial)

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val syncMutex = Mutex()
    private val _syncUiState = MutableStateFlow(SyncUiState())
    val syncUiState: StateFlow<SyncUiState> = combine(
        _syncUiState,
        repo.prefs.lastServerSyncAtMs.catch { emit(0L) },
        ConnectivityMonitor.isOnline
    ) { state, persistedSyncMs, online ->
        state.copy(
            lastSuccessAtMs = maxOf(state.lastSuccessAtMs, persistedSyncMs),
            isOnline = online
        )
    }.safeStateIn(SharingStarted.Eagerly, SyncUiState())

    private val _searchState = MutableStateFlow(SearchFilterState())
    val searchState: StateFlow<SearchFilterState> = _searchState.asStateFlow()

    private val _visiblePageCount = MutableStateFlow(10)
    val visiblePageCount: StateFlow<Int> = _visiblePageCount.asStateFlow()

    val currentLang: StateFlow<AppLang> = repo.prefs.currentLang
        .safeStateIn(SharingStarted.Eagerly, AppLang.BN)

    val isDarkTheme: StateFlow<Boolean> = repo.prefs.isDarkTheme
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.Eagerly, false)

    val selectedHomeDistrict: StateFlow<String?> = repo.prefs.selectedHomeDistrict
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.Eagerly, null)

    val settings: StateFlow<SiteSettingsEntity> = repo.settings
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.Eagerly, SiteSettingsEntity(siteTitle = "Doctor List BD", showDemoData = false))

    val adConfig: StateFlow<AdConfigEntity> = repo.adConfig
        .safeStateIn(SharingStarted.Eagerly, AdConfigEntity())

    val sponsors: StateFlow<List<SponsorEntity>> = repo.sponsors
        .safeStateIn(SharingStarted.Eagerly, emptyList())

    val sponsorPackages: StateFlow<List<SponsorPackageEntity>> = repo.sponsorPackages
        .safeStateIn(SharingStarted.Eagerly, emptyList())

    val placementRateCards: StateFlow<List<PlacementRateCardEntity>> = repo.placementRateCards
        .safeStateIn(SharingStarted.Eagerly, emptyList())

    val rateCardSettings: StateFlow<RateCardSettingsEntity> = repo.rateCardSettings
        .safeStateIn(SharingStarted.Eagerly, RateCardSettingsEntity())

    val paymentSettings: StateFlow<PaymentSettingsEntity> = repo.paymentSettings
        .safeStateIn(SharingStarted.Eagerly, PaymentSettingsEntity())

    val lastServerSyncAtMs: StateFlow<Long> = repo.prefs.lastServerSyncAtMs
        .safeStateIn(SharingStarted.Eagerly, 0L)

    val pendingViewEventsCount: StateFlow<Int> = repo.pendingViewEventsCount
        .safeStateIn(SharingStarted.WhileSubscribed(5000), 0)

    val advertiserSession: StateFlow<AdvertiserSession> = repo.prefs.advertiserSession
        .safeStateIn(SharingStarted.Eagerly, AdvertiserSession())

    companion object {
        private const val LOCAL_VISITOR_UID = "local_device"
    }

    // --- Local Device Favorites (No Sign-In Required) ---
    val favoriteDoctorIds: StateFlow<Set<String>> = repo.observeFavoritesForUser(LOCAL_VISITOR_UID)
        .safeStateIn(SharingStarted.WhileSubscribed(5000), emptySet())

    private val _favoriteHospitalIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteHospitalIds: StateFlow<Set<String>> = _favoriteHospitalIds.asStateFlow()

    private val _favoriteDiagnosticIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteDiagnosticIds: StateFlow<Set<String>> = _favoriteDiagnosticIds.asStateFlow()

    private val _favoriteChamberIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteChamberIds: StateFlow<Set<String>> = _favoriteChamberIds.asStateFlow()

    // --- Complete Advertisement & Advertiser Management System State ---
    private val _adSnapshot = MutableStateFlow(AdvertisingSystemSnapshot())
    val adSnapshot: StateFlow<AdvertisingSystemSnapshot> = _adSnapshot.asStateFlow()

    private val _dynamicPackages = MutableStateFlow<List<DynamicAdPackage>>(emptyList())
    val dynamicPackages: StateFlow<List<DynamicAdPackage>> = _dynamicPackages.asStateFlow()

    private val _managedCampaigns = MutableStateFlow<List<ManagedAdvertiserCampaign>>(emptyList())
    val managedCampaigns: StateFlow<List<ManagedAdvertiserCampaign>> = _managedCampaigns.asStateFlow()

    private val _authoritativeNowMs = MutableStateFlow(System.currentTimeMillis())
    val authoritativeNowMs: StateFlow<Long> = _authoritativeNowMs.asStateFlow()

    @Volatile
    private var hasEvaluatedAppOpenPopupThisSession: Boolean = false

    val isOnline: StateFlow<Boolean> = ConnectivityMonitor.isOnline

    val lastAppOpenPopupDate: StateFlow<String> = repo.prefs.lastAppOpenPopupDate
        .safeStateIn(SharingStarted.Eagerly, "")

    val specialties: StateFlow<List<SpecialtyEntity>> = repo.specialties
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.Eagerly, emptyList())

    val locations: StateFlow<List<LocationEntity>> = repo.locations
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.Eagerly, emptyList())

    val hospitals: StateFlow<List<HospitalEntity>> = repo.hospitals
        .map { list -> list.filter { !it.isDemoData && it.status == "ACTIVE" } }
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.WhileSubscribed(5000), emptyList())

    val diagnosticCenters: StateFlow<List<DiagnosticCenterEntity>> = repo.diagnosticCenters
        .map { list -> list.filter { !it.isDemoData && it.status == "ACTIVE" } }
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.WhileSubscribed(5000), emptyList())

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
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.WhileSubscribed(5000), emptyList())

    private val debouncedSearch: Flow<SearchFilterState> = _searchState.debounce(180)

    val filteredDoctors: StateFlow<List<DoctorFullProfile>> = combine(
        publicDoctors,
        debouncedSearch,
        locations
    ) { docs, filter, locs ->
        val q = filter.query.trim().lowercase()
        val subQ = filter.subSpecialty.trim().lowercase()
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
    }.safeStateIn(SharingStarted.WhileSubscribed(5000), emptyList())

    val appointments: StateFlow<List<AppointmentRequestEntity>> = repo.appointments
        .safeStateIn(SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repo.notifications
        .distinctUntilChanged()
        .safeStateIn(SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load locally saved facility/chamber favorites immediately without requiring sign-in
        viewModelScope.launch(exceptionHandler) {
            runCatching {
                val (cachedHosp, cachedDiag, cachedCham) = repo.prefs.loadCachedFacilityFavorites(LOCAL_VISITOR_UID)
                _favoriteHospitalIds.value = cachedHosp
                _favoriteDiagnosticIds.value = cachedDiag
                _favoriteChamberIds.value = cachedCham
            }
        }

        ConnectivityMonitor.start(application) {
            // Automatically re-sync directory, notifications, ads & favorites on reconnect
            performFirebaseSync(isInitialBootstrap = false, showToast = false)
        }

        // Initial startup sync
        performFirebaseSync(isInitialBootstrap = true, showToast = false)

        // Periodic automatic background sync while app is alive & online (every 3 minutes)
        viewModelScope.launch(exceptionHandler) {
            while (true) {
                delay(180_000L)
                if (ConnectivityMonitor.isOnline.value && !_isSyncing.value) {
                    performFirebaseSync(isInitialBootstrap = false, showToast = false)
                }
            }
        }

        // Real-time lightweight Advertisement & AdMob config refresh (every 10s without touching Auto Sync spinner)
        viewModelScope.launch(exceptionHandler) {
            while (true) {
                delay(10_000L)
                if (ConnectivityMonitor.isOnline.value && !_isSyncing.value) {
                    refreshDynamicAdSystem()
                }
            }
        }
    }

    fun triggerForegroundAutoSyncIfStale() {
        val lastMs = syncUiState.value.lastSuccessAtMs
        val ageMs = System.currentTimeMillis() - lastMs
        if (ConnectivityMonitor.isOnline.value && !_isSyncing.value && (lastMs == 0L || ageMs > 60_000L)) {
            performFirebaseSync(isInitialBootstrap = false, showToast = false)
        }
    }

    fun syncNow(showToast: Boolean = true) {
        performFirebaseSync(isInitialBootstrap = false, showToast = showToast)
    }

    private fun performFirebaseSync(isInitialBootstrap: Boolean, showToast: Boolean) {
        viewModelScope.launch(exceptionHandler) {
            // Skip duplicate overlapping sync requests so the button never re-spins unnecessarily
            if (!syncMutex.tryLock()) return@launch
            try {
                _isSyncing.value = true
                _syncUiState.update { it.copy(status = AutoSyncStatus.SYNCING, lastError = null) }
                ConnectivityMonitor.refreshNow(getApplication())
                if (isInitialBootstrap) {
                    val savedCounters = repo.prefs.loadRotationCounters()
                    FairAdRotationEngine.seedCounters(savedCounters)
                    AdvertisingRotationEngine.seedCounters(savedCounters)
                    _authoritativeNowMs.value = repo.prefs.getAuthoritativeNowMs()
                }
                val result = if (isInitialBootstrap) {
                    repo.initializeAndSync()
                } else {
                    repo.syncFromAdminCloud()
                }
                refreshDynamicAdSystem()
                val latestAuthNow = repo.prefs.getAuthoritativeNowMs()
                if (kotlin.math.abs(latestAuthNow - _authoritativeNowMs.value) >= 60_000L) {
                    _authoritativeNowMs.value = latestAuthNow
                }

                if (result.isSuccess) {
                    val count = result.getOrDefault(0)
                    val nowMs = System.currentTimeMillis()
                    _syncUiState.update {
                        it.copy(
                            status = AutoSyncStatus.SUCCESS,
                            lastSuccessAtMs = maxOf(it.lastSuccessAtMs, nowMs),
                            lastError = null,
                            syncedDoctorsCount = count
                        )
                    }
                    if (showToast) {
                        showMessage(
                            if (currentLang.value == AppLang.BN) "ডিরেক্টরি আপডেট হয়েছে ($count জন ডাক্তার)"
                            else "Firebase synced ($count doctors)"
                        )
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.message
                        ?: if (!ConnectivityMonitor.isOnline.value) "No internet connection" else "Sync failed"
                    if (isInitialBootstrap && !ConnectivityMonitor.isOnline.value) {
                        repo.prefs.recordLocalOfflineSession()
                    }
                    _syncUiState.update {
                        it.copy(
                            status = AutoSyncStatus.FAILURE,
                            lastError = errMsg
                        )
                    }
                    if (showToast) {
                        showMessage(
                            if (currentLang.value == AppLang.BN) "সিঙ্ক ব্যর্থ হয়েছে — লোকাল ডাটা দেখানো হচ্ছে"
                            else "Sync failed — showing cached data"
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w("MainViewModel", "Firebase sync warning: ${t.message}")
                _syncUiState.update {
                    it.copy(
                        status = AutoSyncStatus.FAILURE,
                        lastError = t.message ?: "Unexpected sync error"
                    )
                }
                if (showToast) {
                    showMessage(
                        if (currentLang.value == AppLang.BN) "সিঙ্ক ব্যর্থ হয়েছে — লোকাল ডাটা দেখানো হচ্ছে"
                        else "Sync failed — showing cached data"
                    )
                }
            } finally {
                _isSyncing.value = false
                syncMutex.unlock()
            }
        }
    }

    fun refreshAdvertisingSystem() {
        viewModelScope.launch(exceptionHandler) {
            refreshDynamicAdSystem()
        }
    }

    private suspend fun refreshDynamicAdSystem() {
        if (_dynamicPackages.value.isNotEmpty()) _dynamicPackages.value = emptyList()
        if (_managedCampaigns.value.isNotEmpty()) _managedCampaigns.value = emptyList()
        AdvertisingRemoteService.fetchPublicSnapshot()
            .onSuccess { snapshot ->
                val mergedCounters = repo.prefs.loadRotationCounters() +
                    AdvertisingRotationEngine.snapshotCounters()
                repo.prefs.saveRotationCounters(mergedCounters)
                if (_adSnapshot.value != snapshot) {
                    _adSnapshot.value = snapshot
                }
            }
            .onFailure { err ->
                Log.w("MainViewModel", "Advertising snapshot sync warning: ${err.message}")
            }
    }

    fun requestToggleFavorite(category: FavoriteCategory, itemId: String, label: String = "") {
        val cleanId = itemId.trim()
        if (cleanId.isEmpty()) return
        viewModelScope.launch(exceptionHandler) {
            executeLocalFavoriteToggle(LOCAL_VISITOR_UID, category, cleanId)
        }
    }

    private suspend fun executeLocalFavoriteToggle(
        uid: String,
        category: FavoriteCategory,
        itemId: String
    ) {
        when (category) {
            FavoriteCategory.DOCTOR -> {
                val currentlyFav = itemId in favoriteDoctorIds.value
                repo.toggleFavoriteForUser(uid, itemId, currentlyFav)
            }
            FavoriteCategory.HOSPITAL -> {
                val cur = _favoriteHospitalIds.value
                val next = if (itemId in cur) cur - itemId else cur + itemId
                _favoriteHospitalIds.value = next
                repo.prefs.saveCachedFacilityFavorites(uid, next, _favoriteDiagnosticIds.value, _favoriteChamberIds.value)
            }
            FavoriteCategory.DIAGNOSTIC -> {
                val cur = _favoriteDiagnosticIds.value
                val next = if (itemId in cur) cur - itemId else cur + itemId
                _favoriteDiagnosticIds.value = next
                repo.prefs.saveCachedFacilityFavorites(uid, _favoriteHospitalIds.value, next, _favoriteChamberIds.value)
            }
            FavoriteCategory.CHAMBER -> {
                val cur = _favoriteChamberIds.value
                val next = if (itemId in cur) cur - itemId else cur + itemId
                _favoriteChamberIds.value = next
                repo.prefs.saveCachedFacilityFavorites(uid, _favoriteHospitalIds.value, _favoriteDiagnosticIds.value, next)
            }
        }
    }

    // --- Complete Advertisement & Fair Rotation Helpers ---
    fun getAdvertisingQueueBase(queueKey: String): Long =
        AdvertisingRotationEngine.currentCounter(queueKey)

    fun recordHomeSliderVisitAdvance(firstAdvertiserCampaignId: String = "") {
        val nextVal = AdvertisingRotationEngine.advanceCounter(AdvertisingRotationEngine.QUEUE_HOME_SLIDER)
        viewModelScope.launch(exceptionHandler) {
            val merged = repo.prefs.loadRotationCounters() + AdvertisingRotationEngine.snapshotCounters()
            repo.prefs.saveRotationCounters(merged)
            if (ConnectivityMonitor.isOnline.value) {
                AdvertisingRemoteService.atomicAdvanceRotationQueue(
                    queueKey = AdvertisingRotationEngine.QUEUE_HOME_SLIDER,
                    lastCampaignId = firstAdvertiserCampaignId,
                    fallbackLocalValue = nextVal
                )
            }
        }
    }

    fun selectBottomBanner(): AdCampaignItem? {
        val snap = _adSnapshot.value
        return AdvertisingRotationEngine.selectBottomBanner(
            campaigns = snap.campaigns,
            nowMs = _authoritativeNowMs.value,
            settings = snap.placementSettings
        )
    }

    fun tryConsumeSessionLaunchPopup(): AdCampaignItem? {
        if (hasEvaluatedAppOpenPopupThisSession) return null
        val snap = _adSnapshot.value
        if (!snap.syncedFromServer) return null
        hasEvaluatedAppOpenPopupThisSession = true
        return selectLaunchPopupOnce()
    }

    fun selectLaunchPopupOnce(): AdCampaignItem? {
        val snap = _adSnapshot.value
        val result = AdvertisingRotationEngine.selectNextAppOpenPopup(
            campaigns = snap.campaigns,
            nowMs = _authoritativeNowMs.value,
            settings = snap.placementSettings
        ) ?: return null
        val (chosen, nextVal) = result
        viewModelScope.launch(exceptionHandler) {
            val merged = repo.prefs.loadRotationCounters() + AdvertisingRotationEngine.snapshotCounters()
            repo.prefs.saveRotationCounters(merged)
            if (ConnectivityMonitor.isOnline.value) {
                AdvertisingRemoteService.atomicAdvanceRotationQueue(
                    queueKey = AdvertisingRotationEngine.QUEUE_APP_OPEN_POPUP,
                    lastCampaignId = chosen.id,
                    fallbackLocalValue = nextVal
                )
            }
        }
        return chosen
    }

    fun logBottomBannerImpression(item: BottomBannerDisplayItem) {
        if (item.isAdMobBanner) return
        val sourceCamp = item.sourceCampaign
        if (sourceCamp != null) {
            logAdvertisingImpression(sourceCamp, AdvertisingRotationEngine.QUEUE_BOTTOM_BANNER)
            return
        }
        if (item.isAdminPromo) {
            val rawId = item.id.removePrefix("admin_bb_")
            viewModelScope.launch(exceptionHandler) {
                if (ConnectivityMonitor.isOnline.value) {
                    AdvertisingRemoteService.logBottomBannerImpression(rawId)
                }
            }
        }
    }

    fun logBottomBannerClick(item: BottomBannerDisplayItem) {
        if (item.isAdMobBanner) return
        val sourceCamp = item.sourceCampaign
        if (sourceCamp != null) {
            logAdvertisingClick(sourceCamp, AdvertisingRotationEngine.QUEUE_BOTTOM_BANNER)
            return
        }
        if (item.isAdminPromo) {
            val rawId = item.id.removePrefix("admin_bb_")
            viewModelScope.launch(exceptionHandler) {
                if (ConnectivityMonitor.isOnline.value) {
                    AdvertisingRemoteService.logBottomBannerClick(rawId)
                }
            }
        }
    }

    fun advanceNativePlacementQueue(queueKey: String, campaignId: String) {
        val nextVal = AdvertisingRotationEngine.advanceCounter(queueKey)
        viewModelScope.launch(exceptionHandler) {
            val merged = repo.prefs.loadRotationCounters() + AdvertisingRotationEngine.snapshotCounters()
            repo.prefs.saveRotationCounters(merged)
            if (ConnectivityMonitor.isOnline.value) {
                AdvertisingRemoteService.atomicAdvanceRotationQueue(
                    queueKey = queueKey,
                    lastCampaignId = campaignId,
                    fallbackLocalValue = nextVal
                )
            }
        }
    }

    fun logAdvertisingImpression(campaign: AdCampaignItem, queueKey: String) {
        _adSnapshot.update { cur ->
            cur.copy(
                campaigns = cur.campaigns.map { c ->
                    if (c.id == campaign.id) c.copy(impressionsCount = c.impressionsCount + 1L) else c
                }
            )
        }
        viewModelScope.launch(exceptionHandler) {
            if (ConnectivityMonitor.isOnline.value) {
                AdvertisingRemoteService.logImpression(campaign, queueKey)
            }
        }
    }

    fun logAdvertisingClick(campaign: AdCampaignItem, queueKey: String) {
        _adSnapshot.update { cur ->
            cur.copy(
                campaigns = cur.campaigns.map { c ->
                    if (c.id == campaign.id) c.copy(clicksCount = c.clicksCount + 1L) else c
                }
            )
        }
        viewModelScope.launch(exceptionHandler) {
            if (ConnectivityMonitor.isOnline.value) {
                AdvertisingRemoteService.logClick(campaign, queueKey)
            }
        }
    }

    fun logHomeSliderSlideImpression(slide: ResolvedHomeSliderSlide) {
        if (slide.isCustomAdminSlider || slide.campaignId.isBlank()) return
        val camp = _adSnapshot.value.campaigns.firstOrNull { it.id == slide.campaignId } ?: return
        logAdvertisingImpression(camp, AdvertisingRotationEngine.QUEUE_HOME_SLIDER)
    }

    fun logHomeSliderSlideClick(slide: ResolvedHomeSliderSlide) {
        if (slide.isCustomAdminSlider || slide.campaignId.isBlank()) return
        val camp = _adSnapshot.value.campaigns.firstOrNull { it.id == slide.campaignId } ?: return
        logAdvertisingClick(camp, AdvertisingRotationEngine.QUEUE_HOME_SLIDER)
    }

    // Legacy compatibility hooks kept as safe no-ops
    fun selectFairBannerForScreen(screenId: String): ManagedAdvertiserCampaign? = null
    fun selectFairLaunchPopup(): ManagedAdvertiserCampaign? = null
    fun getNativeRotationBase(screenId: String): Long =
        FairAdRotationEngine.currentCounter(StrictPlacementTypes.NATIVE_300x200, screenId)
    fun logFairAdImpression(campaign: ManagedAdvertiserCampaign, screenId: String) = Unit
    fun logFairAdClick(campaign: ManagedAdvertiserCampaign, screenId: String) = Unit
    fun recordForegroundActivity() = Unit

    fun markAppOpenPopupShown() {
        viewModelScope.launch(exceptionHandler) {
            val now = repo.prefs.getAuthoritativeNowMs()
            _authoritativeNowMs.value = now
            repo.prefs.setLastAppOpenPopupDate(SponsorCampaignSelector.todayDateKey(now))
        }
    }

    fun recordSponsorDisplay(sponsorId: String, onlineNow: Boolean, slotKey: String = "banner") = Unit
    fun recordSponsorClick(sponsorId: String, onlineNow: Boolean) = Unit

    fun saveAdvertiserSession(session: AdvertiserSession) {
        viewModelScope.launch(exceptionHandler) { repo.prefs.saveAdvertiserSession(session) }
    }

    fun logoutAdvertiser(context: Context? = null) {
        viewModelScope.launch(exceptionHandler) { repo.prefs.clearAdvertiserSession() }
    }

    fun refreshDirectory() {
        syncNow(showToast = true)
    }

    fun clearSnackbar() { _snackbar.value = null }
    fun showMessage(msg: String) { _snackbar.value = msg }

    fun toggleLanguage() {
        viewModelScope.launch(exceptionHandler) {
            val next = if (currentLang.value == AppLang.BN) AppLang.EN else AppLang.BN
            repo.prefs.setLanguage(next)
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch(exceptionHandler) {
            repo.prefs.setDarkTheme(isDark)
        }
    }

    fun setSelectedHomeDistrict(district: String?) {
        viewModelScope.launch(exceptionHandler) {
            repo.prefs.setSelectedHomeDistrict(district)
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
        val doc = publicDoctors.value.firstOrNull { it.doctor.id == doctorId }
        val label = doc?.doctor?.fullNameEn?.ifBlank { doc.doctor.fullNameBn } ?: doctorId
        requestToggleFavorite(FavoriteCategory.DOCTOR, doctorId, label)
    }

    fun toggleHospitalFavorite(hospitalId: String, label: String = "") {
        requestToggleFavorite(FavoriteCategory.HOSPITAL, hospitalId, label)
    }

    fun toggleDiagnosticFavorite(diagnosticId: String, label: String = "") {
        requestToggleFavorite(FavoriteCategory.DIAGNOSTIC, diagnosticId, label)
    }

    fun toggleChamberFavorite(chamberId: String, label: String = "") {
        requestToggleFavorite(FavoriteCategory.CHAMBER, chamberId, label)
    }

    fun submitReport(doctorId: String, reason: String, details: String) {
        viewModelScope.launch(exceptionHandler) {
            repo.submitReport(doctorId, reason, details)
                .onSuccess { showMessage(if (currentLang.value == AppLang.BN) "রিপোর্ট পাঠানো হয়েছে" else "Report submitted") }
                .onFailure { showMessage(it.message ?: "Failed") }
        }
    }

    fun requestAppointment(
        patientName: String, patientPhone: String, doctorId: String,
        chamberId: String, day: String, slot: String, notes: String
    ) {
        viewModelScope.launch(exceptionHandler) {
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

    fun markNotificationRead(id: String) = viewModelScope.launch(exceptionHandler) { repo.markNotificationRead(id) }

    fun markAllNotificationsRead() = viewModelScope.launch(exceptionHandler) { repo.markAllNotificationsRead() }
}
