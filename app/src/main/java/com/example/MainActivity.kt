package com.example

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.model.UserRole
import com.example.i18n.AppStrings
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
    const val LOGIN = "login"
    const val REGISTER_DOCTOR = "register_doctor"
    const val REGISTER_USER = "register_user"
    const val DOCTOR_DASHBOARD = "doctor_dashboard"
    const val HOSPITAL_DASHBOARD = "hospital_dashboard"
    const val ADMIN_PANEL = "admin_panel"

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
                DoctorDirectoryRootApp()
            }
        }
    }
}

@Composable
fun DoctorDirectoryRootApp(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val lang by vm.currentLang.collectAsStateWithLifecycle()
    val currentUser by vm.currentUser.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val specialties by vm.specialties.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val hospitals by vm.visibleHospitals.collectAsStateWithLifecycle()
    val allHospitals by vm.allHospitals.collectAsStateWithLifecycle()
    val diagnosticCenters by vm.visibleDiagnosticCenters.collectAsStateWithLifecycle()
    val allDiagnosticCenters by vm.allDiagnosticCenters.collectAsStateWithLifecycle()
    val publicDoctors by vm.publicDoctors.collectAsStateWithLifecycle()
    val allDoctors by vm.allDoctorsFull.collectAsStateWithLifecycle()
    val filteredDoctors by vm.filteredDoctors.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val visibleCount by vm.visiblePageCount.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteDoctorIds.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val users by vm.visibleUsers.collectAsStateWithLifecycle()
    val verificationRequests by vm.verificationRequests.collectAsStateWithLifecycle()
    val profileChangeRequests by vm.profileChangeRequests.collectAsStateWithLifecycle()
    val hospitalDoctorLinks by vm.hospitalDoctorLinks.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    val auditLogs by vm.auditLogs.collectAsStateWithLifecycle()
    val adminStats by vm.adminStats.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbar.collectAsStateWithLifecycle()

    var facilitiesTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbar()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    val mainTabs = listOf(
        BottomTabItem(Routes.HOME, AppStrings.navHome(lang), Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        BottomTabItem(Routes.FIND_DOCTORS, AppStrings.navDoctors(lang), Icons.Rounded.MedicalServices, Icons.Outlined.MedicalServices, "nav_doctors"),
        BottomTabItem(Routes.FACILITIES, AppStrings.navFacilities(lang), Icons.Rounded.LocalHospital, Icons.Outlined.LocalHospital, "nav_facilities"),
        BottomTabItem(Routes.PORTAL, AppStrings.navPortal(lang), Icons.Rounded.Person, Icons.Outlined.Person, "nav_portal")
    )

    val showMainChrome = currentRoute in mainTabs.map { it.route }
    val showAdminChrome = currentRoute == Routes.ADMIN_PANEL
    val unreadCount = notifications.count { !it.isRead }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!showAdminChrome) {
                    val topTitle = when {
                        currentRoute == Routes.HOME -> AppStrings.appTitle(lang)
                        currentRoute == Routes.FIND_DOCTORS -> AppStrings.navDoctors(lang)
                        currentRoute == Routes.FACILITIES -> AppStrings.navFacilities(lang)
                        currentRoute == Routes.PORTAL -> AppStrings.navPortal(lang)
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
                }
            },
            bottomBar = {
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
                            specialties = specialties,
                            locations = locations,
                            doctors = publicDoctors,
                            hospitals = hospitals,
                            favoriteIds = favoriteIds,
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
                            onNavigateInfo = { key -> navController.navigate(Routes.info(key)) },
                            onNavigateAuth = { route -> navController.navigate(route) }
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
                            currentUser = currentUser,
                            favoriteDoctors = favDocs,
                            appointments = appointments,
                            notifications = notifications,
                            onToggleFavorite = vm::toggleFavorite,
                            onDoctorClick = { id -> navController.navigate(Routes.doctorProfile(id)) },
                            onGoLogin = { navController.navigate(Routes.LOGIN) },
                            onGoRegisterDoctor = { navController.navigate(Routes.REGISTER_DOCTOR) },
                            onGoRegisterUser = { navController.navigate(Routes.REGISTER_USER) },
                            onOpenRoleDashboard = { role ->
                                when (role) {
                                    UserRole.DOCTOR -> navController.navigate(Routes.DOCTOR_DASHBOARD)
                                    UserRole.HOSPITAL_MANAGER -> navController.navigate(Routes.HOSPITAL_DASHBOARD)
                                    UserRole.SUPER_ADMIN, UserRole.ADMIN -> navController.navigate(Routes.ADMIN_PANEL)
                                    else -> {}
                                }
                            },
                            onLogout = { vm.logout() },
                            onMarkNotificationRead = vm::markNotificationRead
                        )
                    }

                    composable(
                        route = Routes.DOCTOR_PROFILE,
                        arguments = listOf(navArgument("doctorId") { type = NavType.StringType })
                    ) { backStack ->
                        val docId = backStack.arguments?.getString("doctorId").orEmpty()
                        val profile = publicDoctors.firstOrNull { it.doctor.id == docId }
                            ?: allDoctors.firstOrNull { it.doctor.id == docId }
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

                    composable(Routes.LOGIN) {
                        LoginScreen(
                            lang = lang,
                            onLogin = { email, pass ->
                                vm.login(email, pass) { user ->
                                    when (user.role) {
                                        UserRole.SUPER_ADMIN.name, UserRole.ADMIN.name -> {
                                            navController.navigate(Routes.ADMIN_PANEL) {
                                                popUpTo(Routes.HOME)
                                            }
                                        }
                                        UserRole.DOCTOR.name -> {
                                            navController.navigate(Routes.DOCTOR_DASHBOARD) {
                                                popUpTo(Routes.HOME)
                                            }
                                        }
                                        UserRole.HOSPITAL_MANAGER.name -> {
                                            navController.navigate(Routes.HOSPITAL_DASHBOARD) {
                                                popUpTo(Routes.HOME)
                                            }
                                        }
                                        else -> {
                                            navController.navigate(Routes.PORTAL) {
                                                popUpTo(Routes.HOME)
                                            }
                                        }
                                    }
                                }
                            },
                            onResetPassword = vm::resetPassword,
                            onGoRegisterDoctor = { navController.navigate(Routes.REGISTER_DOCTOR) },
                            onGoRegisterUser = { navController.navigate(Routes.REGISTER_USER) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.REGISTER_DOCTOR) {
                        DoctorRegistrationScreen(
                            lang = lang,
                            specialties = specialties,
                            onRegisterDoctor = { nBn, nEn, em, pw, ph, bmdc, sp, hosp, desig ->
                                vm.registerDoctor(nBn, nEn, em, pw, ph, bmdc, sp, hosp, desig) {
                                    navController.navigate(Routes.DOCTOR_DASHBOARD) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.REGISTER_USER) {
                        UserRegistrationScreen(
                            lang = lang,
                            onRegister = { name, em, pw, ph, dist, role ->
                                vm.registerUser(name, em, pw, ph, dist, role) {
                                    val target = if (role == UserRole.HOSPITAL_MANAGER) Routes.HOSPITAL_DASHBOARD else Routes.PORTAL
                                    navController.navigate(target) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.DOCTOR_DASHBOARD) {
                        val user = currentUser
                        if (user == null || user.role != UserRole.DOCTOR.name) {
                            LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) } }
                        } else {
                            val myDoc = allDoctors.firstOrNull { it.doctor.userId == user.id || it.doctor.id == "doc_${user.id}" }
                            DoctorDashboardScreen(
                                lang = lang,
                                currentUser = user,
                                doctorProfile = myDoc,
                                specialties = specialties,
                                appointments = appointments,
                                onSaveProfile = { vm.saveDoctor(it, isAdminAction = false) },
                                onAddEducation = vm::addEducation,
                                onDeleteEducation = vm::deleteEducation,
                                onAddEmployment = vm::addEmployment,
                                onDeleteEmployment = vm::deleteEmployment,
                                onSaveChamber = vm::saveChamber,
                                onDeleteChamber = vm::deleteChamber,
                                onAddSchedule = vm::addSchedule,
                                onDeleteSchedule = vm::deleteSchedule,
                                onSubmitVerification = vm::submitVerificationRequest,
                                onSubmitChangeRequest = vm::submitProfileChangeRequest,
                                onUpdateAppointment = vm::updateAppointmentStatus,
                                onBack = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) } }
                            )
                        }
                    }

                    composable(Routes.HOSPITAL_DASHBOARD) {
                        val user = currentUser
                        if (user == null || user.role != UserRole.HOSPITAL_MANAGER.name) {
                            LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) } }
                        } else {
                            HospitalDashboardScreen(
                                lang = lang,
                                currentUser = user,
                                hospitals = allHospitals,
                                doctors = publicDoctors,
                                links = hospitalDoctorLinks,
                                onSaveHospital = vm::saveHospital,
                                onRequestDoctorLink = vm::requestHospitalDoctorLink,
                                onAddDoctorChamber = vm::saveChamber,
                                onAddSchedule = vm::addSchedule,
                                onBack = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) } }
                            )
                        }
                    }

                    composable(Routes.ADMIN_PANEL) {
                        val user = currentUser
                        val isAuthorized = user != null && (user.role == UserRole.SUPER_ADMIN.name || user.role == UserRole.ADMIN.name)
                        if (!isAuthorized) {
                            LaunchedEffect(Unit) { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) } }
                        } else {
                            AdminPanelScreen(
                                lang = lang,
                                currentUser = user!!,
                                stats = adminStats,
                                doctors = allDoctors,
                                hospitals = allHospitals,
                                diagnosticCenters = allDiagnosticCenters,
                                specialties = specialties,
                                locations = locations,
                                users = users,
                                verificationRequests = verificationRequests,
                                profileChangeRequests = profileChangeRequests,
                                hospitalDoctorLinks = hospitalDoctorLinks,
                                reports = reports,
                                notifications = notifications,
                                auditLogs = auditLogs,
                                settings = settings,
                                onSaveDoctor = { vm.saveDoctor(it, isAdminAction = true) },
                                onDeleteDoctor = vm::deleteDoctor,
                                onSetDoctorApproval = vm::setDoctorApproval,
                                onSetDoctorActive = vm::setDoctorActive,
                                onVerifyBmdc = vm::verifyDoctorBmdc,
                                onReviewVerificationReq = vm::reviewVerificationRequest,
                                onReviewProfileChangeReq = vm::reviewProfileChangeRequest,
                                onUpdateLinkStatus = vm::updateHospitalDoctorLinkStatus,
                                onSaveHospital = vm::saveHospital,
                                onDeleteHospital = vm::deleteHospital,
                                onSaveDiagnostic = vm::saveDiagnosticCenter,
                                onDeleteDiagnostic = vm::deleteDiagnosticCenter,
                                onSaveSpecialty = vm::saveSpecialty,
                                onDeleteSpecialty = vm::deleteSpecialty,
                                onSaveLocation = vm::saveLocation,
                                onDeleteLocation = vm::deleteLocation,
                                onUpdateUserRole = vm::updateUserRole,
                                onResolveReport = vm::resolveReport,
                                onSendNotification = vm::sendNotification,
                                onSaveSettings = vm::saveSettings,
                                onClearDemoData = vm::clearAllDemoData,
                                onExportDoctorsCsv = {
                                    val csv = vm.repo.buildDoctorsCsv(allDoctors)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                        putExtra(Intent.EXTRA_SUBJECT, "doctors_directory_bd.csv")
                                        type = "text/csv"
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Export Doctors CSV"))
                                },
                                onExportHospitalsCsv = {
                                    val csv = vm.repo.buildHospitalsCsv(allHospitals)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                        putExtra(Intent.EXTRA_SUBJECT, "hospitals_directory_bd.csv")
                                        type = "text/csv"
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Export Hospitals CSV"))
                                },
                                onLogout = {
                                    vm.logout {
                                        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) }
                                    }
                                },
                                onBack = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
