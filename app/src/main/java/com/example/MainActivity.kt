package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.navigation.bottomNavScreens
import com.example.ui.screens.AddEditTransactionScreen
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.Emerald700
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: ExpenseViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = bottomNavScreens.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    bottomNavScreens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                screen.icon?.let {
                                    Icon(imageVector = it, contentDescription = screen.title)
                                }
                            },
                            label = { Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Emerald700.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
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
                    viewModel = viewModel,
                    onNavigateToAddTransaction = { type ->
                        navController.navigate("add_edit_transaction?initialType=$type")
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.Transactions.route)
                    },
                    onNavigateToBudgets = {
                        navController.navigate(Screen.Budgets.route)
                    },
                    onNavigateToReports = {
                        navController.navigate(Screen.Reports.route)
                    },
                    onNavigateToNotifications = {
                        navController.navigate(Screen.Notifications.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    viewModel = viewModel,
                    onNavigateToAddTransaction = {
                        navController.navigate("add_edit_transaction")
                    },
                    onEditTransaction = { txId ->
                        navController.navigate("add_edit_transaction?txId=$txId")
                    }
                )
            }

            composable(Screen.Budgets.route) {
                BudgetsScreen(
                    viewModel = viewModel
                )
            }

            composable(Screen.Reports.route) {
                ReportsScreen(
                    viewModel = viewModel
                )
            }

            composable(
                route = "add_edit_transaction?txId={txId}&initialType={initialType}",
                arguments = listOf(
                    navArgument("txId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("initialType") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "EXPENSE"
                    }
                )
            ) { backStackEntry ->
                val txId = backStackEntry.arguments?.getString("txId")
                val initialType = backStackEntry.arguments?.getString("initialType") ?: "EXPENSE"

                AddEditTransactionScreen(
                    viewModel = viewModel,
                    initialType = initialType,
                    txId = txId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Categories.route) {
                CategoriesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToCategories = {
                        navController.navigate(Screen.Categories.route)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
