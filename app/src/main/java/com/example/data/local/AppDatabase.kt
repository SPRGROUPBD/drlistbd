package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        DoctorEntity::class,
        EducationEntity::class,
        EmploymentHistoryEntity::class,
        SpecialtyEntity::class,
        LocationEntity::class,
        HospitalEntity::class,
        DiagnosticCenterEntity::class,
        ChamberEntity::class,
        ChamberScheduleEntity::class,
        AppointmentRequestEntity::class,
        FavoriteEntity::class,
        VerificationRequestEntity::class,
        ReportEntity::class,
        NotificationEntity::class,
        SiteSettingsEntity::class,
        AuditLogEntity::class,
        HospitalDoctorLinkEntity::class,
        ProfileChangeRequestEntity::class,
        AdConfigEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun doctorDao(): DoctorDao
    abstract fun educationDao(): EducationDao
    abstract fun employmentDao(): EmploymentDao
    abstract fun specialtyDao(): SpecialtyDao
    abstract fun locationDao(): LocationDao
    abstract fun hospitalDao(): HospitalDao
    abstract fun diagnosticCenterDao(): DiagnosticCenterDao
    abstract fun chamberDao(): ChamberDao
    abstract fun chamberScheduleDao(): ChamberScheduleDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun verificationRequestDao(): VerificationRequestDao
    abstract fun reportDao(): ReportDao
    abstract fun notificationDao(): NotificationDao
    abstract fun settingsDao(): SettingsDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun hospitalDoctorLinkDao(): HospitalDoctorLinkDao
    abstract fun profileChangeRequestDao(): ProfileChangeRequestDao
    abstract fun adConfigDao(): AdConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "doctor_list_bd.db"
                ).fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
