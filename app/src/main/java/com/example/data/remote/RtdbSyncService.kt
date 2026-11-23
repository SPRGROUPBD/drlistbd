package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.*
import com.example.ui.ads.SponsorCampaignSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Syncs live directory data managed via index.html Admin Portal from the
 * Firebase Realtime Database REST endpoint directly into the local Room database.
 */
object RtdbSyncService {
    private const val BASE_URL = "https://octor-aad88-default-rtdb.europe-west1.firebasedatabase.app"
    private const val TAG = "RtdbSyncService"

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
            val root = JSONObject(body)
            var syncedDoctors = 0

            // Specialties
            root.optJSONObject("specialties")?.let { obj ->
                val list = parseMap(obj) { id, o ->
                    SpecialtyEntity(
                        id = id,
                        nameBn = o.optString("nameBn", o.optString("nameEn")),
                        nameEn = o.optString("nameEn", o.optString("nameBn")),
                        iconKey = o.optString("iconKey", "medical"),
                        iconUrl = o.optString("iconUrl", "").trim()
                    )
                }
                if (list.isNotEmpty()) {
                    db.specialtyDao().deleteAll()
                    db.specialtyDao().insertAll(list)
                }
            }

            // Locations (preserve all 64 Bangladesh districts + merge Admin custom locations)
            root.optJSONObject("locations")?.let { obj ->
                val list = parseMap(obj) { id, o ->
                    LocationEntity(
                        id = id,
                        division = o.optString("division", ""),
                        district = o.optString("district", ""),
                        upazila = o.optString("upazila", ""),
                        area = o.optString("area", "")
                    )
                }
                if (list.isNotEmpty()) {
                    db.locationDao().insertAll(list)
                }
            }

            // Doctors
            if (root.has("doctors")) {
                val obj = root.optJSONObject("doctors")
                val list = if (obj != null) parseMap(obj) { id, o ->
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
                db.doctorDao().deleteAll()
                if (list.isNotEmpty()) db.doctorDao().insertAll(list)
                syncedDoctors = list.size
            }

            // Education
            if (root.has("education")) {
                val obj = root.optJSONObject("education")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    EducationEntity(
                        id = id,
                        doctorId = o.optString("doctorId", ""),
                        degreeName = o.optString("degreeName", ""),
                        institution = o.optString("institution", ""),
                        passingYear = o.optString("passingYear", ""),
                        specialTraining = o.optString("specialTraining", "")
                    )
                } else emptyList()
                db.educationDao().deleteAll()
                if (list.isNotEmpty()) db.educationDao().insertAll(list)
            }

            // Employment History
            if (root.has("employment_history")) {
                val obj = root.optJSONObject("employment_history")
                val list = if (obj != null) parseMap(obj) { id, o ->
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
                } else emptyList()
                db.employmentDao().deleteAll()
                if (list.isNotEmpty()) db.employmentDao().insertAll(list)
            }

            // Chambers
            if (root.has("chambers")) {
                val obj = root.optJSONObject("chambers")
                val list = if (obj != null) parseMap(obj) { id, o ->
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
                } else emptyList()
                db.chamberDao().deleteAll()
                if (list.isNotEmpty()) db.chamberDao().insertAll(list)
            }

            // Chamber Schedules
            if (root.has("chamber_schedules")) {
                val obj = root.optJSONObject("chamber_schedules")
                val list = if (obj != null) parseMap(obj) { id, o ->
                    ChamberScheduleEntity(
                        id = id,
                        chamberId = o.optString("chamberId", ""),
                        doctorId = o.optString("doctorId", ""),
                        dayOfWeek = o.optString("dayOfWeek", "SATURDAY"),
                        openTime = o.optString("openTime", ""),
                        closeTime = o.optString("closeTime", ""),
                        slotLabel = o.optString("slotLabel", "")
                    )
                } else emptyList()
                db.chamberScheduleDao().deleteAll()
                if (list.isNotEmpty()) db.chamberScheduleDao().insertAll(list)
            }

            // Hospitals
            if (root.has("hospitals")) {
                val obj = root.optJSONObject("hospitals")
                val list = if (obj != null) parseMap(obj) { id, o ->
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
                } else emptyList()
                db.hospitalDao().deleteAll()
                if (list.isNotEmpty()) db.hospitalDao().insertAll(list)
            }

            // Diagnostic Centers
            if (root.has("diagnostic_centers")) {
                val obj = root.optJSONObject("diagnostic_centers")
                val list = if (obj != null) parseMap(obj) { id, o ->
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
                } else emptyList()
                db.diagnosticCenterDao().deleteAll()
                if (list.isNotEmpty()) db.diagnosticCenterDao().insertAll(list)
            }

            // Notifications (preserve local read status across auto-syncs & merge optional root /notifications)
            val existingReadIds = runCatching { db.notificationDao().getReadIds().toSet() }.getOrDefault(emptySet())
            val mergedNotifications = linkedMapOf<String, NotificationEntity>()
            root.optJSONObject("notifications")?.let { obj ->
                parseMap(obj) { id, o ->
                    NotificationEntity(
                        id = id,
                        titleBn = cleanOptString(o, "titleBn").ifBlank { cleanOptString(o, "title") },
                        titleEn = cleanOptString(o, "titleEn").ifBlank { cleanOptString(o, "title") },
                        messageBn = cleanOptString(o, "messageBn").ifBlank { cleanOptString(o, "message") },
                        messageEn = cleanOptString(o, "messageEn").ifBlank { cleanOptString(o, "message") },
                        targetRole = cleanOptString(o, "targetRole", "ALL"),
                        isRead = id in existingReadIds,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                }.forEach { mergedNotifications[it.id] = it }
            }
            // Optional top-level /notifications.json support for Firebase-pushed notifications
            runCatching {
                val nConn = (URL("$BASE_URL/notifications.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (nConn.responseCode in 200..299) {
                    val nBody = nConn.inputStream.bufferedReader().use { it.readText() }
                    if (nBody.isNotBlank() && nBody != "null") {
                        parseMap(JSONObject(nBody)) { id, o ->
                            NotificationEntity(
                                id = id,
                                titleBn = cleanOptString(o, "titleBn").ifBlank { cleanOptString(o, "title") },
                                titleEn = cleanOptString(o, "titleEn").ifBlank { cleanOptString(o, "title") },
                                messageBn = cleanOptString(o, "messageBn").ifBlank { cleanOptString(o, "message") },
                                messageEn = cleanOptString(o, "messageEn").ifBlank { cleanOptString(o, "message") },
                                targetRole = cleanOptString(o, "targetRole", "ALL"),
                                isRead = id in existingReadIds,
                                createdAt = o.optLong("createdAt", System.currentTimeMillis())
                            )
                        }.forEach { mergedNotifications.putIfAbsent(it.id, it) }
                    }
                }
                nConn.disconnect()
            }
            if (root.has("notifications") || mergedNotifications.isNotEmpty()) {
                db.notificationDao().deleteAll()
                if (mergedNotifications.isNotEmpty()) {
                    db.notificationDao().insertAll(mergedNotifications.values.toList())
                }
            }

            // Site Settings
            root.optJSONObject("settings")?.let { s ->
                db.settingsDao().save(
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
                )
            }

            // All advertisement systems have been removed. Clear legacy ad caches and
            // persist a disabled configuration so old Firebase records cannot re-enable ads.
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
