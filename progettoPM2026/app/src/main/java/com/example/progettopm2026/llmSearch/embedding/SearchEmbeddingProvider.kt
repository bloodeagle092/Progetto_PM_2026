package com.example.progettopm2026.llmSearch.embedding

import android.content.Context

object SearchEmbeddingProvider {

    @Volatile
    private var INSTANCE: SearchEmbeddingRuntime? = null

    fun get(context: Context): SearchEmbeddingRuntime {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildRuntime(context.applicationContext).also { INSTANCE = it }
        }
    }

    private fun buildRuntime(context: Context): SearchEmbeddingRuntime {
        return runCatching {
            val embedder = OnnxMiniLmEmbedder.fromAssets(context)
            SearchEmbeddingRuntime(
                embedder = embedder,
                backendLabel = "ONNX MiniLM"
            )
        }.getOrElse { error ->
            SearchEmbeddingRuntime(
                embedder = DeterministicTextEmbedder(),
                backendLabel = "Deterministic fallback (${error.javaClass.simpleName}: ${error.message ?: "no message"})"
            )
        }
    }
}
