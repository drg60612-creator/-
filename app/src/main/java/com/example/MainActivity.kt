package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContainer()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "الرئيسية", Icons.Default.Home)
    object Members : Screen("members", "الأعضاء", Icons.Default.Group)
    object Schedule : Screen("schedule", "الجدول", Icons.Default.CalendarMonth)
    object DailyReport : Screen("daily", "التقرير", Icons.Default.Today)
    object Reports : Screen("reports", "التقارير", Icons.Default.Assessment)
    object Settings : Screen("settings", "الإعدادات", Icons.Default.Settings)
}

@Composable
fun MainContainer() {
    val auth = FirebaseAuth.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = {
            currentUser = auth.currentUser
        })
    } else {
        MainAppContent(onSignedOut = {
            currentUser = null
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(onSignedOut: () -> Unit) {
    val viewModel: MembersViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val dayApprovals by viewModel.dayApprovals.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessages()
            }
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessages()
            }
        }
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    val items = listOf(
        Screen.Home,
        Screen.Members,
        Screen.Schedule,
        Screen.DailyReport,
        Screen.Reports,
        Screen.Settings
    )

    BackHandler(enabled = currentScreen != Screen.Home) {
        currentScreen = Screen.Home
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, maxLines = 1) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                Screen.Home -> HomeScreen(
                    members = members,
                    onNavigateToMembers = { currentScreen = Screen.Members },
                    onNavigateToSchedule = { currentScreen = Screen.Schedule },
                    onNavigateToDailyReport = { currentScreen = Screen.DailyReport },
                    onNavigateToReports = { currentScreen = Screen.Reports },
                    onNavigateToSettings = { currentScreen = Screen.Settings }
                )
                Screen.Members -> MembersScreen(
                    members = members,
                    shifts = shifts,
                    dayApprovals = dayApprovals,
                    searchQuery = searchQuery,
                    selectedTab = selectedTab,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onTabSelected = viewModel::setSelectedTab,
                    onAddMember = viewModel::addMember,
                    onUpdateMember = viewModel::updateMember,
                    onArchiveMember = viewModel::archiveMember,
                    onImportCsv = viewModel::importMembersFromCsv
                )
                Screen.Schedule -> ScheduleScreen(
                    viewModel = viewModel,
                    members = members,
                    shifts = shifts,
                    onBack = { currentScreen = Screen.Home }
                )
                Screen.DailyReport -> DailyReportScreen(
                    viewModel = viewModel,
                    shifts = shifts,
                    dayApprovals = dayApprovals,
                    onBack = { currentScreen = Screen.Home }
                )
                Screen.Reports -> ReportsScreen(
                    viewModel = viewModel,
                    shifts = shifts,
                    dayApprovals = dayApprovals,
                    onBack = { currentScreen = Screen.Home }
                )
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    auditLogs = auditLogs,
                    onSignedOut = onSignedOut
                )
            }
        }
    }
}
