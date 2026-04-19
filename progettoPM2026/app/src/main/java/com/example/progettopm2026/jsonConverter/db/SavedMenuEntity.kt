package com.example.progettopm2026.jsonConverter.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_menus")
data class SavedMenuEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val restaurantName: String?,
    val currency: String?,
    val dishCount: Int,
    val sourceType: String,
    val sourceValue: String,
    val fileName: String,
    val filePath: String,
    val createdAt: Long
)