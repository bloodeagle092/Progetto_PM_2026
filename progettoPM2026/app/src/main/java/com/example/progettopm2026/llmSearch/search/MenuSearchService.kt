package com.example.progettopm2026.llmSearch.search

import com.example.progettopm2026.llmSearch.database.MenuEmbeddingStore
import com.example.progettopm2026.llmSearch.embedding.TextEmbedder

class MenuSearchService(
    private val embedder: TextEmbedder,
    private val store: MenuEmbeddingStore
) {

    suspend fun search(query: String, limit: Int = 5, minScore: Double = 0.65): List<SearchResult> {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return emptyList()

        val queryEmbedding = embedder.embed(cleaned)
        return store.search(queryEmbedding, limit).map { scored ->
            SearchResult(
                itemId = scored.embedding.itemId,
                restaurantName = scored.embedding.restaurantName,
                title = scored.embedding.title,
                description = scored.embedding.description,
                category = scored.embedding.category,
                ingredients = scored.embedding.ingredients,
                notes = scored.embedding.notes,
                allergens = scored.embedding.allergens,
                prices = scored.embedding.prices,
                score = scored.score
            )
        }.filter { it.score >= minScore }
    }
}
