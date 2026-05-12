package com.example.progettopm2026.llmSearch.embedding

import java.text.Normalizer
import java.util.Locale

class WordPieceTokenizer(
    vocab: Map<String, Int>
) {

    private val vocab = vocab
    private val clsId = vocab[CLS_TOKEN] ?: 101
    private val sepId = vocab[SEP_TOKEN] ?: 102
    private val padId = vocab[PAD_TOKEN] ?: 0
    private val unkId = vocab[UNK_TOKEN] ?: 100

    fun encode(text: String, maxLength: Int): TokenizedInput {
        val wordPieces = tokenize(text)

        val ids = ArrayList<Long>(maxLength)
        val attention = LongArray(maxLength)
        val tokenTypes = LongArray(maxLength)

        ids.add(clsId.toLong())
        wordPieces.forEach { piece ->
            if (ids.size < maxLength - 1) {
                ids.add((vocab[piece] ?: unkId).toLong())
            }
        }
        if (ids.size < maxLength) {
            ids.add(sepId.toLong())
        }

        while (ids.size < maxLength) {
            ids.add(padId.toLong())
        }

        for (index in 0 until maxLength) {
            attention[index] = if (index < ids.indexOfLast { it != padId.toLong() } + 1) 1 else 0
            tokenTypes[index] = 0
        }

        return TokenizedInput(
            inputIds = ids.toLongArray(),
            attentionMask = attention,
            tokenTypeIds = tokenTypes
        )
    }

    private fun tokenize(text: String): List<String> {
        val cleaned = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

        val words = BASIC_TOKEN_REGEX.findAll(cleaned).map { it.value }.toList()
        val pieces = ArrayList<String>()

        for (word in words) {
            pieces.addAll(wordPieceTokenize(word))
        }

        return pieces
    }

    private fun wordPieceTokenize(token: String): List<String> {
        if (token.isBlank()) return emptyList()
        if (vocab.containsKey(token)) return listOf(token)

        val subTokens = ArrayList<String>()
        var start = 0

        while (start < token.length) {
            var end = token.length
            var current: String? = null

            while (start < end) {
                val candidate = if (start == 0) token.substring(start, end) else "##" + token.substring(start, end)
                if (vocab.containsKey(candidate)) {
                    current = candidate
                    break
                }
                end--
            }

            if (current == null) {
                return listOf(UNK_TOKEN)
            }

            subTokens.add(current)
            start = end
        }

        return subTokens
    }

    companion object {
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"
        private const val UNK_TOKEN = "[UNK]"
        private val BASIC_TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]")
    }
}

data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
)
