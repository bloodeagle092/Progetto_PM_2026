package com.example.progettopm2026.jsonConverter.storage

import android.content.Context
import com.example.progettopm2026.jsonConverter.data.Menu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MenuFileStore(
    private val context: Context,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }
) {

    suspend fun saveMenu(menu: Menu): SavedMenuFile = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "menus")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val fileName = buildFileName(menu.restaurantName)
        val file = File(dir, fileName)

        file.writeText(json.encodeToString(Menu.serializer(), menu))

        SavedMenuFile(
            fileName = fileName,
            absolutePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }

    private fun buildFileName(restaurantName: String?): String {
        val safeName = restaurantName
            ?.lowercase(Locale.ROOT)
            ?.replace(Regex("[^a-z0-9]+"), "_")
            ?.trim('_')
            ?.takeIf { it.isNotBlank() }
            ?: "menu"

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val shortId = UUID.randomUUID().toString().take(8)

        return "${safeName}_${timestamp}_${shortId}.json"
    }
}

data class SavedMenuFile(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long
)