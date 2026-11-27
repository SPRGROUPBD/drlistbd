package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.GoogleAuthManager
import com.example.data.local.NotificationEntity
import com.example.data.model.GlobalSearchType
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.notifications.InAppUpdateHelper
import com.example.notifications.PushNotificationManager
import com.example.ui.ads.AdCampaignItem
import com.example.ui.ads.AdMobInitializer
import com.example.ui.ads.AdvertisingRotationEngine
import com.example.ui.ads.AppOpenPopup300x200Dialog
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.RewardedAdManager
import com.example.ui.ads.RotatingBottomBannerBar
import com.example.ui.ads.SliderRecordTypes
import com.example.ui.ads.openAdUrl
import com.example.ui.components.FlexibleUpdateBanner
import com.example.ui.components.GeneralDialogPayload
import com.example.ui.components.GeneralNotificationDialog
import com.example.ui.components.GlobalTopAppBar
import com.example.ui.components.NotificationCenterBottomSheet
import com.example.ui.components.NotificationPermissionRationaleDialog
import com.example.ui.components.SyncStatusBottomSheet
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

object Routes {
    const val HOME = "home"
    const val FIND_DOCTORS = "find_doctors"
    const val FACILITIES = "facilities"
    const val PORTAL = "portal"
    const val ABOUT_US = "about_us"
    const val ADVERTISER_PORTAL = "advertiser_portal"
    const val DOCTOR_PROFILE = "doctor_profile/{doctorId}"
    const val INFO = "info/{pageKey}"

    fun doctorProfile(id: String) = "doctor_profile/$id"
    fun info(key: String) = "info/$key"
}

private data class BottomTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

data class PendingPushTarget(
    val notificationId: String = "",
    val type: String = PushNotificationManager.TYPE_GENERAL,
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val doctorId: String = "",
    val actionUrl: String = "",
    val updateMode: String = "",
    val version: String = "",
    val deepLink: String = ""
)

class MainActivity : ComponentActivity() {
    private val pendingPushTarget = mutableStateOf<PendingPushTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdMobInitializer.ensureInitialized(applicationContext)
        PushNotificationManager.ensureNotificationChannel(applicationContext)
        capturePushIntent(intent)
        setContent {
            val vm: MainViewModel = viewModel()
            val isDarkTheme by vm.isDarkTheme.collectAsStateWithLifecycle()
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
            }
            DoctorDirectoryTheme(darkTheme = isDarkTheme) {
                DoctorListBdApp(
                    vm = vm,
                    isDarkTheme = isDarkTheme,
                    pendingPushTarget = pendingPushTarget.value,
                    onConsumePushTarget = { pendingPushTarget.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capturePushIntent(intent)
    }

    private fun capturePushIntent(intent: Intent?) {
        if (intent == null) return
        val notifId = intent.getStringExtra(PushNotificationManager.EXTRA_NOTIFICATION_ID).orEmpty().trim()
            .ifBlank { intent.getStringExtra("notificationId").orEmpty().trim() }
            .ifBlank { intent.getStringExtra("google.message_id").orEmpty().trim() }
        val rawType = intent.getStringExtra(PushNotificationManager.EXTRA_MESSAGE_TYPE).orEmpty().trim()
            .ifBlank { intent.getStringExtra("type").orEmpty().trim() }
            .ifBlank { intent.getStringExtra("messageType").orEmpty().trim() }
        val title = intent.getStringExtra(PushNotificationManager.EXTRA_TITLE).orEmpty().trim()
            .ifBlank { intent.getStringExtra("title").orEmpty().trim() }
            .ifBlank { intent.getStringExtra("gcm.notification.title").orEmpty().trim() }
        val body = intent.getStringExtra(PushNotificationManager.EXTRA_BODY).orEmpty().trim()
            .ifBlank { intent.getStringExtra("body").orEmpty().trim() }
            .ifBlank { intent.getStringExtra("message").orEmpty().trim() }
            .ifBlank { intent.getStringExtra("gcm.notification.body").orEmpty().trim() }
        val imageUrl = PushNotificationManager.sanitizeHttpUrl(
            intent.getStringExtra(PushNotificationManager.EXTRA_IMAGE_URL).orEmpty().trim()
                .ifBlank { intent.getStringExtra("imageUrl").orEmpty().trim() }
        )
        val docId = intent.getStringExtra(PushNotificationManager.EXTRA_DOCTOR_ID).orEmpty().trim()
            .ifBlank { intent.getStringExtra("doctorId").orEmpty().trim() }
            .ifBlank { intent.getStringExtra("selectedDoctorId").orEmpty().trim() }
        val actionUrl = PushNotificationManager.sanitizeActionUrl(
            intent.getStringExtra(PushNotificationManager.EXTRA_ACTION_URL).orEmpty().trim()
                .ifBlank { intent.getStringExtra("actionUrl").orEmpty().trim() }
                .ifBlank { intent.getStringExtra("updateUrl").orEmpty().trim() }
        )
        val updateMode = intent.getStringExtra(PushNotificationManager.EXTRA_UPDATE_MODE).orEmpty().trim()
            .ifBlank { intent.getStringExtra("updateMode").orEmpty().trim() }
            .uppercase()
        val version = intent.getStringExtra(PushNotificationManager.EXTRA_VERSION).orEmpty().trim()
            .ifBlank { intent.getStringExtra("version").orEmpty().trim() }
            .ifBlank { intent.getStringExtra("updateVersion").orEmpty().trim() }
        val deepLink = PushNotificationManager.sanitizeDeepLink(
            intent.getStringExtra(PushNotificationManager.EXTRA_DEEP_LINK).orEmpty().trim()
                .ifBlank { intent.getStringExtra("deepLink").orEmpty().trim() }
                .ifBlank { intent.dataString.orEmpty().trim() }
        )

        val hasPushPayload = notifId.isNotBlank() ||
            rawType.isNotBlank() ||
            docId.isNotBlank() ||
            actionUrl.isNotBlank() ||
            deepLink.isNotBlank() ||
            title.isNotBlank() ||
            body.isNotBlank()

        if (hasPushPayload) {
            val resolvedType = when {
                rawType.isNotBlank() -> PushNotificationManager.normalizeType(rawType)
                docId.isNotBlank() -> PushNotificationManager.TYPE_NEW_DOCTOR
                actionUrl.isNotBlank() || updateMode.isNotBlank() -> PushNotificationManager.TYPE_APP_UPDATE
                else -> PushNotificationManager.TYPE_GENERAL
            }
            pendingPushTarget.value = PendingPushTarget(
                notificationId = notifId,
                type = resolvedType,
                title = title,
                body = body,
                imageUrl = imageUrl,
                doctorId = docId,
                actionUrl = actionUrl,
                updateMode = updateMode,
                version = version,
                deepLink = deepLink
            )
        }
    }
}

@Composable
fun DoctorListBdApp(
    vm: MainViewModel = viewModel(),
    isDarkTheme: Boolean = LocalIsDarkTheme.current,
    pendingPushTarget: PendingPushTarget? = null,
    onConsumePushTarget: () -> Unit = {}
) {
    val lang by vm.currentLang.collectAsStateWithLifecycle()
    val storedAdConfig by vm.adConfig.collectAsStateWithLifecycle()
    // Keep legacy static sponsor slots disabled so only the 4 dynamic RTDB Ad Packages render
    val adConfig = storedAdConfig.copy(
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
    val adSnapshot by vm.adSnapshot.collectAsStateWithLifecycle()
    val sponsors by vm.sponsors.collectAsStateWithLifecycle()
    val placementRateCards by vm.placementRateCards.collectAsStateWithLifecycle()
    val advertiserSession by vm.advertiserSession.collectAsStateWithLifecycle()
    val authoritativeNowMs by vm.authoritativeNowMs.collectAsStateWithLifecycle()
    val isOnline by vm.isOnline.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }
    val appContext = LocalContext.current

    val inAppUpdateHelper = remember { InAppUpdateHelper(appContext) }
    val updateBannerState by inAppUpdateHelper.bannerState.collectAsStateWithLifecycle()
    val inAppUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK && result.resultCode != Activity.RESULT_CANCELED) {
            vm.showMessage(
                tr(
                    lang,
                    "অ্যাপ আপডেট সম্পন্ন হয়নি। অনুগ্রহ করে পরে আবার চেষ্টা করুন।",
                    "App update could not be completed. Please try again later."
                )
            )
        }
    }

