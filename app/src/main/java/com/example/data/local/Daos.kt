package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isHidden = 0 ORDER BY createdAt DESC")
    fun observeVisibleUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:emailOrId) LIMIT 1")
    suspend fun getByEmailOrId(emailOrId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id AND isHidden = 0")
    suspend fun deleteVisibleUser(id: String)
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM doctors ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DoctorEntity>>

    @Query("SELECT * FROM doctors WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DoctorEntity?

    @Query("SELECT * FROM doctors WHERE LOWER(bmdcNumber) = LOWER(:bmdc) LIMIT 1")
    suspend fun getByBmdc(bmdc: String): DoctorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doctor: DoctorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(doctors: List<DoctorEntity>)

    @Update
    suspend fun update(doctor: DoctorEntity)

    @Query("DELETE FROM doctors WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM doctors")
    suspend fun deleteAll()

    @Query("DELETE FROM doctors WHERE isDemoData = 1")
    suspend fun deleteDemoDoctors()
}

@Dao
interface EducationDao {
    @Query("SELECT * FROM education ORDER BY passingYear DESC")
    fun observeAll(): Flow<List<EducationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(education: EducationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<EducationEntity>)

    @Query("DELETE FROM education WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM education")
    suspend fun deleteAll()
}

@Dao
interface EmploymentDao {
    @Query("SELECT * FROM employment_history ORDER BY startDate DESC")
    fun observeAll(): Flow<List<EmploymentHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: EmploymentHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<EmploymentHistoryEntity>)

    @Query("DELETE FROM employment_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM employment_history")
    suspend fun deleteAll()
}

@Dao
interface SpecialtyDao {
    @Query("SELECT * FROM specialties ORDER BY nameEn ASC")
    fun observeAll(): Flow<List<SpecialtyEntity>>

    @Query("SELECT COUNT(*) FROM specialties")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(specialty: SpecialtyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<SpecialtyEntity>)

    @Query("DELETE FROM specialties WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM specialties")
    suspend fun deleteAll()
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY division ASC, district ASC, upazila ASC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<LocationEntity>)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM locations")
    suspend fun deleteAll()
}

@Dao
interface HospitalDao {
    @Query("SELECT * FROM hospitals ORDER BY nameEn ASC")
    fun observeAll(): Flow<List<HospitalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hospital: HospitalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<HospitalEntity>)

    @Query("DELETE FROM hospitals WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM hospitals")
    suspend fun deleteAll()

    @Query("DELETE FROM hospitals WHERE isDemoData = 1")
    suspend fun deleteDemoHospitals()
}

@Dao
interface DiagnosticCenterDao {
    @Query("SELECT * FROM diagnostic_centers ORDER BY nameEn ASC")
    fun observeAll(): Flow<List<DiagnosticCenterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(center: DiagnosticCenterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<DiagnosticCenterEntity>)

    @Query("DELETE FROM diagnostic_centers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM diagnostic_centers")
    suspend fun deleteAll()

    @Query("DELETE FROM diagnostic_centers WHERE isDemoData = 1")
    suspend fun deleteDemoCenters()
}

@Dao
interface ChamberDao {
    @Query("SELECT * FROM chambers")
    fun observeAll(): Flow<List<ChamberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chamber: ChamberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ChamberEntity>)

    @Query("DELETE FROM chambers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chambers")
    suspend fun deleteAll()
}

@Dao
interface ChamberScheduleDao {
    @Query("SELECT * FROM chamber_schedules")
    fun observeAll(): Flow<List<ChamberScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ChamberScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ChamberScheduleEntity>)

    @Query("DELETE FROM chamber_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chamber_schedules")
    suspend fun deleteAll()
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AppointmentRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AppointmentRequestEntity)

    @Query("UPDATE appointments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeForUser(userId: String): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND doctorId = :doctorId")
    suspend fun remove(userId: String, doctorId: String)
}

@Dao
interface VerificationRequestDao {
    @Query("SELECT * FROM verification_requests ORDER BY submittedAt DESC")
    fun observeAll(): Flow<List<VerificationRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: VerificationRequestEntity)

    @Update
    suspend fun update(request: VerificationRequestEntity)
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity)

    @Query("UPDATE reports SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT id FROM notifications WHERE isRead = 1")
    suspend fun getReadIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<NotificationEntity>)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 'global' LIMIT 1")
    fun observe(): Flow<SiteSettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 'global' LIMIT 1")
    suspend fun getOnce(): SiteSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: SiteSettingsEntity)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeAll(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)
}

