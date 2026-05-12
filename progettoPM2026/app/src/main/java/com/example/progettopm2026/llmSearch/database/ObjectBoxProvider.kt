package com.example.progettopm2026.llmSearch.database

import android.content.Context
import io.objectbox.BoxStore
import com.example.progettopm2026.llmSearch.database.entities.MyObjectBox

object ObjectBoxProvider {

    @Volatile
    private var INSTANCE: BoxStore? = null

    fun getStore(context: Context): BoxStore {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildStore(context).also { INSTANCE = it }
        }
    }

    private fun buildStore(context: Context): BoxStore {
        return MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }
}
