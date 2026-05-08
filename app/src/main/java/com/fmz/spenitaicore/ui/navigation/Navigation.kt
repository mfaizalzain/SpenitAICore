package com.fmz.spenitaicore.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fmz.spenitaicore.AppContainer
import com.fmz.spenitaicore.ui.auth.LoginScreen
import com.fmz.spenitaicore.ui.dashboard.DashboardScreen
import com.fmz.spenitaicore.ui.expenses.ExpensesScreen
import com.fmz.spenitaicore.ui.income.IncomeScreen
import com.fmz.spenitaicore.ui.insights.InsightsScreen
import com.fmz.spenitaicore.ui.settings.SettingsScreen
import com.fmz.spenitaicore.ui.scan.ReceiptScanScreen
import com.fmz.spenitaicore.ui.scan.PaySlipScanScreen
import com.fmz.spenitaicore.ui.sharedimports.SharedImportsScreen
import com.fmz.spenitaicore.viewmodel.*
import kotlinx.coroutines.flow.Flow

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Outlined.Home, Icons.Filled.Home)
    data object Expenses : Screen("expenses", "Expenses", Icons.Outlined.Receipt, Icons.Filled.Receipt)
    data object Income : Screen("income", "Income", Icons.Outlined.TrendingUp, Icons.Filled.TrendingUp)
    data object Insights : Screen("insights", "Insights", Icons.Outlined.Insights, Icons.Filled.Insights)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Expenses,
    Screen.Income,
    Screen.Insights,
    Screen.Settings
)

@Composable
fun SpenItNavHost(
    container: AppContainer,
    sharedImportSignal: Int = 0,
    navigateToImportsFlow: Flow<Unit>? = null
) {
    val navController = rememberNavController()

    // Auth state
    val authViewModel = remember { AuthViewModel() }
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    // Determine start destination based on auth state
    val startDestination = if (authState.isLoggedIn) Screen.Dashboard.route else "login"
    var pendingSharedImportNavigation by remember { mutableStateOf(sharedImportSignal > 0) }
    var sharedImportCount by remember {
        mutableIntStateOf(container.sharedImportStore.load().size)
    }

    // Navigate to shared imports when a file is shared into the app.
    // Uses a Channel/Flow from the Activity so navigation is decoupled from
    // Compose state timing and works reliably from both onCreate and onNewIntent.
    LaunchedEffect(Unit) {
        navigateToImportsFlow?.collect {
            pendingSharedImportNavigation = true
        }
    }

    LaunchedEffect(sharedImportSignal) {
        if (sharedImportSignal > 0) {
            sharedImportCount = container.sharedImportStore.load().size
            pendingSharedImportNavigation = true
        }
    }

    LaunchedEffect(pendingSharedImportNavigation, authState.isLoggedIn) {
        if (pendingSharedImportNavigation && authState.isLoggedIn) {
            navController.navigate("shared_imports") {
                launchSingleTop = true
            }
            pendingSharedImportNavigation = false
        }
    }

    // Create ViewModels (scoped to the NavHost)
    val dashboardViewModel = remember { DashboardViewModel() }
    val expensesViewModel = remember { ExpensesViewModel() }
    val incomeViewModel = remember { IncomeViewModel() }
    val insightsViewModel = remember { InsightsViewModel() }
    val settingsViewModel = remember { SettingsViewModel() }

    // Don't show bottom bar on login screen
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute != "login"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == screen.route) screen.selectedIcon else screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Login screen
            composable("login") {
                LoginScreen(
                    viewModel = authViewModel,
                    onSignedIn = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToScan = { navController.navigate("receipt_scan") },
                    onNavigateToInsights = { navController.navigate(Screen.Insights.route) },
                    onNavigateToExpenses = { navController.navigate(Screen.Expenses.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToSharedImports = { navController.navigate("shared_imports") },
                    onNavigateToPaySlipScan = { navController.navigate("payslip_scan") },
                    sharedImportCount = sharedImportCount
                )
            }

            composable(Screen.Expenses.route) {
                ExpensesScreen(
                    viewModel = expensesViewModel,
                    onNavigateToScan = { navController.navigate("receipt_scan") },
                    onNavigateToSharedImports = { navController.navigate("shared_imports") },
                    sharedImportCount = sharedImportCount
                )
            }

            composable(Screen.Income.route) {
                IncomeScreen(
                    viewModel = incomeViewModel,
                    onNavigateToScan = { navController.navigate("payslip_scan") },
                    onNavigateToSharedImports = { navController.navigate("shared_imports") },
                    sharedImportCount = sharedImportCount
                )
            }

            composable(Screen.Insights.route) {
                InsightsScreen(viewModel = insightsViewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    authViewModel = authViewModel,
                    onSignOut = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                "receipt_scan?receiptId={receiptId}",
                arguments = listOf(
                    navArgument("receiptId") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val receiptId = backStackEntry.arguments?.getInt("receiptId") ?: 0
                val scanViewModel = remember { ReceiptScanViewModel() }
                if (receiptId > 0) {
                    LaunchedEffect(receiptId) {
                        scanViewModel.prepareEdit(receiptId)
                    }
                }
                ReceiptScanScreen(
                    viewModel = scanViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                "payslip_scan?incomeEntryId={incomeEntryId}",
                arguments = listOf(
                    navArgument("incomeEntryId") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val incomeEntryId = backStackEntry.arguments?.getInt("incomeEntryId") ?: 0
                val scanViewModel = remember { ReceiptScanViewModel(isIncome = true) }
                if (incomeEntryId > 0) {
                    LaunchedEffect(incomeEntryId) {
                        scanViewModel.prepareEdit(incomeEntryId)
                    }
                }
                PaySlipScanScreen(
                    viewModel = scanViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("shared_imports") {
                val sharedImportsViewModel = remember { SharedImportsViewModel() }
                SharedImportsScreen(
                    viewModel = sharedImportsViewModel,
                    pendingImportSignal = sharedImportSignal,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditReceipt = { receiptId ->
                        navController.navigate("receipt_scan?receiptId=$receiptId")
                    },
                    onNavigateToEditIncome = { incomeEntryId ->
                        navController.navigate("payslip_scan?incomeEntryId=$incomeEntryId")
                    },
                    onImportCountChanged = { count ->
                        sharedImportCount = count
                    }
                )
            }
        }
    }
}
