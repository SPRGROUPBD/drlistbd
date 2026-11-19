package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
import com.example.ui.ads.StartIoBannerAd
import com.example.ui.ads.StartIoConfig
import com.example.ui.ads.rememberStartIoRewardedHelper
import com.example.ui.components.MedicalTopBar
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val FIND_DOCTORS = "find_doctors"
    const val FACILITIES = "facilities"
    const val PORTAL = "portal"
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
        StartIoConfig.init(this)
        enableEdgeToEdge()
        setContent {
            DoctorDirectoryTheme {
                DoctorListBdApp()
            }
        }
    }
}

@Composable
fun DoctorListBdApp(vm: MainViewModel = viewModel()) {
    val lang by vm.currentLang.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(
            lang = lang,
            onFinished = { showSplash = false }
        )
        return
    }

    val navController = rememberNavController()
    val rewardedHelper = rememberStartIoRewardedHelper()

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
    val appointments by vm.appointments.collectAsStateWithLifecycle()
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

    val triggerRewardedAd: () -> Unit = {
        rewardedHelper.show(
            onRewarded = {
                vm.refreshDirectory()
                vm.showMessage(
                    if (lang == AppLang.BN) "ধন্যবাদ! রিওয়ার্ড সম্পন্ন এবং ডিরেক্টরি আপডেট হয়েছে।"
                    else "Thank you! Reward earned & directory refreshed."
                )
            },
            onUnavailable = {
                vm.showMessage(
                    if (lang == AppLang.BN) "ভিডিও বিজ্ঞাপন লোড হচ্ছে, অনুগ্রহ করে একটু পর আবার চেষ্টা করুন।"
                    else "Rewarded video is loading — please tap again in a moment."
                )
            }
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    val mainTabs = listOf(
        BottomTabItem(Routes.HOME, AppStrings.navHome(lang), Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        BottomTabItem(Routes.FIND_DOCTORS, AppStrings.navDoctors(lang), Icons.Rounded.MedicalServices, Icons.Outlined.MedicalServices, "nav_doctors"),
        BottomTabItem(Routes.FACILITIES, AppStrings.navFacilities(lang), Icons.Rounded.LocalHospital, Icons.Outlined.LocalHospital, "nav_facilities"),
        BottomTabItem(Routes.PORTAL, AppStrings.navPortal(lang), Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder, "nav_portal")
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
                    else -> AppStrings.appTitle(lang)
                }
                MedicalTopBar(
                    title = topTitle,
                    lang = lang,
                    unreadNotifications = unreadCount,
                    onToggleLang = { vm.toggleLanguage() },
                    onNotificationsClick = {
                        navController.navigate(Routes.PORTAL) { launchSingleTop = true }
                    },
                    onBackClick = if (showMainChrome) null else ({ navController.popBackStack() })
                )
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Persistent Start.io Banner Ad across all screens
                    StartIoBannerAd()
                    if (showMainChrome && !isWideScreen) {
                        NavigationBar(
                            containerColor = SurfaceWhite,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            mainTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
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
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
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
                            isSyncing = isSyncing,
                            specialties = specialties,
                            locations = locations,
                            doctors = publicDoctors,
                            hospitals = hospitals,
                            favoriteIds = favoriteIds,
                            isRewardedReady = rewardedHelper.isReady,
                            isRewardedLoading = rewardedHelper.isLoading,
                            onWatchRewardedAd = triggerRewardedAd,
                            onRefreshDirectory = vm::refreshDirectory,
                            onSearchSubmit = { query ->
                                vm.updateSearch { it.copy(query = query) }
                                navController.navigate(Routes.FIND_DOCTORS)
                            },
                            onSelectSpecialty = { specId ->
                                vm.updateSearch { it.copy(specialtyId = specId) }
                                navController.navigate(Routes.FIND_DOCTORS)
                            },
                            onSelectDistrict = { district ->
                                vm.updateSearch { it.copy(district = district) }
                                navController.navigate(Routes.FIND_DOCTORS)
                            },
                            onToggleFavorite = vm::toggleFavorite,
                            onDoctorClick = { id -> navController.navigate(Routes.doctorProfile(id)) },
                            onNavigateFindDoctors = { todayOnly, verifiedOnly ->
                                vm.updateSearch { it.copy(availableTodayOnly = todayOnly, verifiedOnly = verifiedOnly) }
                                navController.navigate(Routes.FIND_DOCTORS)
                            },
                            onNavigateFacilitiesTab = { tabIdx ->
                                facilitiesTab = tabIdx
                                navController.navigate(Routes.FACILITIES)
                            },
                            onNavigateInfo = { key -> navController.navigate(Routes.info(key)) }
                        )
                    }

                    composable(Routes.FIND_DOCTORS) {
                        FindDoctorsScreen(
                            lang = lang,
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
                            onDoctorClick = { id -> navController.navigate(Routes.doctorProfile(id)) }
                        )
                    }

                    composable(Routes.FACILITIES) {
                        DirectoriesScreen(
                            lang = lang,
                            selectedTab = facilitiesTab,
                            onTabChange = { facilitiesTab = it },
                            hospitals = hospitals,
                            diagnosticCenters = diagnosticCenters,
                            specialties = specialties,
                            doctors = publicDoctors,
                            onSelectSpecialty = { specId ->
                                vm.updateSearch { it.copy(specialtyId = specId) }
                                navController.navigate(Routes.FIND_DOCTORS)
                            },
                            onSelectFacility = { facName ->
                                vm.updateSearch { it.copy(facilityQuery = facName) }
                                navController.navigate(Routes.FIND_DOCTORS)
                            },
                            onDoctorClick = { id -> navController.navigate(Routes.doctorProfile(id)) }
                        )
                    }

                    composable(Routes.PORTAL) {
                        val favDocs = publicDoctors.filter { it.doctor.id in favoriteIds }
                        PatientPortalScreen(
                            lang = lang,
                            isSyncing = isSyncing,
                            favoriteDoctors = favDocs,
                            appointments = appointments,
                            notifications = notifications,
                            isRewardedReady = rewardedHelper.isReady,
                            isRewardedLoading = rewardedHelper.isLoading,
                            onWatchRewardedAd = triggerRewardedAd,
                            onRefreshDirectory = vm::refreshDirectory,
                            onToggleFavorite = vm::toggleFavorite,
                            onDoctorClick = { id -> navController.navigate(Routes.doctorProfile(id)) },
                            onMarkNotificationRead = vm::markNotificationRead,
                            onNavigateInfo = { key -> navController.navigate(Routes.info(key)) }
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
                            settings = settings,
                            isFavorite = docId in favoriteIds,
                            onToggleFavorite = { vm.toggleFavorite(docId) },
                            onSubmitReport = { reason, details -> vm.submitReport(docId, reason, details) },
                            onRequestAppointment = { chId, name, phone, day, slot, notes ->
                                vm.requestAppointment(name, phone, docId, chId, day, slot, notes)
                            },
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
    }
}
