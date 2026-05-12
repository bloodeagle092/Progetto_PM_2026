package com.example.progettopm2026.llmSearch.embedding

interface TextEmbedder {
    suspend fun embed(text: String): FloatArray
}
