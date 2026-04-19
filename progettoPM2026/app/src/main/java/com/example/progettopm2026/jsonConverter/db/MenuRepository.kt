package com.example.progettopm2026.jsonConverter.repository

import com.example.progettopm2026.jsonConverter.data.Menu
import com.example.progettopm2026.jsonConverter.data.MenuSource
import com.example.progettopm2026.jsonConverter.db.SavedMenuDao
import com.example.progettopm2026.jsonConverter.db.SavedMenuEntity
import com.example.progettopm2026.jsonConverter.storage.MenuFileStore

class MenuRepository(
    private val fileStore: MenuFileStore,
    private val dao: SavedMenuDao
) {

    suspend fun saveMenu(menu: Menu, source: MenuSource) {
        val savedFile = fileStore.saveMenu(menu)

        val entity = SavedMenuEntity(
            restaurantName = menu.restaurantName,
            currency = menu.currency,
            dishCount = menu.dishes.size,
            sourceType = source.javaClass.simpleName,
            sourceValue = source.toReadableSourceValue(),
            fileName = savedFile.fileName,
            filePath = savedFile.absolutePath,
            createdAt = System.currentTimeMillis()
        )

        dao.insert(entity)
    }

    private fun MenuSource.toReadableSourceValue(): String {
        return when (this) {
            is MenuSource.ImageFile -> uri.toString()
            is MenuSource.PdfFile -> uri.toString()
            is MenuSource.TextFile -> uri.toString()
            is MenuSource.WebUrl -> url
            is MenuSource.RawText -> "[raw_text]"
        }
    }
}