package com.example.ui.ads

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.SponsorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Persistent local image & campaign cache manager for Direct Sponsor Advertisements.
 * - Downloads approved active sponsor logos & banners into persistent app storage (`filesDir/sponsor_cache`).
 * - Validates HTTPS / base64 data URI, image format magic bytes (PNG/JPEG/WebP/GIF), and <= 3MB size limit.
 * - Never deletes valid cached sponsors or images simply because internet is temporarily unavailable.
 * - Cleans up expired campaign assets safely when online.
 */
object SponsorOfflineCacheManager {
    private const val TAG = "SponsorCacheManager"
    private const val CACHE_DIR_NAME = "sponsor_cache"

    fun getCacheDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Downloads and validates logo & banner assets for all active non-expired sponsors in Room,
     * preserving existing valid files and purging expired files only during online sync.
     */
    suspend fun syncSponsorAssets(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val allSponsors = db.sponsorDao().getAllOnce()
        val cacheDir = getCacheDir(context)
        val activeFileNames = mutableSetOf<String>()

        for (sp in allSponsors) {
            if (SponsorCampaignSelector.isCampaignExpired(sp, now) ||
                sp.status.equals(SponsorCampaignSelector.STATUS_CANCELLED, ignoreCase = true) ||
                sp.status.equals(SponsorCampaignSelector.STATUS_REJECTED, ignoreCase = true)
            ) {
                // Remove expired/cancelled campaign local files & Room cache so expired ads never appear online or offline
                deleteFileQuietly(sp.localLogoPath)
                deleteFileQuietly(sp.localBannerPath)
                deleteFileQuietly(sp.localVideoPath)
                db.sponsorDao().deleteById(sp.id)
                continue
            }

            val logoPath = ensureCachedAsset(
                cacheDir = cacheDir,
                sponsorId = sp.id,
                kind = "logo",
                sourceUrl = sp.logoUrl,
                existingPath = sp.localLogoPath,
                trackDownload = false
            )
            val bannerPath = ensureCachedAsset(
                cacheDir = cacheDir,
                sponsorId = sp.id,
                kind = "banner",
                sourceUrl = sp.bannerImageUrl,
                existingPath = sp.localBannerPath,
                trackDownload = true
            )
            val videoPath = ensureCachedVideoAsset(
                cacheDir = cacheDir,
                sponsorId = sp.id,
                sourceUrl = sp.videoUrl,
                existingPath = sp.localVideoPath
            )

            if (logoPath.isNotBlank()) activeFileNames.add(File(logoPath).name)
            if (bannerPath.isNotBlank()) activeFileNames.add(File(bannerPath).name)
            if (videoPath.isNotBlank()) activeFileNames.add(File(videoPath).name)

            if (logoPath != sp.localLogoPath || bannerPath != sp.localBannerPath || videoPath != sp.localVideoPath || sp.cachedAt == 0L) {
                db.sponsorDao().updateLocalCachePaths(
                    id = sp.id,
                    logoPath = logoPath,
                    bannerPath = bannerPath,
                    videoPath = videoPath,
                    cachedAt = now
                )
            }
        }

        // Prune orphaned files that no longer belong to any non-expired sponsor
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in activeFileNames) {
                runCatching { file.delete() }
            }
        }
    }

    /**
     * Offloads a base64 `data:video/...` URI directly to persistent local storage so large MP4 payloads
     * are NEVER inserted raw into SQLite/Room (which would trigger SQLiteBlobTooBigException > 2MB).
     */
    fun cacheDataUriVideoToFile(context: Context, sponsorId: String, dataUri: String): String {
        val clean = dataUri.trim()
        if (!clean.startsWith("data:video/", ignoreCase = true)) return ""
        return runCatching {
            val bytes = decodeDataUriBytes(clean) ?: return ""
            if (!SponsorCampaignSelector.isSupportedVideoBytes(bytes)) return ""
            val cacheDir = getCacheDir(context)
            val ext = if (clean.lowercase().contains("webm")) "webm" else "mp4"
            val hash = sha256Short("${sponsorId}_video_${bytes.size}_${bytes.take(32).joinToString(",")}")
            val outFile = File(cacheDir, "${sanitizeId(sponsorId)}_video_${hash}.$ext")
            if (!outFile.exists() || outFile.length() != bytes.size.toLong()) {
                outFile.writeBytes(bytes)
            }
            outFile.absolutePath
        }.getOrElse { e ->
            Log.w(TAG, "Failed to decode base64 video for $sponsorId: ${e.message}")
            ""
        }
    }

    /**
     * Offloads an oversized `data:image/...` URI directly to disk before Room insertion.
     */
    fun cacheDataUriImageToFile(context: Context, sponsorId: String, kind: String, dataUri: String): String {
        val clean = dataUri.trim()
        if (!clean.startsWith("data:image/", ignoreCase = true)) return ""
        return runCatching {
            val bytes = decodeDataUriBytes(clean) ?: return ""
            val saved = saveValidatedImageToCache(getCacheDir(context), sponsorId, kind, clean.take(128), bytes)
            saved?.absolutePath.orEmpty()
        }.getOrElse { "" }
    }

    /**
     * Resolves a safe local file path or streaming URI for Direct Sponsor Reward Video playback.
     * Prefers already-cached local MP4 file (works 100% offline & zero streaming crash risk).
     */
    fun resolvePlayableVideoFile(localVideoPath: String): File? {
        if (localVideoPath.isBlank()) return null
        val f = File(localVideoPath)
        return if (f.exists() && f.isFile && f.length() > 100L) f else null
    }

    /**
     * Resolves the best available model for Coil: local persisted cached file FIRST (works 100% offline),
     * falling back to validated HTTPS/data URL only when online.
     */
    fun resolveImageModel(localPath: String, remoteUrl: String, isOnline: Boolean): Any? {
        if (localPath.isNotBlank()) {
            val file = File(localPath)
            if (file.exists() && file.length() > 12L) {
                return file
            }
        }
        if (remoteUrl.startsWith("data:image/", ignoreCase = true)) {
            return decodeDataUriBytes(remoteUrl)
        }
        if (isOnline && SponsorCampaignSelector.isValidImageSource(remoteUrl)) {
            return remoteUrl.trim()
        }
        return null
    }

    /**
     * Saves validated image bytes directly to cache directory (used by sync and unit tests).
     */
    fun saveValidatedImageToCache(
        cacheDir: File,
        sponsorId: String,
        kind: String,
        sourceUrl: String,
        bytes: ByteArray
    ): File? {
        if (!SponsorCampaignSelector.isSupportedImageBytes(bytes)) {
            Log.w(TAG, "Rejected invalid/oversized sponsor image ($sponsorId/$kind, size=${bytes.size})")
            return null
        }
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val ext = detectExtension(bytes)
        val hash = sha256Short("${sponsorId}_${kind}_${sourceUrl}")
        val outFile = File(cacheDir, "${sanitizeId(sponsorId)}_${kind}_${hash}.$ext")
        outFile.writeBytes(bytes)
        return outFile
    }

    private fun ensureCachedVideoAsset(
        cacheDir: File,
        sponsorId: String,
        sourceUrl: String,
        existingPath: String
    ): String {
        val cleanUrl = sourceUrl.trim()
        val existingFile = existingPath.takeIf { it.isNotBlank() }?.let { File(it) }
        val existingValid = existingFile != null && existingFile.exists() && existingFile.length() > 100L

        if (cleanUrl.isEmpty() || cleanUrl.startsWith("cached://", ignoreCase = true)) {
            return if (existingValid) existingFile!!.absolutePath else ""
        }
        if (cleanUrl.startsWith("data:video/", ignoreCase = true)) {
            if (existingValid) return existingFile!!.absolutePath
            val bytes = decodeDataUriBytes(cleanUrl) ?: return ""
            if (!SponsorCampaignSelector.isSupportedVideoBytes(bytes)) return ""
            val ext = if (cleanUrl.lowercase().contains("webm")) "webm" else "mp4"
            val hash = sha256Short("${sponsorId}_video_${bytes.size}")
            val outFile = File(cacheDir, "${sanitizeId(sponsorId)}_video_${hash}.$ext")
            outFile.writeBytes(bytes)
            return outFile.absolutePath
        }
        if (!cleanUrl.startsWith("https://", ignoreCase = true) && !cleanUrl.startsWith("http://", ignoreCase = true)) {
            return if (existingValid) existingFile!!.absolutePath else ""
        }

        val expectedHash = sha256Short("${sponsorId}_video_${cleanUrl}")
        if (existingValid && existingFile!!.name.contains(expectedHash)) {
            return existingFile.absolutePath
        }

        val downloaded = downloadValidatedVideoBytes(cleanUrl)
            ?: return if (existingValid) existingFile!!.absolutePath else ""
        if (!SponsorCampaignSelector.isSupportedVideoBytes(downloaded)) {
            return if (existingValid) existingFile!!.absolutePath else ""
        }
        val ext = if (cleanUrl.lowercase().contains(".webm")) "webm" else "mp4"
        val outFile = File(cacheDir, "${sanitizeId(sponsorId)}_video_${expectedHash}.$ext")
        return runCatching {
            outFile.writeBytes(downloaded)
            Log.d(TAG, "Cached sponsor video for $sponsorId -> ${outFile.name} (${downloaded.size} bytes)")
            outFile.absolutePath
        }.getOrElse {
            if (existingValid) existingFile!!.absolutePath else ""
        }
    }

    private fun downloadValidatedVideoBytes(urlStr: String): ByteArray? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val contentLength = conn.contentLengthLong
            if (contentLength > SponsorCampaignSelector.MAX_VIDEO_BYTES) {
                conn.disconnect()
                return null
            }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16384)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    total += read
                    if (total > SponsorCampaignSelector.MAX_VIDEO_BYTES) {
                        conn.disconnect()
                        return null
                    }
                    out.write(buf, 0, read)
                }
            }
            conn.disconnect()
            out.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Sponsor video download skipped ($urlStr): ${e.message}")
            null
        }
    }

    private fun ensureCachedAsset(
        cacheDir: File,
        sponsorId: String,
        kind: String,
        sourceUrl: String,
        existingPath: String,
        trackDownload: Boolean
    ): String {
        val cleanUrl = sourceUrl.trim()
        if (cleanUrl.isEmpty() || cleanUrl.startsWith("cached://", ignoreCase = true) || !SponsorCampaignSelector.isValidImageSource(cleanUrl)) {
            return existingPath.takeIf { it.isNotBlank() && File(it).exists() }.orEmpty()
        }

        val expectedHash = sha256Short("${sponsorId}_${kind}_${cleanUrl}")
        if (existingPath.isNotBlank()) {
            val existingFile = File(existingPath)
            if (existingFile.exists() && existingFile.length() > 12L && existingFile.name.contains(expectedHash)) {
                return existingFile.absolutePath
            }
        }

        val bytes = if (cleanUrl.startsWith("data:image/", ignoreCase = true)) {
            decodeDataUriBytes(cleanUrl)
        } else {
            downloadValidatedBytes(cleanUrl)
        } ?: return existingPath.takeIf { it.isNotBlank() && File(it).exists() }.orEmpty()

        val saved = saveValidatedImageToCache(cacheDir, sponsorId, kind, cleanUrl, bytes)
            ?: return existingPath.takeIf { it.isNotBlank() && File(it).exists() }.orEmpty()

        if (trackDownload) {
            AdRevenueTracker.recordSponsorDownload(sponsorId)
        }
        Log.d(TAG, "Cached sponsor $kind for $sponsorId -> ${saved.name} (${bytes.size} bytes)")
        return saved.absolutePath
    }

    private fun downloadValidatedBytes(urlStr: String): ByteArray? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val contentLength = conn.contentLengthLong
            if (contentLength > SponsorCampaignSelector.MAX_IMAGE_BYTES) {
                conn.disconnect()
                return null
            }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    total += read
                    if (total > SponsorCampaignSelector.MAX_IMAGE_BYTES) {
                        conn.disconnect()
                        return null
                    }
                    out.write(buf, 0, read)
                }
            }
            conn.disconnect()
            out.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Sponsor image download skipped ($urlStr): ${e.message}")
            null
        }
    }

    private fun decodeDataUriBytes(dataUri: String): ByteArray? {
        return try {
            val comma = dataUri.indexOf(',')
            if (comma <= 0) return null
            val b64 = dataUri.substring(comma + 1)
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    private fun detectExtension(bytes: ByteArray): String = when {
        bytes[0] == 0x89.toByte() -> "png"
        bytes[0] == 0xFF.toByte() -> "jpg"
        bytes[0] == 'R'.code.toByte() -> "webp"
        else -> "gif"
    }

    private fun sanitizeId(id: String): String =
        id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun deleteFileQuietly(path: String) {
        if (path.isNotBlank()) runCatching { File(path).delete() }
    }
}
