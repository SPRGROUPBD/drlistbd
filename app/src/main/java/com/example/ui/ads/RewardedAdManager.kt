package com.example.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates Google AdMob Rewarded Ads and Unity Ads Rewarded Ads for the
 * Doctor Profile Click -> Reward Completed -> Open Doctor Profile flow.
 *
 * - Dynamically synced from Firebase RTDB (/advertising/admob) managed in Admin Panel.
 * - Preloads active AdMob & Unity Rewarded placements in the background for instant display.
 * - Supports Admin-selected network waterfall (ADMOB_FIRST, UNITY_FIRST, ADMOB_ONLY, UNITY_ONLY)
 *   and optional cooldown seconds.
 * - Never blocks the user permanently: if offline, timed out, or all configured networks fail to fill,
 *   opens the Doctor Profile gracefully.
 */
object RewardedAdManager {
    private const val TAG = "RewardedAdManager"
    private const val ON_DEMAND_LOAD_TIMEOUT_MS = 6500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    // AdMob Rewarded state
    @Volatile
    private var preloadedAdMobAd: RewardedAd? = null
    @Volatile
    private var preloadedAdMobUnitId: String = ""
    @Volatile
    private var isPreloadingAdMob: Boolean = false
    private var admobRotationCursor: Int = 0

    // Unity Ads state
    @Volatile
    private var unityInitializedGameId: String = ""
    @Volatile
    private var unityInitializedTestMode: Boolean = true
    @Volatile
    private var unityInitInProgress: Boolean = false
    private val unityInitWaiters = mutableListOf<(Boolean) -> Unit>()
    private val loadedUnityPlacements = mutableSetOf<String>()
    private val loadingUnityPlacements = mutableSetOf<String>()
    private var unityRotationCursor: Int = 0

    // Session cooldown & active request tracking
    @Volatile
    private var lastRewardShownElapsedMs: Long = 0L
    @Volatile
    private var activeFlowInProgress: Boolean = false

