package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.AppOpenSponsorPopupDialog
import com.example.ui.ads.ConnectivityMonitor
import com.example.ui.ads.DirectSponsorRewardVideoDialog
import com.example.ui.ads.FullScreenSponsorDialog
import com.example.ui.ads.HybridNetworkAdController
import com.example.ui.ads.ManagedBannerAd
import com.example.ui.ads.OnlineAdFormat
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.RewardedAdOutcome
import com.example.ui.ads.SponsorBannerView
import com.example.ui.ads.SponsorCampaignSelector
import com.example.ui.ads.StartIoAdManager
import com.example.ui.ads.UnityAdsManager
import com.example.ui.ads.WortiseAdsManager
import com.example.ui.ads.findActivity
import com.example.ui.components.MedicalTopBar
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoctorDirectoryTheme {
                DoctorListBdApp()
            }
        }
    }

    override fun getSystemService(name: String): Any? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            HybridNetworkAdController.isDebugOrEmulator() &&
            (name.contains("adservices", ignoreCase = true) ||
                name.contains("measurement", ignoreCase = true))
        ) {
            return null
        }
        return super.getSystemService(name)
    }
}

@Composable
fun DoctorListBdApp(vm: MainViewModel = viewModel()) {
    val lang by vm.currentLang.collectAsStateWithLifecycle()
    val adConfig by vm.adConfig.collectAsStateWithLifecycle()
    val sponsors by vm.sponsors.collectAsStateWithLifecycle()
    val sponsorPackages by vm.sponsorPackages.collectAsStateWithLifecycle()
    val placementRateCards by vm.placementRateCards.collectAsStateWithLifecycle()
    val rateCardSettings by vm.rateCardSettings.collectAsStateWithLifecycle()
    val paymentSettings by vm.paymentSettings.collectAsStateWithLifecycle()
    val screenAdPlacements by vm.screenAdPlacements.collectAsStateWithLifecycle()
    val advertiserSession by vm.advertiserSession.collectAsStateWithLifecycle()
    val authoritativeNowMs by vm.authoritativeNowMs.collectAsStateWithLifecycle()
    val isOnline by vm.isOnline.collectAsStateWithLifecycle()
    val lastPopupDate by vm.lastAppOpenPopupDate.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }
    val appContext = LocalContext.current

    // Initialize Online-ONLY ad networks (Unity Ads + Start.io + Wortise) once when internet is available.
    // Strictly disabled in Offline Mode so Offline serves Custom Direct Sponsor Ads ONLY.
    LaunchedEffect(
        isOnline,
        adConfig.adsEnabled,
        adConfig.activeNetwork,
        adConfig.networkPriorityOrder,
        adConfig.unityEnabled,
        adConfig.startIoEnabled,
        adConfig.wortiseEnabled,
        adConfig.unityGameId,
        adConfig.startIoAppId,
        adConfig.wortiseAppId,
        adConfig.testMode
    ) {
        if (isOnline && adConfig.adsEnabled) {
            HybridNetworkAdController.syncOnlineSdks(appContext, adConfig, isOnline = true)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, adConfig, isOnline) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ConnectivityMonitor.refreshNow(appContext)
                vm.recordForegroundActivity()
                if (isOnline && adConfig.adsEnabled) {
                    HybridNetworkAdController.preloadRewardedForOnline(appContext, adConfig, isOnline = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Direct Sponsor Popup Advertisement (OTH-POPUP) + Full-Screen Advertisement (OTH-FULLSCREEN)
    var appOpenPopupEvaluated by remember { mutableStateOf(false) }
    var activePopupSponsor by remember { mutableStateOf<com.example.data.local.SponsorEntity?>(null) }
    var activeFullScreenSponsor by remember { mutableStateOf<com.example.data.local.SponsorEntity?>(null) }

    LaunchedEffect(
        showSplash,
        sponsors,
        placementRateCards,
        adConfig.adsEnabled,
        adConfig.appOpenPopupEnabled,
        adConfig.appOpenPopupFrequency,
        adConfig.appOpenPopupSponsorId,
        lastPopupDate,
        authoritativeNowMs
    ) {
        if (showSplash || appOpenPopupEvaluated) return@LaunchedEffect
        val fullCandidate = PlacementRateCardEngine.selectSponsorForPlacement(
            sponsors = sponsors,
            rateCards = placementRateCards,
            placementId = PlacementRateCardEngine.OTH_FULLSCREEN,
            nowMs = authoritativeNowMs
        )
        val today = SponsorCampaignSelector.todayDateKey(authoritativeNowMs)
        val shouldShow = SponsorCampaignSelector.shouldShowAppOpenPopup(
            popupEnabled = adConfig.adsEnabled && adConfig.appOpenPopupEnabled,
            frequency = adConfig.appOpenPopupFrequency,
            lastShownDate = lastPopupDate,
            todayDate = today,
            alreadyShownThisLaunch = appOpenPopupEvaluated
        )
        if (!shouldShow) {
            appOpenPopupEvaluated = true
            return@LaunchedEffect
        }
        val candidate = SponsorCampaignSelector.selectAppOpenPopupSponsor(
            sponsors = sponsors,
            preferredSponsorId = adConfig.appOpenPopupSponsorId,
            nowMs = authoritativeNowMs
        )
        if (fullCandidate != null) {
            appOpenPopupEvaluated = true
            activeFullScreenSponsor = fullCandidate
            vm.markAppOpenPopupShown()
        } else if (candidate != null) {
            appOpenPopupEvaluated = true
            activePopupSponsor = candidate
            vm.markAppOpenPopupShown()
        } else if (sponsors.isNotEmpty() || !isOnline) {
            appOpenPopupEvaluated = true
        }
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
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbar.collectAsStateWithLifecycle()

    var facilitiesTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbar()
        }
    }

    val unityRewardedReady by UnityAdsManager.isRewardedLoaded.collectAsStateWithLifecycle()
    val startIoRewardedReady by StartIoAdManager.isRewardedReady.collectAsStateWithLifecycle()
    val wortiseRewardedReady by WortiseAdsManager.isRewardedReady.collectAsStateWithLifecycle()
    var pendingRewardDoctorId by remember { mutableStateOf<String?>(null) }
    var activeDirectRewardSponsor by remember { mutableStateOf<Pair<com.example.data.local.SponsorEntity, String>?>(null) }

    val handleRewardOutcome: (android.app.Activity, String, RewardedAdOutcome) -> Unit = { act, doctorId, outcome ->
        act.runOnUiThread {
            when (outcome) {
                RewardedAdOutcome.COMPLETED,
                RewardedAdOutcome.FAILED_TO_SHOW -> {
                    navController.navigate(Routes.doctorProfile(doctorId)) {
                        launchSingleTop = true
                    }
                }
                RewardedAdOutcome.SKIPPED -> {
                    vm.showMessage(
                        tr(
                            lang,
                            "ডাক্তারের বিস্তারিত তথ্য দেখতে সম্পূর্ণ রিওয়ার্ড অ্যাড দেখুন।",
                            "Watch the complete rewarded ad to view doctor details."
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(pendingRewardDoctorId, unityRewardedReady, startIoRewardedReady, wortiseRewardedReady) {
        val targetDoctorId = pendingRewardDoctorId ?: return@LaunchedEffect
        val act = appContext.findActivity()
        if (act == null || !isOnline || !adConfig.adsEnabled || !adConfig.rewardedEnabled) {
            pendingRewardDoctorId = null
            navController.navigate(Routes.doctorProfile(targetDoctorId))
            return@LaunchedEffect
        }
        if (HybridNetworkAdController.isAnyRewardedReady(adConfig)) {
            val shown = HybridNetworkAdController.showOnlineRewardedIfReady(act, adConfig, isOnline = true) { outcome ->
                handleRewardOutcome(act, targetDoctorId, outcome)
            }
            if (shown) {
                pendingRewardDoctorId = null
                return@LaunchedEffect
            }
        }
        var elapsedMs = 0L
        while (elapsedMs < 2600L && pendingRewardDoctorId == targetDoctorId) {
            delay(200L)
            elapsedMs += 200L
            if (HybridNetworkAdController.isAnyRewardedReady(adConfig)) {
                val shown = HybridNetworkAdController.showOnlineRewardedIfReady(act, adConfig, isOnline = true) { outcome ->
                    handleRewardOutcome(act, targetDoctorId, outcome)
                }
                if (shown) {
                    pendingRewardDoctorId = null
                    return@LaunchedEffect
                }
            } else if (!HybridNetworkAdController.isAnyRewardedLoading(adConfig)) {
                break
            }
        }
        if (pendingRewardDoctorId == targetDoctorId) {
            pendingRewardDoctorId = null
            val shownAfterWait = HybridNetworkAdController.showOnlineRewardedIfReady(act, adConfig, isOnline = true) { outcome ->
                handleRewardOutcome(act, targetDoctorId, outcome)
            }
            if (!shownAfterWait) {
                navController.navigate(Routes.doctorProfile(targetDoctorId)) {
                    launchSingleTop = true
                }
            }
        }
    }

    // Show online Start.io / Unity Rewarded Ad (unchanged) OR active Direct Sponsor Reward Video on doctor click
    val openDoctorProfile: (String) -> Unit = { doctorId ->
        if (pendingRewardDoctorId == null && activeDirectRewardSponsor == null) {
            val act = appContext.findActivity()
            val directRewardSponsor = if (adConfig.adsEnabled) {
                SponsorCampaignSelector.selectRewardVideoSponsor(
                    sponsors = sponsors,
                    rotationTick = doctorId.hashCode(),
                    nowMs = authoritativeNowMs
                )
            } else null

            val shouldShowOnlineNetworkReward = isOnline &&
                adConfig.adsEnabled &&
                adConfig.rewardedEnabled &&
                HybridNetworkAdController.getOrderedNetworksForOnline(adConfig, OnlineAdFormat.REWARDED).isNotEmpty() &&
                act != null

            if (!shouldShowOnlineNetworkReward) {
                if (directRewardSponsor != null) {
                    activeDirectRewardSponsor = directRewardSponsor to doctorId
                } else {
                    navController.navigate(Routes.doctorProfile(doctorId)) {
                        launchSingleTop = true
                    }
                }
            } else {
                val shownNow = HybridNetworkAdController.showOnlineRewardedIfReady(act!!, adConfig, isOnline = true) { outcome ->
                    handleRewardOutcome(act, doctorId, outcome)
                }
                if (!shownNow) {
                    if (directRewardSponsor != null) {
                        HybridNetworkAdController.preloadRewardedForOnline(appContext, adConfig, isOnline = true)
                        activeDirectRewardSponsor = directRewardSponsor to doctorId
                    } else {
                        HybridNetworkAdController.preloadRewardedForOnline(appContext, adConfig, isOnline = true)
                        pendingRewardDoctorId = doctorId
                    }
                }
            }
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

    val showMainChrome = currentRoute in mainTabs.map { it.route }
    val unreadCount = notifications.count { !it.isRead }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                val topTitle = when (currentRoute) {
                    Routes.HOME -> AppStrings.appTitle(lang)
                    Routes.FIND_DOCTORS -> AppStrings.navDoctors(lang)
                    Routes.FACILITIES -> AppStrings.navFacilities(lang)
                    Routes.PORTAL -> AppStrings.navPortal(lang)
                    Routes.ABOUT_US -> AppStrings.aboutUs(lang)
                    Routes.ADVERTISER_PORTAL -> tr(lang, "অ্যাডভার্টাইজার পোর্টাল", "Advertiser Portal")
                    else -> AppStrings.appTitle(lang)
                }
                MedicalTopBar(
                    title = topTitle,
                    lang = lang,
                    unreadNotifications = unreadCount,
                    onToggleLang = { vm.toggleLanguage() },
                    onNotificationsClick = { navigateToTab(Routes.PORTAL) },
                    onBackClick = if (showMainChrome) null else ({ navController.popBackStack() })
                )
            },
            bottomBar = {
                val currentPageKey = SponsorCampaignSelector.normalizeRouteToPageKey(currentRoute)
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Custom Direct Sponsor Banner (Works ONLINE + OFFLINE Cached; hidden on Advertiser Portal)
                    if (currentRoute != Routes.ADVERTISER_PORTAL) {
                        SponsorBannerView(
                            sponsors = sponsors,
                            adConfig = adConfig,
                            pageKey = currentPageKey,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            lang = lang,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick
                        )
                        // 2. Online-ONLY Multi-Network Banner (Unity Ads -> Start.io -> Wortise; auto-disabled offline)
                        if (isOnline) {
                            ManagedBannerAd(
                                adConfig = adConfig,
                                isOnline = isOnline,
                                refreshKey = currentPageKey,
                                screenKey = currentPageKey,
                                screenPlacements = screenAdPlacements
                            )
                        }
                    }
                    if (showMainChrome && !isWideScreen) {
                        NavigationBar(
                            containerColor = SurfaceWhite
                        ) {
                            mainTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
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
                                        selectedIconColor = MedicalBlueDark,
                                        selectedTextColor = MedicalBlueDark,
                                        indicatorColor = MedicalBlueLight
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
                    NavigationRail(containerColor = SurfaceWhite) {
                        mainTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
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
                        HomeScreen(
                            lang = lang,
                            settings = settings,
                            adConfig = adConfig,
                            sponsors = sponsors,
                            rateCards = placementRateCards,
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
                            onRefreshDirectory = vm::refreshDirectory,
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
                            }
                        )
                    }

                    composable(Routes.FIND_DOCTORS) {
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
                            onDoctorClick = openDoctorProfile
                        )
                    }

                    composable(Routes.FACILITIES) {
                        DirectoriesScreen(
                            lang = lang,
                            adConfig = adConfig,
                            sponsors = sponsors,
                            rateCards = placementRateCards,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onSponsorDisplayed = vm::recordSponsorDisplay,
                            onSponsorClicked = vm::recordSponsorClick,
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
                            onDoctorClick = openDoctorProfile
                        )
                    }

                    composable(Routes.PORTAL) {
                        val favDocs = publicDoctors.filter { it.doctor.id in favoriteIds }
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
                            notifications = notifications,
                            onRefreshDirectory = vm::refreshDirectory,
                            onToggleFavorite = vm::toggleFavorite,
                            onDoctorClick = openDoctorProfile,
                            onMarkNotificationRead = vm::markNotificationRead,
                            onOpenAdvertiserPortal = { navController.navigate(Routes.ADVERTISER_PORTAL) },
                            onNavigateInfo = { key ->
                                if (key == "about") {
                                    navigateToTab(Routes.ABOUT_US)
                                } else {
                                    navController.navigate(Routes.info(key))
                                }
                            }
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
                            onNavigateInfo = { key -> navController.navigate(Routes.info(key)) }
                        )
                    }

                    composable(Routes.ADVERTISER_PORTAL) {
                        AdvertiserPortalScreen(
                            lang = lang,
                            session = advertiserSession,
                            cachedPackages = sponsorPackages,
                            cachedPaymentSettings = paymentSettings,
                            isOnline = isOnline,
                            nowMs = authoritativeNowMs,
                            onLoginSuccess = vm::saveAdvertiserSession,
                            onLogout = vm::logoutAdvertiser,
                            onShowMessage = vm::showMessage
                        )
                    }

                    composable(
                        route = Routes.DOCTOR_PROFILE,
                        arguments = listOf(navArgument("doctorId") { type = NavType.StringType })
                    ) { backStack ->
                        val docId = backStack.arguments?.getString("doctorId").orEmpty()
                        val profile = publicDoctors.firstOrNull { it.doctor.id == docId }
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
                            onBack = { navController.popBackStack() }
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

        // Direct Sponsor Full Screen Advertisement (OTH-FULLSCREEN)
        activeFullScreenSponsor?.let { fsSponsor ->
            FullScreenSponsorDialog(
                sponsor = fsSponsor,
                isOnline = isOnline,
                lang = lang,
                onDisplayed = vm::recordSponsorDisplay,
                onClicked = vm::recordSponsorClick,
                onDismiss = { activeFullScreenSponsor = null }
            )
        }

        // Direct Sponsor App Open Popup (Online + Offline cached; dismissible immediately via Close X)
        activePopupSponsor?.let { popupSponsor ->
            AppOpenSponsorPopupDialog(
                sponsor = popupSponsor,
                isOnline = isOnline,
                lang = lang,
                onDisplayed = vm::recordSponsorDisplay,
                onClicked = vm::recordSponsorClick,
                onDismiss = {
                    activePopupSponsor = null
                }
            )
        }

        // Direct Sponsor Reward Video – Doctor Click (Works Online + Offline Cached)
        activeDirectRewardSponsor?.let { (rewardSponsor, targetDoctorId) ->
            DirectSponsorRewardVideoDialog(
                sponsor = rewardSponsor,
                isOnline = isOnline,
                lang = lang,
                onDisplayed = vm::recordSponsorDisplay,
                onClicked = vm::recordSponsorClick,
                onCompleted = {
                    activeDirectRewardSponsor = null
                    navController.navigate(Routes.doctorProfile(targetDoctorId)) {
                        launchSingleTop = true
                    }
                },
                onDismiss = {
                    activeDirectRewardSponsor = null
                }
            )
        }

        // Online-ONLY Multi-Network App Open / Interstitial (Unity Ads -> Start.io -> Wortise)
        // Only shown when online, cooldown & session cap met, and no sponsor popup is open
        LaunchedEffect(showSplash, activePopupSponsor, activeFullScreenSponsor, appOpenPopupEvaluated, isOnline) {
            if (!showSplash && appOpenPopupEvaluated && activePopupSponsor == null && activeFullScreenSponsor == null && isOnline && adConfig.adsEnabled && (adConfig.appOpenEnabled || adConfig.interstitialEnabled)) {
                appContext.findActivity()?.let { act ->
                    val shownAppOpen = HybridNetworkAdController.maybeShowOnlineAppOpen(act, adConfig, isOnline = true)
                    if (!shownAppOpen) {
                        HybridNetworkAdController.maybeShowOnlineInterstitial(act, adConfig, isOnline = true)
                    }
                }
            }
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
    }
}
