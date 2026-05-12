package com.example.progettopm2026.jsonConverter.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedMenuEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedMenuDao(): SavedMenuDao
}
