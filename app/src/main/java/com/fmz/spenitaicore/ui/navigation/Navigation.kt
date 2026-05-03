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
import com.fmz.spenitaicore.ui.dashboard.DashboardScreen
import com.fmz.spenitaicore.ui.expenses.ExpensesScreen
import com.fmz.spenitaicore.ui.income.IncomeScreen
import com.fmz.spenitaicore.ui.insights.InsightsScreen
import com.fmz.spenitaicore.ui.settings.SettingsScreen
import com.fmz.spenitaicore.ui.scan.ReceiptScanScreen
import com.fmz.spenitaicore.ui.scan.PaySlipScanScreen
import com.fmz.spenitaicore.ui.sharedimports.SharedImportsScreen
import com.fmz.spenitaicore.viewmodel.*

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
    sharedImportSignal: Int = 0
) {
    val navController = rememberNavController()

    // Auto-navigate to shared imports if files were shared into the app
    LaunchedEffect(sharedImportSignal) {
        if (sharedImportSignal > 0) {
            navController.navigate("shared_imports") {
                launchSingleTop = true
            }
        }
    }

    // Create ViewModels (scoped to the NavHost)
    val dashboardViewModel = remember { DashboardViewModel() }
    val expensesViewModel = remember { ExpensesViewModel() }
    val incomeViewModel = remember { IncomeViewModel() }
    val insightsViewModel = remember { InsightsViewModel() }
    val settingsViewModel = remember { SettingsViewModel() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToScan = { navController.navigate("receipt_scan") },
                    onNavigateToInsights = { navController.navigate(Screen.Insights.route) },
                    onNavigateToExpenses = { navController.navigate(Screen.Expenses.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToSharedImports = { navController.navigate("shared_imports") },
                    onNavigateToPaySlipScan = { navController.navigate("payslip_scan") }
                )
            }

            composable(Screen.Expenses.route) {
                ExpensesScreen(
                    viewModel = expensesViewModel,
                    onNavigateToScan = { navController.navigate("receipt_scan") },
                    onNavigateToSharedImports = { navController.navigate("shared_imports") }
                )
            }

            composable(Screen.Income.route) {
                IncomeScreen(
                    viewModel = incomeViewModel,
                    onNavigateToScan = { navController.navigate("payslip_scan") },
                    onNavigateToSharedImports = { navController.navigate("shared_imports") }
                )
            }

            composable(Screen.Insights.route) {
                InsightsScreen(viewModel = insightsViewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable("receipt_scan") {
                val scanViewModel = remember { ReceiptScanViewModel() }
                ReceiptScanScreen(
                    viewModel = scanViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("payslip_scan") {
                val scanViewModel = remember { ReceiptScanViewModel(isIncome = true) }
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
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
