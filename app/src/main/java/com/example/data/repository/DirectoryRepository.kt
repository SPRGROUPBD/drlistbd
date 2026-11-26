package com.example.data.repository

import android.content.Context
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.RtdbSyncService
import com.example.data.seed.SeedData
import com.example.notifications.PushNotificationManager
import kotlinx.coroutines.flow.*
import java.util.UUID

class DirectoryRepository(context: Context) {
    private val appContext = context.applicationContext ?: context
    val db = AppDatabase.getInstance(appContext)
    val prefs = SessionPreferences(appContext)

    suspend fun initializeAndSync(): Result<Int> {
        SeedData.ensureCleanBootstrap(db)
        return RtdbSyncService.pullDirectoryIntoRoom(db, appContext)
    }

    suspend fun syncFromAdminCloud(): Result<Int> {
        return RtdbSyncService.pullDirectoryIntoRoom(db, appContext)
    }

    suspend fun forceSyncDoctorFromCloud(doctorId: String): Boolean {
        RtdbSyncService.forcePullDirectoryIntoRoom(db, appContext)
        val found = db.doctorDao().getById(doctorId.trim())
        return found != null && found.isActive
    }

    val specialties: Flow<List<SpecialtyEntity>> = db.specialtyDao().observeAll()
    val locations: Flow<List<LocationEntity>> = db.locationDao().observeAll()
    val hospitals: Flow<List<HospitalEntity>> = db.hospitalDao().observeAll()
    val diagnosticCenters: Flow<List<DiagnosticCenterEntity>> = db.diagnosticCenterDao().observeAll()
    val appointments: Flow<List<AppointmentRequestEntity>> = db.appointmentDao().observeAll()
    val notifications: Flow<List<NotificationEntity>> = db.notificationDao().observeAll()
    val settings: Flow<SiteSettingsEntity> = db.settingsDao().observe().map {
        it ?: SiteSettingsEntity(siteTitle = "Doctor List BD", showDemoData = false)
    }
    val adConfig: Flow<AdConfigEntity> = db.adConfigDao().observe().map {
        it ?: AdConfigEntity()
    }
    val sponsors: Flow<List<SponsorEntity>> = db.sponsorDao().observeAll()
    val sponsorPackages: Flow<List<SponsorPackageEntity>> = db.sponsorPackageDao().observeAll()
    val placementRateCards: Flow<List<PlacementRateCardEntity>> = db.placementRateCardDao().observeAll()
    val rateCardSettings: Flow<RateCardSettingsEntity> = db.rateCardSettingsDao().observe().map {
        it ?: RateCardSettingsEntity()
    }
    val paymentSettings: Flow<PaymentSettingsEntity> = db.paymentSettingsDao().observe().map {
        it ?: PaymentSettingsEntity()
    }
    val pendingViewEventsCount: Flow<Int> = db.pendingAdViewEventDao().observePendingCount()

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

    fun observeFavorites(): Flow<Set<String>> {
        return db.favoriteDao().observeForUser("local_user").map { list -> list.map { it.doctorId }.toSet() }
    }

    fun observeFavoritesForUser(uid: String): Flow<Set<String>> {
        if (uid.isBlank()) return flowOf(emptySet())
        return db.favoriteDao().observeForUser(uid).map { list -> list.map { it.doctorId }.toSet() }
    }

    suspend fun toggleFavorite(doctorId: String, currentlyFav: Boolean) {
        toggleFavoriteForUser("local_user", doctorId, currentlyFav)
    }

    suspend fun toggleFavoriteForUser(uid: String, doctorId: String, currentlyFav: Boolean) {
        if (uid.isBlank() || doctorId.isBlank()) return
        if (currentlyFav) {
            db.favoriteDao().remove(uid, doctorId)
        } else {
            db.favoriteDao().insert(FavoriteEntity("fav_${uid}_$doctorId", uid, doctorId))
        }
    }

    suspend fun replaceDoctorFavoritesForUser(uid: String, doctorIds: Set<String>) {
        if (uid.isBlank()) return
        val existing = db.favoriteDao().observeForUser(uid).first().map { it.doctorId }.toSet()
        (existing - doctorIds).forEach { id -> db.favoriteDao().remove(uid, id) }
        (doctorIds - existing).forEach { id ->
            db.favoriteDao().insert(FavoriteEntity("fav_${uid}_$id", uid, id))
        }
    }

    suspend fun submitReport(doctorId: String, reason: String, details: String): Result<Unit> {
        if (reason.isBlank() || details.isBlank()) {
            return Result.failure(IllegalArgumentException("Reason and details required"))
        }
        val report = ReportEntity(
            id = "rep_${UUID.randomUUID().toString().take(8)}",
            doctorId = doctorId,
            reportedByUserId = "app_user",
            reason = reason.trim(),
            details = details.trim()
        )
        db.reportDao().insert(report)
        RtdbSyncService.pushReport(report)
        return Result.success(Unit)
    }

    suspend fun requestAppointment(
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
        val apt = AppointmentRequestEntity(
            id = "apt_${UUID.randomUUID().toString().take(8)}",
            patientUserId = "local_user",
            patientName = patientName.trim(),
            patientPhone = patientPhone.trim(),
            doctorId = doctorId,
            chamberId = chamberId,
            preferredDay = preferredDay,
            preferredSlot = preferredSlot,
            notes = notes.trim(),
            status = "PENDING"
        )
        db.appointmentDao().insert(apt)
        RtdbSyncService.pushAppointment(apt)
        return Result.success(Unit)
    }

    suspend fun markNotificationRead(id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        val existing = db.notificationDao().getById(cleanId)
        db.notificationDao().markRead(cleanId)
        PushNotificationManager.syncUserNotificationState(
            context = appContext,
            notificationId = cleanId,
            type = existing?.messageType ?: PushNotificationManager.TYPE_GENERAL,
            isRead = true,
            isDisplayed = existing?.isDisplayed ?: false
        )
    }

    suspend fun markNotificationDisplayed(id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        val existing = db.notificationDao().getById(cleanId)
        PushNotificationManager.markGeneralDisplayedLocally(appContext, cleanId)
        db.notificationDao().markDisplayed(cleanId)
        PushNotificationManager.syncUserNotificationState(
            context = appContext,
            notificationId = cleanId,
            type = existing?.messageType ?: PushNotificationManager.TYPE_GENERAL,
            isRead = true,
            isDisplayed = true
        )
    }

    suspend fun markAllNotificationsRead() {
        val unread = db.notificationDao().observeAll().first().filter { !it.isRead }
        db.notificationDao().markAllRead()
        unread.forEach { n ->
            PushNotificationManager.syncUserNotificationState(
                context = appContext,
                notificationId = n.id,
                type = n.messageType,
                isRead = true,
                isDisplayed = n.isDisplayed
            )
        }
    }
}
