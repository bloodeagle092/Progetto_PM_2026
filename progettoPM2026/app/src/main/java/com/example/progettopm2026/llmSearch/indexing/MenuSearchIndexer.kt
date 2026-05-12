package com.example.progettopm2026.llmSearch.indexing

import com.example.progettopm2026.jsonConverter.data.Menu
import com.example.progettopm2026.llmSearch.database.MenuEmbeddingStore
import com.example.progettopm2026.llmSearch.database.entities.MenuEmbedding
import com.example.progettopm2026.llmSearch.embedding.TextEmbedder

class MenuSearchIndexer(
    private val embedder: TextEmbedder,
    private val store: MenuEmbeddingStore
) {

    suspend fun index(menu: Menu, savedFileName: String) {
        val restaurantName = menu.restaurantName?.trim().orEmpty()

        val embeddings = menu.dishes.mapIndexed { index, dish ->
            val title = dish.name.trim()
            val description = dish.description?.trim().orEmpty()
            val category = dish.category?.trim().orEmpty()
            val ingredients = dish.ingredients.map { it.trim() }.filter { it.isNotBlank() }
            val notes = dish.notes?.trim().orEmpty()
            val allergens = dish.allergens.map { it.trim() }.filter { it.isNotBlank() }
            val prices = dish.prices?.joinToString(", ") ?: ""
            val searchableText = buildSearchText(
                restaurantName = restaurantName,
                title = title,
                description = description,
                category = category,
                ingredients = ingredients,
                notes = notes,
                allergens = allergens,
                prices = prices
            )

            MenuEmbedding(
                itemId = "$savedFileName:$index",
                restaurantName = restaurantName,
                title = title,
                description = description,
                category = category,
                ingredients = ingredients.joinToString(", "),
                notes = notes,
                allergens = allergens.joinToString(", "),
                prices = prices,
                searchText = searchableText,
                embedding = embedder.embed(searchableText)
            )
        }

        store.upsertAll(embeddings)
    }

    private fun buildSearchText(
        restaurantName: String,
        title: String,
        description: String,
        category: String,
        ingredients: List<String>,
        notes: String,
        allergens: List<String>,
        prices: String
    ): String {
        return buildString {
            if (restaurantName.isNotBlank()) appendLine("restaurant: $restaurantName")
            appendLine("dish: $title")
            if (description.isNotBlank()) appendLine("description: $description")
            if (category.isNotBlank()) appendLine("category: $category")
            if (ingredients.isNotEmpty()) appendLine("ingredients: ${ingredients.joinToString(", ")}")
            if (allergens.isNotEmpty()) appendLine("allergens: ${allergens.joinToString(", ")}")
            if (notes.isNotBlank()) appendLine("notes: $notes")
            if (prices.isNotBlank()) appendLine("prices: $prices")
        }.trim()
    }
}
