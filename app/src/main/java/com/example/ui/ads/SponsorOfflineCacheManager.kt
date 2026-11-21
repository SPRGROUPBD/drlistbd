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

            if (logoPath.isNotBlank()) activeFileNames.add(File(logoPath).name)
            if (bannerPath.isNotBlank()) activeFileNames.add(File(bannerPath).name)

            if (logoPath != sp.localLogoPath || bannerPath != sp.localBannerPath || sp.cachedAt == 0L) {
                db.sponsorDao().updateLocalCachePaths(
                    id = sp.id,
                    logoPath = logoPath,
                    bannerPath = bannerPath,
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

    private fun ensureCachedAsset(
        cacheDir: File,
        sponsorId: String,
        kind: String,
        sourceUrl: String,
        existingPath: String,
        trackDownload: Boolean
    ): String {
        val cleanUrl = sourceUrl.trim()
        if (cleanUrl.isEmpty() || !SponsorCampaignSelector.isValidImageSource(cleanUrl)) {
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
