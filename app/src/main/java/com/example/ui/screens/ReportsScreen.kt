package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryIcon
import com.example.ui.theme.Emerald700
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.Gold500
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.util.DateUtils
import com.example.util.PaisaHelper
import kotlinx.coroutines.launch

enum class ReportPeriod(val label: String) {
    THIS_MONTH("This Month"),
    THIS_WEEK("This Week"),
    ALL_TIME("All Time")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val user by viewModel.user.collectAsState()
    val currency = user?.currency ?: "PKR"

    var selectedPeriod by remember { mutableStateOf(ReportPeriod.THIS_MONTH) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Filter transactions by selected report period
    val periodTransactions = remember(allTransactions, selectedPeriod) {
        val (start, end) = when (selectedPeriod) {
            ReportPeriod.THIS_WEEK -> DateUtils.getWeekRange()
            ReportPeriod.THIS_MONTH -> DateUtils.getMonthRange(DateUtils.getCurrentMonth())
            ReportPeriod.ALL_TIME -> Pair(0L, Long.MAX_VALUE)
        }
        allTransactions.filter { it.transaction.occurred_on in start..end }
    }

    val totalExpenseMinor = remember(periodTransactions) {
        periodTransactions.filter { it.transaction.type == "EXPENSE" }.sumOf { it.transaction.amount_minor }
    }

    val totalIncomeMinor = remember(periodTransactions) {
        periodTransactions.filter { it.transaction.type == "INCOME" }.sumOf { it.transaction.amount_minor }
    }

    val netSavingsMinor = totalIncomeMinor - totalExpenseMinor
    val savingsRate = if (totalIncomeMinor > 0L) {
        ((netSavingsMinor.toFloat() / totalIncomeMinor.toFloat()) * 100f).coerceAtLeast(0f)
    } else 0f

    // Category breakdown
    val categoryBreakdown = remember(periodTransactions, categories, totalExpenseMinor) {
        val map = mutableMapOf<String, Long>()
        for (item in periodTransactions) {
            if (item.transaction.type == "EXPENSE") {
                val catId = item.transaction.category_id
                map[catId] = (map[catId] ?: 0L) + item.transaction.amount_minor
            }
        }

        map.entries.sortedByDescending { it.value }.map { entry ->
            val cat = categories.find { it.id == entry.key }
            val pct = if (totalExpenseMinor > 0L) (entry.value.toFloat() / totalExpenseMinor.toFloat()) * 100f else 0f
            Triple(cat, entry.value, pct)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Financial Analytics",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period Selector Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportPeriod.values().forEach { period ->
                        val isSelected = selectedPeriod == period
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPeriod = period },
                            label = { Text(period.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald700,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Summary Overview Cards (Income vs Expense vs Savings)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Cash Flow Summary (${selectedPeriod.label})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Total Income
                            Column {
                                Text(
                                    text = "Total Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = PaisaHelper.formatPkr(totalIncomeMinor, currencyCode = currency),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen
                                    )
                                )
                            }

                            // Total Expense
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Expense",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = PaisaHelper.formatPkr(totalExpenseMinor, currencyCode = currency),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Net Savings & Rate
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Net Savings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = PaisaHelper.formatPkr(netSavingsMinor, currencyCode = currency),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (netSavingsMinor >= 0) Emerald700 else ExpenseRed
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Savings Rate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${savingsRate.toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald700
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Category Expense Breakdown
            item {
                Text(
                    text = "Expense Distribution by Category",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (categoryBreakdown.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No expenses recorded for ${selectedPeriod.label.lowercase()}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(categoryBreakdown) { (cat, spent, pct) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryIcon(
                                        iconName = cat?.icon ?: "category",
                                        colorHex = cat?.color_hex ?: "#10B981",
                                        size = 36.dp,
                                        iconSize = 20.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = cat?.name ?: "Other",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                Text(
                                    text = "${PaisaHelper.formatPkr(spent, currencyCode = currency)} (${String.format("%.1f", pct)}%)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = Emerald700,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            // CSV Export Feature
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Emerald700,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Export Expenses to CSV",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Download or share your entire financial journal as a spreadsheet CSV with precision paisa values.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val csvData = viewModel.exportTransactionsCsv()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "Expense_Tracker_Export.csv")
                                        putExtra(Intent.EXTRA_TEXT, csvData)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share CSV Report"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
                                modifier = Modifier.testTag("share_csv_button")
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share CSV")
                            }

                            OutlinedButton(
                                onClick = {
                                    val csvData = viewModel.exportTransactionsCsv()
                                    clipboardManager.setText(AnnotatedString(csvData))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("CSV data copied to clipboard!")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("copy_csv_button")
                            ) {
                                Text("Copy to Clipboard")
                            }
                        }
                    }
                }
            }
        }
    }
}
