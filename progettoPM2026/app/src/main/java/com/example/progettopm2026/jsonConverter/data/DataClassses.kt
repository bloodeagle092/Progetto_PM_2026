package com.example.progettopm2026.jsonConverter.data

import android.net.Uri
import kotlinx.serialization.Serializable

class DataClassses {

    @Serializable
    data class Menu(
        val restaurantName: String? = null,  // nullable: not always detectable
        val currency: String? = null,        // e.g. "EUR", "USD" — useful for UI formatting
        val dishes: List<Dish>
    )

    @Serializable
    data class Dish(
        val name: String,
        val description: String? = null,
        val price: Double? = null,           // nullable: some menus omit prices
        val ingredients: List<String> = emptyList(),
        val category: String? = null,        // "Antipasti", "Primi", "Desserts"...
        val allergens: List<String> = emptyList()  // bonus: many menus list these
    )

    sealed class MenuSource{
        data class ImageFile(val uri: Uri) : MenuSource()
        data class PdfFile(val uri: Uri) : MenuSource()
        data class TextFile(val uri: Uri) : MenuSource()
        data class WebUrl(val url: String) : MenuSource()
        data class RawText(val text: String) : MenuSource() // for testing
    }
}