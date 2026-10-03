package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.util.PaisaHelper

/**
 * Room entity representing an Expense.
 * Money precision rule: 'amount' is stored as an integer in the smallest unit (paisa, 1 PKR = 100 paisa)
 * to avoid floating-point rounding errors and ensure financial calculation accuracy.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["date"]),
        Index(value = ["category"])
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Long, // Amount in Paisa (1 PKR = 100 paisa)
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val description: String
) {
    /**
     * Convenient getter for human-readable PKR decimal representation
     */
    val amountPkr: Double
        get() = PaisaHelper.paisaToPkrDouble(amount)

    /**
     * Convenient formatted display string (e.g. "PKR 2,500")
     */
    val formattedAmount: String
        get() = PaisaHelper.formatPkr(amount)
}
