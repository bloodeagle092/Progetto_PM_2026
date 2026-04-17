package com.example.progettopm2026.jsonConverter.model

import com.example.progettopm2026.jsonConverter.data.Menu
import com.example.progettopm2026.jsonConverter.data.MenuSource
class MenuConverter(
    private val extractor: TextExtractor,
    private val llmClient: MenuLlmClient
) {
    suspend fun convert(source: MenuSource): Result<Menu> = runCatching {
        val text = extractor.extract(source)
        require(text.isNotBlank()) { "No text could be extracted from source" }
        llmClient.extractMenu(text)
    }
}