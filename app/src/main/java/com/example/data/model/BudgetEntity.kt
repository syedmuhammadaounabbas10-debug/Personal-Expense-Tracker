package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["user_id", "month", "category_id"])
    ]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val user_id: String,
    val category_id: String?, // null = overall monthly budget
    val month: String, // "YYYY-MM" in Asia/Karachi
    val limit_minor: Long, // Paisa (e.g. 50,000 PKR = 5,000,000 paisa)
    val alerted_80: Boolean = false,
    val alerted_100: Boolean = false
)
