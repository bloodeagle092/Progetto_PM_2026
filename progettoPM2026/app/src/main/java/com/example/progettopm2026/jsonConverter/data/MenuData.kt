package com.example.progettopm2026.jsonConverter.data

import android.net.Uri
import kotlinx.serialization.Serializable
@Serializable
data class Menu(
    val restaurantName: String? = null,
    val currency: String? = null,
    val dishes: List<Dish> = emptyList()
)

@Serializable
data class Dish(
    val name: String,
    val description: String? = null,
    val price: Double? = null,
    val ingredients: List<String> = emptyList(),
    val category: String? = null,
    val allergens: List<String> = emptyList()
)

sealed class MenuSource {
    data class ImageFile(val uri: Uri) : MenuSource()
    data class PdfFile(val uri: Uri) : MenuSource()
    data class TextFile(val uri: Uri) : MenuSource()
    data class WebUrl(val url: String) : MenuSource()
    data class RawText(val text: String) : MenuSource()  // handy for unit testing
}
