package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Transactions : Screen("transactions", "Transactions", Icons.Default.ReceiptLong)
    object Budgets : Screen("budgets", "Budgets", Icons.Default.AccountBalanceWallet)
    object Reports : Screen("reports", "Reports", Icons.Default.BarChart)
    object AddEditTransaction : Screen("add_edit_transaction?txId={txId}", "Add Transaction") {
        fun createRoute(txId: String? = null) = if (txId != null) "add_edit_transaction?txId=$txId" else "add_edit_transaction"
    }
    object Categories : Screen("categories", "Categories", Icons.Default.Category)
    object Notifications : Screen("notifications", "Alerts", Icons.Default.Notifications)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Transactions,
    Screen.Budgets,
    Screen.Reports
)
