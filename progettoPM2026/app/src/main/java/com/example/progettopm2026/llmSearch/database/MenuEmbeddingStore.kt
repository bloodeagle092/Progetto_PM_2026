package com.example.progettopm2026.llmSearch.database

import android.content.Context
import com.example.progettopm2026.llmSearch.database.entities.MenuEmbedding
import com.example.progettopm2026.llmSearch.database.entities.MenuEmbedding_
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class ScoredMenuEmbedding(
    val embedding: MenuEmbedding,
    val score: Double
)

class MenuEmbeddingStore(context: Context) {

    private val box: Box<MenuEmbedding> = ObjectBoxProvider.getStore(context).boxFor(MenuEmbedding::class.java)

    suspend fun upsertAll(items: List<MenuEmbedding>) = withContext(Dispatchers.IO) {
        if (items.isNotEmpty()) {
            box.put(items)
        }
    }

    suspend fun count(): Long = withContext(Dispatchers.IO) {
        box.count()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        box.removeAll()
    }

    suspend fun search(queryEmbedding: FloatArray, limit: Int = 10): List<ScoredMenuEmbedding> = withContext(Dispatchers.IO) {
        val query = normalize(queryEmbedding)
        if (isZeroVector(query)) return@withContext emptyList()

        box.query(MenuEmbedding_.embedding.nearestNeighbors(query, limit))
            .build()
            .findWithScores()
            .map { result ->
                ScoredMenuEmbedding(
                    embedding = result.get(),
                    score = 1.0 / (1.0 + result.score)
                )
            }
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        vector.forEach { value ->
            sum += (value * value).toDouble()
        }

        val magnitude = sqrt(sum).takeIf { it > 0.0 } ?: return vector.copyOf()
        return vector.map { (it / magnitude).toFloat() }.toFloatArray()
    }

    private fun isZeroVector(vector: FloatArray): Boolean {
        return vector.all { it == 0f }
    }
}
