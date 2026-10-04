package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Emerald600
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.Gold600
import com.example.ui.theme.WarningOrange
import com.example.util.PaisaHelper

data class MonthlyBarData(
    val monthLabel: String,
    val expenseMinor: Long,
    val budgetLimitMinor: Long
)

@Composable
fun MonthlyExpensesVsBudgetChartCard(
    currentExpenseMinor: Long,
    budgetLimitMinor: Long,
    monthlyHistory: List<MonthlyBarData> = emptyList(),
    currencyCode: String = "PKR",
    modifier: Modifier = Modifier
) {
    val percentage = if (budgetLimitMinor > 0) {
        (currentExpenseMinor.toFloat() / budgetLimitMinor.toFloat() * 100f).coerceAtLeast(0f)
    } else 0f

    val remainingMinor = budgetLimitMinor - currentExpenseMinor

    val (statusColor, statusLabel) = when {
        budgetLimitMinor <= 0L -> Pair(MaterialTheme.colorScheme.outline, "No Limit Set")
        percentage >= 100f -> Pair(ExpenseRed, "Budget Exceeded")
        percentage >= 80f -> Pair(WarningOrange, "Threshold Warning")
        else -> Pair(Emerald600, "Within Limit")
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "chartProgress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expenses_vs_budget_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Emerald600.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Expenses vs. Budget",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Monthly Limit Summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stat Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Expense
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Total Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = PaisaHelper.formatPkr(currentExpenseMinor, currencyCode = currencyCode),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = ExpenseRed
                        )
                    }
                }

                // Limit
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Budget Limit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (budgetLimitMinor > 0) PaisaHelper.formatPkr(budgetLimitMinor, currencyCode = currencyCode) else "Not Set",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Emerald600
                        )
                    }
                }

                // Remaining
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (budgetLimitMinor > 0) PaisaHelper.formatPkr(remainingMinor.coerceAtLeast(0), currencyCode = currencyCode) else "—",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (remainingMinor >= 0) Emerald600 else ExpenseRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Canvas Chart
            val displayBars = if (monthlyHistory.isNotEmpty()) {
                monthlyHistory
            } else {
                listOf(
                    MonthlyBarData("This Month", currentExpenseMinor, budgetLimitMinor)
                )
            }

            val maxVal = displayBars.flatMap { listOf(it.expenseMinor, it.budgetLimitMinor) }
                .maxOrNull()?.coerceAtLeast(10000L) ?: 100000L

            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            val expenseBarColor = ExpenseRed
            val budgetBarColor = Emerald600.copy(alpha = 0.7f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("monthly_vs_budget_canvas_chart")
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val bottomPadding = 30.dp.toPx()
                val topPadding = 15.dp.toPx()
                val chartHeight = canvasHeight - bottomPadding - topPadding

                // Grid lines (3 horizontal lines)
                val lineCount = 3
                for (i in 0..lineCount) {
                    val y = topPadding + (chartHeight / lineCount) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // Draw bars
                val itemCount = displayBars.size
                val groupWidth = canvasWidth / itemCount

                displayBars.forEachIndexed { index, item ->
                    val groupLeft = index * groupWidth
                    val barWidth = (groupWidth * 0.28f).coerceAtMost(36.dp.toPx())

                    // Expense bar height
                    val expenseHeight = (item.expenseMinor.toFloat() / maxVal.toFloat() * chartHeight).coerceIn(0f, chartHeight)
                    val expenseX = groupLeft + (groupWidth / 2f) - barWidth - 4.dp.toPx()
                    val expenseY = topPadding + (chartHeight - expenseHeight)

                    // Draw Expense bar
                    drawRoundRect(
                        color = expenseBarColor,
                        topLeft = Offset(expenseX, expenseY),
                        size = Size(barWidth, expenseHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Budget bar height
                    val budgetHeight = (item.budgetLimitMinor.toFloat() / maxVal.toFloat() * chartHeight).coerceIn(0f, chartHeight)
                    val budgetX = groupLeft + (groupWidth / 2f) + 4.dp.toPx()
                    val budgetY = topPadding + (chartHeight - budgetHeight)

                    // Draw Budget Limit bar
                    drawRoundRect(
                        color = budgetBarColor,
                        topLeft = Offset(budgetX, budgetY),
                        size = Size(barWidth, budgetHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ExpenseRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Emerald600.copy(alpha = 0.7f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Budget Limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
