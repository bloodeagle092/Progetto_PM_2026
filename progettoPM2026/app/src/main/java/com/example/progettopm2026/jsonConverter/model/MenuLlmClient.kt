package com.example.progettopm2026.jsonConverter.model

import android.util.Base64
import com.example.progettopm2026.jsonConverter.data.Menu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
        prettyPrint = false
    }
) {

    companion object {
        private const val API_URL = "https://api.openai.com/v1/responses"
        private const val MODEL = "gpt-5.4-mini"
        private const val MAX_INPUT_CHARS = 20_000
    }

    suspend fun extractMenuFromText(rawText: String): Menu = withContext(Dispatchers.IO) {
        val trimmed = rawText.take(MAX_INPUT_CHARS)

        val userContent = buildJsonArray {
            addJsonObject {
                put("type", "input_text")
                put("text", buildTextInstruction() + "\n\n" + trimmed)
            }
        }

        executeStructuredRequest(userContent)
    }

    suspend fun extractMenuFromImage(
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): Menu = withContext(Dispatchers.IO) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$base64"

        val userContent = buildJsonArray {
            addJsonObject {
                put("type", "input_text")
                put("text", buildVisionInstruction(fileName))
            }
            addJsonObject {
                put("type", "input_image")
                put("image_url", dataUrl)
            }
        }

        executeStructuredRequest(userContent)
    }

    suspend fun extractMenuFromPdf(
        bytes: ByteArray,
        fileName: String
    ): Menu = withContext(Dispatchers.IO) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val fileData = "data:application/pdf;base64,$base64"

        val userContent = buildJsonArray {
            addJsonObject {
                put("type", "input_text")
                put("text", buildPdfInstruction(fileName))
            }
            addJsonObject {
                put("type", "input_file")
                put("filename", fileName)
                put("file_data", fileData)
            }
        }

        executeStructuredRequest(userContent)
    }

    private suspend fun executeStructuredRequest(userContent: JsonArray): Menu =
        withContext(Dispatchers.IO) {
            val requestBody = buildRequestBody(userContent)

            val request = Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException("OpenAI API error ${response.code}: $body")
                }

                val content = extractStructuredText(body)
                val normalized = normalizeStructuredJson(content)

                json.decodeFromString<Menu>(normalized)
            }
        }

    private fun buildRequestBody(userContent: JsonArray): String {
        val requestObj = buildJsonObject {
            put("model", MODEL)
            put("store", false)

            putJsonArray("input") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt())
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userContent)
                }
            }

            putJsonObject("text") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("name", "restaurant_menu")
                    put("strict", true)
                    put("schema", buildSchema())
                }
            }

            put("temperature", 0.0)
        }

        return json.encodeToString(JsonObject.serializer(), requestObj)
    }

    private fun systemPrompt(): String =
        """
        Extract structured restaurant menu data.

        Hard rules:
        - Return only data explicitly supported by the input.
        - If the restaurant name is not explicitly shown as the venue/business name, return restaurantName = null.
        - Never use section titles, promo text, generic food words, or decorative words as restaurantName.
        - Examples that must NOT become restaurantName: Chicken, Chicker, Original, Special, Combo, Menu, Dinner, Lunch.
        - Extract only real purchasable menu items.
        - Ignore purely explanatory or decorative text unless it adds clear item-level metadata.
        - Preserve visible category names when clearly present.
        - If a dish has one or more clearly visible prices attached to that same item, return all of them in prices as numeric values only.
        - Do not merge prices from nearby dishes.
        - If a price is ambiguous or seems attached to another item, omit it.
        - Do not invent ingredients.
        - Do not invent allergens.
        - Keep useful item-level details such as size hints, minimum portions, or "served with rice" in notes.
        - If a nullable text field is missing, return null.
        - If a list field is missing, return [].
        - Output only structured data matching the schema.
        """.trimIndent()

    private fun buildTextInstruction(): String =
        """
        The following input is extracted text from a restaurant menu.
        Parse it into the schema exactly.
        """.trimIndent()

    private fun buildVisionInstruction(fileName: String): String =
        """
        Analyze this menu image directly.
        File name: $fileName

        Read the visual layout carefully.
        Do not infer restaurantName unless it is explicitly the venue/business name.
        Do not treat icons, decorative marks, isolated "V", chili symbols, or promo labels as notes.
        """.trimIndent()

    private fun buildPdfInstruction(fileName: String): String =
        """
        Analyze this menu PDF directly.
        File name: $fileName

        Use the PDF pages themselves.
        Do not infer restaurantName unless it is explicitly the venue/business name.
        Read each item from its own visual/text block and do not merge prices from neighboring items.
        """.trimIndent()

    private fun buildSchema(): JsonObject =
        buildJsonObject {
            put("type", "object")

            putJsonObject("properties") {
                putJsonObject("restaurantName") {
                    putJsonArray("type") {
                        add(JsonPrimitive("string"))
                        add(JsonPrimitive("null"))
                    }
                }

                putJsonObject("currency") {
                    putJsonArray("type") {
                        add(JsonPrimitive("string"))
                        add(JsonPrimitive("null"))
                    }
                }

                putJsonObject("dishes") {
                    put("type", "array")

                    putJsonObject("items") {
                        put("type", "object")

                        putJsonObject("properties") {
                            putJsonObject("name") {
                                put("type", "string")
                            }

                            putJsonObject("description") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }

                            putJsonObject("prices") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "number")
                                }
                            }

                            putJsonObject("ingredients") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "string")
                                }
                            }

                            putJsonObject("category") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }

                            putJsonObject("allergens") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "string")
                                }
                            }

                            putJsonObject("notes") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }
                        }

                        putJsonArray("required") {
                            add(JsonPrimitive("name"))
                            add(JsonPrimitive("description"))
                            add(JsonPrimitive("prices"))
                            add(JsonPrimitive("ingredients"))
                            add(JsonPrimitive("category"))
                            add(JsonPrimitive("allergens"))
                            add(JsonPrimitive("notes"))
                        }

                        put("additionalProperties", false)
                    }
                }
            }

            putJsonArray("required") {
                add(JsonPrimitive("restaurantName"))
                add(JsonPrimitive("currency"))
                add(JsonPrimitive("dishes"))
            }

            put("additionalProperties", false)
        }

    private fun extractStructuredText(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val output = root["output"]?.jsonArray
            ?: throw IOException("Missing output array in Responses API response")

        val collected = StringBuilder()

        for (item in output) {
            val itemObj = item.jsonObject
            val content = itemObj["content"]?.jsonArray ?: continue

            for (contentItem in content) {
                val contentObj = contentItem.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.contentOrNull

                if (type == "output_text") {
                    val text = contentObj["text"]?.jsonPrimitive?.contentOrNull
                    if (!text.isNullOrBlank()) {
                        if (collected.isNotEmpty()) collected.append('\n')
                        collected.append(text)
                    }
                }
            }
        }

        val result = collected.toString().trim()
        if (result.isBlank()) {
            throw IOException("No structured text found in Responses API response")
        }

        return result
    }

    private fun normalizeStructuredJson(rawJson: String): String {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val dishes = root["dishes"]?.jsonArray ?: JsonArray(emptyList())

        val normalizedDishes = dishes.map { dishElement ->
            val dish = dishElement.jsonObject

            buildJsonObject {
                put("name", normalizeRequiredString(dish["name"]))
                put("description", normalizeNullableString(dish["description"]))
                put("prices", normalizeNumberArray(dish["prices"]))
                put("ingredients", normalizeStringArray(dish["ingredients"]))
                put("category", normalizeNullableString(dish["category"]))
                put("allergens", normalizeStringArray(dish["allergens"]))
                put("notes", normalizeNullableNote(dish["notes"]))
            }
        }

        val categoryValues = normalizedDishes.mapNotNull {
            it["category"]?.jsonPrimitive?.contentOrNull?.lowercase()
        }.toSet()

        val dishNameValues = normalizedDishes.mapNotNull {
            it["name"]?.jsonPrimitive?.contentOrNull?.lowercase()
        }.toSet()

        val normalizedRoot = buildJsonObject {
            put(
                "restaurantName",
                sanitizeRestaurantName(
                    value = root["restaurantName"],
                    categoryNames = categoryValues,
                    dishNames = dishNameValues
                )
            )
            put("currency", normalizeCurrency(root["currency"]))
            put("dishes", JsonArray(normalizedDishes))
        }

        return json.encodeToString(JsonObject.serializer(), normalizedRoot)
    }

    private fun normalizeRequiredString(value: JsonElement?): JsonElement {
        val content = value?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return JsonPrimitive(content)
    }

    private fun normalizeNullableString(value: JsonElement?): JsonElement {
        val content = value?.jsonPrimitive?.contentOrNull?.trim()
        return if (content.isNullOrBlank()) JsonNull else JsonPrimitive(content)
    }

    private fun normalizeNullableNote(value: JsonElement?): JsonElement {
        val content = value?.jsonPrimitive?.contentOrNull?.trim()
        if (content.isNullOrBlank()) return JsonNull

        val normalized = content
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        val badStandaloneNotes = setOf(
            "v", "vv", "vvv", "veg", "vegan", "vegetarian", "🌶", "🌶️", "🔥", "*"
        )

        val lowered = normalized.lowercase()
        if (lowered in badStandaloneNotes) return JsonNull
        if (Regex("""^[vV\s]+$""").matches(normalized)) return JsonNull
        if (Regex("""^[🌶️🔥*•·.\-–—_ ]+$""").matches(normalized)) return JsonNull

        return JsonPrimitive(normalized)
    }

    private fun normalizeStringArray(value: JsonElement?): JsonArray {
        val arr = value?.jsonArray ?: return JsonArray(emptyList())

        val seen = linkedSetOf<String>()
        arr.forEach { item ->
            val text = item.jsonPrimitive.contentOrNull?.trim()
            if (!text.isNullOrBlank()) {
                seen.add(text)
            }
        }

        return JsonArray(seen.map { JsonPrimitive(it) })
    }

    private fun normalizeNumberArray(value: JsonElement?): JsonArray {
        val arr = value?.jsonArray ?: return JsonArray(emptyList())

        val seen = linkedSetOf<Double>()
        arr.forEach { item ->
            val primitive = item.jsonPrimitive
            val asDouble = primitive.doubleOrNull
                ?: primitive.contentOrNull
                    ?.replace(",", ".")
                    ?.trim()
                    ?.toDoubleOrNull()

            if (asDouble != null) {
                seen.add(asDouble)
            }
        }

        return JsonArray(seen.map { JsonPrimitive(it) })
    }

    private fun normalizeCurrency(value: JsonElement?): JsonElement {
        val raw = value?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
        if (raw.isNullOrBlank()) return JsonNull

        return when (raw) {
            "€" -> JsonPrimitive("EUR")
            "$" -> JsonPrimitive("USD")
            "£" -> JsonPrimitive("GBP")
            else -> JsonPrimitive(raw)
        }
    }

    private fun sanitizeRestaurantName(
        value: JsonElement?,
        categoryNames: Set<String>,
        dishNames: Set<String>
    ): JsonElement {
        val raw = value?.jsonPrimitive?.contentOrNull?.trim()
            ?: return JsonNull

        val cleaned = raw
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        if (cleaned.isBlank()) return JsonNull

        val lower = cleaned.lowercase()

        val forbiddenExact = setOf(
            "menu", "dinner", "lunch", "chicken", "chicker",
            "original", "special", "combo", "ciao"
        )

        val forbiddenContains = listOf(
            "fried chicken", "pollo fritto", "frittate", "ravioli",
            "zuppe", "dessert", "antipasti", "salse", "special"
        )

        val tokens = lower.split(Regex("""[^a-zàèéìòù'’]+"""))
            .filter { it.isNotBlank() }

        if (lower in forbiddenExact) return JsonNull
        if (lower in categoryNames) return JsonNull
        if (lower in dishNames) return JsonNull
        if (tokens.size == 1 && tokens.first() in forbiddenExact) return JsonNull
        if (forbiddenContains.any { lower.contains(it) }) return JsonNull
        if (lower.length < 3) return JsonNull

        return JsonPrimitive(cleaned)
    }
}