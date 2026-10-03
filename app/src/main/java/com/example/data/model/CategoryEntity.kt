package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["user_id", "type"]),
        Index(value = ["user_id", "name"])
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val user_id: String,
    val name: String,
    val icon: String,
    val color_hex: String,
    val type: String, // "EXPENSE" or "INCOME"
    val is_default: Boolean = false
)
