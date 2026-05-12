package com.example.progettopm2026.llmSearch.embedding

data class SearchEmbeddingRuntime(
    val embedder: TextEmbedder,
    val backendLabel: String
)
