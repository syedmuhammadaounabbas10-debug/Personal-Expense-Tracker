package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"])]
)
data class UserEntity(
    @PrimaryKey val id: String, // Firebase UID
    val email: String,
    val name: String,
    val currency: String = "PKR",
    val timezone: String = "Asia/Karachi",
    val is_guest: Boolean = false,
    val photo_url: String? = null,
    val provider_id: String = "password",
    val created_at: Long = System.currentTimeMillis()
)
