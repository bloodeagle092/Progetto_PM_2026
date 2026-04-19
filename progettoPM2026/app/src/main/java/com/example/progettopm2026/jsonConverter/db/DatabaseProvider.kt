package com.example.progettopm2026.jsonConverter.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "menu_converter.db"
            )
                //dev mode(per la version finale, non integro questa riga sotto, in quanto va azzerare i dati nel database ogni volta
                // che la struttura del database viene aggiornata)
                .fallbackToDestructiveMigration(true)
                .build().also { INSTANCE = it }
        }
    }
}