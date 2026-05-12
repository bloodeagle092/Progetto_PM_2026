package com.example.progettopm2026.llmSearch.search

data class SearchResult(
    val itemId: String,
    val restaurantName: String,
    val title: String,
    val description: String,
    val category: String,
    val ingredients: String,
    val notes: String,
    val allergens: String,
    val prices: String,
    val score: Double
)