    var hasNotifPermission by remember {
        mutableStateOf(PushNotificationManager.hasNotificationPermission(appContext))
    }
    var showNotifPermissionRationale by remember { mutableStateOf(false) }
    var notifPermPermanentlyDenied by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, lang) {
        inAppUpdateHelper.registerListener(lang) { msg -> vm.showMessage(msg) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ConnectivityMonitor.refreshNow(appContext)
                hasNotifPermission = PushNotificationManager.hasNotificationPermission(appContext)
                vm.recordForegroundActivity()
                vm.triggerForegroundAutoSyncIfStale()
                vm.refreshAdvertisingSystem()
                RewardedAdManager.findActivity(appContext)?.let { act ->
                    inAppUpdateHelper.resumeInProgressIfNeeded(act, inAppUpdateLauncher, lang)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inAppUpdateHelper.unregisterListener()
        }
    }

    LaunchedEffect(adSnapshot.admobConfig, adSnapshot.placementSettings) {
        AdMobInitializer.syncRemoteAppId(appContext, adSnapshot.admobConfig.appId)
        RewardedAdManager.syncConfig(appContext, adSnapshot.admobConfig, adSnapshot.placementSettings)
    }

    // App Open Popup (300x200) — shown STRICTLY ONCE per app session (guarded in MainViewModel)
    var activeAppOpenPopup by remember { mutableStateOf<AdCampaignItem?>(null) }

    LaunchedEffect(showSplash, adSnapshot.syncedFromServer, adSnapshot.campaigns) {
        if (showSplash || !adSnapshot.syncedFromServer) return@LaunchedEffect
        val popup = vm.tryConsumeSessionLaunchPopup()
        if (popup != null) {
            activeAppOpenPopup = popup
            vm.markAppOpenPopupShown()
        }
    }

    val googleUser by GoogleAuthManager.currentUser.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        PushNotificationManager.markNotificationPermissionAsked(appContext)
        if (granted) {
            PushNotificationManager.syncCurrentFcmToken(appContext, googleUser?.uid)
        }
    }

    val triggerNotificationPermissionFlow: (Boolean) -> Unit = { fromUserAction ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = PushNotificationManager.hasNotificationPermission(appContext)
            hasNotifPermission = alreadyGranted
            if (!alreadyGranted) {
                val activity = RewardedAdManager.findActivity(appContext)
                val askedBefore = PushNotificationManager.hasAskedNotificationPermissionBefore(appContext)
                val shouldShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                if (fromUserAction && askedBefore && !shouldShowRationale) {
                    notifPermPermanentlyDenied = true
                    showNotifPermissionRationale = true
                } else {
                    notifPermPermanentlyDenied = false
                    showNotifPermissionRationale = true
                }
            }
        }
    }

    LaunchedEffect(showSplash) {
        if (!showSplash) {
            PushNotificationManager.syncCurrentFcmToken(appContext, googleUser?.uid)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PushNotificationManager.hasNotificationPermission(appContext) &&
                !PushNotificationManager.hasAskedNotificationPermissionBefore(appContext)
            ) {
                delay(450L)
                triggerNotificationPermissionFlow(false)
            }
        }
    }

    LaunchedEffect(googleUser?.uid) {
        PushNotificationManager.syncCurrentFcmToken(appContext, googleUser?.uid)
    }

    if (showSplash) {
        SplashScreen(
            lang = lang,
            onFinished = { showSplash = false }
        )
        return
    }

    val navController = rememberNavController()

    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val syncUiState by vm.syncUiState.collectAsStateWithLifecycle()
    val selectedHomeDistrict by vm.selectedHomeDistrict.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val specialties by vm.specialties.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val hospitals by vm.hospitals.collectAsStateWithLifecycle()
    val diagnosticCenters by vm.diagnosticCenters.collectAsStateWithLifecycle()
    val publicDoctors by vm.publicDoctors.collectAsStateWithLifecycle()
    val filteredDoctors by vm.filteredDoctors.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val visibleCount by vm.visiblePageCount.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteDoctorIds.collectAsStateWithLifecycle()
    val favoriteHospitalIds by vm.favoriteHospitalIds.collectAsStateWithLifecycle()
    val favoriteDiagnosticIds by vm.favoriteDiagnosticIds.collectAsStateWithLifecycle()
    val favoriteChamberIds by vm.favoriteChamberIds.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbar.collectAsStateWithLifecycle()

    var facilitiesTab by remember { mutableIntStateOf(0) }
    var pendingOpenHospitalId by remember { mutableStateOf<String?>(null) }
    var pendingOpenDiagnosticId by remember { mutableStateOf<String?>(null) }
    var pendingOpenChamberId by remember { mutableStateOf<String?>(null) }

    // Global Top App Bar interactive state (shared consistently across all screens)
    var globalSearchExpanded by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }
    var showSyncSheet by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbar()
        }
    }

    var pendingRewardDoctorId by remember { mutableStateOf<String?>(null) }

    // Clicking any doctor plays an Admin-configured AdMob or Unity Rewarded Ad first;
    // once the reward completes (or if ads are disabled/unavailable), the doctor profile opens.
    val openDoctorProfile: (String) -> Unit = { doctorId ->
        val navigateDirect = {
            navController.navigate(Routes.doctorProfile(doctorId)) {
                launchSingleTop = true
            }
        }
        val activity = RewardedAdManager.findActivity(appContext)
        val shouldShowReward = activity != null &&
            isOnline &&
            RewardedAdManager.shouldShowRewardedForDoctorClick(
                config = adSnapshot.admobConfig,
                placementSettings = adSnapshot.placementSettings
            )
        if (pendingRewardDoctorId != null) {
            // Prevent duplicate taps while rewarded ad is loading
        } else if (shouldShowReward && activity != null) {
            pendingRewardDoctorId = doctorId
            RewardedAdManager.showRewardedForDoctorClick(
                activity = activity,
                config = adSnapshot.admobConfig,
                placementSettings = adSnapshot.placementSettings,
                isOnline = isOnline,
                onAdOpening = { pendingRewardDoctorId = null },
                onUnlocked = {
                    pendingRewardDoctorId = null
                    navigateDirect()
                },
                onIncomplete = {
                    pendingRewardDoctorId = null
                    vm.showMessage(
                        tr(
                            lang,
                            "ডাক্তারের প্রোফাইল দেখতে সম্পূর্ণ বিজ্ঞাপন দেখুন",
                            "Watch the full ad to view the doctor profile"
                        )
                    )
                }
            )
        } else {
            navigateDirect()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    // Reliable top-level tab navigation that guarantees Home nav button ALWAYS returns to Home
    val navigateToTab: (String) -> Unit = { route ->
        if (route == Routes.HOME) {
            val popped = navController.popBackStack(Routes.HOME, inclusive = false)
            if (!popped) {
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } else if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = false
                }
                launchSingleTop = true
            }
        }
    }

    val mainTabs = listOf(
        BottomTabItem(Routes.HOME, AppStrings.navHome(lang), Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        BottomTabItem(Routes.FIND_DOCTORS, AppStrings.navDoctors(lang), Icons.Rounded.MedicalServices, Icons.Outlined.MedicalServices, "nav_doctors"),
        BottomTabItem(Routes.FACILITIES, AppStrings.navFacilities(lang), Icons.Rounded.LocalHospital, Icons.Outlined.LocalHospital, "nav_facilities"),
        BottomTabItem(Routes.PORTAL, AppStrings.navPortal(lang), Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder, "nav_portal"),
        BottomTabItem(Routes.ABOUT_US, AppStrings.navAbout(lang), Icons.Rounded.Info, Icons.Outlined.Info, "nav_about")
    )

    // Keep Bottom Navigation Bar / Navigation Rail visible on ALL screens (including About Us, Info, Advertiser Portal, Doctor Profile)
    val showMainChrome = true
    val selectedTabRoute = when {
        currentRoute == Routes.DOCTOR_PROFILE || currentRoute.startsWith("doctor_profile") -> Routes.FIND_DOCTORS
        currentRoute == Routes.INFO || currentRoute.startsWith("info") || currentRoute == Routes.ADVERTISER_PORTAL -> Routes.ABOUT_US
        else -> currentRoute
    }
    val unreadCount = notifications.count { !it.isRead }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                GlobalTopAppBar(
                    lang = lang,
                    syncState = syncUiState,
                    unreadNotifications = unreadCount,
                    searchExpanded = globalSearchExpanded,
                    searchQuery = globalSearchQuery,
                    doctors = publicDoctors,
                    hospitals = hospitals,
                    diagnosticCenters = diagnosticCenters,
                    onLogoClick = {
                        globalSearchExpanded = false
                        globalSearchQuery = ""
                        navigateToTab(Routes.HOME)
                    },
                    onToggleSearch = {
                        globalSearchExpanded = !globalSearchExpanded
                        if (!globalSearchExpanded) {
                            globalSearchQuery = ""
                        }
                    },
                    onSearchQueryChange = { globalSearchQuery = it },
                    onSearchSubmit = { query ->
                        globalSearchExpanded = false
                        globalSearchQuery = ""
                        vm.updateSearch { it.copy(query = query) }
                        navigateToTab(Routes.FIND_DOCTORS)
                    },
                    onSuggestionSelected = { suggestion ->
                        globalSearchExpanded = false
                        globalSearchQuery = ""
                        when (suggestion.type) {
                            GlobalSearchType.DOCTOR -> openDoctorProfile(suggestion.id)
                            GlobalSearchType.HOSPITAL -> {
                                facilitiesTab = 0
                                pendingOpenHospitalId = suggestion.id
                                navigateToTab(Routes.FACILITIES)
                            }
                            GlobalSearchType.DIAGNOSTIC -> {
                                facilitiesTab = 1
                                pendingOpenDiagnosticId = suggestion.id
                                navigateToTab(Routes.FACILITIES)
                            }
                            GlobalSearchType.CHAMBER -> {
                                facilitiesTab = 3
                                pendingOpenChamberId = suggestion.id
                                navigateToTab(Routes.FACILITIES)
                            }
                        }
                    },
                    onSyncIconClick = { showSyncSheet = true },
                    onNotificationsClick = { showNotificationCenter = true }
                )
            },
            bottomBar = {
                val showBottomAd = currentRoute != Routes.ADVERTISER_PORTAL
                val activeBottomBanners = remember(
                    adSnapshot.adminBottomBanners,
                    adSnapshot.campaigns,
                    adSnapshot.admobConfig,
                    adSnapshot.placementSettings,
                    authoritativeNowMs / 60_000L,
                    showBottomAd
                ) {
                    if (!showBottomAd) emptyList()
                    else AdvertisingRotationEngine.selectActiveBottomBanners(
                        adminBanners = adSnapshot.adminBottomBanners,
                        campaigns = adSnapshot.campaigns,
                        admobConfig = adSnapshot.admobConfig,
                        nowMs = authoritativeNowMs,
                        settings = adSnapshot.placementSettings
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    FlexibleUpdateBanner(
                        state = updateBannerState,
                        lang = lang,
                        onInstallClick = { inAppUpdateHelper.completeFlexibleUpdate() }
                    )
                    if (activeBottomBanners.isNotEmpty()) {
                        RotatingBottomBannerBar(
                            banners = activeBottomBanners,
                            lang = lang,
                            onVisibleImpression = vm::logBottomBannerImpression,
                            onClicked = vm::logBottomBannerClick
                        )
                    }
                    if (showMainChrome && !isWideScreen) {
                        val isDark = LocalIsDarkTheme.current
                        val navBg = if (isDark) androidx.compose.ui.graphics.Color(0xFF0A121C) else LightSurfaceWhite
                        val activeEmerald = androidx.compose.ui.graphics.Color(0xFF10B96B)
                        val activeIndicator = if (isDark) androidx.compose.ui.graphics.Color(0xFF0D3D2A) else androidx.compose.ui.graphics.Color(0xFFD6F7E5)
                        val inactiveNavTint = if (isDark) androidx.compose.ui.graphics.Color(0xFF9EB2C8) else TextSecondary

                        NavigationBar(
                            containerColor = navBg
                        ) {
                            mainTabs.forEach { tab ->
                                val selected = selectedTabRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = activeEmerald,
                                        selectedTextColor = activeEmerald,
                                        indicatorColor = activeIndicator,
                                        unselectedIconColor = inactiveNavTint,
                                        unselectedTextColor = inactiveNavTint
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showMainChrome && isWideScreen) {
                    val isDark = LocalIsDarkTheme.current
                    NavigationRail(
                        containerColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF0A121C) else LightSurfaceWhite
                    ) {
                        mainTabs.forEach { tab ->
                            val selected = selectedTabRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.HOME) {
                        val homeSliderBase = remember { vm.getAdvertisingQueueBase(AdvertisingRotationEngine.QUEUE_HOME_SLIDER) }
                        val homeDoctorNativeBase = remember { vm.getAdvertisingQueueBase(AdvertisingRotationEngine.QUEUE_DOCTOR_NATIVE) }
                        val homeHospitalNativeBase = remember { vm.getAdvertisingQueueBase(AdvertisingRotationEngine.QUEUE_HOSPITAL_NATIVE) }
                        var sliderVisitRecorded by remember { mutableStateOf(false) }
                        val homeSliderSlides = remember(
                            adSnapshot,
                            publicDoctors,
                            hospitals,
                            diagnosticCenters,
                            homeSliderBase,
                            authoritativeNowMs / 60_000L
                        ) {
                            AdvertisingRotationEngine.buildHomeSliderSlides(
                                campaigns = adSnapshot.campaigns,
                                advertiserSliderIndex = adSnapshot.advertiserHomeSliders,
                                customSliders = adSnapshot.adminCustomSliders,
                                sliderSettings = adSnapshot.homeSliderSettings,
                                placementSettings = adSnapshot.placementSettings,
                                doctors = publicDoctors,
                                hospitals = hospitals,
                                diagnostics = diagnosticCenters,
                                baseOffset = homeSliderBase,
                                nowMs = authoritativeNowMs
                            )
                        }
                        LaunchedEffect(adSnapshot.syncedFromServer, homeSliderSlides.size) {
                            if (adSnapshot.syncedFromServer && !sliderVisitRecorded && homeSliderSlides.any { !it.isCustomAdminSlider }) {
                                sliderVisitRecorded = true
                                val firstAdv = homeSliderSlides.firstOrNull { !it.isCustomAdminSlider }?.campaignId.orEmpty()
                                vm.recordHomeSliderVisitAdvance(firstAdv)
                            }
                        }

                        HomeScreen(
                            lang = lang,
                            settings = settings,
                            adConfig = adConfig,
                            sponsors = emptyList(),
                            rateCards = emptyList(),
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick,
                            isSyncing = isSyncing,
                            specialties = specialties,
                            locations = locations,
                            doctors = publicDoctors,
                            hospitals = hospitals,
                            favoriteIds = favoriteIds,
                            selectedHomeDistrict = selectedHomeDistrict,
                            onSelectHomeDistrict = vm::setSelectedHomeDistrict,
                            onShowMessage = vm::showMessage,
                            onRefreshDirectory = vm::refreshDirectory,
                            onToggleLanguage = { vm.toggleLanguage() },
                            onOpenNotifications = { showNotificationCenter = true },
                            onSearchSubmit = { query ->
                                vm.updateSearch { it.copy(query = query) }
                                navigateToTab(Routes.FIND_DOCTORS)
                            },
                            onSelectSpecialty = { specId ->
                                vm.updateSearch { it.copy(specialtyId = specId) }
                                navigateToTab(Routes.FIND_DOCTORS)
                            },
                            onSelectDistrict = { district ->
                                vm.updateSearch { it.copy(district = district) }
                                navigateToTab(Routes.FIND_DOCTORS)
                            },
                            onToggleFavorite = vm::toggleFavorite,
                            onDoctorClick = openDoctorProfile,
                            onNavigateFindDoctors = { todayOnly ->
                                vm.updateSearch { it.copy(availableTodayOnly = todayOnly) }
                                navigateToTab(Routes.FIND_DOCTORS)
                            },
                            onNavigateFacilitiesTab = { tabIdx ->
                                facilitiesTab = tabIdx
                                navigateToTab(Routes.FACILITIES)
                            },
                            onNavigateSavedTab = { navigateToTab(Routes.PORTAL) },
                            onNavigateAboutTab = { navigateToTab(Routes.ABOUT_US) },
                            homeSliderSlides = homeSliderSlides,
                            homeSliderAutoSlideMs = adSnapshot.homeSliderSettings.autoSlideIntervalMs,
                            onHomeSliderVisible = vm::logHomeSliderSlideImpression,
                            onHomeSliderClick = { slide ->
                                vm.logHomeSliderSlideClick(slide)
                                when (slide.targetRecordType) {
                                    SliderRecordTypes.DOCTOR -> openDoctorProfile(slide.targetRecordId)
                                    SliderRecordTypes.HOSPITAL -> {
                                        facilitiesTab = 0
                                        pendingOpenHospitalId = slide.targetRecordId
                                        navigateToTab(Routes.FACILITIES)
                                    }
                                    SliderRecordTypes.DIAGNOSTIC -> {
                                        facilitiesTab = 1
                                        pendingOpenDiagnosticId = slide.targetRecordId
                                        navigateToTab(Routes.FACILITIES)
                                    }
                                    else -> {
                                        if (slide.destinationUrl.isNotBlank()) {
                                            openAdUrl(appContext, slide.destinationUrl, lang)
                                        }
                                    }
                                }
                            },
                            adCampaigns = adSnapshot.campaigns,
                            adPlacementSettings = adSnapshot.placementSettings,
                            admobConfig = adSnapshot.admobConfig,
                            doctorListRotationBase = homeDoctorNativeBase,
                            hospitalRotationBase = homeHospitalNativeBase,
                            onNativeAdImpression = { camp, q ->
                                vm.logAdvertisingImpression(camp, q)
                                vm.advanceNativePlacementQueue(q, camp.id)
                            },
                            onNativeAdClick = vm::logAdvertisingClick
                        )
                    }

                    composable(Routes.FIND_DOCTORS) {
                        val doctorNativeBase = remember { vm.getAdvertisingQueueBase(AdvertisingRotationEngine.QUEUE_DOCTOR_NATIVE) }
                        FindDoctorsScreen(
                            lang = lang,
                            adConfig = adConfig,
                            sponsors = sponsors,
                            rateCards = placementRateCards,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick,
                            searchState = searchState,
                            specialties = specialties,
                            locations = locations,
                            filteredDoctors = filteredDoctors,
                            visibleCount = visibleCount,
                            favoriteIds = favoriteIds,
                            onUpdateSearch = vm::updateSearch,
                            onResetFilters = vm::resetFilters,
                            onLoadMore = vm::loadMoreDoctors,
                            onToggleFavorite = vm::toggleFavorite,
                            onDoctorClick = openDoctorProfile,
                            adCampaigns = adSnapshot.campaigns,
                            adPlacementSettings = adSnapshot.placementSettings,
                            admobConfig = adSnapshot.admobConfig,
                            doctorListRotationBase = doctorNativeBase,
                            onNativeAdImpression = { camp, q ->
                                vm.logAdvertisingImpression(camp, q)
                                vm.advanceNativePlacementQueue(q, camp.id)
                            },
                            onNativeAdClick = vm::logAdvertisingClick
                        )
                    }

                    composable(Routes.FACILITIES) {
                        val hospBase = remember { vm.getAdvertisingQueueBase(AdvertisingRotationEngine.QUEUE_HOSPITAL_NATIVE) }
                        val diagBase = remember { vm.getAdvertisingQueueBase(AdvertisingRotationEngine.QUEUE_DIAGNOSTIC_NATIVE) }
                        DirectoriesScreen(
                            lang = lang,
                            adConfig = adConfig,
                            sponsors = sponsors,
                            rateCards = placementRateCards,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick,
                            favoriteHospitalIds = favoriteHospitalIds,
                            favoriteDiagnosticIds = favoriteDiagnosticIds,
                            favoriteChamberIds = favoriteChamberIds,
                            onToggleHospitalFavorite = vm::toggleHospitalFavorite,
                            onToggleDiagnosticFavorite = vm::toggleDiagnosticFavorite,
                            onToggleChamberFavorite = vm::toggleChamberFavorite,
                            selectedTab = facilitiesTab,
                            onTabChange = { facilitiesTab = it },
                            hospitals = hospitals,
                            diagnosticCenters = diagnosticCenters,
                            specialties = specialties,
                            doctors = publicDoctors,
                            onSelectSpecialty = { specId ->
                                vm.updateSearch { it.copy(specialtyId = specId) }
                                navigateToTab(Routes.FIND_DOCTORS)
                            },
                            onSelectFacility = { facName ->
                                vm.updateSearch { it.copy(facilityQuery = facName) }
                                navigateToTab(Routes.FIND_DOCTORS)
                            },
                            onDoctorClick = openDoctorProfile,
                            pendingOpenHospitalId = pendingOpenHospitalId,
                            pendingOpenDiagnosticId = pendingOpenDiagnosticId,
                            pendingOpenChamberId = pendingOpenChamberId,
                            onConsumePendingFacilityDetail = {
                                pendingOpenHospitalId = null
                                pendingOpenDiagnosticId = null
                                pendingOpenChamberId = null
                            },
                            adCampaigns = adSnapshot.campaigns,
                            adPlacementSettings = adSnapshot.placementSettings,
                            admobConfig = adSnapshot.admobConfig,
                            hospitalRotationBase = hospBase,
                            diagnosticRotationBase = diagBase,
                            onNativeAdImpression = { camp, q ->
                                vm.logAdvertisingImpression(camp, q)
                                vm.advanceNativePlacementQueue(q, camp.id)
                            },
                            onNativeAdClick = vm::logAdvertisingClick
                        )
                    }

                    composable(Routes.PORTAL) {
                        val favBase = remember { vm.getAdvertisingQueueBase(AdvertisingRotationEngine.QUEUE_FAVORITE_NATIVE) }
                        val favDocs = publicDoctors.filter { it.doctor.id in favoriteIds }
                        val favHospitals = hospitals.filter { it.id in favoriteHospitalIds }
                        val favDiagnostics = diagnosticCenters.filter { it.id in favoriteDiagnosticIds }
                        val favChambers = remember(publicDoctors, favoriteChamberIds) {
                            publicDoctors.flatMap { doc ->
                                doc.chambers
                                    .filter { it.chamber.id in favoriteChamberIds }
                                    .map { doc to it.chamber }
                            }
                        }
                        PatientPortalScreen(
                            lang = lang,
                            adConfig = adConfig,
                            sponsors = sponsors,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick,
                            isSyncing = isSyncing,
                            favoriteDoctors = favDocs,
                            favoriteHospitals = favHospitals,
                            favoriteDiagnostics = favDiagnostics,
                            favoriteChambers = favChambers,
                            onRefreshDirectory = vm::refreshDirectory,
                            onToggleFavorite = vm::toggleFavorite,
                            onToggleHospitalFavorite = vm::toggleHospitalFavorite,
                            onToggleDiagnosticFavorite = vm::toggleDiagnosticFavorite,
                            onToggleChamberFavorite = vm::toggleChamberFavorite,
                            onSelectFacility = { facName ->
                                vm.updateSearch { it.copy(facilityQuery = facName) }
                                navigateToTab(Routes.FIND_DOCTORS)
                            },
                            onDoctorClick = openDoctorProfile,
                            adCampaigns = adSnapshot.campaigns,
                            adPlacementSettings = adSnapshot.placementSettings,
                            admobConfig = adSnapshot.admobConfig,
                            favoriteRotationBase = favBase,
                            onNativeAdImpression = { camp, q ->
                                vm.logAdvertisingImpression(camp, q)
                                vm.advanceNativePlacementQueue(q, camp.id)
                            },
                            onNativeAdClick = vm::logAdvertisingClick
                        )
                    }

                    composable(Routes.ABOUT_US) {
                        AboutUsScreen(
                            lang = lang,
                            settings = settings,
                            adConfig = adConfig,
                            sponsors = sponsors,
                            rateCards = placementRateCards,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick,
                            onOpenAdvertiserPortal = { navController.navigate(Routes.ADVERTISER_PORTAL) },
                            doctorsCount = publicDoctors.size,
                            hospitalsCount = hospitals.size,
                            diagnosticCount = diagnosticCenters.size,
                            specialtiesCount = specialties.size,
                            onNavigateInfo = { key -> navController.navigate(Routes.info(key)) },
                            onToggleLang = { vm.toggleLanguage() },
                            isDarkTheme = isDarkTheme,
                            onSetDarkTheme = vm::setDarkTheme
                        )
                    }

                    composable(Routes.ADVERTISER_PORTAL) {
                        AdvertiserPortalScreen(
                            lang = lang,
                            session = advertiserSession,
                            doctors = publicDoctors,
                            hospitals = hospitals,
                            diagnosticCenters = diagnosticCenters,
                            cachedPackages = adSnapshot.packages,
                            cachedPaymentMethods = adSnapshot.paymentMethods,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onLoginSuccess = vm::saveAdvertiserSession,
                            onLogout = { vm.logoutAdvertiser(appContext) },
                            onShowMessage = vm::showMessage,
                            onRefreshGlobalAds = vm::refreshAdvertisingSystem,
                            onDoctorClick = openDoctorProfile
                        )
                    }

                    composable(
                        route = Routes.DOCTOR_PROFILE,
                        arguments = listOf(navArgument("doctorId") { type = NavType.StringType })
                    ) { backStack ->
                        val docId = backStack.arguments?.getString("doctorId").orEmpty()
                        val profile = publicDoctors.firstOrNull { it.doctor.id == docId }
                        val isFetchingPushDoctor by vm.isFetchingPushDoctor.collectAsStateWithLifecycle()
                        DoctorProfileScreen(
                            profile = profile,
                            lang = lang,
                            adConfig = adConfig,
                            sponsors = sponsors,
                            rateCards = placementRateCards,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick,
                            isFavorite = docId in favoriteIds,
                            onToggleFavorite = { vm.toggleFavorite(docId) },
                            onBack = { navController.popBackStack() },
                            isLoadingProfile = isFetchingPushDoctor && profile == null,
                            onGoToDoctorList = { navigateToTab(Routes.FIND_DOCTORS) }
                        )
                    }

                    composable(
                        route = Routes.INFO,
                        arguments = listOf(navArgument("pageKey") { type = NavType.StringType })
                    ) { backStack ->
                        StaticInfoScreen(
                            pageKey = backStack.arguments?.getString("pageKey") ?: "about",
                            lang = lang,
                            settings = settings,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // App Open Popup (300x200) — shown ONCE per app session ONLY
        activeAppOpenPopup?.let { popupCamp ->
            AppOpenPopup300x200Dialog(
                campaign = popupCamp,
                lang = lang,
                onVisibleImpression = vm::logAdvertisingImpression,
                onClicked = vm::logAdvertisingClick,
                onDismiss = { activeAppOpenPopup = null }
            )
        }

        if (pendingRewardDoctorId != null) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceWhite,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = tr(lang, "রিওয়ার্ড অ্যাড লোড হচ্ছে...", "Loading rewarded ad..."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
        if (showSyncSheet) {
            SyncStatusBottomSheet(
                lang = lang,
                syncState = syncUiState,
                doctorsCount = publicDoctors.size,
                hospitalsCount = hospitals.size,
                diagnosticsCount = diagnosticCenters.size,
                onSyncNow = { vm.syncNow(showToast = true) },
                onDismiss = { showSyncSheet = false }
            )
        }

        var activeGeneralDialog by remember { mutableStateOf<GeneralDialogPayload?>(null) }

        val openDoctorFromNotification: (String) -> Unit = { rawDoctorId ->
            val cleanDocId = rawDoctorId.trim()
            if (cleanDocId.isBlank()) {
                vm.showMessage(tr(lang, "ডাক্তারের তথ্য পাওয়া যায়নি। ডাক্তার তালিকা দেখুন।", "Doctor info unavailable. Browse doctor list."))
                navigateToTab(Routes.FIND_DOCTORS)
            } else {
                // Always sync latest doctor details from Firebase RTDB if newly added or stale
                if (publicDoctors.none { it.doctor.id == cleanDocId }) {
                    vm.refreshDoctorForPush(cleanDocId)
                }
                navController.navigate(Routes.doctorProfile(cleanDocId)) {
                    launchSingleTop = true
                }
            }
        }

        val handleAppUpdateNotification: (String, String) -> Unit = { rawActionUrl, rawUpdateMode ->
            val safeUrl = PushNotificationManager.sanitizeActionUrl(rawActionUrl)
            if (safeUrl.isNotBlank()) {
                // Admin provided an explicit Update / Play Store URL -> open directly without Play In-App Update API
                val opened = PushNotificationManager.openExternalUpdateUrl(appContext, safeUrl)
                if (!opened) {
                    vm.showMessage(
                        tr(lang, "আপডেট লিংক খোলা যায়নি। অনুগ্রহ করে ব্রাউজার চেক করুন।", "Could not open update URL.")
                    )
                }
            } else {
                // No Update URL -> launch official Google Play In-App Updates API (Flexible or Immediate)
                val activity = RewardedAdManager.findActivity(appContext)
                if (activity != null) {
                    inAppUpdateHelper.checkAndStartUpdate(
                        activity = activity,
                        launcher = inAppUpdateLauncher,
                        updateMode = rawUpdateMode.ifBlank { "FLEXIBLE" },
                        lang = lang,
                        isOnline = isOnline,
                        onStatusMessage = vm::showMessage
                    )
                } else {
                    vm.showMessage(
                        tr(lang, "এই মুহূর্তে অ্যাপ আপডেট চেক করা যাচ্ছে না।", "Unable to check for app updates right now.")
                    )
                }
            }
        }

        val handleTypedNotification: (
            notificationId: String,
            type: String,
            title: String,
            body: String,
            imageUrl: String,
            doctorId: String,
            actionUrl: String,
            updateMode: String,
            deepLink: String
        ) -> Unit = { notifId, rawType, title, body, imageUrl, doctorId, actionUrl, updateMode, rawDeepLink ->
            val normType = PushNotificationManager.normalizeType(rawType)
            val cleanDocId = doctorId.trim()
            val cleanLink = PushNotificationManager.sanitizeDeepLink(rawDeepLink)
            when (normType) {
                PushNotificationManager.TYPE_NEW_DOCTOR -> {
                    val resolvedDocId = cleanDocId.ifBlank {
                        if (cleanLink.startsWith("doctorlistbd://doctor/", ignoreCase = true)) {
                            cleanLink.substringAfter("doctorlistbd://doctor/").substringBefore("?").trim()
                        } else ""
                    }
                    openDoctorFromNotification(resolvedDocId)
                }
                PushNotificationManager.TYPE_APP_UPDATE -> {
                    val resolvedUrl = PushNotificationManager.sanitizeActionUrl(actionUrl).ifBlank {
                        PushNotificationManager.sanitizeActionUrl(cleanLink)
                    }
                    handleAppUpdateNotification(resolvedUrl, updateMode)
                }
                else -> {
                    if (notifId.isNotBlank()) {
                        vm.markNotificationDisplayed(notifId)
                    }
                    activeGeneralDialog = GeneralDialogPayload(
                        notificationId = notifId,
                        title = title.ifBlank { tr(lang, "গুরুত্বপূর্ণ বিজ্ঞপ্তি", "Announcement") },
                        body = body,
                        imageUrl = imageUrl
                    )
                }
            }
        }

        // Handle cold-start, background, and warm notification taps
        LaunchedEffect(pendingPushTarget) {
            val target = pendingPushTarget ?: return@LaunchedEffect
            if (target.notificationId.isNotBlank()) {
                vm.markNotificationRead(target.notificationId)
            }
            val stored = notifications.firstOrNull { it.id == target.notificationId }
            val resolvedTitle = target.title.ifBlank {
                stored?.let { tr(lang, it.titleBn.ifBlank { it.titleEn }, it.titleEn.ifBlank { it.titleBn }) }.orEmpty()
            }
            val resolvedBody = target.body.ifBlank {
                stored?.let { tr(lang, it.messageBn.ifBlank { it.messageEn }, it.messageEn.ifBlank { it.messageBn }) }.orEmpty()
            }
            val resolvedImage = target.imageUrl.ifBlank { stored?.imageUrl.orEmpty() }
            val resolvedDocId = target.doctorId.ifBlank { stored?.selectedDoctorId.orEmpty() }
            val resolvedActionUrl = target.actionUrl.ifBlank { stored?.actionUrl.orEmpty() }
            val resolvedUpdateMode = target.updateMode.ifBlank { stored?.updateMode.orEmpty() }
            val resolvedDeepLink = target.deepLink.ifBlank { stored?.deepLink.orEmpty() }
            handleTypedNotification(
                target.notificationId,
                target.type,
                resolvedTitle,
                resolvedBody,
                resolvedImage,
                resolvedDocId,
                resolvedActionUrl,
                resolvedUpdateMode,
                resolvedDeepLink
            )
            onConsumePushTarget()
        }

        // Listen for foreground General FCM events and display strictly once
        LaunchedEffect(Unit) {
            PushNotificationManager.foregroundGeneralEvents.collect { item ->
                if (!PushNotificationManager.hasDisplayedGeneral(appContext, item.id)) {
                    vm.markNotificationDisplayed(item.id)
                    activeGeneralDialog = GeneralDialogPayload(
                        notificationId = item.id,
                        title = tr(lang, item.titleBn.ifBlank { item.titleEn }, item.titleEn.ifBlank { item.titleBn }),
                        body = tr(lang, item.messageBn.ifBlank { item.messageEn }, item.messageEn.ifBlank { item.messageBn }),
                        imageUrl = item.imageUrl
                    )
                }
            }
        }

        // Auto-show the latest undisplayed GENERAL notification ONCE when the user opens the app
        LaunchedEffect(notifications, pendingPushTarget, showNotifPermissionRationale) {
            if (pendingPushTarget != null || activeGeneralDialog != null || showNotifPermissionRationale) return@LaunchedEffect
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
            val candidate = notifications
                .asSequence()
                .filter { it.messageType.equals(PushNotificationManager.TYPE_GENERAL, ignoreCase = true) }
                .filter { !it.isDisplayed && !PushNotificationManager.hasDisplayedGeneral(appContext, it.id) }
                .filter { it.createdAt >= thirtyDaysAgo }
                .maxByOrNull { it.createdAt }
            if (candidate != null) {
                vm.markNotificationDisplayed(candidate.id)
                activeGeneralDialog = GeneralDialogPayload(
                    notificationId = candidate.id,
                    title = tr(lang, candidate.titleBn.ifBlank { candidate.titleEn }, candidate.titleEn.ifBlank { candidate.titleBn }),
                    body = tr(lang, candidate.messageBn.ifBlank { candidate.messageEn }, candidate.messageEn.ifBlank { candidate.messageBn }),
                    imageUrl = candidate.imageUrl
                )
            }
        }

        activeGeneralDialog?.let { payload ->
            GeneralNotificationDialog(
                payload = payload,
                lang = lang,
                onDismiss = {
                    if (payload.notificationId.isNotBlank()) {
                        vm.markNotificationDisplayed(payload.notificationId)
                    }
                    activeGeneralDialog = null
                }
            )
        }

        if (showNotifPermissionRationale) {
            NotificationPermissionRationaleDialog(
                lang = lang,
                isPermanentlyDenied = notifPermPermanentlyDenied,
                onConfirm = {
                    showNotifPermissionRationale = false
                    if (notifPermPermanentlyDenied) {
                        PushNotificationManager.openAppNotificationSettings(appContext)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PushNotificationManager.markNotificationPermissionAsked(appContext)
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onDismiss = {
                    PushNotificationManager.markNotificationPermissionAsked(appContext)
                    showNotifPermissionRationale = false
                }
            )
        }

        if (showNotificationCenter) {
            NotificationCenterBottomSheet(
                lang = lang,
                notifications = notifications,
                isSyncing = isSyncing,
                onMarkRead = vm::markNotificationRead,
                onMarkAllRead = vm::markAllNotificationsRead,
                onRefresh = { vm.syncNow(showToast = false) },
                onOpenNotification = { item: NotificationEntity ->
                    showNotificationCenter = false
                    handleTypedNotification(
                        item.id,
                        item.messageType,
                        tr(lang, item.titleBn.ifBlank { item.titleEn }, item.titleEn.ifBlank { item.titleBn }),
                        tr(lang, item.messageBn.ifBlank { item.messageEn }, item.messageEn.ifBlank { item.messageBn }),
                        item.imageUrl,
                        item.selectedDoctorId,
                        item.actionUrl,
                        item.updateMode,
                        item.deepLink
                    )
                },
                notificationPermissionGranted = hasNotifPermission,
                onRequestEnableNotifications = {
                    showNotificationCenter = false
                    triggerNotificationPermissionFlow(true)
                },
                onDismiss = { showNotificationCenter = false }
            )
        }
    }
}
