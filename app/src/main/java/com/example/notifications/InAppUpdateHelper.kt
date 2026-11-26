package com.example.notifications

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state emitted by Google Play In-App Updates for Flexible download progress
 * and restart-to-install readiness.
 */
data class InAppUpdateBannerState(
    val isDownloading: Boolean = false,
    val progressPercent: Int = 0,
    val isReadyToInstall: Boolean = false
)

/**
 * Official Google Play In-App Updates manager for Doctor List BD.
 * Supports Flexible and Immediate update modes without ever simulating fake updates.
 */
class InAppUpdateHelper(context: Context) {
    private val appContext = context.applicationContext
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)

    private val _bannerState = MutableStateFlow(InAppUpdateBannerState())
    val bannerState: StateFlow<InAppUpdateBannerState> = _bannerState.asStateFlow()

    private var onUserMessage: ((String) -> Unit)? = null
    private var currentLang: AppLang = AppLang.BN

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val downloaded = state.bytesDownloaded()
                val total = state.totalBytesToDownload()
                val pct = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                _bannerState.value = InAppUpdateBannerState(
                    isDownloading = true,
                    progressPercent = pct,
                    isReadyToInstall = false
                )
            }
            InstallStatus.DOWNLOADED -> {
                _bannerState.value = InAppUpdateBannerState(
                    isDownloading = false,
                    progressPercent = 100,
                    isReadyToInstall = true
                )
                onUserMessage?.invoke(
                    tr(
                        currentLang,
                        "নতুন আপডেট ডাউনলোড সম্পন্ন হয়েছে। ইনস্টল করতে রিস্টার্ট করুন।",
                        "Update downloaded. Tap Restart to install."
                    )
                )
            }
            InstallStatus.FAILED -> {
                _bannerState.value = InAppUpdateBannerState()
                onUserMessage?.invoke(
                    tr(
                        currentLang,
                        "অ্যাপ আপডেট ডাউনলোড ব্যর্থ হয়েছে। অনুগ্রহ করে পরে আবার চেষ্টা করুন।",
                        "App update download failed. Please try again later."
                    )
                )
            }
            InstallStatus.CANCELED -> {
                _bannerState.value = InAppUpdateBannerState()
            }
            InstallStatus.INSTALLED -> {
                _bannerState.value = InAppUpdateBannerState()
            }
            else -> Unit
        }
    }

    fun registerListener(lang: AppLang, onMessage: (String) -> Unit) {
        currentLang = lang
        onUserMessage = onMessage
        runCatching { appUpdateManager.registerListener(installStateListener) }
    }

    fun unregisterListener() {
        runCatching { appUpdateManager.unregisterListener(installStateListener) }
        onUserMessage = null
    }

    /**
     * Checks Google Play for an available update and launches either Flexible or Immediate flow.
     * Reports real Play Store status cleanly (never fakes an update).
     */
    fun checkAndStartUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        updateMode: String,
        lang: AppLang,
        isOnline: Boolean,
        onStatusMessage: (String) -> Unit
    ) {
        currentLang = lang
        onUserMessage = onStatusMessage

        if (!isOnline) {
            onStatusMessage(
                tr(
                    lang,
                    "ইন্টারনেট সংযোগ নেই। আপডেট চেক করতে ইন্টারনেট চালু করুন।",
                    "No internet connection. Connect to the internet to check for updates."
                )
            )
            return
        }

        val preferImmediate = updateMode.trim().equals("IMMEDIATE", ignoreCase = true)
        val requestedType = if (preferImmediate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                val availability = info.updateAvailability()
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _bannerState.value = InAppUpdateBannerState(isReadyToInstall = true, progressPercent = 100)
                    onStatusMessage(
                        tr(
                            lang,
                            "আপডেট ইতিমধ্যেই ডাউনলোড করা আছে। ইনস্টল করতে রিস্টার্ট করুন।",
                            "Update is already downloaded. Tap Restart to install."
                        )
                    )
                    return@addOnSuccessListener
                }

                if (availability == UpdateAvailability.UPDATE_AVAILABLE ||
                    availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    val allowedType = when {
                        info.isUpdateTypeAllowed(requestedType) -> requestedType
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                        else -> null
                    }
                    if (allowedType != null) {
                        val started = runCatching {
                            appUpdateManager.startUpdateFlowForResult(
                                info,
                                launcher,
                                AppUpdateOptions.newBuilder(allowedType).build()
                            )
                        }.getOrDefault(false)
                        if (!started) {
                            onStatusMessage(
                                tr(
                                    lang,
                                    "গুগল প্লে আপডেট চালু করা যায়নি। পরে আবার চেষ্টা করুন।",
                                    "Could not start Google Play update flow. Please try again."
                                )
                            )
                        }
                    } else {
                        onStatusMessage(
                            tr(
                                lang,
                                "এই ডিভাইসে ইন-অ্যাপ আপডেট সমর্থিত নয়। গুগল প্লে স্টোর থেকে আপডেট করুন।",
                                "In-app update mode is not supported on this device. Please update from Google Play."
                            )
                        )
                    }
                } else {
                    onStatusMessage(
                        tr(
                            lang,
                            "আপনার Doctor List BD অ্যাপটি ইতিমধ্যেই সর্বশেষ ভার্সনে আছে।",
                            "Your Doctor List BD app is already up to date."
                        )
                    )
                }
            }
            .addOnFailureListener { err ->
                Log.w(TAG, "Play In-App Update check failed: ${err.message}")
                onStatusMessage(
                    tr(
                        lang,
                        "গুগল প্লে স্টোর থেকে আপডেট যাচাই করা যায়নি (ডিভাইসে প্লে স্টোর সক্রিয় নাও থাকতে পারে)।",
                        "Unable to check Google Play for updates right now."
                    )
                )
            }
    }

    /**
     * Resumes any in-progress Immediate update or surfaces a downloaded Flexible update on resume.
     */
    fun resumeInProgressIfNeeded(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        lang: AppLang
    ) {
        currentLang = lang
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                _bannerState.value = InAppUpdateBannerState(isReadyToInstall = true, progressPercent = 100)
            } else if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                runCatching {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        launcher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }
            }
        }
    }

    fun completeFlexibleUpdate() {
        runCatching { appUpdateManager.completeUpdate() }
            .onFailure { Log.w(TAG, "completeUpdate failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "InAppUpdateHelper"
    }
}
