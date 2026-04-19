package com.example.progettopm2026.jsonConverter.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SavedMenuDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(menu: SavedMenuEntity): Long

    @Query("SELECT * FROM saved_menus ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedMenuEntity>
}