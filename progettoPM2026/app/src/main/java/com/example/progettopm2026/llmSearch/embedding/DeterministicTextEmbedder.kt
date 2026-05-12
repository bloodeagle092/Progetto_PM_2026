package com.example.progettopm2026.llmSearch.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class DeterministicTextEmbedder : TextEmbedder {

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val vector = FloatArray(DIMENSIONS)
        val normalized = text.lowercase(Locale.ROOT)
        val tokens = TOKEN_REGEX.findAll(normalized).map { it.value }.toList()

        if (tokens.isEmpty()) return@withContext vector

        tokens.forEachIndexed { index, token ->
            accumulate(vector, token, 1.0f)

            if (index > 0) {
                accumulate(vector, tokens[index - 1] + '_' + token, 0.7f)
            }

            if (token.length >= 3) {
                token.windowed(3, 1, partialWindows = false).forEach { triGram ->
                    accumulate(vector, triGram, 0.35f)
                }
            }
        }

        normalize(vector)
        vector
    }

    private fun accumulate(vector: FloatArray, token: String, weight: Float) {
        val hash = stableHash(token)
        val primaryIndex = abs(hash % DIMENSIONS)
        val secondaryIndex = abs((hash * 31) % DIMENSIONS)

        vector[primaryIndex] += weight
        vector[secondaryIndex] += weight * 0.5f
    }

    private fun stableHash(value: String): Int {
        var hash = 0x811C9DC5.toInt()
        value.forEach { char ->
            hash = hash xor char.code
            hash *= 0x01000193
        }
        return hash
    }

    private fun normalize(vector: FloatArray) {
        var sum = 0.0
        vector.forEach { value ->
            sum += (value * value).toDouble()
        }

        val magnitude = sqrt(sum).takeIf { it > 0.0 } ?: return
        for (index in vector.indices) {
            vector[index] = (vector[index] / magnitude).toFloat()
        }
    }

    private companion object {
        const val DIMENSIONS = 384
        val TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
    }
}
