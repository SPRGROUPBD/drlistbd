package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.ui.ads.SponsorCampaignSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Syncs live directory data managed via index.html Admin Portal from the
 * Firebase Realtime Database REST endpoint directly into the local Room database.
 * Uses payload hashing + atomic Room transactions so UI screens never flicker or refresh.
 */
object RtdbSyncService {
    private const val BASE_URL = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"
    private const val TAG = "RtdbSyncService"

    @Volatile
    private var lastSyncedCombinedHash: String? = null

    @Volatile
    private var lastSyncedDoctorCount: Int = 0

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun pullDirectoryIntoRoom(db: AppDatabase, context: Context? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$BASE_URL/directory.json").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext Result.failure(IllegalStateException("HTTP $code"))
            }
            // Capture authoritative server timestamp from HTTP Date header (prevents offline clock rollback abuse)
            val serverHeaderDateMs = conn.date.takeIf { it > 0L } ?: System.currentTimeMillis()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            if (context != null) {
                SessionPreferences(context.applicationContext).saveServerSyncAnchor(serverHeaderDateMs)
            }

            if (body.isBlank() || body == "null") {
                return@withContext Result.success(0)
            }

            // Optional top-level /notifications.json support for Firebase-pushed notifications
            val rootNotifBody = runCatching {
                val nConn = (URL("$BASE_URL/notifications.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val text = if (nConn.responseCode in 200..299) {
                    nConn.inputStream.bufferedReader().use { it.readText() }
                } else ""
                nConn.disconnect()
                text
            }.getOrDefault("")

            // If Firebase payload is identical to what was already committed in this session,
            // skip rewriting Room tables so the UI never refreshes or flickers.
            val combinedHash = sha256("$body||$rootNotifBody")
            if (lastSyncedCombinedHash == combinedHash) {
                return@withContext Result.success(lastSyncedDoctorCount)
            }

            val root = JSONObject(body)
            var syncedDoctors = 0

            val parsedSpecialties: List<SpecialtyEntity>? = root.optJSONObject("specialties")?.let { obj ->
                parseMap(obj) { id, o ->
                    SpecialtyEntity(
                        id = id,
                        nameBn = o.optString("nameBn", o.optString("nameEn")),
                        nameEn = o.optString("nameEn", o.optString("nameBn")),
                        iconKey = o.optString("iconKey", "medical"),
                        iconUrl = o.optString("iconUrl", "").trim()
                    )
                }.takeIf { it.isNotEmpty() }
            }

            val parsedLocations: List<LocationEntity>? = root.optJSONObject("locations")?.let { obj ->
                parseMap(obj) { id, o ->
                    LocationEntity(
                        id = id,
                        division = o.optString("division", ""),
                        district = o.optString("district", ""),
                        upazila = o.optString("upazila", ""),
                        area = o.optString("area", "")
                    )
                }.takeIf { it.isNotEmpty() }
            }

            val hasDoctors = root.has("doctors")
            val parsedDoctors: List<DoctorEntity> = if (hasDoctors) {
                val obj = root.optJSONObject("doctors")
                if (obj != null) parseMap(obj) { id, o ->
                    val fallbackName = o.optString("doctorName", "")
                    val nameBn = o.optString("fullNameBn", "").ifBlank { fallbackName }
                    val nameEn = o.optString("fullNameEn", "").ifBlank { fallbackName.ifBlank { nameBn } }
                    val qual = o.optString("qualification", "")
                        .ifBlank { o.optString("degree", "") }
                        .ifBlank { o.optString("trainings", "") }
                    DoctorEntity(
                        id = id,
                        userId = o.optString("userId", ""),
                        fullNameBn = nameBn,
                        fullNameEn = nameEn,
                        photoUrl = o.optString("photoUrl", ""),
                        qualification = qual,
                        bmdcNumber = o.optString("bmdcNumber", ""),
                        bmdcVerified = o.optBoolean("bmdcVerified", false),
                        verificationStatus = o.optString("verificationStatus", "PENDING"),
                        approvalStatus = o.optString("approvalStatus", "APPROVED"),
                        verificationNotes = o.optString("verificationNotes", ""),
                        gender = o.optString("gender", ""),
                        bioBn = o.optString("bioBn", ""),
                        bioEn = o.optString("bioEn", ""),
                        contactNumber = o.optString("contactNumber", ""),
                        mainSpecialtyId = o.optString("mainSpecialtyId", ""),
                        subSpecialty = o.optString("subSpecialty", ""),
                        expertise = o.optString("expertise", ""),
                        currentHospital = o.optString("currentHospital", ""),
                        currentMedicalCollege = o.optString("currentMedicalCollege", ""),
                        currentDepartment = o.optString("currentDepartment", ""),
                        currentDesignation = o.optString("currentDesignation", ""),
                        employmentStartDate = o.optString("employmentStartDate", ""),
                        totalExperienceYears = o.optInt("totalExperienceYears", 0),
                        teachingExperienceYears = o.optInt("teachingExperienceYears", 0),
                        trainings = o.optString("trainings", ""),
                        isActive = o.optBoolean("isActive", true),
                        isFeatured = o.optBoolean("isFeatured", false),
                        isDemoData = false,
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                } else emptyList()
            } else emptyList()
            if (hasDoctors) syncedDoctors = parsedDoctors.size

            val hasEducation = root.has("education")
            val parsedEducation: List<EducationEntity> = if (hasEducation) {
                root.optJSONObject("education")?.let { obj ->
                    parseMap(obj) { id, o ->
                        EducationEntity(
                            id = id,
                            doctorId = o.optString("doctorId", ""),
                            degreeName = o.optString("degreeName", ""),
                            institution = o.optString("institution", ""),
                            passingYear = o.optString("passingYear", ""),
                            specialTraining = o.optString("specialTraining", "")
                        )
                    }
                } ?: emptyList()
            } else emptyList()

            val hasEmployment = root.has("employment_history")
            val parsedEmployment: List<EmploymentHistoryEntity> = if (hasEmployment) {
                root.optJSONObject("employment_history")?.let { obj ->
                    parseMap(obj) { id, o ->
                        EmploymentHistoryEntity(
                            id = id,
                            doctorId = o.optString("doctorId", ""),
                            hospitalName = o.optString("hospitalName", ""),
                            medicalCollege = o.optString("medicalCollege", ""),
                            designation = o.optString("designation", ""),
                            department = o.optString("department", ""),
                            startDate = o.optString("startDate", ""),
                            endDate = o.optString("endDate", ""),
                            description = o.optString("description", "")
                        )
                    }
                } ?: emptyList()
            } else emptyList()

            val hasChambers = root.has("chambers")
            val parsedChambers: List<ChamberEntity> = if (hasChambers) {
                root.optJSONObject("chambers")?.let { obj ->
                    parseMap(obj) { id, o ->
                        ChamberEntity(
                            id = id,
                            doctorId = o.optString("doctorId", ""),
                            facilityName = o.optString("facilityName", ""),
                            facilityType = o.optString("facilityType", "HOSPITAL"),
                            chamberName = o.optString("chamberName", ""),
                            district = o.optString("district", ""),
                            upazila = o.optString("upazila", ""),
                            fullAddress = o.optString("fullAddress", ""),
                            locationArea = o.optString("locationArea", ""),
                            googleMapsUrl = o.optString("googleMapsUrl", ""),
                            serialPhone = o.optString("serialPhone", ""),
                            instructions = o.optString("instructions", ""),
                            isActive = o.optBoolean("isActive", true)
                        )
                    }
                } ?: emptyList()
            } else emptyList()

            val hasSchedules = root.has("chamber_schedules")
            val parsedSchedules: List<ChamberScheduleEntity> = if (hasSchedules) {
                root.optJSONObject("chamber_schedules")?.let { obj ->
                    parseMap(obj) { id, o ->
                        ChamberScheduleEntity(
                            id = id,
                            chamberId = o.optString("chamberId", ""),
                            doctorId = o.optString("doctorId", ""),
                            dayOfWeek = o.optString("dayOfWeek", "SATURDAY"),
                            openTime = o.optString("openTime", ""),
                            closeTime = o.optString("closeTime", ""),
                            slotLabel = o.optString("slotLabel", "")
                        )
                    }
                } ?: emptyList()
            } else emptyList()

            val hasHospitals = root.has("hospitals")
            val parsedHospitals: List<HospitalEntity> = if (hasHospitals) {
                root.optJSONObject("hospitals")?.let { obj ->
                    parseMap(obj) { id, o ->
                        HospitalEntity(
                            id = id,
                            managerUserId = o.optString("managerUserId", ""),
                            nameBn = o.optString("nameBn", ""),
                            nameEn = o.optString("nameEn", ""),
                            district = o.optString("district", ""),
                            upazila = o.optString("upazila", ""),
                            address = o.optString("address", ""),
                            contactNumber = o.optString("contactNumber", ""),
                            imageUrl = o.optString("imageUrl", ""),
                            status = o.optString("status", "ACTIVE"),
                            isDemoData = false
                        )
                    }
                } ?: emptyList()
            } else emptyList()

            val hasDiagnostics = root.has("diagnostic_centers")
            val parsedDiagnostics: List<DiagnosticCenterEntity> = if (hasDiagnostics) {
                root.optJSONObject("diagnostic_centers")?.let { obj ->
                    parseMap(obj) { id, o ->
                        DiagnosticCenterEntity(
                            id = id,
                            nameBn = o.optString("nameBn", ""),
                            nameEn = o.optString("nameEn", ""),
                            district = o.optString("district", ""),
                            upazila = o.optString("upazila", ""),
                            address = o.optString("address", ""),
                            contactNumber = o.optString("contactNumber", ""),
                            imageUrl = o.optString("imageUrl", ""),
                            status = o.optString("status", "ACTIVE"),
                            isDemoData = false
                        )
                    }
                } ?: emptyList()
            } else emptyList()

            // Notifications (preserve local read status across auto-syncs & merge optional root /notifications)
            val existingReadIds = runCatching { db.notificationDao().getReadIds().toSet() }.getOrDefault(emptySet())
            val mergedNotifications = linkedMapOf<String, NotificationEntity>()
            fun parseNotificationRecord(id: String, o: JSONObject): NotificationEntity = NotificationEntity(
                id = cleanOptString(o, "notificationId").ifBlank { id },
                titleBn = cleanOptString(o, "titleBn").ifBlank { cleanOptString(o, "title") },
                titleEn = cleanOptString(o, "titleEn").ifBlank { cleanOptString(o, "title") },
                messageBn = cleanOptString(o, "messageBn").ifBlank { cleanOptString(o, "message") },
                messageEn = cleanOptString(o, "messageEn").ifBlank { cleanOptString(o, "message") },
                targetRole = cleanOptString(o, "targetRole", "ALL"),
                messageType = cleanOptString(o, "messageType", "GENERAL").uppercase(),
                selectedDoctorId = cleanOptString(o, "selectedDoctorId"),
                selectedDoctorName = cleanOptString(o, "selectedDoctorName"),
                imageUrl = cleanOptString(o, "imageUrl"),
                deepLink = cleanOptString(o, "deepLink"),
                isRead = id in existingReadIds,
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
            root.optJSONObject("notifications")?.let { obj ->
                parseMap(obj) { id, o -> parseNotificationRecord(id, o) }
                    .forEach { mergedNotifications[it.id] = it }
            }
            if (rootNotifBody.isNotBlank() && rootNotifBody != "null") {
                runCatching {
                    parseMap(JSONObject(rootNotifBody)) { id, o -> parseNotificationRecord(id, o) }
                        .forEach { mergedNotifications[it.id] = it }
                }
            }
            val shouldUpdateNotifications = root.has("notifications") || mergedNotifications.isNotEmpty()

            val parsedSettings: SiteSettingsEntity? = root.optJSONObject("settings")?.let { s ->
                SiteSettingsEntity(
                    id = "global",
                    siteTitle = s.optString("siteTitle", "Doctor List BD"),
                    taglineBn = s.optString("taglineBn", "ডাক্তার কোথায় বসেন, কখন বসেন — সব তথ্য এক জায়গায়।"),
                    taglineEn = s.optString("taglineEn", "Where doctors sit & when they sit — all verified chamber info in one place."),
                    contactEmail = s.optString("contactEmail", "support@doctorlistbd.org"),
                    contactPhone = s.optString("contactPhone", "+880 1700-000000"),
                    allowOnlineBooking = s.optBoolean("allowOnlineBooking", true),
                    showDemoData = false
                )
            }

            // Single atomic Room transaction: prevents intermediate empty list emissions and page refresh flicker
            db.withTransaction {
                parsedSpecialties?.let { list ->
                    db.specialtyDao().deleteAll()
                    db.specialtyDao().insertAll(list)
                }
                parsedLocations?.let { list ->
                    db.locationDao().insertAll(list)
                }
                if (hasDoctors) {
                    db.doctorDao().deleteAll()
                    if (parsedDoctors.isNotEmpty()) db.doctorDao().insertAll(parsedDoctors)
                }
                if (hasEducation) {
                    db.educationDao().deleteAll()
                    if (parsedEducation.isNotEmpty()) db.educationDao().insertAll(parsedEducation)
                }
                if (hasEmployment) {
                    db.employmentDao().deleteAll()
                    if (parsedEmployment.isNotEmpty()) db.employmentDao().insertAll(parsedEmployment)
                }
                if (hasChambers) {
                    db.chamberDao().deleteAll()
                    if (parsedChambers.isNotEmpty()) db.chamberDao().insertAll(parsedChambers)
                }
                if (hasSchedules) {
                    db.chamberScheduleDao().deleteAll()
                    if (parsedSchedules.isNotEmpty()) db.chamberScheduleDao().insertAll(parsedSchedules)
                }
                if (hasHospitals) {
                    db.hospitalDao().deleteAll()
                    if (parsedHospitals.isNotEmpty()) db.hospitalDao().insertAll(parsedHospitals)
                }
                if (hasDiagnostics) {
                    db.diagnosticCenterDao().deleteAll()
                    if (parsedDiagnostics.isNotEmpty()) db.diagnosticCenterDao().insertAll(parsedDiagnostics)
                }
                if (shouldUpdateNotifications) {
                    db.notificationDao().deleteAll()
                    if (mergedNotifications.isNotEmpty()) {
                        db.notificationDao().insertAll(mergedNotifications.values.toList())
                    }
                }
                parsedSettings?.let { db.settingsDao().save(it) }

                // Keep legacy ad caches disabled cleanly inside the same transaction
                db.sponsorDao().deleteAll()
                db.sponsorPackageDao().deleteAll()
                db.placementRateCardDao().deleteAll()
                db.adConfigDao().save(
                    AdConfigEntity(
                        adsEnabled = false,
                        bannerEnabled = false,
                        appOpenEnabled = false,
                        nativeBannerEnabled = false,
                        rewardedEnabled = false,
                        sponsorBannerEnabled = false,
                        appOpenPopupEnabled = false,
                        nativeSponsorEnabled = false,
                        aboutUsSponsorEnabled = false
                    )
                )
                db.paymentSettingsDao().save(PaymentSettingsEntity())
            }

            lastSyncedCombinedHash = combinedHash
            lastSyncedDoctorCount = syncedDoctors
            Result.success(syncedDoctors)
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync skipped/failed (using local Room cache): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun pushReport(report: ReportEntity) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", report.id)
                put("doctorId", report.doctorId)
                put("reason", report.reason)
                put("details", report.details)
                put("status", report.status)
                put("createdAt", report.createdAt)
            }
            putJson("$BASE_URL/directory/reports/${report.id}.json", json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Push report offline: ${e.message}")
        }
    }

    suspend fun pushAppointment(apt: AppointmentRequestEntity) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", apt.id)
                put("patientName", apt.patientName)
                put("patientPhone", apt.patientPhone)
                put("doctorId", apt.doctorId)
                put("chamberId", apt.chamberId)
                put("preferredDay", apt.preferredDay)
                put("preferredSlot", apt.preferredSlot)
                put("notes", apt.notes)
                put("status", apt.status)
                put("createdAt", apt.createdAt)
            }
            putJson("$BASE_URL/directory/appointments/${apt.id}.json", json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Push appointment offline: ${e.message}")
        }
    }

    private fun putJson(url: String, payload: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 6000
            readTimeout = 6000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(payload) }
        conn.responseCode
        conn.disconnect()
    }

    private fun cleanOptString(obj: JSONObject?, key: String, fallback: String = ""): String {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return fallback
        val v = obj.optString(key, fallback).trim()
        return if (v.equals("null", ignoreCase = true)) fallback else v
    }

    private fun <T> parseMap(obj: JSONObject, mapper: (String, JSONObject) -> T): List<T> {
        val out = mutableListOf<T>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = obj.optJSONObject(key)
            if (item != null) out.add(mapper(key, item))
        }
        return out
    }
}