@Dao
interface HospitalDoctorLinkDao {
    @Query("SELECT * FROM hospital_doctor_links")
    fun observeAll(): Flow<List<HospitalDoctorLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: HospitalDoctorLinkEntity)

    @Query("UPDATE hospital_doctor_links SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface ProfileChangeRequestDao {
    @Query("SELECT * FROM profile_change_requests ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProfileChangeRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(req: ProfileChangeRequestEntity)

    @Query("UPDATE profile_change_requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface AdConfigDao {
    @Query("SELECT * FROM ad_config WHERE id = 'global' LIMIT 1")
    fun observe(): Flow<AdConfigEntity?>

    @Query("SELECT * FROM ad_config WHERE id = 'global' LIMIT 1")
    suspend fun getOnce(): AdConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: AdConfigEntity)
}

@Dao
interface SponsorDao {
    @Query("SELECT * FROM sponsors ORDER BY displayPriority DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SponsorEntity>>

    @Query("SELECT * FROM sponsors ORDER BY displayPriority DESC, updatedAt DESC")
    suspend fun getAllOnce(): List<SponsorEntity>

    @Query("SELECT * FROM sponsors WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SponsorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sponsor: SponsorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sponsors: List<SponsorEntity>)

    @Query("UPDATE sponsors SET localLogoPath = :logoPath, localBannerPath = :bannerPath, cachedAt = :cachedAt WHERE id = :id")
    suspend fun updateLocalCachePaths(id: String, logoPath: String, bannerPath: String, cachedAt: Long)

    @Query("DELETE FROM sponsors WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sponsors WHERE endTimestamp > 0 AND endTimestamp < :cutoffTimestamp")
    suspend fun deleteExpiredBefore(cutoffTimestamp: Long)

    @Query("DELETE FROM sponsors")
    suspend fun deleteAll()
}

@Dao
interface SponsorPackageDao {
    @Query("SELECT * FROM ad_packages ORDER BY priceBdt ASC, durationDays ASC")
    fun observeAll(): Flow<List<SponsorPackageEntity>>

    @Query("SELECT * FROM ad_packages WHERE status = 'ACTIVE' ORDER BY priceBdt ASC")
    suspend fun getActiveOnce(): List<SponsorPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(packages: List<SponsorPackageEntity>)

    @Query("DELETE FROM ad_packages")
    suspend fun deleteAll()
}

@Dao
interface PaymentSettingsDao {
    @Query("SELECT * FROM payment_settings WHERE id = 'global' LIMIT 1")
    fun observe(): Flow<PaymentSettingsEntity?>

    @Query("SELECT * FROM payment_settings WHERE id = 'global' LIMIT 1")
    suspend fun getOnce(): PaymentSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: PaymentSettingsEntity)
}

@Dao
interface PendingAdViewEventDao {
    @Query("SELECT * FROM pending_ad_view_events ORDER BY createdAt ASC LIMIT 250")
    suspend fun getPendingOnce(): List<PendingAdViewEventEntity>

    @Query("SELECT COUNT(*) FROM pending_ad_view_events")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: PendingAdViewEventEntity): Long

    @Query("DELETE FROM pending_ad_view_events WHERE eventKey = :eventKey")
    suspend fun deleteByKey(eventKey: String)

    @Query("DELETE FROM pending_ad_view_events")
    suspend fun deleteAll()
}

@Dao
interface PlacementRateCardDao {
    @Query("SELECT * FROM placement_rate_cards ORDER BY sortOrder ASC, placementId ASC")
    fun observeAll(): Flow<List<PlacementRateCardEntity>>

    @Query("SELECT * FROM placement_rate_cards ORDER BY sortOrder ASC, placementId ASC")
    suspend fun getAllOnce(): List<PlacementRateCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<PlacementRateCardEntity>)

    @Query("DELETE FROM placement_rate_cards")
    suspend fun deleteAll()
}

@Dao
interface RateCardSettingsDao {
    @Query("SELECT * FROM rate_card_settings WHERE id = 'global' LIMIT 1")
    fun observe(): Flow<RateCardSettingsEntity?>

    @Query("SELECT * FROM rate_card_settings WHERE id = 'global' LIMIT 1")
    suspend fun getOnce(): RateCardSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: RateCardSettingsEntity)
}

@Dao
interface AppInstallationDao {
    @Query("SELECT * FROM app_installations WHERE installationId = :id LIMIT 1")
    suspend fun getById(id: String): AppInstallationEntity?

    @Query("SELECT * FROM app_installations ORDER BY lastActiveDate DESC LIMIT 1")
    suspend fun getLatest(): AppInstallationEntity?

    @Query("SELECT * FROM app_installations ORDER BY lastActiveDate DESC LIMIT 1")
    fun observeLatest(): Flow<AppInstallationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AppInstallationEntity)
}

@Dao
interface PendingAppAnalyticsEventDao {
    @Query("SELECT * FROM pending_app_analytics_events ORDER BY createdAt ASC LIMIT 250")
    suspend fun getPendingOnce(): List<PendingAppAnalyticsEventEntity>

    @Query("SELECT COUNT(*) FROM pending_app_analytics_events")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: PendingAppAnalyticsEventEntity): Long

    @Query("DELETE FROM pending_app_analytics_events WHERE eventId = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM pending_app_analytics_events")
    suspend fun deleteAll()
}
