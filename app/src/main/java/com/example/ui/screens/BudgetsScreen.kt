package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetEntity
import com.example.ui.components.BudgetProgressCard
import com.example.ui.components.CategoryIcon
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Gold500
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.util.DateUtils
import com.example.util.PaisaHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val budgets by viewModel.budgets.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val monthStats by viewModel.thisMonthStats.collectAsState()
    val user by viewModel.user.collectAsState()
    val currency = user?.currency ?: "PKR"

    val (incomeMinor, expenseMinor, catSpendMap) = monthStats
    val expenseCategories = remember(categories) {
        categories.filter { it.type == "EXPENSE" }
    }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var dialogCategoryId by remember { mutableStateOf<String?>(null) } // null = overall
    var dialogLimitInput by remember { mutableStateOf("") }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    // Budget Dialog
    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = {
                Text(
                    text = if (dialogCategoryId == null) "Set Monthly Overall Budget" else "Set Category Budget",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Category selector inside dialog
                    ExposedDropdownMenuBox(
                        expanded = isCategoryDropdownExpanded,
                        onExpandedChange = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded }
                    ) {
                        val selectedCatName = if (dialogCategoryId == null) {
                            "Overall Monthly Budget (All Expenses)"
                        } else {
                            categories.find { it.id == dialogCategoryId }?.name ?: "Select Category"
                        }

                        OutlinedTextField(
                            value = selectedCatName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Budget Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isCategoryDropdownExpanded,
                            onDismissRequest = { isCategoryDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Overall Monthly Budget (All Expenses)", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    dialogCategoryId = null
                                    isCategoryDropdownExpanded = false
                                }
                            )
                            expenseCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CategoryIcon(iconName = cat.icon, colorHex = cat.color_hex, size = 26.dp, iconSize = 14.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(cat.name)
                                        }
                                    },
                                    onClick = {
                                        dialogCategoryId = cat.id
                                        isCategoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Limit input
                    OutlinedTextField(
                        value = dialogLimitInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' }) {
                                dialogLimitInput = input
                            }
                        },
                        label = { Text("Limit in $currency") },
                        placeholder = { Text("e.g. 50000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("budget_limit_input")
                    )

                    Text(
                        text = "💡 Threshold Alert Rule: You'll receive instant in-app alerts when reaching 80% warning and 100% limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = dialogLimitInput.toDoubleOrNull()
                        if (limit != null && limit > 0.0) {
                            viewModel.saveBudget(dialogCategoryId, limit)
                            showBudgetDialog = false
                            dialogLimitInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
                    modifier = Modifier.testTag("save_budget_dialog_button")
                ) {
                    Text("Save Budget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Monthly Budgets",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    dialogCategoryId = null
                    dialogLimitInput = ""
                    showBudgetDialog = true
                },
                containerColor = Emerald700,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_budget_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Budget")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month Header & Rule Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Emerald700,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Active Month: ${DateUtils.formatMonthString(DateUtils.getCurrentMonth())}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Automated threshold checks notify you once at 80% and 100% capacity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Overall Monthly Budget
            item {
                Text(
                    text = "Overall Monthly Budget",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            item {
                val overall = overallBudget
                if (overall != null && overall.limit_minor > 0L) {
                    BudgetProgressCard(
                        title = "All Expenses",
                        spentMinor = expenseMinor,
                        limitMinor = overall.limit_minor,
                        currencyCode = currency
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No overall monthly budget set",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Set an overall spending limit to keep your monthly cash flow in check.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    dialogCategoryId = null
                                    dialogLimitInput = ""
                                    showBudgetDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                            ) {
                                Text("Set Overall Budget")
                            }
                        }
                    }
                }
            }

            // Category Budgets Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Budgets",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = {
                        dialogCategoryId = expenseCategories.firstOrNull()?.id
                        dialogLimitInput = ""
                        showBudgetDialog = true
                    }) {
                        Text("+ Add Category Budget", color = Emerald700, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Category Budgets List
            val categoryBudgetsList = budgets.filter { it.category_id != null }
            if (categoryBudgetsList.isEmpty()) {
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
                                text = "No category budgets configured yet.\nTap '+ Add Category Budget' to set limits for Food, Transport, Rent, etc.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(categoryBudgetsList, key = { it.id }) { catBudget ->
                    val category = categories.find { it.id == catBudget.category_id }
                    val spent = catSpendMap[catBudget.category_id] ?: 0L

                    BudgetProgressCard(
                        title = category?.name ?: "Category",
                        spentMinor = spent,
                        limitMinor = catBudget.limit_minor,
                        currencyCode = currency,
                        categoryIcon = category?.icon,
                        categoryColorHex = category?.color_hex
                    )
                }
            }
        }
    }
}
