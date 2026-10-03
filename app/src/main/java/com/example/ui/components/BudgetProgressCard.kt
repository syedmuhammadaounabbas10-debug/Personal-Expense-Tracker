package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Emerald600
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.Gold600
import com.example.ui.theme.WarningOrange
import com.example.util.PaisaHelper

@Composable
fun BudgetProgressCard(
    title: String,
    spentMinor: Long,
    limitMinor: Long,
    currencyCode: String = "PKR",
    categoryIcon: String? = null,
    categoryColorHex: String? = null,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null
) {
    val percentage = PaisaHelper.calculateBudgetPercentage(spentMinor, limitMinor)
    val remainingMinor = limitMinor - spentMinor

    // Threshold styling
    val (statusColor, statusBg, statusText) = when {
        percentage >= 100f -> Triple(ExpenseRed, Color(0xFFFFE4E6), "Exceeded 100%!")
        percentage >= 80f -> Triple(WarningOrange, Color(0xFFFFEDD5), "Approaching (80%+)")
        else -> Triple(Emerald600, Color(0xFFD1FAE5), "On Track")
    }

    val progressFraction = (percentage / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progressFraction, label = "budgetProgress")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("budget_card_$title"),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (categoryIcon != null && categoryColorHex != null) {
                        CategoryIcon(
                            iconName = categoryIcon,
                            colorHex = categoryColorHex,
                            size = 36.dp,
                            iconSize = 20.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Limit: ${PaisaHelper.formatPkr(limitMinor, currencyCode = currencyCode)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Threshold badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (percentage >= 80f) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(CircleShape),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spent: ${PaisaHelper.formatPkr(spentMinor, currencyCode = currencyCode)} (${percentage.toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                val remainingText = if (remainingMinor >= 0) {
                    "Left: ${PaisaHelper.formatPkr(remainingMinor, currencyCode = currencyCode)}"
                } else {
                    "Over: ${PaisaHelper.formatPkr(-remainingMinor, currencyCode = currencyCode)}"
                }

                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (remainingMinor >= 0) Emerald600 else ExpenseRed
                    )
                )
            }
        }
    }
}
