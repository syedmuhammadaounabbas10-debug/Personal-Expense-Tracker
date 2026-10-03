package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun getCategoryIconVector(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "restaurant", "food" -> Icons.Default.Restaurant
        "directions_bus", "transport", "commute" -> Icons.Default.DirectionsBus
        "receipt_long", "receipt", "bills" -> Icons.Default.ReceiptLong
        "school", "education" -> Icons.Default.School
        "medical_services", "health" -> Icons.Default.MedicalServices
        "shopping_bag", "shopping" -> Icons.Default.ShoppingBag
        "movie", "entertainment" -> Icons.Default.Movie
        "shopping_cart", "groceries" -> Icons.Default.ShoppingCart
        "account_balance_wallet", "salary" -> Icons.Default.AccountBalanceWallet
        "payments", "allowance" -> Icons.Default.Payments
        "laptop_mac", "freelance" -> Icons.Default.LaptopMac
        "store", "business" -> Icons.Default.Store
        "card_giftcard", "gift" -> Icons.Default.CardGiftcard
        "savings", "income" -> Icons.Default.Savings
        else -> Icons.Default.Category
    }
}

fun parseColorFromHex(hex: String, fallback: Color = Color(0xFF10B981)): Color {
    return try {
        val clean = if (hex.startsWith("#")) hex.substring(1) else hex
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorInt or 0x00000000FF000000)
        } else {
            Color(colorInt)
        }
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun CategoryIcon(
    iconName: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp
) {
    val tintColor = parseColorFromHex(colorHex)
    val bgColor = tintColor.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getCategoryIconVector(iconName),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
