package com.example.progettopm2026.jsonConverter.model

import com.example.progettopm2026.jsonConverter.data.Menu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
class MenuLlmClient(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }
) {
    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-haiku-4-5-20251001"

        private const val MAX_TOKENS = 4096

        private const val MAX_INPUT_CHARS = 30_000
    }

    private val systemPrompt = """
        You extract restaurant menu data from raw text.
        Return ONLY valid JSON matching this exact schema:
        {
          "restaurantName": string | null,
          "currency": string | null,
          "dishes": [
            {
              "name": string,
              "description": string | null,
              "price": number | null,
              "ingredients": [string],
              "category": string | null,
              "allergens": [string]
            }
          ]
        }
        Rules:
        - Never invent data. Use null if a field is missing.
        - Prices as numbers only (no currency symbols).
        - Output JSON and nothing else. No markdown, no commentary.
    """.trimIndent()
    suspend fun extractMenu(rawText: String): Menu = withContext(Dispatchers.IO) {
        // Truncate to avoid runaway token costs on huge inputs.
        val input = rawText.take(MAX_INPUT_CHARS)

        val requestBody = buildRequestBody(input)
        val request = Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IOException("API error ${response.code}: $errorBody")
            }
            val rawJson = extractTextFromResponse(response.body!!.string())
            json.decodeFromString<Menu>(rawJson)
        }
    }

    private fun buildRequestBody(userText: String): String {
        val requestObj = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            put("system", systemPrompt)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "Extract the menu from this text:\n\n$userText")
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), requestObj)
    }

    private fun extractTextFromResponse(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val text = root["content"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
        return text
            .replace(Regex("^```(?:json)?\\s*"), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
    }
}