    private enum class NetworkType { ADMOB, UNITY }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { block() }
        } else {
            mainHandler.post { runCatching { block() } }
        }
    }

    /**
     * Syncs the latest Admin Panel AdMob & Unity Ads config and preloads active rewarded placements.
     */
    fun syncConfig(
        context: Context,
        config: AdMobRemoteConfig,
        placementSettings: PlacementGlobalSettings
    ) {
        val appContext = context.applicationContext
        if (!placementSettings.adsEnabled || !config.isDoctorClickRewardActive) {
            preloadedAdMobAd = null
            preloadedAdMobUnitId = ""
            synchronized(lock) { loadedUnityPlacements.clear() }
            return
        }

        val admobIds = config.activeRewardedUnitIds()
        if (admobIds.isNotEmpty()) {
            val targetId = admobIds.first()
            if (preloadedAdMobUnitId != targetId) {
                preloadedAdMobAd = null
                preloadedAdMobUnitId = ""
            }
            if (preloadedAdMobAd == null && !isPreloadingAdMob) {
                preloadAdMobRewarded(appContext, targetId)
            }
        } else {
            preloadedAdMobAd = null
            preloadedAdMobUnitId = ""
        }

        val unityIds = config.activeUnityRewardedPlacementIds()
        if (unityIds.isNotEmpty() && config.unityGameId.isNotBlank()) {
            val targetPlacement = unityIds.first()
            ensureUnityInitialized(appContext, config.unityGameId, config.unityTestMode) { ready ->
                if (ready) {
                    preloadUnityRewarded(targetPlacement)
                }
            }
        }
    }

    fun shouldShowRewardedForDoctorClick(
        config: AdMobRemoteConfig,
        placementSettings: PlacementGlobalSettings
    ): Boolean {
        if (!placementSettings.adsEnabled) return false
        if (!config.isDoctorClickRewardActive) return false
        val cooldownMs = config.rewardedCooldownSec.coerceAtLeast(0) * 1000L
        if (cooldownMs > 0L && lastRewardShownElapsedMs > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - lastRewardShownElapsedMs
            if (elapsed < cooldownMs) return false
        }
        return true
    }

    /**
     * Shows a Rewarded Ad (AdMob or Unity per Admin Panel priority with automatic waterfall fallback)
     * before navigating to a Doctor Profile.
     *
     * - [onAdOpening]: called right as the fullscreen ad launches (to hide any loading indicator).
     * - [onUnlocked]: called once the user earns the reward (or if ads are unavailable/failed/offline).
     * - [onIncomplete]: called if the user closes/skips the rewarded ad before completing the reward.
     */
    fun showRewardedForDoctorClick(
        activity: Activity,
        config: AdMobRemoteConfig,
        placementSettings: PlacementGlobalSettings,
        isOnline: Boolean,
        onAdOpening: () -> Unit,
        onUnlocked: () -> Unit,
        onIncomplete: () -> Unit
    ) {
        if (!shouldShowRewardedForDoctorClick(config, placementSettings) || !isOnline) {
            postToMain { onUnlocked() }
            return
        }
        if (activeFlowInProgress) return
        activeFlowInProgress = true

        val finished = AtomicBoolean(false)
        fun finishUnlocked() {
            if (finished.compareAndSet(false, true)) {
                activeFlowInProgress = false
                postToMain { onUnlocked() }
            }
        }

        fun finishIncomplete() {
            if (finished.compareAndSet(false, true)) {
                activeFlowInProgress = false
                postToMain { onIncomplete() }
            }
        }

        val admobIds = config.activeRewardedUnitIds()
        val unityIds = config.activeUnityRewardedPlacementIds()
        val networkOrder = buildNetworkOrder(
            priority = config.rewardedNetworkPriority,
            hasAdMob = admobIds.isNotEmpty(),
            hasUnity = unityIds.isNotEmpty()
        )

        if (networkOrder.isEmpty()) {
            finishUnlocked()
            return
        }

        fun tryNext(index: Int) {
            if (finished.get()) return
            if (index >= networkOrder.size || activity.isFinishing || activity.isDestroyed) {
                finishUnlocked()
                return
            }
            when (networkOrder[index]) {
                NetworkType.ADMOB -> {
                    val unitId = pickNextAdMobId(admobIds)
                    attemptAdMobRewarded(
                        activity = activity,
                        unitId = unitId,
                        onAdOpening = { postToMain { onAdOpening() } },
                        onRewardEarnedAndClosed = {
                            lastRewardShownElapsedMs = SystemClock.elapsedRealtime()
                            preloadAdMobRewarded(activity.applicationContext, unitId)
                            finishUnlocked()
                        },
                        onClosedWithoutReward = {
                            preloadAdMobRewarded(activity.applicationContext, unitId)
                            finishIncomplete()
                        },
                        onUnavailable = { tryNext(index + 1) }
                    )
                }
                NetworkType.UNITY -> {
                    val placementId = pickNextUnityPlacementId(unityIds)
                    attemptUnityRewarded(
                        activity = activity,
                        gameId = config.unityGameId,
                        testMode = config.unityTestMode,
                        placementId = placementId,
                        onAdOpening = { postToMain { onAdOpening() } },
                        onRewardEarnedAndClosed = {
                            lastRewardShownElapsedMs = SystemClock.elapsedRealtime()
                            preloadUnityRewarded(placementId)
                            finishUnlocked()
                        },
                        onClosedWithoutReward = {
                            preloadUnityRewarded(placementId)
                            finishIncomplete()
                        },
                        onUnavailable = { tryNext(index + 1) }
                    )
                }
            }
        }

        postToMain { tryNext(0) }
    }

    private fun buildNetworkOrder(
        priority: String,
        hasAdMob: Boolean,
        hasUnity: Boolean
    ): List<NetworkType> {
        val list = mutableListOf<NetworkType>()
        when (RewardedNetworkPriorities.normalize(priority)) {
            RewardedNetworkPriorities.UNITY_FIRST -> {
                if (hasUnity) list.add(NetworkType.UNITY)
                if (hasAdMob) list.add(NetworkType.ADMOB)
            }
            RewardedNetworkPriorities.ADMOB_ONLY -> {
                if (hasAdMob) list.add(NetworkType.ADMOB)
            }
            RewardedNetworkPriorities.UNITY_ONLY -> {
                if (hasUnity) list.add(NetworkType.UNITY)
            }
            else -> {
                if (hasAdMob) list.add(NetworkType.ADMOB)
                if (hasUnity) list.add(NetworkType.UNITY)
            }
        }
        return list
    }

    private fun pickNextAdMobId(ids: List<String>): String {
        if (ids.size == 1) return ids.first()
        val idx = synchronized(lock) {
            val current = Math.floorMod(admobRotationCursor, ids.size)
            admobRotationCursor = (current + 1) % ids.size
            current
        }
        return ids[idx]
    }

    private fun pickNextUnityPlacementId(ids: List<String>): String {
        if (ids.size == 1) return ids.first()
        val idx = synchronized(lock) {
            val current = Math.floorMod(unityRotationCursor, ids.size)
            unityRotationCursor = (current + 1) % ids.size
            current
        }
        return ids[idx]
    }

    // =========================================================================
    // GOOGLE ADMOB REWARDED AD
    // =========================================================================

    private fun preloadAdMobRewarded(context: Context, unitId: String) {
        val cleanId = unitId.trim()
        if (cleanId.isBlank() || isPreloadingAdMob) return
        isPreloadingAdMob = true
        AdMobInitializer.runWhenInitialized(context) {
            loadAdMobRewardedInternal(
                context = context,
                unitId = cleanId,
                allowDebugTestFallback = true,
                onLoaded = { ad, loadedId ->
                    isPreloadingAdMob = false
                    preloadedAdMobAd = ad
                    preloadedAdMobUnitId = loadedId
                    Log.d(TAG, "Preloaded AdMob Rewarded ($loadedId)")
                },
                onFailed = {
                    isPreloadingAdMob = false
                }
            )
        }
    }

    private fun loadAdMobRewardedInternal(
        context: Context,
        unitId: String,
        allowDebugTestFallback: Boolean,
        onLoaded: (RewardedAd, String) -> Unit,
        onFailed: () -> Unit
    ) {
        postToMain {
            runCatching {
                RewardedAd.load(
                    context,
                    unitId,
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            onLoaded(ad, unitId)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.w(TAG, "AdMob Rewarded failed ($unitId): code=${error.code} ${error.message}")
                            val canFallbackToTest = allowDebugTestFallback &&
                                (BuildConfig.DEBUG || AdMobInitializer.isRunningOnEmulator()) &&
                                unitId != AdMobRemoteConfig.DEFAULT_TEST_REWARDED_ID
                            if (canFallbackToTest) {
                                Log.i(TAG, "Falling back to Official Google Test Rewarded Ad on debug/emulator")
                                loadAdMobRewardedInternal(
                                    context = context,
                                    unitId = AdMobRemoteConfig.DEFAULT_TEST_REWARDED_ID,
                                    allowDebugTestFallback = false,
                                    onLoaded = onLoaded,
                                    onFailed = onFailed
                                )
                            } else {
                                onFailed()
                            }
                        }
                    }
                )
            }.onFailure { err ->
                Log.w(TAG, "AdMob Rewarded load exception ($unitId): ${err.message}")
                onFailed()
            }
        }
    }

    private fun attemptAdMobRewarded(
        activity: Activity,
        unitId: String,
        onAdOpening: () -> Unit,
        onRewardEarnedAndClosed: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val readyAd = if (preloadedAdMobUnitId == unitId ||
            (preloadedAdMobUnitId == AdMobRemoteConfig.DEFAULT_TEST_REWARDED_ID &&
                (BuildConfig.DEBUG || AdMobInitializer.isRunningOnEmulator()))
        ) {
            preloadedAdMobAd.also {
                preloadedAdMobAd = null
                preloadedAdMobUnitId = ""
            }
        } else {
            null
        }

        if (readyAd != null) {
            presentAdMobRewarded(
                activity = activity,
                rewardedAd = readyAd,
                onAdOpening = onAdOpening,
                onRewardEarnedAndClosed = onRewardEarnedAndClosed,
                onClosedWithoutReward = onClosedWithoutReward,
                onFailedToShow = onUnavailable
            )
            return
        }

        val resolved = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (resolved.compareAndSet(false, true)) {
                Log.w(TAG, "AdMob Rewarded load timed out ($unitId)")
                onUnavailable()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, ON_DEMAND_LOAD_TIMEOUT_MS)

        AdMobInitializer.runWhenInitialized(activity) {
            loadAdMobRewardedInternal(
                context = activity,
                unitId = unitId,
                allowDebugTestFallback = true,
                onLoaded = { ad, _ ->
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (resolved.compareAndSet(false, true)) {
                        presentAdMobRewarded(
                            activity = activity,
                            rewardedAd = ad,
                            onAdOpening = onAdOpening,
                            onRewardEarnedAndClosed = onRewardEarnedAndClosed,
                            onClosedWithoutReward = onClosedWithoutReward,
                            onFailedToShow = onUnavailable
                        )
                    } else {
                        // Save for next doctor click if timeout already moved on
                        preloadedAdMobAd = ad
                        preloadedAdMobUnitId = unitId
                    }
                },
                onFailed = {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (resolved.compareAndSet(false, true)) {
                        onUnavailable()
                    }
                }
            )
        }
    }

    private fun presentAdMobRewarded(
        activity: Activity,
        rewardedAd: RewardedAd,
        onAdOpening: () -> Unit,
        onRewardEarnedAndClosed: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onFailedToShow: () -> Unit
    ) {
        postToMain {
            if (activity.isFinishing || activity.isDestroyed) {
                onFailedToShow()
                return@postToMain
            }
            var rewardEarned = false
            rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    onAdOpening()
                }

                override fun onAdDismissedFullScreenContent() {
                    if (rewardEarned) {
                        onRewardEarnedAndClosed()
                    } else {
                        onClosedWithoutReward()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "AdMob Rewarded show failed: ${adError.message}")
                    onFailedToShow()
                }
            }
            runCatching {
                onAdOpening()
                rewardedAd.show(activity) { rewardItem ->
                    Log.d(TAG, "AdMob Reward earned: ${rewardItem.amount} ${rewardItem.type}")
                    rewardEarned = true
                }
            }.onFailure { err ->
                Log.w(TAG, "AdMob Rewarded show exception: ${err.message}")
                onFailedToShow()
            }
        }
    }

    // =========================================================================
    // UNITY ADS REWARDED AD
    // =========================================================================

    private fun ensureUnityInitialized(
        context: Context,
        gameId: String,
        testMode: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        val cleanGameId = gameId.trim()
        if (cleanGameId.isBlank()) {
            postToMain { onResult(false) }
            return
        }

        var shouldStartInit = false
        synchronized(lock) {
            if (UnityAds.isInitialized && unityInitializedGameId == cleanGameId && unityInitializedTestMode == testMode) {
                postToMain { onResult(true) }
                return
            }
            unityInitWaiters.add(onResult)
            if (!unityInitInProgress) {
                unityInitInProgress = true
                shouldStartInit = true
            }
        }

        if (!shouldStartInit) return

        postToMain {
            runCatching {
                UnityAds.initialize(
                    context.applicationContext,
                    cleanGameId,
                    testMode,
                    object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            Log.d(TAG, "Unity Ads initialized (gameId=$cleanGameId, testMode=$testMode)")
                            val waiters = synchronized(lock) {
                                unityInitializedGameId = cleanGameId
                                unityInitializedTestMode = testMode
                                unityInitInProgress = false
                                loadedUnityPlacements.clear()
                                val copy = unityInitWaiters.toList()
                                unityInitWaiters.clear()
                                copy
                            }
                            postToMain { waiters.forEach { it(true) } }
                        }

                        override fun onInitializationFailed(
                            error: UnityAds.UnityAdsInitializationError?,
                            message: String?
                        ) {
                            Log.w(TAG, "Unity Ads init failed: $error - $message")
                            val waiters = synchronized(lock) {
                                unityInitInProgress = false
                                val copy = unityInitWaiters.toList()
                                unityInitWaiters.clear()
                                copy
                            }
                            postToMain { waiters.forEach { it(false) } }
                        }
                    }
                )
            }.onFailure { err ->
                Log.w(TAG, "Unity Ads initialize exception: ${err.message}")
                val waiters = synchronized(lock) {
                    unityInitInProgress = false
                    val copy = unityInitWaiters.toList()
                    unityInitWaiters.clear()
                    copy
                }
                postToMain { waiters.forEach { it(false) } }
            }
        }
    }

    private fun preloadUnityRewarded(placementId: String) {
        val cleanPlacement = placementId.trim()
        if (cleanPlacement.isBlank() || !UnityAds.isInitialized) return
        synchronized(lock) {
            if (cleanPlacement in loadedUnityPlacements || cleanPlacement in loadingUnityPlacements) return
            loadingUnityPlacements.add(cleanPlacement)
        }
        postToMain {
            runCatching {
                UnityAds.load(
                    cleanPlacement,
                    object : IUnityAdsLoadListener {
                        override fun onUnityAdsAdLoaded(loadedId: String?) {
                            val key = loadedId ?: cleanPlacement
                            synchronized(lock) {
                                loadingUnityPlacements.remove(cleanPlacement)
                                loadedUnityPlacements.add(key)
                            }
                            Log.d(TAG, "Preloaded Unity Rewarded ($key)")
                        }

                        override fun onUnityAdsFailedToLoad(
                            failedId: String?,
                            error: UnityAds.UnityAdsLoadError?,
                            message: String?
                        ) {
                            synchronized(lock) {
                                loadingUnityPlacements.remove(cleanPlacement)
                                loadedUnityPlacements.remove(cleanPlacement)
                            }
                            Log.w(TAG, "Unity Rewarded preload failed ($cleanPlacement): $error - $message")
                        }
                    }
                )
            }.onFailure { err ->
                synchronized(lock) { loadingUnityPlacements.remove(cleanPlacement) }
                Log.w(TAG, "Unity preload exception ($cleanPlacement): ${err.message}")
            }
        }
    }

    private fun attemptUnityRewarded(
        activity: Activity,
        gameId: String,
        testMode: Boolean,
        placementId: String,
        onAdOpening: () -> Unit,
        onRewardEarnedAndClosed: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val cleanPlacement = placementId.trim()
        if (gameId.isBlank() || cleanPlacement.isBlank()) {
            onUnavailable()
            return
        }

        val resolved = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (resolved.compareAndSet(false, true)) {
                Log.w(TAG, "Unity Rewarded load timed out ($cleanPlacement)")
                onUnavailable()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, ON_DEMAND_LOAD_TIMEOUT_MS)

        ensureUnityInitialized(activity.applicationContext, gameId, testMode) { initialized ->
            if (!initialized) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (resolved.compareAndSet(false, true)) onUnavailable()
                return@ensureUnityInitialized
            }

            val alreadyLoaded = synchronized(lock) { loadedUnityPlacements.remove(cleanPlacement) }
            if (alreadyLoaded) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (resolved.compareAndSet(false, true)) {
                    presentUnityRewarded(
                        activity = activity,
                        placementId = cleanPlacement,
                        onAdOpening = onAdOpening,
                        onRewardEarnedAndClosed = onRewardEarnedAndClosed,
                        onClosedWithoutReward = onClosedWithoutReward,
                        onFailedToShow = onUnavailable
                    )
                }
                return@ensureUnityInitialized
            }

            postToMain {
                runCatching {
                    UnityAds.load(
                        cleanPlacement,
                        object : IUnityAdsLoadListener {
                            override fun onUnityAdsAdLoaded(loadedId: String?) {
                                mainHandler.removeCallbacks(timeoutRunnable)
                                if (resolved.compareAndSet(false, true)) {
                                    presentUnityRewarded(
                                        activity = activity,
                                        placementId = loadedId ?: cleanPlacement,
                                        onAdOpening = onAdOpening,
                                        onRewardEarnedAndClosed = onRewardEarnedAndClosed,
                                        onClosedWithoutReward = onClosedWithoutReward,
                                        onFailedToShow = onUnavailable
                                    )
                                } else {
                                    synchronized(lock) { loadedUnityPlacements.add(loadedId ?: cleanPlacement) }
                                }
                            }

                            override fun onUnityAdsFailedToLoad(
                                failedId: String?,
                                error: UnityAds.UnityAdsLoadError?,
                                message: String?
                            ) {
                                Log.w(TAG, "Unity Rewarded failed ($cleanPlacement): $error - $message")
                                mainHandler.removeCallbacks(timeoutRunnable)
                                if (resolved.compareAndSet(false, true)) {
                                    onUnavailable()
                                }
                            }
                        }
                    )
                }.onFailure { err ->
                    Log.w(TAG, "Unity Rewarded load exception ($cleanPlacement): ${err.message}")
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (resolved.compareAndSet(false, true)) {
                        onUnavailable()
                    }
                }
            }
        }
    }

    private fun presentUnityRewarded(
        activity: Activity,
        placementId: String,
        onAdOpening: () -> Unit,
        onRewardEarnedAndClosed: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onFailedToShow: () -> Unit
    ) {
        postToMain {
            if (activity.isFinishing || activity.isDestroyed) {
                onFailedToShow()
                return@postToMain
            }
            runCatching {
                onAdOpening()
                UnityAds.show(
                    activity,
                    placementId,
                    UnityAdsShowOptions(),
                    object : IUnityAdsShowListener {
                        override fun onUnityAdsShowFailure(
                            failedId: String?,
                            error: UnityAds.UnityAdsShowError?,
                            message: String?
                        ) {
                            Log.w(TAG, "Unity Rewarded show failure ($placementId): $error - $message")
                            onFailedToShow()
                        }

                        override fun onUnityAdsShowStart(startedId: String?) {
                            onAdOpening()
                        }

                        override fun onUnityAdsShowClick(clickedId: String?) = Unit

                        override fun onUnityAdsShowComplete(
                            completedId: String?,
                            state: UnityAds.UnityAdsShowCompletionState?
                        ) {
                            Log.d(TAG, "Unity Rewarded complete ($placementId): state=$state")
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                onRewardEarnedAndClosed()
                            } else {
                                onClosedWithoutReward()
                            }
                        }
                    }
                )
            }.onFailure { err ->
                Log.w(TAG, "Unity Rewarded show exception ($placementId): ${err.message}")
                onFailedToShow()
            }
        }
    }
}
