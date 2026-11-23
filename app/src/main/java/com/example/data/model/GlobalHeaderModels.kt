package com.example.data.model

import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.i18n.AppLang
import com.example.i18n.tr

enum class GlobalSearchType {
    DOCTOR,
    HOSPITAL,
    DIAGNOSTIC,
    CHAMBER
}

data class GlobalSearchSuggestion(
    val id: String,
    val type: GlobalSearchType,
    val title: String,
    val subtitle: String,
    val secondaryInfo: String = "",
    val doctorId: String = "",
    val matchScore: Int = 0
)

enum class AutoSyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILURE
}

data class SyncUiState(
    val status: AutoSyncStatus = AutoSyncStatus.IDLE,
    val lastSuccessAtMs: Long = 0L,
    val lastError: String? = null,
    val syncedDoctorsCount: Int = 0,
    val isOnline: Boolean = true,
    val autoSyncEnabled: Boolean = true
)

/**
 * Unified live search engine for the global Top App Bar.
 * Enforces a minimum of 2 characters before returning categorized suggestions across:
 * Doctors, Hospitals, Diagnostic Centers, and Chambers.
 */
object GlobalSearchEngine {
    const val MIN_QUERY_LENGTH = 2

    fun search(
        query: String,
        doctors: List<DoctorFullProfile>,
        hospitals: List<HospitalEntity>,
        diagnostics: List<DiagnosticCenterEntity>,
        lang: AppLang,
        maxResults: Int = 24
    ): List<GlobalSearchSuggestion> {
        val clean = query.trim()
        if (clean.length < MIN_QUERY_LENGTH) return emptyList()
        val q = clean.lowercase()

        val results = mutableListOf<GlobalSearchSuggestion>()

        // 1. Doctors
        for (profile in doctors) {
            val d = profile.doctor
            val nameBn = d.fullNameBn.trim()
            val nameEn = d.fullNameEn.trim()
            val specBn = profile.specialty?.nameBn.orEmpty()
            val specEn = profile.specialty?.nameEn.orEmpty()
            val score = bestMatchScore(
                q = q,
                primaryFields = listOf(nameBn, nameEn),
                secondaryFields = listOf(
                    specBn,
                    specEn,
                    d.qualification,
                    d.subSpecialty,
                    d.currentHospital,
                    d.currentDesignation
                )
            )
            if (score > 0) {
                val title = tr(lang, nameBn.ifBlank { nameEn }, nameEn.ifBlank { nameBn })
                val spec = profile.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: d.subSpecialty
                val subtitle = listOf(spec, d.qualification)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                val secondary = d.currentHospital.ifBlank {
                    profile.chambers.firstOrNull { it.chamber.isActive }?.chamber?.district.orEmpty()
                }
                results += GlobalSearchSuggestion(
                    id = d.id,
                    type = GlobalSearchType.DOCTOR,
                    title = title,
                    subtitle = subtitle,
                    secondaryInfo = secondary,
                    doctorId = d.id,
                    matchScore = score
                )
            }
        }

        // 2. Hospitals
        for (h in hospitals) {
            val score = bestMatchScore(
                q = q,
                primaryFields = listOf(h.nameBn, h.nameEn),
                secondaryFields = listOf(h.district, h.upazila, h.address)
            )
            if (score > 0) {
                val title = tr(lang, h.nameBn.ifBlank { h.nameEn }, h.nameEn.ifBlank { h.nameBn })
                val location = listOf(h.upazila, h.district).filter { it.isNotBlank() }.joinToString(", ")
                results += GlobalSearchSuggestion(
                    id = h.id,
                    type = GlobalSearchType.HOSPITAL,
                    title = title,
                    subtitle = location.ifBlank { h.address },
                    secondaryInfo = h.address,
                    matchScore = score
                )
            }
        }

        // 3. Diagnostic Centers
        for (dc in diagnostics) {
            val score = bestMatchScore(
                q = q,
                primaryFields = listOf(dc.nameBn, dc.nameEn),
                secondaryFields = listOf(dc.district, dc.upazila, dc.address)
            )
            if (score > 0) {
                val title = tr(lang, dc.nameBn.ifBlank { dc.nameEn }, dc.nameEn.ifBlank { dc.nameBn })
                val location = listOf(dc.upazila, dc.district).filter { it.isNotBlank() }.joinToString(", ")
                results += GlobalSearchSuggestion(
                    id = dc.id,
                    type = GlobalSearchType.DIAGNOSTIC,
                    title = title,
                    subtitle = location.ifBlank { dc.address },
                    secondaryInfo = dc.address,
                    matchScore = score
                )
            }
        }

        // 4. Chambers
        val seenChamberIds = hashSetOf<String>()
        for (profile in doctors) {
            val docName = tr(
                lang,
                profile.doctor.fullNameBn.ifBlank { profile.doctor.fullNameEn },
                profile.doctor.fullNameEn.ifBlank { profile.doctor.fullNameBn }
            )
            for (cw in profile.chambers) {
                val ch = cw.chamber
                if (!ch.isActive || !seenChamberIds.add(ch.id)) continue
                val score = bestMatchScore(
                    q = q,
                    primaryFields = listOf(ch.chamberName, ch.facilityName),
                    secondaryFields = listOf(
                        ch.district,
                        ch.upazila,
                        ch.locationArea,
                        ch.fullAddress,
                        profile.doctor.fullNameBn,
                        profile.doctor.fullNameEn
                    )
                )
                if (score > 0) {
                    val chamberTitle = when {
                        ch.chamberName.isNotBlank() && ch.facilityName.isNotBlank() ->
                            "${ch.facilityName} (${ch.chamberName})"
                        ch.facilityName.isNotBlank() -> ch.facilityName
                        else -> ch.chamberName
                    }
                    val location = listOf(ch.upazila.ifBlank { ch.locationArea }, ch.district)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    results += GlobalSearchSuggestion(
                        id = ch.id,
                        type = GlobalSearchType.CHAMBER,
                        title = chamberTitle,
                        subtitle = docName,
                        secondaryInfo = location.ifBlank { ch.fullAddress },
                        doctorId = profile.doctor.id,
                        matchScore = score
                    )
                }
            }
        }

        return results
            .sortedWith(
                compareByDescending<GlobalSearchSuggestion> { it.matchScore }
                    .thenBy { it.type.ordinal }
                    .thenBy { it.title.lowercase() }
            )
            .take(maxResults)
    }

    private fun bestMatchScore(
        q: String,
        primaryFields: List<String>,
        secondaryFields: List<String>
    ): Int {
        var best = 0
        for (raw in primaryFields) {
            val v = raw.trim().lowercase()
            if (v.isEmpty()) continue
            val s = when {
                v == q -> 100
                v.startsWith(q) -> 90
                v.split(" ", ",", "-", "(", ")").any { it.startsWith(q) } -> 80
                v.contains(q) -> 70
                else -> 0
            }
            if (s > best) best = s
        }
        if (best >= 70) return best
        for (raw in secondaryFields) {
            val v = raw.trim().lowercase()
            if (v.isEmpty()) continue
            val s = when {
                v.startsWith(q) -> 55
                v.contains(q) -> 45
                else -> 0
            }
            if (s > best) best = s
        }
        return best
    }
}
