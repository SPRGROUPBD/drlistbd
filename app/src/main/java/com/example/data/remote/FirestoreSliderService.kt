package com.example.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads public slider documents from the configured Cloud Firestore REST endpoint.
 * Firestore security rules remain authoritative; this client does not bypass them.
 */
object FirestoreSliderService {
    suspend fun fetchActiveSliders(config: com.example.data.local.FirestoreSliderConfig): Result<List<HomeAutoSliderItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(config.projectId.isNotBlank()) { "Firestore Project ID is required" }
                val collection = Uri.encode(config.collectionName.ifBlank { "sliders" })
                val endpoint = buildString {
                    append("https://firestore.googleapis.com/v1/projects/")
                    append(Uri.encode(config.projectId))
                    append("/databases/(default)/documents/")
                    append(collection)
                    if (config.apiKey.isNotBlank()) append("?key=").append(Uri.encode(config.apiKey))
                }
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    check(code in 200..299) {
                        "Firestore HTTP $code: " + runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty().ifBlank { body.take(250) }
                    }
                    val root = JSONObject(body)
                    val docs = root.optJSONArray("documents")
                    buildList {
                        if (docs != null) for (i in 0 until docs.length()) {
                            val doc = docs.optJSONObject(i) ?: continue
                            val fields = doc.optJSONObject("fields") ?: JSONObject()
                            val name = doc.optString("name")
                            val id = name.substringAfterLast('/').ifBlank { "slider_$i" }
                            val item = fields.toHomeAutoSliderItem(id)
                            val active = fields.boolean("isActive", fields.boolean("active", true))
                            val status = fields.string("status").uppercase()
                            if (active && status !in setOf("INACTIVE", "DISABLED", "DRAFT", "PAUSED")) add(item)
                        }
                    }.sortedWith(compareByDescending<HomeAutoSliderItem> { it.priority }.thenBy { it.id })
                } finally {
                    connection.disconnect()
                }
            }
        }

    fun fromResolvedSlide(slide: com.example.ui.ads.ResolvedHomeSliderSlide, index: Int): HomeAutoSliderItem =
        HomeAutoSliderItem.fromResolvedSlide(slide, index)

    fun defaultBangladeshDemoSlides(): List<HomeAutoSliderItem> = listOf(
        HomeAutoSliderItem(
            id = "demo_doctor", category = "DOCTOR",
            titleBn = "বিশেষজ্ঞ ডাক্তার খুঁজুন", titleEn = "Find Specialist Doctors",
            subtitleBn = "আপনার প্রয়োজনীয় বিশেষজ্ঞের তথ্য এক জায়গায়",
            subtitleEn = "Find doctor information in one place",
            designationBn = "চিকিৎসা বিশেষজ্ঞ", designationEn = "Medical Specialist"
        ),
        HomeAutoSliderItem(
            id = "demo_schedule", category = "SCHEDULE",
            titleBn = "আজকের চেম্বার সময়সূচী", titleEn = "Today's Chamber Schedule",
            subtitleBn = "ডাক্তার কোথায় বসেন, কখন বসেন জানুন",
            subtitleEn = "Check where and when doctors see patients"
        ),
        HomeAutoSliderItem(
            id = "demo_hospital", category = "HOSPITAL",
            titleBn = "হাসপাতাল ও ক্লিনিক", titleEn = "Hospitals & Clinics",
            subtitleBn = "কাছাকাছি স্বাস্থ্যসেবা কেন্দ্র খুঁজুন",
            subtitleEn = "Discover nearby healthcare facilities"
        )
    )

    fun filterSlides(
        slides: List<HomeAutoSliderItem>,
        quickFilter: SliderQuickFilter,
        searchQuery: String
    ): List<HomeAutoSliderItem> {
        val filtered = when (quickFilter) {
            SliderQuickFilter.ALL -> slides
            SliderQuickFilter.TODAY -> slides.filter { it.category.contains("TODAY") || it.category.contains("SCHEDULE") }
            SliderQuickFilter.SCHEDULE -> slides.filter { it.category.contains("SCHEDULE") || it.category.contains("CHAMBER") }
            SliderQuickFilter.HOSPITAL -> slides.filter { it.category.contains("HOSPITAL") }
            SliderQuickFilter.DIAGNOSTIC -> slides.filter { it.category.contains("DIAGNOSTIC") }
            SliderQuickFilter.NOTICE -> slides.filter { it.category.contains("NOTICE") || it.category.contains("ANNOUNCEMENT") }
        }
        val query = searchQuery.trim()
        if (query.length < 2) return filtered
        return filtered.filter { item ->
            listOf(item.titleBn, item.titleEn, item.subtitleBn, item.subtitleEn,
                item.descriptionBn, item.descriptionEn, item.category).any {
                it.contains(query, ignoreCase = true)
            }
        }
    }

    fun extractInitials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "D" }

    private fun JSONObject.toHomeAutoSliderItem(id: String): HomeAutoSliderItem {
        fun value(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            if (has(key)) string(key).takeIf { it.isNotBlank() } else null
        }.orEmpty()
        return HomeAutoSliderItem(
            id = id,
            category = value("category", "type", "recordType").ifBlank { "NOTICE" }.uppercase(),
            titleBn = value("titleBn", "nameBn", "title", "name"),
            titleEn = value("titleEn", "nameEn", "title", "name"),
            subtitleBn = value("subtitleBn", "subtitle", "shortDescription"),
            subtitleEn = value("subtitleEn", "subtitle", "shortDescription"),
            descriptionBn = value("descriptionBn", "description"),
            descriptionEn = value("descriptionEn", "description"),
            specialtyBn = value("specialtyBn", "specialty"),
            specialtyEn = value("specialtyEn", "specialty"),
            designationBn = value("designationBn", "designation"),
            designationEn = value("designationEn", "designation"),
            addressBn = value("addressBn", "address", "location"),
            addressEn = value("addressEn", "address", "location"),
            imageUrl = value("imageUrl", "image", "photoUrl", "bannerUrl"),
            targetRecordType = value("targetRecordType", "linkedRecordType").uppercase(),
            targetRecordId = value("targetRecordId", "linkedRecordId"),
            destinationUrl = value("destinationUrl", "url", "link"),
            priority = int("priority", int("sortOrder", 0))
        )
    }

    private fun JSONObject.string(key: String): String {
        val field = optJSONObject(key) ?: return opt(key)?.takeIf { it !is JSONObject }?.toString().orEmpty()
        return field.optString("stringValue").ifBlank {
            field.optJSONObject("mapValue")?.toString().orEmpty()
        }
    }

    private fun JSONObject.boolean(key: String, fallback: Boolean): Boolean {
        val field = optJSONObject(key) ?: return optBoolean(key, fallback)
        return if (field.has("booleanValue")) field.optBoolean("booleanValue", fallback) else fallback
    }

    private fun JSONObject.int(key: String, fallback: Int): Int {
        val field = optJSONObject(key) ?: return optInt(key, fallback)
        return field.optString("integerValue").toIntOrNull()
            ?: field.optString("doubleValue").toDoubleOrNull()?.toInt()
            ?: fallback
    }
}
