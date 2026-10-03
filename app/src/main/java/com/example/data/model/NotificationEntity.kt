package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["user_id", "created_at"])
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val user_id: String,
    val title: String,
    val body: String,
    val type: String = "BUDGET_ALERT", // "BUDGET_WARNING", "BUDGET_EXCEEDED", "INFO"
    val is_read: Boolean = false,
    val created_at: Long = System.currentTimeMillis()
)
