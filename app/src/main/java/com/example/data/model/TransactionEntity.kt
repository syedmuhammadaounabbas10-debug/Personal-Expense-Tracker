package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["user_id", "occurred_on"]),
        Index(value = ["user_id", "category_id"]),
        Index(value = ["client_id"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val user_id: String,
    val category_id: String,
    val type: String, // "EXPENSE" or "INCOME"
    val amount_minor: Long, // Paisa (1 PKR = 100 paisa)
    val note: String,
    val occurred_on: Long, // timestamp in millis
    val deleted_at: Long? = null, // Soft delete
    val updated_at: Long = System.currentTimeMillis(),
    val client_id: String // Idempotent sync ID
)
