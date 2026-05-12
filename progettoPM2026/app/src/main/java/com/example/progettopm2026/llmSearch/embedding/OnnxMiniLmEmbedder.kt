package com.example.progettopm2026.llmSearch.embedding

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.LongBuffer
import kotlin.math.sqrt

class OnnxMiniLmEmbedder private constructor(
    private val session: OrtSession,
    private val tokenizer: WordPieceTokenizer,
    private val maxLength: Int = 256
) : TextEmbedder {

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val input = tokenizer.encode(text, maxLength)
        val env = OrtEnvironment.getEnvironment()

        val inputs = linkedMapOf<String, OnnxTensor>()
        inputs[INPUT_IDS] = OnnxTensor.createTensor(env, LongBuffer.wrap(input.inputIds), longArrayOf(1, maxLength.toLong()))
        inputs[ATTENTION_MASK] = OnnxTensor.createTensor(env, LongBuffer.wrap(input.attentionMask), longArrayOf(1, maxLength.toLong()))

        if (session.inputInfo.containsKey(TOKEN_TYPE_IDS)) {
            inputs[TOKEN_TYPE_IDS] = OnnxTensor.createTensor(env, LongBuffer.wrap(input.tokenTypeIds), longArrayOf(1, maxLength.toLong()))
        }

        try {
            session.run(inputs).use { result ->
                extractEmbedding(result)
            }
        } finally {
            inputs.values.forEach { tensor ->
                runCatching { tensor.close() }
            }
        }
    }

    private fun extractEmbedding(result: OrtSession.Result): FloatArray {
        val raw = result.get(0).value
        val embedding = when (raw) {
            is FloatArray -> raw
            is Array<*> -> flattenArray(raw)
            is java.nio.FloatBuffer -> FloatArray(raw.remaining()).also { raw.get(it) }
            else -> throw IOException("Unsupported ONNX output type: ${raw?.javaClass?.name}")
        }

        normalize(embedding)
        return embedding
    }

    private fun flattenArray(raw: Array<*>): FloatArray {
        val first = raw.firstOrNull()
        return when (first) {
            is FloatArray -> first.copyOf()
            is Array<*> -> flattenArray(first)
            is Number -> raw.map { (it as Number).toFloat() }.toFloatArray()
            else -> throw IOException("Unsupported ONNX array output: ${first?.javaClass?.name}")
        }
    }

    private fun normalize(vector: FloatArray) {
        var sum = 0.0
        vector.forEach { value -> sum += (value * value).toDouble() }
        val magnitude = sqrt(sum).takeIf { it > 0.0 } ?: return
        for (index in vector.indices) {
            vector[index] = (vector[index] / magnitude).toFloat()
        }
    }

    companion object {
        private const val MODEL_ASSET = "llmSearch/all-MiniLM-L6-v2/model.onnx"
        private const val VOCAB_ASSET = "llmSearch/all-MiniLM-L6-v2/vocab.txt"
        private const val INPUT_IDS = "input_ids"
        private const val ATTENTION_MASK = "attention_mask"
        private const val TOKEN_TYPE_IDS = "token_type_ids"

        fun fromAssets(context: Context): OnnxMiniLmEmbedder {
            val env = OrtEnvironment.getEnvironment()
            val modelFile = copyAssetToCache(context, MODEL_ASSET, "all-MiniLM-L6-v2.onnx")
            val tokenizer = WordPieceTokenizer(loadVocab(context, VOCAB_ASSET))
            val session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            return OnnxMiniLmEmbedder(session = session, tokenizer = tokenizer)
        }

        private fun copyAssetToCache(context: Context, assetPath: String, fileName: String): File {
            val targetDir = File(context.cacheDir, "onnx_mini_lm")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val targetFile = File(targetDir, fileName)
            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile
            }

            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return targetFile
        }

        private fun loadVocab(context: Context, assetPath: String): Map<String, Int> {
            return context.assets.open(assetPath).bufferedReader().useLines { lines ->
                lines
                    .mapIndexed { index, token -> token.trim() to index }
                    .filter { (token, _) -> token.isNotBlank() }
                    .toMap()
            }
        }
    }
}
