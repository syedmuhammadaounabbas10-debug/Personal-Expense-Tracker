package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val name: String,
    val currency: String = "PKR",
    val timezone: String = "Asia/Karachi",
    val created_at: Long = System.currentTimeMillis()
)
